package com.subscriptiontracker.domain.repository

import com.subscriptiontracker.domain.model.Subscription
import java.util.UUID

interface SubscriptionRepository {
    suspend fun getById(id: UUID): Subscription?

    suspend fun list(): List<Subscription>

    suspend fun save(subscription: Subscription)

    suspend fun delete(id: UUID)
}
