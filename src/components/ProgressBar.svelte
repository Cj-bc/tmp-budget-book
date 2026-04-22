<script lang="ts">
  export let value: number = 0;
  export let max:   number = 100;
  export let color: string = 'var(--accent)';

  $: pct  = max > 0 ? Math.min(100, (value / max) * 100) : 0;
  $: fill = max > 0 && value > max ? 'var(--expense)' : color;
</script>

<div class="track" role="progressbar" aria-valuenow={Math.round(pct)} aria-valuemin="0" aria-valuemax="100">
  <div class="fill" style="width:{pct}%; background:{fill};"></div>
</div>

<style>
  .track {
    height: 5px;
    background: var(--border);
    border-radius: 3px;
    overflow: hidden;
    margin-top: 6px;
  }
  .fill {
    height: 100%;
    border-radius: 3px;
    transition: width 0.4s ease;
  }
</style>
