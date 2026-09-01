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
import com.subscriptiontracker.R
import com.subscriptiontracker.domain.model.RecurringEventType

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
            ?: return Result.failure(Data.Builder().putString(KEY_FAILURE_REASON, FAILURE_INVALID_INPUT).build())
        val subscriptionName = inputData.getString(KEY_SUBSCRIPTION_NAME).orEmpty()
        val title = subscriptionName.ifBlank { applicationContext.getString(R.string.notification_default_title) }
        val eventTypeLabel = localizedEventType(eventType)
        val content = applicationContext.getString(R.string.notification_content_format, eventTypeLabel, occurrenceDate)
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
            applicationContext.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = applicationContext.getString(R.string.notification_channel_description)
        }
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun localizedEventType(raw: String): String = try {
        applicationContext.getString(
            when (RecurringEventType.valueOf(raw.uppercase())) {
                RecurringEventType.BILLING -> R.string.event_type_billing
                RecurringEventType.EXPIRATION -> R.string.event_type_expiration
                RecurringEventType.TRIAL_END -> R.string.event_type_trial_end
                RecurringEventType.QUOTA_RESET -> R.string.event_type_quota_reset
                RecurringEventType.CUSTOM -> R.string.event_type_custom
                RecurringEventType.PRICE_CHANGE -> R.string.event_type_price_change
                RecurringEventType.PROMOTION_END -> R.string.event_type_promotion_end
                RecurringEventType.CONTRACT_NOTICE -> R.string.event_type_contract_notice
            },
        )
    } catch (_: IllegalArgumentException) {
        applicationContext.getString(R.string.event_type_custom)
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
