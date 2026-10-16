package jp.cjbc.kakeibo.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EntryLogicTest {
    @Test
    fun keysAppendDigitsAndDropLeadingZeros() {
        assertEquals("1", pressKey("", "1"))
        assertEquals("1000", pressKey("1", "000"))
        assertEquals("100", pressKey("1", "00"))
        assertEquals("", pressKey("", "0"))
        assertEquals("", pressKey("", "000"))
    }

    @Test
    fun keysBeyondNineDigitsAreIgnored() {
        assertEquals("123456789", pressKey("123456789", "0"))
        assertEquals("1234567", pressKey("1234567", "000"))
        assertEquals("12345678", pressKey("1234567", "8"))
    }

    @Test
    fun backspaceDropsTheLastDigit() {
        assertEquals("12", pressBackspace("123"))
        assertEquals("", pressBackspace(""))
    }

    @Test
    fun onlyDigitKeysExist() {
        assertFailsWith<IllegalArgumentException> { pressKey("", "a") }
    }

    @Test
    fun warnsWhenTheFundIsOrWillBeOverspent() {
        assertNull(fundWarning(balance = 1000, amount = 1000))
        assertEquals(FundWarning.WillBeOver(left = 1000, overAfter = 1), fundWarning(1000, 1001))
        // The design's example: 5,330 over, 680 more makes 6,010.
        assertEquals(FundWarning.AlreadyOver(over = 5330, overAfter = 6010), fundWarning(-5330, 680))
        assertEquals(FundWarning.AlreadyOver(over = 10, overAfter = 10), fundWarning(-10, 0))
    }

    @Test
    fun monthNetCountsTransactionsDatedInTheMonth() {
        val transactions = listOf(
            expense("2026-10-01", 500),
            expense("2026-09-30", 9999),
            income("2026-10-25", 20_900 + 500),
        )
        assertEquals(20_900L, monthNet(transactions, YearMonth(2026, 10)))
    }

    private fun expense(date: String, amount: Long) = Transaction.Expense(
        TransactionId("e$date"), LocalDate.parse(date), amount, CategoryId("food"), "", FundId("f"), null,
    )

    private fun income(date: String, amount: Long) = Transaction.Income(
        TransactionId("i$date"), LocalDate.parse(date), amount, CategoryId("salary"), "",
        YearMonth(2026, 11), listOf(Allocation(FundId("f"), amount)),
    )
}
