package com.subscriptiontracker.domain.repository

import com.subscriptiontracker.domain.model.Quota
import java.util.UUID

interface QuotaRepository {
    suspend fun getById(id: UUID): Quota?

    suspend fun listForSubscription(subscriptionId: UUID): List<Quota>

    suspend fun save(quota: Quota)

    suspend fun delete(id: UUID)
}
