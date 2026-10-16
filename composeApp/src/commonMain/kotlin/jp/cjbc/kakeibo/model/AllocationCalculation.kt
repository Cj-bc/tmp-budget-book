package jp.cjbc.kakeibo.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** The amount of [line] for an income of [income] yen, given the amount entered for an [RuleLine.Ask] line. */
fun RuleLine.amountFor(income: Long, asked: Long): Long = when (this) {
    is RuleLine.Fixed -> amount
    // A percentage of the whole income, rounded down to the yen.
    is RuleLine.Percent -> income * percent / 100
    is RuleLine.Ask -> asked
}

/** One line of a computed allocation. */
data class PreviewLine(val line: RuleLine, val amount: Long)

/**
 * An income of [income] split by rule lines: [lines] in order, then [rest] to [restFundId].
 * A negative [rest] is a shortage: the lines take more than the income.
 */
data class AllocationPreview(
    val income: Long,
    val lines: List<PreviewLine>,
    val restFundId: FundId?,
) {
    val rest: Long get() = income - lines.sumOf { it.amount }

    /** How much the lines exceed the income; 0 when they fit. */
    val shortage: Long get() = (-rest).coerceAtLeast(0)

    /** Whether the income can be recorded with this allocation. */
    val isRecordable: Boolean get() = shortage == 0L && (rest == 0L || restFundId != null) && lines.all { it.amount >= 0 }

    /** The allocations to store, without zero amounts, the rest last. */
    fun allocations(): List<Allocation> =
        (lines.map { Allocation(it.line.fundId, it.amount) } + listOfNotNull(restFundId?.let { Allocation(it, rest) }))
            .filter { it.amount > 0 }
}

/**
 * Splits an income of [income] by [rule]. [asked] holds the amounts entered for the rule's
 * [RuleLine.Ask] lines by their index; missing ones count as 0. Without a rule, everything is
 * the rest, which goes to [restFundId] (or the rule's own rest fund when it is `null`).
 */
fun previewAllocation(
    rule: AllocationRule?,
    income: Long,
    asked: Map<Int, Long> = emptyMap(),
    restFundId: FundId? = null,
): AllocationPreview = AllocationPreview(
    income = income,
    lines = rule?.lines.orEmpty().mapIndexed { index, line ->
        PreviewLine(line, line.amountFor(income, asked[index] ?: 0))
    },
    restFundId = restFundId ?: rule?.restFundId,
)

/** How money comes into a fund from one income category's rule. */
sealed interface FundIncomeSource {
    val incomeCategoryId: CategoryId

    data class Line(override val incomeCategoryId: CategoryId, val line: RuleLine) : FundIncomeSource

    /** The fund receives what the rule's lines leave. */
    data class Rest(override val incomeCategoryId: CategoryId) : FundIncomeSource
}

/** Every way [fundId] receives money in [rules], in the rules' order. */
fun fundIncomeSources(rules: List<AllocationRule>, fundId: FundId): List<FundIncomeSource> =
    rules.flatMap { rule ->
        rule.lines.filter { it.fundId == fundId }.map { FundIncomeSource.Line(rule.incomeCategoryId, it) } +
            listOfNotNull(FundIncomeSource.Rest(rule.incomeCategoryId).takeIf { rule.restFundId == fundId })
    }

/** A movement of money into or out of a fund. */
data class FundMovement(
    val date: LocalDate,
    /** Positive for allocations, negative for expenses. */
    val amount: Long,
    val transaction: Transaction,
)

/** The allocations to and expenses from [fundId], latest first. */
fun fundMovements(transactions: List<Transaction>, fundId: FundId): List<FundMovement> =
    transactions.flatMap { transaction ->
        when (transaction) {
            is Transaction.Expense ->
                listOfNotNull(FundMovement(transaction.date, -transaction.amount, transaction).takeIf { transaction.fundId == fundId })
            is Transaction.Income -> transaction.allocations.filter { it.fundId == fundId }
                .map { FundMovement(transaction.date, it.amount, transaction) }
        }
    }.sortedByDescending { it.date }

/** The incomes whose allocations are for [month], latest first. */
fun incomesFor(transactions: List<Transaction>, month: YearMonth): List<Transaction.Income> =
    transactions.filterIsInstance<Transaction.Income>().filter { it.budgetMonth == month }.sortedByDescending { it.date }
