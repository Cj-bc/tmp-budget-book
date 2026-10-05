package jp.cjbc.kakeibo.util

import jp.cjbc.kakeibo.currentDate

/** Returns the current year and month as `YYYY-MM`. */
fun currentYearMonth(): String = currentDate().take(7)

/** Returns the `YYYY-MM` that is [delta] months after [yearMonth]. */
fun addMonths(yearMonth: String, delta: Int): String {
    val parts = yearMonth.split("-").map { it.toInt() }
    val absolute = parts[0] * 12 + parts[1] - 1 + delta
    val year = (absolute / 12).toString().padStart(4, '0')
    val month = (absolute % 12 + 1).toString().padStart(2, '0')
    return "$year-$month"
}

/** Returns the number of months from [from] to [to], both given as `YYYY-MM`. */
fun monthsBetween(from: String, to: String): Int {
    val fromParts = from.split("-").map { it.toInt() }
    val toParts = to.split("-").map { it.toInt() }
    return (toParts[0] - fromParts[0]) * 12 + toParts[1] - fromParts[1]
}

/** Returns the approximate number of days from [from] to [to], both given as `YYYY-MM-DD`. */
fun daysBetween(from: String, to: String): Int {
    // ISO dates sort lexically; convert only the small, fixed calendar representation to a
    // day count.
    fun toDayCount(date: String): Int {
        val parts = date.split("-").map { it.toInt() }
        return parts[0] * 365 + parts[1] * 30 + parts[2]
    }
    return toDayCount(to) - toDayCount(from)
}
