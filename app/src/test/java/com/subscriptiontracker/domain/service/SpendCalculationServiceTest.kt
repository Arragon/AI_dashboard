package com.subscriptiontracker.domain.service

import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency
import java.util.UUID

class SpendCalculationServiceTest {
    private val service = SpendCalculationService()
    private val usd = Currency.getInstance("USD")
    private val eur = Currency.getInstance("EUR")

    @Test
    fun `current month uses billing events and never combines currencies`() {
        val asOf = LocalDate.of(2025, 1, 20)
        val usdSubscription = subscription("USD", "10.25")
        val eurSubscription = subscription("EUR", "7.50")
        val subscriptions = listOf(usdSubscription, eurSubscription)

        val result = service.spendForCurrentCalendarMonth(
            subscriptions,
            listOf(
                billing(usdSubscription, LocalDate.of(2025, 1, 1), RecurrenceRule.weekly()),
                billing(eurSubscription, LocalDate.of(2025, 1, 31), RecurrenceRule.OneTime),
            ),
            asOf,
        )

        assertMoney("51.25", result.getValue(usd))
        assertMoney("7.50", result.getValue(eur))
        assertEquals(setOf(usd, eur), result.keys)
    }

    @Test
    fun `calendar month and next thirty days have explicit inclusive boundaries`() {
        val candidate = subscription("USD", "3.00")
        val events = listOf(
            billing(candidate, LocalDate.of(2025, 2, 1), RecurrenceRule.daily()),
        )

        val february = service.spendForCurrentCalendarMonth(
            listOf(candidate),
            events,
            LocalDate.of(2025, 2, 28),
        )
        val nextThirty = service.spendForNext30Days(
            listOf(candidate),
            events,
            LocalDate.of(2025, 2, 28),
        )

        assertMoney("84.00", february.getValue(usd))
        assertMoney("90.00", nextThirty.getValue(usd))
    }

    @Test
    fun `month-end recurrence integrates leap clamping and anchor recovery`() {
        val candidate = subscription("USD", "12.00")
        val event = billing(candidate, LocalDate.of(2024, 1, 31), RecurrenceRule.monthly())

        val february = service.spendForCurrentCalendarMonth(
            listOf(candidate), listOf(event), LocalDate.of(2024, 2, 10),
        )
        val march = service.spendForCurrentCalendarMonth(
            listOf(candidate), listOf(event), LocalDate.of(2024, 3, 10),
        )

        assertMoney("12.00", february.getValue(usd))
        assertMoney("12.00", march.getValue(usd))
    }

    @Test
    fun `disabled and non-billing events do not contribute`() {
        val candidate = subscription("USD", "9.00")
        val date = LocalDate.of(2025, 4, 10)
        val disabled = billing(candidate, date, RecurrenceRule.OneTime).copy(enabled = false)
        val custom = billing(candidate, date, RecurrenceRule.OneTime).copy(type = RecurringEventType.CUSTOM)

        assertEquals(emptyMap<Currency, BigDecimal>(), service.spendForCurrentCalendarMonth(listOf(candidate), listOf(disabled, custom), date))
    }

    @Test
    fun `one-time can charge once while non-chargeable lifecycle states are excluded`() {
        val date = LocalDate.of(2025, 5, 5)
        val activeOneTime = subscription("USD", "30.00", interval = null, intervalCount = null, autoRenew = false)
        val pending = subscription("USD", "40.00", status = SubscriptionStatus.CANCELLED_PENDING_EXPIRY, expiration = date.plusDays(10), autoRenew = false)
        val paused = subscription("USD", "50.00", status = SubscriptionStatus.PAUSED)
        val expired = subscription("USD", "60.00", status = SubscriptionStatus.EXPIRED)
        val archived = subscription("USD", "70.00", status = SubscriptionStatus.ARCHIVED)
        val subscriptions = listOf(activeOneTime, pending, paused, expired, archived)

        val result = service.spendBetween(
            subscriptions,
            subscriptions.map { billing(it, date, RecurrenceRule.OneTime) },
            date,
            date,
        )

        assertMoney("30.00", result.getValue(usd))
    }

