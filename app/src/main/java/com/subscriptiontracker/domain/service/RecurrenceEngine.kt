package com.subscriptiontracker.domain.service

import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

class RecurrenceEngine {
    fun occurrenceOnOrAfter(
        anchorDate: LocalDate,
        rule: RecurrenceRule,
        queryDate: LocalDate,
    ): LocalDate? {
        if (queryDate <= anchorDate) return anchorDate
        if (rule == RecurrenceRule.OneTime) return null

        rule as RecurrenceRule.Repeating
        return when (rule.unit) {
            RecurrenceUnit.DAYS -> fixedDayOccurrence(anchorDate, queryDate, rule.interval.toLong())
            RecurrenceUnit.WEEKS -> fixedDayOccurrence(anchorDate, queryDate, rule.interval.toLong() * 7)
            RecurrenceUnit.MONTHS -> monthlyOccurrence(anchorDate, queryDate, rule.interval.toLong())
            RecurrenceUnit.YEARS -> yearlyOccurrence(anchorDate, queryDate, rule.interval.toLong())
        }
    }

    fun occurrenceAfter(
        anchorDate: LocalDate,
        rule: RecurrenceRule,
        queryDate: LocalDate,
    ): LocalDate? {
        if (queryDate == LocalDate.MAX) return null
        return occurrenceOnOrAfter(anchorDate, rule, queryDate.plusDays(1))
    }

    private fun fixedDayOccurrence(
        anchorDate: LocalDate,
        queryDate: LocalDate,
        intervalDays: Long,
    ): LocalDate {
        val elapsedDays = ChronoUnit.DAYS.between(anchorDate, queryDate)
        val occurrenceIndex = ceilDiv(elapsedDays, intervalDays)
        return anchorDate.plusDays(Math.multiplyExact(occurrenceIndex, intervalDays))
    }

    private fun monthlyOccurrence(
        anchorDate: LocalDate,
        queryDate: LocalDate,
        intervalMonths: Long,
    ): LocalDate {
        val elapsedMonths = ChronoUnit.MONTHS.between(
            YearMonth.from(anchorDate),
            YearMonth.from(queryDate),
        )
        var occurrenceIndex = elapsedMonths.coerceAtLeast(0) / intervalMonths
        var candidate = anchoredMonthDate(anchorDate, occurrenceIndex * intervalMonths)
        if (candidate < queryDate) {
            occurrenceIndex++
            candidate = anchoredMonthDate(anchorDate, occurrenceIndex * intervalMonths)
        }
        return candidate
    }

    private fun yearlyOccurrence(
        anchorDate: LocalDate,
        queryDate: LocalDate,
        intervalYears: Long,
    ): LocalDate {
        val elapsedYears = (queryDate.year.toLong() - anchorDate.year).coerceAtLeast(0)
        var occurrenceIndex = elapsedYears / intervalYears
        var candidate = anchorDate.plusYears(occurrenceIndex * intervalYears)
        if (candidate < queryDate) {
            occurrenceIndex++
            candidate = anchorDate.plusYears(occurrenceIndex * intervalYears)
        }
        return candidate
    }

    private fun anchoredMonthDate(anchorDate: LocalDate, monthsToAdd: Long): LocalDate {
        val targetMonth = YearMonth.from(anchorDate).plusMonths(monthsToAdd)
        return targetMonth.atDay(anchorDate.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth()))
    }

    private fun ceilDiv(value: Long, divisor: Long): Long =
        value / divisor + if (value % divisor == 0L) 0 else 1
}
