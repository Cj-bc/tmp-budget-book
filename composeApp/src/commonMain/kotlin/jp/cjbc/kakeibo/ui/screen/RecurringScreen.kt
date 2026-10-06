package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.RecurringItem
import jp.cjbc.kakeibo.model.TransactionType
import jp.cjbc.kakeibo.model.categoriesFor
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.MetricItem
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.theme.EXPENSE_COLOR
import jp.cjbc.kakeibo.ui.theme.INCOME_COLOR
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.util.formatYen
import jp.cjbc.kakeibo.util.generateId
import kotlin.math.abs

private const val MIN_DAY_OF_MONTH = 1
private const val MAX_DAY_OF_MONTH = 28

@Composable
internal fun RecurringScreen(ledger: Ledger, onLedgerChange: () -> Unit) {
    val categoryNames = categoryNames()
    var isAdding by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    val defaultCategory = categoryNames.display(categoriesFor(type).first())
    var category by remember(defaultCategory) { mutableStateOf(defaultCategory) }
    var dayOfMonth by remember { mutableStateOf("1") }
    val activeIncome = ledger.recurringItems
        .filter { it.active && it.type == TransactionType.INCOME }
        .sumOf { it.amount }
    val activeExpense = ledger.recurringItems
        .filter { it.active && it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }

    ScreenColumn {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("固定収支")
            ActionButton("+ 追加", { isAdding = !isAdding })
        }
        if (activeIncome > 0 || activeExpense > 0) {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem("月次固定収入", "+${formatYen(activeIncome)}", INCOME_COLOR)
                    MetricItem("月次固定支出", "-${formatYen(activeExpense)}", EXPENSE_COLOR)
                    MetricItem(
                        "差引",
                        formatYen(abs(activeIncome - activeExpense)),
                        if (activeIncome >= activeExpense) INCOME_COLOR else EXPENSE_COLOR,
                    )
                }
            }
        }
        if (isAdding) {
            SectionCard {
                Text("固定収支を追加", fontWeight = FontWeight.Bold)
                Row {
                    TransactionType.entries.forEach { entry ->
                        ActionButton(
                            label = if (entry == TransactionType.EXPENSE) "支出" else "収入",
                            onClick = { type = entry },
                            isPrimary = type == entry,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                LabeledTextField(description, "名称", { description = it })
                LabeledTextField(amount, "金額", { amount = it })
                LabeledTextField(category, "カテゴリ（自由入力可）", { category = it })
                LabeledTextField(dayOfMonth, "毎月何日 (1〜28)", { dayOfMonth = it })
                Row {
                    ActionButton("追加する", {
                        amount.toDoubleOrNull()
                            ?.takeIf { description.isNotBlank() && it > 0 }
                            ?.let {
                                val item = RecurringItem(
                                    id = generateId(),
                                    description = description.trim(),
                                    amount = it,
                                    type = type,
                                    category = categoryNames.parse(category),
                                    dayOfMonth = (dayOfMonth.toIntOrNull() ?: MIN_DAY_OF_MONTH)
                                        .coerceIn(MIN_DAY_OF_MONTH, MAX_DAY_OF_MONTH),
                                )
                                ledger.updateRecurringItems(ledger.recurringItems + item)
                                isAdding = false
                                description = ""
                                amount = ""
                                onLedgerChange()
                            }
                    })
                    Spacer(Modifier.width(8.dp))
                    ActionButton("キャンセル", { isAdding = false }, isPrimary = false)
                }
            }
        }
        if (ledger.recurringItems.isEmpty() && !isAdding) {
            SectionCard { EmptyMessage("🔄\n固定収支を登録すると毎月自動記録されます") }
        }
        TransactionType.entries.reversed().forEach { entry ->
            val items = ledger.recurringItems.filter { it.type == entry }
            if (items.isNotEmpty()) {
                SectionHeader(if (entry == TransactionType.EXPENSE) "固定支出" else "固定収入")
                SectionCard {
                    items.forEach { item -> RecurringItemRow(ledger, item, onLedgerChange) }
                }
            }
        }
    }
}

@Composable
private fun RecurringItemRow(ledger: Ledger, item: RecurringItem, onLedgerChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.description, fontWeight = FontWeight.Medium)
            Text(
                "${categoryNames().display(item.category)} · 毎月${item.dayOfMonth}日" +
                    (item.lastApplied?.let { " · 最終: $it" } ?: ""),
                color = MUTED_COLOR,
                fontSize = 11.sp,
            )
        }
        AmountText(item.amount, item.type)
        ActionButton(
            if (item.active) "ON" else "OFF",
            {
                ledger.updateRecurringItems(
                    ledger.recurringItems.map {
                        if (it.id == item.id) it.copy(active = !it.active) else it
                    },
                )
                onLedgerChange()
            },
            isPrimary = item.active,
            modifier = Modifier.padding(start = 6.dp),
        )
        Text(
            "×",
            color = MUTED_COLOR,
            fontSize = 20.sp,
            modifier = Modifier.padding(start = 8.dp).clickable {
                ledger.updateRecurringItems(ledger.recurringItems.filterNot { it.id == item.id })
                onLedgerChange()
            },
        )
    }
}
