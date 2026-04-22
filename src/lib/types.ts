export type TransactionType = 'income' | 'expense';

export interface Transaction {
  id: string;
  date: string;          // YYYY-MM-DD
  amount: number;
  type: TransactionType;
  category: string;
  description: string;
  isRecurring?: boolean;
}

export interface Budget {
  id: string;
  category: string;
  monthlyAmount: number;
}

export interface Contribution {
  id: string;
  date: string;
  amount: number;
}

export interface Saving {
  id: string;
  name: string;
  monthlyTarget: number;
  startYM: string;       // YYYY-MM
  targetYM: string;      // YYYY-MM
  contributions: Contribution[];
}

export interface RecurringItem {
  id: string;
  description: string;
  amount: number;
  type: TransactionType;
  category: string;
  dayOfMonth: number;
  active: boolean;
  lastApplied: string | null;
}

export interface Pattern {
  description: string;
  count: number;
  avgInterval: number;
  avgAmount: number;
  type: TransactionType;
  lastDate: string;
}

export interface PeriodOpt {
  v: string;
  l: string;
}
