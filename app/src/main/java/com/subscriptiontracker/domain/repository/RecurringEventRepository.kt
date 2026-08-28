package com.subscriptiontracker.domain.repository

import com.subscriptiontracker.domain.model.RecurringEvent
import java.util.UUID

interface RecurringEventRepository {
    suspend fun getById(id: UUID): RecurringEvent?

    suspend fun listForSubscription(subscriptionId: UUID): List<RecurringEvent>

    suspend fun save(event: RecurringEvent)

    suspend fun delete(id: UUID)
}
