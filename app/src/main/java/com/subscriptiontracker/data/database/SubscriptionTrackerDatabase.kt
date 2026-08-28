package com.subscriptiontracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubscriptionEntity::class,
        RecurringEventEntity::class,
        QuotaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SubscriptionTrackerDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun recurringEventDao(): RecurringEventDao

    abstract fun quotaDao(): QuotaDao

    abstract fun transactionDao(): DatabaseTransactionDao

    companion object {
        fun create(context: Context, name: String = "subscription-tracker.db"): SubscriptionTrackerDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SubscriptionTrackerDatabase::class.java,
                name,
            ).build()
    }
}
