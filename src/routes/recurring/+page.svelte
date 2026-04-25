<script lang="ts">
  import { recurring } from '$lib/stores';
  import { EXPENSE_CATS, INCOME_CATS, CAT_COLOR } from '$lib/constants';
  import { genId, yen, yenSign } from '$lib/utils';
  import type { TransactionType } from '$lib/types';

  interface RecurringForm {
    description: string;
    type: TransactionType;
    category: string;
    amount: string;
    dayOfMonth: number;
  }

  let showAdd: boolean       = $state(false);
  let form:    RecurringForm = $state(defaultForm());

  function defaultForm(): RecurringForm {
    return { description: '', type: 'expense', category: EXPENSE_CATS[0]!, amount: '', dayOfMonth: 1 };
  }

  const formCats = $derived(form.type === 'expense' ? EXPENSE_CATS : INCOME_CATS);

  function setFormType(t: TransactionType): void {
    form.type     = t;
    form.category = (t === 'expense' ? EXPENSE_CATS : INCOME_CATS)[0]!;
  }

  function addItem(): void {
    if (!form.description.trim() || !form.amount) return;
    recurring.update(list => [...list, {
      id:          genId(),
      description: form.description.trim(),
      type:        form.type,
      category:    form.category,
      amount:      Math.abs(parseFloat(form.amount)),
      dayOfMonth:  Math.max(1, Math.min(28, parseInt(String(form.dayOfMonth)) || 1)),
      active:      true,
      lastApplied: null,
    }]);
    form    = defaultForm();
    showAdd = false;
  }

  function toggle(id: string): void {
    recurring.update(list => list.map(r => r.id === id ? { ...r, active: !r.active } : r));
  }

  function del(id: string): void {
    if (!confirm('この固定費を削除しますか？')) return;
    recurring.update(list => list.filter(r => r.id !== id));
  }

  // ── Derived totals ───────────────────────────────────────
  const fixedIncome  = $derived($recurring.filter(r => r.type === 'income'  && r.active).reduce((s, r) => s + r.amount, 0));
  const fixedExpense = $derived($recurring.filter(r => r.type === 'expense' && r.active).reduce((s, r) => s + r.amount, 0));

  const incomeItems  = $derived($recurring.filter(r => r.type === 'income'));
  const expenseItems = $derived($recurring.filter(r => r.type === 'expense'));
</script>

<div class="top-row">
  <p class="section-title" style="margin:0">固定収支</p>
  <button type="button" class="btn-primary btn-sm" onclick={() => showAdd = !showAdd}>+ 追加</button>
</div>

