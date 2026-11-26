import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { makeExecution, makeScheduling, pageOf } from '../test/fixtures';
import { currentLocation, renderApp } from '../test/render';
import { API, problem, server } from '../test/server';

const scheduling = makeScheduling({
  id: 's-1',
  name: 'Email digest',
  task: { key: 'DUMMY_EMAIL', name: 'Send Email (simulated)', known: true },
  timing: { type: 'WEEKLY', days: ['MON', 'WED'], time: '09:00', timezone: 'Asia/Jerusalem' },
  nextFireTime: '2030-01-07T07:00:00Z',
});

const runs = [
  makeExecution({
    id: 'x-3',
    status: 'RUNNING',
    triggerType: 'MANUAL',
    executedBy: 'backend-2',
    startedAt: new Date(Date.now() - 5_000).toISOString(),
    finishedAt: null,
    durationMs: null,
  }),
  makeExecution({
    id: 'x-2',
    status: 'FAILED',
    triggerType: 'RETRY',
    attempt: 2,
    errorMessage: 'Simulated SMTP failure: connection refused',
    nextRetryAt: '2026-11-26T10:00:30Z',
  }),
  makeExecution({
    id: 'x-1',
    status: 'FAILED',
    attempt: 1,
    errorMessage: 'Simulated SMTP failure',
    nextRetryAt: '2026-11-26T10:00:10Z',
    retryCancelledReason: 'PAUSED',
  }),
  makeExecution({ id: 'x-0', status: 'SKIPPED', errorMessage: 'Skipped: the previous run is still running' }),
];

function serve(total = runs.length) {
  const requests: URLSearchParams[] = [];
  server.use(
    http.get(`${API}/schedulings/:id`, () => HttpResponse.json(scheduling)),
    http.get(`${API}/schedulings/:id/executions`, ({ request }) => {
      const params = new URL(request.url).searchParams;
      requests.push(params);
      return HttpResponse.json(pageOf(runs, Number(params.get('page') ?? 0), 20, total));
    }),
  );
  return requests;
}

const row = (id: string, text: RegExp) =>
  screen.getAllByRole('row').find((r) => text.test(r.textContent ?? '')) ?? (() => {
    throw new Error(`No row ${id}`);
  })();

describe('ExecutionHistoryPage', () => {
  it('shows the scheduling and its runs', async () => {
    serve();
    renderApp('/schedulings/s-1/history');

    expect(await screen.findByRole('heading', { name: 'Email digest' })).toBeInTheDocument();
    expect(screen.getByText('Send Email (simulated) · Mon, Wed at 09:00 (Asia/Jerusalem)')).toBeInTheDocument();
    expect(await screen.findByText('backend-2')).toBeInTheDocument();

    const running = row('x-3', /Running/);
    expect(within(running).getByText('Manual')).toBeInTheDocument();
    expect(within(running).getByText(/…/)).toBeInTheDocument();

    const retried = row('x-2', /Retry at 10:00:30/);
    expect(within(retried).getByText('2/3')).toBeInTheDocument();
    expect(within(retried).getByText('Simulated SMTP failure: connection refused')).toBeInTheDocument();

    expect(row('x-1', /Cancelled: scheduling paused/)).toBeInTheDocument();
    expect(within(row('x-0', /Skipped/)).getByText('1.3 s')).toBeInTheDocument();
  });

  it('filters by status through the URL', async () => {
    const requests = serve();
    const { user } = renderApp('/schedulings/s-1/history');
    await screen.findByText('backend-2');

    await user.click(screen.getByRole('combobox', { name: /^status/i }));
    await user.click(screen.getByRole('option', { name: 'Failed' }));
    await waitFor(() => expect(requests.at(-1)?.get('status')).toBe('FAILED'));
    expect(currentLocation()).toBe('/schedulings/s-1/history?status=FAILED');
  });

  it('pages through the runs', async () => {
    const requests = serve(45);
    const { user } = renderApp('/schedulings/s-1/history');
    await screen.findByText('backend-2');
    await user.click(screen.getByRole('button', { name: /next page/i }));
    await waitFor(() => expect(requests.at(-1)?.get('page')).toBe('1'));
    expect(currentLocation()).toBe('/schedulings/s-1/history?page=2');
  });

  it('runs the scheduling now', async () => {
    serve();
    const started: string[] = [];
    server.use(
      http.post(`${API}/schedulings/:id/run`, ({ params }) => {
        started.push(String(params.id));
        return new HttpResponse(null, { status: 202 });
      }),
    );
    const { user } = renderApp('/schedulings/s-1/history');
    await screen.findByRole('heading', { name: 'Email digest' });
    await user.click(screen.getByRole('button', { name: 'Run now' }));

    expect(await screen.findByText('Run started. It appears below within a few seconds.')).toBeInTheDocument();
    expect(started).toEqual(['s-1']);
  });

  it('says when there are no runs', async () => {
    server.use(
      http.get(`${API}/schedulings/:id`, () => HttpResponse.json(scheduling)),
      http.get(`${API}/schedulings/:id/executions`, () => HttpResponse.json(pageOf([]))),
    );
    renderApp('/schedulings/s-1/history?status=STUCK');
    expect(await screen.findByText('No stuck runs')).toBeInTheDocument();
  });

  it('handles a deleted scheduling and goes back to the list', async () => {
    server.use(
      http.get(`${API}/schedulings/:id`, () => problem(404, 'NOT_FOUND', 'Scheduling s-1 was not found')),
      http.get(`${API}/schedulings/:id/executions`, () => problem(404, 'NOT_FOUND', 'Scheduling s-1 was not found')),
    );
    const { user } = renderApp('/schedulings/s-1/history');
    expect(await screen.findByText('This scheduling does not exist. It may have been deleted.')).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: /schedulings/i }));
    expect(currentLocation()).toBe('/');
  });

  it('shows errors with a retry button', async () => {
    server.use(
      http.get(`${API}/schedulings/:id`, () => problem(500, 'INTERNAL_ERROR', 'Something went wrong. Please try again.')),
      http.get(`${API}/schedulings/:id/executions`, () =>
        problem(500, 'INTERNAL_ERROR', 'Something went wrong. Please try again.'),
      ),
    );
    renderApp('/schedulings/s-1/history');
    expect(await screen.findByText('Could not load the history')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Retry' })).toHaveLength(2);
  });
});
