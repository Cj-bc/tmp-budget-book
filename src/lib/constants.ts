import type { PeriodOpt } from './types';

export const EXPENSE_CATS: string[] = [
  '食費', '交通費', '住居費', '光熱費', '医療費',
  '娯楽費', '衣服費', '日用品', '教育費', '通信費', 'その他',
];

export const INCOME_CATS: string[] = ['給与', '副収入', '賞与', 'その他'];

export const CAT_COLOR: Record<string, string> = {
  食費: '#E85D4A', 交通費: '#E8914A', 住居費: '#D4A843',
  光熱費: '#7BC67A', 医療費: '#5DB8E8', 娯楽費: '#9B6BE8',
  衣服費: '#E86BAF', 日用品: '#6BB5E8', 教育費: '#4A9E8B',
  通信費: '#8B8B8B', その他: '#B0B0B0',
  給与: '#2D6A4F', 副収入: '#4A9E8B', 賞与: '#D4A843',
};

export const PERIOD_OPTS: PeriodOpt[] = [
  { v: '1',  l: '1ヶ月' },
  { v: '3',  l: '3ヶ月' },
  { v: '6',  l: '6ヶ月' },
  { v: '12', l: '1年'   },
];
