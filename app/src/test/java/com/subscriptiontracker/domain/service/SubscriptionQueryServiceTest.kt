package com.subscriptiontracker.domain.query

import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency
import java.util.UUID

class SubscriptionQueryServiceTest {
    private val service = SubscriptionQueryService()
    private val asOf = LocalDate.of(2025, 1, 10)

    @Test
    fun `search covers name provider category and notes case insensitively`() {
        val candidates = listOf(
            subscription("Alpha Cloud", provider = "Acme", category = "Software", notes = "Primary"),
            subscription("Movie Pass", provider = "CINEMA Co", category = "Media", notes = "Family"),
        )

        listOf("alpha", "ACME", "software", "PRIMARY").forEach { term ->
            assertEquals(listOf("Alpha Cloud"), names(service.query(candidates, emptyList(), searchText = term, asOfDate = asOf)))
        }
        assertEquals(listOf("Movie Pass"), names(service.query(candidates, emptyList(), searchText = "family", asOfDate = asOf)))
    }

    @Test
    fun `active and inactive filters respect pending expiry boundary`() {
        val active = subscription("Active")
        val pending = subscription(
            "Pending",
            status = SubscriptionStatus.CANCELLED_PENDING_EXPIRY,
            expiration = asOf.plusDays(1),
        )
        val expiredPending = subscription(
            "Boundary",
            status = SubscriptionStatus.CANCELLED_PENDING_EXPIRY,
            expiration = asOf,
        )
        val paused = subscription("Paused", status = SubscriptionStatus.PAUSED)
        val all = listOf(active, pending, expiredPending, paused)

        assertEquals(
            listOf("Active", "Pending"),
            names(service.query(all, emptyList(), filters = SubscriptionFilters(activity = ActivityFilter.ACTIVE), asOfDate = asOf)),
        )
        assertEquals(
            listOf("Boundary", "Paused"),
            names(service.query(all, emptyList(), filters = SubscriptionFilters(activity = ActivityFilter.INACTIVE), asOfDate = asOf)),
        )
    }

    @Test
    fun `due soon defaults to seven days and uses next enabled event of any type`() {
        val today = subscription("Today")
        val boundary = subscription("Boundary")
        val later = subscription("Later")
        val disabled = subscription("Disabled")
        val events = listOf(
            event(today, asOf, RecurrenceRule.OneTime),
            event(boundary, asOf.plusDays(7), RecurrenceRule.OneTime, RecurringEventType.TRIAL_END),
            event(later, asOf.plusDays(8), RecurrenceRule.OneTime),
            event(disabled, asOf.plusDays(1), RecurrenceRule.OneTime, enabled = false),
        )

        val result = service.query(
            listOf(today, boundary, later, disabled),
            events,
            filters = SubscriptionFilters(dueSoon = true),
            asOfDate = asOf,
        )

        assertEquals(listOf("Boundary", "Today"), names(result))
    }

    @Test
    fun `past repeating event is advanced by recurrence engine for due soon`() {
        val leap = subscription("Leap")
        val event = event(leap, LocalDate.of(2024, 2, 29), RecurrenceRule.yearly())

        val result = service.query(
            listOf(leap),
            listOf(event),
            filters = SubscriptionFilters(dueSoon = true, dueSoonDays = 50),
            asOfDate = LocalDate.of(2025, 1, 10),
        )

        assertEquals(listOf("Leap"), names(result))
        assertEquals(LocalDate.of(2025, 2, 28), service.nextEnabledEventDate(leap.id, listOf(event), asOf))
    }

    @Test
    fun `category currency activity due soon and search filters compose`() {
        val match = subscription("Cloud Pro", category = "Software", currency = "USD")
        val wrongCurrency = subscription("Cloud Euro", category = "Software", currency = "EUR")
        val inactive = subscription("Cloud Paused", category = "Software", currency = "USD", status = SubscriptionStatus.PAUSED)
        val wrongCategory = subscription("Cloud News", category = "Media", currency = "USD")
        val all = listOf(match, wrongCurrency, inactive, wrongCategory)
        val events = all.map { event(it, asOf.plusDays(2), RecurrenceRule.OneTime) }

        val result = service.execute(
            all,
            events,
            SubscriptionQuery(
                searchText = "cloud",
                filters = SubscriptionFilters(
                    activity = ActivityFilter.ACTIVE,
                    dueSoon = true,
                    category = "software",
                    currency = Currency.getInstance("USD"),
                ),
                sort = SubscriptionSort.NAME,
            ),
            asOf,
        )

        assertEquals(listOf("Cloud Pro"), names(result))
    }

    @Test
    fun `next event sorting advances recurrence and puts missing events last`() {
        val later = subscription("Later")
        val sooner = subscription("Sooner")
        val none = subscription("None")
        val events = listOf(
            event(later, asOf.plusDays(5), RecurrenceRule.OneTime),
            event(sooner, asOf.minusMonths(1), RecurrenceRule.monthly()),
        )

        val result = service.query(
            listOf(later, none, sooner),
            events,
            sort = SubscriptionSort.NEXT_EVENT,
            asOfDate = asOf,
        )

        assertEquals(listOf("Sooner", "Later", "None"), names(result))
    }

    @Test
    fun `price name and recently added sorts have deterministic tie breakers`() {
        val olderB = subscription("Beta", price = "5.00", created = Instant.parse("2024-01-01T00:00:00Z"))
        val newer = subscription("Newest", price = "9.00", created = Instant.parse("2025-01-01T00:00:00Z"))
        val olderA = subscription("Alpha", price = "5.00", created = Instant.parse("2024-01-01T00:00:00Z"))
        val all = listOf(olderB, newer, olderA)

        assertEquals(listOf("Alpha", "Beta", "Newest"), names(service.query(all, emptyList(), sort = SubscriptionSort.PRICE, asOfDate = asOf)))
        assertEquals(listOf("Alpha", "Beta", "Newest"), names(service.query(all, emptyList(), sort = SubscriptionSort.NAME, asOfDate = asOf)))
        assertEquals(listOf("Newest", "Alpha", "Beta"), names(service.query(all, emptyList(), sort = SubscriptionSort.RECENTLY_ADDED, asOfDate = asOf)))
    }

    @Test
    fun `negative due soon window is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SubscriptionFilters(dueSoonDays = -1) }
    }

    private fun subscription(
        name: String,
        provider: String = "Provider",
        category: String = "Software",
        notes: String = "",
        currency: String = "USD",
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        expiration: LocalDate? = null,
        price: String = "10.00",
        created: Instant = Instant.parse("2024-06-01T00:00:00Z"),
    ): Subscription = Subscription(
        id = UUID.nameUUIDFromBytes(name.toByteArray()),
        name = name,
        provider = provider,
        category = category,
        status = status,
        price = BigDecimal(price),
        currency = Currency.getInstance(currency),
        startDate = LocalDate.of(2020, 1, 1),
        expirationDate = expiration,
        notes = notes,
        createdAt = created,
        updatedAt = created,
    )

    private fun event(
        subscription: Subscription,
        date: LocalDate,
        rule: RecurrenceRule,
        type: RecurringEventType = RecurringEventType.BILLING,
        enabled: Boolean = true,
    ): RecurringEvent = RecurringEvent(
        id = UUID.randomUUID(),
        subscriptionId = subscription.id,
        type = type,
        title = "Event",
        nextOccurrence = date,
        recurrenceRule = rule,
        timezone = ZoneOffset.UTC,
        enabled = enabled,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun names(subscriptions: List<Subscription>): List<String> = subscriptions.map { it.name }
}
