package com.subscriptiontracker.platform

import android.content.Context
import com.subscriptiontracker.presentation.LedgerDefaults
import com.subscriptiontracker.presentation.LedgerDefaultsStore

class LedgerPreferences(context: Context) : LedgerDefaultsStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): LedgerDefaults = LedgerDefaults(
        currencyCode = preferences.getString(KEY_CURRENCY, "USD") ?: "USD",
        reminderOffsetDays = preferences.getInt(KEY_OFFSET, 1),
        reminderTime = preferences.getString(KEY_TIME, "09:00") ?: "09:00",
    )

    override fun write(defaults: LedgerDefaults) {
        preferences.edit()
            .putString(KEY_CURRENCY, defaults.currencyCode)
            .putInt(KEY_OFFSET, defaults.reminderOffsetDays)
            .putString(KEY_TIME, defaults.reminderTime)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "subscription_tracker_defaults"
        const val KEY_CURRENCY = "currency"
        const val KEY_OFFSET = "reminder_offset_days"
        const val KEY_TIME = "reminder_time"
    }
}
