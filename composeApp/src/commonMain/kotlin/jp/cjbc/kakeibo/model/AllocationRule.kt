package jp.cjbc.kakeibo.model

import jp.cjbc.kakeibo.util.generateUlid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A template for splitting an income of one category into funds. Changing it does not change
 * past incomes, which store their own allocations.
 */
@Serializable
data class AllocationRule(
    val incomeCategoryId: CategoryId,
    val lines: List<RuleLine>,
    /** The fund that receives what the [lines] leave. */
    val restFundId: FundId,
    /** A new rule gets a new ID, and so does one saved before rules had IDs. */
    val id: AllocationRuleId = AllocationRuleId(generateUlid()),
)

@Serializable
sealed interface RuleLine {
    val fundId: FundId

    @Serializable
    @SerialName("fixed")
    data class Fixed(override val fundId: FundId, val amount: Long) : RuleLine

    /** [percent] percent of the whole income, rounded down to the yen. */
    @Serializable
    @SerialName("percent")
    data class Percent(override val fundId: FundId, val percent: Int) : RuleLine

    /** An amount entered each time an income is recorded. */
    @Serializable
    @SerialName("ask")
    data class Ask(override val fundId: FundId) : RuleLine
}
