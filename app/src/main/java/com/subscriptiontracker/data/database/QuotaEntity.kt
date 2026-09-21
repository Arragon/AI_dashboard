package com.subscriptiontracker.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quotas",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecurringEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["resetEventId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscriptionId"]),
        Index(value = ["resetEventId"]),
    ],
)
data class QuotaEntity(
    @PrimaryKey val id: String,
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
    val stableKey: String?,
    @ColumnInfo(defaultValue = "'MANUAL'")
    val origin: String,
    @ColumnInfo(defaultValue = "'FRESH'")
    val syncState: String,
    val syncNote: String?,
)
