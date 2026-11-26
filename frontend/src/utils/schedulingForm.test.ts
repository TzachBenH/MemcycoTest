import { describe, expect, it } from 'vitest';
import { makeScheduling, tasksFixture } from '../test/fixtures';
import dayjs from './dayjs';
import {
  emptySchedulingForm,
  schedulingToForm,
  toSchedulingRequest,
  validateSchedulingForm,
  visibleFieldPaths,
} from './schedulingForm';

const [logTask, emailTask] = tasksFixture;
const now = dayjs('2026-11-26T10:20:00Z');

describe('schedulingForm', () => {
  it('starts empty and active', () => {
    const values = emptySchedulingForm('Asia/Jerusalem', now);
    expect(values).toMatchObject({ name: '', taskKey: '', parameters: {}, enabled: true });
    expect(values.timing.timezone).toBe('Asia/Jerusalem');
  });

  it('loads an existing scheduling', () => {
    const scheduling = makeScheduling({ name: 'Nightly', parameters: { message: 'Hi', level: 'WARN' }, enabled: false });
    expect(schedulingToForm(scheduling, logTask, now)).toMatchObject({
      name: 'Nightly',
      taskKey: 'LOG',
      parameters: { message: 'Hi', level: 'WARN' },
      enabled: false,
      timing: { type: 'INTERVAL', every: '15', unit: 'MINUTES' },
    });
    expect(schedulingToForm(scheduling, undefined, now).parameters).toEqual({});
  });

  it('collects all errors at once, keyed like the API', () => {
    const values = { ...emptySchedulingForm('UTC', now), timing: { ...emptySchedulingForm('UTC', now).timing, every: '0' } };
    expect(validateSchedulingForm(values, undefined, { now })).toEqual({
      name: 'is required',
      taskKey: 'is required',
      'timing.every': 'must be between 1 and 10000',
    });
  });

  it('validates the name length, unknown tasks and parameters', () => {
    const values = { ...emptySchedulingForm('UTC', now), name: 'x'.repeat(101), taskKey: 'GONE' };
    expect(validateSchedulingForm(values, undefined, { now })).toEqual({
      name: 'must be at most 100 characters',
      taskKey: 'is not a known task',
    });
    const email = { ...emptySchedulingForm('UTC', now), name: 'Mail', taskKey: 'DUMMY_EMAIL', parameters: { to: 'x' } };
    expect(validateSchedulingForm(email, emailTask, { now })).toEqual({
      'parameters.to': 'must be a valid email address',
      'parameters.subject': 'is required',
    });
  });

  it('builds the request (with the version only when editing)', () => {
    const values = {
      ...emptySchedulingForm('UTC', now),
      name: '  Nightly  ',
      taskKey: 'LOG',
      parameters: { message: 'Hello', level: 'INFO' },
    };
    expect(toSchedulingRequest(values, logTask)).toEqual({
      name: 'Nightly',
      taskKey: 'LOG',
      parameters: { message: 'Hello', level: 'INFO' },
      timing: { type: 'INTERVAL', every: 15, unit: 'MINUTES', startAt: null, timezone: 'UTC' },
      enabled: true,
    });
    expect(toSchedulingRequest(values, logTask, 4).version).toBe(4);
  });

  it('knows which fields are on screen', () => {
    const values = { ...emptySchedulingForm('UTC', now), taskKey: 'LOG' };
    expect([...visibleFieldPaths(values, logTask)]).toEqual([
      'name',
      'taskKey',
      'parameters.message',
      'parameters.level',
      'timing.every',
      'timing.unit',
      'timing.startAt',
      'timing.timezone',
    ]);
  });
});
