<script lang="ts">
  import { onMount } from 'svelte';
  import { transactions, recurring } from './lib/stores';
  import { genId, nowYM, ymLabel } from './lib/utils';
  import HomeTab      from './components/HomeTab.svelte';
  import MonthlyTab   from './components/MonthlyTab.svelte';
  import SavingsTab   from './components/SavingsTab.svelte';
  import RecurringTab from './components/RecurringTab.svelte';
  import AnalyticsTab from './components/AnalyticsTab.svelte';

  let tab = $state('home');

  const TABS = [
    { id: 'home',      icon: '🏠', label: 'ホーム'  },
    { id: 'monthly',   icon: '📊', label: '月次'    },
    { id: 'savings',   icon: '🎯', label: '積立'    },
    { id: 'recurring', icon: '🔄', label: '固定費'  },
    { id: 'analytics', icon: '📈', label: '分析'    },
  ];

  /** 当月の固定費が未適用であれば自動でトランザクションを追加する */
  onMount(() => {
    const curYM  = nowYM();
    const todayD = new Date().getDate();

    recurring.update(list => list.map(r => {
      if (!r.active || r.lastApplied === curYM) return r;
      if (todayD >= r.dayOfMonth) {
        const day = String(r.dayOfMonth).padStart(2, '0');
        transactions.update(txs => [{
          id:          genId(),
          date:        `${curYM}-${day}`,
          amount:      r.amount,
          type:        r.type,
          category:    r.category,
          description: r.description + '（自動）',
          isRecurring: true,
        }, ...txs]);
        return { ...r, lastApplied: curYM };
      }
      return r;
    }));
  });
</script>

<!-- ── Header ── -->
<header>
  <span class="logo">家計簿</span>
  <span class="ym">{ymLabel(nowYM())}</span>
</header>

<!-- ── Page content ── -->
<main>
  {#if tab === 'home'}
    <HomeTab />
  {:else if tab === 'monthly'}
    <MonthlyTab />
  {:else if tab === 'savings'}
    <SavingsTab />
  {:else if tab === 'recurring'}
    <RecurringTab />
  {:else if tab === 'analytics'}
    <AnalyticsTab />
  {/if}
</main>

<!-- ── Tab bar ── -->
<nav>
  {#each TABS as t}
    <button
      type="button"
      class="tab-btn"
      class:active={tab === t.id}
      onclick={() => tab = t.id}
      aria-label={t.label}
      aria-current={tab === t.id ? 'page' : undefined}
    >
      <span class="tab-icon">{t.icon}</span>
      <span class="tab-label">{t.label}</span>
    </button>
  {/each}
</nav>

<style>
  header {
    position: sticky;
    top: 0;
    z-index: 100;
    background: var(--surf);
    border-bottom: 1px solid var(--border);
    padding: 12px 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .logo {
    font-family: 'Noto Serif JP', serif;
    font-size: 18px;
    font-weight: 600;
    color: var(--accent);
    letter-spacing: 0.06em;
  }
  .ym { font-size: 12px; color: var(--muted); }

  main {
    max-width: 600px;
    margin: 0 auto;
    padding: 16px;
  }

  nav {
    position: fixed;
    bottom: 0; left: 0; right: 0;
    background: var(--surf);
    border-top: 1px solid var(--border);
    display: flex;
    z-index: 100;
    /* Safe area for iOS home indicator */
    padding-bottom: env(safe-area-inset-bottom, 0);
  }

  .tab-btn {
    flex: 1;
    border: none;
    background: none;
    padding: 10px 4px;
    cursor: pointer;
    color: var(--muted);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    border-top: 2px solid transparent;
    transition: color 0.2s, border-color 0.2s;
    font-family: inherit;
  }
  .tab-btn.active {
    color: var(--accent);
    border-top-color: var(--accent);
    font-weight: 600;
  }
  .tab-icon  { font-size: 16px; }
  .tab-label { font-size: 10px; }
</style>
