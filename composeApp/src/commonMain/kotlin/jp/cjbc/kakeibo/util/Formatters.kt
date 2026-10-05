package jp.cjbc.kakeibo.util

import jp.cjbc.kakeibo.model.TransactionType

/** Formats `YYYY-MM` as `YYYY年M月`. */
fun formatYearMonth(yearMonth: String): String =
    "${yearMonth.substringBefore("-")}年${yearMonth.substringAfter("-").toInt()}月"

/** Formats [value] as a yen amount with thousands separators, e.g. `¥1,234`. */
fun formatYen(value: Double): String =
    "¥${value.toLong().toString().reversed().chunked(3).joinToString(",").reversed()}"

/** Formats [value] as a yen amount prefixed with `+` for income and `-` for expense. */
fun formatSignedYen(value: Double, type: TransactionType): String =
    if (type == TransactionType.INCOME) "+${formatYen(value)}" else "-${formatYen(value)}"
