package com.subscriptiontracker.platform.reminder

import com.subscriptiontracker.domain.model.SubscriptionStatus
import com.subscriptiontracker.domain.reminder.ReminderReconciliationPlanner
import com.subscriptiontracker.domain.reminder.ReminderSchedulingPolicy
import com.subscriptiontracker.domain.repository.RecurringEventRepository
import com.subscriptiontracker.domain.repository.SubscriptionRepository
import java.time.Clock

class ScheduleReconciler(
    private val subscriptionRepository: SubscriptionRepository,
    private val recurringEventRepository: RecurringEventRepository,
    private val policy: ReminderSchedulingPolicy,
    private val planner: ReminderReconciliationPlanner,
    private val scheduler: WorkManagerReminderScheduler,
    private val registry: ReminderWorkRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun reconcile() {
        val subscriptions = subscriptionRepository.list()
            .filter { it.status != SubscriptionStatus.ARCHIVED }
        val names = subscriptions.associate { it.id.toString() to it.name }
        val now = clock.instant()
        val desired = subscriptions.flatMap { subscription ->
            recurringEventRepository.listForSubscription(subscription.id)
                .flatMap { event -> policy.schedulesFor(event, now) }
        }
        val plan = planner.plan(desired, registry.registeredWorkNames())
        scheduler.apply(plan, names)
    }
}