    @Test
    fun `expiration boundary charge is excluded`() {
        val expiry = LocalDate.of(2025, 6, 15)
        val candidate = subscription("USD", "11.00", expiration = expiry)
        val result = service.spendBetween(
            listOf(candidate),
            listOf(billing(candidate, expiry.minusDays(1), RecurrenceRule.daily())),
            expiry.minusDays(1),
            expiry,
        )

        assertMoney("11.00", result.getValue(usd))
    }

    @Test
    fun `annualized recurring spend uses exact decimal arithmetic and omits one-time and cancelled`() {
        val monthly = subscription("USD", "19.99")
        val quarterly = subscription("USD", "30.00", intervalCount = 3)
        val yearlyEur = subscription("EUR", "120.00", interval = RecurrenceUnit.YEARS)
        val oneTime = subscription("USD", "500.00", interval = null, intervalCount = null)
        val cancelled = subscription("USD", "99.00", status = SubscriptionStatus.CANCELLED_PENDING_EXPIRY, autoRenew = false)

        val result = service.annualizedRecurringSpend(
            listOf(monthly, quarterly, yearlyEur, oneTime, cancelled),
            LocalDate.of(2025, 1, 1),
        )

        assertMoney("359.88", result.getValue(usd))
        assertMoney("120.00", result.getValue(eur))
    }

    @Test
    fun `active count includes pending access before but not on expiry boundary`() {
        val expiry = LocalDate.of(2025, 8, 1)
        val active = subscription("USD", "1")
        val pending = subscription("USD", "1", status = SubscriptionStatus.CANCELLED_PENDING_EXPIRY, expiration = expiry)
        val paused = subscription("USD", "1", status = SubscriptionStatus.PAUSED)

        assertEquals(2, service.activeSubscriptionCount(listOf(active, pending, paused), expiry.minusDays(1)))
        assertEquals(1, service.activeSubscriptionCount(listOf(active, pending, paused), expiry))
    }

    @Test
    fun `category subtotal is case insensitive and remains currency grouped`() {
        val date = LocalDate.of(2025, 9, 10)
        val softwareUsd = subscription("USD", "8.00", category = "Software")
        val softwareEur = subscription("EUR", "6.00", category = "software")
        val media = subscription("USD", "20.00", category = "Media")
        val all = listOf(softwareUsd, softwareEur, media)

        val result = service.categorySubtotal(
            all,
            all.map { billing(it, date, RecurrenceRule.OneTime) },
            "SOFTWARE",
            date,
            date,
        )

        assertMoney("8.00", result.getValue(usd))
        assertMoney("6.00", result.getValue(eur))
    }

    @Test
    fun `invalid spend range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.spendBetween(emptyList(), emptyList(), LocalDate.of(2025, 2, 2), LocalDate.of(2025, 2, 1))
        }
    }

    private fun subscription(
        currency: String,
        price: String,
        category: String = "Software",
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        interval: RecurrenceUnit? = RecurrenceUnit.MONTHS,
        intervalCount: Int? = if (interval == null) null else 1,
        expiration: LocalDate? = null,
        autoRenew: Boolean = true,
    ): Subscription {
        val id = UUID.randomUUID()
        return Subscription(
            id = id,
            name = "Subscription-$id",
            provider = "Provider",
            category = category,
            status = status,
            price = BigDecimal(price),
            currency = Currency.getInstance(currency),
            billingInterval = interval,
            billingIntervalCount = intervalCount,
            autoRenew = autoRenew,
            startDate = LocalDate.of(2020, 1, 1),
            expirationDate = expiration,
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
            archivedAt = if (status == SubscriptionStatus.ARCHIVED) Instant.parse("2025-01-01T00:00:00Z") else null,
        )
    }

    private fun billing(
        subscription: Subscription,
        date: LocalDate,
        rule: RecurrenceRule,
    ): RecurringEvent = RecurringEvent(
        id = UUID.randomUUID(),
        subscriptionId = subscription.id,
        type = RecurringEventType.BILLING,
        title = "Billing",
        nextOccurrence = date,
        recurrenceRule = rule,
        timezone = ZoneOffset.UTC,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
        assertFalse(actual.toString().contains("E"))
    }
}
