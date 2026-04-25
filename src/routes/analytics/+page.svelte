<script lang="ts">
  import { transactions, budgets, savings, recurring } from '$lib/stores';
  import { addMonths, yen, yenSign, ymLabel, shortYen } from '$lib/utils';
  import { detectPatterns } from '$lib/detect';

  // ── 12-month data ────────────────────────────────────────
  const months12 = $derived.by(() => {
    const now = new Date();
    return Array.from({ length: 12 }, (_, i) => {
      const d = new Date(now.getFullYear(), now.getMonth() - 11 + i, 1);
      const ym = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
      const mTx = $transactions.filter(t => t.date.startsWith(ym));
      const inc = mTx.filter(t => t.type === 'income') .reduce((s, t) => s + t.amount, 0);
      const exp = mTx.filter(t => t.type === 'expense').reduce((s, t) => s + t.amount, 0);
      return { ym, label: String(d.getMonth() + 1) + '月', inc, exp, bal: inc - exp };
    });
  });

  const cumData = $derived.by(() => {
    let cum = 0;
    return months12.map(d => { cum += d.bal; return { ...d, cum }; });
  });

  // ── Chart geometry ───────────────────────────────────────
  const VW = 360, VH_BAR = 180, VH_LINE = 150;
  const ML = 44, MR = 8, MT = 8, MB = 22;
  const UW = VW - ML - MR;
  const UH_BAR  = VH_BAR  - MT - MB;
  const UH_LINE = VH_LINE - MT - MB;

  // Bar chart
  const maxBar    = $derived(Math.max(...months12.flatMap(d => [d.inc, d.exp]), 1));
  const groupW    = $derived(UW / months12.length);
  const barW      = $derived((groupW * 0.72) / 2);
  const yTicksBar = $derived([0, 0.5, 1].map(p => ({
    y: MT + UH_BAR - UH_BAR * p,
    lbl: shortYen(maxBar * p),
  })));

  // Line chart
  const minCum   = $derived(Math.min(...cumData.map(d => d.cum), 0));
  const maxCum   = $derived(Math.max(...cumData.map(d => d.cum), 1));
  const rangeCum = $derived(maxCum - minCum || 1);

  const linePoints = $derived(cumData.map((d, i) => ({
    x: ML + (i / Math.max(cumData.length - 1, 1)) * UW,
    y: MT + UH_LINE - ((d.cum - minCum) / rangeCum) * UH_LINE,
    label: d.label,
    cum: d.cum,
  })));

  const linePath = $derived(linePoints.length >= 2
    ? 'M ' + linePoints.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' L ')
    : '');
  const areaPath = $derived(linePoints.length >= 2
    ? `M ${linePoints[0]!.x.toFixed(1)},${MT + UH_LINE} L ` +
      linePoints.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' L ') +
      ` L ${linePoints[linePoints.length - 1]!.x.toFixed(1)},${MT + UH_LINE} Z`
    : '');

  const yTicksLine = $derived([0, 0.5, 1].map(p => ({
    y: MT + UH_LINE - UH_LINE * p,
    lbl: shortYen(minCum + rangeCum * p),
  })));

  // ── Pattern detection ────────────────────────────────────
  const patterns = $derived(detectPatterns($transactions));

  // ── Export ───────────────────────────────────────────────
  function doExport(): void {
    const data = JSON.stringify({
      version:    1,
      exportedAt: new Date().toISOString(),
      tx:         $transactions,
      bud:        $budgets,
      sav:        $savings,
      rec:        $recurring,
    }, null, 2);
    const blob = new Blob([data], { type: 'application/json' });
    const a    = document.createElement('a');
    a.href     = URL.createObjectURL(blob);
    a.download = `kakeibo-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  // ── Import ───────────────────────────────────────────────
  let fileInput: HTMLInputElement | undefined = $state();
  let importError = $state('');

  function handleImport(e: Event): void {
    const input = e.target as HTMLInputElement;
    const file  = input.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = ev => {
      try {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const d: any = JSON.parse((ev.target as FileReader).result as string);
        if (!d.version) throw new Error('invalid');
        if (d.tx)  transactions.set(d.tx);
        if (d.bud) budgets.set(d.bud);
        if (d.sav) savings.set(d.sav);
        if (d.rec) recurring.set(d.rec);
        importError = '';
        alert('インポートが完了しました');
      } catch {
        importError = 'ファイルの形式が正しくありません';
      }
    };
    reader.readAsText(file);
    input.value = '';
  }
</script>

<!-- ── Bar chart ── -->
<p class="section-title">月次収支（過去12ヶ月）</p>
<div class="card chart-card">
  <!-- Legend -->
  <div class="legend">
    <span class="leg-item"><span class="leg-dot" style="background:var(--income)"></span>収入</span>
    <span class="leg-item"><span class="leg-dot" style="background:var(--expense)"></span>支出</span>
  </div>
  <svg viewBox="0 0 {VW} {VH_BAR}" width="100%" aria-label="月次収支棒グラフ">
    <!-- Y grid + labels -->
    {#each yTicksBar as t}
      <line x1={ML} y1={t.y} x2={VW - MR} y2={t.y} stroke="var(--border)" stroke-width="0.5"/>
      <text x={ML - 4} y={t.y + 4} text-anchor="end" font-size="8" fill="var(--muted)" font-family="sans-serif">{t.lbl}</text>
    {/each}
    <line x1={ML} y1={MT} x2={ML} y2={VH_BAR - MB} stroke="var(--border)" stroke-width="1"/>

    <!-- Bars -->
    {#each months12 as d, i}
      {@const gx = ML + i * groupW}
      {@const cx = gx + groupW / 2}
      {@const incH = (d.inc / maxBar) * UH_BAR}
      {@const expH = (d.exp / maxBar) * UH_BAR}
      <rect x={cx - barW - 1} y={MT + UH_BAR - incH} width={barW} height={incH} fill="var(--income)" rx="2"/>
      <rect x={cx + 1}        y={MT + UH_BAR - expH} width={barW} height={expH} fill="var(--expense)" rx="2"/>
      <text x={cx} y={VH_BAR - 6} text-anchor="middle" font-size="8" fill="var(--muted)" font-family="sans-serif">{d.label}</text>
    {/each}
  </svg>
</div>

<!-- ── Line chart ── -->
<p class="section-title">残高推移（累積）</p>
<div class="card chart-card">
  <svg viewBox="0 0 {VW} {VH_LINE}" width="100%" aria-label="残高推移折れ線グラフ">
    <!-- Y grid -->
    {#each yTicksLine as t}
      <line x1={ML} y1={t.y} x2={VW - MR} y2={t.y} stroke="var(--border)" stroke-width="0.5"/>
      <text x={ML - 4} y={t.y + 4} text-anchor="end" font-size="8" fill="var(--muted)" font-family="sans-serif">{t.lbl}</text>
    {/each}
    <!-- Zero line (if range crosses 0) -->
    {#if minCum < 0 && maxCum > 0}
      {@const zy = MT + UH_LINE - ((0 - minCum) / rangeCum) * UH_LINE}
      <line x1={ML} y1={zy} x2={VW - MR} y2={zy} stroke="var(--muted)" stroke-width="0.8" stroke-dasharray="4,3"/>
    {/if}
    <line x1={ML} y1={MT} x2={ML} y2={VH_LINE - MB} stroke="var(--border)" stroke-width="1"/>
    <!-- Area fill -->
    {#if areaPath}
      <path d={areaPath} fill="rgba(139,105,20,0.08)"/>
    {/if}
    <!-- Line -->
    {#if linePath}
      <path d={linePath} fill="none" stroke="var(--accent)" stroke-width="2.5" stroke-linejoin="round"/>
    {/if}
    <!-- Dots + month labels -->
    {#each linePoints as p}
      <circle cx={p.x} cy={p.y} r="3" fill="var(--accent)"/>
      <text x={p.x} y={VH_LINE - 6} text-anchor="middle" font-size="8" fill="var(--muted)" font-family="sans-serif">{p.label}</text>
    {/each}
  </svg>
</div>

<!-- ── Pattern detection ── -->
<p class="section-title">購入パターン</p>
<div class="card">
  {#if patterns.length === 0}
    <p class="empty-state">同じメモで3回以上記録すると購入パターンが表示されます</p>
  {:else}
    {#each patterns.slice(0, 12) as p}
      <div class="pattern-row">
        <div class="pat-info">
          <div class="pat-name">{p.description}</div>
          <div class="pat-meta">約{p.avgInterval}日ごと · {p.count}回 · 最終: {p.lastDate}</div>
        </div>
        <span class="pat-amt" class:income={p.type === 'income'} class:expense={p.type === 'expense'}>
          {yenSign(p.avgAmount, p.type)}
        </span>
      </div>
    {/each}
  {/if}
</div>

<!-- ── Data management ── -->
<p class="section-title">データ管理</p>
<div class="card">
  <div class="data-btns">
    <button type="button" class="btn-primary" onclick={doExport}>📤 エクスポート</button>
    <button type="button" class="btn-secondary" onclick={() => fileInput?.click()}>📥 インポート</button>
    <input bind:this={fileInput} type="file" accept=".json" style="display:none" onchange={handleImport} />
  </div>
  {#if importError}
    <p class="import-err">{importError}</p>
  {/if}
  <p class="data-note">
    データはブラウザの localStorage にオフライン保存されます。
    定期的にエクスポートしてバックアップを作成してください。
  </p>
</div>

<style>
  .card { background: var(--surf); border-radius: var(--r); padding: 16px; margin-bottom: 12px; box-shadow: var(--sh); border: 1px solid var(--border); }
  .chart-card { padding: 12px 8px 8px; }

  .legend { display: flex; gap: 14px; margin-bottom: 6px; padding: 0 6px; }
  .leg-item { display: flex; align-items: center; gap: 5px; font-size: 11px; color: var(--muted); }
  .leg-dot { width: 10px; height: 10px; border-radius: 2px; flex-shrink: 0; }

  .pattern-row { display: flex; align-items: center; gap: 10px; padding: 9px 0; border-bottom: 1px solid var(--border); }
  .pattern-row:last-child { border-bottom: none; }
  .pat-info { flex: 1; min-width: 0; }
  .pat-name { font-weight: 500; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .pat-meta { font-size: 11px; color: var(--muted); margin-top: 1px; }
  .pat-amt  { font-weight: 700; font-size: 13px; flex-shrink: 0; }
  .pat-amt.income  { color: var(--income); }
  .pat-amt.expense { color: var(--expense); }

  .data-btns { display: flex; gap: 10px; margin-bottom: 10px; }
  .data-btns .btn-primary, .data-btns .btn-secondary { flex: 1; text-align: center; }
  .import-err { font-size: 12px; color: var(--expense); margin-bottom: 8px; }
  .data-note  { font-size: 11px; color: var(--muted); line-height: 1.7; }
</style>
