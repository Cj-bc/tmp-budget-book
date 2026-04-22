import { writable } from 'svelte/store';
import type { Writable } from 'svelte/store';
import { loadFromStorage, saveToStorage } from './storage';
import type { Transaction, Budget, Saving, RecurringItem } from './types';

/**
 * localStorage に自動永続化する writable store。
 */
function persisted<T>(key: string, fallback: T): Writable<T> {
  const initial = loadFromStorage<T>(key) ?? fallback;
  const store = writable(initial);
  store.subscribe(value => saveToStorage(key, value));
  return store;
}

export const transactions: Writable<Transaction[]>   = persisted('tx',  []);
export const budgets:      Writable<Budget[]>         = persisted('bud', []);
export const savings:      Writable<Saving[]>         = persisted('sav', []);
export const recurring:    Writable<RecurringItem[]>  = persisted('rec', []);
