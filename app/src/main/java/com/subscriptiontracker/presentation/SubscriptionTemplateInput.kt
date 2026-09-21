package com.subscriptiontracker.presentation

import com.subscriptiontracker.domain.catalog.SubscriptionTemplate
import java.time.LocalDate

fun SubscriptionTemplate.toInput(startDate: LocalDate): SubscriptionInput = SubscriptionInput(
    name = name,
    provider = provider,
    category = category,
    price = "",
    currencyCode = currencyCode,
    billingUnit = billingUnit,
    billingCount = billingCount.toString(),
    autoRenew = true,
    startDate = startDate.toString(),
)
