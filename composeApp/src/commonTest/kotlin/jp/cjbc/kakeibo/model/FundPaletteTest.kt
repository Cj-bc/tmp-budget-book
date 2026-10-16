package jp.cjbc.kakeibo.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private fun fund(id: String, color: Long, sortOrder: Int = 0) = Fund(
    id = FundId(id),
    name = id,
    color = FundColor(color),
    openingBalance = 0,
    goal = FundGoal.None,
    showInEntry = true,
    sortOrder = sortOrder,
    archived = false,
)

class FundPaletteTest {
    @Test
    fun theIncomeBlueIsNotAFundColor() {
        assertEquals(false, FundColor(0xFF1A5BC4) in FUND_PALETTE)
        assertEquals(10, FUND_PALETTE.toSet().size)
    }

    @Test
    fun oldColorsMoveToTheNearestUnusedPaletteColor() {
        val data = LedgerData(
            funds = listOf(
                fund("kept", 0xFFC8553D, sortOrder = 0),
                // Nearest is the red, which "kept" already has.
                fund("food", 0xFFE85D4A, sortOrder = 1),
                fund("purple", 0xFF9B6BE8, sortOrder = 2),
            ),
        )
        val colors = data.withPaletteColors().funds.associate { it.id.value to it.color.argb }
        assertEquals(0xFFC8553D, colors["kept"])
        assertEquals(0xFF7B5CC4, colors["purple"])
        assertEquals(true, FundColor(colors.getValue("food")) in FUND_PALETTE)
        assertEquals(3, colors.values.toSet().size)
    }

    @Test
    fun paletteColorsAreLeftAlone() {
        val data = LedgerData(funds = listOf(fund("a", 0xFF5A616D), fund("b", 0xFF5A616D)))
        assertSame(data, data.withPaletteColors())
    }

    @Test
    fun aNewFundTakesTheFirstUnusedColor() {
        assertEquals(FUND_PALETTE[1], nextPaletteColor(listOf(fund("a", FUND_PALETTE[0].argb))))
    }
}
