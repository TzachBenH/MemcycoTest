import { http, HttpResponse, type JsonBodyType } from 'msw';
import { setupServer } from 'msw/node';
import type { ErrorCode, ProblemDetail } from '../types/api';
import { makeExecution, makeScheduling, pageOf, tasksFixture } from './fixtures';

/** Matches the API on any origin (jsdom resolves the relative /api base URL against its own location). */
export const API = '*/api';

export function problem(status: number, code: ErrorCode, detail: string, extra: Partial<ProblemDetail> = {}) {
  const body: ProblemDetail = { type: 'about:blank', title: code, status, detail, code, ...extra };
  return HttpResponse.json(body as JsonBodyType, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

/** Default happy-path handlers; tests override single endpoints with server.use(...). */
export const handlers = [
  http.get(`${API}/tasks`, () => HttpResponse.json(tasksFixture)),
  http.get(`${API}/schedulings`, () => HttpResponse.json(pageOf([makeScheduling({ name: 'Default row' })]))),
  http.get(`${API}/schedulings/:id/executions`, () => HttpResponse.json(pageOf([makeExecution()]))),
  http.get(`${API}/schedulings/:id`, ({ params }) => HttpResponse.json(makeScheduling({ id: String(params.id) }))),
  http.post(`${API}/schedulings/:id/edit-lock`, () =>
    HttpResponse.json({ lockToken: 'lock-token-1', expiresAt: '2030-01-01T00:02:00Z' }),
  ),
  http.put(`${API}/schedulings/:id/edit-lock`, () =>
    HttpResponse.json({ lockToken: 'lock-token-1', expiresAt: '2030-01-01T00:04:00Z' }),
  ),
  http.delete(`${API}/schedulings/:id/edit-lock`, () => new HttpResponse(null, { status: 204 })),
  http.post(`${API}/schedulings/:id/run`, () => new HttpResponse(null, { status: 202 })),
  http.post(`${API}/schedulings/:id/pause`, ({ params }) =>
    HttpResponse.json(makeScheduling({ id: String(params.id), enabled: false, status: 'PAUSED' })),
  ),
  http.post(`${API}/schedulings/:id/resume`, ({ params }) => HttpResponse.json(makeScheduling({ id: String(params.id) }))),
  http.delete(`${API}/schedulings/:id`, () => new HttpResponse(null, { status: 204 })),
  http.post(`${API}/schedulings`, () => HttpResponse.json(makeScheduling(), { status: 201 })),
  http.put(`${API}/schedulings/:id`, ({ params }) => HttpResponse.json(makeScheduling({ id: String(params.id) }))),
];

export const server = setupServer(...handlers);
