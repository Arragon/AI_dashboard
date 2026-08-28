package com.subscriptiontracker.domain.query

import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import com.subscriptiontracker.domain.service.RecurrenceEngine
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import java.util.UUID

/** ACTIVE includes pending cancellation while access remains; the expiry date itself is inactive. */
enum class ActivityFilter {
    ALL,
    ACTIVE,
    INACTIVE,
}

enum class SubscriptionSort {
    NEXT_EVENT,
    PRICE,
    NAME,
    RECENTLY_ADDED,
}

data class SubscriptionFilters(
    val activity: ActivityFilter = ActivityFilter.ALL,
    val dueSoon: Boolean = false,
    val dueSoonDays: Int = DEFAULT_DUE_SOON_DAYS,
    val category: String? = null,
    val currency: Currency? = null,
) {
    init {
        require(dueSoonDays >= 0) { "Due-soon days cannot be negative" }
    }

    companion object {
        const val DEFAULT_DUE_SOON_DAYS: Int = 7
    }
}

data class SubscriptionQuery(
    val searchText: String = "",
    val filters: SubscriptionFilters = SubscriptionFilters(),
    val sort: SubscriptionSort = SubscriptionSort.NAME,
)

/** Pure in-memory search/filter/sort behavior shared by any future UI or persistence adapter. */
class SubscriptionQueryService(
    private val recurrenceEngine: RecurrenceEngine = RecurrenceEngine(),
) {
    fun execute(
        subscriptions: Collection<Subscription>,
        events: Collection<RecurringEvent>,
        query: SubscriptionQuery = SubscriptionQuery(),
        asOfDate: LocalDate,
    ): List<Subscription> {
        val nextEvents = nextEnabledEvents(subscriptions, events, asOfDate)
        val dueThrough = asOfDate.plusDays(query.filters.dueSoonDays.toLong())

        return subscriptions.asSequence()
            .filter { matchesSearch(it, query.searchText) }
            .filter { matchesActivity(it, query.filters.activity, asOfDate) }
            .filter { query.filters.category == null || it.category.equals(query.filters.category, ignoreCase = true) }
            .filter { query.filters.currency == null || it.currency == query.filters.currency }
            .filter {
                !query.filters.dueSoon || nextEvents[it.id]?.let { date ->
                    date >= asOfDate && date <= dueThrough
                } == true
            }
            .sortedWith(comparator(query.sort, nextEvents))
            .toList()
    }

    fun query(
        subscriptions: Collection<Subscription>,
        events: Collection<RecurringEvent>,
        searchText: String = "",
        filters: SubscriptionFilters = SubscriptionFilters(),
        sort: SubscriptionSort = SubscriptionSort.NAME,
        asOfDate: LocalDate,
    ): List<Subscription> = execute(
        subscriptions,
        events,
        SubscriptionQuery(searchText, filters, sort),
        asOfDate,
    )

    /** Returns the next on-or-after date from every enabled first-class event, not only billing. */
    fun nextEnabledEventDate(
        subscriptionId: UUID,
        events: Collection<RecurringEvent>,
        asOfDate: LocalDate,
    ): LocalDate? = events.asSequence()
        .filter { it.subscriptionId == subscriptionId && it.enabled }
        .mapNotNull {
            recurrenceEngine.occurrenceOnOrAfter(it.nextOccurrence, it.recurrenceRule, asOfDate)
        }
        .minOrNull()

    private fun nextEnabledEvents(
        subscriptions: Collection<Subscription>,
        events: Collection<RecurringEvent>,
        asOfDate: LocalDate,
    ): Map<UUID, LocalDate> {
        val knownIds = subscriptions.mapTo(hashSetOf()) { it.id }
        return events.asSequence()
            .filter { it.enabled && it.subscriptionId in knownIds }
            .mapNotNull { event ->
                recurrenceEngine.occurrenceOnOrAfter(
                    event.nextOccurrence,
                    event.recurrenceRule,
                    asOfDate,
                )?.let { event.subscriptionId to it }
            }
            .groupingBy { it.first }
            .reduce { _, earliest, candidate -> if (candidate.second < earliest.second) candidate else earliest }
            .mapValues { it.value.second }
    }

    private fun matchesSearch(subscription: Subscription, searchText: String): Boolean {
        val term = searchText.trim()
        if (term.isEmpty()) return true
        return subscription.name.contains(term, ignoreCase = true) ||
            subscription.provider.contains(term, ignoreCase = true) ||
            subscription.category.contains(term, ignoreCase = true) ||
            subscription.notes.contains(term, ignoreCase = true)
    }

    private fun matchesActivity(
        subscription: Subscription,
        filter: ActivityFilter,
        asOfDate: LocalDate,
    ): Boolean = when (filter) {
        ActivityFilter.ALL -> true
        ActivityFilter.ACTIVE -> hasActiveAccess(subscription, asOfDate)
        ActivityFilter.INACTIVE -> !hasActiveAccess(subscription, asOfDate)
    }

    private fun hasActiveAccess(subscription: Subscription, date: LocalDate): Boolean =
        subscription.status in setOf(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.CANCELLED_PENDING_EXPIRY,
        ) && subscription.expirationDate?.let { date < it } != false

    private fun comparator(
        sort: SubscriptionSort,
        nextEvents: Map<UUID, LocalDate>,
    ): Comparator<Subscription> {
        val nameThenId = compareBy<Subscription>(
            { it.name.lowercase(Locale.ROOT) },
            { it.name },
            { it.id.toString() },
        )
        return when (sort) {
            SubscriptionSort.NAME -> nameThenId
            SubscriptionSort.NEXT_EVENT -> compareBy<Subscription>(
                { nextEvents[it.id] == null },
                { nextEvents[it.id] ?: LocalDate.MAX },
                { it.name.lowercase(Locale.ROOT) },
                { it.id.toString() },
            )
            SubscriptionSort.PRICE -> compareBy<Subscription>(
                { it.price },
                { it.currency.currencyCode },
                { it.name.lowercase(Locale.ROOT) },
                { it.id.toString() },
            )
            SubscriptionSort.RECENTLY_ADDED -> compareByDescending<Subscription> { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id.toString() }
        }
    }
}
