package com.subscriptiontracker.data.database

import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.ReminderOffset
import com.subscriptiontracker.domain.model.ReminderSettings
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val persistenceJson = Json
private val stringListSerializer = ListSerializer(String.serializer())
private val integerListSerializer = ListSerializer(Int.serializer())

fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id.toString(),
    name = name,
    provider = provider,
    category = category,
    iconIdentifier = iconIdentifier,
    status = status.name,
    price = price.toString(),
    currencyCode = currency.currencyCode,
    billingInterval = billingInterval?.name,
    billingIntervalCount = billingIntervalCount,
    autoRenew = autoRenew,
    startDate = startDate.toString(),
    nextBillingDate = nextBillingDate?.toString(),
    expirationDate = expirationDate?.toString(),
    trialEndDate = trialEndDate?.toString(),
    notes = notes,
    tagsJson = persistenceJson.encodeToString(stringListSerializer, tags.sorted()),
    plan = plan,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    archivedAt = archivedAt?.toString(),
)

fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = UUID.fromString(id),
    name = name,
    provider = provider,
    category = category,
    iconIdentifier = iconIdentifier,
    status = SubscriptionStatus.valueOf(status),
    price = BigDecimal(price),
    currency = Currency.getInstance(currencyCode),
    billingInterval = billingInterval?.let(RecurrenceUnit::valueOf),
    billingIntervalCount = billingIntervalCount,
    autoRenew = autoRenew,
    startDate = LocalDate.parse(startDate),
    nextBillingDate = nextBillingDate?.let(LocalDate::parse),
    expirationDate = expirationDate?.let(LocalDate::parse),
    trialEndDate = trialEndDate?.let(LocalDate::parse),
    notes = notes,
    tags = persistenceJson.decodeFromString(stringListSerializer, tagsJson).toSet(),
    plan = plan,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    archivedAt = archivedAt?.let(Instant::parse),
)

fun RecurringEvent.toEntity(): RecurringEventEntity {
    val repeating = recurrenceRule as? RecurrenceRule.Repeating
    return RecurringEventEntity(
        id = id.toString(),
        subscriptionId = subscriptionId.toString(),
        type = type.name,
        title = title,
        nextOccurrence = nextOccurrence.toString(),
        recurrenceKind = if (repeating == null) "ONE_TIME" else "REPEATING",
        recurrenceInterval = repeating?.interval,
        recurrenceUnit = repeating?.unit?.name,
        timezoneId = timezone.id,
        enabled = enabled,
        remindersEnabled = reminders.enabled,
        reminderOffsetsJson = persistenceJson.encodeToString(
            integerListSerializer,
            reminders.offsets.map(ReminderOffset::daysBefore).sorted(),
        ),
        notificationTime = reminders.notificationTime.toString(),
        notes = notes,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}

fun RecurringEventEntity.toDomain(): RecurringEvent = RecurringEvent(
    id = UUID.fromString(id),
    subscriptionId = UUID.fromString(subscriptionId),
    type = RecurringEventType.valueOf(type),
    title = title,
    nextOccurrence = LocalDate.parse(nextOccurrence),
    recurrenceRule = when (recurrenceKind) {
        "ONE_TIME" -> RecurrenceRule.OneTime
        "REPEATING" -> RecurrenceRule.Repeating(
            interval = requireNotNull(recurrenceInterval),
            unit = RecurrenceUnit.valueOf(requireNotNull(recurrenceUnit)),
        )
        else -> error("Unknown recurrence kind: $recurrenceKind")
    },
    timezone = ZoneId.of(timezoneId),
    enabled = enabled,
    reminders = ReminderSettings(
        enabled = remindersEnabled,
        offsets = persistenceJson.decodeFromString(integerListSerializer, reminderOffsetsJson)
            .map(::ReminderOffset)
            .toSet(),
        notificationTime = LocalTime.parse(notificationTime),
    ),
    notes = notes,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
)

fun Quota.toEntity(): QuotaEntity = QuotaEntity(
    id = id.toString(),
    subscriptionId = subscriptionId.toString(),
    name = name,
    unit = unit,
    used = used?.toString(),
    remaining = remaining?.toString(),
    limit = limit?.toString(),
    percentage = percentage?.toString(),
    unlimited = unlimited,
    resetEventId = resetEventId?.toString(),
    warningThresholdPercentage = warningThresholdPercentage?.toString(),
    updatedAt = updatedAt.toString(),
)

fun QuotaEntity.toDomain(): Quota = Quota(
    id = UUID.fromString(id),
    subscriptionId = UUID.fromString(subscriptionId),
    name = name,
    unit = unit,
    used = used?.let(::BigDecimal),
    remaining = remaining?.let(::BigDecimal),
    limit = limit?.let(::BigDecimal),
    percentage = percentage?.let(::BigDecimal),
    unlimited = unlimited,
    resetEventId = resetEventId?.let(UUID::fromString),
    warningThresholdPercentage = warningThresholdPercentage?.let(::BigDecimal),
    updatedAt = Instant.parse(updatedAt),
)
