package jp.cjbc.kakeibo.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class FundId(val value: String)

/** Identifies a category: a ULID, unique among all categories. What is shown is its [Category.name]. */
@Serializable
@JvmInline
value class CategoryId(val value: String)

@Serializable
@JvmInline
value class TransactionId(val value: String)

@Serializable
@JvmInline
value class RecurringItemId(val value: String)
