import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { makeScheduling, pageOf } from '../test/fixtures';
import { currentLocation, renderApp } from '../test/render';
import { API, problem, server } from '../test/server';
import type { Scheduling } from '../types/api';

const activeLog = makeScheduling({
  id: 'a-1',
  name: 'Active log',
  lastExecution: {
    id: 'e-1',
    status: 'SUCCESS',
    triggerType: 'SCHEDULED',
    attempt: 1,
    maxAttempts: 3,
    startedAt: '2026-11-26T09:00:00Z',
    finishedAt: '2026-11-26T09:00:01Z',
    nextRetryAt: null,
    retryCancelledReason: null,
    errorMessage: null,
  },
});
const pausedWeekly = makeScheduling({
  id: 'p-1',
  name: 'Paused weekly',
  enabled: false,
  status: 'PAUSED',
  nextFireTime: null,
  timing: { type: 'WEEKLY', days: ['MON', 'WED'], time: '09:00', timezone: 'Asia/Jerusalem' },
});
const lockedRow = makeScheduling({
  id: 'l-1',
  name: 'Locked one',
  editLock: { lockedBy: 'alice', since: '2026-11-26T09:58:00Z', expiresAt: '2026-11-26T10:00:00Z', mine: false },
});
const retrying = makeScheduling({
  id: 'r-1',
  name: 'Retrying email',
  task: { key: 'DUMMY_EMAIL', name: 'Send Email (simulated)', known: true },
  parameters: { to: 'x@fail.example', subject: 'Hi' },
  timing: { type: 'CRON', expression: '0 0 9 ? * MON', timezone: 'UTC' },
  lastExecution: {
    id: 'e-2',
    status: 'FAILED',
    triggerType: 'MANUAL',
    attempt: 1,
    maxAttempts: 3,
    startedAt: '2026-11-26T09:59:00Z',
    finishedAt: '2026-11-26T09:59:01Z',
    nextRetryAt: '2026-11-26T09:59:11Z',
    retryCancelledReason: null,
    errorMessage: 'Simulated SMTP failure',
  },
});
const orphan = makeScheduling({ id: 'o-1', name: 'Old task', task: { key: 'OLD', name: 'OLD', known: false } });

const allRows = [activeLog, pausedWeekly, lockedRow, retrying, orphan];

/** Serves the list and records the query string of every list request. */
function serveList(rows: Scheduling[] = allRows, total = rows.length) {
  const requests: URLSearchParams[] = [];
  server.use(
    http.get(`${API}/schedulings`, ({ request }) => {
      const params = new URL(request.url).searchParams;
      requests.push(params);
      return HttpResponse.json(pageOf(rows, Number(params.get('page') ?? 0), Number(params.get('size') ?? 20), total));
    }),
  );
  return requests;
}

const row = (name: string) => screen.getByRole('row', { name: new RegExp(name) });

