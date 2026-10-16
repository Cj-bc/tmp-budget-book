package jp.cjbc.kakeibo.model

/**
 * The colors a fund can have. The design's `#1A5BC4` is replaced with `#6B8F2A`, because it is
 * the income color.
 */
val FUND_PALETTE: List<FundColor> = listOf(
    0xFF5A616D, 0xFFC8553D, 0xFF7B5CC4, 0xFF2E86AB, 0xFFD89A2B,
    0xFF3F9E6E, 0xFF6B8F2A, 0xFF14171C, 0xFF0E8C8C, 0xFF4A6FA5,
).map(::FundColor)

/** The squared distance between two colors in RGB. */
private fun distance(a: FundColor, b: FundColor): Int {
    fun channel(color: FundColor, shift: Int) = ((color.argb shr shift) and 0xFF).toInt()
    return listOf(16, 8, 0).sumOf { shift ->
        val d = channel(a, shift) - channel(b, shift)
        d * d
    }
}

/** The palette color closest to [color], preferring one not in [used]. */
fun nearestPaletteColor(color: FundColor, used: Collection<FundColor> = emptyList()): FundColor =
    (FUND_PALETTE - used.toSet()).ifEmpty { FUND_PALETTE }.minBy { distance(it, color) }

/** A palette color for a new fund: the first one no fund in [funds] has, else the first. */
fun nextPaletteColor(funds: List<Fund>): FundColor {
    val used = funds.map { it.color }.toSet()
    return FUND_PALETTE.firstOrNull { it !in used } ?: FUND_PALETTE.first()
}

/**
 * Moves funds whose color is not in [FUND_PALETTE] (the old category colors funds got in the
 * migration) to the nearest unused palette color. Funds already on the palette keep theirs, so
 * applying it again changes nothing.
 */
fun LedgerData.withPaletteColors(): LedgerData {
    if (funds.all { it.color in FUND_PALETTE }) return this
    val used = funds.map { it.color }.filter { it in FUND_PALETTE }.toMutableList()
    val recolored = funds.sortedBy { it.sortOrder }.associate { fund ->
        if (fund.color in FUND_PALETTE) {
            fund.id to fund.color
        } else {
            val color = nearestPaletteColor(fund.color, used)
            used += color
            fund.id to color
        }
    }
    return copy(funds = funds.map { it.copy(color = recolored.getValue(it.id)) })
}
