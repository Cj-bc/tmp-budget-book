package jp.cjbc.kakeibo.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Transaction {
    val id: TransactionId
    val date: LocalDate

    /** Always positive, in yen. */
    val amount: Long
    val categoryId: CategoryId
    val memo: String

    @Serializable
    @SerialName("expense")
    data class Expense(
        override val id: TransactionId,
        override val date: LocalDate,
        override val amount: Long,
        override val categoryId: CategoryId,
        override val memo: String,
        /** The fund this expense was paid from. */
        val fundId: FundId,
        /** The recurring item that recorded this expense automatically, if any. */
        val recurringItemId: RecurringItemId?,
    ) : Transaction

    @Serializable
    @SerialName("income")
    data class Income(
        override val id: TransactionId,
        override val date: LocalDate,
        override val amount: Long,
        override val categoryId: CategoryId,
        override val memo: String,
        /** The month whose funds receive the [allocations]. Defaults to the month after [date]. */
        val budgetMonth: YearMonth,
        /** How the income was split into funds. Must add up to [amount]. */
        val allocations: List<Allocation>,
    ) : Transaction {
        val isFullyAllocated: Boolean get() = allocations.sumOf { it.amount } == amount
    }
}

@Serializable
data class Allocation(val fundId: FundId, val amount: Long)

/** The budget month of an income received on [date] unless the user picks another. */
fun defaultBudgetMonth(date: LocalDate): YearMonth = date.yearMonth.plusMonth()

val Transaction.categoryKey: CategoryKey get() = CategoryKey(categoryId, this is Transaction.Expense)
