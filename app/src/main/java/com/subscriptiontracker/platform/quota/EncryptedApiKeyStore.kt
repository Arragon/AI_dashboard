package com.subscriptiontracker.platform.quota

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.subscriptiontracker.domain.provider.ApiKeyStore
import java.util.UUID

class EncryptedApiKeyStore(context: Context) : ApiKeyStore {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NAME,
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun hasKey(subscriptionId: UUID): Boolean = preferences.contains(subscriptionId.toString())

    override fun read(subscriptionId: UUID): String? = preferences.getString(subscriptionId.toString(), null)

    override fun write(subscriptionId: UUID, apiKey: String) {
        preferences.edit().putString(subscriptionId.toString(), apiKey).apply()
    }

    override fun delete(subscriptionId: UUID) {
        preferences.edit().remove(subscriptionId.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "subscription_tracker_api_keys"
    }
}
