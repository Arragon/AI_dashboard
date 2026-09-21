package com.subscriptiontracker.data.backup

import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.QuotaOrigin
import com.subscriptiontracker.domain.model.QuotaSyncState
import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.ReminderOffset
import com.subscriptiontracker.domain.model.ReminderSettings
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupCodec(
    private val appVersion: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
        encodeDefaults = true
        prettyPrint = true
    }

    init {
        require(appVersion.isNotBlank()) { "App version cannot be blank" }
    }

    fun encode(content: BackupContent): String = json.encodeToString(content.toDto(appVersion, clock.instant()))

    fun write(content: BackupContent, output: OutputStream) {
        output.write(encode(content).toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    fun decode(encoded: String): BackupDecodeResult {
        val document = try {
            json.decodeFromString<BackupDocumentDto>(encoded)
        } catch (_: SerializationException) {
            return malformedJson()
        } catch (_: IllegalArgumentException) {
            return malformedJson()
        }
        if (document.schemaVersion != BACKUP_SCHEMA_VERSION) {
            return BackupDecodeResult.Failure(
                listOf(
                    BackupValidationError(
                        BackupErrorCode.UNSUPPORTED_SCHEMA,
                        "schemaVersion",
                        "Backup schema version ${document.schemaVersion} is not supported",
                    ),
                ),
            )
        }
        return validate(document)
    }

    fun read(input: InputStream): BackupDecodeResult {
        val encoded = try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(input.readBytes())).toString()
        } catch (_: Exception) {
            return malformedJson()
        }
        return decode(encoded)
    }

    private fun validate(document: BackupDocumentDto): BackupDecodeResult {
        val validator = Validator()
        validator.requiredText(document.appVersion, "appVersion")
        validator.instant(document.exportedAt, "exportedAt")
        document.settings.defaultCurrencyCode?.let { validator.currency(it, "settings.defaultCurrencyCode") }
        document.settings.defaultTimezoneId?.let { validator.zone(it, "settings.defaultTimezoneId") }

        val subscriptions = document.subscriptions.mapIndexedNotNull { index, dto ->
            validator.subscription(dto, "subscriptions[$index]")
        }
        val events = document.events.mapIndexedNotNull { index, dto ->
            validator.event(dto, "events[$index]")
        }
        val quotas = document.quotas.mapIndexedNotNull { index, dto ->
            validator.quota(dto, "quotas[$index]")
        }

        validator.uniqueIds(subscriptions.map(Subscription::id), "subscriptions")
        validator.uniqueIds(events.map(RecurringEvent::id), "events")
        validator.uniqueIds(quotas.map(Quota::id), "quotas")

        val subscriptionIds = subscriptions.mapTo(mutableSetOf(), Subscription::id)
        val eventById = events.associateBy(RecurringEvent::id)
        events.forEachIndexed { index, event ->
            if (event.subscriptionId !in subscriptionIds) {
                validator.reference("events[$index].subscriptionId", "Event references a missing subscription")
            }
        }
        quotas.forEachIndexed { index, quota ->
            if (quota.subscriptionId !in subscriptionIds) {
                validator.reference("quotas[$index].subscriptionId", "Quota references a missing subscription")
            }
            quota.resetEventId?.let { resetEventId ->
                val resetEvent = eventById[resetEventId]
                if (resetEvent == null) {
                    validator.reference("quotas[$index].resetEventId", "Quota references a missing reset event")
                } else if (resetEvent.subscriptionId != quota.subscriptionId) {
                    validator.reference(
                        "quotas[$index].resetEventId",
                        "Quota reset event must belong to the same subscription",
                    )
                }
            }
        }

        if (validator.errors.isNotEmpty()) return BackupDecodeResult.Failure(validator.errors)
        return BackupDecodeResult.Success(
            ValidatedBackup(
                schemaVersion = document.schemaVersion,
                appVersion = document.appVersion,
                exportedAt = document.exportedAt,
                content = BackupContent(
                    subscriptions = subscriptions,
                    events = events,
                    quotas = quotas,
                    settings = BackupSettings(
                        defaultCurrencyCode = document.settings.defaultCurrencyCode,
                        defaultTimezoneId = document.settings.defaultTimezoneId,
                        reminderOffsetDays = document.settings.reminderOffsetDays,
                        reminderTime = document.settings.reminderTime,
                    ),
                ),
            ),
        )
    }

    private fun malformedJson() = BackupDecodeResult.Failure(
        listOf(BackupValidationError(BackupErrorCode.MALFORMED_JSON, "$", "Backup is not valid JSON")),
    )
}

private class Validator {
    val errors = mutableListOf<BackupValidationError>()

