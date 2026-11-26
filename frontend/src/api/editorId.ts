const STORAGE_KEY = 'scheduler.editorId';

let cachedEditorId: string | null = null;

/** RFC 4122 v4 UUID; works in non-secure contexts too (where crypto.randomUUID is unavailable). */
export function randomId(): string {
  const cryptoApi: Crypto | undefined = globalThis.crypto;
  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (cryptoApi && typeof cryptoApi.getRandomValues === 'function') {
    cryptoApi.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Identifies this browser tab as an editor (sent as X-Editor-Id). Kept in sessionStorage, which is per tab, so a
 * reload keeps the id while two tabs of the same user count as two editors for the edit lock.
 */
export function getEditorId(): string {
  if (cachedEditorId) {
    return cachedEditorId;
  }
  let id: string | null = null;
  try {
    id = sessionStorage.getItem(STORAGE_KEY);
  } catch {
    // Storage blocked (privacy mode): a per-page-load id still works.
  }
  if (!id) {
    id = randomId();
    try {
      sessionStorage.setItem(STORAGE_KEY, id);
    } catch {
      // ignore
    }
  }
  cachedEditorId = id;
  return id;
}

/** Test helper: forget the cached id. */
export function resetEditorIdForTests(): void {
  cachedEditorId = null;
}
