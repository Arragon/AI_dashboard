package com.subscriptiontracker

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.subscriptiontracker.data.backup.BackupCodec
import com.subscriptiontracker.data.backup.BackupContent
import com.subscriptiontracker.data.backup.BackupDecodeResult
import com.subscriptiontracker.data.backup.BackupSettings
import com.subscriptiontracker.data.backup.ReplaceRestoreService
import com.subscriptiontracker.data.backup.RestoreResult
import com.subscriptiontracker.data.database.RoomRecordStore
import com.subscriptiontracker.data.database.SubscriptionTrackerDatabase
import com.subscriptiontracker.data.repository.RoomQuotaRepository
import com.subscriptiontracker.data.repository.RoomRecurringEventRepository
import com.subscriptiontracker.data.repository.RoomSubscriptionRepository
import com.subscriptiontracker.domain.reminder.ReminderReconciliationPlanner
import com.subscriptiontracker.domain.reminder.ReminderSchedulingPolicy
import com.subscriptiontracker.domain.repository.QuotaRepository
import com.subscriptiontracker.domain.repository.RecurringEventRepository
import com.subscriptiontracker.domain.repository.SubscriptionRepository
import com.subscriptiontracker.platform.reminder.AndroidNotificationPermissionStatusProvider
import com.subscriptiontracker.platform.reminder.NotificationAppSettingsIntentFactory
import com.subscriptiontracker.platform.reminder.NotificationPermissionStatusProvider
import com.subscriptiontracker.platform.reminder.ReminderWorkRegistry
import com.subscriptiontracker.platform.reminder.ScheduleReconciler
import com.subscriptiontracker.platform.reminder.ScheduleReconciliationWorker
import com.subscriptiontracker.platform.reminder.WorkManagerReminderScheduler
import com.subscriptiontracker.presentation.BackupGateway
import com.subscriptiontracker.presentation.ImportPreview
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId

class SubscriptionTrackerApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    lateinit var subscriptionRepository: SubscriptionRepository
        private set
    lateinit var recurringEventRepository: RecurringEventRepository
        private set
    lateinit var quotaRepository: QuotaRepository
        private set
    lateinit var recordStore: RoomRecordStore
        private set
    lateinit var backupCodec: BackupCodec
        private set
    lateinit var replaceRestoreService: ReplaceRestoreService
        private set
    lateinit var backupGateway: BackupGateway
        private set
    lateinit var scheduleReconciler: ScheduleReconciler
        private set
    lateinit var notificationPermissionStatus: NotificationPermissionStatusProvider
        private set
    lateinit var notificationSettingsIntentFactory: NotificationAppSettingsIntentFactory
        private set

    override fun onCreate() {
        super.onCreate()
        val database = SubscriptionTrackerDatabase.create(this)
        subscriptionRepository = RoomSubscriptionRepository(database.subscriptionDao())
        recurringEventRepository = RoomRecurringEventRepository(database.recurringEventDao())
        quotaRepository = RoomQuotaRepository(database.quotaDao())
        recordStore = RoomRecordStore(database.transactionDao())
        backupCodec = BackupCodec(BuildConfig.VERSION_NAME)
        replaceRestoreService = ReplaceRestoreService(backupCodec, recordStore)
        backupGateway = object : BackupGateway {
            override suspend fun export(output: OutputStream) {
                val allSubscriptions = subscriptionRepository.list()
                backupCodec.write(
                    BackupContent(
                        subscriptions = allSubscriptions,
                        events = allSubscriptions.flatMap { recurringEventRepository.listForSubscription(it.id) },
                        quotas = allSubscriptions.flatMap { quotaRepository.listForSubscription(it.id) },
                        settings = BackupSettings(defaultCurrencyCode = "USD", defaultTimezoneId = ZoneId.systemDefault().id),
                    ),
                    output,
                )
            }

            override suspend fun validate(input: InputStream): Result<ImportPreview> {
                val bytes = input.readBytes()
                return when (val decoded = backupCodec.read(ByteArrayInputStream(bytes))) {
                    is BackupDecodeResult.Success -> Result.success(
                        ImportPreview(
                            bytes,
                            "${decoded.backup.content.subscriptions.size} subscriptions, ${decoded.backup.content.events.size} events, ${decoded.backup.content.quotas.size} quotas",
                        ),
                    )
                    is BackupDecodeResult.Failure -> Result.failure(IllegalArgumentException(decoded.errors.joinToString("\n") { "${it.path}: ${it.message}" }))
                }
            }

            override suspend fun replace(preview: ImportPreview): Result<Unit> = when (
                val restored = replaceRestoreService.restore(ByteArrayInputStream(preview.bytes), confirmed = true)
            ) {
                is RestoreResult.Success -> Result.success(Unit)
                is RestoreResult.Failure -> Result.failure(IllegalArgumentException(restored.errors.joinToString("\n") { it.message }))
            }
        }
        val registry = ReminderWorkRegistry(this)
        val scheduler = WorkManagerReminderScheduler(WorkManager.getInstance(this), registry)
        scheduleReconciler = ScheduleReconciler(
            subscriptionRepository = subscriptionRepository,
            recurringEventRepository = recurringEventRepository,
            policy = ReminderSchedulingPolicy(),
            planner = ReminderReconciliationPlanner(),
            scheduler = scheduler,
            registry = registry,
        )
        notificationPermissionStatus = AndroidNotificationPermissionStatusProvider(this)
        notificationSettingsIntentFactory = NotificationAppSettingsIntentFactory(this)
        ScheduleReconciliationWorker.enqueue(this)
    }
}