    fun subscription(dto: SubscriptionBackupDto, path: String): Subscription? {
        requiredText(dto.name, "$path.name")
        requiredText(dto.provider, "$path.provider")
        requiredText(dto.category, "$path.category")
        dto.tags.forEachIndexed { index, tag -> requiredText(tag, "$path.tags[$index]") }
        if (dto.tags.size != dto.tags.distinct().size) invalid("$path.tags", "Tags must be unique")
        val id = uuid(dto.id, "$path.id")
        val status = enumValue<SubscriptionStatus>(dto.status, "$path.status")
        val price = decimal(dto.price, "$path.price")?.also {
            if (it < BigDecimal.ZERO) invalid("$path.price", "Price cannot be negative")
        }
        val currency = currency(dto.currencyCode, "$path.currencyCode")
        val interval = dto.billingInterval?.let { enumValue<RecurrenceUnit>(it, "$path.billingInterval") }
        if ((dto.billingInterval == null) != (dto.billingIntervalCount == null)) {
            invalid("$path.billingInterval", "Billing interval and count must both be present or absent")
        }
        if (dto.billingIntervalCount != null && dto.billingIntervalCount <= 0) {
            invalid("$path.billingIntervalCount", "Billing interval count must be positive")
        }
        val startDate = date(dto.startDate, "$path.startDate")
        val nextBillingDate = dto.nextBillingDate?.let { date(it, "$path.nextBillingDate") }
        val expirationDate = dto.expirationDate?.let { date(it, "$path.expirationDate") }
        val trialEndDate = dto.trialEndDate?.let { date(it, "$path.trialEndDate") }
        val createdAt = instant(dto.createdAt, "$path.createdAt")
        val updatedAt = instant(dto.updatedAt, "$path.updatedAt")
        val archivedAt = dto.archivedAt?.let { instant(it, "$path.archivedAt") }
        if (status != null && status != SubscriptionStatus.ARCHIVED && dto.archivedAt != null) {
            invalid("$path.archivedAt", "Only archived subscriptions can have an archived timestamp")
        }
        if (dto.onlineProviderId != null && dto.onlineProviderId.isBlank()) {
            invalid("$path.onlineProviderId", "Online provider id cannot be blank")
        }
        if (listOf(id, status, price, currency, startDate, createdAt, updatedAt).any { it == null }) return null
        return construct(path) {
            Subscription(
                id = id!!,
                name = dto.name,
                provider = dto.provider,
                category = dto.category,
                iconIdentifier = dto.iconIdentifier,
                status = status!!,
                price = price!!,
                currency = currency!!,
                billingInterval = interval,
                billingIntervalCount = dto.billingIntervalCount,
                autoRenew = dto.autoRenew,
                startDate = startDate!!,
                nextBillingDate = nextBillingDate,
                expirationDate = expirationDate,
                trialEndDate = trialEndDate,
                notes = dto.notes,
                tags = dto.tags.toSet(),
                plan = dto.plan,
                createdAt = createdAt!!,
                updatedAt = updatedAt!!,
                archivedAt = archivedAt,
                onlineProviderId = dto.onlineProviderId?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun event(dto: EventBackupDto, path: String): RecurringEvent? {
        requiredText(dto.title, "$path.title")
        val id = uuid(dto.id, "$path.id")
        val subscriptionId = uuid(dto.subscriptionId, "$path.subscriptionId")
        val type = enumValue<RecurringEventType>(dto.type, "$path.type")
        val occurrence = date(dto.nextOccurrence, "$path.nextOccurrence")
        val timezone = zone(dto.timezoneId, "$path.timezoneId")
        val notificationTime = time(dto.reminders.notificationTime, "$path.reminders.notificationTime")
        val createdAt = instant(dto.createdAt, "$path.createdAt")
        val updatedAt = instant(dto.updatedAt, "$path.updatedAt")
        val recurrence = recurrence(dto.recurrence, "$path.recurrence")
        if (dto.reminders.offsetsDaysBefore.any { it < 0 }) {
            invalid("$path.reminders.offsetsDaysBefore", "Reminder offsets cannot be negative")
        }
        if (dto.reminders.offsetsDaysBefore.size != dto.reminders.offsetsDaysBefore.distinct().size) {
            invalid("$path.reminders.offsetsDaysBefore", "Reminder offsets must be unique")
        }
        if (dto.reminders.enabled && dto.reminders.offsetsDaysBefore.isEmpty()) {
            invalid("$path.reminders.offsetsDaysBefore", "Enabled reminders require at least one offset")
        }
        if (listOf(id, subscriptionId, type, occurrence, timezone, notificationTime, createdAt, updatedAt, recurrence)
                .any { it == null }
        ) return null
        return construct(path) {
            RecurringEvent(
                id = id!!,
                subscriptionId = subscriptionId!!,
                type = type!!,
                title = dto.title,
                nextOccurrence = occurrence!!,
                recurrenceRule = recurrence!!,
                timezone = timezone!!,
                enabled = dto.enabled,
                reminders = ReminderSettings(
                    enabled = dto.reminders.enabled,
                    offsets = dto.reminders.offsetsDaysBefore.map(::ReminderOffset).toSet(),
                    notificationTime = notificationTime!!,
                ),
                notes = dto.notes,
                createdAt = createdAt!!,
                updatedAt = updatedAt!!,
            )
        }
    }

    fun quota(dto: QuotaBackupDto, path: String): Quota? {
        requiredText(dto.name, "$path.name")
        requiredText(dto.unit, "$path.unit")
        val id = uuid(dto.id, "$path.id")
        val subscriptionId = uuid(dto.subscriptionId, "$path.subscriptionId")
        val used = nullableDecimal(dto.used, "$path.used", allowZero = true)
        val remaining = nullableDecimal(dto.remaining, "$path.remaining", allowZero = true)
        val limit = nullableDecimal(dto.limit, "$path.limit", allowZero = false)
        val percentage = percentage(dto.percentage, "$path.percentage")
        val warning = percentage(dto.warningThresholdPercentage, "$path.warningThresholdPercentage")
        val resetEventId = dto.resetEventId?.let { uuid(it, "$path.resetEventId") }
        val updatedAt = instant(dto.updatedAt, "$path.updatedAt")
        val origin = enumValue<QuotaOrigin>(dto.origin, "$path.origin")
        val syncState = enumValue<QuotaSyncState>(dto.syncState, "$path.syncState")
        if (dto.stableKey != null && dto.stableKey.isBlank()) invalid("$path.stableKey", "Quota stable key cannot be blank")
        if (dto.syncNote != null && dto.syncNote.isBlank()) invalid("$path.syncNote", "Quota sync note cannot be blank")
        if (listOf(id, subscriptionId, updatedAt, origin, syncState).any { it == null }) return null
        return construct(path) {
            Quota(
                id = id!!,
                subscriptionId = subscriptionId!!,
                name = dto.name,
                unit = dto.unit,
                used = used,
                remaining = remaining,
                limit = limit,
                percentage = percentage,
                unlimited = dto.unlimited,
                resetEventId = resetEventId,
                warningThresholdPercentage = warning,
                updatedAt = updatedAt!!,
                stableKey = dto.stableKey,
                origin = origin!!,
                syncState = syncState!!,
                syncNote = dto.syncNote,
            )
        }
    }

    private fun recurrence(dto: RecurrenceBackupDto, path: String): RecurrenceRule? = when (dto.kind) {
        "ONE_TIME" -> {
            if (dto.interval != null || dto.unit != null) {
                invalid(path, "One-time recurrence cannot have an interval or unit")
                null
            } else RecurrenceRule.OneTime
        }
        "REPEATING" -> {
            val unit = dto.unit?.let { enumValue<RecurrenceUnit>(it, "$path.unit") }
            if (dto.interval == null || dto.interval <= 0) {
                invalid("$path.interval", "Repeating recurrence requires a positive interval")
                null
            } else if (unit == null) {
                if (dto.unit == null) invalid("$path.unit", "Repeating recurrence requires a unit")
                null
            } else RecurrenceRule.Repeating(dto.interval, unit)
        }
        else -> {
            invalid("$path.kind", "Unknown recurrence kind")
            null
        }
    }

    fun uniqueIds(ids: List<UUID>, path: String) {
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            errors += BackupValidationError(BackupErrorCode.DUPLICATE_ID, path, "Duplicate ID: $it")
        }
    }

    fun requiredText(value: String, path: String) {
        if (value.isBlank()) invalid(path, "Value cannot be blank")
    }

    fun reference(path: String, message: String) {
        errors += BackupValidationError(BackupErrorCode.DANGLING_REFERENCE, path, message)
    }

    fun instant(value: String, path: String): Instant? = parse(value, path, "Invalid ISO instant", Instant::parse)
    private fun date(value: String, path: String): LocalDate? = parse(value, path, "Invalid ISO date", LocalDate::parse)
    private fun time(value: String, path: String): LocalTime? = parse(value, path, "Invalid ISO time", LocalTime::parse)
    fun zone(value: String, path: String): ZoneId? = parse(value, path, "Invalid time zone", ZoneId::of)
    private fun uuid(value: String, path: String): UUID? = parse(value, path, "Invalid UUID", UUID::fromString)
    private fun decimal(value: String, path: String): BigDecimal? = parse(value, path, "Invalid decimal", ::BigDecimal)

    fun currency(value: String, path: String): Currency? {
        if (value.length != 3 || value.uppercase() != value) {
            invalid(path, "Invalid ISO currency code")
            return null
        }
        return parse(value, path, "Invalid ISO currency code", Currency::getInstance)
    }

    private fun nullableDecimal(value: String?, path: String, allowZero: Boolean): BigDecimal? {
        val number = value?.let { decimal(it, path) } ?: return null
        if (number < BigDecimal.ZERO || (!allowZero && number == BigDecimal.ZERO)) {
            invalid(path, if (allowZero) "Value cannot be negative" else "Value must be positive")
        }
        return number
    }

    private fun percentage(value: String?, path: String): BigDecimal? {
        val number = value?.let { decimal(it, path) } ?: return null
        if (number < BigDecimal.ZERO || number > BigDecimal(100)) invalid(path, "Percentage must be between 0 and 100")
        return number
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String, path: String): T? =
        enumValues<T>().firstOrNull { it.name == value } ?: run {
            invalid(path, "Unknown value")
            null
        }

    private fun <T> parse(value: String, path: String, message: String, parser: (String) -> T): T? = try {
        parser(value)
    } catch (_: RuntimeException) {
        invalid(path, message)
        null
    }

    private fun <T> construct(path: String, constructor: () -> T): T? = try {
        constructor()
    } catch (_: IllegalArgumentException) {
        invalid(path, "Record is semantically invalid")
        null
    }

    fun invalid(path: String, message: String) {
        errors += BackupValidationError(BackupErrorCode.INVALID_VALUE, path, message)
    }
}

private fun BackupContent.toDto(appVersion: String, exportedAt: Instant) = BackupDocumentDto(
    schemaVersion = BACKUP_SCHEMA_VERSION,
    appVersion = appVersion,
    exportedAt = exportedAt.toString(),
    subscriptions = subscriptions.map(Subscription::toBackupDto),
    events = events.map(RecurringEvent::toBackupDto),
    quotas = quotas.map(Quota::toBackupDto),
    settings = SettingsBackupDto(
        settings.defaultCurrencyCode,
        settings.defaultTimezoneId,
        settings.reminderOffsetDays,
        settings.reminderTime,
    ),
)

private fun Subscription.toBackupDto() = SubscriptionBackupDto(
    id = id.toString(), name = name, provider = provider, category = category, iconIdentifier = iconIdentifier,
    status = status.name, price = price.toString(), currencyCode = currency.currencyCode,
    billingInterval = billingInterval?.name, billingIntervalCount = billingIntervalCount, autoRenew = autoRenew,
    startDate = startDate.toString(), nextBillingDate = nextBillingDate?.toString(),
    expirationDate = expirationDate?.toString(), trialEndDate = trialEndDate?.toString(), notes = notes,
    tags = tags.sorted(), plan = plan, createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
    archivedAt = archivedAt?.toString(), onlineProviderId = onlineProviderId,
)

private fun RecurringEvent.toBackupDto(): EventBackupDto {
    val recurrence = when (val rule = recurrenceRule) {
        RecurrenceRule.OneTime -> RecurrenceBackupDto("ONE_TIME", null, null)
        is RecurrenceRule.Repeating -> RecurrenceBackupDto("REPEATING", rule.interval, rule.unit.name)
    }
    return EventBackupDto(
        id = id.toString(), subscriptionId = subscriptionId.toString(), type = type.name, title = title,
        nextOccurrence = nextOccurrence.toString(), recurrence = recurrence, timezoneId = timezone.id,
        enabled = enabled, reminders = ReminderBackupDto(
            reminders.enabled,
            reminders.offsets.map(ReminderOffset::daysBefore).sorted(),
            reminders.notificationTime.toString(),
        ), notes = notes, createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
    )
}

private fun Quota.toBackupDto() = QuotaBackupDto(
    id = id.toString(), subscriptionId = subscriptionId.toString(), name = name, unit = unit,
    used = used?.toString(), remaining = remaining?.toString(), limit = limit?.toString(),
    percentage = percentage?.toString(), unlimited = unlimited, resetEventId = resetEventId?.toString(),
    warningThresholdPercentage = warningThresholdPercentage?.toString(), updatedAt = updatedAt.toString(),
    stableKey = stableKey, origin = origin.name, syncState = syncState.name, syncNote = syncNote,
)
