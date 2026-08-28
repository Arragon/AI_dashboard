package com.subscriptiontracker.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_events",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscriptionId"]),
        Index(value = ["nextOccurrence"]),
        Index(value = ["enabled"]),
    ],
)
data class RecurringEventEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String,
    val type: String,
    val title: String,
    val nextOccurrence: String,
    val recurrenceKind: String,
    val recurrenceInterval: Int?,
    val recurrenceUnit: String?,
    val timezoneId: String,
    val enabled: Boolean,
    val remindersEnabled: Boolean,
    val reminderOffsetsJson: String,
    val notificationTime: String,
    val notes: String,
    val createdAt: String,
    val updatedAt: String,
)
