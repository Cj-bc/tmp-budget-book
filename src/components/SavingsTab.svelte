<script>
  import { savings } from '../lib/stores.js';
  import { genId, nowYM, addMonths, monthsBetween, yen, ymLabel } from '../lib/utils.js';
  import ProgressBar from './ProgressBar.svelte';

  // ── Add goal form ────────────────────────────────────────
  let showAddGoal = false;
  let goalForm = defaultGoalForm();

  function defaultGoalForm() {
    return { name: '', monthlyTarget: '', startYM: nowYM(), targetYM: addMonths(nowYM(), 12) };
  }

  function addGoal() {
    if (!goalForm.name.trim() || !goalForm.monthlyTarget) return;
    savings.update(list => [...list, {
      id:            genId(),
      name:          goalForm.name.trim(),
      monthlyTarget: Math.abs(parseFloat(goalForm.monthlyTarget)),
      startYM:       goalForm.startYM,
      targetYM:      goalForm.targetYM,
      contributions: [],
    }]);
    goalForm    = defaultGoalForm();
    showAddGoal = false;
  }

  function deleteGoal(id) {
    if (!confirm('この積立目標を削除しますか？')) return;
    savings.update(list => list.filter(s => s.id !== id));
  }

  // ── Contribution form ────────────────────────────────────
  /** @type {string | null} 積立記録フォームを開いている goal の id */
  let contribGoalId = null;
  let contribForm   = { amount: '', date: nowYM().slice(0, 7) };  // keep as month string for simplicity

  function addContrib(goalId) {
    const amt = Math.abs(parseFloat(contribForm.amount));
    if (!amt) return;
    savings.update(list => list.map(s => s.id !== goalId ? s : {
      ...s,
      contributions: [...s.contributions, { id: genId(), date: contribForm.date, amount: amt }],
    }));
    contribForm   = { amount: '', date: nowYM() };
    contribGoalId = null;
  }

  function deleteContrib(goalId, contribId) {
    savings.update(list => list.map(s => s.id !== goalId ? s : {
      ...s,
      contributions: s.contributions.filter(c => c.id !== contribId),
    }));
  }

  // ── Goal stats ───────────────────────────────────────────
  function calcStats(goal) {
    const now        = nowYM();
    const totalM     = Math.max(1, monthsBetween(goal.startYM, goal.targetYM) + 1);
    const elapsedM   = Math.max(0, Math.min(totalM, monthsBetween(goal.startYM, now) + 1));
    const totalTarget = totalM * goal.monthlyTarget;
    const expected    = elapsedM * goal.monthlyTarget;
    const actual      = goal.contributions.reduce((s, c) => s + c.amount, 0);
    const diff        = actual - expected;
    const remM        = Math.max(0, monthsBetween(now, goal.targetYM));
    const pct         = totalTarget > 0 ? Math.min(100, (actual / totalTarget) * 100) : 0;
    return { totalM, elapsedM, totalTarget, expected, actual, diff, remM, pct };
  }
</script>

<div class="top-row">
  <p class="section-title" style="margin:0">積立目標</p>
  <button type="button" class="btn-primary btn-sm" on:click={() => showAddGoal = !showAddGoal}>+ 追加</button>
</div>

