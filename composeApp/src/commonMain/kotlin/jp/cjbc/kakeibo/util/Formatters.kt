package jp.cjbc.kakeibo.util

import androidx.compose.runtime.Composable
import jp.cjbc.kakeibo.resources.Res
import jp.cjbc.kakeibo.resources.date_long
import jp.cjbc.kakeibo.resources.date_short
import jp.cjbc.kakeibo.resources.month_names
import jp.cjbc.kakeibo.resources.weekday_names
import jp.cjbc.kakeibo.resources.year_month_format
import kotlin.math.absoluteValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** U+2212 MINUS SIGN, which negative amounts start with. */
const val MINUS_SIGN = '−'

/**
 * Formats a yen [amount] with thousands separators and no currency symbol, e.g. `1,234`.
 * Negative amounts start with [MINUS_SIGN]; positive ones start with `+` if [signed]. Zero never
 * has a sign. Every amount on screen goes through this function.
 */
fun formatAmount(amount: Long, signed: Boolean = false): String {
    val digits = amount.absoluteValue.toString().reversed().chunked(3).joinToString(",").reversed()
    return when {
        amount < 0 -> "$MINUS_SIGN$digits"
        amount > 0 && signed -> "+$digits"
        else -> digits
    }
}

/** Returns the localized name of the month of [yearMonth], e.g. `Jan` or `1月`. */
@Composable
fun formatMonth(yearMonth: YearMonth): String =
    stringArrayResource(Res.array.month_names).getOrElse(yearMonth.month.number - 1) { yearMonth.toString() }

/** Formats [yearMonth] for display, e.g. `Jan 2025` or `2025年1月`. */
@Composable
fun formatYearMonth(yearMonth: YearMonth): String =
    stringResource(Res.string.year_month_format, yearMonth.year.toString(), formatMonth(yearMonth))

/** The localized short weekday of [date], e.g. `金` or `Fri`. */
@Composable
fun formatWeekday(date: LocalDate): String =
    stringArrayResource(Res.array.weekday_names).getOrElse(date.dayOfWeek.isoDayNumber - 1) { "" }

/** Formats [date] briefly, e.g. `10/9（金）` or `Fri 10/9`. */
@Composable
fun formatShortDate(date: LocalDate): String =
    stringResource(Res.string.date_short, date.month.number, date.day, formatWeekday(date))

/** Formats [date] as a day heading, e.g. `10月9日（金）` or `Fri, 10/9`. */
@Composable
fun formatLongDate(date: LocalDate): String =
    stringResource(Res.string.date_long, date.month.number, date.day, formatWeekday(date))
