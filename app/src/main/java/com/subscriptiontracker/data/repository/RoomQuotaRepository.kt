package com.subscriptiontracker.data.repository

import com.subscriptiontracker.data.database.QuotaDao
import com.subscriptiontracker.data.database.QuotaEntity
import com.subscriptiontracker.data.database.toDomain
import com.subscriptiontracker.data.database.toEntity
import com.subscriptiontracker.domain.model.Quota
import com.subscriptiontracker.domain.repository.QuotaRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomQuotaRepository(
    private val dao: QuotaDao,
) : QuotaRepository {
    fun observeForSubscription(subscriptionId: UUID): Flow<List<Quota>> =
        dao.observeForSubscription(subscriptionId.toString())
            .map { entities -> entities.map(QuotaEntity::toDomain) }

    fun observeById(id: UUID): Flow<Quota?> =
        dao.observeById(id.toString()).map { it?.toDomain() }

    override suspend fun getById(id: UUID): Quota? = dao.getById(id.toString())?.toDomain()

    override suspend fun listForSubscription(subscriptionId: UUID): List<Quota> =
        dao.getForSubscription(subscriptionId.toString()).map(QuotaEntity::toDomain)

    suspend fun create(quota: Quota) = dao.create(quota.toEntity())

    suspend fun update(quota: Quota): Boolean = dao.update(quota.toEntity()) == 1

    override suspend fun save(quota: Quota) = dao.save(quota.toEntity())

    override suspend fun delete(id: UUID) {
        dao.delete(id.toString())
    }
}
