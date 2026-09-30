package jp.cjbc.kakeibo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val Bg = Color(0xFFF2EFE8); private val Surface = Color(0xFFFFFFFE); private val Text = Color(0xFF1A1714)
private val Muted = Color(0xFF8A8580); private val Accent = Color(0xFF8B6914); private val AccentBg = Color(0xFFEDE3CC)
private val Income = Color(0xFF2D6A4F); private val Expense = Color(0xFFB83232); private val Border = Color(0xFFDDD8CE)
private val shape = RoundedCornerShape(12.dp)

private enum class Tab(val label: String, val icon: String) { Home("ホーム", "🏠"), Monthly("月次", "📊"), Savings("積立", "🎯"), Recurring("固定費", "🔄"), Analytics("分析", "📈") }

@Composable fun App() {
    val ledger = remember { Ledger().also { it.applyRecurring() } }
    var revision by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(Tab.Home) }
    fun refresh() { revision++ }
    MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Bg, surface = Surface)) {
        Scaffold(containerColor = Bg, topBar = {
            Row(Modifier.fillMaxWidth().background(Surface).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("家計簿", color = Accent, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(ymLabel(nowYM()), color = Muted, fontSize = 12.sp)
            }
        }, bottomBar = {
            Row(Modifier.fillMaxWidth().background(Surface).border(1.dp, Border), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tab.entries.forEach { item ->
                    Column(Modifier.weight(1f).clickable { tab = item }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(item.icon, fontSize = 16.sp); Text(item.label, color = if (tab == item) Accent else Muted, fontSize = 10.sp, fontWeight = if (tab == item) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                // Reading revision makes every persistence operation immediately redraw shared UI state.
                revision
                when (tab) {
                    Tab.Home -> Home(ledger, ::refresh)
                    Tab.Monthly -> Monthly(ledger, ::refresh)
                    Tab.Savings -> Savings(ledger, ::refresh)
                    Tab.Recurring -> Recurring(ledger, ::refresh)
                    Tab.Analytics -> Analytics(ledger, ::refresh)
                }
            }
        }
    }
}

@Composable private fun Page(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), content = content)
@Composable private fun Card(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Surface, shape).border(1.dp, Border, shape).padding(16.dp), content = content)
@Composable private fun Section(title: String) = Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
@Composable private fun Amount(value: Double, type: TransactionType? = null) = Text(if (type == null) yen(value) else signedYen(value, type), color = when (type) { TransactionType.income -> Income; TransactionType.expense -> Expense; null -> Text }, fontWeight = FontWeight.Bold)
@Composable private fun Progress(value: Double, total: Double, color: Color = Accent) { LinearProgressIndicator(progress = { min(1.0, if (total == 0.0) 0.0 else value / total).toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp), color = if (value > total && total > 0) Expense else color, trackColor = Border) }
@Composable private fun Action(label: String, onClick: () -> Unit, primary: Boolean = true, modifier: Modifier = Modifier) { Button(onClick, modifier, colors = ButtonDefaults.buttonColors(containerColor = if (primary) Accent else Surface, contentColor = if (primary) Color.White else Accent), border = if (primary) null else ButtonDefaults.outlinedButtonBorder) { Text(label, fontSize = 12.sp) } }
@Composable private fun Field(value: String, label: String, change: (String) -> Unit, modifier: Modifier = Modifier) { OutlinedTextField(value, change, label = { Text(label) }, modifier = modifier, singleLine = true) }

@Composable private fun Home(ledger: Ledger, refresh: () -> Unit) {
    var amount by remember { mutableStateOf("") }; var type by remember { mutableStateOf(TransactionType.expense) }
    var category by remember { mutableStateOf(expenseCategories.first()) }; var description by remember { mutableStateOf("") }; var date by remember { mutableStateOf(today()) }
    val current = ledger.transactions.filter { it.date.startsWith(nowYM()) }; val inc = current.filter { it.type == TransactionType.income }.sumOf { it.amount }; val exp = current.filter { it.type == TransactionType.expense }.sumOf { it.amount }
    Page {
        Column(Modifier.fillMaxWidth().background(Accent, shape).padding(20.dp)) { Text("${ymLabel(nowYM())} の収支", color = Color.White.copy(alpha = .75f), fontSize = 12.sp); Text((if (inc - exp >= 0) "+" else "") + yen(abs(inc - exp)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp); Row { Text("収入 +${yen(inc)}", color = Color(0xFFA0E8B8), modifier = Modifier.padding(end = 18.dp)); Text("支出 -${yen(exp)}", color = Color(0xFFF8A8A8)) } }
        Spacer(Modifier.height(12.dp)); Card {
            Text("クイック入力", color = Muted, fontSize = 12.sp); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { TransactionType.entries.forEach { t -> Action(if (t == TransactionType.expense) "支出" else "収入", { type = t; category = (if (t == TransactionType.expense) expenseCategories else incomeCategories).first() }, t == type, Modifier.weight(1f)) } }
            Field(amount, "金額", { amount = it }); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Field(category, "カテゴリ（候補: ${(if (type == TransactionType.expense) expenseCategories else incomeCategories).joinToString("、")}）", { category = it }, Modifier.weight(1f)); Field(date, "日付 (YYYY-MM-DD)", { date = it }, Modifier.weight(1f)) }; Field(description, "メモ（任意）", { description = it })
            Action("記録する", { amount.toDoubleOrNull()?.takeIf { it != 0.0 }?.let { ledger.setTransactions(listOf(Transaction(id(), date, abs(it), type, category, description.trim())) + ledger.transactions); amount = ""; description = ""; date = today(); refresh() } }, modifier = Modifier.fillMaxWidth())
        }
        Section("最近の記録"); Card { if (ledger.transactions.isEmpty()) Empty("まだ記録がありません") else ledger.transactions.sortedByDescending { it.date }.take(20).forEach { tx -> TransactionRow(tx) { ledger.setTransactions(ledger.transactions.filterNot { it.id == tx.id }); refresh() } } }
    }
}

@Composable private fun TransactionRow(tx: Transaction, onDelete: () -> Unit) = Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(Color(categoryColors[tx.category] ?: 0xFFB0B0B0), RoundedCornerShape(2.dp))); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(if (tx.description.isBlank()) tx.category else tx.description, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium); Text("${tx.date} · ${tx.category}${if (tx.isRecurring) " · 🔄" else ""}", color = Muted, fontSize = 11.sp) }; Amount(tx.amount, tx.type); Text("×", color = Muted, fontSize = 20.sp, modifier = Modifier.padding(start = 10.dp).clickable(onClick = onDelete)) }
@Composable private fun Empty(text: String) = Text(text, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp))

