import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { releaseEditLock, releaseEditLockOnUnload, renewEditLock } from '../api/editLock';
import { ApiError } from '../utils/errors';
import { RENEW_INTERVAL_MS, useEditLock } from './useEditLock';

vi.mock('../api/editLock', () => ({
  renewEditLock: vi.fn(),
  releaseEditLock: vi.fn(),
  releaseEditLockOnUnload: vi.fn(),
  acquireEditLock: vi.fn(),
}));

const renew = vi.mocked(renewEditLock);
const release = vi.mocked(releaseEditLock);
const releaseOnUnload = vi.mocked(releaseEditLockOnUnload);

describe('useEditLock', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    renew.mockResolvedValue({ lockToken: 't1', expiresAt: '2030-01-01T00:00:00Z' });
    release.mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('renews the lock every 30 seconds', async () => {
    renderHook(() => useEditLock('s1', 't1'));
    expect(renew).not.toHaveBeenCalled();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(RENEW_INTERVAL_MS);
    });
    expect(renew).toHaveBeenCalledWith('s1', 't1');
    await act(async () => {
      await vi.advanceTimersByTimeAsync(RENEW_INTERVAL_MS);
    });
    expect(renew).toHaveBeenCalledTimes(2);
  });

  it('reports the lock as lost when renewing is refused, and stops renewing', async () => {
    renew.mockRejectedValue(new ApiError({ status: 409, code: 'EDIT_LOCK_LOST', message: 'lost' }));
    const { result } = renderHook(() => useEditLock('s1', 't1'));
    expect(result.current.lost).toBe(false);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(RENEW_INTERVAL_MS);
    });
    expect(result.current.lost).toBe(true);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(RENEW_INTERVAL_MS * 3);
    });
    expect(renew).toHaveBeenCalledTimes(1);
  });

  it('keeps the lock after a network error (the next renewal may succeed)', async () => {
    renew.mockRejectedValueOnce(new ApiError({ status: 0, code: 'NETWORK_ERROR', message: 'offline' }));
    const { result } = renderHook(() => useEditLock('s1', 't1'));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(RENEW_INTERVAL_MS * 2);
    });
    expect(result.current.lost).toBe(false);
    expect(renew).toHaveBeenCalledTimes(2);
  });

  it('releases the lock when the editor closes', async () => {
    const { unmount } = renderHook(() => useEditLock('s1', 't1'));
    unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(release).toHaveBeenCalledWith('s1', 't1');
  });

  it('does not release a lock the backend already released after saving', async () => {
    const { result, unmount } = renderHook(() => useEditLock('s1', 't1'));
    result.current.markReleased();
    unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(release).not.toHaveBeenCalled();
  });

  it('releases the lock when the page is closed', () => {
    renderHook(() => useEditLock('s1', 't1'));
    window.dispatchEvent(new Event('pagehide'));
    expect(releaseOnUnload).toHaveBeenCalledWith('s1', 't1');
  });

  it('can be marked lost by a failed save, and does nothing without a lock', async () => {
    const { result } = renderHook(() => useEditLock(null, null));
    act(() => result.current.markLost());
    expect(result.current.lost).toBe(true);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(RENEW_INTERVAL_MS);
    });
    expect(renew).not.toHaveBeenCalled();
  });
});
