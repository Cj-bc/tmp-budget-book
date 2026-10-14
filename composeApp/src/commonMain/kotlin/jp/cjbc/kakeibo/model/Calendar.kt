package jp.cjbc.kakeibo.model

import jp.cjbc.kakeibo.currentDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private const val MONTHS_PER_YEAR = 12

/** A month of a year, stored as `YYYY-MM`. */
@Serializable(with = YearMonthSerializer::class)
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    init {
        require(month in 1..MONTHS_PER_YEAR) { "month must be 1..12: $month" }
    }

    val firstDay: LocalDate get() = LocalDate(year, month, 1)

    val lastDay: LocalDate get() = LocalDate(year, month, daysInMonth(year, month))

    /** Returns the month that is [months] months after this one. */
    operator fun plus(months: Int): YearMonth {
        val absolute = index + months
        return YearMonth(absolute.floorDiv(MONTHS_PER_YEAR), absolute.mod(MONTHS_PER_YEAR) + 1)
    }

    operator fun minus(months: Int): YearMonth = plus(-months)

    /** Returns the number of months from this month to [other]; negative if [other] is earlier. */
    fun monthsUntil(other: YearMonth): Int = other.index - index

    override fun compareTo(other: YearMonth): Int = index.compareTo(other.index)

    override fun toString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}"

    private val index: Int get() = year * MONTHS_PER_YEAR + month - 1

    companion object {
        /** Parses `YYYY-MM`, or returns `null` if [text] is not one. */
        fun parseOrNull(text: String): YearMonth? {
            val parts = text.split("-")
            if (parts.size != 2 || parts[0].length != 4 || parts[1].length != 2) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull()?.takeIf { it in 1..MONTHS_PER_YEAR } ?: return null
            return YearMonth(year, month)
        }

        fun parse(text: String): YearMonth =
            requireNotNull(parseOrNull(text)) { "not a YYYY-MM month: $text" }

        fun current(): YearMonth = LocalDate.today().yearMonth
    }
}

/** A calendar date, stored as `YYYY-MM-DD`. */
@Serializable(with = LocalDateSerializer::class)
data class LocalDate(val year: Int, val month: Int, val day: Int) : Comparable<LocalDate> {
    init {
        require(month in 1..MONTHS_PER_YEAR) { "month must be 1..12: $month" }
        require(day in 1..daysInMonth(year, month)) { "no day $day in $year-$month" }
    }

    val yearMonth: YearMonth get() = YearMonth(year, month)

    /** Returns the number of days from this date to [other]; negative if [other] is earlier. */
    fun daysUntil(other: LocalDate): Int = other.epochDay - epochDay

    override fun compareTo(other: LocalDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    override fun toString(): String = "$yearMonth-${day.toString().padStart(2, '0')}"

    /** Days since 1970-01-01 (proleptic Gregorian calendar). */
    private val epochDay: Int
        get() {
            // Count from March so that the leap day ends the year.
            val y = if (month <= 2) year - 1 else year
            val era = y.floorDiv(400)
            val yearOfEra = y - era * 400
            val shiftedMonth = (month + 9) % MONTHS_PER_YEAR
            val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146097 + dayOfEra - 719468
        }

    companion object {
        /** Parses `YYYY-MM-DD`, or returns `null` if [text] is not a valid date. */
        fun parseOrNull(text: String): LocalDate? {
            val parts = text.split("-")
            if (parts.size != 3 || parts[2].length != 2) return null
            val yearMonth = YearMonth.parseOrNull("${parts[0]}-${parts[1]}") ?: return null
            val day = parts[2].toIntOrNull() ?: return null
            if (day !in 1..daysInMonth(yearMonth.year, yearMonth.month)) return null
            return LocalDate(yearMonth.year, yearMonth.month, day)
        }

        fun parse(text: String): LocalDate =
            requireNotNull(parseOrNull(text)) { "not a YYYY-MM-DD date: $text" }

        fun today(): LocalDate = parse(currentDate())
    }
}

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

internal object YearMonthSerializer : KSerializer<YearMonth> {
    override val descriptor = PrimitiveSerialDescriptor("jp.cjbc.kakeibo.YearMonth", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: YearMonth) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): YearMonth = YearMonth.parse(decoder.decodeString())
}

internal object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("jp.cjbc.kakeibo.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
