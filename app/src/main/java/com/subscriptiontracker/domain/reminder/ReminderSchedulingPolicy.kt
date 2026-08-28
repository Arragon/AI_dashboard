package com.subscriptiontracker.domain.reminder

import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.ReminderOffset
import com.subscriptiontracker.domain.service.RecurrenceEngine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.zone.ZoneOffsetTransition
import java.util.UUID

data class ScheduledReminder(
    val id: String,
    val uniqueWorkName: String,
    val eventId: UUID,
    val subscriptionId: UUID,
    val eventType: String,
    val occurrenceDate: LocalDate,
    val offset: ReminderOffset,
    val targetInstant: Instant,
)

/** Pure conversion from date-based event semantics to schedulable instants. */
class ReminderSchedulingPolicy(
    private val recurrenceEngine: RecurrenceEngine = RecurrenceEngine(),
) {
    fun schedulesFor(event: RecurringEvent, now: Instant): List<ScheduledReminder> {
        if (!event.enabled || !event.reminders.enabled) return emptyList()

        val currentDate = now.atZone(event.timezone).toLocalDate()
        return event.reminders.offsets
            .sortedByDescending(ReminderOffset::daysBefore)
            .mapNotNull { offset -> nextSchedule(event, offset, currentDate, now) }
    }

    private fun nextSchedule(
        event: RecurringEvent,
        offset: ReminderOffset,
        currentDate: LocalDate,
        now: Instant,
    ): ScheduledReminder? {
        var occurrence = recurrenceEngine.occurrenceOnOrAfter(
            event.nextOccurrence,
            event.recurrenceRule,
            currentDate,
        )
        while (occurrence != null) {
            val reminderDate = occurrence.minusDays(offset.daysBefore.toLong())
            val target = resolveLocalDateTime(
                LocalDateTime.of(reminderDate, event.reminders.notificationTime),
                event.timezone,
            ).toInstant()
            if (target.isAfter(now)) {
                val id = reminderId(event.id, occurrence, offset)
                return ScheduledReminder(
                    id = id,
                    uniqueWorkName = "$WORK_NAME_PREFIX$id",
                    eventId = event.id,
                    subscriptionId = event.subscriptionId,
                    eventType = event.type.name,
                    occurrenceDate = occurrence,
                    offset = offset,
                    targetInstant = target,
                )
            }
            occurrence = recurrenceEngine.occurrenceAfter(
                event.nextOccurrence,
                event.recurrenceRule,
                occurrence,
            )
        }
        return null
    }

    /**
     * Gaps use the first valid local time after the transition. Overlaps use the earlier offset,
     * matching java.time's default while making the behavior stable and explicit.
     */
    internal fun resolveLocalDateTime(localDateTime: LocalDateTime, zoneId: ZoneId): ZonedDateTime {
        val rules = zoneId.rules
        val offsets = rules.getValidOffsets(localDateTime)
        return when {
            offsets.size == 1 -> ZonedDateTime.ofLocal(localDateTime, zoneId, offsets.single())
            offsets.size >= 2 -> ZonedDateTime.ofLocal(localDateTime, zoneId, offsets.first())
            else -> {
                val transition: ZoneOffsetTransition = requireNotNull(rules.getTransition(localDateTime))
                ZonedDateTime.ofLocal(transition.dateTimeAfter, zoneId, transition.offsetAfter)
            }
        }
    }

    companion object {
        const val WORK_NAME_PREFIX = "reminder:"

        fun reminderId(eventId: UUID, occurrenceDate: LocalDate, offset: ReminderOffset): String =
            "$eventId:${occurrenceDate.toEpochDay()}:${offset.daysBefore}"
    }
}
