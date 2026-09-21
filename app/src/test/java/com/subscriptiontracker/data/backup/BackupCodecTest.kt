package com.subscriptiontracker.data.backup

import com.subscriptiontracker.data.database.AtomicRecordReplacement
import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.ReminderOffset
import com.subscriptiontracker.domain.model.ReminderSettings
import com.subscriptiontracker.domain.model.Subscription
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupCodecTest {
    private fun stripOnlineFields(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { it !in ONLINE_FIELDS }.mapValues { (_, value) -> stripOnlineFields(value) },
        )
        is JsonArray -> JsonArray(element.map(::stripOnlineFields))
        else -> element
    }

    private val exportedAt = Instant.parse("2026-03-01T02:03:04.123456789Z")
    private val codec = BackupCodec("1.2.3-test", Clock.fixed(exportedAt, ZoneId.of("UTC")))

    @Test
    fun roundTripPreservesAllRecordsSettingsAndFutureReservedEventTypes() {
        val content = contentWithEveryEventType()

        val encoded = codec.encode(content)
        val result = assertInstanceOf(BackupDecodeResult.Success::class.java, codec.decode(encoded))

        assertEquals(BACKUP_SCHEMA_VERSION, result.backup.schemaVersion)
        assertEquals("1.2.3-test", result.backup.appVersion)
        assertEquals(exportedAt.toString(), result.backup.exportedAt)
        assertEquals(content, result.backup.content)
        assertTrue(RecurringEventType.entries.all { type -> result.backup.content.events.any { it.type == type } })
        assertTrue(encoded.contains("\"price\": \"1234567890.0012300\""))
    }

    @Test
    fun streamApisUseUtf8WithoutClosingCallerStreams() {
        val content = contentWithEveryEventType()
        val output = ByteArrayOutputStream()

        codec.write(content, output)
        output.write('\n'.code)
        val result = codec.read(ByteArrayInputStream(output.toByteArray().dropLast(1).toByteArray()))

        assertEquals(content, assertInstanceOf(BackupDecodeResult.Success::class.java, result).backup.content)
    }

    @Test
    fun malformedJsonAndUnknownFieldsAreRejected() {
        assertFailureCode(codec.decode("{not-json"), BackupErrorCode.MALFORMED_JSON)
        val withUnknownField = codec.encode(contentWithEveryEventType()).replaceFirst("{", "{\n  \"unknown\": true,")
        assertFailureCode(codec.decode(withUnknownField), BackupErrorCode.MALFORMED_JSON)
    }

    @Test
    fun legacyBackupWithoutOnlineFieldsStillDecodes() {
        val content = contentWithEveryEventType()
        val stripped = Json.encodeToString(JsonElement.serializer(), stripOnlineFields(Json.parseToJsonElement(codec.encode(content))))

        val result = assertInstanceOf(BackupDecodeResult.Success::class.java, codec.decode(stripped))

        assertEquals(content, result.backup.content)
    }

    @Test
    fun unsupportedSchemaIsRejectedWithTypedError() {
        val encoded = codec.encode(contentWithEveryEventType()).replace("\"schemaVersion\": 1", "\"schemaVersion\": 999")

        assertFailureCode(codec.decode(encoded), BackupErrorCode.UNSUPPORTED_SCHEMA)
    }

    @Test
    fun duplicateIdsAreRejected() {
        val content = contentWithEveryEventType()
        val duplicate = content.copy(subscriptions = content.subscriptions + content.subscriptions.first())

        assertFailureCode(codec.decode(codec.encode(duplicate)), BackupErrorCode.DUPLICATE_ID)
    }

    @Test
    fun danglingSubscriptionAndResetEventReferencesAreRejected() {
        val content = contentWithEveryEventType()
        val danglingEvent = content.events.first().copy(subscriptionId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
        val danglingQuota = content.quotas.first().copy(resetEventId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"))
        val invalid = content.copy(events = listOf(danglingEvent) + content.events.drop(1), quotas = listOf(danglingQuota))

        val failure = assertInstanceOf(BackupDecodeResult.Failure::class.java, codec.decode(codec.encode(invalid)))

        assertTrue(failure.errors.count { it.code == BackupErrorCode.DANGLING_REFERENCE } >= 2)
    }

    @Test
    fun resetEventMustBelongToQuotaSubscription() {
        val content = contentWithEveryEventType()
        val secondSubscription = subscription(UUID.fromString("20000000-0000-0000-0000-000000000001"))
        val invalidQuota = content.quotas.first().copy(subscriptionId = secondSubscription.id)

        val result = codec.decode(codec.encode(content.copy(
            subscriptions = content.subscriptions + secondSubscription,
            quotas = listOf(invalidQuota),
        )))

        assertFailureCode(result, BackupErrorCode.DANGLING_REFERENCE)
    }

    @Test
    fun invalidNumericDateTimeZoneCurrencyAndRecurrenceValuesAreRejected() {
        val valid = codec.encode(contentWithEveryEventType())
        val invalidBackups = listOf(
            valid.replaceFirst("\"price\": \"1234567890.0012300\"", "\"price\": \"-1\""),
            valid.replaceFirst("\"startDate\": \"2024-01-31\"", "\"startDate\": \"2024-02-30\""),
            valid.replaceFirst("\"notificationTime\": \"09:07:06.123456789\"", "\"notificationTime\": \"25:00\""),
            valid.replaceFirst("\"timezoneId\": \"Asia/Tokyo\"", "\"timezoneId\": \"Not/AZone\""),
            valid.replaceFirst("\"currencyCode\": \"JPY\"", "\"currencyCode\": \"NOPE\""),
            valid.replaceFirst("\"interval\": 3", "\"interval\": 0"),
            valid.replaceFirst("\"percentage\": \"0.12300\"", "\"percentage\": \"101\""),
        )

        invalidBackups.forEach { encoded ->
            assertFailureCode(codec.decode(encoded), BackupErrorCode.INVALID_VALUE)
        }
    }

    @Test
    fun restoreRequiresConfirmationAndValidInputNeverCallsStoreOnFailure() = runBlocking {
        val store = FakeReplacement()
        val service = ReplaceRestoreService(codec, store)
        val valid = codec.encode(contentWithEveryEventType())

        assertRestoreFailureCode(service.restore(valid, confirmed = false), BackupErrorCode.CONFIRMATION_REQUIRED)
        assertRestoreFailureCode(service.restore("not json", confirmed = true), BackupErrorCode.MALFORMED_JSON)
        val invalid = valid.replaceFirst("\"currencyCode\": \"JPY\"", "\"currencyCode\": \"INVALID\"")
        assertRestoreFailureCode(service.restore(invalid, confirmed = true), BackupErrorCode.INVALID_VALUE)
        assertEquals(0, store.calls)
    }

    @Test
    fun confirmedValidRestoreInvokesAtomicStoreOnceWithValidatedData() = runBlocking {
        val store = FakeReplacement()
        val service = ReplaceRestoreService(codec, store)
        val content = contentWithEveryEventType()

        val result = service.restore(
            ByteArrayInputStream(codec.encode(content).toByteArray(Charsets.UTF_8)),
            confirmed = true,
        )

        assertInstanceOf(RestoreResult.Success::class.java, result)
        assertEquals(1, store.calls)
        assertEquals(content.subscriptions, store.subscriptions)
        assertEquals(content.events, store.events)
        assertEquals(content.quotas, store.quotas)
    }

    private fun contentWithEveryEventType(): BackupContent {
        val subscription = subscription()
        val events = RecurringEventType.entries.mapIndexed { index, type ->
            event(
                id = UUID.fromString("10000000-0000-0000-0000-${(index + 2).toString().padStart(12, '0')}"),
                subscriptionId = subscription.id,
                type = type,
                recurrence = if (type == RecurringEventType.CONTRACT_NOTICE) RecurrenceRule.OneTime
                else RecurrenceRule.Repeating(3, RecurrenceUnit.MONTHS),
            )
        }
        val resetEvent = events.first { it.type == RecurringEventType.QUOTA_RESET }
        val nullableQuota = quota(subscription.id, resetEvent.id).copy(
            id = UUID.fromString("10000000-0000-0000-0000-000000000098"),
            used = null,
            remaining = null,
            limit = null,
            percentage = null,
            unlimited = true,
            resetEventId = null,
            warningThresholdPercentage = null,
        )
        return BackupContent(
            subscriptions = listOf(subscription),
            events = events,
            quotas = listOf(quota(subscription.id, resetEvent.id), nullableQuota),
            settings = BackupSettings(defaultCurrencyCode = "USD", defaultTimezoneId = "Europe/Paris"),
        )
    }

    private fun subscription(id: UUID = UUID.fromString("10000000-0000-0000-0000-000000000001")) = Subscription(
        id = id,
        name = "Precise Plan",
        provider = "Provider",
        category = "Software",
        iconIdentifier = "provider/icon",
        price = BigDecimal("1234567890.0012300"),
        currency = Currency.getInstance("JPY"),
        billingInterval = RecurrenceUnit.MONTHS,
        billingIntervalCount = 3,
        autoRenew = false,
        startDate = LocalDate.parse("2024-01-31"),
        nextBillingDate = LocalDate.parse("2024-04-30"),
        expirationDate = LocalDate.parse("2027-12-31"),
        trialEndDate = LocalDate.parse("2024-02-15"),
        notes = "notes",
        tags = setOf("comma,tag", "quote\"tag", "line\nbreak", "日本語"),
        plan = "Enterprise",
        createdAt = Instant.parse("2024-01-02T03:04:05.123456789Z"),
        updatedAt = Instant.parse("2024-02-03T04:05:06.987654321Z"),
    )

    private fun event(
        id: UUID,
        subscriptionId: UUID,
        type: RecurringEventType,
        recurrence: RecurrenceRule,
    ) = RecurringEvent(
        id = id,
        subscriptionId = subscriptionId,
        type = type,
        title = "${type.name} event",
        nextOccurrence = LocalDate.parse("2025-03-31"),
        recurrenceRule = recurrence,
        timezone = ZoneId.of("Asia/Tokyo"),
        reminders = ReminderSettings(
            enabled = true,
            offsets = setOf(ReminderOffset(0), ReminderOffset(7), ReminderOffset(30)),
            notificationTime = LocalTime.parse("09:07:06.123456789"),
        ),
        notes = "event notes",
        createdAt = Instant.parse("2024-01-02T03:04:05.123456789Z"),
        updatedAt = Instant.parse("2024-02-03T04:05:06.987654321Z"),
    )

    private fun quota(subscriptionId: UUID, resetEventId: UUID) = Quota(
        id = UUID.fromString("10000000-0000-0000-0000-000000000099"),
        subscriptionId = subscriptionId,
        name = "API calls",
        unit = "requests",
        used = BigDecimal("1.2300"),
        remaining = BigDecimal("998.7700"),
        limit = BigDecimal("1000.0000"),
        percentage = BigDecimal("0.12300"),
        unlimited = false,
        resetEventId = resetEventId,
        warningThresholdPercentage = BigDecimal("80.5000"),
        updatedAt = Instant.parse("2024-02-03T04:05:06.123456789Z"),
    )

    private fun assertFailureCode(result: BackupDecodeResult, code: BackupErrorCode) {
        val failure = assertInstanceOf(BackupDecodeResult.Failure::class.java, result)
        assertTrue(failure.errors.any { it.code == code }, "Expected $code but got ${failure.errors}")
    }

    private fun assertRestoreFailureCode(result: RestoreResult, code: BackupErrorCode) {
        val failure = assertInstanceOf(RestoreResult.Failure::class.java, result)
        assertTrue(failure.errors.any { it.code == code }, "Expected $code but got ${failure.errors}")
    }

    private class FakeReplacement : AtomicRecordReplacement {
        var calls = 0
        var subscriptions = emptyList<Subscription>()
        var events = emptyList<RecurringEvent>()
        var quotas = emptyList<Quota>()

        override suspend fun replaceAll(
            subscriptions: List<Subscription>,
            events: List<RecurringEvent>,
            quotas: List<Quota>,
        ) {
            calls++
            this.subscriptions = subscriptions
            this.events = events
            this.quotas = quotas
        }
    }

    private companion object {
        val ONLINE_FIELDS = setOf("onlineProviderId", "stableKey", "origin", "syncState", "syncNote")
    }
}