@Composable private fun Monthly(ledger: Ledger, refresh: () -> Unit) {
    var month by remember { mutableStateOf(nowYM()) }; var period by remember { mutableStateOf(1) }; var editing by remember { mutableStateOf<String?>(null) }; var budgetText by remember { mutableStateOf("") }
    val months = (0 until period).map { addMonths(month, it - period + 1) }; val tx = ledger.transactions.filter { t -> months.any { t.date.startsWith(it) } }; val income = tx.filter { it.type == TransactionType.income }.sumOf { it.amount }; val expense = tx.filter { it.type == TransactionType.expense }.sumOf { it.amount }
    Page { Card { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Action("‹", { month = addMonths(month, -1) }, false); Text(ymLabel(month), fontWeight = FontWeight.Bold); Action("›", { month = addMonths(month, 1) }, false) } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf(1 to "1ヶ月", 3 to "3ヶ月", 6 to "6ヶ月", 12 to "1年").forEach { (n, label) -> Action(label, { period = n }, n == period, Modifier.weight(1f)) } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Stat("収入", income, Income, Modifier.weight(1f)); Stat("支出", expense, Expense, Modifier.weight(1f)); Stat("差額", income - expense, if (income >= expense) Income else Expense, Modifier.weight(1f)) }; Section("カテゴリ別支出"); Card { val shown = expenseCategories.filter { c -> tx.any { it.type == TransactionType.expense && it.category == c } || ledger.budgets.any { it.category == c } }; if (shown.isEmpty()) Empty("この期間の記録がありません") else shown.forEach { cat -> val actual = tx.filter { it.type == TransactionType.expense && it.category == cat }.sumOf { it.amount }; val budget = (ledger.budgets.find { it.category == cat }?.monthlyAmount ?: 0.0) * period; Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(cat, Modifier.weight(1f)); Amount(actual); Text(" ✏", modifier = Modifier.clickable { editing = cat; budgetText = (ledger.budgets.find { it.category == cat }?.monthlyAmount ?: 0.0).toString() }) }; if (budget > 0) { Progress(actual, budget, Color(categoryColors[cat]!!)); Text("予算 ${yen(budget)}${if (actual > budget) "  ⚠ +${yen(actual - budget)}" else ""}", color = if (actual > budget) Expense else Muted, fontSize = 11.sp) }; if (editing == cat) { Field(budgetText, "月次予算", { budgetText = it }); Action("保存", { val value = budgetText.toDoubleOrNull() ?: 0.0; val old = ledger.budgets.find { it.category == cat }; ledger.setBudgets(if (old == null) ledger.budgets + Budget(id(), cat, value) else ledger.budgets.map { if (it.id == old.id) it.copy(monthlyAmount = value) else it }); editing = null; refresh() }) } } } }
}
@Composable private fun Stat(label: String, value: Double, color: Color, modifier: Modifier) = Column(modifier.background(Surface, shape).border(1.dp, Border, shape).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(label, color = Muted, fontSize = 11.sp); Text(yen(value), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp) }

