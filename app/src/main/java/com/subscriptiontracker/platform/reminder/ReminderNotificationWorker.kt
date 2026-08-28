package com.subscriptiontracker.platform.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters

class ReminderNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        createChannel()
        val permissionStatus = AndroidNotificationPermissionStatusProvider(applicationContext).currentStatus()
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (permissionStatus == NotificationPermissionStatus.DENIED || !notificationManager.areNotificationsEnabled()) {
            return Result.failure(Data.Builder().putString(KEY_FAILURE_REASON, FAILURE_PERMISSION_DENIED).build())
        }

        val reminderId = inputData.getString(KEY_REMINDER_ID)
            ?: return Result.failure(Data.Builder().putString(KEY_FAILURE_REASON, FAILURE_INVALID_INPUT).build())
        val occurrenceDate = inputData.getString(KEY_OCCURRENCE_DATE)
            ?: return Result.failure(Data.Builder().putString(KEY_FAILURE_REASON, FAILURE_INVALID_INPUT).build())
        val eventType = inputData.getString(KEY_EVENT_TYPE)
            ?.lowercase()
            ?.replace('_', ' ')
            ?: return Result.failure(Data.Builder().putString(KEY_FAILURE_REASON, FAILURE_INVALID_INPUT).build())
        val subscriptionName = inputData.getString(KEY_SUBSCRIPTION_NAME).orEmpty()
        val title = subscriptionName.ifBlank { "Subscription reminder" }
        val content = "$eventType on $occurrenceDate"
        val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                applicationContext,
                reminderId.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            notificationManager.notify(reminderId.hashCode(), notification)
            ScheduleReconciliationWorker.enqueue(applicationContext)
            Result.success()
        } catch (_: SecurityException) {
            Result.failure(Data.Builder().putString(KEY_FAILURE_REASON, FAILURE_PERMISSION_DENIED).build())
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Subscription reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Upcoming subscription event reminders"
        }
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_SUBSCRIPTION_NAME = "subscription_name"
        const val KEY_EVENT_TYPE = "event_type"
        const val KEY_OCCURRENCE_DATE = "occurrence_date"
        const val KEY_FAILURE_REASON = "failure_reason"
        const val FAILURE_PERMISSION_DENIED = "notification_permission_denied"
        const val FAILURE_INVALID_INPUT = "invalid_input"
        const val CHANNEL_ID = "subscription_reminders"
    }
}
