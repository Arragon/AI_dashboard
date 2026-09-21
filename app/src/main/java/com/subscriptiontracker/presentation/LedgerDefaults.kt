package com.subscriptiontracker.presentation

data class LedgerDefaults(
    val currencyCode: String = "USD",
    val reminderOffsetDays: Int = 1,
    val reminderTime: String = "09:00",
)

interface LedgerDefaultsStore {
    fun read(): LedgerDefaults
    fun write(defaults: LedgerDefaults)
}

class MemoryDefaultsStore(initial: LedgerDefaults = LedgerDefaults()) : LedgerDefaultsStore {
    var current: LedgerDefaults = initial
        private set

    override fun read(): LedgerDefaults = current

    override fun write(defaults: LedgerDefaults) {
        current = defaults
    }
}
