package com.subscriptiontracker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.subscriptiontracker.R
import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.QuotaOrigin
import com.subscriptiontracker.domain.model.QuotaSyncState
import com.subscriptiontracker.domain.provider.OnlineProviders
import com.subscriptiontracker.domain.service.RecurrenceEngine
import com.subscriptiontracker.presentation.AttentionKind
import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.ReminderSettings
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.model.SubscriptionStatus
import com.subscriptiontracker.domain.query.ActivityFilter
import com.subscriptiontracker.domain.query.SubscriptionSort
import com.subscriptiontracker.platform.i18n.AppLanguageManager
import com.subscriptiontracker.platform.reminder.NotificationPermissionStatus
import com.subscriptiontracker.presentation.CoreState
import com.subscriptiontracker.presentation.CoreViewModel
import com.subscriptiontracker.presentation.EventInput
import com.subscriptiontracker.presentation.QuotaInput
import com.subscriptiontracker.domain.catalog.SubscriptionTemplate
import com.subscriptiontracker.domain.catalog.SubscriptionTemplates
import com.subscriptiontracker.presentation.SubscriptionInput
import com.subscriptiontracker.presentation.toInput
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

private enum class Page { OVERVIEW, SUBSCRIPTIONS, INSIGHTS, SETTINGS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreApp(
    state: CoreState,
    model: CoreViewModel,
    notificationStatus: NotificationPermissionStatus,
    onRequestNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSetLanguage: (String) -> Unit,
    versionName: String,
) {
    var page by remember { mutableStateOf(Page.OVERVIEW) }
    var detailId by remember { mutableStateOf<UUID?>(null) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var adding by remember { mutableStateOf(false) }
    var templateSeed by remember { mutableStateOf<SubscriptionInput?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.ledger), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            pageLabel(page),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Page.entries.forEach { item ->
                        NavigationItem(
                            label = pageLabel(item),
                            selected = page == item && detailId == null,
                            onClick = { page = item; detailId = null },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxWidth().widthIn(max = 1100.dp)) {
                when {
                    state.loading -> {
                        val loadingText = stringResource(R.string.loading)
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.semantics { contentDescription = loadingText }) }
                    }
                    detailId != null -> state.subscriptions.firstOrNull { it.id == detailId }?.let { sub ->
                        DetailScreen(sub, state, onBack = { detailId = null }, onEdit = { editing = sub }, model = model)
                    }
                    page == Page.OVERVIEW -> OverviewScreen(
                        state,
                        onOpen = { detailId = it },
                        onAdd = { templateSeed = null; adding = true },
                        onTemplate = { templateSeed = it.toInput(LocalDate.now()); adding = true },
                        onRefreshOnline = { model.refreshOnlineQuotas() },
                    )
                    page == Page.SUBSCRIPTIONS -> SubscriptionsScreen(
                        state,
                        model,
                        { detailId = it },
                        { templateSeed = null; adding = true },
                        { templateSeed = it.toInput(LocalDate.now()); adding = true },
                    )
                    page == Page.INSIGHTS -> InsightsScreen(state)
                    page == Page.SETTINGS -> SettingsScreen(state, model, notificationStatus, onRequestNotifications, onOpenAppSettings, onExport, onImport, onSetLanguage, versionName)
                }
            }
        }
    }
    val renewalTitle = stringResource(R.string.event_generated_renewal)
    val expirationTitle = stringResource(R.string.event_generated_expiration)
    val trialTitle = stringResource(R.string.event_generated_trial)
    if (adding || editing != null) SubscriptionDialog(
        existing = editing,
        defaults = state.defaults,
        seed = if (editing == null) templateSeed else null,
        error = if (state.error != null) resolveUserMessage(state.error) else null,
        onDismiss = { model.clearMessage(); adding = false; editing = null; templateSeed = null },
    ) { id, input ->
        model.saveSubscription(
            id,
            input.copy(
                generatedBillingTitle = renewalTitle,
                generatedExpirationTitle = expirationTitle,
                generatedTrialTitle = trialTitle,
            ),
        ) { if (it.isSuccess) { adding = false; editing = null; templateSeed = null } }
    }
    state.pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = model::dismissImport,
            title = { Text(stringResource(R.string.replace_all_data_title)) },
            text = { Text(stringResource(R.string.replace_all_data_body_format, preview.summary)) },
            confirmButton = { DangerButton(onClick = model::confirmImport) { Text(stringResource(R.string.replace_and_restore)) } },
            dismissButton = { TextButton(onClick = model::dismissImport) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val feedback = when {
        (adding || editing != null) && state.error != null -> null
        state.message == "Saved" -> null
        else -> resolveUserMessage(state.error ?: state.message)
    }
    if (feedback != null) AlertDialog(
        onDismissRequest = model::clearMessage,
        text = { Text(feedback) },
        confirmButton = { TextButton(onClick = model::clearMessage) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun resolveUserMessage(text: String?): String? = text?.let {
    when (it) {
        "Saved" -> stringResource(R.string.saved)
        "Backup exported" -> stringResource(R.string.backup_exported)
        "Backup restored; existing data was replaced" -> stringResource(R.string.backup_restored)
        "Unable to load data" -> stringResource(R.string.unable_to_load_data)
        "Operation failed" -> stringResource(R.string.operation_failed)
        "Defaults saved" -> stringResource(R.string.defaults_saved)
        "Name is required" -> stringResource(R.string.error_name_required)
        "Provider is required" -> stringResource(R.string.error_provider_required)
        "Category is required" -> stringResource(R.string.error_category_required)
        "Price must be a number" -> stringResource(R.string.error_price_number)
        "Price cannot be negative" -> stringResource(R.string.error_price_negative)
        "Use a 3-letter ISO currency code" -> stringResource(R.string.error_currency_length)
        "Unknown ISO currency" -> stringResource(R.string.error_currency_unknown)
        "Billing count must be a number" -> stringResource(R.string.error_billing_count_number)
        "Billing count must be positive" -> stringResource(R.string.error_billing_count_positive)
        "Start date must use YYYY-MM-DD" -> stringResource(R.string.error_start_date)
        "Expiration date must use YYYY-MM-DD" -> stringResource(R.string.error_expiration_date)
        "Trial end date must use YYYY-MM-DD" -> stringResource(R.string.error_trial_date)
        "Reminder offset must be a number" -> stringResource(R.string.error_reminder_offset_number)
        "Reminder offset cannot be negative" -> stringResource(R.string.error_reminder_offset_negative)
        "Reminder time must use HH:MM" -> stringResource(R.string.error_reminder_time)
        "API key saved" -> stringResource(R.string.api_key_saved)
        "API key removed" -> stringResource(R.string.api_key_removed)
        "Online usage refreshed" -> stringResource(R.string.online_usage_refreshed)
        "Some usage refreshes failed" -> stringResource(R.string.some_usage_refreshes_failed)
        "API key is required" -> stringResource(R.string.error_api_key_required)
        "API key must be a single line" -> stringResource(R.string.error_api_key_single_line)
        "Choose an online provider first" -> stringResource(R.string.error_choose_online_provider)
        "Online provider is not supported" -> stringResource(R.string.error_online_provider_unsupported)
        "API key was rejected" -> stringResource(R.string.error_api_key_rejected)
        "Usage request failed" -> stringResource(R.string.error_usage_request_failed)
        "Usage response could not be read" -> stringResource(R.string.error_usage_unreadable)
        else -> it
    }
}

@Composable
private fun pageLabel(page: Page): String = stringResource(
    when (page) {
        Page.OVERVIEW -> R.string.overview
        Page.SUBSCRIPTIONS -> R.string.subscriptions
        Page.INSIGHTS -> R.string.insights
        Page.SETTINGS -> R.string.settings
    },
)

@Composable
private fun PageBody(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewScreen(
    state: CoreState,
    onOpen: (UUID) -> Unit,
    onAdd: () -> Unit,
    onTemplate: (SubscriptionTemplate) -> Unit,
    onRefreshOnline: () -> Unit,
) = PageBody(
    stringResource(R.string.overview),
    stringResource(R.string.overview_subtitle),
) {
    if (state.subscriptions.isEmpty()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            EmptyState(stringResource(R.string.no_subscriptions_yet), stringResource(R.string.no_subscriptions_prompt))
            Spacer(Modifier.height(16.dp))
            TemplatePicker(onTemplate)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) { Text(stringResource(R.string.add_subscription)) }
        }
        return@PageBody
    }
    val hasOnline = state.subscriptions.any { it.onlineProviderId != null }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp,
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Metric(stringResource(R.string.active), state.activeCount.toString())
                    CurrencyMetric(stringResource(R.string.this_month), state.currentMonth)
                    CurrencyMetric(stringResource(R.string.next_30_days), state.next30Days)
                    CurrencyMetric(stringResource(R.string.annualized), state.annualized)
                }
            }
        }
        if (hasOnline) item {
            OutlinedButton(onClick = onRefreshOnline) { Text(stringResource(R.string.refresh_all_usage)) }
        }
        if (state.attention.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.attention_title)) }
            items(state.attention, key = { "${it.subscriptionId}-${it.kind}-${it.quotaName}-${it.date}" }) { item ->
                val detail = when (item.kind) {
                    AttentionKind.RENEWAL -> stringResource(R.string.attention_renewal, item.subscriptionName, item.date.toString())
                    AttentionKind.TRIAL -> stringResource(R.string.attention_trial, item.subscriptionName, item.date.toString())
                    AttentionKind.EXPIRATION -> stringResource(R.string.attention_expiration, item.subscriptionName, item.date.toString())
                    AttentionKind.QUOTA -> stringResource(R.string.attention_quota, item.subscriptionName, item.quotaName.orEmpty())
                }
                CompactRow(item.subscriptionName, detail, item.date?.toString().orEmpty()) { onOpen(item.subscriptionId) }
            }
        }
        item { SectionTitle(stringResource(R.string.upcoming_events)) }
        if (state.upcoming.isEmpty()) item { EmptyState(stringResource(R.string.nothing_due_30_days), stringResource(R.string.upcoming_events_hint)) }
        items(state.upcoming, key = { it.event.id }) { item ->
            val typeLabel = eventTypeLabel(item.event.type)
            CompactRow(
                title = item.event.title,
                subtitle = "${item.subscriptionName} · $typeLabel",
                trailing = item.date.toString(),
                onClick = { onOpen(item.event.subscriptionId) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionsScreen(
    state: CoreState,
    model: CoreViewModel,
    onOpen: (UUID) -> Unit,
    onAdd: () -> Unit,
    onTemplate: (SubscriptionTemplate) -> Unit,
) = PageBody(stringResource(R.string.subscriptions)) {
    val allText = stringResource(R.string.all)
    val activityLabel = stringResource(R.string.activity_label)
    val searchLabel = stringResource(R.string.search_subscriptions)
    val addLabel = stringResource(R.string.add_subscription)
    Column(Modifier.weight(1f)) {
        OutlinedTextField(
            value = state.query.searchText,
            onValueChange = model::updateSearch,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CycleButton("$activityLabel: ${activityFilterLabel(state.query.filters.activity)}") {
                model.updateActivity(ActivityFilter.entries[(state.query.filters.activity.ordinal + 1) % ActivityFilter.entries.size])
            }
            ToggleButton(stringResource(R.string.due_soon), state.query.filters.dueSoon) { model.updateDueSoon(!state.query.filters.dueSoon) }
            ChoiceMenu(stringResource(R.string.category_label), state.query.filters.category ?: allText, listOf(allText) + state.subscriptions.map { it.category }.distinct().sorted()) {
                model.updateCategory(it.takeUnless { value -> value == allText })
            }
            ChoiceMenu(stringResource(R.string.currency_label), state.query.filters.currency?.currencyCode ?: allText, listOf(allText) + state.subscriptions.map { it.currency.currencyCode }.distinct().sorted()) {
                model.updateCurrency(it.takeUnless { value -> value == allText }?.let(Currency::getInstance))
            }
            val sortLabels = linkedMapOf<SubscriptionSort, String>()
            for (sort in SubscriptionSort.entries) sortLabels[sort] = sortLabel(sort)
            ChoiceMenu(stringResource(R.string.sort_label), sortLabels.getValue(state.query.sort), sortLabels.values.toList()) { label ->
                model.updateSort(sortLabels.entries.first { it.value == label }.key)
            }
        }
        Spacer(Modifier.height(12.dp))
        TemplatePicker(onTemplate)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAdd, modifier = Modifier.align(Alignment.End).semantics { contentDescription = addLabel }) { Text(stringResource(R.string.add_subscription)) }
        Spacer(Modifier.height(10.dp))
        if (state.visibleSubscriptions.isEmpty()) EmptyState(stringResource(R.string.no_matching_subscriptions), stringResource(R.string.no_matching_prompt))
        else LazyColumn(Modifier.weight(1f)) {
            items(state.visibleSubscriptions, key = { it.id }) { sub ->
                val cycle = recurrenceDisplay(RecurrenceRule.every(sub.billingIntervalCount ?: 1, sub.billingInterval ?: RecurrenceUnit.MONTHS))
                val next = nextEventLine(sub, state.events)
                val subtitle = buildString {
                    append(sub.provider).append(" · ").append(sub.category).append(" · ").append(cycle)
                    if (next != null) append('\n').append(next)
                }
                CompactRow(sub.name, subtitle, "${sub.currency.currencyCode} ${sub.price.pretty()}") { onOpen(sub.id) }
            }
        }
    }
}

@Composable
private fun InsightsScreen(state: CoreState) = PageBody(
    stringResource(R.string.insights),
    stringResource(R.string.insights_subtitle),
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SectionTitle(stringResource(R.string.annual_recurring_spend)) }
        if (state.annualized.isEmpty()) item { EmptyState(stringResource(R.string.no_recurring_spend), stringResource(R.string.no_recurring_spend_hint)) }
        else items(state.annualized.entries.toList()) { (currency, amount) -> CompactRow(currency.currencyCode, stringResource(R.string.annualized), amount.pretty(), null) }
        item { SectionTitle(stringResource(R.string.current_month_by_category)) }
        val rows = state.subscriptions.groupBy { it.category }.map { (category, subscriptions) ->
            category to subscriptions.groupBy { it.currency }.mapValues { (_, values) -> values.fold(BigDecimal.ZERO) { total, sub -> total + sub.price } }
        }
        items(rows) { (category, totals) -> CompactRow(category, stringResource(R.string.listed_recurring_prices), totals.entries.joinToString(" · ") { "${it.key.currencyCode} ${it.value.pretty()}" }, null) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    state: CoreState,
    model: CoreViewModel,
    status: NotificationPermissionStatus,
    onRequest: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSetLanguage: (String) -> Unit,
    versionName: String,
) = PageBody(stringResource(R.string.settings)) {
    val context = LocalContext.current
    val selectedLanguage = AppLanguageManager.get(context)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            SectionTitle(stringResource(R.string.settings_notifications))
            Text(
                when (status) {
                    NotificationPermissionStatus.GRANTED -> stringResource(R.string.notifications_allowed)
                    NotificationPermissionStatus.NOT_REQUIRED -> stringResource(R.string.notifications_not_required)
                    NotificationPermissionStatus.DENIED -> stringResource(R.string.notifications_denied)
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status == NotificationPermissionStatus.DENIED) Button(onClick = onRequest) { Text(stringResource(R.string.request_permission)) }
                OutlinedButton(onClick = onSettings) { Text(stringResource(R.string.open_app_settings)) }
            }
        }
        item {
            HorizontalDivider(); Spacer(Modifier.height(16.dp)); SectionTitle(stringResource(R.string.local_defaults))
            var currency by remember(state.defaults) { mutableStateOf(state.defaults.currencyCode) }
            var offset by remember(state.defaults) { mutableStateOf(state.defaults.reminderOffsetDays.toString()) }
            var time by remember(state.defaults) { mutableStateOf(state.defaults.reminderTime) }
            Field(stringResource(R.string.field_default_currency), currency) { currency = it }
            Field(stringResource(R.string.field_reminder_days), offset) { offset = it }
            Field(stringResource(R.string.field_reminder_time), time) { time = it }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { model.updateDefaults(currency, offset, time) }) { Text(stringResource(R.string.save_defaults)) }
        }
        item {
            HorizontalDivider(); Spacer(Modifier.height(16.dp)); SectionTitle(stringResource(R.string.language))
            Text(stringResource(R.string.language_summary))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguageManager.supported().forEach { (tag, label) ->
                    if (tag == selectedLanguage) {
                        Button(onClick = {}, enabled = false) { Text(label) }
                    } else {
                        TextButton(onClick = { onSetLanguage(tag) }) { Text(label) }
                    }
                }
            }
        }
        item {
            HorizontalDivider(); Spacer(Modifier.height(16.dp)); SectionTitle(stringResource(R.string.backup))
            Text(stringResource(R.string.backup_summary))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, modifier = Modifier.semantics { contentDescription = "Export backup document" }) { Text(stringResource(R.string.export)) }
                OutlinedButton(onClick = onImport, modifier = Modifier.semantics { contentDescription = "Import backup document" }) { Text(stringResource(R.string.import_replace)) }
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.app_version, versionName), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailScreen(subscription: Subscription, state: CoreState, onBack: () -> Unit, onEdit: () -> Unit, model: CoreViewModel) {
    val events = state.events.filter { it.subscriptionId == subscription.id }
    val quotas = state.quotas.filter { it.subscriptionId == subscription.id }
    var deleteConfirm by remember { mutableStateOf(false) }
    var cancelConfirm by remember { mutableStateOf(false) }
    var eventEdit by remember { mutableStateOf<RecurringEvent?>(null) }
    var addEvent by remember { mutableStateOf(false) }
    var quotaEdit by remember { mutableStateOf<Quota?>(null) }
    var addQuota by remember { mutableStateOf(false) }
    val status = statusLabel(subscription.status)
    PageBody(subscription.name, "${subscription.provider} · ${subscription.category}") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                    if (subscription.status == SubscriptionStatus.ACTIVE) OutlinedButton(onClick = { cancelConfirm = true }) { Text(stringResource(R.string.cancel_at_expiry)) }
                    if (subscription.status != SubscriptionStatus.ARCHIVED) OutlinedButton(onClick = { model.archive(subscription.id) }) { Text(stringResource(R.string.archive)) }
                    TextButton(onClick = { deleteConfirm = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
            }
            item { CompactRow(stringResource(R.string.status), status, "${subscription.currency.currencyCode} ${subscription.price}", null) }
            item { SectionTitle(stringResource(R.string.recurring_events)); Button(onClick = { addEvent = true }) { Text(stringResource(R.string.add_event)) } }
            if (events.isEmpty()) item { EmptyState(stringResource(R.string.no_events), stringResource(R.string.no_events_hint)) }
            items(events, key = { it.id }) { event ->
                val typeLabel = eventTypeLabel(event.type)
                val ruleLabel = recurrenceDisplay(event.recurrenceRule)
                CompactRow(event.title, "$typeLabel · $ruleLabel", event.nextOccurrence.toString()) { eventEdit = event }
            }
            item { OnlineUsageSection(subscription, state, model) }
            item { SectionTitle(stringResource(R.string.quotas)); Button(onClick = { addQuota = true }) { Text(stringResource(R.string.add_quota)) } }
            if (quotas.isEmpty()) item { EmptyState(stringResource(R.string.no_quotas), stringResource(R.string.no_quotas_hint)) }
            items(quotas, key = { it.id }) { quota ->
                CompactRow(quotaTitle(quota), quotaSubtitle(quota), quotaSummary(quota)) { quotaEdit = quota }
            }
        }
    }
    if (deleteConfirm) ConfirmDialog(
        stringResource(R.string.delete_subscription_title),
        stringResource(R.string.delete_subscription_body),
        stringResource(R.string.delete),
        { model.deleteSubscription(subscription.id) { if (it.isSuccess) onBack() }; deleteConfirm = false },
        { deleteConfirm = false },
        destructive = true,
    )
    if (cancelConfirm) ConfirmDialog(
        stringResource(R.string.cancel_period_title),
        stringResource(R.string.cancel_period_body),
        stringResource(R.string.cancel_subscription),
        { model.cancel(subscription.id, subscription.expirationDate); cancelConfirm = false },
        { cancelConfirm = false },
    )
    if (addEvent || eventEdit != null) EventDialog(
        eventEdit,
        state.defaults.reminderOffsetDays.toString(),
        state.defaults.reminderTime,
        { addEvent = false; eventEdit = null },
        { id, input -> model.saveEvent(subscription.id, id, input) { if (it.isSuccess) { addEvent = false; eventEdit = null } } },
        { id -> model.deleteEvent(id); eventEdit = null },
    )
    if (addQuota || quotaEdit != null) QuotaDialog(quotaEdit, events, { addQuota = false; quotaEdit = null }, { id, input -> model.saveQuota(subscription.id, id, input) { if (it.isSuccess) { addQuota = false; quotaEdit = null } } }, { id -> model.deleteQuota(id); quotaEdit = null })
}

