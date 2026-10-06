package jp.cjbc.kakeibo.util

import androidx.compose.runtime.Composable
import jp.cjbc.kakeibo.model.TransactionType
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.month_names
import jp.cjbc.kakeibo.resources.year_month_format
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Returns the localized name of the month in `YYYY-MM`, e.g. `Jan` or `1月`. */
@Composable
fun formatMonth(yearMonth: String): String =
    stringArrayResource(Res.array.month_names)
        .getOrElse(yearMonth.substringAfter("-").toInt() - 1) { yearMonth }

/** Formats `YYYY-MM` for display, e.g. `Jan 2025` or `2025年1月`. */
@Composable
fun formatYearMonth(yearMonth: String): String =
    stringResource(
        Res.string.year_month_format,
        yearMonth.substringBefore("-"),
        formatMonth(yearMonth),
    )

/** Formats [value] as a yen amount with thousands separators, e.g. `¥1,234`. */
fun formatYen(value: Double): String =
    "¥${value.toLong().toString().reversed().chunked(3).joinToString(",").reversed()}"

/** Formats [value] as a yen amount prefixed with `+` for income and `-` for expense. */
fun formatSignedYen(value: Double, type: TransactionType): String =
    if (type == TransactionType.INCOME) "+${formatYen(value)}" else "-${formatYen(value)}"
