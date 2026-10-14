package jp.cjbc.kakeibo.model

import kotlinx.serialization.Serializable

const val MIN_RECURRING_DAY = 1
const val MAX_RECURRING_DAY = 28

/** An expense recorded automatically every month. */
@Serializable
data class RecurringItem(
    val id: RecurringItemId,
    val name: String,
    val amount: Long,
    val categoryId: CategoryId,
    val fundId: FundId,
    /** 1 to 28, so that every month has the day. */
    val dayOfMonth: Int,
    val enabled: Boolean,
    val lastAppliedMonth: YearMonth?,
)
