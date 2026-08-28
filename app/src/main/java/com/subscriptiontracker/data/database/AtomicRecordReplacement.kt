package com.subscriptiontracker.data.database

import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.Subscription

interface AtomicRecordReplacement {
    suspend fun replaceAll(
        subscriptions: List<Subscription>,
        events: List<RecurringEvent>,
        quotas: List<Quota>,
    )
}
