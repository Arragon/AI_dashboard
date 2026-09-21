package com.subscriptiontracker.presentation

import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import com.subscriptiontracker.domain.repository.QuotaRepository
import com.subscriptiontracker.domain.repository.RecurringEventRepository
import com.subscriptiontracker.domain.repository.SubscriptionRepository
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var subscriptions: FakeSubscriptions
    private lateinit var model: CoreViewModel
    private var reconciliations = 0

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        subscriptions = FakeSubscriptions()
        model = CoreViewModel(
            subscriptions,
            FakeEvents(),
            FakeQuotas(),
            object : BackupGateway {
                override suspend fun export(output: OutputStream) = Unit
                override suspend fun validate(input: InputStream) = Result.failure<ImportPreview>(IllegalStateException())
                override suspend fun replace(preview: ImportPreview) = Result.success(Unit)
            },
            ScheduleRefresh { reconciliations++ },
            Clock.fixed(Instant.parse("2025-01-10T12:00:00Z"), ZoneOffset.UTC),
            dispatcher,
        )
    }

    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `add edit cancel archive delete refresh state and reconcile`() = runTest(dispatcher) {
        advanceUntilIdle()
        model.saveSubscription(null, validInput(name = "Cloud"))
        advanceUntilIdle()
        val created = model.state.value.subscriptions.single()
        assertEquals("Cloud", created.name)

        model.saveSubscription(created.id, validInput(name = "Cloud Plus"))
        advanceUntilIdle()
        assertEquals("Cloud Plus", model.state.value.subscriptions.single().name)

        val expiry = LocalDate.parse("2025-02-01")
        model.cancel(created.id, expiry)
        advanceUntilIdle()
        assertEquals(SubscriptionStatus.CANCELLED_PENDING_EXPIRY, model.state.value.subscriptions.single().status)
        assertEquals(expiry, model.state.value.subscriptions.single().expirationDate)

        model.archive(created.id)
        advanceUntilIdle()
        assertEquals(SubscriptionStatus.ARCHIVED, model.state.value.subscriptions.single().status)

        model.deleteSubscription(created.id)
        advanceUntilIdle()
        assertEquals(emptyList<Subscription>(), model.state.value.subscriptions)
        assertEquals(6, reconciliations)
    }

    @Test
    fun `creating a subscription synthesizes billing expiration and trial events`() = runTest(dispatcher) {
        advanceUntilIdle()
        model.saveSubscription(
            null,
            validInput(name = "Cloud").copy(expirationDate = "2025-06-01", trialEndDate = "2025-01-12"),
        )
        advanceUntilIdle()
        val types = model.state.value.events.map { it.type }.toSet()
        assertEquals(setOf(com.subscriptiontracker.domain.model.RecurringEventType.BILLING, com.subscriptiontracker.domain.model.RecurringEventType.EXPIRATION, com.subscriptiontracker.domain.model.RecurringEventType.TRIAL_END), types)
        assertEquals(1, model.state.value.attention.count { it.kind == AttentionKind.TRIAL })
    }

    @Test
    fun `invalid edit preserves data and exposes correctable error`() = runTest(dispatcher) {
        subscriptions.save(validInput(name = "Original").domain())
        model.refresh()
        advanceUntilIdle()
        val id = model.state.value.subscriptions.single().id
        model.saveSubscription(id, validInput(name = "", price = "-1"))
        advanceUntilIdle()
        assertEquals("Original", subscriptions.getById(id)?.name)
        assertEquals("Name is required", model.state.value.error)
        assertNull(model.state.value.message)
    }

    @Test
    fun `refresh persists pending cancellation expiry transition`() = runTest(dispatcher) {
        val pending = validInput(name = "Ending").domain().copy(
            status = SubscriptionStatus.CANCELLED_PENDING_EXPIRY,
            autoRenew = false,
            expirationDate = LocalDate.parse("2025-01-10"),
        )
        subscriptions.save(pending)

        model.refresh()
        advanceUntilIdle()

        assertEquals(SubscriptionStatus.EXPIRED, model.state.value.subscriptions.single().status)
        assertEquals(SubscriptionStatus.EXPIRED, subscriptions.getById(pending.id)?.status)
    }

    private fun validInput(name: String, price: String = "10.50") = SubscriptionInput(
        name = name,
        provider = "Provider",
        category = "Work",
        price = price,
        currencyCode = "USD",
        startDate = "2025-01-01",
    )

    private fun SubscriptionInput.domain() = Subscription(
        UUID.randomUUID(), name, provider, category, price = price.toBigDecimal(), currency = java.util.Currency.getInstance(currencyCode),
        billingInterval = billingUnit, billingIntervalCount = billingCount.toInt(), autoRenew = autoRenew,
        startDate = LocalDate.parse(startDate), createdAt = Instant.parse("2025-01-01T00:00:00Z"), updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
    )
}

private class FakeSubscriptions : SubscriptionRepository {
    private val values = linkedMapOf<UUID, Subscription>()
    override suspend fun getById(id: UUID) = values[id]
    override suspend fun list() = values.values.toList()
    override suspend fun save(subscription: Subscription) { values[subscription.id] = subscription }
    override suspend fun delete(id: UUID) { values.remove(id) }
}

private class FakeEvents : RecurringEventRepository {
    private val values = linkedMapOf<UUID, RecurringEvent>()
    override suspend fun getById(id: UUID) = values[id]
    override suspend fun listForSubscription(subscriptionId: UUID) = values.values.filter { it.subscriptionId == subscriptionId }
    override suspend fun save(event: RecurringEvent) { values[event.id] = event }
    override suspend fun delete(id: UUID) { values.remove(id) }
}

private class FakeQuotas : QuotaRepository {
    private val values = linkedMapOf<UUID, Quota>()
    override suspend fun getById(id: UUID) = values[id]
    override suspend fun listForSubscription(subscriptionId: UUID) = values.values.filter { it.subscriptionId == subscriptionId }
    override suspend fun save(quota: Quota) { values[quota.id] = quota }
    override suspend fun delete(id: UUID) { values.remove(id) }
}
