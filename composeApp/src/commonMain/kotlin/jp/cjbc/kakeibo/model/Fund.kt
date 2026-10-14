package jp.cjbc.kakeibo.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An ARGB color such as `0xFF5A616D`. */
@Serializable
@JvmInline
value class FundColor(val argb: Long)

/**
 * A pot of money. Income allocations go in and expenses come out; both the surplus and the
 * shortfall carry over to the next month.
 */
@Serializable
data class Fund(
    val id: FundId,
    val name: String,
    val color: FundColor,
    /** The balance before any allocation or expense. May be negative. */
    val openingBalance: Long,
    val goal: FundGoal,
    /** Whether the entry screen offers this fund. */
    val showInEntry: Boolean,
    val sortOrder: Int,
    /** Archived funds are hidden but still counted, since past transactions refer to them. */
    val archived: Boolean,
)

@Serializable
sealed interface FundGoal {
    @Serializable
    @SerialName("none")
    data object None : FundGoal

    /** Saving [amount] evenly from [startMonth] through [dueMonth]. */
    @Serializable
    @SerialName("target")
    data class Target(
        val amount: Long,
        val startMonth: YearMonth,
        val dueMonth: YearMonth,
    ) : FundGoal
}

/** Whether the entry screen offers a fund with [goal] unless the user changes it. */
fun defaultShowInEntry(goal: FundGoal): Boolean = goal is FundGoal.None
