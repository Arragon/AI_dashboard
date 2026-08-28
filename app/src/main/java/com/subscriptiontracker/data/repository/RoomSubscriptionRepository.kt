package com.subscriptiontracker.data.repository

import com.subscriptiontracker.data.database.SubscriptionDao
import com.subscriptiontracker.data.database.SubscriptionEntity
import com.subscriptiontracker.data.database.toDomain
import com.subscriptiontracker.data.database.toEntity
import com.subscriptiontracker.domain.model.Subscription
import com.subscriptiontracker.domain.repository.SubscriptionRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSubscriptionRepository(
    private val dao: SubscriptionDao,
) : SubscriptionRepository {
    fun observeAll(): Flow<List<Subscription>> =
        dao.observeAll().map { entities -> entities.map(SubscriptionEntity::toDomain) }

    fun observeVisible(): Flow<List<Subscription>> =
        dao.observeVisible().map { entities -> entities.map(SubscriptionEntity::toDomain) }

    fun observeById(id: UUID): Flow<Subscription?> =
        dao.observeById(id.toString()).map { it?.toDomain() }

    override suspend fun getById(id: UUID): Subscription? = dao.getById(id.toString())?.toDomain()

    override suspend fun list(): List<Subscription> = dao.getAll().map(SubscriptionEntity::toDomain)

    suspend fun create(subscription: Subscription) = dao.create(subscription.toEntity())

    suspend fun update(subscription: Subscription): Boolean = dao.update(subscription.toEntity()) == 1

    override suspend fun save(subscription: Subscription) = dao.save(subscription.toEntity())

    suspend fun archive(id: UUID, archivedAt: Instant, updatedAt: Instant = archivedAt): Boolean =
        dao.archive(id.toString(), archivedAt.toString(), updatedAt.toString()) == 1

    override suspend fun delete(id: UUID) {
        dao.delete(id.toString())
    }
}
