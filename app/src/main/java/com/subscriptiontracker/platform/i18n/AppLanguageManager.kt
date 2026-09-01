package com.subscriptiontracker.platform.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppLanguageManager {
    private const val PREFS_NAME = "subscription_tracker_language"
    private const val KEY_LANGUAGE = "app_language"
    private val SUPPORTED = linkedMapOf(
        "en" to "English",
        "zh" to "中文",
    )

    fun supported(): Map<String, String> = SUPPORTED

    fun get(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return when {
            stored != null && stored in SUPPORTED -> stored
            else -> systemOrDefault()
        }
    }

    fun set(context: Context, languageTag: String) {
        val normalized = languageTag.takeIf { it in SUPPORTED } ?: "en"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, normalized)
            .apply()
    }

    fun wrap(context: Context): Context = wrap(context, get(context))

    private fun wrap(context: Context, languageTag: String): Context {
        val locale = Locale(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    private fun systemOrDefault(): String = when (Locale.getDefault().language) {
        "zh" -> "zh"
        else -> "en"
    }
}
