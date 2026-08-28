package com.subscriptiontracker.domain.reminder

import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.ReminderOffset
import com.subscriptiontracker.domain.model.ReminderSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReminderSchedulingPolicyTest {
    private val policy = ReminderSchedulingPolicy()
    private val now = Instant.parse("2024-01-01T00:00:00Z")

    @Test
    fun `combines reminder date and event zone without changing logical date`() {
        val occurrence = LocalDate.of(2024, 1, 2)
        val honolulu = policy.schedulesFor(event(occurrence = occurrence, zone = "Pacific/Honolulu"), now).single()
        val tokyo = policy.schedulesFor(event(occurrence = occurrence, zone = "Asia/Tokyo"), now).single()

        assertEquals(Instant.parse("2024-01-02T19:00:00Z"), honolulu.targetInstant)
        assertEquals(Instant.parse("2024-01-02T00:00:00Z"), tokyo.targetInstant)
        assertEquals(occurrence, honolulu.occurrenceDate)
    }

    @Test
    fun `DST gap advances to first valid local time`() {
        val schedule = policy.schedulesFor(
            event(
                occurrence = LocalDate.of(2024, 3, 10),
                zone = "America/New_York",
                time = LocalTime.of(2, 30),
            ),
            Instant.parse("2024-03-01T00:00:00Z"),
        ).single()

        assertEquals(Instant.parse("2024-03-10T07:00:00Z"), schedule.targetInstant)
    }

    @Test
    fun `DST overlap deterministically chooses earlier offset`() {
        val schedule = policy.schedulesFor(
            event(
                occurrence = LocalDate.of(2024, 11, 3),
                zone = "America/New_York",
                time = LocalTime.of(1, 30),
            ),
            Instant.parse("2024-11-01T00:00:00Z"),
        ).single()

        assertEquals(Instant.parse("2024-11-03T05:30:00Z"), schedule.targetInstant)
    }

    @Test
    fun `multiple offsets create reminders from occurrence date`() {
        val schedules = policy.schedulesFor(
            event(
                occurrence = LocalDate.of(2024, 2, 10),
                offsets = setOf(ReminderOffset.SameDay, ReminderOffset.OneDayBefore, ReminderOffset.SevenDaysBefore),
            ),
            now,
        )

        assertEquals(3, schedules.size)
        assertEquals(
            setOf(
                Instant.parse("2024-02-03T09:00:00Z"),
                Instant.parse("2024-02-09T09:00:00Z"),
                Instant.parse("2024-02-10T09:00:00Z"),
            ),
            schedules.map(ScheduledReminder::targetInstant).toSet(),
        )
    }

    @Test
    fun `disabled event disabled reminders and past targets produce no schedules`() {
        assertTrue(policy.schedulesFor(event(enabled = false), now).isEmpty())
        assertTrue(policy.schedulesFor(event(remindersEnabled = false), now).isEmpty())
        assertTrue(
            policy.schedulesFor(
                event(occurrence = LocalDate.of(2023, 12, 31), offsets = setOf(ReminderOffset.SameDay)),
                now,
            ).isEmpty(),
        )
        assertTrue(
            policy.schedulesFor(
                event(
                    occurrence = LocalDate.of(2024, 1, 1),
                    time = LocalTime.MIDNIGHT,
                    offsets = setOf(ReminderOffset.SameDay),
                ),
                now,
            ).isEmpty(),
        )
    }

    @Test
    fun `past repeating anchor advances to the next reminder occurrence`() {
        val schedules = policy.schedulesFor(
            event(
                occurrence = LocalDate.of(2024, 1, 31),
                offsets = setOf(ReminderOffset.OneDayBefore),
                recurrenceRule = RecurrenceRule.monthly(),
            ),
            Instant.parse("2024-02-28T10:00:00Z"),
        )

        assertEquals(LocalDate.of(2024, 3, 31), schedules.single().occurrenceDate)
        assertEquals(Instant.parse("2024-03-30T09:00:00Z"), schedules.single().targetInstant)
    }

    @Test
    fun `elapsed reminder offset advances independently to a later occurrence`() {
        val schedules = policy.schedulesFor(
            event(
                occurrence = LocalDate.of(2024, 2, 10),
                offsets = setOf(ReminderOffset.SameDay, ReminderOffset.SevenDaysBefore),
                recurrenceRule = RecurrenceRule.monthly(),
            ),
            Instant.parse("2024-02-05T12:00:00Z"),
        )

        assertEquals(
            setOf(LocalDate.of(2024, 2, 10), LocalDate.of(2024, 3, 10)),
            schedules.map(ScheduledReminder::occurrenceDate).toSet(),
        )
    }

    @Test
    fun `all event types are supported`() {
        RecurringEventType.entries.forEach { type ->
            val schedules = policy.schedulesFor(event(type = type), now)
            assertEquals(type.name, schedules.single().eventType)
        }
    }

    @Test
    fun `work identities are stable and unique by event occurrence and offset`() {
        val first = policy.schedulesFor(event(offsets = setOf(ReminderOffset.SameDay)), now).single()
        val same = policy.schedulesFor(event(offsets = setOf(ReminderOffset.SameDay)), now).single()
        val otherOffset = policy.schedulesFor(event(offsets = setOf(ReminderOffset.OneDayBefore)), now).single()
        val otherOccurrence = policy.schedulesFor(
            event(occurrence = LocalDate.of(2024, 2, 11), offsets = setOf(ReminderOffset.SameDay)),
            now,
        ).single()

        assertEquals(first.uniqueWorkName, same.uniqueWorkName)
        assertNotEquals(first.uniqueWorkName, otherOffset.uniqueWorkName)
        assertNotEquals(first.uniqueWorkName, otherOccurrence.uniqueWorkName)
    }

    private fun event(
        occurrence: LocalDate = LocalDate.of(2024, 2, 10),
        zone: String = "UTC",
        time: LocalTime = LocalTime.of(9, 0),
        offsets: Set<ReminderOffset> = setOf(ReminderOffset.SameDay),
        enabled: Boolean = true,
        remindersEnabled: Boolean = true,
        type: RecurringEventType = RecurringEventType.BILLING,
        recurrenceRule: RecurrenceRule = RecurrenceRule.OneTime,
    ) = RecurringEvent(
        id = EVENT_ID,
        subscriptionId = SUBSCRIPTION_ID,
        type = type,
        title = "Upcoming event",
        nextOccurrence = occurrence,
        recurrenceRule = recurrenceRule,
        timezone = ZoneId.of(zone),
        enabled = enabled,
        reminders = ReminderSettings(remindersEnabled, offsets, time),
        createdAt = now,
        updatedAt = now,
    )

    companion object {
        private val EVENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001")
        private val SUBSCRIPTION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001")
    }
}
