package com.subscriptiontracker.domain.provider

import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.QuotaOrigin
import com.subscriptiontracker.domain.model.QuotaSyncState
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import com.subscriptiontracker.domain.repository.QuotaRepository
import com.subscriptiontracker.domain.repository.RecurringEventRepository
import com.subscriptiontracker.domain.repository.SubscriptionRepository
import com.subscriptiontracker.presentation.BackupGateway
import com.subscriptiontracker.presentation.CoreViewModel
import com.subscriptiontracker.presentation.ImportPreview
import com.subscriptiontracker.presentation.ScheduleRefresh
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnlineQuotaTest {
    private val now = Instant.parse("2026-09-22T00:00:00Z")
    private val subscriptionId = UUID.fromString("00000000-0000-0000-0000-000000000010")

    @Test
    fun openRouterReadsUsedRemainingAndLimitWithoutInventingASecondWindow() {
        val key = parseOpenRouterKey(
            """{"data":{"usage":25.5,"limit":100,"limit_remaining":74.5}}""",
        )
        val credits = parseOpenRouterCredits(
            """{"data":{"total_credits":40,"total_usage":10}}""",
        )

        assertEquals(0, BigDecimal("25.5").compareTo(key.used))
        assertEquals(0, BigDecimal("74.5").compareTo(key.remaining))
        assertEquals(0, BigDecimal("100").compareTo(key.limit))
        assertEquals(0, BigDecimal("10").compareTo(credits.used))
        assertEquals(0, BigDecimal("30").compareTo(credits.remaining))
        assertEquals("openrouter.key", key.stableKey)
        assertEquals("openrouter.credits", credits.stableKey)
    }

    @Test
    fun openRouterNullLimitStaysUnlimited() {
        val key = parseOpenRouterKey("""{"data":{"usage":3,"limit":null}}""")
        assertTrue(key.unlimited)
        assertNull(key.limit)
        assertEquals(0, BigDecimal("3").compareTo(key.used))
    }

    @Test
    fun deepSeekKeepsRemainingAndDoesNotInventConsumption() {
        val meters = parseDeepSeekBalance(
            """
            {"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"110.00","granted_balance":"10.00","topped_up_balance":"100.00"}]}
            """.trimIndent(),
        )
        val balance = meters.single()
        assertNull(balance.used)
        assertNull(balance.limit)
        assertEquals(0, BigDecimal("110.00").compareTo(balance.remaining))
        assertEquals("granted=10.00;topped=100.00", balance.note)
    }

    @Test
    fun moonshotNegativeCashIsADeficitNote() {
        val meters = parseMoonshotBalance(
            """{"code":0,"data":{"available_balance":2,"voucher_balance":2,"cash_balance":-3.5},"status":true}""",
            "moonshot",
            "CNY",
        )
        assertEquals(listOf("moonshot.available", "moonshot.voucher"), meters.map { it.stableKey })
        assertEquals("deficit=3.5;currency=CNY", meters.first().note)
    }

    @Test
    fun zaiUsesCountsAndDoesNotTreatAMissingWindowAsZero() {
        val meters = parseZaiLimits(
            """
            {"code":200,"data":{"limits":[
              {"type":"TOKENS_LIMIT","percentage":20,"currentValue":200,"remaining":800,"usage":1000},
              {"type":"TIME_LIMIT"}
            ]}}
            """.trimIndent(),
            "zai",
        )
        val tokens = meters.single()
        assertEquals(0, BigDecimal("200").compareTo(tokens.used))
        assertEquals(0, BigDecimal("800").compareTo(tokens.remaining))
        assertEquals(0, BigDecimal("1000").compareTo(tokens.limit))
        assertNull(tokens.percentage)
    }

    @Test
    fun failedRefreshKeepsTheLastSuccessAndMarksItStale() {
        val existing = quota(used = BigDecimal("25.5"), remaining = BigDecimal("74.5"), limit = BigDecimal("100"))
        val merged = OnlineQuotaMerge.apply(
            listOf(existing, manualQuota()),
            subscriptionId,
            "openrouter",
            OnlineFetchOutcome.Failure(OnlineFailureKind.TRANSIENT, "Usage request failed"),
            now.plusSeconds(60),
        )
        val stale = merged.single()
        assertEquals(existing.id, stale.id)
        assertEquals(existing.updatedAt, stale.updatedAt)
        assertEquals(QuotaSyncState.STALE, stale.syncState)
        assertEquals(0, BigDecimal("25.5").compareTo(stale.used))
    }

    @Test
    fun authFailureIsNotPresentedAsAFreshReading() {
        val merged = OnlineQuotaMerge.apply(
            listOf(quota()),
            subscriptionId,
            "openrouter",
            OnlineFetchOutcome.Failure(OnlineFailureKind.AUTH, "API key was rejected"),
            now,
        )
        assertEquals(QuotaSyncState.AUTH_REQUIRED, merged.single().syncState)
    }

    @Test
    fun omittedWindowStaysVisibleAsUnavailable() {
        val weekly = quota(stableKey = "zai.tokens_limit.1", name = "Token limit")
        val merged = OnlineQuotaMerge.apply(
            listOf(weekly),
            subscriptionId,
            "zai",
            OnlineFetchOutcome.Success(emptyList()),
            now.plusSeconds(5),
        )
        assertEquals(QuotaSyncState.UNAVAILABLE, merged.single().syncState)
        assertEquals(weekly.updatedAt, merged.single().updatedAt)
    }

    @Test
    fun serviceKeepsKeyUsageWhenCreditsCannotBeRead() = kotlinx.coroutines.test.runTest {
        val service = OnlineQuotaService { url, _ ->
            when {
                url.endsWith("/key") -> QuotaHttpResponse(200, """{"data":{"usage":1,"limit":2,"limit_remaining":1}}""")
                else -> QuotaHttpResponse(503, "")
            }
        }
        val outcome = service.fetch("openrouter", "secret") as OnlineFetchOutcome.Success
        assertEquals(listOf("openrouter.key"), outcome.drafts.map { it.stableKey })
        assertEquals(setOf("openrouter.credits"), outcome.staleKeys)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun viewModelRefreshUpdatesTheSameMeterAndDoesNotStoreTheKey() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val subscriptions = MemorySubscriptions()
            val quotas = MemoryQuotas()
            val keys = MemoryApiKeyStore()
            val id = UUID.randomUUID()
            subscriptions.save(sampleSubscription(id))
            keys.write(id, "sk-test")
            val model = CoreViewModel(
                subscriptions,
                MemoryEvents(),
                quotas,
                object : BackupGateway {
                    override suspend fun export(output: OutputStream) = Unit
                    override suspend fun validate(input: InputStream) = Result.failure<ImportPreview>(IllegalStateException())
                    override suspend fun replace(preview: ImportPreview) = Result.success(Unit)
                },
                ScheduleRefresh {},
                Clock.fixed(now, ZoneOffset.UTC),
                dispatcher,
                apiKeys = keys,
                onlineQuotas = OnlineQuotaService { url, token ->
                    assertEquals("sk-test", token)
                    if (url.endsWith("/key")) {
                        QuotaHttpResponse(200, """{"data":{"usage":8,"limit":20,"limit_remaining":12}}""")
                    } else {
                        QuotaHttpResponse(200, """{"data":{"total_credits":5,"total_usage":1}}""")
                    }
                },
            )
            advanceUntilIdle()
            model.updateOnlineProvider(id, "openrouter")
            advanceUntilIdle()
            model.refreshOnlineQuotas(id)
            advanceUntilIdle()

            val keyMeter = model.state.value.quotas.single { it.stableKey == "openrouter.key" }
            val credits = model.state.value.quotas.single { it.stableKey == "openrouter.credits" }
            assertEquals(0, BigDecimal("8").compareTo(keyMeter.used))
            assertEquals(0, BigDecimal("12").compareTo(keyMeter.remaining))
            assertEquals(0, BigDecimal("20").compareTo(keyMeter.limit))
            assertEquals(0, BigDecimal("1").compareTo(credits.used))
            assertEquals(0, BigDecimal("4").compareTo(credits.remaining))
            assertTrue(model.state.value.subscriptions.single().onlineProviderId == "openrouter")
            assertEquals("Online usage refreshed", model.state.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun quota(
        stableKey: String = "openrouter.key",
        name: String = "API key usage",
        used: BigDecimal? = BigDecimal.ONE,
        remaining: BigDecimal? = BigDecimal.TEN,
        limit: BigDecimal? = BigDecimal("11"),
    ) = Quota(
        id = UUID.randomUUID(),
        subscriptionId = subscriptionId,
        name = name,
        unit = "USD",
        used = used,
        remaining = remaining,
        limit = limit,
        updatedAt = now,
        stableKey = stableKey,
        origin = QuotaOrigin.ONLINE,
        syncState = QuotaSyncState.FRESH,
    )

    private fun manualQuota() = Quota(
        id = UUID.randomUUID(),
        subscriptionId = subscriptionId,
        name = "Hand entered",
        unit = "tokens",
        remaining = BigDecimal.TEN,
        updatedAt = now,
    )
}

private fun sampleSubscription(id: UUID) = Subscription(
    id = id,
    name = "Router",
    provider = "OpenRouter",
    category = "AI",
    price = BigDecimal.TEN,
    currency = Currency.getInstance("USD"),
    billingInterval = com.subscriptiontracker.domain.model.RecurrenceUnit.MONTHS,
    billingIntervalCount = 1,
    startDate = LocalDate.parse("2026-01-01"),
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
)

private class MemorySubscriptions : SubscriptionRepository {
    private val values = linkedMapOf<UUID, Subscription>()
    override suspend fun getById(id: UUID) = values[id]
    override suspend fun list() = values.values.toList()
    override suspend fun save(subscription: Subscription) { values[subscription.id] = subscription }
    override suspend fun delete(id: UUID) { values.remove(id) }
}

private class MemoryEvents : RecurringEventRepository {
    override suspend fun getById(id: UUID) = null
    override suspend fun listForSubscription(subscriptionId: UUID) = emptyList<com.subscriptiontracker.domain.model.RecurringEvent>()
    override suspend fun save(event: com.subscriptiontracker.domain.model.RecurringEvent) = Unit
    override suspend fun delete(id: UUID) = Unit
}

private class MemoryQuotas : QuotaRepository {
    private val values = linkedMapOf<UUID, Quota>()
    override suspend fun getById(id: UUID) = values[id]
    override suspend fun listForSubscription(subscriptionId: UUID) = values.values.filter { it.subscriptionId == subscriptionId }
    override suspend fun save(quota: Quota) { values[quota.id] = quota }
    override suspend fun delete(id: UUID) { values.remove(id) }
}
