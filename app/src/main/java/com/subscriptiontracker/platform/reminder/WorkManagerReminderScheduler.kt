package com.subscriptiontracker.platform.reminder

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.subscriptiontracker.domain.reminder.ReminderReconciliationPlan
import java.time.Clock
import java.util.concurrent.TimeUnit

class WorkManagerReminderScheduler(
    private val workManager: WorkManager,
    private val registry: ReminderWorkRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun apply(plan: ReminderReconciliationPlan, subscriptionNames: Map<String, String>) {
        plan.staleWorkNames.forEach(workManager::cancelUniqueWork)

        val enqueuedWorkNames = mutableSetOf<String>()
        plan.desired.forEach { reminder ->
            val delayMillis = reminder.targetInstant.toEpochMilli() - clock.millis()
            if (delayMillis <= 0L) {
                workManager.cancelUniqueWork(reminder.uniqueWorkName)
                return@forEach
            }

            val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build(),
                )
                .setInputData(
                    Data.Builder()
                        .putString(ReminderNotificationWorker.KEY_REMINDER_ID, reminder.id)
                        .putString(ReminderNotificationWorker.KEY_SUBSCRIPTION_NAME, subscriptionNames[reminder.subscriptionId.toString()].orEmpty())
                        .putString(ReminderNotificationWorker.KEY_EVENT_TYPE, reminder.eventType)
                        .putString(ReminderNotificationWorker.KEY_OCCURRENCE_DATE, reminder.occurrenceDate.toString())
                        .build(),
                )
                .addTag(REMINDER_WORK_TAG)
                .build()

            // REPLACE is required because timezone or notification-time edits retain the logical identity.
            workManager.enqueueUniqueWork(reminder.uniqueWorkName, ExistingWorkPolicy.REPLACE, request)
            enqueuedWorkNames += reminder.uniqueWorkName
        }
        registry.replace(enqueuedWorkNames)
    }

    companion object {
        const val REMINDER_WORK_TAG = "scheduled-reminder"
    }
}
