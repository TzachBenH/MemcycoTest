import type { EditLock } from '../types/api';
import { API_BASE_URL, EDITOR_ID_HEADER, EDIT_LOCK_TOKEN_HEADER, apiClient } from './client';
import { getEditorId } from './editorId';

const lockPath = (schedulingId: string) => `/schedulings/${schedulingId}/edit-lock`;

/** 409 EDIT_LOCKED (with lockedBy/since) when another editor holds it. */
export async function acquireEditLock(schedulingId: string): Promise<EditLock> {
  const { data } = await apiClient.post<EditLock>(lockPath(schedulingId));
  return data;
}

/** 409 EDIT_LOCK_LOST when the lock expired or was taken over. */
export async function renewEditLock(schedulingId: string, token: string): Promise<EditLock> {
  const { data } = await apiClient.put<EditLock>(lockPath(schedulingId), undefined, {
    headers: { [EDIT_LOCK_TOKEN_HEADER]: token },
  });
  return data;
}

/** Idempotent. */
export async function releaseEditLock(schedulingId: string, token: string): Promise<void> {
  await apiClient.delete(lockPath(schedulingId), { headers: { [EDIT_LOCK_TOKEN_HEADER]: token } });
}

/** Best-effort release while the page is closing (keepalive lets the request outlive the page). */
export function releaseEditLockOnUnload(schedulingId: string, token: string): void {
  try {
    void fetch(`${API_BASE_URL}${lockPath(schedulingId)}`, {
      method: 'DELETE',
      keepalive: true,
      headers: { [EDIT_LOCK_TOKEN_HEADER]: token, [EDITOR_ID_HEADER]: getEditorId() },
    }).catch(() => undefined);
  } catch {
    // The lock expires by itself after 2 minutes.
  }
}
