package com.subscriptiontracker.platform.reminder

import android.content.Context

class ReminderWorkRegistry(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun registeredWorkNames(): Set<String> =
        preferences.getStringSet(KEY_WORK_NAMES, emptySet()).orEmpty().toSet()

    fun replace(workNames: Set<String>) {
        preferences.edit().putStringSet(KEY_WORK_NAMES, workNames.toSet()).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "reminder_work_registry"
        private const val KEY_WORK_NAMES = "unique_work_names"
    }
}