@Composable
private fun SubscriptionDialog(
    existing: Subscription?,
    defaults: com.subscriptiontracker.presentation.LedgerDefaults,
    seed: SubscriptionInput?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (UUID?, SubscriptionInput) -> Unit,
) {
    var input by remember(existing?.id, seed) {
        mutableStateOf(existing?.toInput() ?: seed ?: SubscriptionInput(currencyCode = defaults.currencyCode))
    }
    var step by remember { mutableIntStateOf(0) }
    var localError by remember { mutableStateOf<String?>(null) }
    val unitOptions = mutableMapOf<String, RecurrenceUnit>()
    for (unit in RecurrenceUnit.entries) {
        unitOptions[unitLabel(unit)] = unit
    }
    val stepName = when (step) {
        0 -> stringResource(R.string.step_basics)
        1 -> stringResource(R.string.step_billing)
        else -> stringResource(R.string.step_dates)
    }
    val nameRequired = stringResource(R.string.error_name_required)
    val providerRequired = stringResource(R.string.error_provider_required)
    val categoryRequired = stringResource(R.string.error_category_required)
    val priceNumber = stringResource(R.string.error_price_number)
    val priceNegative = stringResource(R.string.error_price_negative)
    val currencyLength = stringResource(R.string.error_currency_length)
    val billingNumber = stringResource(R.string.error_billing_count_number)
    val billingPositive = stringResource(R.string.error_billing_count_positive)
    FormDialog(
        if (existing == null) stringResource(R.string.add_subscription_title) else stringResource(R.string.edit_subscription_title),
        onDismiss,
        {
            val problem = stepProblem(step, input, nameRequired, providerRequired, categoryRequired, priceNumber, priceNegative, currencyLength, billingNumber, billingPositive)
            if (problem != null) localError = problem
            else if (step < 2) {
                localError = null
                step += 1
            } else onSave(existing?.id, input)
        },
        confirmText = if (step < 2) stringResource(R.string.next) else stringResource(R.string.save),
        onBack = if (step == 0) null else ({ localError = null; step -= 1 }),
    ) {
        Text(stringResource(R.string.step_of, step + 1, 3) + " · " + stepName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (step) {
            0 -> {
                Field(stringResource(R.string.field_name), input.name) { input = input.copy(name = it) }
                Field(stringResource(R.string.field_provider), input.provider) { input = input.copy(provider = it) }
                Field(stringResource(R.string.field_category), input.category) { input = input.copy(category = it) }
            }
            1 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(stringResource(R.string.field_price), input.price, Modifier.weight(1f)) { input = input.copy(price = it) }
                    Field(stringResource(R.string.field_iso_currency), input.currencyCode, Modifier.weight(1f)) { input = input.copy(currencyCode = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChoiceMenu(stringResource(R.string.field_billing_unit), unitLabel(input.billingUnit), unitOptions.keys.toList()) { input = input.copy(billingUnit = unitOptions.getValue(it)) }
                    Field(stringResource(R.string.field_every_n), input.billingCount, Modifier.weight(1f)) { input = input.copy(billingCount = it) }
                }
                CheckRow(stringResource(R.string.field_auto_renew), input.autoRenew) { input = input.copy(autoRenew = it) }
            }
            else -> {
                Field(stringResource(R.string.field_start_date), input.startDate) { input = input.copy(startDate = it) }
                Field(stringResource(R.string.field_expiration_date), input.expirationDate) { input = input.copy(expirationDate = it) }
                Field(stringResource(R.string.field_trial_end_date), input.trialEndDate) { input = input.copy(trialEndDate = it) }
                Field(stringResource(R.string.field_notes), input.notes) { input = input.copy(notes = it) }
            }
        }
    }
}

private fun stepProblem(
    step: Int,
    input: SubscriptionInput,
    nameRequired: String,
    providerRequired: String,
    categoryRequired: String,
    priceNumber: String,
    priceNegative: String,
    currencyLength: String,
    billingNumber: String,
    billingPositive: String,
): String? = when (step) {
    0 -> when {
        input.name.isBlank() -> nameRequired
        input.provider.isBlank() -> providerRequired
        input.category.isBlank() -> categoryRequired
        else -> null
    }
    1 -> {
        val amount = input.price.toBigDecimalOrNull()
        val count = input.billingCount.toIntOrNull()
        when {
            amount == null -> priceNumber
            amount < BigDecimal.ZERO -> priceNegative
            input.currencyCode.trim().length != 3 -> currencyLength
            count == null -> billingNumber
            count <= 0 -> billingPositive
            else -> null
        }
    }
    else -> null
}

@Composable
private fun EventDialog(
    existing: RecurringEvent?,
    defaultOffset: String,
    defaultTime: String,
    onDismiss: () -> Unit,
    onSave: (UUID?, EventInput) -> Unit,
    onDelete: (UUID) -> Unit,
) {
    var input by remember(existing) {
        mutableStateOf(existing?.toInput() ?: EventInput(reminderOffset = defaultOffset, reminderTime = defaultTime))
    }
    val unitOptions = mutableMapOf<String, RecurrenceUnit>()
    for (unit in RecurrenceUnit.entries) {
        unitOptions[unitLabel(unit)] = unit
    }
    FormDialog(
        if (existing == null) stringResource(R.string.add_event_title) else stringResource(R.string.edit_event_title),
        onDismiss,
        { onSave(existing?.id, input) },
        existing?.let { { onDelete(it.id) } },
    ) {
        Field(stringResource(R.string.field_title), input.title) { input = input.copy(title = it) }
        ChoiceMenu(stringResource(R.string.field_type), input.type.name, listOf("BILLING", "EXPIRATION", "TRIAL_END", "QUOTA_RESET", "CUSTOM")) { input = input.copy(type = RecurringEventType.valueOf(it)) }
        Field(stringResource(R.string.field_logical_date), input.date) { input = input.copy(date = it) }
        ChoiceMenu(stringResource(R.string.field_recurrence), input.recurrence, listOf("ONE_TIME", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "CUSTOM")) { input = input.copy(recurrence = it) }
        if (input.recurrence == "CUSTOM") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(R.string.field_every_n), input.customCount, Modifier.weight(1f)) { input = input.copy(customCount = it) }
            ChoiceMenu(stringResource(R.string.field_unit), unitLabel(input.customUnit), unitOptions.keys.toList()) { input = input.copy(customUnit = unitOptions.getValue(it)) }
        }
        Field(stringResource(R.string.field_timezone), input.timezone) { input = input.copy(timezone = it) }
        CheckRow(stringResource(R.string.field_event_enabled), input.enabled) { input = input.copy(enabled = it) }
        CheckRow(stringResource(R.string.field_reminder_enabled), input.reminderEnabled) { input = input.copy(reminderEnabled = it) }
        if (input.reminderEnabled) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(R.string.field_days_before), input.reminderOffset, Modifier.weight(1f)) { input = input.copy(reminderOffset = it) }
            Field(stringResource(R.string.field_time), input.reminderTime, Modifier.weight(1f)) { input = input.copy(reminderTime = it) }
        }
        Field(stringResource(R.string.field_notes), input.notes) { input = input.copy(notes = it) }
    }
}

