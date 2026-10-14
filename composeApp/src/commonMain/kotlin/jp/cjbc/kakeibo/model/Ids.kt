package jp.cjbc.kakeibo.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class FundId(val value: String)

/** A built-in category's language-independent ID (e.g. `food`), or a user-defined category's name. */
@Serializable
@JvmInline
value class CategoryId(val value: String)

@Serializable
@JvmInline
value class TransactionId(val value: String)

@Serializable
@JvmInline
value class RecurringItemId(val value: String)