<!-- Summary -->
{#if fixedIncome > 0 || fixedExpense > 0}
  <div class="card summary-row">
    <div class="summary-cell">
      <div class="s-lbl">月次固定収入</div>
      <div class="s-val income">+{yen(fixedIncome)}</div>
    </div>
    <div class="divider"></div>
    <div class="summary-cell">
      <div class="s-lbl">月次固定支出</div>
      <div class="s-val expense">-{yen(fixedExpense)}</div>
    </div>
    <div class="divider"></div>
    <div class="summary-cell">
      <div class="s-lbl">差引</div>
      <div class="s-val" class:income={fixedIncome >= fixedExpense} class:expense={fixedIncome < fixedExpense}>
        {yen(fixedIncome - fixedExpense)}
      </div>
    </div>
  </div>
{/if}

<!-- Add form -->
{#if showAdd}
  <div class="card add-form">
    <p class="form-ttl">固定収支を追加</p>

    <div class="type-row">
      <button type="button" class="type-btn" class:act-exp={form.type === 'expense'} onclick={() => setFormType('expense')}>支出</button>
      <button type="button" class="type-btn" class:act-inc={form.type === 'income'}  onclick={() => setFormType('income')}>収入</button>
    </div>

    <label class="field-label">名称</label>
    <input class="field-input mb" type="text" placeholder="家賃、Netflix、給与…" bind:value={form.description} />

    <div class="row3">
      <div>
        <label class="field-label">金額</label>
        <input class="field-input" type="number" min="0" bind:value={form.amount} />
      </div>
      <div>
        <label class="field-label">カテゴリ</label>
        <select class="field-input" bind:value={form.category}>
          {#each formCats as c}<option>{c}</option>{/each}
        </select>
      </div>
      <div>
        <label class="field-label">毎月何日</label>
        <input class="field-input" type="number" min="1" max="28" bind:value={form.dayOfMonth} />
      </div>
    </div>

    <div class="btn-row mt">
      <button type="button" class="btn-primary" onclick={addItem}>追加する</button>
      <button type="button" class="btn-secondary" onclick={() => { showAdd = false; form = defaultForm(); }}>キャンセル</button>
    </div>
  </div>
{/if}

{#if $recurring.length === 0 && !showAdd}
  <div class="card empty-state">
    <div class="empty-icon">🔄</div>
    <p>固定収支を登録すると毎月自動記録されます</p>
  </div>
{/if}

<!-- Expense items -->
{#if expenseItems.length > 0}
  <p class="section-title">固定支出</p>
  <div class="card">
    {#each expenseItems as r, i}
      <div class="item-row" class:bordered={i < expenseItems.length - 1} class:inactive={!r.active}>
        <div class="cat-dot" style="background:{CAT_COLOR[r.category] ?? '#B0B0B0'}"></div>
        <div class="item-info">
          <div class="item-name">{r.description}</div>
          <div class="item-meta">{r.category} · 毎月{r.dayOfMonth}日{r.lastApplied ? ' · 最終: ' + r.lastApplied : ''}</div>
        </div>
        <span class="item-amt expense">{yenSign(r.amount, r.type)}</span>
        <button type="button" class="toggle-btn" class:on={r.active} onclick={() => toggle(r.id)}>
          {r.active ? 'ON' : 'OFF'}
        </button>
        <button type="button" class="del-btn" onclick={() => del(r.id)}>×</button>
      </div>
    {/each}
  </div>
{/if}

<!-- Income items -->
{#if incomeItems.length > 0}
  <p class="section-title">固定収入</p>
  <div class="card">
    {#each incomeItems as r, i}
      <div class="item-row" class:bordered={i < incomeItems.length - 1} class:inactive={!r.active}>
        <div class="cat-dot" style="background:{CAT_COLOR[r.category] ?? '#B0B0B0'}"></div>
        <div class="item-info">
          <div class="item-name">{r.description}</div>
          <div class="item-meta">{r.category} · 毎月{r.dayOfMonth}日{r.lastApplied ? ' · 最終: ' + r.lastApplied : ''}</div>
        </div>
        <span class="item-amt income">{yenSign(r.amount, r.type)}</span>
        <button type="button" class="toggle-btn" class:on={r.active} onclick={() => toggle(r.id)}>
          {r.active ? 'ON' : 'OFF'}
        </button>
        <button type="button" class="del-btn" onclick={() => del(r.id)}>×</button>
      </div>
    {/each}
  </div>
{/if}

<style>
  .top-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }

  .card { background: var(--surf); border-radius: var(--r); padding: 16px; margin-bottom: 12px; box-shadow: var(--sh); border: 1px solid var(--border); }

  .summary-row { display: flex; align-items: center; padding: 12px 16px; }
  .summary-cell { flex: 1; text-align: center; }
  .s-lbl  { font-size: 11px; color: var(--muted); margin-bottom: 2px; }
  .s-val  { font-weight: 700; font-size: 13px; }
  .s-val.income  { color: var(--income); }
  .s-val.expense { color: var(--expense); }
  .divider { width: 1px; height: 36px; background: var(--border); }

  .add-form .type-row { display: flex; gap: 6px; margin-bottom: 12px; }
  .type-btn { flex: 1; padding: 8px; border: none; border-radius: 8px; cursor: pointer; background: var(--border); color: var(--muted); font-weight: 500; font-family: inherit; transition: background 0.2s, color 0.2s; }
  .type-btn.act-exp { background: var(--expense); color: #FFF; }
  .type-btn.act-inc { background: var(--income);  color: #FFF; }

  .form-ttl { font-weight: 600; font-size: 14px; margin-bottom: 12px; }
  .row3 { display: grid; grid-template-columns: 1fr 1fr 64px; gap: 8px; }

  .item-row { display: flex; align-items: center; gap: 10px; padding: 10px 0; transition: opacity 0.2s; }
  .item-row.bordered  { border-bottom: 1px solid var(--border); }
  .item-row.inactive  { opacity: 0.45; }
  .cat-dot { width: 8px; height: 8px; border-radius: 2px; flex-shrink: 0; }
  .item-info { flex: 1; min-width: 0; }
  .item-name { font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .item-meta { font-size: 11px; color: var(--muted); margin-top: 1px; }
  .item-amt  { font-weight: 700; font-size: 14px; flex-shrink: 0; }
  .item-amt.income  { color: var(--income); }
  .item-amt.expense { color: var(--expense); }

  .toggle-btn { background: var(--border); border: none; border-radius: 6px; padding: 4px 8px; cursor: pointer; font-size: 11px; color: var(--muted); font-family: inherit; font-weight: 600; transition: all 0.15s; flex-shrink: 0; }
  .toggle-btn.on { background: var(--ac-bg); color: var(--accent); }

  .del-btn { background: none; border: none; color: var(--border); cursor: pointer; font-size: 18px; padding: 0 2px; flex-shrink: 0; transition: color 0.15s; }
  .del-btn:hover { color: var(--muted); }

  .empty-icon { font-size: 32px; margin-bottom: 8px; }
  .btn-row { display: flex; gap: 8px; }
  .mt { margin-top: 10px; }
  .mb { margin-bottom: 10px; }
</style>
