package jp.cjbc.kakeibo.model

import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/** The most digits the entry keypad accepts: up to 999,999,999 yen. */
const val MAX_AMOUNT_DIGITS = 9

/**
 * The digits after pressing [key] (`0`–`9`, `00` or `000`) on the keypad showing [digits].
 * Leading zeros are dropped, and a key that would exceed [MAX_AMOUNT_DIGITS] does nothing.
 */
fun pressKey(digits: String, key: String): String {
    require(key.isNotEmpty() && key.all(Char::isDigit)) { "not a digit key: $key" }
    val next = (digits + key).trimStart('0')
    return if (next.length > MAX_AMOUNT_DIGITS) digits else next
}

/** The digits after pressing backspace. */
fun pressBackspace(digits: String): String = digits.dropLast(1)

/** What recording an expense does to its fund, when it leaves the fund negative. */
sealed interface FundWarning {
    /** The fund is already [over] overspent and will be [overAfter] overspent. */
    data class AlreadyOver(val over: Long, val overAfter: Long) : FundWarning

    /** The fund has [left] and will be [overAfter] overspent. */
    data class WillBeOver(val left: Long, val overAfter: Long) : FundWarning
}

/** The warning for spending [amount] from a fund whose balance is [balance], if any. */
fun fundWarning(balance: Long, amount: Long): FundWarning? {
    val after = balance - amount
    return when {
        balance < 0 -> FundWarning.AlreadyOver(-balance, -after)
        after < 0 -> FundWarning.WillBeOver(balance, -after)
        else -> null
    }
}

/** Incomes minus expenses dated in [month]. */
fun monthNet(transactions: List<Transaction>, month: YearMonth): Long =
    transactions.filter { it.date.yearMonth == month }.sumOf {
        when (it) {
            is Transaction.Income -> it.amount
            is Transaction.Expense -> -it.amount
        }
    }

/** How a transaction changes the user's money: negative for expenses. */
val Transaction.signedAmount: Long
    get() = when (this) {
        is Transaction.Expense -> -amount
        is Transaction.Income -> amount
    }
