import type { TransactionType } from './types';

/** ランダムな短い ID を生成 */
export const genId = (): string =>
  Date.now().toString(36) + Math.random().toString(36).slice(2, 5);

/** 今日の日付文字列 YYYY-MM-DD */
export const todayStr = (): string => new Date().toISOString().slice(0, 10);

/** 今月の YYYY-MM 文字列 */
export const nowYM = (): string => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
};

/** YYYY-MM に n ヶ月を加算 */
export const addMonths = (ym: string, n: number): string => {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(y!, m! - 1 + n, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
};

/** 月差 b - a */
export const monthsBetween = (a: string, b: string): number => {
  const [ay, am] = a.split('-').map(Number);
  const [by, bm] = b.split('-').map(Number);
  return (by! - ay!) * 12 + (bm! - am!);
};

/** ¥1,234 形式 */
export const yen = (n: number): string =>
  `\u00a5${Math.round(Math.abs(n)).toLocaleString('ja-JP')}`;

/** +¥ / -¥ 形式 */
export const yenSign = (n: number, type: TransactionType): string =>
  (type === 'income' ? '+' : '-') + yen(n);

/** YYYY年M月 */
export const ymLabel = (ym: string): string => {
  const [y, m] = ym.split('-');
  return `${y}\u5e74${parseInt(m!)}\u6708`;
};

/** チャート用の短い金額ラベル */
export const shortYen = (n: number): string =>
  Math.abs(n) >= 10_000
    ? (n / 10_000).toFixed(n % 10_000 === 0 ? 0 : 1) + '\u4e07'
    : String(Math.round(n));
