package com.subscriptiontracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.RecurringEventType
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.query.ActivityFilter
import com.subscriptiontracker.domain.query.SubscriptionSort
import com.subscriptiontracker.platform.reminder.NotificationPermissionStatus
import com.subscriptiontracker.presentation.CoreState
import com.subscriptiontracker.presentation.CoreViewModel
import com.subscriptiontracker.presentation.EventInput
import com.subscriptiontracker.presentation.QuotaInput
import com.subscriptiontracker.presentation.SubscriptionInput
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
) {
    var page by remember { mutableStateOf(Page.OVERVIEW) }
    var detailId by remember { mutableStateOf<UUID?>(null) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ledger", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { page = Page.SETTINGS; detailId = null }, modifier = Modifier.semantics { contentDescription = "Open settings" }) { Text("Settings") }
                }
                Divider(color = MaterialTheme.colorScheme.outline)
            }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
                Page.entries.forEach { item ->
                    TextButton(onClick = { page = item; detailId = null }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Text(item.name.lowercase().replaceFirstChar(Char::uppercase), color = if (page == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxWidth().widthIn(max = 1100.dp)) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading" }) }
                    detailId != null -> state.subscriptions.firstOrNull { it.id == detailId }?.let { sub ->
                        DetailScreen(sub, state, onBack = { detailId = null }, onEdit = { editing = sub }, model = model)
                    }
                    page == Page.OVERVIEW -> OverviewScreen(state) { detailId = it }
                    page == Page.SUBSCRIPTIONS -> SubscriptionsScreen(state, model, { detailId = it }, { adding = true })
                    page == Page.INSIGHTS -> InsightsScreen(state)
                    page == Page.SETTINGS -> SettingsScreen(notificationStatus, onRequestNotifications, onOpenAppSettings, onExport, onImport)
                }
            }
        }
    }
    if (adding || editing != null) SubscriptionDialog(editing, onDismiss = { adding = false; editing = null }) { id, input ->
        model.saveSubscription(id, input) { if (it.isSuccess) { adding = false; editing = null } }
    }
    state.pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = model::dismissImport,
            title = { Text("Replace all local data?") },
            text = { Text("Validated backup: ${preview.summary}. Replace removes all current subscriptions, events, and quotas before restoring this file.") },
            confirmButton = { Button(onClick = model::confirmImport) { Text("Replace and restore") } },
            dismissButton = { TextButton(onClick = model::dismissImport) { Text("Cancel") } },
        )
    }
    val feedback = state.error ?: state.message
    if (feedback != null) AlertDialog(
        onDismissRequest = model::clearMessage,
        text = { Text(feedback) },
        confirmButton = { TextButton(onClick = model::clearMessage) { Text("OK") } },
    )
}

@Composable
private fun PageBody(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewScreen(state: CoreState, onOpen: (UUID) -> Unit) = PageBody("Overview", "Current commitments without currency conversion") {
    if (state.subscriptions.isEmpty()) {
        EmptyState("No subscriptions yet", "Add one from Subscriptions to begin tracking spend and events.")
        return@PageBody
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Active", state.activeCount.toString())
                CurrencyMetric("This month", state.currentMonth)
                CurrencyMetric("Next 30 days", state.next30Days)
                CurrencyMetric("Annualized", state.annualized)
            }
        }
        item { SectionTitle("Upcoming events") }
        if (state.upcoming.isEmpty()) item { EmptyState("Nothing due in 30 days", "Enabled billing, trial, expiration, quota, and custom events appear here.") }
        items(state.upcoming, key = { it.event.id }) { item ->
            CompactRow(
                title = item.event.title,
                subtitle = "${item.subscriptionName} · ${item.event.type.name.replace('_', ' ')}",
                trailing = item.date.toString(),
                onClick = { onOpen(item.event.subscriptionId) },
            )
        }
    }
}

