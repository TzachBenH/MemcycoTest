import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { API, problem, server } from '../test/server';
import { ApiError } from '../utils/errors';
import { getEditorId, randomId, resetEditorIdForTests } from './editorId';
import { fetchTasks } from './tasks';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends the same editor id with every request of this tab', async () => {
    const seen: (string | null)[] = [];
    server.use(
      http.get(`${API}/tasks`, ({ request }) => {
        seen.push(request.headers.get('X-Editor-Id'));
        return HttpResponse.json([]);
      }),
    );
    await fetchTasks();
    await fetchTasks();
    expect(seen[0]).toMatch(UUID);
    expect(seen[1]).toBe(seen[0]);
    expect(sessionStorage.getItem('scheduler.editorId')).toBe(seen[0]);
  });

  it('turns error responses into ApiError', async () => {
    server.use(http.get(`${API}/tasks`, () => problem(500, 'INTERNAL_ERROR', 'Something went wrong. Please try again.')));
    const error = await fetchTasks().catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 500, code: 'INTERNAL_ERROR', message: 'Something went wrong. Please try again.' });
  });

  it('reports network failures', async () => {
    server.use(http.get(`${API}/tasks`, () => HttpResponse.error()));
    await expect(fetchTasks()).rejects.toMatchObject({ code: 'NETWORK_ERROR', status: 0 });
  });
});

describe('editor id', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    resetEditorIdForTests();
  });

  it('reuses the id stored for this tab', () => {
    sessionStorage.setItem('scheduler.editorId', 'stored-id');
    expect(getEditorId()).toBe('stored-id');
  });

  it('creates v4 UUIDs even without crypto.randomUUID (plain http)', () => {
    const getRandomValues = (bytes: Uint8Array) => bytes.fill(7);
    vi.stubGlobal('crypto', { getRandomValues });
    expect(randomId()).toMatch(UUID);
    vi.stubGlobal('crypto', undefined);
    expect(randomId()).toMatch(UUID);
  });
});