@Composable private fun Savings(ledger: Ledger, refresh: () -> Unit) {
    var adding by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; var monthly by remember { mutableStateOf("") }; var start by remember { mutableStateOf(nowYM()) }; var target by remember { mutableStateOf(addMonths(nowYM(), 12)) }
    var contributionFor by remember { mutableStateOf<String?>(null) }; var contributionAmount by remember { mutableStateOf("") }; var contributionDate by remember { mutableStateOf(today()) }
    Page { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Section("積立目標"); Action("+ 追加", { adding = !adding }) }
        if (adding) Card { Text("新しい積立目標", fontWeight = FontWeight.Bold); Field(name, "名前 / 目的", { name = it }); Field(monthly, "月次目標額", { monthly = it }); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Field(start, "開始月", { start = it }, Modifier.weight(1f)); Field(target, "目標達成月", { target = it }, Modifier.weight(1f)) }; Row { Action("追加する", { monthly.toDoubleOrNull()?.takeIf { name.isNotBlank() && it > 0 }?.let { ledger.setSavings(ledger.savings + Saving(id(), name.trim(), it, start, target)); adding = false; name = ""; monthly = ""; refresh() } }); Spacer(Modifier.width(8.dp)); Action("キャンセル", { adding = false }, false) } }
        if (ledger.savings.isEmpty() && !adding) Card { Empty("🎯\n積立目標を設定しましょう") }
        ledger.savings.forEach { goal ->
            val totalMonths = max(1, monthsBetween(goal.startYM, goal.targetYM) + 1); val elapsed = min(totalMonths, max(0, monthsBetween(goal.startYM, nowYM()) + 1)); val total = totalMonths * goal.monthlyTarget; val actual = goal.contributions.sumOf { it.amount }; val expected = elapsed * goal.monthlyTarget; val diff = actual - expected
            Card { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(goal.name, fontWeight = FontWeight.Bold); Text("${ymLabel(goal.startYM)} 〜 ${ymLabel(goal.targetYM)} · あと${max(0, monthsBetween(nowYM(), goal.targetYM))}ヶ月", color = Muted, fontSize = 11.sp) }; Text("×", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { ledger.setSavings(ledger.savings.filterNot { it.id == goal.id }); refresh() }) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("月次目標", yen(goal.monthlyTarget), Accent); Metric("総目標", yen(total), Text); Metric("積立済", yen(actual), Income); Metric("計画差額", (if (diff >= 0) "+" else "") + yen(abs(diff)), if (diff >= 0) Income else Expense) }; Progress(actual, total, Income); Text("${((actual / total) * 100).coerceAtMost(100.0).roundToInt()}% 達成　残 ${yen(max(0.0, total - actual))}", color = Muted, fontSize = 11.sp)
                goal.contributions.sortedByDescending { it.date }.forEach { c -> Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(c.date, color = Muted, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("+${yen(c.amount)}", color = Income, fontWeight = FontWeight.Bold); Text(" ×", color = Muted, modifier = Modifier.clickable { ledger.setSavings(ledger.savings.map { if (it.id == goal.id) it.copy(contributions = it.contributions.filterNot { x -> x.id == c.id }) else it }); refresh() }) } }
                if (contributionFor == goal.id) { Field(contributionAmount, "積立額", { contributionAmount = it }); Field(contributionDate, "日付 (YYYY-MM-DD)", { contributionDate = it }); Row { Action("記録", { contributionAmount.toDoubleOrNull()?.takeIf { it > 0 }?.let { amount -> ledger.setSavings(ledger.savings.map { if (it.id == goal.id) it.copy(contributions = it.contributions + Contribution(id(), contributionDate, amount)) else it }); contributionFor = null; contributionAmount = ""; refresh() } }); Spacer(Modifier.width(8.dp)); Action("取消", { contributionFor = null }, false) } } else Action("+ 積立を記録する", { contributionFor = goal.id; contributionDate = today() }, false, Modifier.fillMaxWidth())
            }
        }
    }
}
@Composable private fun Metric(label: String, value: String, color: Color) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label, color = Muted, fontSize = 10.sp); Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }

