package com.subscriptiontracker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.ReminderOffset
import com.subscriptiontracker.domain.model.ReminderSettings
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import com.subscriptiontracker.domain.query.ActivityFilter
import com.subscriptiontracker.domain.query.SubscriptionFilters
import com.subscriptiontracker.domain.query.SubscriptionQuery
import com.subscriptiontracker.domain.query.SubscriptionQueryService
import com.subscriptiontracker.domain.query.SubscriptionSort
import com.subscriptiontracker.domain.repository.QuotaRepository
import com.subscriptiontracker.domain.repository.RecurringEventRepository
import com.subscriptiontracker.domain.repository.SubscriptionRepository
import com.subscriptiontracker.domain.service.RecurrenceEngine
import com.subscriptiontracker.domain.service.SpendCalculationService
import com.subscriptiontracker.domain.service.SubscriptionLifecycleService
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportPreview(val bytes: ByteArray, val summary: String)

interface BackupGateway {
    suspend fun export(output: OutputStream)
    suspend fun validate(input: InputStream): Result<ImportPreview>
    suspend fun replace(preview: ImportPreview): Result<Unit>
}

fun interface ScheduleRefresh {
    suspend fun reconcile()
}

data class UpcomingEvent(val event: RecurringEvent, val subscriptionName: String, val date: LocalDate)

data class CoreState(
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val visibleSubscriptions: List<Subscription> = emptyList(),
    val events: List<RecurringEvent> = emptyList(),
    val quotas: List<Quota> = emptyList(),
    val currentMonth: Map<Currency, BigDecimal> = emptyMap(),
    val next30Days: Map<Currency, BigDecimal> = emptyMap(),
    val annualized: Map<Currency, BigDecimal> = emptyMap(),
    val activeCount: Int = 0,
    val upcoming: List<UpcomingEvent> = emptyList(),
    val query: SubscriptionQuery = SubscriptionQuery(),
    val pendingImport: ImportPreview? = null,
)

data class SubscriptionInput(
    val name: String = "",
    val provider: String = "",
    val category: String = "",
    val price: String = "",
    val currencyCode: String = "USD",
    val billingUnit: RecurrenceUnit = RecurrenceUnit.MONTHS,
    val billingCount: String = "1",
    val autoRenew: Boolean = true,
    val startDate: String = LocalDate.now().toString(),
    val expirationDate: String = "",
    val trialEndDate: String = "",
    val notes: String = "",
)

data class EventInput(
    val title: String = "",
    val type: RecurringEventType = RecurringEventType.BILLING,
    val date: String = LocalDate.now().toString(),
    val recurrence: String = "MONTHLY",
    val customCount: String = "1",
    val customUnit: RecurrenceUnit = RecurrenceUnit.MONTHS,
    val timezone: String = ZoneId.systemDefault().id,
    val enabled: Boolean = true,
    val reminderEnabled: Boolean = true,
    val reminderOffset: String = "1",
    val reminderTime: String = "09:00",
    val notes: String = "",
)

data class QuotaInput(
    val name: String = "",
    val unit: String = "",
    val used: String = "",
    val remaining: String = "",
    val limit: String = "",
    val percentage: String = "",
    val unlimited: Boolean = false,
    val resetEventId: String = "",
)