@Composable
private fun QuotaDialog(existing: Quota?, events: List<RecurringEvent>, onDismiss: () -> Unit, onSave: (UUID?, QuotaInput) -> Unit, onDelete: (UUID) -> Unit) {
    var input by remember(existing) { mutableStateOf(existing?.toInput() ?: QuotaInput()) }
    val noneText = stringResource(R.string.none)
    FormDialog(
        if (existing == null) stringResource(R.string.add_quota_title) else stringResource(R.string.edit_quota_title),
        onDismiss,
        { onSave(existing?.id, input) },
        existing?.let { { onDelete(it.id) } },
    ) {
        Field(stringResource(R.string.field_quota_name), input.name) { input = input.copy(name = it) }
        Field(stringResource(R.string.field_quota_unit), input.unit) { input = input.copy(unit = it) }
        CheckRow(stringResource(R.string.unlimited), input.unlimited) { input = input.copy(unlimited = it) }
        if (!input.unlimited) {
            Field(stringResource(R.string.field_used), input.used) { input = input.copy(used = it) }
            Field(stringResource(R.string.field_remaining), input.remaining) { input = input.copy(remaining = it) }
            Field(stringResource(R.string.field_limit), input.limit) { input = input.copy(limit = it) }
            Field(stringResource(R.string.field_percentage), input.percentage) { input = input.copy(percentage = it) }
        }
        ChoiceMenu(
            stringResource(R.string.field_reset_event),
            events.firstOrNull { it.id.toString() == input.resetEventId }?.title ?: noneText,
            listOf(noneText) + events.map { it.title },
        ) { title ->
            input = input.copy(resetEventId = events.firstOrNull { it.title == title }?.id?.toString().orEmpty())
        }
    }
}

