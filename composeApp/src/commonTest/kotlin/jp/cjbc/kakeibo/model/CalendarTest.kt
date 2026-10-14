package jp.cjbc.kakeibo.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarTest {
    @Test
    fun addsMonthsAcrossYears() {
        assertEquals(YearMonth(2027, 1), YearMonth(2026, 12) + 1)
        assertEquals(YearMonth(2025, 12), YearMonth(2026, 1) - 1)
        assertEquals(YearMonth(2024, 11), YearMonth(2026, 3) - 16)
    }

    @Test
    fun countsMonthsBetween() {
        assertEquals(11, YearMonth(2026, 4).monthsUntil(YearMonth(2027, 3)))
        assertEquals(-2, YearMonth(2026, 4).monthsUntil(YearMonth(2026, 2)))
    }

    @Test
    fun countsDaysAcrossMonthsAndLeapYears() {
        assertEquals(1, LocalDate(2024, 2, 28).daysUntil(LocalDate(2024, 2, 29)))
        assertEquals(1, LocalDate(2024, 2, 29).daysUntil(LocalDate(2024, 3, 1)))
        assertEquals(366, LocalDate(2024, 1, 1).daysUntil(LocalDate(2025, 1, 1)))
        assertEquals(-31, LocalDate(2026, 2, 1).daysUntil(LocalDate(2026, 1, 1)))
    }

    @Test
    fun parsesOnlyValidDates() {
        assertEquals(LocalDate(2026, 10, 14), LocalDate.parseOrNull("2026-10-14"))
        assertNull(LocalDate.parseOrNull("2026-02-30"))
        assertNull(LocalDate.parseOrNull("2026-1-01"))
        assertNull(LocalDate.parseOrNull("2026/10/14"))
        assertEquals(YearMonth(2026, 10), YearMonth.parseOrNull("2026-10"))
        assertNull(YearMonth.parseOrNull("2026-13"))
    }

    @Test
    fun theDefaultBudgetMonthIsTheNextMonth() {
        assertEquals(YearMonth(2026, 11), defaultBudgetMonth(LocalDate(2026, 10, 25)))
        assertEquals(YearMonth(2027, 1), defaultBudgetMonth(LocalDate(2026, 12, 31)))
    }
}
