package com.subscriptiontracker.domain.catalog

import com.subscriptiontracker.presentation.toInput
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionTemplateTest {
    @Test
    fun catalogOffersTheRequestedCodingSubscriptions() {
        assertEquals(
            listOf("Cursor", "Qoder", "Codex", "Cloud Code", "Devin", "Workbuddy"),
            SubscriptionTemplates.all.map { it.name },
        )
        assertEquals(SubscriptionTemplates.all.size, SubscriptionTemplates.all.map { it.id }.distinct().size)
    }

    @Test
    fun templateLeavesPriceBlankAndFillsIdentityAndMonthlyBilling() {
        val template = SubscriptionTemplates.find("codex")
        requireNotNull(template)
        val input = template.toInput(LocalDate.parse("2026-09-22"))
        assertEquals("Codex", input.name)
        assertEquals("OpenAI", input.provider)
        assertEquals("AI", input.category)
        assertEquals("", input.price)
        assertEquals("USD", input.currencyCode)
        assertEquals("1", input.billingCount)
        assertEquals("2026-09-22", input.startDate)
        assertTrue(input.autoRenew)
    }
}
