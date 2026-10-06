package jp.cjbc.kakeibo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.cjbc.kakeibo.currentDate
import jp.cjbc.kakeibo.data.Ledger
import jp.cjbc.kakeibo.model.Transaction
import jp.cjbc.kakeibo.model.TransactionType
import jp.cjbc.kakeibo.model.categoriesFor
import jp.cjbc.kakeibo.model.categoryColor
import jp.cjbc.kakeibo.ui.component.ActionButton
import jp.cjbc.kakeibo.ui.component.AmountText
import jp.cjbc.kakeibo.ui.component.EmptyMessage
import jp.cjbc.kakeibo.ui.component.LabeledTextField
import jp.cjbc.kakeibo.ui.component.ScreenColumn
import jp.cjbc.kakeibo.ui.component.SectionCard
import jp.cjbc.kakeibo.ui.component.SectionHeader
import jp.cjbc.kakeibo.ui.component.categoryNames
import jp.cjbc.kakeibo.ui.theme.ACCENT_COLOR
import jp.cjbc.kakeibo.ui.theme.CARD_SHAPE
import jp.cjbc.kakeibo.ui.theme.MUTED_COLOR
import jp.cjbc.kakeibo.util.currentYearMonth
import jp.cjbc.kakeibo.util.formatYearMonth
import jp.cjbc.kakeibo.util.formatYen
import jp.cjbc.kakeibo.util.generateId
import kotlin.math.abs

private const val RECENT_TRANSACTION_COUNT = 20

@Composable
internal fun HomeScreen(ledger: Ledger, onLedgerChange: () -> Unit) {
    val categoryNames = categoryNames()
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    val defaultCategory = categoryNames.display(categoriesFor(type).first())
    var category by remember(defaultCategory) { mutableStateOf(defaultCategory) }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(currentDate()) }
    val currentTransactions = ledger.transactions.filter { it.date.startsWith(currentYearMonth()) }
    val income = currentTransactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amount }
    val expense = currentTransactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }
    val balance = income - expense

    ScreenColumn {
        Column(
            Modifier
                .fillMaxWidth()
                .background(ACCENT_COLOR, CARD_SHAPE)
                .padding(20.dp),
        ) {
            Text(
                "${formatYearMonth(currentYearMonth())} の収支",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
            )
            Text(
                (if (balance >= 0) "+" else "") + formatYen(abs(balance)),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
            Row {
                Text(
                    "収入 +${formatYen(income)}",
                    color = Color(0xFFA0E8B8),
                    modifier = Modifier.padding(end = 18.dp),
                )
                Text("支出 -${formatYen(expense)}", color = Color(0xFFF8A8A8))
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("クイック入力", color = MUTED_COLOR, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { entry ->
                    ActionButton(
                        label = if (entry == TransactionType.EXPENSE) "支出" else "収入",
                        onClick = { type = entry },
                        isPrimary = entry == type,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            LabeledTextField(amount, "金額", { amount = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledTextField(
                    category,
                    "カテゴリ（候補: ${categoriesFor(type).joinToString("、") { categoryNames.display(it) }}）",
                    { category = it },
                    Modifier.weight(1f),
                )
                LabeledTextField(date, "日付 (YYYY-MM-DD)", { date = it }, Modifier.weight(1f))
            }
            LabeledTextField(description, "メモ（任意）", { description = it })
            ActionButton(
                label = "記録する",
                onClick = {
                    amount.toDoubleOrNull()?.takeIf { it != 0.0 }?.let {
                        val transaction = Transaction(
                            id = generateId(),
                            date = date,
                            amount = abs(it),
                            type = type,
                            category = categoryNames.parse(category),
                            description = description.trim(),
                        )
                        ledger.updateTransactions(listOf(transaction) + ledger.transactions)
                        amount = ""
                        description = ""
                        date = currentDate()
                        onLedgerChange()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionHeader("最近の記録")
        SectionCard {
            if (ledger.transactions.isEmpty()) {
                EmptyMessage("まだ記録がありません")
            } else {
                ledger.transactions
                    .sortedByDescending { it.date }
                    .take(RECENT_TRANSACTION_COUNT)
                    .forEach { transaction ->
                        TransactionRow(transaction) {
                            ledger.updateTransactions(
                                ledger.transactions.filterNot { it.id == transaction.id },
                            )
                            onLedgerChange()
                        }
                    }
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, onDelete: () -> Unit) {
    val category = categoryNames().display(transaction.category)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    Color(categoryColor(transaction.category)),
                    RoundedCornerShape(2.dp),
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                transaction.description.ifBlank { category },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${transaction.date} · $category" +
                    if (transaction.isRecurring) " · 🔄" else "",
                color = MUTED_COLOR,
                fontSize = 11.sp,
            )
        }
        AmountText(transaction.amount, transaction.type)
        Text(
            "×",
            color = MUTED_COLOR,
            fontSize = 20.sp,
            modifier = Modifier.padding(start = 10.dp).clickable(onClick = onDelete),
        )
    }
}
