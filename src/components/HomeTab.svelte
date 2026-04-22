<script lang="ts">
  import { transactions } from '../lib/stores';
  import { EXPENSE_CATS, INCOME_CATS, CAT_COLOR } from '../lib/constants';
  import { genId, todayStr, nowYM, yen, yenSign, ymLabel } from '../lib/utils';
  import type { TransactionType } from '../lib/types';

  const ym = nowYM();

  // ── Form state ───────────────────────────────────────────
  let amount:      string          = $state('');
  let type:        TransactionType = $state('expense');
  let category:    string          = $state(EXPENSE_CATS[0]!);
  let description: string          = $state('');
  let date:        string          = $state(todayStr());
  let suggestions: string[]        = $state([]);
  let showSugg:    boolean         = $state(false);

  const cats = $derived(type === 'expense' ? EXPENSE_CATS : INCOME_CATS);

  /** 収支タイプを切り替え（カテゴリも先頭にリセット） */
  function setType(t: TransactionType): void {
    type     = t;
    category = (t === 'expense' ? EXPENSE_CATS : INCOME_CATS)[0]!;
  }

  // ── Derived ──────────────────────────────────────────────
  const monthTx      = $derived($transactions.filter(t => t.date.startsWith(ym)));
  const totalIncome  = $derived(monthTx.filter(t => t.type === 'income') .reduce((s, t) => s + t.amount, 0));
  const totalExpense = $derived(monthTx.filter(t => t.type === 'expense').reduce((s, t) => s + t.amount, 0));
  const balance      = $derived(totalIncome - totalExpense);

  const recent = $derived(
    [...$transactions]
      .sort((a, b) => b.date.localeCompare(a.date))
      .slice(0, 20)
  );

  // ── Autocomplete ─────────────────────────────────────────
  function onDescInput(e: Event): void {
    description = (e.target as HTMLInputElement).value;
    if (description.length > 0) {
      const all = [...new Set($transactions.map(t => t.description).filter(Boolean))];
      suggestions = all
        .filter(d => d.toLowerCase().includes(description.toLowerCase()))
        .slice(0, 5);
      showSugg = suggestions.length > 0;
    } else {
      showSugg = false;
    }
  }

  function pickSuggestion(s: string): void {
    description = s;
    showSugg    = false;
  }

  // ── Submit ───────────────────────────────────────────────
  function submit(): void {
    const amt = parseFloat(amount);
    if (!amt || isNaN(amt)) return;
    transactions.update(list => [
      { id: genId(), date, amount: Math.abs(amt), type, category, description: description.trim() },
      ...list,
    ]);
    amount      = '';
    description = '';
    date        = todayStr();
  }

  function del(id: string): void {
    if (!confirm('この記録を削除しますか？')) return;
    transactions.update(list => list.filter(t => t.id !== id));
  }
</script>

<!-- Month summary card -->
<div class="month-card">
  <div class="month-label">{ymLabel(ym)} の収支</div>
  <div class="balance">{balance >= 0 ? '+' : ''}{yen(balance)}</div>
  <div class="sub-row">
    <span>収入 <em class="inc">+{yen(totalIncome)}</em></span>
    <span>支出 <em class="exp">-{yen(totalExpense)}</em></span>
  </div>
</div>

