package com.subscriptiontracker.domain.provider

import java.util.UUID

interface ApiKeyStore {
    fun hasKey(subscriptionId: UUID): Boolean

    fun read(subscriptionId: UUID): String?

    fun write(subscriptionId: UUID, apiKey: String)

    fun delete(subscriptionId: UUID)
}

class MemoryApiKeyStore : ApiKeyStore {
    private val keys = linkedMapOf<UUID, String>()

    override fun hasKey(subscriptionId: UUID): Boolean = keys.containsKey(subscriptionId)

    override fun read(subscriptionId: UUID): String? = keys[subscriptionId]

    override fun write(subscriptionId: UUID, apiKey: String) {
        keys[subscriptionId] = apiKey
    }

    override fun delete(subscriptionId: UUID) {
        keys.remove(subscriptionId)
    }
}
