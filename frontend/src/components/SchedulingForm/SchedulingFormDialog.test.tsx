import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { makeScheduling, tasksFixture } from '../../test/fixtures';
import { currentLocation, renderWithProviders } from '../../test/render';
import { API, problem, server } from '../../test/server';
import type { Scheduling, SchedulingRequest } from '../../types/api';
import { SchedulingFormDialog } from './SchedulingFormDialog';

function renderDialog(props: { scheduling?: Scheduling; lockToken?: string } = {}) {
  const onClose = vi.fn();
  const onSaved = vi.fn();
  const result = renderWithProviders(
    <SchedulingFormDialog tasks={tasksFixture} onClose={onClose} onSaved={onSaved} {...props} />,
  );
  const dialog = screen.getByRole('dialog');
  return { ...result, dialog, onClose, onSaved };
}

/** Records the request bodies (and headers) the backend receives. */
function captureCreate(response: () => Response = () => HttpResponse.json(makeScheduling(), { status: 201 })) {
  const bodies: SchedulingRequest[] = [];
  server.use(
    http.post(`${API}/schedulings`, async ({ request }) => {
      bodies.push((await request.json()) as SchedulingRequest);
      return response();
    }),
  );
  return bodies;
}

describe('SchedulingFormDialog — create', () => {
  async function fillLogScheduling(user: ReturnType<typeof renderDialog>['user'], dialog: HTMLElement) {
    await user.type(within(dialog).getByRole('textbox', { name: /^name/i }), 'Nightly log');
    await user.click(within(dialog).getByRole('combobox', { name: /^task/i }));
    await user.click(screen.getByRole('option', { name: 'Log Message' }));
    await user.type(within(dialog).getByRole('textbox', { name: /log message/i }), 'Hello world');
  }

  it('creates a scheduling from the form', async () => {
    const bodies = captureCreate();
    const { user, dialog, onSaved } = renderDialog();
    expect(within(dialog).getByRole('heading', { name: 'New scheduling' })).toBeInTheDocument();

    await fillLogScheduling(user, dialog);
    expect(within(dialog).getByText('Writes a message to the application log.')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ id: expect.any(String) }), 'create'));
    expect(bodies).toEqual([
      {
        name: 'Nightly log',
        taskKey: 'LOG',
        parameters: { message: 'Hello world', level: 'INFO' },
        timing: { type: 'INTERVAL', every: 15, unit: 'MINUTES', startAt: null, timezone: 'UTC' },
        enabled: true,
      },
    ]);
  });

  it('validates on the client before sending anything', async () => {
    const bodies = captureCreate();
    const { user, dialog } = renderDialog();
    await user.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(await within(dialog).findAllByText('Is required')).toHaveLength(2);
    expect(bodies).toHaveLength(0);
  });

  it('shows server validation errors under the fields, and the rest in a banner', async () => {
    captureCreate(() =>
      problem(400, 'VALIDATION_FAILED', 'The request has 3 invalid field(s)', {
        errors: {
          'parameters.message': 'must be at most 500 characters',
          'timing.every': 'must be between 1 and 10000',
          version: 'is required',
        },
      }),
    );
    const { user, dialog, onSaved } = renderDialog();
    await fillLogScheduling(user, dialog);
    await user.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(await within(dialog).findByText('Must be at most 500 characters')).toBeInTheDocument();
    expect(within(dialog).getByText('Must be between 1 and 10000')).toBeInTheDocument();
    expect(within(dialog).getByText('Version: is required')).toBeInTheDocument();
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('explains a duplicate and links to the existing scheduling', async () => {
    captureCreate(() =>
      problem(409, 'DUPLICATE_SCHEDULING', '"Existing one" is already scheduled with the same task, parameters and timing', {
        conflictingScheduling: { id: 'c-1', name: 'Existing one' },
      }),
    );
    const { user, dialog, onClose } = renderDialog();
    await fillLogScheduling(user, dialog);
    await user.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(await within(dialog).findByText('Duplicate scheduling')).toBeInTheDocument();
    expect(within(dialog).getByText(/is already scheduled with the same task, parameters and timing/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Show it' }));
    expect(onClose).toHaveBeenCalled();
    expect(currentLocation()).toBe('/?search=Existing%20one');
  });

  it('resets the parameters when another task is chosen', async () => {
    const { user, dialog } = renderDialog();
    await user.click(within(dialog).getByRole('combobox', { name: /^task/i }));
    await user.click(screen.getByRole('option', { name: 'Log Message' }));
    expect(within(dialog).getByRole('textbox', { name: /log message/i })).toBeInTheDocument();

    await user.click(within(dialog).getByRole('combobox', { name: /^task/i }));
    await user.click(screen.getByRole('option', { name: 'Send Email (simulated)' }));
    expect(within(dialog).queryByRole('textbox', { name: /log message/i })).not.toBeInTheDocument();
    expect(within(dialog).getByRole('textbox', { name: /^to/i })).toHaveValue('');
    expect(within(dialog).getByRole('textbox', { name: /subject/i })).toBeInTheDocument();
  });
});

describe('SchedulingFormDialog — edit', () => {
  const existing = () =>
    makeScheduling({
      id: 'edit-1',
      name: 'Morning report',
      version: 7,
      parameters: { message: 'Good morning', level: 'WARN' },
    });

  function capturePut(response: () => Response = () => HttpResponse.json(existing())) {
    const calls: { body: SchedulingRequest; token: string | null }[] = [];
    server.use(
      http.put(`${API}/schedulings/:id`, async ({ request }) => {
        calls.push({ body: (await request.json()) as SchedulingRequest, token: request.headers.get('X-Edit-Lock-Token') });
        return response();
      }),
    );
    return calls;
  }

  it('saves with the lock token and the loaded version', async () => {
    const calls = capturePut();
    const { user, dialog, onSaved } = renderDialog({ scheduling: existing(), lockToken: 'tok-1' });
    expect(within(dialog).getByRole('heading', { name: 'Edit scheduling' })).toBeInTheDocument();
    const name = within(dialog).getByRole('textbox', { name: /^name/i });
    expect(name).toHaveValue('Morning report');
    expect(within(dialog).getByRole('textbox', { name: /log message/i })).toHaveValue('Good morning');

    await user.clear(name);
    await user.type(name, 'Morning report v2');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(expect.anything(), 'edit'));
    expect(calls).toHaveLength(1);
    expect(calls[0].token).toBe('tok-1');
    expect(calls[0].body).toMatchObject({
      name: 'Morning report v2',
      version: 7,
      parameters: { message: 'Good morning', level: 'WARN' },
      timing: { type: 'INTERVAL', every: 15, unit: 'MINUTES', startAt: '2026-11-20T08:00:00.000Z', timezone: 'UTC' },
    });
  });

  it('keeps an unchanged past run time of a completed one-time scheduling', async () => {
    const calls = capturePut();
    const completed = makeScheduling({
      id: 'done-1',
      status: 'COMPLETED',
      timing: { type: 'ONE_TIME', runAt: '2025-01-01T08:00:00Z', timezone: 'UTC' },
      version: 2,
    });
    const { user, dialog, onSaved } = renderDialog({ scheduling: completed, lockToken: 'tok-2' });
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(calls[0].body.timing).toEqual({ type: 'ONE_TIME', runAt: '2025-01-01T08:00:00.000Z', timezone: 'UTC' });
  });

  it('disables saving when the edit lock was lost', async () => {
    capturePut(() =>
      problem(409, 'EDIT_LOCK_LOST', 'You no longer hold the edit lock for this scheduling. Reload to see the latest version.'),
    );
    const { user, dialog } = renderDialog({ scheduling: existing(), lockToken: 'tok-1' });
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(await within(dialog).findByText('Edit lock lost')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('offers to reload after a concurrent change', async () => {
    capturePut(() =>
      problem(409, 'CONCURRENT_MODIFICATION', 'This scheduling was changed by someone else. Reload to see the latest version.'),
    );
    server.use(
      http.get(`${API}/schedulings/:id`, () =>
        HttpResponse.json(makeScheduling({ id: 'edit-1', name: 'Renamed elsewhere', version: 8 })),
      ),
    );
    const { user, dialog } = renderDialog({ scheduling: existing(), lockToken: 'tok-1' });
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(await within(dialog).findByText('Changed by someone else')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Reload' }));
    await waitFor(() => expect(within(dialog).getByRole('textbox', { name: /^name/i })).toHaveValue('Renamed elsewhere'));
    expect(within(dialog).queryByText('Changed by someone else')).not.toBeInTheDocument();
  });

  it('releases the edit lock when the editor closes', async () => {
    const released: (string | null)[] = [];
    server.use(
      http.delete(`${API}/schedulings/:id/edit-lock`, ({ request }) => {
        released.push(request.headers.get('X-Edit-Lock-Token'));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user, dialog, onClose, unmount } = renderDialog({ scheduling: existing(), lockToken: 'tok-9' });
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalled();

    unmount();
    await waitFor(() => expect(released).toEqual(['tok-9']));
  });

  it('shows duplicate warnings of the loaded scheduling', () => {
    const withWarning = makeScheduling({
      warnings: [
        {
          code: 'PAUSED_DUPLICATE_EXISTS',
          message: 'A paused scheduling with the same settings exists: "Old copy"',
          schedulingId: 'x',
          schedulingName: 'Old copy',
        },
      ],
    });
    const { dialog } = renderDialog({ scheduling: withWarning, lockToken: 't' });
    expect(within(dialog).getByText('A paused scheduling with the same settings exists: "Old copy"')).toBeInTheDocument();
  });
});