<!-- Quick input card -->
<div class="card">
  <p class="card-ttl">クイック入力</p>

  <div class="type-row">
    <button type="button" class="type-btn" class:act-exp={type === 'expense'} onclick={() => setType('expense')}>支出</button>
    <button type="button" class="type-btn" class:act-inc={type === 'income'}  onclick={() => setType('income')}>収入</button>
  </div>

  <label class="field-label" for="amount">金額</label>
  <input id="amount" class="field-input big-num" type="number" min="0" placeholder="0"
    bind:value={amount}
    onkeydown={e => e.key === 'Enter' && submit()}
  />

  <div class="row2">
    <div>
      <label class="field-label" for="category">カテゴリ</label>
      <select id="category" class="field-input" bind:value={category}>
        {#each cats as c}<option value={c}>{c}</option>{/each}
      </select>
    </div>
    <div>
      <label class="field-label" for="date">日付</label>
      <input id="date" class="field-input" type="date" bind:value={date} />
    </div>
  </div>

  <div class="desc-wrap">
    <label class="field-label" for="desc">メモ（任意）</label>
    <input id="desc" class="field-input" type="text"
      placeholder="購入したもの・店名など"
      value={description}
      oninput={onDescInput}
      onblur={() => setTimeout(() => (showSugg = false), 150)}
      onfocus={() => { if (description.length > 0) showSugg = suggestions.length > 0; }}
    />
    {#if showSugg}
      <ul class="sugg-list" role="listbox">
        {#each suggestions as s}
          <li role="option" aria-selected="false">
            <button type="button" class="sugg-item" onmousedown={() => pickSuggestion(s)}>{s}</button>
          </li>
        {/each}
      </ul>
    {/if}
  </div>

  <button type="button" class="btn-primary btn-full" onclick={submit}>記録する</button>
</div>

<!-- Recent transactions -->
<p class="section-title">最近の記録</p>
<div class="card">
  {#if recent.length === 0}
    <p class="empty-state">まだ記録がありません</p>
  {:else}
    {#each recent as t, i}
      <div class="tx-row" class:bordered={i < recent.length - 1}>
        <div class="cat-dot" style="background:{CAT_COLOR[t.category] ?? '#B0B0B0'}"></div>
        <div class="tx-info">
          <div class="tx-name">{t.description || t.category}</div>
          <div class="tx-meta">{t.date} · {t.category}{t.isRecurring ? ' · 🔄' : ''}</div>
        </div>
        <span class="tx-amt" class:inc={t.type === 'income'} class:exp={t.type === 'expense'}>
          {yenSign(t.amount, t.type)}
        </span>
        <button type="button" class="del-btn" onclick={() => del(t.id)} title="削除">×</button>
      </div>
    {/each}
  {/if}
</div>

<style>
  .month-card {
    background: linear-gradient(135deg, var(--accent), #5A4010);
    border-radius: var(--r);
    padding: 20px;
    margin-bottom: 12px;
    color: #FFF;
  }
  .month-label { font-size: 12px; opacity: 0.7; margin-bottom: 4px; }
  .balance { font-family: 'Noto Serif JP', serif; font-size: 30px; font-weight: 700; letter-spacing: -0.01em; }
  .sub-row { display: flex; gap: 20px; margin-top: 10px; font-size: 13px; }
  .inc { color: #A0E8B8; font-style: normal; }
  .exp { color: #F8A8A8; font-style: normal; }

  .card { background: var(--surf); border-radius: var(--r); padding: 16px; margin-bottom: 12px; box-shadow: var(--sh); border: 1px solid var(--border); }
  .card-ttl { font-size: 12px; font-weight: 600; color: var(--muted); letter-spacing: 0.06em; margin-bottom: 12px; }

  .type-row { display: flex; gap: 6px; margin-bottom: 12px; }
  .type-btn { flex: 1; padding: 8px; border: none; border-radius: 8px; cursor: pointer; background: var(--border); color: var(--muted); font-weight: 500; font-family: inherit; transition: background 0.2s, color 0.2s; }
  .type-btn.act-exp { background: var(--expense); color: #FFF; }
  .type-btn.act-inc { background: var(--income);  color: #FFF; }

  .big-num { font-size: 22px; font-weight: 700; margin-bottom: 10px; }
  .row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 10px; }

  .desc-wrap { position: relative; margin-bottom: 12px; }
  .sugg-list { position: absolute; top: 100%; left: 0; right: 0; background: var(--surf); border: 1px solid var(--border); border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.12); z-index: 20; list-style: none; overflow: hidden; margin: 0; padding: 0; }
  .sugg-item { display: block; width: 100%; padding: 9px 12px; cursor: pointer; font-size: 13px; background: none; border: none; border-bottom: 1px solid var(--border); text-align: left; font-family: inherit; color: var(--text); }
  .sugg-item:last-child { border-bottom: none; }
  .sugg-item:hover { background: var(--ac-bg); }

  .tx-row { display: flex; align-items: center; gap: 10px; padding: 9px 0; }
  .tx-row.bordered { border-bottom: 1px solid var(--border); }
  .cat-dot { width: 8px; height: 8px; border-radius: 3px; flex-shrink: 0; }
  .tx-info { flex: 1; min-width: 0; }
  .tx-name { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .tx-meta { font-size: 11px; color: var(--muted); margin-top: 1px; }
  .tx-amt { font-weight: 700; font-size: 14px; flex-shrink: 0; }
  .tx-amt.inc { color: var(--income); }
  .tx-amt.exp { color: var(--expense); }
  .del-btn { background: none; border: none; color: var(--border); cursor: pointer; font-size: 18px; padding: 0 3px; flex-shrink: 0; transition: color 0.15s; line-height: 1; }
  .del-btn:hover { color: var(--muted); }
</style>
