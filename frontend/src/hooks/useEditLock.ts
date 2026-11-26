import { useCallback, useEffect, useRef, useState } from 'react';
import { releaseEditLock, releaseEditLockOnUnload, renewEditLock } from '../api/editLock';
import { toApiError } from '../utils/errors';

/** The backend lock lasts 2 minutes; renewing every 30 seconds keeps it with room for a missed renewal. */
export const RENEW_INTERVAL_MS = 30_000;

export interface EditLockState {
  /** The lock expired or was taken over: saving is no longer possible. */
  lost: boolean;
  /** Call when the lock was lost on save (409 EDIT_LOCK_LOST). */
  markLost: () => void;
  /** Call after a successful save: the backend already released the lock. */
  markReleased: () => void;
}

/**
 * Keeps an acquired edit lock alive while the editor is open: renews it every 30 seconds and releases it when the
 * editor closes (unmount) or the page is closed. The lock itself is acquired before the editor opens.
 */
export function useEditLock(schedulingId: string | null, token: string | null): EditLockState {
  const [lost, setLost] = useState(false);
  const released = useRef(false);
  const pendingRelease = useRef<number | null>(null);

  useEffect(() => {
    if (!schedulingId || !token) {
      return undefined;
    }
    // React StrictMode re-runs effects once in development: cancel the release the simulated unmount scheduled.
    if (pendingRelease.current !== null) {
      window.clearTimeout(pendingRelease.current);
      pendingRelease.current = null;
    }
    released.current = false;
    setLost(false);

    const timer = window.setInterval(() => {
      renewEditLock(schedulingId, token).catch((error: unknown) => {
        const status = toApiError(error).status;
        if (status === 409 || status === 404) {
          window.clearInterval(timer);
          setLost(true);
        }
        // Network errors: try again on the next tick; the lock outlives one missed renewal.
      });
    }, RENEW_INTERVAL_MS);

    const onPageClose = () => {
      if (!released.current) {
        released.current = true;
        releaseEditLockOnUnload(schedulingId, token);
      }
    };
    window.addEventListener('pagehide', onPageClose);

    return () => {
      window.clearInterval(timer);
      window.removeEventListener('pagehide', onPageClose);
      pendingRelease.current = window.setTimeout(() => {
        pendingRelease.current = null;
        if (!released.current) {
          released.current = true;
          releaseEditLock(schedulingId, token).catch(() => undefined);
        }
      }, 0);
    };
  }, [schedulingId, token]);

  const markLost = useCallback(() => setLost(true), []);
  const markReleased = useCallback(() => {
    released.current = true;
  }, []);

  return { lost, markLost, markReleased };
}
