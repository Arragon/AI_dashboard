package com.subscriptiontracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QuotaDao {
    @Query("SELECT * FROM quotas WHERE subscriptionId = :subscriptionId ORDER BY name COLLATE NOCASE, id")
    fun observeForSubscription(subscriptionId: String): Flow<List<QuotaEntity>>

    @Query("SELECT * FROM quotas WHERE id = :id")
    fun observeById(id: String): Flow<QuotaEntity?>

    @Query("SELECT * FROM quotas WHERE id = :id")
    suspend fun getById(id: String): QuotaEntity?

    @Query("SELECT * FROM quotas WHERE subscriptionId = :subscriptionId ORDER BY name COLLATE NOCASE, id")
    suspend fun getForSubscription(subscriptionId: String): List<QuotaEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun create(quota: QuotaEntity)

    @Update
    suspend fun update(quota: QuotaEntity): Int

    @Upsert
    suspend fun save(quota: QuotaEntity)

    @Query("DELETE FROM quotas WHERE id = :id")
    suspend fun delete(id: String): Int
}
