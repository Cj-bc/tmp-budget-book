const PREFIX = 'kakeibo:v1:';

export function loadFromStorage(key) {
  try {
    const raw = localStorage.getItem(PREFIX + key);
    return raw != null ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveToStorage(key, value) {
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify(value));
  } catch (e) {
    console.warn('[kakeibo] storage write failed:', e);
  }
}
