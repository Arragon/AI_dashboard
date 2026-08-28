package com.subscriptiontracker.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.subscriptiontracker.data.repository.RoomQuotaRepository
import com.subscriptiontracker.data.repository.RoomRecurringEventRepository
import com.subscriptiontracker.data.repository.RoomSubscriptionRepository
import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.ReminderOffset
import com.subscriptiontracker.domain.model.ReminderSettings
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomPersistenceTest {
    private lateinit var database: SubscriptionTrackerDatabase
    private lateinit var subscriptions: RoomSubscriptionRepository
    private lateinit var events: RoomRecurringEventRepository
    private lateinit var quotas: RoomQuotaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SubscriptionTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        subscriptions = RoomSubscriptionRepository(database.subscriptionDao())
        events = RoomRecurringEventRepository(database.recurringEventDao())
        quotas = RoomQuotaRepository(database.quotaDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun subscriptionRoundTripPreservesLosslessValuesAndTags() = runBlocking {
        val subscription = subscription(
            price = BigDecimal("1234567890.0012300"),
            tags = setOf("comma,tag", "quote\"tag", "line\nbreak", "日本語"),
            createdAt = Instant.parse("2024-01-02T03:04:05.123456789Z"),
            updatedAt = Instant.parse("2025-06-07T08:09:10.987654321Z"),
        )

        subscriptions.create(subscription)

        assertEquals(subscription, subscriptions.getById(subscription.id))
        assertEquals(listOf(subscription), subscriptions.observeAll().first())
        assertEquals("1234567890.0012300", subscriptions.getById(subscription.id)?.price?.toString())
    }

    @Test
    fun eventAndQuotaRelationshipsPreserveRulesAndNullResetReferenceWhenEventIsDeleted() = runBlocking {
        val subscription = subscription()
        val resetEvent = event(
            subscriptionId = subscription.id,
            recurrenceRule = RecurrenceRule.Repeating(3, RecurrenceUnit.MONTHS),
        )
        val oneTimeEvent = event(
            id = UUID.fromString("10000000-0000-0000-0000-000000000003"),
            subscriptionId = subscription.id,
            recurrenceRule = RecurrenceRule.OneTime,
        )
        val quota = quota(subscription.id, resetEvent.id)
        subscriptions.create(subscription)
        events.create(resetEvent)
        events.create(oneTimeEvent)
        quotas.create(quota)

        assertEquals(setOf(resetEvent, oneTimeEvent), events.observeForSubscription(subscription.id).first().toSet())
        assertEquals(quota, quotas.getById(quota.id))

        events.delete(resetEvent.id)

        assertNull(events.getById(resetEvent.id))
        assertEquals(quota.copy(resetEventId = null), quotas.getById(quota.id))
    }

    @Test
    fun archiveHidesSubscriptionWithoutDeletingChildrenAndDeleteCascades() = runBlocking {
        val subscription = subscription()
        val event = event(subscriptionId = subscription.id)
        val quota = quota(subscription.id, event.id)
        subscriptions.create(subscription)
        events.create(event)
        quotas.create(quota)
        val archivedAt = Instant.parse("2026-02-03T04:05:06.123456789Z")

        assertTrue(subscriptions.archive(subscription.id, archivedAt))

        assertTrue(subscriptions.observeVisible().first().isEmpty())
        assertEquals(SubscriptionStatus.ARCHIVED, subscriptions.getById(subscription.id)?.status)
        assertEquals(archivedAt, subscriptions.getById(subscription.id)?.archivedAt)
        assertEquals(event, events.getById(event.id))
        assertEquals(quota, quotas.getById(quota.id))

        subscriptions.delete(subscription.id)

        assertNull(subscriptions.getById(subscription.id))
        assertNull(events.getById(event.id))
        assertNull(quotas.getById(quota.id))
    }

    @Test
    fun repositoriesSupportCrudVisibilityAndAtomicReplacement() = runBlocking {
        val original = subscription()
        subscriptions.save(original)
        val renamed = original.copy(name = "Renamed", updatedAt = Instant.parse("2025-02-03T04:05:06Z"))
        assertTrue(subscriptions.update(renamed))
        assertEquals("Renamed", subscriptions.getById(original.id)?.name)

        val event = event(subscriptionId = original.id)
        events.save(event)
        assertTrue(events.setVisible(event.id, false, Instant.parse("2025-02-04T04:05:06Z")))
        assertFalse(events.getById(event.id)!!.enabled)
        assertTrue(events.observeVisibleForSubscription(original.id).first().isEmpty())

        val quota = quota(original.id, event.id)
        quotas.save(quota)
        val changedQuota = quota.copy(used = BigDecimal("8.500"))
        assertTrue(quotas.update(changedQuota))
        assertEquals(changedQuota, quotas.getById(quota.id))
        quotas.delete(quota.id)
        assertNull(quotas.getById(quota.id))

        val replacement = subscription(
            id = UUID.fromString("20000000-0000-0000-0000-000000000001"),
            name = "Replacement",
        )
        val replacementEvent = event(
            id = UUID.fromString("20000000-0000-0000-0000-000000000002"),
            subscriptionId = replacement.id,
        )
        val replacementQuota = quota(
            subscriptionId = replacement.id,
            resetEventId = replacementEvent.id,
            id = UUID.fromString("20000000-0000-0000-0000-000000000003"),
        )

        RoomRecordStore(database.transactionDao()).replaceAll(
            listOf(replacement),
            listOf(replacementEvent),
            listOf(replacementQuota),
        )

        assertNull(subscriptions.getById(original.id))
        assertEquals(replacement, subscriptions.getById(replacement.id))
        assertEquals(replacementEvent, events.getById(replacementEvent.id))
        assertEquals(replacementQuota, quotas.getById(replacementQuota.id))
    }

    private fun subscription(
        id: UUID = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        name: String = "Precise Plan",
        price: BigDecimal = BigDecimal("19.9900"),
        tags: Set<String> = setOf("work", "priority"),
        createdAt: Instant = Instant.parse("2024-01-02T03:04:05Z"),
        updatedAt: Instant = Instant.parse("2024-02-03T04:05:06Z"),
    ) = Subscription(
        id = id,
        name = name,
        provider = "Provider",
        category = "Software",
        iconIdentifier = "provider/icon",
        price = price,
        currency = Currency.getInstance("JPY"),
        billingInterval = RecurrenceUnit.MONTHS,
        billingIntervalCount = 3,
        autoRenew = false,
        startDate = LocalDate.parse("2024-01-31"),
        nextBillingDate = LocalDate.parse("2024-04-30"),
        expirationDate = LocalDate.parse("2027-12-31"),
        trialEndDate = LocalDate.parse("2024-02-15"),
        notes = "notes",
        tags = tags,
        plan = "Enterprise",
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun event(
        id: UUID = UUID.fromString("10000000-0000-0000-0000-000000000002"),
        subscriptionId: UUID,
        recurrenceRule: RecurrenceRule = RecurrenceRule.monthly(),
    ) = RecurringEvent(
        id = id,
        subscriptionId = subscriptionId,
        type = RecurringEventType.QUOTA_RESET,
        title = "Quota reset",
        nextOccurrence = LocalDate.parse("2025-03-31"),
        recurrenceRule = recurrenceRule,
        timezone = ZoneId.of("Asia/Tokyo"),
        reminders = ReminderSettings(
            enabled = true,
            offsets = setOf(ReminderOffset(0), ReminderOffset(7), ReminderOffset(30)),
            notificationTime = LocalTime.parse("09:07:06.123456789"),
        ),
        notes = "event notes",
        createdAt = Instant.parse("2024-01-02T03:04:05.123456789Z"),
        updatedAt = Instant.parse("2024-02-03T04:05:06.987654321Z"),
    )

    private fun quota(
        subscriptionId: UUID,
        resetEventId: UUID?,
        id: UUID = UUID.fromString("10000000-0000-0000-0000-000000000004"),
    ) = Quota(
        id = id,
        subscriptionId = subscriptionId,
        name = "API calls",
        unit = "requests",
        used = BigDecimal("1.2300"),
        remaining = BigDecimal("998.7700"),
        limit = BigDecimal("1000.0000"),
        percentage = BigDecimal("0.12300"),
        resetEventId = resetEventId,
        warningThresholdPercentage = BigDecimal("80.5000"),
        updatedAt = Instant.parse("2024-02-03T04:05:06.123456789Z"),
    )
}
