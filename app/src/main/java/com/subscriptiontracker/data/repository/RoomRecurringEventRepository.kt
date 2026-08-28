package com.subscriptiontracker.data.repository

import com.subscriptiontracker.data.database.RecurringEventDao
import com.subscriptiontracker.data.database.RecurringEventEntity
import com.subscriptiontracker.data.database.toDomain
import com.subscriptiontracker.data.database.toEntity
import com.subscriptiontracker.domain.model.RecurringEvent
import com.subscriptiontracker.domain.repository.RecurringEventRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRecurringEventRepository(
    private val dao: RecurringEventDao,
) : RecurringEventRepository {
    fun observeForSubscription(subscriptionId: UUID): Flow<List<RecurringEvent>> =
        dao.observeForSubscription(subscriptionId.toString())
            .map { entities -> entities.map(RecurringEventEntity::toDomain) }

    fun observeVisibleForSubscription(subscriptionId: UUID): Flow<List<RecurringEvent>> =
        dao.observeVisibleForSubscription(subscriptionId.toString())
            .map { entities -> entities.map(RecurringEventEntity::toDomain) }

    fun observeById(id: UUID): Flow<RecurringEvent?> =
        dao.observeById(id.toString()).map { it?.toDomain() }

    override suspend fun getById(id: UUID): RecurringEvent? = dao.getById(id.toString())?.toDomain()

    override suspend fun listForSubscription(subscriptionId: UUID): List<RecurringEvent> =
        dao.getForSubscription(subscriptionId.toString()).map(RecurringEventEntity::toDomain)

    suspend fun create(event: RecurringEvent) = dao.create(event.toEntity())

    suspend fun update(event: RecurringEvent): Boolean = dao.update(event.toEntity()) == 1

    override suspend fun save(event: RecurringEvent) = dao.save(event.toEntity())

    suspend fun setVisible(id: UUID, visible: Boolean, updatedAt: Instant): Boolean =
        dao.setVisible(id.toString(), visible, updatedAt.toString()) == 1

    override suspend fun delete(id: UUID) {
        dao.delete(id.toString())
    }
}
