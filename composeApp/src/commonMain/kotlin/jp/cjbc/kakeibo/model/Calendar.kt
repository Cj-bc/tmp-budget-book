package jp.cjbc.kakeibo.model

import jp.cjbc.kakeibo.currentDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/** Today's date on the device. */
fun today(): LocalDate = LocalDate.parse(currentDate())

/** The current month on the device. */
fun currentMonth(): YearMonth = today().yearMonth
