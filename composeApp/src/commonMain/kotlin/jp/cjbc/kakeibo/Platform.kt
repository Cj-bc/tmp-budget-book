package jp.cjbc.kakeibo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

expect fun storageGet(key: String): String?
expect fun storagePut(key: String, value: String)
expect fun today(): String

fun id() = "${today().replace("-", "")}-${Random.nextInt(100000, 999999)}"
fun nowYM() = today().take(7)
fun addMonths(ym: String, delta: Int): String {
    val parts = ym.split("-").map { it.toInt() }
    val absolute = parts[0] * 12 + parts[1] - 1 + delta
    return (absolute / 12).toString().padStart(4, '0') + "-" + (absolute % 12 + 1).toString().padStart(2, '0')
}
fun monthsBetween(a: String, b: String): Int {
    val x = a.split("-").map { it.toInt() }; val y = b.split("-").map { it.toInt() }
    return (y[0] - x[0]) * 12 + y[1] - x[1]
}
fun ymLabel(ym: String) = "${ym.substringBefore("-")}年${ym.substringAfter("-").toInt()}月"
fun yen(value: Double) = "¥${value.toLong().toString().reversed().chunked(3).joinToString(",").reversed()}"
fun signedYen(value: Double, type: TransactionType) = if (type == TransactionType.income) "+${yen(value)}" else "-${yen(value)}"

class Ledger {
    var transactions = load("tx") { emptyList<Transaction>() }; private set
    var budgets = load("bud") { emptyList<Budget>() }; private set
    var savings = load("sav") { emptyList<Saving>() }; private set
    var recurring = load("rec") { emptyList<RecurringItem>() }; private set

    private inline fun <reified T> load(key: String, fallback: () -> T): T =
        storageGet("kakeibo:v1:$key")?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() } ?: fallback()
    private inline fun <reified T> save(key: String, value: T) = storagePut("kakeibo:v1:$key", json.encodeToString(value))
    fun setTransactions(value: List<Transaction>) { transactions = value; save("tx", value) }
    fun setBudgets(value: List<Budget>) { budgets = value; save("bud", value) }
    fun setSavings(value: List<Saving>) { savings = value; save("sav", value) }
    fun setRecurring(value: List<RecurringItem>) { recurring = value; save("rec", value) }
    fun backup() = json.encodeToString(Backup(tx = transactions, bud = budgets, sav = savings, rec = recurring))
    fun restore(raw: String): Boolean = runCatching {
        val value = json.decodeFromString<Backup>(raw)
        setTransactions(value.tx); setBudgets(value.bud); setSavings(value.sav); setRecurring(value.rec)
    }.isSuccess
    fun applyRecurring() {
        val month = nowYM(); val day = today().takeLast(2).toInt()
        recurring.filter { it.active && it.lastApplied != month && day >= it.dayOfMonth }.forEach { item ->
            setTransactions(listOf(Transaction(id(), "$month-${item.dayOfMonth.toString().padStart(2, '0')}", item.amount, item.type, item.category, "${item.description}（自動）", true)) + transactions)
            setRecurring(recurring.map { if (it.id == item.id) it.copy(lastApplied = month) else it })
        }
    }
}
