package com.subscriptiontracker.domain.service

import com.subscriptiontracker.domain.model.RecurrenceRule
import com.subscriptiontracker.domain.model.RecurrenceUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.TimeZone

class RecurrenceEngineTest {
    private val engine = RecurrenceEngine()
    private val originalTimeZone = TimeZone.getDefault()

    @AfterEach
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `monthly recurrence preserves day 31 after February clamp`() {
        val anchor = LocalDate.of(2023, 1, 31)

        assertEquals(LocalDate.of(2023, 2, 28), engine.occurrenceAfter(anchor, RecurrenceRule.monthly(), anchor))
        assertEquals(LocalDate.of(2023, 3, 31), engine.occurrenceAfter(anchor, RecurrenceRule.monthly(), LocalDate.of(2023, 2, 28)))
        assertEquals(LocalDate.of(2023, 4, 30), engine.occurrenceAfter(anchor, RecurrenceRule.monthly(), LocalDate.of(2023, 3, 31)))
        assertEquals(LocalDate.of(2023, 5, 31), engine.occurrenceAfter(anchor, RecurrenceRule.monthly(), LocalDate.of(2023, 4, 30)))
    }

    @Test
    fun `monthly recurrence handles leap February and recovers anchor`() {
        val anchor = LocalDate.of(2024, 1, 31)

        assertEquals(LocalDate.of(2024, 2, 29), engine.occurrenceAfter(anchor, RecurrenceRule.monthly(), anchor))
        assertEquals(LocalDate.of(2024, 3, 31), engine.occurrenceAfter(anchor, RecurrenceRule.monthly(), LocalDate.of(2024, 2, 29)))
    }

    @Test
    fun `yearly leap-day recurrence uses February 28 then returns to February 29`() {
        val anchor = LocalDate.of(2020, 2, 29)

        assertEquals(LocalDate.of(2021, 2, 28), engine.occurrenceAfter(anchor, RecurrenceRule.yearly(), anchor))
        assertEquals(LocalDate.of(2023, 2, 28), engine.occurrenceOnOrAfter(anchor, RecurrenceRule.yearly(), LocalDate.of(2022, 3, 1)))
        assertEquals(LocalDate.of(2024, 2, 29), engine.occurrenceAfter(anchor, RecurrenceRule.yearly(), LocalDate.of(2023, 2, 28)))
    }

    @Test
    fun `quarterly and custom month intervals preserve anchor`() {
        val anchor = LocalDate.of(2023, 11, 30)

        assertEquals(LocalDate.of(2024, 2, 29), engine.occurrenceAfter(anchor, RecurrenceRule.quarterly(), anchor))
        assertEquals(LocalDate.of(2024, 5, 30), engine.occurrenceAfter(anchor, RecurrenceRule.quarterly(), LocalDate.of(2024, 2, 29)))
    }

    @Test
    fun `custom two-month recurrence anchored on 31 recovers after short month`() {
        val anchor = LocalDate.of(2023, 12, 31)
        val rule = RecurrenceRule.every(2, RecurrenceUnit.MONTHS)

        assertEquals(LocalDate.of(2024, 2, 29), engine.occurrenceAfter(anchor, rule, anchor))
        assertEquals(LocalDate.of(2024, 4, 30), engine.occurrenceAfter(anchor, rule, LocalDate.of(2024, 2, 29)))
        assertEquals(LocalDate.of(2024, 6, 30), engine.occurrenceAfter(anchor, rule, LocalDate.of(2024, 4, 30)))
    }

    @Test
    fun `daily and weekly recurrences cross year boundaries`() {
        assertEquals(
            LocalDate.of(2024, 1, 2),
            engine.occurrenceAfter(
                LocalDate.of(2023, 12, 29),
                RecurrenceRule.every(4, RecurrenceUnit.DAYS),
                LocalDate.of(2023, 12, 31),
            ),
        )
        assertEquals(
            LocalDate.of(2024, 1, 5),
            engine.occurrenceAfter(
                LocalDate.of(2023, 12, 29),
                RecurrenceRule.weekly(),
                LocalDate.of(2023, 12, 31),
            ),
        )
        assertEquals(
            LocalDate.of(2024, 1, 12),
            engine.occurrenceAfter(
                LocalDate.of(2023, 12, 29),
                RecurrenceRule.every(2, RecurrenceUnit.WEEKS),
                LocalDate.of(2024, 1, 1),
            ),
        )
    }

    @Test
    fun `inclusive and strict APIs have explicit boundary behavior`() {
        val anchor = LocalDate.of(2025, 1, 15)
        val query = LocalDate.of(2025, 3, 15)

        assertEquals(query, engine.occurrenceOnOrAfter(anchor, RecurrenceRule.monthly(), query))
        assertEquals(LocalDate.of(2025, 4, 15), engine.occurrenceAfter(anchor, RecurrenceRule.monthly(), query))
        assertEquals(anchor, engine.occurrenceOnOrAfter(anchor, RecurrenceRule.monthly(), LocalDate.of(2024, 1, 1)))
    }

    @Test
    fun `one-time recurrence is available once and then exhausted`() {
        val anchor = LocalDate.of(2025, 6, 10)

        assertEquals(anchor, engine.occurrenceOnOrAfter(anchor, RecurrenceRule.OneTime, LocalDate.of(2025, 1, 1)))
        assertEquals(anchor, engine.occurrenceOnOrAfter(anchor, RecurrenceRule.OneTime, anchor))
        assertNull(engine.occurrenceAfter(anchor, RecurrenceRule.OneTime, anchor))
        assertNull(engine.occurrenceOnOrAfter(anchor, RecurrenceRule.OneTime, anchor.plusDays(1)))
    }

    @Test
    fun `custom year interval keeps leap semantics`() {
        val anchor = LocalDate.of(2020, 2, 29)
        val everyTwoYears = RecurrenceRule.every(2, RecurrenceUnit.YEARS)

        assertEquals(LocalDate.of(2022, 2, 28), engine.occurrenceAfter(anchor, everyTwoYears, anchor))
        assertEquals(LocalDate.of(2024, 2, 29), engine.occurrenceAfter(anchor, everyTwoYears, LocalDate.of(2022, 2, 28)))
    }

    @Test
    fun `date-only recurrence is independent of default timezone and DST`() {
        val anchor = LocalDate.of(2024, 3, 10)
        val query = LocalDate.of(2024, 3, 10)

        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val newYorkResult = engine.occurrenceAfter(anchor, RecurrenceRule.daily(), query)
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
        val aucklandResult = engine.occurrenceAfter(anchor, RecurrenceRule.daily(), query)

        assertEquals(LocalDate.of(2024, 3, 11), newYorkResult)
        assertEquals(newYorkResult, aucklandResult)
    }

    @Test
    fun `invalid custom interval is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurrenceRule.every(0, RecurrenceUnit.DAYS)
        }
    }
}
