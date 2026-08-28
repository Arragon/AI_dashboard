package com.subscriptiontracker.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class RecurringEventType {
    BILLING,
    QUOTA_RESET,
    EXPIRATION,
    TRIAL_END,
    CUSTOM,
    PRICE_CHANGE,
    PROMOTION_END,
    CONTRACT_NOTICE,
}

data class RecurringEvent(
    val id: UUID,
    val subscriptionId: UUID,
    val type: RecurringEventType,
    val title: String,
    val nextOccurrence: LocalDate,
    val recurrenceRule: RecurrenceRule,
    val timezone: ZoneId,
    val enabled: Boolean = true,
    val reminders: ReminderSettings = ReminderSettings(),
    val notes: String = "",
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(title.isNotBlank()) { "Event title cannot be blank" }
    }
}
