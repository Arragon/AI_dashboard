package com.subscriptiontracker.domain.reminder

data class ReminderReconciliationPlan(
    val desired: List<ScheduledReminder>,
    val staleWorkNames: Set<String>,
)

/** Pure set reconciliation kept separate from WorkManager and its registry. */
class ReminderReconciliationPlanner {
    fun plan(
        desired: List<ScheduledReminder>,
        registeredWorkNames: Set<String>,
    ): ReminderReconciliationPlan {
        val normalizedDesired = desired
            .distinctBy(ScheduledReminder::uniqueWorkName)
            .sortedBy(ScheduledReminder::uniqueWorkName)
        val desiredNames = normalizedDesired.mapTo(mutableSetOf(), ScheduledReminder::uniqueWorkName)
        return ReminderReconciliationPlan(
            desired = normalizedDesired,
            staleWorkNames = registeredWorkNames - desiredNames,
        )
    }
}
