package com.subscriptiontracker.domain.model

import java.time.LocalTime

data class ReminderOffset(val daysBefore: Int) {
    init {
        require(daysBefore >= 0) { "Reminder offset cannot be negative" }
    }

    companion object {
        val SameDay = ReminderOffset(0)
        val OneDayBefore = ReminderOffset(1)
        val ThreeDaysBefore = ReminderOffset(3)
        val SevenDaysBefore = ReminderOffset(7)
    }
}

data class ReminderSettings(
    val enabled: Boolean = true,
    val offsets: Set<ReminderOffset> = setOf(ReminderOffset.OneDayBefore),
    val notificationTime: LocalTime = LocalTime.of(9, 0),
) {
    init {
        require(!enabled || offsets.isNotEmpty()) {
            "Enabled reminders require at least one offset"
        }
    }
}