@Composable
private fun SubscriptionsScreen(state: CoreState, model: CoreViewModel, onOpen: (UUID) -> Unit, onAdd: () -> Unit) = PageBody("Subscriptions") {
    Column {
        OutlinedTextField(
            value = state.query.searchText,
            onValueChange = model::updateSearch,
            label = { Text("Search name, provider, category, notes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search subscriptions" },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CycleButton("Activity: ${state.query.filters.activity.name}") {
                model.updateActivity(ActivityFilter.entries[(state.query.filters.activity.ordinal + 1) % ActivityFilter.entries.size])
            }
            ToggleButton("Due soon", state.query.filters.dueSoon) { model.updateDueSoon(!state.query.filters.dueSoon) }
            ChoiceMenu("Category", state.query.filters.category ?: "All", listOf("All") + state.subscriptions.map { it.category }.distinct().sorted()) {
                model.updateCategory(it.takeUnless { value -> value == "All" })
            }
            ChoiceMenu("Currency", state.query.filters.currency?.currencyCode ?: "All", listOf("All") + state.subscriptions.map { it.currency.currencyCode }.distinct().sorted()) {
                model.updateCurrency(it.takeUnless { value -> value == "All" }?.let(Currency::getInstance))
            }
            ChoiceMenu("Sort", state.query.sort.name, SubscriptionSort.entries.map { it.name }) { model.updateSort(SubscriptionSort.valueOf(it)) }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onAdd, modifier = Modifier.align(Alignment.End).semantics { contentDescription = "Add subscription" }) { Text("Add subscription") }
        Spacer(Modifier.height(10.dp))
        if (state.visibleSubscriptions.isEmpty()) EmptyState("No matching subscriptions", "Change filters or add a subscription.")
        else LazyColumn {
            items(state.visibleSubscriptions, key = { it.id }) { sub ->
                CompactRow(sub.name, "${sub.provider} · ${sub.category} · ${sub.status.name.replace('_', ' ')}", "${sub.currency.currencyCode} ${sub.price}") { onOpen(sub.id) }
            }
        }
    }
}

@Composable
private fun InsightsScreen(state: CoreState) = PageBody("Insights", "Amounts remain separated by currency") {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SectionTitle("Annual recurring spend") }
        if (state.annualized.isEmpty()) item { EmptyState("No recurring spend", "Active auto-renewing subscriptions with billing intervals appear here.") }
        else items(state.annualized.entries.toList()) { (currency, amount) -> CompactRow(currency.currencyCode, "Annualized", amount.pretty(), null) }
        item { SectionTitle("Current month by category") }
        val rows = state.subscriptions.groupBy { it.category }.map { (category, subscriptions) ->
            category to subscriptions.groupBy { it.currency }.mapValues { (_, values) -> values.fold(BigDecimal.ZERO) { total, sub -> total + sub.price } }
        }
        items(rows) { (category, totals) -> CompactRow(category, "Listed recurring prices", totals.entries.joinToString(" · ") { "${it.key.currencyCode} ${it.value.pretty()}" }, null) }
    }
}

