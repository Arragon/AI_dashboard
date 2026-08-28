package com.subscriptiontracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class DatabaseTransactionDao {
    @Query("DELETE FROM quotas")
    protected abstract suspend fun deleteQuotas()

    @Query("DELETE FROM recurring_events")
    protected abstract suspend fun deleteEvents()

    @Query("DELETE FROM subscriptions")
    protected abstract suspend fun deleteSubscriptions()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertSubscriptions(subscriptions: List<SubscriptionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertEvents(events: List<RecurringEventEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertQuotas(quotas: List<QuotaEntity>)

    @Transaction
    open suspend fun replaceAll(
        subscriptions: List<SubscriptionEntity>,
        events: List<RecurringEventEntity>,
        quotas: List<QuotaEntity>,
    ) {
        deleteQuotas()
        deleteEvents()
        deleteSubscriptions()
        insertSubscriptions(subscriptions)
        insertEvents(events)
        insertQuotas(quotas)
    }
}
