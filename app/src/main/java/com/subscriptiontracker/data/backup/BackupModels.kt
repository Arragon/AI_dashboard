package com.subscriptiontracker.data.backup

import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.Subscription
import kotlinx.serialization.Serializable

const val BACKUP_SCHEMA_VERSION = 1

data class BackupContent(
    val subscriptions: List<Subscription>,
    val events: List<RecurringEvent>,
    val quotas: List<Quota>,
    val settings: BackupSettings = BackupSettings(),
)

data class BackupSettings(
    val defaultCurrencyCode: String? = null,
    val defaultTimezoneId: String? = null,
)

data class ValidatedBackup(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: String,
    val content: BackupContent,
)

enum class BackupErrorCode {
    MALFORMED_JSON,
    UNSUPPORTED_SCHEMA,
    INVALID_VALUE,
    DUPLICATE_ID,
    DANGLING_REFERENCE,
    CONFIRMATION_REQUIRED,
}

data class BackupValidationError(
    val code: BackupErrorCode,
    val path: String,
    val message: String,
)

sealed interface BackupDecodeResult {
    data class Success(val backup: ValidatedBackup) : BackupDecodeResult
    data class Failure(val errors: List<BackupValidationError>) : BackupDecodeResult
}

sealed interface RestoreResult {
    data class Success(val backup: ValidatedBackup) : RestoreResult
    data class Failure(val errors: List<BackupValidationError>) : RestoreResult
}

@Serializable
internal data class BackupDocumentDto(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: String,
    val subscriptions: List<SubscriptionBackupDto>,
    val events: List<EventBackupDto>,
    val quotas: List<QuotaBackupDto>,
    val settings: SettingsBackupDto,
)

@Serializable
internal data class SettingsBackupDto(
    val defaultCurrencyCode: String?,
    val defaultTimezoneId: String?,
)

@Serializable
internal data class SubscriptionBackupDto(
    val id: String,
    val name: String,
    val provider: String,
    val category: String,
    val iconIdentifier: String?,
    val status: String,
    val price: String,
    val currencyCode: String,
    val billingInterval: String?,
    val billingIntervalCount: Int?,
    val autoRenew: Boolean,
    val startDate: String,
    val nextBillingDate: String?,
    val expirationDate: String?,
    val trialEndDate: String?,
    val notes: String,
    val tags: List<String>,
    val plan: String?,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
)

@Serializable
internal data class EventBackupDto(
    val id: String,
    val subscriptionId: String,
    val type: String,
    val title: String,
    val nextOccurrence: String,
    val recurrence: RecurrenceBackupDto,
    val timezoneId: String,
    val enabled: Boolean,
    val reminders: ReminderBackupDto,
    val notes: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal data class RecurrenceBackupDto(
    val kind: String,
    val interval: Int?,
    val unit: String?,
)

@Serializable
internal data class ReminderBackupDto(
    val enabled: Boolean,
    val offsetsDaysBefore: List<Int>,
    val notificationTime: String,
)

@Serializable
internal data class QuotaBackupDto(
    val id: String,
    val subscriptionId: String,
    val name: String,
    val unit: String,
    val used: String?,
    val remaining: String?,
    val limit: String?,
    val percentage: String?,
    val unlimited: Boolean,
    val resetEventId: String?,
    val warningThresholdPercentage: String?,
    val updatedAt: String,
)
