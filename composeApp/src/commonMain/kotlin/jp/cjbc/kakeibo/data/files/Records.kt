package jp.cjbc.kakeibo.data.files

import jp.cjbc.kakeibo.model.Allocation
import jp.cjbc.kakeibo.model.AllocationRule
import jp.cjbc.kakeibo.model.AllocationRuleId
import jp.cjbc.kakeibo.model.Category
import jp.cjbc.kakeibo.model.CategoryId
import jp.cjbc.kakeibo.model.Fund
import jp.cjbc.kakeibo.model.FundColor
import jp.cjbc.kakeibo.model.FundGoal
import jp.cjbc.kakeibo.model.FundId
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.RecurringItemId
import jp.cjbc.kakeibo.model.RuleLine
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Version 1 of the records in the data files. Field names are snake_case; amounts are in yen.
// A newer version gets classes of its own, and a conversion from these.

@Serializable
data class FundRecord(
    val id: String,
    val name: String,
    val color: Long,
    @SerialName("opening_balance") val openingBalance: Long,
    val goal: GoalRecord,
    @SerialName("show_in_entry") val showInEntry: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
    val archived: Boolean,
) {
    fun toModel() = Fund(
        id = FundId(id),
        name = name,
        color = FundColor(color),
        openingBalance = openingBalance,
        goal = goal.toModel(),
        showInEntry = showInEntry,
        sortOrder = sortOrder,
        archived = archived,
    )

    companion object {
        fun of(fund: Fund) = FundRecord(
            id = fund.id.value,
            name = fund.name,
            color = fund.color.argb,
            openingBalance = fund.openingBalance,
            goal = GoalRecord.of(fund.goal),
            showInEntry = fund.showInEntry,
            sortOrder = fund.sortOrder,
            archived = fund.archived,
        )
    }
}

@Serializable
sealed interface GoalRecord {
    fun toModel(): FundGoal

    @Serializable
    @SerialName("none")
    data object None : GoalRecord {
        override fun toModel() = FundGoal.None
    }

    @Serializable
    @SerialName("target")
    data class Target(
        val amount: Long,
        @SerialName("start_month") val startMonth: YearMonth,
        @SerialName("due_month") val dueMonth: YearMonth,
    ) : GoalRecord {
        override fun toModel() = FundGoal.Target(amount, startMonth, dueMonth)
    }

    companion object {
        fun of(goal: FundGoal): GoalRecord = when (goal) {
            FundGoal.None -> None
            is FundGoal.Target -> Target(goal.amount, goal.startMonth, goal.dueMonth)
        }
    }
}

@Serializable
data class ExpenseCategoryRecord(
    val id: String,
    val name: String,
    val pinned: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
    val archived: Boolean,
    @SerialName("default_fund_id") val defaultFundId: String,
) {
    fun toModel() = Category.Expense(
        id = CategoryId(id),
        name = name,
        pinned = pinned,
        sortOrder = sortOrder,
        archived = archived,
        defaultFundId = FundId(defaultFundId),
    )

    companion object {
        fun of(category: Category.Expense) = ExpenseCategoryRecord(
            id = category.id.value,
            name = category.name,
            pinned = category.pinned,
            sortOrder = category.sortOrder,
            archived = category.archived,
            defaultFundId = category.defaultFundId.value,
        )
    }
}

@Serializable
data class IncomeCategoryRecord(
    val id: String,
    val name: String,
    val pinned: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
    val archived: Boolean,
) {
    fun toModel() = Category.Income(
        id = CategoryId(id),
        name = name,
        pinned = pinned,
        sortOrder = sortOrder,
        archived = archived,
    )

    companion object {
        fun of(category: Category.Income) = IncomeCategoryRecord(
            id = category.id.value,
            name = category.name,
            pinned = category.pinned,
            sortOrder = category.sortOrder,
            archived = category.archived,
        )
    }
}

@Serializable
data class AllocationRuleRecord(
    val id: String,
    @SerialName("income_category_id") val incomeCategoryId: String,
    val lines: List<RuleLineRecord>,
    @SerialName("rest_fund_id") val restFundId: String,
) {
    fun toModel() = AllocationRule(
        incomeCategoryId = CategoryId(incomeCategoryId),
        lines = lines.map { it.toModel() },
        restFundId = FundId(restFundId),
        id = AllocationRuleId(id),
    )

    companion object {
        fun of(rule: AllocationRule) = AllocationRuleRecord(
            id = rule.id.value,
            incomeCategoryId = rule.incomeCategoryId.value,
            lines = rule.lines.map(RuleLineRecord::of),
            restFundId = rule.restFundId.value,
        )
    }
}

