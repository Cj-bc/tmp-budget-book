<script lang="ts">
  import { transactions, budgets } from '../lib/stores';
  import { EXPENSE_CATS, CAT_COLOR, PERIOD_OPTS } from '../lib/constants';
  import { genId, nowYM, addMonths, yen, ymLabel } from '../lib/utils';
  import ProgressBar from './ProgressBar.svelte';

  interface EditBud {
    category: string;
    monthlyAmount: string;
  }

  let ym:      string      = nowYM();
  let period:  string      = '1';
  let editBud: EditBud | null = null;

  // ── Derived ──────────────────────────────────────────────
  $: n = parseInt(period);

  $: months = Array.from({ length: n }, (_, i) => addMonths(ym, -(n - 1) + i));

  $: periodTx = $transactions.filter(t => months.some(m => t.date.startsWith(m)));

  $: totalIncome  = periodTx.filter(t => t.type === 'income') .reduce((s, t) => s + t.amount, 0);
  $: totalExpense = periodTx.filter(t => t.type === 'expense').reduce((s, t) => s + t.amount, 0);
  $: balance      = totalIncome - totalExpense;

  $: byCategory = Object.fromEntries(
    EXPENSE_CATS.map(cat => [
      cat,
      periodTx
        .filter(t => t.type === 'expense' && t.category === cat)
        .reduce((s, t) => s + t.amount, 0),
    ])
  );

  /** カテゴリの期間予算額を返す（月次予算 × n ヶ月）*/
  function getBudget(cat: string): number {
    const b = $budgets.find(b => b.category === cat);
    return b ? b.monthlyAmount * n : 0;
  }

  /** カテゴリ別予算を保存する */
  function saveBudget(): void {
    if (!editBud) return;
    const { category, monthlyAmount } = editBud;
    const amt = Math.max(0, parseFloat(monthlyAmount) || 0);
    budgets.update(list => {
      const exists = list.find(b => b.category === category);
      if (exists) return list.map(b => b.category === category ? { ...b, monthlyAmount: amt } : b);
      return [...list, { id: genId(), category, monthlyAmount: amt }];
    });
    editBud = null;
  }

  /** 実績か予算があるカテゴリのみ表示 */
  $: shownCats = EXPENSE_CATS.filter(cat => (byCategory[cat] ?? 0) > 0 || getBudget(cat) > 0);
</script>

<!-- ── Month navigation ── -->
<div class="nav-card card">
  <button type="button" class="btn-secondary nav-btn" on:click={() => ym = addMonths(ym, -1)}>‹</button>
  <span class="nav-ym">{ymLabel(ym)}</span>
  <button type="button" class="btn-secondary nav-btn" on:click={() => ym = addMonths(ym, 1)}>›</button>
</div>

<!-- ── Period selector ── -->
<div class="period-row">
  {#each PERIOD_OPTS as p}
    <button type="button" class="period-btn" class:active={period === p.v} on:click={() => period = p.v}>
      {p.l}
    </button>
  {/each}
</div>

<!-- ── Totals ── -->
<div class="totals">
  {#each [
    { l: '収入', v: totalIncome,  cls: 'income'  },
    { l: '支出', v: totalExpense, cls: 'expense' },
    { l: '差額', v: balance,      cls: balance >= 0 ? 'income' : 'expense' },
  ] as item}
    <div class="card total-cell">
      <div class="total-lbl">{item.l}</div>
      <div class="total-val {item.cls}">{yen(item.v)}</div>
    </div>
  {/each}
</div>