@Composable
private fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    confirmText: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp).heightIn(max = 760.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } }
                    onBack?.let { TextButton(onClick = it) { Text(stringResource(R.string.back)) } }
                    Spacer(Modifier.weight(1f)); TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }; Button(onClick = onSave) { Text(confirmText ?: stringResource(R.string.save)) }
                }
            }
        }
    }
}

@Composable private fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) = MaterialButton(onClick, modifier.sizeIn(minHeight = 48.dp), enabled, content = content)
@Composable private fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) = MaterialOutlinedButton(onClick, modifier.sizeIn(minHeight = 48.dp), enabled, content = content)
@Composable private fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) = MaterialTextButton(onClick, modifier.sizeIn(minHeight = 44.dp), enabled, content = content)
@Composable private fun DangerButton(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) = MaterialButton(
    onClick = onClick,
    modifier = Modifier.sizeIn(minHeight = 48.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    ),
    content = content,
)

@Composable
private fun RowScope.NavigationItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tween(180),
        label = "navigationBackground",
    )
    val foreground by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(180),
        label = "navigationForeground",
    )
        MaterialTextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).background(background, MaterialTheme.shapes.medium).semantics {
            this.selected = selected
            role = Role.Tab
        },
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = foreground,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable private fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) = OutlinedTextField(value, onChange, modifier.sizeIn(minHeight = 56.dp), label = { Text(label) }, singleLine = label != stringResource(R.string.field_notes), shape = MaterialTheme.shapes.medium)
@Composable private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.sizeIn(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label) } }

