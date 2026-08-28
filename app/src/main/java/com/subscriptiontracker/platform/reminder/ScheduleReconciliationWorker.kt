package com.subscriptiontracker.platform.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.subscriptiontracker.SubscriptionTrackerApplication

class ScheduleReconciliationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as SubscriptionTrackerApplication).scheduleReconciler.reconcile()
        Result.success()
    } catch (exception: Exception) {
        Log.e(TAG, "Reminder schedule reconciliation failed", exception)
        Result.retry()
    }

    companion object {
        private const val TAG = "ReminderReconciler"
        private const val UNIQUE_WORK_NAME = "reconcile-reminder-schedules"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ScheduleReconciliationWorker>().build(),
            )
        }
    }
}
