const PREFIX = 'kakeibo:v1:';

export function loadFromStorage<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(PREFIX + key);
    return raw != null ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

export function saveToStorage<T>(key: string, value: T): void {
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify(value));
  } catch (e) {
    console.warn('[kakeibo] storage write failed:', e);
  }
}