<!-- Add goal form -->
{#if showAddGoal}
  <div class="card add-form">
    <p class="form-ttl">新しい積立目標</p>

    <label class="field-label">名前 / 目的</label>
    <input class="field-input mb" type="text" placeholder="旅行、新PC、緊急資金…" bind:value={goalForm.name} />

    <div class="row2">
      <div>
        <label class="field-label">月次目標額</label>
        <input class="field-input" type="number" min="0" placeholder="10000" bind:value={goalForm.monthlyTarget} />
      </div>
      <div>
        <label class="field-label">開始月</label>
        <input class="field-input" type="month" bind:value={goalForm.startYM} />
      </div>
    </div>
    <div class="mt">
      <label class="field-label">目標達成月</label>
      <input class="field-input" type="month" bind:value={goalForm.targetYM} />
    </div>

    <div class="btn-row mt">
      <button type="button" class="btn-primary" on:click={addGoal}>追加する</button>
      <button type="button" class="btn-secondary" on:click={() => { showAddGoal = false; goalForm = defaultGoalForm(); }}>キャンセル</button>
    </div>
  </div>
{/if}

{#if $savings.length === 0 && !showAddGoal}
  <div class="card empty-state">
    <div class="empty-icon">🎯</div>
    <p>積立目標を設定しましょう</p>
  </div>
{/if}

<!-- Goal cards -->
{#each $savings as goal (goal.id)}
  {@const s = calcStats(goal)}
  <div class="card goal-card">
    <div class="goal-head">
      <div>
        <div class="goal-name">{goal.name}</div>
        <div class="goal-sub">{ymLabel(goal.startYM)} 〜 {ymLabel(goal.targetYM)} · あと{s.remM}ヶ月</div>
      </div>
      <button type="button" class="del-btn" on:click={() => deleteGoal(goal.id)}>×</button>
    </div>

    <!-- Key metrics grid -->
    <div class="metrics">
      {#each [
        { l: '月次目標', v: yen(goal.monthlyTarget), cls: 'accent' },
        { l: '総目標',   v: yen(s.totalTarget),       cls: '' },
        { l: '積立済',   v: yen(s.actual),             cls: 'income' },
        { l: '計画差額', v: (s.diff >= 0 ? '+' : '') + yen(s.diff), cls: s.diff >= 0 ? 'income' : 'expense' },
      ] as m}
        <div class="metric">
          <div class="metric-lbl">{m.l}</div>
          <div class="metric-val {m.cls}">{m.v}</div>
        </div>
      {/each}
    </div>

    <ProgressBar value={s.actual} max={s.totalTarget} color="var(--income)" />
    <div class="prog-labels">
      <span>{s.pct.toFixed(1)}% 達成</span>
      {#if s.totalTarget > s.actual}
        <span>残 {yen(s.totalTarget - s.actual)}</span>
      {/if}
    </div>

    <!-- Contribution history -->
    {#if goal.contributions.length > 0}
      <div class="contrib-list">
        {#each [...goal.contributions].sort((a, b) => b.date.localeCompare(a.date)) as c}
          <div class="contrib-row">
            <span class="contrib-date">{c.date}</span>
            <span class="contrib-amt">+{yen(c.amount)}</span>
            <button type="button" class="del-btn sm" on:click={() => deleteContrib(goal.id, c.id)}>×</button>
          </div>
        {/each}
      </div>
    {/if}

    <!-- Add contribution form -->
    {#if contribGoalId === goal.id}
      <div class="contrib-form">
        <input class="field-input" type="number" min="0" placeholder="金額" bind:value={contribForm.amount} />
        <input class="field-input" type="date"   bind:value={contribForm.date} />
        <div class="btn-row">
          <button type="button" class="btn-primary btn-sm" on:click={() => addContrib(goal.id)}>記録</button>
          <button type="button" class="btn-secondary btn-sm" on:click={() => contribGoalId = null}>取消</button>
        </div>
      </div>
    {:else}
      <button type="button" class="btn-secondary btn-full mt" on:click={() => { contribGoalId = goal.id; contribForm = { amount: '', date: new Date().toISOString().slice(0,10) }; }}>
        + 積立を記録する
      </button>
    {/if}
  </div>
{/each}

<style>
  .top-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }

  .card { background: var(--surf); border-radius: var(--r); padding: 16px; margin-bottom: 12px; box-shadow: var(--sh); border: 1px solid var(--border); }
  .add-form .row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 10px; }
  .form-ttl { font-weight: 600; font-size: 14px; margin-bottom: 12px; }

  .goal-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
  .goal-name { font-weight: 700; font-size: 15px; }
  .goal-sub  { font-size: 11px; color: var(--muted); margin-top: 2px; }

  .metrics { display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 6px; margin-bottom: 10px; }
  .metric  { background: var(--bg); border-radius: 8px; padding: 8px 6px; text-align: center; }
  .metric-lbl { font-size: 10px; color: var(--muted); margin-bottom: 3px; }
  .metric-val { font-weight: 700; font-size: 12px; }
  .metric-val.accent  { color: var(--accent); }
  .metric-val.income  { color: var(--income); }
  .metric-val.expense { color: var(--expense); }

  .prog-labels { display: flex; justify-content: space-between; font-size: 11px; color: var(--muted); margin-top: 3px; }

  .contrib-list { max-height: 120px; overflow-y: auto; margin-top: 10px; border-top: 1px solid var(--border); }
  .contrib-row  { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid var(--border); font-size: 12px; }
  .contrib-row:last-child { border-bottom: none; }
  .contrib-date { color: var(--muted); }
  .contrib-amt  { color: var(--income); font-weight: 600; flex: 1; }

  .contrib-form { margin-top: 10px; background: var(--ac-bg); border-radius: 8px; padding: 10px; display: flex; flex-direction: column; gap: 8px; }

  .del-btn { background: none; border: none; color: var(--border); cursor: pointer; font-size: 18px; padding: 0 2px; transition: color 0.15s; line-height: 1; }
  .del-btn:hover { color: var(--muted); }
  .del-btn.sm { font-size: 14px; }

  .empty-icon { font-size: 32px; margin-bottom: 8px; }
  .btn-row { display: flex; gap: 8px; }
  .mt { margin-top: 10px; }
  .mb { margin-bottom: 10px; }
</style>