@Composable
private fun ChoiceMenu(label: String, selected: String, choices: List<String>, onChoice: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${selected.replace('_', ' ')}", maxLines = 1) }
        DropdownMenu(expanded, { expanded = false }) { choices.forEach { choice -> DropdownMenuItem({ Text(choice.replace('_', ' ')) }, { expanded = false; onChoice(choice) }) } }
    }
}

@Composable private fun CycleButton(text: String, onClick: () -> Unit) = OutlinedButton(onClick) { Text(text.replace('_', ' ')) }
@Composable private fun ToggleButton(text: String, selected: Boolean, onClick: () -> Unit) = if (selected) Button(onClick) { Text(text) } else OutlinedButton(onClick) { Text(text) }

@Composable
private fun CompactRow(title: String, subtitle: String, trailing: String, onClick: (() -> Unit)?) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(120), label = "rowPress")
    val rowColor by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
        tween(120),
        label = "rowBackground",
    )
    val interaction = if (onClick == null) Modifier else Modifier.clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick,
    )
    Row(
        Modifier.fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.small)
            .background(rowColor)
            .then(interaction)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text(trailing, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider(Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable private fun Metric(label: String, value: String) { Column(Modifier.widthIn(min = 128.dp)) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(4.dp)); Text(value, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace) } }