@Composable private fun Recurring(ledger: Ledger, refresh: () -> Unit) {
    var adding by remember { mutableStateOf(false) }; var description by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var type by remember { mutableStateOf(TransactionType.expense) }; var category by remember { mutableStateOf(expenseCategories.first()) }; var day by remember { mutableStateOf("1") }
    val activeIncome = ledger.recurring.filter { it.active && it.type == TransactionType.income }.sumOf { it.amount }; val activeExpense = ledger.recurring.filter { it.active && it.type == TransactionType.expense }.sumOf { it.amount }
    Page { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Section("固定収支"); Action("+ 追加", { adding = !adding }) }; if (activeIncome > 0 || activeExpense > 0) Card { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("月次固定収入", "+${yen(activeIncome)}", Income); Metric("月次固定支出", "-${yen(activeExpense)}", Expense); Metric("差引", yen(abs(activeIncome - activeExpense)), if (activeIncome >= activeExpense) Income else Expense) } }
        if (adding) Card { Text("固定収支を追加", fontWeight = FontWeight.Bold); Row { TransactionType.entries.forEach { t -> Action(if (t == TransactionType.expense) "支出" else "収入", { type = t; category = (if (t == TransactionType.expense) expenseCategories else incomeCategories).first() }, type == t, Modifier.weight(1f)) } }; Field(description, "名称", { description = it }); Field(amount, "金額", { amount = it }); Field(category, "カテゴリ（自由入力可）", { category = it }); Field(day, "毎月何日 (1〜28)", { day = it }); Row { Action("追加する", { amount.toDoubleOrNull()?.takeIf { description.isNotBlank() && it > 0 }?.let { ledger.setRecurring(ledger.recurring + RecurringItem(id(), description.trim(), it, type, category, (day.toIntOrNull() ?: 1).coerceIn(1, 28))); adding = false; description = ""; amount = ""; refresh() } }); Spacer(Modifier.width(8.dp)); Action("キャンセル", { adding = false }, false) } }
        if (ledger.recurring.isEmpty() && !adding) Card { Empty("🔄\n固定収支を登録すると毎月自動記録されます") }
        TransactionType.entries.reversed().forEach { kind -> val items = ledger.recurring.filter { it.type == kind }; if (items.isNotEmpty()) { Section(if (kind == TransactionType.expense) "固定支出" else "固定収入"); Card { items.forEach { item -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.description, fontWeight = FontWeight.Medium); Text("${item.category} · 毎月${item.dayOfMonth}日${item.lastApplied?.let { " · 最終: $it" } ?: ""}", color = Muted, fontSize = 11.sp) }; Amount(item.amount, item.type); Action(if (item.active) "ON" else "OFF", { ledger.setRecurring(ledger.recurring.map { if (it.id == item.id) it.copy(active = !it.active) else it }); refresh() }, item.active, Modifier.padding(start = 6.dp)); Text("×", color = Muted, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp).clickable { ledger.setRecurring(ledger.recurring.filterNot { it.id == item.id }); refresh() }) } } } } }
    }
}