<!-- ── Category budgets ── -->
<p class="section-title">カテゴリ別支出</p>
<div class="card">
  {#if shownCats.length === 0}
    <p class="empty-state">この期間の記録がありません</p>
  {:else}
    {#each shownCats as cat}
      {@const actual = (byCategory[cat] ?? 0)}
      {@const budget = getBudget(cat)}
      {@const over   = budget > 0 && actual > budget}
      <div class="cat-row">
        <div class="cat-top">
          <span class="cat-name">
            <span class="dot" style="background:{CAT_COLOR[cat] ?? '#B0B0B0'}"></span>
            {cat}
          </span>
          <span class="cat-right">
            <span class="actual" class:over={over}>{yen(actual)}</span>
            {#if budget > 0}
              <span class="budget">/ {yen(budget)}</span>
            {/if}
            <button type="button" class="edit-btn"
              on:click={() => editBud = { category: cat, monthlyAmount: String($budgets.find(b => b.category === cat)?.monthlyAmount ?? '') }}
              title="予算を編集"
            >✏️</button>
          </span>
        </div>

        {#if budget > 0}
          <ProgressBar value={actual} max={budget} color={CAT_COLOR[cat]} />
          {#if over}
            <p class="over-msg">⚠ 予算超過 +{yen(actual - budget)}</p>
          {/if}
        {/if}

        <!-- Inline budget edit form -->
        {#if editBud?.category === cat}
          <div class="edit-form">
            <span class="edit-lbl">月次予算</span>
            <input class="field-input" type="number" min="0" placeholder="0"
              bind:value={editBud.monthlyAmount}
              on:keydown={e => e.key === 'Enter' && saveBudget()}
            />
            <button type="button" class="btn-primary btn-sm" on:click={saveBudget}>保存</button>
            <button type="button" class="btn-secondary btn-sm" on:click={() => editBud = null}>✕</button>
          </div>
        {/if}
      </div>
    {/each}
  {/if}

  <!-- Budget setup for unconfigured categories -->
  {#if editBud?.category && !shownCats.includes(editBud.category)}
    <div class="edit-form mt">
      <select class="field-input sm-select" bind:value={editBud.category}>
        {#each EXPENSE_CATS as c}<option>{c}</option>{/each}
      </select>
      <input class="field-input" type="number" min="0" placeholder="月次予算" bind:value={editBud.monthlyAmount} />
      <button type="button" class="btn-primary btn-sm" on:click={saveBudget}>追加</button>
      <button type="button" class="btn-secondary btn-sm" on:click={() => editBud = null}>✕</button>
    </div>
  {/if}

  <button type="button" class="btn-secondary btn-full mt"
    on:click={() => {
      const unset = EXPENSE_CATS.find(c => !$budgets.find(b => b.category === c)) ?? EXPENSE_CATS[0]!;
      editBud = { category: unset, monthlyAmount: '' };
    }}
  >
    + 予算を設定する
  </button>
</div>

<style>
  .nav-card { display: flex; align-items: center; gap: 10px; padding: 10px 16px; margin-bottom: 10px; }
  .nav-btn  { padding: 6px 14px; }
  .nav-ym   { flex: 1; text-align: center; font-weight: 600; font-size: 15px; }

  .period-row { display: flex; gap: 6px; margin-bottom: 12px; }
  .period-btn { flex: 1; padding: 7px 4px; border: 1px solid var(--border); border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; font-size: 12px; font-weight: 500; font-family: inherit; transition: all 0.15s; }
  .period-btn.active { border-color: var(--accent); background: var(--ac-bg); color: var(--accent); font-weight: 600; }

  .totals { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; margin-bottom: 12px; }
  .total-cell { padding: 12px; text-align: center; margin-bottom: 0; }
  .total-lbl  { font-size: 11px; color: var(--muted); margin-bottom: 3px; }
  .total-val  { font-weight: 700; font-size: 13px; }
  .total-val.income  { color: var(--income);  }
  .total-val.expense { color: var(--expense); }

  .cat-row    { padding: 10px 0; border-bottom: 1px solid var(--border); }
  .cat-row:last-of-type { border-bottom: none; }
  .cat-top    { display: flex; align-items: center; justify-content: space-between; }
  .cat-name   { display: flex; align-items: center; gap: 8px; font-weight: 500; }
  .dot        { width: 8px; height: 8px; border-radius: 2px; flex-shrink: 0; }
  .cat-right  { display: flex; align-items: center; gap: 5px; flex-shrink: 0; }
  .actual     { font-weight: 600; font-size: 14px; }
  .actual.over { color: var(--expense); }
  .budget     { color: var(--muted); font-size: 12px; }
  .edit-btn   { background: none; border: none; cursor: pointer; font-size: 12px; padding: 0; }
  .over-msg   { font-size: 11px; color: var(--expense); margin-top: 3px; }

  .edit-form  { display: flex; align-items: center; gap: 8px; margin-top: 10px; background: var(--ac-bg); padding: 10px; border-radius: 8px; flex-wrap: wrap; }
  .edit-lbl   { font-size: 12px; color: var(--accent); flex-shrink: 0; }
  .edit-form .field-input { flex: 1; min-width: 80px; }
  .sm-select  { flex: 1; min-width: 80px; }

  .card { background: var(--surf); border-radius: var(--r); padding: 16px; margin-bottom: 12px; box-shadow: var(--sh); border: 1px solid var(--border); }
  .mt  { margin-top: 12px; }
</style>
