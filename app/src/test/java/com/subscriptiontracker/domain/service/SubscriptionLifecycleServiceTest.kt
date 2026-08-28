package com.subscriptiontracker.domain.service

import com.subscriptiontracker.domain.model.RecurrenceUnit
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

class SubscriptionLifecycleServiceTest {
    private val service = SubscriptionLifecycleService()
    private val changedAt = Instant.parse("2025-01-10T12:00:00Z")

    @Test
    fun `cancellation at period end retains access and disables renewal`() {
        val endDate = LocalDate.of(2025, 2, 1)

        val result = service.cancelAtPeriodEnd(subscription(), endDate, changedAt)

        assertEquals(SubscriptionStatus.CANCELLED_PENDING_EXPIRY, result.status)
        assertFalse(result.autoRenew)
        assertEquals(endDate, result.expirationDate)
        assertEquals(changedAt, result.updatedAt)
    }

    @Test
    fun `pending cancellation may retain an unknown service end date`() {
        val result = service.cancelAtPeriodEnd(subscription().copy(expirationDate = null), null, changedAt)

        assertEquals(SubscriptionStatus.CANCELLED_PENDING_EXPIRY, result.status)
        assertEquals(null, result.expirationDate)
        assertSame(result, service.applyExpiry(result, LocalDate.of(2030, 1, 1), changedAt.plusSeconds(1)))
    }

    @Test
    fun `pending cancellation remains pending before service end`() {
        val pending = service.cancelAtPeriodEnd(subscription(), LocalDate.of(2025, 2, 1), changedAt)

        val result = service.applyExpiry(pending, LocalDate.of(2025, 1, 31), changedAt.plusSeconds(1))

        assertSame(pending, result)
    }

    @Test
    fun `pending cancellation expires on service end date`() {
        val pending = service.cancelAtPeriodEnd(subscription(), LocalDate.of(2025, 2, 1), changedAt)
        val expiryTime = changedAt.plusSeconds(1)

        val result = service.applyExpiry(pending, LocalDate.of(2025, 2, 1), expiryTime)

        assertEquals(SubscriptionStatus.EXPIRED, result.status)
        assertEquals(expiryTime, result.updatedAt)
    }

    @Test
    fun `pending cancellation expires after service end date`() {
        val pending = service.cancelAtPeriodEnd(subscription(), LocalDate.of(2024, 12, 31), changedAt)

        val result = service.applyExpiry(pending, LocalDate.of(2025, 1, 1), changedAt.plusSeconds(1))

        assertEquals(SubscriptionStatus.EXPIRED, result.status)
    }

    @Test
    fun `immediate cancellation expires without deleting record`() {
        val original = subscription()

        val result = service.cancelImmediately(original, changedAt)

        assertEquals(original.id, result.id)
        assertEquals(SubscriptionStatus.EXPIRED, result.status)
        assertFalse(result.autoRenew)
    }

    @Test
    fun `expiry processing does not change active paused expired or archived records`() {
        val asOf = LocalDate.of(2025, 2, 2)
        listOf(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAUSED,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.ARCHIVED,
        ).forEach { status ->
            val candidate = subscription(status)
            assertSame(candidate, service.applyExpiry(candidate, asOf, changedAt.plusSeconds(1)))
        }
    }

    private fun subscription(status: SubscriptionStatus = SubscriptionStatus.ACTIVE): Subscription {
        val createdAt = Instant.parse("2024-01-01T00:00:00Z")
        return Subscription(
            id = UUID.fromString("70526979-4da7-4fd2-b330-b9a28a92535d"),
            name = "Example Pro",
            provider = "Example",
            category = "Software",
            status = status,
            price = BigDecimal("19.99"),
            currency = Currency.getInstance("USD"),
            billingInterval = RecurrenceUnit.MONTHS,
            billingIntervalCount = 1,
            startDate = LocalDate.of(2024, 1, 1),
            expirationDate = LocalDate.of(2025, 2, 1),
            createdAt = createdAt,
            updatedAt = createdAt,
            archivedAt = if (status == SubscriptionStatus.ARCHIVED) changedAt else null,
        )
    }
}