@Composable private fun Analytics(ledger: Ledger, refresh: () -> Unit) {
    var backupText by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }
    val months = (0 until 12).map { addMonths(nowYM(), it - 11) }
    val rows = months.map { month -> val list = ledger.transactions.filter { it.date.startsWith(month) }; Triple(month, list.filter { it.type == TransactionType.income }.sumOf { it.amount }, list.filter { it.type == TransactionType.expense }.sumOf { it.amount }) }
    val maxValue = max(1.0, rows.maxOf { max(it.second, it.third) })
    val patterns = ledger.transactions.filter { it.description.isNotBlank() }.groupBy { it.description.trim().lowercase() }.values.filter { it.size >= 3 }.map { values -> val ordered = values.sortedBy { it.date }; val gaps = ordered.zipWithNext { a, b -> daysBetween(a.date, b.date) }; PatternSummary(ordered.first().description, values.size, gaps.average().roundToInt(), values.map { it.amount }.average(), ordered.first().type, ordered.last().date) }.sortedBy { it.interval }
    Page { Section("月次収支（過去12ヶ月）"); Card { Text("収入", color = Income, fontSize = 11.sp); Text("支出", color = Expense, fontSize = 11.sp); rows.forEach { (month, income, expense) -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(month.takeLast(2) + "月", color = Muted, fontSize = 10.sp, modifier = Modifier.width(30.dp)); Column(Modifier.weight(1f)) { Progress(income, maxValue, Income); Spacer(Modifier.height(2.dp)); Progress(expense, maxValue, Expense) }; Text(" ${yen(income - expense)}", fontSize = 10.sp, modifier = Modifier.width(80.dp), textAlign = TextAlign.End) } } }
        Section("残高推移（累積）"); Card { var cumulative = 0.0; rows.forEach { (month, income, expense) -> cumulative += income - expense; Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(ymLabel(month), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text((if (cumulative >= 0) "+" else "-") + yen(abs(cumulative)), color = if (cumulative >= 0) Income else Expense, fontWeight = FontWeight.Bold) } } }
        Section("購入パターン"); Card { if (patterns.isEmpty()) Empty("同じメモで3回以上記録すると購入パターンが表示されます") else patterns.take(12).forEach { p -> Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) { Column(Modifier.weight(1f)) { Text(p.description, fontWeight = FontWeight.Medium); Text("約${p.interval}日ごと · ${p.count}回 · 最終: ${p.lastDate}", color = Muted, fontSize = 11.sp) }; Amount(p.amount, p.type) } } }
        Section("データ管理"); Card { Text("JSON バックアップ", fontWeight = FontWeight.Bold); Text("エクスポートした内容をコピーして保存し、同じ欄へ貼り付けてインポートできます。", color = Muted, fontSize = 11.sp); Row { Action("エクスポート", { backupText = ledger.backup(); message = "バックアップをこの欄に出力しました" }); Spacer(Modifier.width(8.dp)); Action("インポート", { message = if (ledger.restore(backupText)) { refresh(); "インポートが完了しました" } else "ファイルの形式が正しくありません" }, false) }; Field(backupText, "バックアップ JSON", { backupText = it }, Modifier.fillMaxWidth().height(160.dp)); if (message.isNotEmpty()) Text(message, color = if (message.startsWith("インポートが完了")) Income else Expense, fontSize = 12.sp) }
    }
}
private data class PatternSummary(val description: String, val count: Int, val interval: Int, val amount: Double, val type: TransactionType, val lastDate: String)
private fun daysBetween(a: String, b: String): Int {
    // ISO dates sort lexically; convert only the small, fixed calendar representation to a day count.
    fun day(s: String): Int { val p = s.split("-").map { it.toInt() }; return p[0] * 365 + p[1] * 30 + p[2] }
    return day(b) - day(a)
}