describe('SchedulingsPage', () => {
  it('shows one row per scheduling with its timing, last run and state', async () => {
    serveList();
    renderApp('/');
    expect(await screen.findByText('Active log')).toBeInTheDocument();

    expect(within(row('Active log')).getByText('Every 15 minutes from Nov 20, 2026 08:00 (UTC)')).toBeInTheDocument();
    expect(within(row('Active log')).getByText('Success')).toBeInTheDocument();
    expect(within(row('Active log')).getByText('level: INFO · message: Hello')).toBeInTheDocument();
    expect(within(row('Paused weekly')).getByText('Mon, Wed at 09:00 (Asia/Jerusalem)')).toBeInTheDocument();
    expect(within(row('Paused weekly')).getByText('Paused')).toBeInTheDocument();
    expect(within(row('Retrying email')).getByText('At 09:00, only on Monday (UTC)')).toBeInTheDocument();
    expect(within(row('Retrying email')).getByText('↻ 2/3 at 09:59:11')).toBeInTheDocument();
    expect(within(row('Old task')).getByText('Unknown task')).toBeInTheDocument();
    expect(within(row('Old task')).getByText('Never')).toBeInTheDocument();
  });

  it('blocks editing, deleting and pausing a row someone else is editing, but not Run now', async () => {
    serveList();
    renderApp('/');
    await screen.findByText('Locked one');
    const locked = row('Locked one');
    expect(within(locked).getByRole('img', { name: /Being edited by alice since 09:58:00/ })).toBeInTheDocument();
    expect(within(locked).getByRole('button', { name: 'Edit' })).toBeDisabled();
    expect(within(locked).getByRole('button', { name: 'Delete' })).toBeDisabled();
    expect(within(locked).getByLabelText('Active: Locked one')).toBeDisabled();
    expect(within(locked).getByRole('button', { name: 'Run now' })).toBeEnabled();
  });

  it('searches by name through the URL (debounced)', async () => {
    const requests = serveList();
    const { user } = renderApp('/');
    await screen.findByText('Active log');
    await user.type(screen.getByRole('textbox', { name: /search by name/i }), 'nightly');

    await waitFor(() => expect(currentLocation()).toBe('/?search=nightly'));
    await waitFor(() => expect(requests.at(-1)?.get('search')).toBe('nightly'));
  });

  it('filters by status and sorts by column', async () => {
    const requests = serveList();
    const { user } = renderApp('/');
    await screen.findByText('Active log');

    await user.click(screen.getByRole('combobox', { name: /^status/i }));
    await user.click(screen.getByRole('option', { name: 'Paused' }));
    await waitFor(() => expect(requests.at(-1)?.get('status')).toBe('PAUSED'));
    expect(currentLocation()).toBe('/?status=PAUSED');

    await user.click(screen.getByRole('button', { name: 'Name' }));
    await waitFor(() => expect(requests.at(-1)?.get('sort')).toBe('name,desc'));
    expect(currentLocation()).toBe('/?status=PAUSED&sort=name%2Cdesc');
  });

  it('pages through results', async () => {
    const requests = serveList(allRows, 45);
    const { user } = renderApp('/');
    await screen.findByText('Active log');
    await user.click(screen.getByRole('button', { name: /next page/i }));
    await waitFor(() => expect(requests.at(-1)?.get('page')).toBe('1'));
    expect(currentLocation()).toBe('/?page=2');
  });

  it('pauses an active scheduling', async () => {
    serveList();
    const paused: string[] = [];
    server.use(
      http.post(`${API}/schedulings/:id/pause`, ({ params }) => {
        paused.push(String(params.id));
        return HttpResponse.json({ ...activeLog, enabled: false, status: 'PAUSED' });
      }),
    );
    const { user } = renderApp('/');
    await screen.findByText('Active log');
    await user.click(screen.getByLabelText('Active: Active log'));
    expect(await screen.findByText('“Active log” paused')).toBeInTheDocument();
    expect(paused).toEqual(['a-1']);
  });

  it('explains why resuming is blocked and goes to the active duplicate', async () => {
    serveList();
    server.use(
      http.post(`${API}/schedulings/:id/resume`, () =>
        problem(409, 'DUPLICATE_SCHEDULING', '"Twin" is already scheduled with the same task, parameters and timing', {
          conflictingScheduling: { id: 't-1', name: 'Twin' },
        }),
      ),
    );
    const { user } = renderApp('/');
    await screen.findByText('Paused weekly');
    await user.click(screen.getByLabelText('Active: Paused weekly'));

    const dialog = await screen.findByRole('dialog', { name: /Can't resume “Paused weekly”/ });
    expect(within(dialog).getByText(/"Twin" is already scheduled/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Go to “Twin”' }));
    await waitFor(() => expect(currentLocation()).toBe('/?search=Twin'));
  });

  it('runs a scheduling now and links to its history', async () => {
    serveList();
    const runs: string[] = [];
    server.use(
      http.post(`${API}/schedulings/:id/run`, ({ params }) => {
        runs.push(String(params.id));
        return new HttpResponse(null, { status: 202 });
      }),
    );
    const { user } = renderApp('/');
    await screen.findByText('Active log');
    await user.click(within(row('Active log')).getByRole('button', { name: 'Run now' }));

    expect(await screen.findByText('Run of “Active log” started')).toBeInTheDocument();
    expect(runs).toEqual(['a-1']);
    await user.click(within(screen.getByRole('alert')).getByRole('button', { name: 'History' }));
    expect(currentLocation()).toBe('/schedulings/a-1/history');
  });

  it('opens the history page of a row', async () => {
    serveList();
    const { user } = renderApp('/');
    await screen.findByText('Active log');
    await user.click(within(row('Active log')).getByRole('button', { name: 'History' }));
    expect(currentLocation()).toBe('/schedulings/a-1/history');
  });

  it('deletes after confirmation', async () => {
    serveList();
    const deleted: string[] = [];
    server.use(
      http.delete(`${API}/schedulings/:id`, ({ params }) => {
        deleted.push(String(params.id));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderApp('/');
    await screen.findByText('Active log');
    await user.click(within(row('Active log')).getByRole('button', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete scheduling?' });
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(await screen.findByText('“Active log” deleted')).toBeInTheDocument();
    expect(deleted).toEqual(['a-1']);
  });

  it('shows who is editing when the edit lock is taken, without opening the editor', async () => {
    serveList();
    server.use(
      http.post(`${API}/schedulings/:id/edit-lock`, () =>
        problem(409, 'EDIT_LOCKED', 'This scheduling is being edited by another user or in another tab', {
          lockedBy: 'bob',
          since: '2026-11-26T09:30:00Z',
        }),
      ),
    );
    const { user } = renderApp('/');
    await screen.findByText('Active log');
    await user.click(within(row('Active log')).getByRole('button', { name: 'Edit' }));

    expect(await screen.findByText('“Active log” is being edited by bob since 09:30:00. Try again when they are done.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('edits with the lock: loads the latest version and releases the lock on cancel', async () => {
    serveList();
    const released: string[] = [];
    server.use(
      http.get(`${API}/schedulings/:id`, () => HttpResponse.json({ ...activeLog, name: 'Active log (latest)' })),
      http.delete(`${API}/schedulings/:id/edit-lock`, ({ request }) => {
        released.push(request.headers.get('X-Edit-Lock-Token') ?? '');
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderApp('/');
    await screen.findByText('Active log');
    await user.click(within(row('Active log')).getByRole('button', { name: 'Edit' }));

    const dialog = await screen.findByRole('dialog', { name: 'Edit scheduling' });
    expect(within(dialog).getByRole('textbox', { name: /^name/i })).toHaveValue('Active log (latest)');
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(released).toEqual(['lock-token-1']));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('creates a scheduling and shows duplicate warnings from the response', async () => {
    serveList();
    server.use(
      http.post(`${API}/schedulings`, () =>
        HttpResponse.json(
          makeScheduling({
            name: 'Copy',
            warnings: [
              {
                code: 'PAUSED_DUPLICATE_EXISTS',
                message: 'A paused scheduling with the same settings exists: "Paused weekly"',
                schedulingId: 'p-1',
                schedulingName: 'Paused weekly',
              },
            ],
          }),
          { status: 201 },
        ),
      ),
    );
    const { user } = renderApp('/');
    await screen.findByText('Active log');
    await user.click(screen.getByRole('button', { name: 'New scheduling' }));
    const dialog = await screen.findByRole('dialog', { name: 'New scheduling' });
    await user.type(within(dialog).getByRole('textbox', { name: /^name/i }), 'Copy');
    await user.click(within(dialog).getByRole('combobox', { name: /^task/i }));
    await user.click(screen.getByRole('option', { name: 'Log Message' }));
    await user.type(within(dialog).getByRole('textbox', { name: /log message/i }), 'Hello');
    await user.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(
      await screen.findByText('“Copy” created. A paused scheduling with the same settings exists: "Paused weekly"'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('has empty states with and without filters', async () => {
    serveList([]);
    const { user } = renderApp('/');
    expect(await screen.findByText('No schedulings yet')).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: /^status/i }));
    await user.click(screen.getByRole('option', { name: 'Completed' }));
    expect(await screen.findByText('No schedulings match the filters')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(currentLocation()).toBe('/'));
  });

  it('shows an error with a retry button when loading fails', async () => {
    let fail = true;
    server.use(
      http.get(`${API}/schedulings`, () =>
        fail ? problem(500, 'INTERNAL_ERROR', 'Something went wrong. Please try again.') : HttpResponse.json(pageOf(allRows)),
      ),
    );
    const { user } = renderApp('/');
    expect(await screen.findByText('Could not load schedulings')).toBeInTheDocument();
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Active log')).toBeInTheDocument();
  });

  it('reports action errors', async () => {
    serveList();
    server.use(
      http.post(`${API}/schedulings/:id/pause`, () =>
        problem(409, 'EDIT_LOCKED', 'This scheduling is being edited by another user or in another tab', {
          lockedBy: 'carol',
          since: '2026-11-26T09:45:00Z',
        }),
      ),
      http.post(`${API}/schedulings/:id/run`, () => problem(404, 'NOT_FOUND', 'Scheduling a-1 was not found')),
    );
    const { user } = renderApp('/');
    await screen.findByText('Active log');
    await user.click(screen.getByLabelText('Active: Active log'));
    expect(await screen.findByText(/is being edited by carol/)).toBeInTheDocument();
  });
});
