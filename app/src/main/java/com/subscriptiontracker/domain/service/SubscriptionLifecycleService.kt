package com.subscriptiontracker.domain.service

import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import java.time.Instant
import java.time.LocalDate

class SubscriptionLifecycleService {
    fun cancelAtPeriodEnd(
        subscription: Subscription,
        serviceEndDate: LocalDate?,
        changedAt: Instant,
    ): Subscription = subscription.copy(
        status = SubscriptionStatus.CANCELLED_PENDING_EXPIRY,
        autoRenew = false,
        expirationDate = serviceEndDate ?: subscription.expirationDate,
        updatedAt = changedAt,
    )

    fun cancelImmediately(
        subscription: Subscription,
        changedAt: Instant,
    ): Subscription = subscription.copy(
        status = SubscriptionStatus.EXPIRED,
        autoRenew = false,
        updatedAt = changedAt,
    )

    fun applyExpiry(
        subscription: Subscription,
        asOfDate: LocalDate,
        changedAt: Instant,
    ): Subscription {
        val shouldExpire = subscription.status == SubscriptionStatus.CANCELLED_PENDING_EXPIRY &&
            subscription.expirationDate?.let { it <= asOfDate } == true
        return if (shouldExpire) {
            subscription.copy(
                status = SubscriptionStatus.EXPIRED,
                updatedAt = changedAt,
            )
        } else {
            subscription
        }
    }
}
