package com.subscriptiontracker.domain.model

enum class RecurrenceUnit {
    DAYS,
    WEEKS,
    MONTHS,
    YEARS,
}

sealed interface RecurrenceRule {
    data object OneTime : RecurrenceRule

    data class Repeating(
        val interval: Int,
        val unit: RecurrenceUnit,
    ) : RecurrenceRule {
        init {
            require(interval > 0) { "Recurrence interval must be positive" }
        }
    }

    companion object {
        fun every(interval: Int, unit: RecurrenceUnit): RecurrenceRule =
            Repeating(interval, unit)

        fun daily(): RecurrenceRule = Repeating(1, RecurrenceUnit.DAYS)

        fun weekly(): RecurrenceRule = Repeating(1, RecurrenceUnit.WEEKS)

        fun monthly(): RecurrenceRule = Repeating(1, RecurrenceUnit.MONTHS)

        fun quarterly(): RecurrenceRule = Repeating(3, RecurrenceUnit.MONTHS)

        fun yearly(): RecurrenceRule = Repeating(1, RecurrenceUnit.YEARS)
    }
}
