package com.subscriptiontracker.domain.service

import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.TreeMap

/**
 * Pure spend projections. Every result is separated by [Currency]; this service never converts or
 * combines currencies.
 *
 * Date ranges are inclusive. A calendar-month projection covers the whole month containing
 * [asOfDate], while "next 30 days" means today plus the following 29 dates. Window projections use
 * enabled BILLING events as the source of truth. Paused, archived, expired, and cancelled-pending-
 * expiry subscriptions do not produce projected charges. A charge on an expiration date is also
 * excluded because service access ends at the start of that boundary date.
 */
class SpendCalculationService(
    private val recurrenceEngine: RecurrenceEngine = RecurrenceEngine(),
) {
    fun spendForCurrentCalendarMonth(
        subscriptions: Collection<Subscription>,
        events: Collection<RecurringEvent>,
        asOfDate: LocalDate,
    ): Map<Currency, BigDecimal> {
        val month = YearMonth.from(asOfDate)
        return spendBetween(subscriptions, events, month.atDay(1), month.atEndOfMonth())
    }

    fun currentCalendarMonthSpend(
        subscriptions: Collection<Subscription>,
        events: Collection<RecurringEvent>,
        asOfDate: LocalDate,
    ): Map<Currency, BigDecimal> = spendForCurrentCalendarMonth(subscriptions, events, asOfDate)

    fun spendForNext30Days(
        subscriptions: Collection<Subscription>,
        events: Collection<RecurringEvent>,
        asOfDate: LocalDate,
    ): Map<Currency, BigDecimal> = spendBetween(subscriptions, events, asOfDate, asOfDate.plusDays(29))

    fun next30DaysSpend(
        subscriptions: Collection<Subscription>,
        events: Collection<RecurringEvent>,
        asOfDate: LocalDate,
    ): Map<Currency, BigDecimal> = spendForNext30Days(subscriptions, events, asOfDate)

    /**
     * Normalizes active auto-renewing subscriptions to a 365-day/52-week/12-month/one-year basis.
     * One-time subscriptions (no billing interval) are intentionally omitted. For intervals that
     * produce a non-terminating decimal, DECIMAL128 supplies an explicit deterministic precision.
     */
    fun annualizedRecurringSpend(
        subscriptions: Collection<Subscription>,
        asOfDate: LocalDate,
    ): Map<Currency, BigDecimal> = accumulate(
        subscriptions.asSequence()
            .filter { it.status == SubscriptionStatus.ACTIVE && it.autoRenew && hasAccessOn(it, asOfDate) }
            .mapNotNull { subscription ->
                val unit = subscription.billingInterval ?: return@mapNotNull null
                val count = subscription.billingIntervalCount ?: return@mapNotNull null
                subscription.currency to annualizedAmount(subscription.price, unit, count)
            },
    )

    /** Pending cancellation counts as active access until (but not including) its expiry date. */
    fun activeSubscriptionCount(
        subscriptions: Collection<Subscription>,
        asOfDate: LocalDate,
    ): Int = subscriptions.count { hasActiveAccess(it, asOfDate) }

    fun categorySubtotal(
        subscriptions: Collection<Subscription>,
        events: Collection<RecurringEvent>,
        category: String,
        startDate: LocalDate,
        endDateInclusive: LocalDate,
    ): Map<Currency, BigDecimal> = spendBetween(
        subscriptions.filter { it.category.equals(category, ignoreCase = true) },
        events,
        startDate,
        endDateInclusive,
    )

    fun currentMonthCategorySubtotal(
        subscriptions: Collection<Subscription>,
        events: Collection<RecurringEvent>,
        category: String,
        asOfDate: LocalDate,
    ): Map<Currency, BigDecimal> {
        val month = YearMonth.from(asOfDate)
        return categorySubtotal(subscriptions, events, category, month.atDay(1), month.atEndOfMonth())
    }

    fun spendBetween(
        subscriptions: Collection<Subscription>,
        events: Collection<RecurringEvent>,
        startDate: LocalDate,
        endDateInclusive: LocalDate,
    ): Map<Currency, BigDecimal> {
        require(endDateInclusive >= startDate) { "Spend range end must not precede start" }
        val subscriptionsById = subscriptions.associateBy { it.id }
        val amounts = mutableListOf<Pair<Currency, BigDecimal>>()
        events.asSequence()
            .filter { it.enabled && it.type == RecurringEventType.BILLING }
            .forEach { event ->
                val subscription = subscriptionsById[event.subscriptionId] ?: return@forEach
                if (!projectsCharges(subscription)) return@forEach
                occurrences(event, startDate, endDateInclusive).forEach { occurrence ->
                    if (occurrence >= subscription.startDate && hasAccessOn(subscription, occurrence)) {
                        amounts += subscription.currency to subscription.price
                    }
                }
            }
        return accumulate(amounts.asSequence())
    }

    private fun occurrences(
        event: RecurringEvent,
        startDate: LocalDate,
        endDateInclusive: LocalDate,
    ): Sequence<LocalDate> = sequence {
        var occurrence = recurrenceEngine.occurrenceOnOrAfter(
            event.nextOccurrence,
            event.recurrenceRule,
            startDate,
        )
        while (occurrence != null && occurrence <= endDateInclusive) {
            yield(occurrence)
            occurrence = recurrenceEngine.occurrenceAfter(
                event.nextOccurrence,
                event.recurrenceRule,
                occurrence,
            )
        }
    }

    private fun projectsCharges(subscription: Subscription): Boolean =
        subscription.status == SubscriptionStatus.ACTIVE &&
            (subscription.autoRenew || subscription.billingInterval == null)

    private fun hasActiveAccess(subscription: Subscription, date: LocalDate): Boolean =
        subscription.status in setOf(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.CANCELLED_PENDING_EXPIRY,
        ) && hasAccessOn(subscription, date)

    private fun hasAccessOn(subscription: Subscription, date: LocalDate): Boolean =
        subscription.expirationDate?.let { date < it } ?: true

    private fun annualizedAmount(
        price: BigDecimal,
        unit: RecurrenceUnit,
        intervalCount: Int,
    ): BigDecimal {
        val periodsPerYear = when (unit) {
            RecurrenceUnit.DAYS -> BigDecimal("365")
            RecurrenceUnit.WEEKS -> BigDecimal("52")
            RecurrenceUnit.MONTHS -> BigDecimal("12")
            RecurrenceUnit.YEARS -> BigDecimal.ONE
        }
        return price.multiply(periodsPerYear).divide(BigDecimal(intervalCount), MathContext.DECIMAL128)
    }

    private fun accumulate(amounts: Sequence<Pair<Currency, BigDecimal>>): Map<Currency, BigDecimal> {
        val totals = TreeMap<Currency, BigDecimal>(compareBy { it.currencyCode })
        amounts.forEach { (currency, amount) ->
            totals[currency] = totals.getOrDefault(currency, BigDecimal.ZERO).add(amount)
        }
        return totals.toMap()
    }
}
