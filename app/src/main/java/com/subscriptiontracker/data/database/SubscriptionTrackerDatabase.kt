package com.subscriptiontracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubscriptionEntity::class,
        RecurringEventEntity::class,
        QuotaEntity::class,
    ],
    version = 2,
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
            ).addMigrations(MIGRATION_1_2).build()
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `onlineProviderId` TEXT")
        db.execSQL("ALTER TABLE `quotas` ADD COLUMN `stableKey` TEXT")
        db.execSQL("ALTER TABLE `quotas` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'MANUAL'")
        db.execSQL("ALTER TABLE `quotas` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'FRESH'")
        db.execSQL("ALTER TABLE `quotas` ADD COLUMN `syncNote` TEXT")
    }
}
