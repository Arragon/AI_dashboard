package com.subscriptiontracker.domain.catalog

import com.subscriptiontracker.domain.model.RecurrenceUnit

data class SubscriptionTemplate(
    val id: String,
    val name: String,
    val provider: String,
    val category: String,
    val currencyCode: String,
    val billingUnit: RecurrenceUnit = RecurrenceUnit.MONTHS,
    val billingCount: Int = 1,
) {
    init {
        require(id.isNotBlank()) { "Template id cannot be blank" }
        require(name.isNotBlank()) { "Template name cannot be blank" }
        require(provider.isNotBlank()) { "Template provider cannot be blank" }
        require(category.isNotBlank()) { "Template category cannot be blank" }
        require(currencyCode.length == 3) { "Template currency must be a 3-letter ISO code" }
        require(billingCount > 0) { "Template billing count must be positive" }
    }
}

object SubscriptionTemplates {
    val all: List<SubscriptionTemplate> = listOf(
        SubscriptionTemplate("cursor", "Cursor", "Cursor", "AI", "USD"),
        SubscriptionTemplate("qoder", "Qoder", "Qoder", "AI", "USD"),
        SubscriptionTemplate("codex", "Codex", "OpenAI", "AI", "USD"),
        SubscriptionTemplate("cloud-code", "Cloud Code", "Cloud Code", "AI", "USD"),
        SubscriptionTemplate("devin", "Devin", "Cognition", "AI", "USD"),
        SubscriptionTemplate("workbuddy", "Workbuddy", "Tencent", "AI", "USD"),
    )

    fun find(id: String): SubscriptionTemplate? = all.firstOrNull { it.id == id }
}