@Composable
private fun SettingsScreen(
    status: NotificationPermissionStatus,
    onRequest: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) = PageBody("Settings") {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            SectionTitle("Notifications")
            Text(when (status) {
                NotificationPermissionStatus.GRANTED -> "Allowed. Event reminders can be delivered."
                NotificationPermissionStatus.NOT_REQUIRED -> "Permission is not required on this Android version."
                NotificationPermissionStatus.DENIED -> "Denied. Reminders are scheduled but cannot notify until allowed."
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status == NotificationPermissionStatus.DENIED) Button(onClick = onRequest) { Text("Request permission") }
                OutlinedButton(onClick = onSettings) { Text("Open app settings") }
            }
        }
        item {
            Divider(); Spacer(Modifier.height(16.dp)); SectionTitle("Local defaults")
            Text("Currency: USD · Reminder: 1 day before at 09:00", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Shown as suggested defaults only; these values are not persisted.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Divider(); Spacer(Modifier.height(16.dp)); SectionTitle("Backup")
            Text("Export or replace all local records using Android's document picker. No network is used.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, modifier = Modifier.semantics { contentDescription = "Export backup document" }) { Text("Export") }
                OutlinedButton(onClick = onImport, modifier = Modifier.semantics { contentDescription = "Import backup document" }) { Text("Import / Replace") }
            }
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
    PageBody(subscription.name, "${subscription.provider} · ${subscription.category}") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TextButton(onClick = onBack) { Text("Back") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEdit) { Text("Edit") }
                    if (subscription.status.name == "ACTIVE") OutlinedButton(onClick = { cancelConfirm = true }) { Text("Cancel at expiry") }
                    if (subscription.status.name != "ARCHIVED") OutlinedButton(onClick = { model.archive(subscription.id) }) { Text("Archive") }
                    TextButton(onClick = { deleteConfirm = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
            item { CompactRow("Status", subscription.status.name.replace('_', ' '), "${subscription.currency.currencyCode} ${subscription.price}", null) }
            item { SectionTitle("Recurring events"); Button(onClick = { addEvent = true }) { Text("Add event") } }
            if (events.isEmpty()) item { EmptyState("No events", "Add billing, expiration, trial, quota reset, or custom events.") }
            items(events, key = { it.id }) { event ->
                CompactRow(event.title, "${event.type.name.replace('_', ' ')} · ${event.recurrenceRule.label()}", event.nextOccurrence.toString()) { eventEdit = event }
            }
            item { SectionTitle("Quotas"); Button(onClick = { addQuota = true }) { Text("Add quota") } }
            if (quotas.isEmpty()) item { EmptyState("No quotas", "Track plan limits manually.") }
            items(quotas, key = { it.id }) { quota ->
                CompactRow(quota.name, if (quota.unlimited) "Unlimited" else quota.unit, quota.usedPercentage()?.let { "$it%" } ?: "—") { quotaEdit = quota }
            }
        }
    }
    if (deleteConfirm) ConfirmDialog("Delete subscription?", "This permanently deletes the subscription and its events and quotas.", "Delete", { model.deleteSubscription(subscription.id) { if (it.isSuccess) onBack() }; deleteConfirm = false }, { deleteConfirm = false })
    if (cancelConfirm) ConfirmDialog("Cancel at period end?", "Auto-renew will turn off and status becomes pending expiry. The current expiration date is used when available.", "Cancel subscription", { model.cancel(subscription.id, subscription.expirationDate); cancelConfirm = false }, { cancelConfirm = false })
    if (addEvent || eventEdit != null) EventDialog(eventEdit, { addEvent = false; eventEdit = null }, { id, input -> model.saveEvent(subscription.id, id, input) { if (it.isSuccess) { addEvent = false; eventEdit = null } } }, { id -> model.deleteEvent(id); eventEdit = null })
    if (addQuota || quotaEdit != null) QuotaDialog(quotaEdit, events, { addQuota = false; quotaEdit = null }, { id, input -> model.saveQuota(subscription.id, id, input) { if (it.isSuccess) { addQuota = false; quotaEdit = null } } }, { id -> model.deleteQuota(id); quotaEdit = null })
}

@Composable
private fun SubscriptionDialog(existing: Subscription?, onDismiss: () -> Unit, onSave: (UUID?, SubscriptionInput) -> Unit) {
    var input by remember(existing) { mutableStateOf(existing?.toInput() ?: SubscriptionInput()) }
    FormDialog(if (existing == null) "Add subscription" else "Edit subscription", onDismiss, { onSave(existing?.id, input) }) {
        Field("Name *", input.name) { input = input.copy(name = it) }
        Field("Provider *", input.provider) { input = input.copy(provider = it) }
        Field("Category *", input.category) { input = input.copy(category = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Price *", input.price, Modifier.weight(1f)) { input = input.copy(price = it) }
            Field("ISO currency *", input.currencyCode, Modifier.weight(1f)) { input = input.copy(currencyCode = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChoiceMenu("Billing unit", input.billingUnit.name, RecurrenceUnit.entries.map { it.name }) { input = input.copy(billingUnit = RecurrenceUnit.valueOf(it)) }
            Field("Every N", input.billingCount, Modifier.weight(1f)) { input = input.copy(billingCount = it) }
        }
        CheckRow("Auto-renew", input.autoRenew) { input = input.copy(autoRenew = it) }
        Field("Start date * (YYYY-MM-DD)", input.startDate) { input = input.copy(startDate = it) }
        Field("Expiration date (optional)", input.expirationDate) { input = input.copy(expirationDate = it) }
        Field("Trial end date (optional)", input.trialEndDate) { input = input.copy(trialEndDate = it) }
        Field("Notes", input.notes) { input = input.copy(notes = it) }
    }
}

@Composable
private fun EventDialog(existing: RecurringEvent?, onDismiss: () -> Unit, onSave: (UUID?, EventInput) -> Unit, onDelete: (UUID) -> Unit) {
    var input by remember(existing) { mutableStateOf(existing?.toInput() ?: EventInput()) }
    FormDialog(if (existing == null) "Add event" else "Edit event", onDismiss, { onSave(existing?.id, input) }, existing?.let { { onDelete(it.id) } }) {
        Field("Title *", input.title) { input = input.copy(title = it) }
        ChoiceMenu("Type", input.type.name, listOf("BILLING", "EXPIRATION", "TRIAL_END", "QUOTA_RESET", "CUSTOM")) { input = input.copy(type = RecurringEventType.valueOf(it)) }
        Field("Logical date * (YYYY-MM-DD)", input.date) { input = input.copy(date = it) }
        ChoiceMenu("Recurrence", input.recurrence, listOf("ONE_TIME", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "CUSTOM")) { input = input.copy(recurrence = it) }
        if (input.recurrence == "CUSTOM") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Every N", input.customCount, Modifier.weight(1f)) { input = input.copy(customCount = it) }
            ChoiceMenu("Unit", input.customUnit.name, RecurrenceUnit.entries.map { it.name }) { input = input.copy(customUnit = RecurrenceUnit.valueOf(it)) }
        }
        Field("Timezone *", input.timezone) { input = input.copy(timezone = it) }
        CheckRow("Event enabled", input.enabled) { input = input.copy(enabled = it) }
        CheckRow("Reminder enabled", input.reminderEnabled) { input = input.copy(reminderEnabled = it) }
        if (input.reminderEnabled) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Days before", input.reminderOffset, Modifier.weight(1f)) { input = input.copy(reminderOffset = it) }
            Field("Time (HH:MM)", input.reminderTime, Modifier.weight(1f)) { input = input.copy(reminderTime = it) }
        }
        Field("Notes", input.notes) { input = input.copy(notes = it) }
    }
}

@Composable
private fun QuotaDialog(existing: Quota?, events: List<RecurringEvent>, onDismiss: () -> Unit, onSave: (UUID?, QuotaInput) -> Unit, onDelete: (UUID) -> Unit) {
    var input by remember(existing) { mutableStateOf(existing?.toInput() ?: QuotaInput()) }
    FormDialog(if (existing == null) "Add quota" else "Edit quota", onDismiss, { onSave(existing?.id, input) }, existing?.let { { onDelete(it.id) } }) {
        Field("Name *", input.name) { input = input.copy(name = it) }
        Field("Unit *", input.unit) { input = input.copy(unit = it) }
        CheckRow("Unlimited", input.unlimited) { input = input.copy(unlimited = it) }
        if (!input.unlimited) {
            Field("Used (optional)", input.used) { input = input.copy(used = it) }
            Field("Remaining (optional)", input.remaining) { input = input.copy(remaining = it) }
            Field("Limit (optional)", input.limit) { input = input.copy(limit = it) }
            Field("Percentage (optional)", input.percentage) { input = input.copy(percentage = it) }
        }
        ChoiceMenu("Reset event", events.firstOrNull { it.id.toString() == input.resetEventId }?.title ?: "None", listOf("None") + events.map { it.title }) { title ->
            input = input.copy(resetEventId = events.firstOrNull { it.title == title }?.id?.toString().orEmpty())
        }
    }
}

@Composable
private fun FormDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().widthIn(max = 620.dp).heightIn(max = 760.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            Spacer(Modifier.height(12.dp)); Divider();
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                Spacer(Modifier.weight(1f)); TextButton(onClick = onDismiss) { Text("Cancel") }; Button(onClick = onSave) { Text("Save") }
            }
        }
    }
}

