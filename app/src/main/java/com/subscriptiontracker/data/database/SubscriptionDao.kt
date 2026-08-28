package com.subscriptiontracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE status != 'ARCHIVED' ORDER BY name COLLATE NOCASE, id")
    fun observeVisible(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    fun observeById(id: String): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE, id")
    suspend fun getAll(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun create(subscription: SubscriptionEntity)

    @Update
    suspend fun update(subscription: SubscriptionEntity): Int

    @Upsert
    suspend fun save(subscription: SubscriptionEntity)

    @Query(
        "UPDATE subscriptions SET status = 'ARCHIVED', archivedAt = :archivedAt, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun archive(id: String, archivedAt: String, updatedAt: String): Int

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun delete(id: String): Int
}
