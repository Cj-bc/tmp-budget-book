package jp.cjbc.kakeibo.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test
    fun separatesThousandsWithoutACurrencySymbol() {
        assertEquals("1,234", formatAmount(1234))
        assertEquals("1,234,567", formatAmount(1_234_567))
        assertEquals("999", formatAmount(999))
    }

    @Test
    fun negativeAmountsUseTheMinusSign() {
        assertEquals("−1,234", formatAmount(-1234))
        assertEquals("−1,234", formatAmount(-1234, signed = true))
    }

    @Test
    fun positiveAmountsArePlusSignedOnlyWhenAsked() {
        assertEquals("+1,234", formatAmount(1234, signed = true))
        assertEquals("1,234", formatAmount(1234, signed = false))
    }

    @Test
    fun zeroHasNoSign() {
        assertEquals("0", formatAmount(0, signed = true))
        assertEquals("0", formatAmount(0))
    }
}
