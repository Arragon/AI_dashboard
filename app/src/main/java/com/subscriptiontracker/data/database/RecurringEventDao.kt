package com.subscriptiontracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringEventDao {
    @Query("SELECT * FROM recurring_events WHERE subscriptionId = :subscriptionId ORDER BY nextOccurrence, id")
    fun observeForSubscription(subscriptionId: String): Flow<List<RecurringEventEntity>>

    @Query(
        "SELECT * FROM recurring_events WHERE subscriptionId = :subscriptionId AND enabled = 1 " +
            "ORDER BY nextOccurrence, id",
    )
    fun observeVisibleForSubscription(subscriptionId: String): Flow<List<RecurringEventEntity>>

    @Query("SELECT * FROM recurring_events WHERE id = :id")
    fun observeById(id: String): Flow<RecurringEventEntity?>

    @Query("SELECT * FROM recurring_events WHERE id = :id")
    suspend fun getById(id: String): RecurringEventEntity?

    @Query("SELECT * FROM recurring_events WHERE subscriptionId = :subscriptionId ORDER BY nextOccurrence, id")
    suspend fun getForSubscription(subscriptionId: String): List<RecurringEventEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun create(event: RecurringEventEntity)

    @Update
    suspend fun update(event: RecurringEventEntity): Int

    @Upsert
    suspend fun save(event: RecurringEventEntity)

    @Query("UPDATE recurring_events SET enabled = :visible, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setVisible(id: String, visible: Boolean, updatedAt: String): Int

    @Query("DELETE FROM recurring_events WHERE id = :id")
    suspend fun delete(id: String): Int
}