class CoreViewModel(
    private val subscriptions: SubscriptionRepository,
    private val events: RecurringEventRepository,
    private val quotas: QuotaRepository,
    private val backup: BackupGateway,
    private val scheduleRefresh: ScheduleRefresh,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val queryService = SubscriptionQueryService()
    private val spendService = SpendCalculationService()
    private val recurrenceEngine = RecurrenceEngine()
    private val lifecycle = SubscriptionLifecycleService()
    private val mutableState = MutableStateFlow(CoreState())
    val state: StateFlow<CoreState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch { load() }

    fun updateSearch(value: String) = updateQuery(mutableState.value.query.copy(searchText = value))
    fun updateActivity(value: ActivityFilter) = updateQuery(mutableState.value.query.copy(filters = mutableState.value.query.filters.copy(activity = value)))
    fun updateDueSoon(value: Boolean) = updateQuery(mutableState.value.query.copy(filters = mutableState.value.query.filters.copy(dueSoon = value)))
    fun updateCategory(value: String?) = updateQuery(mutableState.value.query.copy(filters = mutableState.value.query.filters.copy(category = value)))
    fun updateCurrency(value: Currency?) = updateQuery(mutableState.value.query.copy(filters = mutableState.value.query.filters.copy(currency = value)))
    fun updateSort(value: SubscriptionSort) = updateQuery(mutableState.value.query.copy(sort = value))
    fun clearMessage() { mutableState.value = mutableState.value.copy(message = null, error = null) }

    fun saveSubscription(id: UUID?, input: SubscriptionInput, onResult: (Result<Unit>) -> Unit = {}) = mutate(onResult) {
        val now = clock.instant()
        val existing = id?.let { subscriptions.getById(it) }
        val parsed = input.toDomain(existing, now)
        subscriptions.save(parsed)
    }

    fun cancel(id: UUID, expiry: LocalDate?, onResult: (Result<Unit>) -> Unit = {}) = mutate(onResult) {
        val current = requireNotNull(subscriptions.getById(id)) { "Subscription not found" }
        subscriptions.save(lifecycle.cancelAtPeriodEnd(current, expiry, clock.instant()))
    }

    fun archive(id: UUID, onResult: (Result<Unit>) -> Unit = {}) = mutate(onResult) {
        val current = requireNotNull(subscriptions.getById(id)) { "Subscription not found" }
        val now = clock.instant()
        subscriptions.save(current.copy(status = SubscriptionStatus.ARCHIVED, archivedAt = now, updatedAt = now))
    }

    fun deleteSubscription(id: UUID, onResult: (Result<Unit>) -> Unit = {}) = mutate(onResult) { subscriptions.delete(id) }

    fun saveEvent(subscriptionId: UUID, id: UUID?, input: EventInput, onResult: (Result<Unit>) -> Unit = {}) = mutate(onResult) {
        val now = clock.instant()
        val existing = id?.let { events.getById(it) }
        events.save(input.toDomain(subscriptionId, existing, now))
    }

    fun deleteEvent(id: UUID, onResult: (Result<Unit>) -> Unit = {}) = mutate(onResult) { events.delete(id) }

    fun saveQuota(subscriptionId: UUID, id: UUID?, input: QuotaInput, onResult: (Result<Unit>) -> Unit = {}) = mutate(onResult) {
        val existing = id?.let { quotas.getById(it) }
        quotas.save(input.toDomain(subscriptionId, existing, clock.instant()))
    }

    fun deleteQuota(id: UUID, onResult: (Result<Unit>) -> Unit = {}) = mutate(onResult) { quotas.delete(id) }

    fun export(output: OutputStream, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch {
        val result = runCatching { withContext(ioDispatcher) { backup.export(output) } }
        mutableState.value = mutableState.value.copy(message = result.fold({ "Backup exported" }, { null }), error = result.exceptionOrNull()?.message)
        onResult(result)
    }

    fun validateImport(input: InputStream) = viewModelScope.launch {
        val result = runCatching { withContext(ioDispatcher) { input.use { backup.validate(it).getOrThrow() } } }
        mutableState.value = mutableState.value.copy(pendingImport = result.getOrNull(), error = result.exceptionOrNull()?.message)
    }

    fun dismissImport() { mutableState.value = mutableState.value.copy(pendingImport = null) }

    fun confirmImport() = viewModelScope.launch {
        val preview = mutableState.value.pendingImport ?: return@launch
        val result = runCatching { withContext(ioDispatcher) { backup.replace(preview).getOrThrow() } }
        if (result.isSuccess) {
            runCatching { scheduleRefresh.reconcile() }
            load("Backup restored; existing data was replaced")
        } else mutableState.value = mutableState.value.copy(error = result.exceptionOrNull()?.message, pendingImport = null)
    }

    private fun updateQuery(query: SubscriptionQuery) {
        val current = mutableState.value
        mutableState.value = current.copy(query = query, visibleSubscriptions = queryService.execute(current.subscriptions, current.events, query, LocalDate.now(clock)))
    }

    private fun mutate(onResult: (Result<Unit>) -> Unit, operation: suspend () -> Unit) = viewModelScope.launch {
        val result = runCatching { withContext(ioDispatcher) { operation() } }
        if (result.isSuccess) {
            runCatching { withContext(ioDispatcher) { scheduleRefresh.reconcile() } }
            load("Saved")
        } else mutableState.value = mutableState.value.copy(error = result.exceptionOrNull()?.message ?: "Operation failed")
        onResult(result)
    }

    private suspend fun load(message: String? = null) {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        runCatching {
            withContext(ioDispatcher) {
                val today = LocalDate.now(clock)
                val now = clock.instant()
                val allSubscriptions = subscriptions.list().map { subscription ->
                    lifecycle.applyExpiry(subscription, today, now).also { updated ->
                        if (updated != subscription) subscriptions.save(updated)
                    }
                }
                val allEvents = allSubscriptions.flatMap { events.listForSubscription(it.id) }
                val allQuotas = allSubscriptions.flatMap { quotas.listForSubscription(it.id) }
                Triple(allSubscriptions, allEvents, allQuotas)
            }
        }.onSuccess { (allSubscriptions, allEvents, allQuotas) ->
            val today = LocalDate.now(clock)
            val query = mutableState.value.query
            val names = allSubscriptions.associate { it.id to it.name }
            val upcoming = allEvents.asSequence().filter { it.enabled }.mapNotNull { event ->
                recurrenceEngine.occurrenceOnOrAfter(event.nextOccurrence, event.recurrenceRule, today)?.let {
                    UpcomingEvent(event, names[event.subscriptionId].orEmpty(), it)
                }
            }.filter { it.date <= today.plusDays(30) }.sortedBy { it.date }.toList()
            mutableState.value = CoreState(
                loading = false,
                message = message,
                subscriptions = allSubscriptions,
                visibleSubscriptions = queryService.execute(allSubscriptions, allEvents, query, today),
                events = allEvents,
                quotas = allQuotas,
                currentMonth = spendService.spendForCurrentCalendarMonth(allSubscriptions, allEvents, today),
                next30Days = spendService.spendForNext30Days(allSubscriptions, allEvents, today),
                annualized = spendService.annualizedRecurringSpend(allSubscriptions, today),
                activeCount = spendService.activeSubscriptionCount(allSubscriptions, today),
                upcoming = upcoming,
                query = query,
            )
        }.onFailure { mutableState.value = mutableState.value.copy(loading = false, error = it.message ?: "Unable to load data") }
    }
}

private fun SubscriptionInput.toDomain(existing: Subscription?, now: Instant): Subscription {
    require(name.isNotBlank()) { "Name is required" }
    require(provider.isNotBlank()) { "Provider is required" }
    require(category.isNotBlank()) { "Category is required" }
    val amount = price.toBigDecimalOrNull() ?: error("Price must be a number")
    require(amount >= BigDecimal.ZERO) { "Price cannot be negative" }
    val code = currencyCode.trim().uppercase()
    require(code.length == 3) { "Use a 3-letter ISO currency code" }
    val currency = runCatching { Currency.getInstance(code) }.getOrElse { error("Unknown ISO currency") }
    val count = billingCount.toIntOrNull() ?: error("Billing count must be a number")
    require(count > 0) { "Billing count must be positive" }
    val start = startDate.parseDate("Start date")
    return Subscription(
        id = existing?.id ?: UUID.randomUUID(), name = name.trim(), provider = provider.trim(), category = category.trim(),
        status = existing?.status ?: SubscriptionStatus.ACTIVE, price = amount, currency = currency,
        billingInterval = billingUnit, billingIntervalCount = count, autoRenew = autoRenew, startDate = start,
        nextBillingDate = existing?.nextBillingDate, expirationDate = expirationDate.parseOptionalDate("Expiration date"),
        trialEndDate = trialEndDate.parseOptionalDate("Trial end date"), notes = notes.trim(), tags = existing?.tags.orEmpty(),
        plan = existing?.plan, createdAt = existing?.createdAt ?: now, updatedAt = now, archivedAt = existing?.archivedAt,
    )
}

private fun EventInput.toDomain(subscriptionId: UUID, existing: RecurringEvent?, now: Instant): RecurringEvent {
    require(title.isNotBlank()) { "Event title is required" }
    val rule = when (recurrence) {
        "ONE_TIME" -> RecurrenceRule.OneTime
        "DAILY" -> RecurrenceRule.daily()
        "WEEKLY" -> RecurrenceRule.weekly()
        "MONTHLY" -> RecurrenceRule.monthly()
        "QUARTERLY" -> RecurrenceRule.quarterly()
        "YEARLY" -> RecurrenceRule.yearly()
        else -> RecurrenceRule.every(customCount.toIntOrNull()?.takeIf { it > 0 } ?: error("Custom interval must be positive"), customUnit)
    }
    val offset = reminderOffset.toIntOrNull() ?: error("Reminder offset must be a number")
    require(offset >= 0) { "Reminder offset cannot be negative" }
    val reminders = if (reminderEnabled) ReminderSettings(true, setOf(ReminderOffset(offset)), reminderTime.parseTime()) else ReminderSettings(false, emptySet(), reminderTime.parseTime())
    return RecurringEvent(existing?.id ?: UUID.randomUUID(), subscriptionId, type, title.trim(), date.parseDate("Event date"), rule,
        runCatching { ZoneId.of(timezone.trim()) }.getOrElse { error("Unknown timezone") }, enabled, reminders, notes.trim(), existing?.createdAt ?: now, now)
}

private fun QuotaInput.toDomain(subscriptionId: UUID, existing: Quota?, now: Instant): Quota {
    require(name.isNotBlank()) { "Quota name is required" }
    require(unit.isNotBlank()) { "Quota unit is required" }
    fun decimal(value: String, label: String) = if (value.isBlank()) null else value.toBigDecimalOrNull() ?: error("$label must be a number")
    return Quota(existing?.id ?: UUID.randomUUID(), subscriptionId, name.trim(), unit.trim(), decimal(used, "Used"), decimal(remaining, "Remaining"),
        decimal(limit, "Limit"), decimal(percentage, "Percentage"), unlimited,
        resetEventId.takeIf(String::isNotBlank)?.let { runCatching { UUID.fromString(it) }.getOrElse { error("Invalid reset event") } }, null, now)
}

private fun String.parseDate(label: String): LocalDate = runCatching { LocalDate.parse(trim()) }.getOrElse { error("$label must use YYYY-MM-DD") }
private fun String.parseOptionalDate(label: String): LocalDate? = if (isBlank()) null else parseDate(label)
private fun String.parseTime(): LocalTime = runCatching { LocalTime.parse(trim()) }.getOrElse { error("Reminder time must use HH:MM") }
