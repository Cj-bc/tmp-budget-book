import type { Transaction, Pattern } from './types';

/**
 * 同じ description で 3 回以上登場する取引を検出し、
 * 平均購入間隔・平均金額を計算して返す。
 */
export function detectPatterns(txs: Transaction[]): Pattern[] {
  const groups: Record<string, Transaction[]> = {};
  for (const t of txs) {
    if (!t.description?.trim()) continue;
    const key = t.description.trim().toLowerCase();
    (groups[key] ??= []).push(t);
  }

  return Object.values(groups)
    .filter(v => v.length >= 3)
    .map(v => {
      const sorted = [...v].sort((a, b) => a.date.localeCompare(b.date));
      const dates  = sorted.map(t => new Date(t.date));
      const gaps: number[] = [];
      for (let i = 1; i < dates.length; i++) {
        gaps.push((dates[i]!.getTime() - dates[i - 1]!.getTime()) / 86_400_000);
      }
      return {
        description: sorted[0]!.description,
        count:       sorted.length,
        avgInterval: Math.round(gaps.reduce((a, b) => a + b, 0) / gaps.length),
        avgAmount:   v.reduce((s, t) => s + t.amount, 0) / v.length,
        type:        sorted[0]!.type,
        lastDate:    sorted[sorted.length - 1]!.date,
      };
    })
    .sort((a, b) => a.avgInterval - b.avgInterval);
}
