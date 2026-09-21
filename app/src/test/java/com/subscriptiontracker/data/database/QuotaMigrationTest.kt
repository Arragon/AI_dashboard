package com.subscriptiontracker.data.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuotaMigrationTest {
    @Test
    fun migrateFromVersion1KeepsRowsAndAddsOnlineColumns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(NAME)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(SUBSCRIPTIONS_V1)
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_status` ON `subscriptions` (`status`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subscriptions_nextBillingDate` ON `subscriptions` (`nextBillingDate`)")
                        db.execSQL(EVENTS_V1)
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_events_subscriptionId` ON `recurring_events` (`subscriptionId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_events_nextOccurrence` ON `recurring_events` (`nextOccurrence`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_events_enabled` ON `recurring_events` (`enabled`)")
                        db.execSQL(QUOTAS_V1)
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quotas_subscriptionId` ON `quotas` (`subscriptionId`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quotas_resetEventId` ON `quotas` (`resetEventId`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                        db.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES(42, '$V1_IDENTITY')")
                        db.execSQL(
                            "INSERT INTO subscriptions (id, name, provider, category, status, price, currencyCode, autoRenew, startDate, notes, tagsJson, createdAt, updatedAt) " +
                                "VALUES ('sub', 'Router', 'OpenRouter', 'AI', 'ACTIVE', '10', 'USD', 1, '2026-01-01', '', '[]', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
                        )
                        db.execSQL(
                            "INSERT INTO quotas (id, subscriptionId, name, unit, remaining, unlimited, updatedAt) " +
                                "VALUES ('quota', 'sub', 'Balance', 'USD', '4', 0, '2026-01-02T00:00:00Z')",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.close()
        helper.close()

        val database = Room.databaseBuilder(context, SubscriptionTrackerDatabase::class.java, NAME)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val subscription = database.subscriptionDao().getAll().single()
        val quota = database.quotaDao().getForSubscription("sub").single()
        assertEquals("Router", subscription.name)
        assertNull(subscription.onlineProviderId)
        assertEquals("Balance", quota.name)
        assertEquals("4", quota.remaining)
        assertEquals("MANUAL", quota.origin)
        assertEquals("FRESH", quota.syncState)
        assertNull(quota.stableKey)
        database.close()
    }

    private companion object {
        const val NAME = "quota-migration.db"
        const val V1_IDENTITY = "969243668308aa3aea0a14b6af0121b9"
        const val SUBSCRIPTIONS_V1 =
            "CREATE TABLE IF NOT EXISTS `subscriptions` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `provider` TEXT NOT NULL, `category` TEXT NOT NULL, `iconIdentifier` TEXT, `status` TEXT NOT NULL, `price` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `billingInterval` TEXT, `billingIntervalCount` INTEGER, `autoRenew` INTEGER NOT NULL, `startDate` TEXT NOT NULL, `nextBillingDate` TEXT, `expirationDate` TEXT, `trialEndDate` TEXT, `notes` TEXT NOT NULL, `tagsJson` TEXT NOT NULL, `plan` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `archivedAt` TEXT, PRIMARY KEY(`id`))"
        const val EVENTS_V1 =
            "CREATE TABLE IF NOT EXISTS `recurring_events` (`id` TEXT NOT NULL, `subscriptionId` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `nextOccurrence` TEXT NOT NULL, `recurrenceKind` TEXT NOT NULL, `recurrenceInterval` INTEGER, `recurrenceUnit` TEXT, `timezoneId` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `remindersEnabled` INTEGER NOT NULL, `reminderOffsetsJson` TEXT NOT NULL, `notificationTime` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )"
        const val QUOTAS_V1 =
            "CREATE TABLE IF NOT EXISTS `quotas` (`id` TEXT NOT NULL, `subscriptionId` TEXT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `used` TEXT, `remaining` TEXT, `limit` TEXT, `percentage` TEXT, `unlimited` INTEGER NOT NULL, `resetEventId` TEXT, `warningThresholdPercentage` TEXT, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE CASCADE ON DELETE CASCADE , FOREIGN KEY(`resetEventId`) REFERENCES `recurring_events`(`id`) ON UPDATE CASCADE ON DELETE SET NULL )"
    }
}
