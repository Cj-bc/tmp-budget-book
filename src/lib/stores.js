import { writable } from 'svelte/store';
import { loadFromStorage, saveToStorage } from './storage.js';

/**
 * localStorage に自動永続化する writable store。
 * @template T
 * @param {string} key
 * @param {T} fallback
 */
function persisted(key, fallback) {
  const initial = loadFromStorage(key) ?? fallback;
  const store = writable(initial);
  store.subscribe(value => saveToStorage(key, value));
  return store;
}

export const transactions = persisted('tx',  []);
export const budgets      = persisted('bud', []);
export const savings      = persisted('sav', []);
export const recurring    = persisted('rec', []);
