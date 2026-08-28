package com.subscriptiontracker.domain.reminder

import com.subscriptiontracker.domain.model.ReminderOffset
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReminderReconciliationPlannerTest {
    private val planner = ReminderReconciliationPlanner()

    @Test
    fun `plan keeps desired schedules and marks only registered extras stale`() {
        val desired = listOf(reminder("reminder:a"), reminder("reminder:b"), reminder("reminder:a"))

        val plan = planner.plan(desired, setOf("reminder:b", "reminder:old"))

        assertEquals(listOf("reminder:a", "reminder:b"), plan.desired.map(ScheduledReminder::uniqueWorkName))
        assertEquals(setOf("reminder:old"), plan.staleWorkNames)
    }

    @Test
    fun `empty desired set makes every registered work stale`() {
        val plan = planner.plan(emptyList(), setOf("reminder:a", "reminder:b"))

        assertEquals(emptyList<ScheduledReminder>(), plan.desired)
        assertEquals(setOf("reminder:a", "reminder:b"), plan.staleWorkNames)
    }

    private fun reminder(name: String) = ScheduledReminder(
        id = name.removePrefix("reminder:"),
        uniqueWorkName = name,
        eventId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        subscriptionId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
        eventType = "BILLING",
        occurrenceDate = LocalDate.of(2024, 2, 10),
        offset = ReminderOffset.SameDay,
        targetInstant = Instant.parse("2024-02-10T09:00:00Z"),
    )
}
