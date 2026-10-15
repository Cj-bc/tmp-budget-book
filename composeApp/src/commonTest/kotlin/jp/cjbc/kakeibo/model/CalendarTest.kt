package jp.cjbc.kakeibo.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarTest {
    @Test
    fun theEntryScreenAcceptsOnlyValidIsoDates() {
        val iso = LocalDate.Formats.ISO
        assertEquals(LocalDate(2026, 10, 14), iso.parseOrNull("2026-10-14"))
        assertNull(iso.parseOrNull("2026-02-30"))
        assertNull(iso.parseOrNull("2026-1-01"))
        assertNull(iso.parseOrNull("2026/10/14"))
    }

    @Test
    fun theDefaultBudgetMonthIsTheNextMonth() {
        assertEquals(YearMonth(2026, 11), defaultBudgetMonth(LocalDate(2026, 10, 25)))
        assertEquals(YearMonth(2027, 1), defaultBudgetMonth(LocalDate(2026, 12, 31)))
    }

    @Test
    fun datesAndMonthsAreStoredAsIsoStrings() {
        // The stored format must not change with the library.
        assertEquals("\"2026-10-14\"", Json.encodeToString(LocalDate.serializer(), LocalDate(2026, 10, 14)))
        assertEquals("\"2026-10\"", Json.encodeToString(YearMonth.serializer(), YearMonth(2026, 10)))
        assertEquals(YearMonth(2026, 10), Json.decodeFromString(YearMonth.serializer(), "\"2026-10\""))
    }
}