@Composable private fun CurrencyMetric(label: String, values: Map<Currency, BigDecimal>) = Metric(label, if (values.isEmpty()) "—" else values.entries.joinToString("\n") { "${it.key.currencyCode} ${it.value.pretty()}" })
@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplatePicker(onPick: (SubscriptionTemplate) -> Unit) {
    SectionTitle(stringResource(R.string.subscription_templates))
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.subscription_templates_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (template in SubscriptionTemplates.all) {
            OutlinedButton(onClick = { onPick(template) }) { Text(template.name) }
        }
    }
}
@Composable private fun EmptyState(title: String, detail: String) { Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) { Text(title, fontWeight = FontWeight.Medium); Spacer(Modifier.height(4.dp)); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) } } }
@Composable private fun ConfirmDialog(title: String, body: String, action: String, confirm: () -> Unit, dismiss: () -> Unit, destructive: Boolean = false) = AlertDialog(
    onDismissRequest = dismiss,
    confirmButton = {
        if (destructive) DangerButton(onClick = confirm) { Text(action) }
        else Button(onClick = confirm) { Text(action) }
    },
    dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.keep)) } },
    title = { Text(title) },
    text = { Text(body) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineUsageSection(subscription: Subscription, state: CoreState, model: CoreViewModel) {
    var apiKey by remember(subscription.id) { mutableStateOf("") }
    val none = stringResource(R.string.online_provider_none)
    val selected = OnlineProviders.find(subscription.onlineProviderId)?.displayName ?: none
    val choices = listOf(none) + OnlineProviders.all.map { it.displayName }
    SectionTitle(stringResource(R.string.online_usage))
    Text(stringResource(R.string.online_usage_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    ChoiceMenu(stringResource(R.string.online_provider), selected, choices) { label ->
        val providerId = OnlineProviders.all.firstOrNull { it.displayName == label }?.id
        model.updateOnlineProvider(subscription.id, providerId)
    }
    val providerId = subscription.onlineProviderId
    if (providerId != null) {
        Spacer(Modifier.height(8.dp))
        Text(coverageText(providerId), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp),
            label = { Text(stringResource(R.string.online_api_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = MaterialTheme.shapes.medium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { model.saveApiKey(subscription.id, apiKey) { if (it.isSuccess) apiKey = "" } }) {
                Text(stringResource(R.string.save_api_key))
            }
            if (subscription.id in state.linkedKeyIds) {
                TextButton(onClick = { model.clearApiKey(subscription.id) }) { Text(stringResource(R.string.remove_api_key)) }
                OutlinedButton(onClick = { model.refreshOnlineQuotas(subscription.id) }) { Text(stringResource(R.string.refresh_usage)) }
            }
        }
        if (subscription.id in state.linkedKeyIds) {
            Text(stringResource(R.string.api_key_saved_label), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun coverageText(providerId: String): String = when (providerId) {
    "openrouter" -> stringResource(R.string.online_coverage_openrouter)
    "deepseek" -> stringResource(R.string.online_coverage_deepseek)
    "moonshot" -> stringResource(R.string.online_coverage_moonshot)
    "moonshot-intl" -> stringResource(R.string.online_coverage_moonshot_intl)
    "zai" -> stringResource(R.string.online_coverage_zai)
    "bigmodel" -> stringResource(R.string.online_coverage_bigmodel)
    else -> ""
}

@Composable
private fun quotaTitle(quota: Quota): String = quota.name

@Composable
private fun quotaSubtitle(quota: Quota): String {
    val unit = if (quota.unlimited) stringResource(R.string.unlimited) else quota.unit
    if (quota.origin != QuotaOrigin.ONLINE) return unit
    val status = when (quota.syncState) {
        QuotaSyncState.FRESH -> stringResource(R.string.quota_sync_fresh)
        QuotaSyncState.STALE -> stringResource(R.string.quota_sync_stale)
        QuotaSyncState.UNAVAILABLE -> stringResource(R.string.quota_sync_unavailable)
        QuotaSyncState.AUTH_REQUIRED -> stringResource(R.string.quota_sync_auth)
    }
    val note = quotaNote(quota.syncNote)
    return if (note == null) "$unit · $status" else "$unit · $status · $note"
}

@Composable
private fun quotaNote(note: String?): String? {
    if (note.isNullOrBlank()) return null
    if (note == "over-limit") return stringResource(R.string.quota_note_over_limit)
    if (note == "not-reported") return stringResource(R.string.quota_note_not_reported)
    if (note == "refresh-failed") return stringResource(R.string.quota_note_refresh_failed)
    if (note == "auth-rejected") return stringResource(R.string.quota_note_auth)
    if (note.startsWith("granted=") && note.contains(";topped=")) {
        val granted = note.substringAfter("granted=").substringBefore(";topped=")
        val topped = note.substringAfter(";topped=")
        return stringResource(R.string.quota_note_granted, granted, topped)
    }
    if (note.startsWith("deficit=") && note.contains(";currency=")) {
        val amount = note.substringAfter("deficit=").substringBefore(";currency=")
        val currency = note.substringAfter(";currency=")
        return stringResource(R.string.quota_note_deficit, amount, currency)
    }
    return null
}

@Composable
private fun quotaSummary(quota: Quota): String {
    if (quota.unlimited) return stringResource(R.string.unlimited)
    val used = quota.used?.pretty()
    val limit = quota.limit?.pretty()
    val remaining = quota.remaining?.pretty()
    val percentage = quota.percentage?.pretty() ?: quota.usedPercentage()?.pretty()
    return when {
        used != null && limit != null -> stringResource(R.string.quota_used_of_limit, used, limit)
        remaining != null && limit != null -> stringResource(R.string.quota_remaining_of_limit, remaining, limit)
        remaining != null -> stringResource(R.string.quota_remaining, remaining)
        percentage != null -> "$percentage%"
        else -> "—"
    }
}

@Composable
private fun nextEventLine(subscription: Subscription, events: List<RecurringEvent>): String? {
    val today = LocalDate.now()
    val engine = remember { RecurrenceEngine() }
    val next = events.asSequence()
        .filter { it.subscriptionId == subscription.id && it.enabled }
        .mapNotNull { event -> engine.occurrenceOnOrAfter(event.nextOccurrence, event.recurrenceRule, today)?.let { event to it } }
        .minByOrNull { it.second }
        ?: return null
    return stringResource(R.string.list_next_event, eventTypeLabel(next.first.type), next.second.toString())
}

@Composable
private fun activityFilterLabel(filter: ActivityFilter): String = when (filter) {
    ActivityFilter.ALL -> stringResource(R.string.activity_all)
    ActivityFilter.ACTIVE -> stringResource(R.string.activity_active)
    ActivityFilter.INACTIVE -> stringResource(R.string.activity_inactive)
}

@Composable
private fun sortLabel(sort: SubscriptionSort): String = when (sort) {
    SubscriptionSort.NEXT_EVENT -> stringResource(R.string.sort_next_event)
    SubscriptionSort.PRICE -> stringResource(R.string.sort_price)
    SubscriptionSort.NAME -> stringResource(R.string.sort_name)
    SubscriptionSort.RECENTLY_ADDED -> stringResource(R.string.sort_recently_added)
}

private fun BigDecimal.pretty() = stripTrailingZeros().toPlainString()
private fun Subscription.toInput() = SubscriptionInput(name, provider, category, price.toPlainString(), currency.currencyCode, billingInterval ?: RecurrenceUnit.MONTHS, billingIntervalCount?.toString() ?: "1", autoRenew, startDate.toString(), expirationDate?.toString().orEmpty(), trialEndDate?.toString().orEmpty(), notes, onlineProviderId = onlineProviderId.orEmpty())
private fun RecurringEvent.toInput(): EventInput {
    val recurrence = when (val rule = recurrenceRule) {
        RecurrenceRule.OneTime -> "ONE_TIME"
        is RecurrenceRule.Repeating -> when {
            rule.interval == 1 && rule.unit == RecurrenceUnit.DAYS -> "DAILY"
            rule.interval == 1 && rule.unit == RecurrenceUnit.WEEKS -> "WEEKLY"
            rule.interval == 1 && rule.unit == RecurrenceUnit.MONTHS -> "MONTHLY"
            rule.interval == 3 && rule.unit == RecurrenceUnit.MONTHS -> "QUARTERLY"
            rule.interval == 1 && rule.unit == RecurrenceUnit.YEARS -> "YEARLY"
            else -> "CUSTOM"
        }
    }
    val repeating = recurrenceRule as? RecurrenceRule.Repeating
    return EventInput(title, type, nextOccurrence.toString(), recurrence, repeating?.interval?.toString() ?: "1", repeating?.unit ?: RecurrenceUnit.MONTHS, timezone.id, enabled, reminders.enabled, reminders.offsets.firstOrNull()?.daysBefore?.toString() ?: "1", reminders.notificationTime.toString(), notes)
}
private fun Quota.toInput() = QuotaInput(name, unit, used?.toPlainString().orEmpty(), remaining?.toPlainString().orEmpty(), limit?.toPlainString().orEmpty(), percentage?.toPlainString().orEmpty(), unlimited, resetEventId?.toString().orEmpty())

@Composable
private fun statusLabel(status: SubscriptionStatus): String = stringResource(
    when (status) {
        SubscriptionStatus.ACTIVE -> R.string.status_active
        SubscriptionStatus.CANCELLED_PENDING_EXPIRY -> R.string.status_cancelled_pending_expiry
        SubscriptionStatus.EXPIRED -> R.string.status_expired
        SubscriptionStatus.PAUSED -> R.string.status_paused
        SubscriptionStatus.ARCHIVED -> R.string.status_archived
    },
)

@Composable
private fun eventTypeLabel(type: RecurringEventType): String = stringResource(
    when (type) {
        RecurringEventType.BILLING -> R.string.event_type_billing
        RecurringEventType.EXPIRATION -> R.string.event_type_expiration
        RecurringEventType.TRIAL_END -> R.string.event_type_trial_end
        RecurringEventType.QUOTA_RESET -> R.string.event_type_quota_reset
        RecurringEventType.CUSTOM -> R.string.event_type_custom
        RecurringEventType.PRICE_CHANGE -> R.string.event_type_price_change
        RecurringEventType.PROMOTION_END -> R.string.event_type_promotion_end
        RecurringEventType.CONTRACT_NOTICE -> R.string.event_type_contract_notice
    },
)

@Composable
private fun unitLabel(unit: RecurrenceUnit): String = stringResource(
    when (unit) {
        RecurrenceUnit.DAYS -> R.string.unit_days
        RecurrenceUnit.WEEKS -> R.string.unit_weeks
        RecurrenceUnit.MONTHS -> R.string.unit_months
        RecurrenceUnit.YEARS -> R.string.unit_years
    },
)

@Composable
private fun recurrenceDisplay(rule: RecurrenceRule): String = when (rule) {
    RecurrenceRule.OneTime -> stringResource(R.string.one_time)
    is RecurrenceRule.Repeating -> stringResource(R.string.every_n_unit, rule.interval, unitLabel(rule.unit))
}