@Serializable
sealed interface RuleLineRecord {
    fun toModel(): RuleLine

    @Serializable
    @SerialName("fixed")
    data class Fixed(@SerialName("fund_id") val fundId: String, val amount: Long) : RuleLineRecord {
        override fun toModel() = RuleLine.Fixed(FundId(fundId), amount)
    }

    @Serializable
    @SerialName("percent")
    data class Percent(@SerialName("fund_id") val fundId: String, val percent: Int) : RuleLineRecord {
        override fun toModel() = RuleLine.Percent(FundId(fundId), percent)
    }

    @Serializable
    @SerialName("ask")
    data class Ask(@SerialName("fund_id") val fundId: String) : RuleLineRecord {
        override fun toModel() = RuleLine.Ask(FundId(fundId))
    }

    companion object {
        fun of(line: RuleLine): RuleLineRecord = when (line) {
            is RuleLine.Fixed -> Fixed(line.fundId.value, line.amount)
            is RuleLine.Percent -> Percent(line.fundId.value, line.percent)
            is RuleLine.Ask -> Ask(line.fundId.value)
        }
    }
}

@Serializable
data class RecurringItemRecord(
    val id: String,
    val name: String,
    val amount: Long,
    @SerialName("category_id") val categoryId: String,
    @SerialName("fund_id") val fundId: String,
    @SerialName("day_of_month") val dayOfMonth: Int,
    val enabled: Boolean,
    @SerialName("last_applied_month") val lastAppliedMonth: YearMonth?,
) {
    fun toModel() = RecurringItem(
        id = RecurringItemId(id),
        name = name,
        amount = amount,
        categoryId = CategoryId(categoryId),
        fundId = FundId(fundId),
        dayOfMonth = dayOfMonth,
        enabled = enabled,
        lastAppliedMonth = lastAppliedMonth,
    )

    companion object {
        fun of(item: RecurringItem) = RecurringItemRecord(
            id = item.id.value,
            name = item.name,
            amount = item.amount,
            categoryId = item.categoryId.value,
            fundId = item.fundId.value,
            dayOfMonth = item.dayOfMonth,
            enabled = item.enabled,
            lastAppliedMonth = item.lastAppliedMonth,
        )
    }
}

/** A line of a transactions file, besides its version `v`. */
@Serializable
sealed interface TransactionRecord {
    fun toModel(): Transaction

    @Serializable
    @SerialName("expense")
    data class Expense(
        val id: String,
        @SerialName("transaction_date") val transactionDate: LocalDate,
        val amount: Long,
        @SerialName("category_id") val categoryId: String,
        val memo: String,
        @SerialName("fund_id") val fundId: String,
        @SerialName("recurring_item_id") val recurringItemId: String?,
    ) : TransactionRecord {
        override fun toModel() = Transaction.Expense(
            id = TransactionId(id),
            date = transactionDate,
            amount = amount,
            categoryId = CategoryId(categoryId),
            memo = memo,
            fundId = FundId(fundId),
            recurringItemId = recurringItemId?.let(::RecurringItemId),
        )
    }

    @Serializable
    @SerialName("income")
    data class Income(
        val id: String,
        @SerialName("transaction_date") val transactionDate: LocalDate,
        val amount: Long,
        @SerialName("category_id") val categoryId: String,
        val memo: String,
        @SerialName("budget_month") val budgetMonth: YearMonth,
        val allocations: List<AllocationRecord>,
    ) : TransactionRecord {
        override fun toModel() = Transaction.Income(
            id = TransactionId(id),
            date = transactionDate,
            amount = amount,
            categoryId = CategoryId(categoryId),
            memo = memo,
            budgetMonth = budgetMonth,
            allocations = allocations.map { Allocation(FundId(it.fundId), it.amount) },
        )
    }

    companion object {
        fun of(transaction: Transaction): TransactionRecord = when (transaction) {
            is Transaction.Expense -> Expense(
                id = transaction.id.value,
                transactionDate = transaction.date,
                amount = transaction.amount,
                categoryId = transaction.categoryId.value,
                memo = transaction.memo,
                fundId = transaction.fundId.value,
                recurringItemId = transaction.recurringItemId?.value,
            )
            is Transaction.Income -> Income(
                id = transaction.id.value,
                transactionDate = transaction.date,
                amount = transaction.amount,
                categoryId = transaction.categoryId.value,
                memo = transaction.memo,
                budgetMonth = transaction.budgetMonth,
                allocations = transaction.allocations.map { AllocationRecord(it.fundId.value, it.amount) },
            )
        }
    }
}

@Serializable
data class AllocationRecord(@SerialName("fund_id") val fundId: String, val amount: Long)
