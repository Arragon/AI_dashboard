package com.subscriptiontracker.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscriptions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["nextBillingDate"]),
    ],
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
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
    val tagsJson: String,
    val plan: String?,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String?,
)
