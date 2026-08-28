package com.subscriptiontracker.data.database

import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.model.Subscription

class RoomRecordStore(
    private val transactionDao: DatabaseTransactionDao,
) : AtomicRecordReplacement {
    override suspend fun replaceAll(
        subscriptions: List<Subscription>,
        events: List<RecurringEvent>,
        quotas: List<Quota>,
    ) {
        transactionDao.replaceAll(
            subscriptions = subscriptions.map(Subscription::toEntity),
            events = events.map(RecurringEvent::toEntity),
            quotas = quotas.map(Quota::toEntity),
        )
    }
}
