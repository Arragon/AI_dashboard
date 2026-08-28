package com.subscriptiontracker.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

enum class SubscriptionStatus {
    ACTIVE,
    CANCELLED_PENDING_EXPIRY,
    EXPIRED,
    PAUSED,
    ARCHIVED,
}

data class Subscription(
    val id: UUID,
    val name: String,
    val provider: String,
    val category: String,
    val iconIdentifier: String? = null,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val price: BigDecimal,
    val currency: Currency,
    val billingInterval: RecurrenceUnit? = null,
    val billingIntervalCount: Int? = null,
    val autoRenew: Boolean = true,
    val startDate: LocalDate,
    val nextBillingDate: LocalDate? = null,
    val expirationDate: LocalDate? = null,
    val trialEndDate: LocalDate? = null,
    val notes: String = "",
    val tags: Set<String> = emptySet(),
    val plan: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
) {
    init {
        require(name.isNotBlank()) { "Subscription name cannot be blank" }
        require(category.isNotBlank()) { "Subscription category cannot be blank" }
        require(price >= BigDecimal.ZERO) { "Subscription price cannot be negative" }
        require((billingInterval == null) == (billingIntervalCount == null)) {
            "Billing interval and count must either both be set or both be absent"
        }
        require(billingIntervalCount == null || billingIntervalCount > 0) {
            "Billing interval count must be positive"
        }
        require(status == SubscriptionStatus.ARCHIVED || archivedAt == null) {
            "Only archived subscriptions can have archivedAt"
        }
    }
}