@Composable private fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) = OutlinedTextField(value, onChange, modifier, label = { Text(label) }, singleLine = label != "Notes")
@Composable private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.sizeIn(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label) } }

@Composable
private fun ChoiceMenu(label: String, selected: String, choices: List<String>, onChoice: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("$label: ${selected.replace('_', ' ')}", maxLines = 1) }
        DropdownMenu(expanded, { expanded = false }) { choices.forEach { choice -> DropdownMenuItem({ Text(choice.replace('_', ' ')) }, { expanded = false; onChoice(choice) }) } }
    }
}

@Composable private fun CycleButton(text: String, onClick: () -> Unit) = OutlinedButton(onClick, Modifier.sizeIn(minHeight = 48.dp)) { Text(text.replace('_', ' ')) }
@Composable private fun ToggleButton(text: String, selected: Boolean, onClick: () -> Unit) = if (selected) Button(onClick, Modifier.sizeIn(minHeight = 48.dp)) { Text(text) } else OutlinedButton(onClick, Modifier.sizeIn(minHeight = 48.dp)) { Text(text) }

@Composable
private fun CompactRow(title: String, subtitle: String, trailing: String, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp).sizeIn(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.width(12.dp)); Text(trailing, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
    Divider(color = MaterialTheme.colorScheme.outline)
}

@Composable private fun Metric(label: String, value: String) { Column(Modifier.widthIn(min = 120.dp)) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace) } }
@Composable private fun CurrencyMetric(label: String, values: Map<Currency, BigDecimal>) = Metric(label, if (values.isEmpty()) "—" else values.entries.joinToString("\n") { "${it.key.currencyCode} ${it.value.pretty()}" })
@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
@Composable private fun EmptyState(title: String, detail: String) { Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) { Text(title, fontWeight = FontWeight.Medium); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun ConfirmDialog(title: String, body: String, action: String, confirm: () -> Unit, dismiss: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss,
    confirmButton = { Button(onClick = confirm) { Text(action) } },
    dismissButton = { TextButton(onClick = dismiss) { Text("Keep") } },
    title = { Text(title) },
    text = { Text(body) },
)

private fun BigDecimal.pretty() = stripTrailingZeros().toPlainString()
private fun RecurrenceRule.label() = when (this) { RecurrenceRule.OneTime -> "One time"; is RecurrenceRule.Repeating -> "Every $interval ${unit.name.lowercase()}" }
private fun Subscription.toInput() = SubscriptionInput(name, provider, category, price.toPlainString(), currency.currencyCode, billingInterval ?: RecurrenceUnit.MONTHS, billingIntervalCount?.toString() ?: "1", autoRenew, startDate.toString(), expirationDate?.toString().orEmpty(), trialEndDate?.toString().orEmpty(), notes)
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
