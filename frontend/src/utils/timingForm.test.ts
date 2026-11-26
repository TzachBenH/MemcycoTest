import { describe, expect, it } from 'vitest';
import type { Timing } from '../types/api';
import dayjs from './dayjs';
import {
  defaultTimingValues,
  formToTiming,
  fromWallClock,
  isValidTimezone,
  timingFieldsOf,
  timingToForm,
  toWallClock,
  validateTiming,
  type TimingFormValues,
} from './timingForm';

const now = dayjs('2026-11-26T10:20:00Z');
const base = (overrides: Partial<TimingFormValues>): TimingFormValues => ({
  ...defaultTimingValues('UTC', now),
  ...overrides,
});

describe('wall-clock conversion', () => {
  it('shows instants as wall-clock time in the zone and converts back', () => {
    const wall = toWallClock('2026-12-01T12:00:00Z', 'Asia/Jerusalem');
    expect(wall.format('YYYY-MM-DD HH:mm')).toBe('2026-12-01 14:00');
    expect(fromWallClock(wall, 'Asia/Jerusalem')).toBe('2026-12-01T12:00:00.000Z');
  });

  it('keeps the wall-clock time when the zone changes', () => {
    const wall = toWallClock('2026-12-01T12:00:00Z', 'UTC');
    expect(fromWallClock(wall, 'Asia/Tokyo')).toBe('2026-12-01T03:00:00.000Z');
  });
});

describe('defaultTimingValues', () => {
  it('starts as "every 15 minutes from now", with sensible values on the other tabs', () => {
    const values = defaultTimingValues('UTC', now);
    expect(values.type).toBe('INTERVAL');
    expect(values.every).toBe('15');
    expect(values.unit).toBe('MINUTES');
    expect(values.startAt).toBeNull();
    expect(values.runAt?.format('YYYY-MM-DD HH:mm')).toBe('2026-11-26 11:00');
    expect(values.time?.format('HH:mm')).toBe('09:00');
    expect(values.timezone).toBe('UTC');
  });
});

describe('timing ⇄ form', () => {
  it('round-trips a one-time timing', () => {
    const timing = { type: 'ONE_TIME', runAt: '2026-12-01T12:00:00Z', timezone: 'Asia/Jerusalem' } as const;
    expect(formToTiming(timingToForm(timing, now))).toEqual({ ...timing, runAt: '2026-12-01T12:00:00.000Z' });
  });

  it('round-trips an interval timing', () => {
    const timing = {
      type: 'INTERVAL',
      every: 2,
      unit: 'HOURS',
      startAt: '2026-11-20T08:00:00Z',
      timezone: 'UTC',
    } as const;
    expect(formToTiming(timingToForm(timing, now))).toEqual({ ...timing, startAt: '2026-11-20T08:00:00.000Z' });
  });

  it('round-trips weekly and cron timings', () => {
    const weekly: Timing = { type: 'WEEKLY', days: ['MON', 'WED'], time: '09:30', timezone: 'UTC' };
    expect(formToTiming(timingToForm(weekly, now))).toEqual(weekly);
    const cron = { type: 'CRON', expression: '0 0 9 ? * MON', timezone: 'UTC' } as const;
    expect(formToTiming(timingToForm(cron, now))).toEqual(cron);
  });

  it('builds requests: no start = start now, sorted days, normalized cron spacing', () => {
    expect(formToTiming(base({ every: ' 5 ' }))).toEqual({
      type: 'INTERVAL',
      every: 5,
      unit: 'MINUTES',
      startAt: null,
      timezone: 'UTC',
    });
    expect(formToTiming(base({ type: 'WEEKLY', days: ['FRI', 'MON'] }))).toMatchObject({ days: ['MON', 'FRI'] });
    expect(formToTiming(base({ type: 'CRON', expression: '  0  0 9 ? *   MON ' }))).toMatchObject({
      expression: '0 0 9 ? * MON',
    });
  });

  it('refuses to build a timing with a missing value', () => {
    expect(() => formToTiming(base({ type: 'ONE_TIME', runAt: null }))).toThrow('runAt is required');
  });
});

describe('validateTiming (same rules as the backend)', () => {
  it('requires a valid time zone', () => {
    expect(validateTiming(base({ timezone: '' }), { now })).toEqual({ 'timing.timezone': 'is required' });
    expect(validateTiming(base({ timezone: 'Mars/Olympus' }), { now })).toEqual({
      'timing.timezone': 'is not a valid timezone',
    });
  });

  it('requires one-time runs in the future, except an unchanged saved run time', () => {
    expect(validateTiming(base({ type: 'ONE_TIME', runAt: null }), { now })).toEqual({ 'timing.runAt': 'is required' });
    const past = toWallClock('2026-11-26T09:00:00Z', 'UTC');
    expect(validateTiming(base({ type: 'ONE_TIME', runAt: past }), { now })).toEqual({
      'timing.runAt': 'must be in the future',
    });
    expect(validateTiming(base({ type: 'ONE_TIME', runAt: past }), { now, originalRunAt: '2026-11-26T09:00:00Z' })).toEqual(
      {},
    );
    const future = toWallClock('2026-11-26T11:00:00Z', 'UTC');
    expect(validateTiming(base({ type: 'ONE_TIME', runAt: future }), { now })).toEqual({});
  });

  it('checks the interval', () => {
    expect(validateTiming(base({ every: '' }), { now })).toEqual({ 'timing.every': 'is required' });
    for (const every of ['0', '10001', '1.5', 'x']) {
      expect(validateTiming(base({ every }), { now })).toEqual({ 'timing.every': 'must be between 1 and 10000' });
    }
    expect(validateTiming(base({ every: '10000' }), { now })).toEqual({});
    expect(validateTiming(base({ startAt: dayjs('invalid') }), { now })).toEqual({
      'timing.startAt': 'is not a valid date',
    });
  });

  it('checks weekly days and time', () => {
    expect(validateTiming(base({ type: 'WEEKLY', days: [], time: null }), { now })).toEqual({
      'timing.days': 'select at least one day',
      'timing.time': 'is required',
    });
    expect(validateTiming(base({ type: 'WEEKLY', days: ['SUN'] }), { now })).toEqual({});
  });

  it('checks the shape of cron expressions', () => {
    expect(validateTiming(base({ type: 'CRON', expression: ' ' }), { now })).toEqual({
      'timing.expression': 'is required',
    });
    expect(validateTiming(base({ type: 'CRON', expression: `0 0 9 ? * ${'MON,'.repeat(40)}MON` }), { now })).toEqual({
      'timing.expression': 'must be at most 120 characters',
    });
    expect(validateTiming(base({ type: 'CRON', expression: '0 9 * * *' }), { now })['timing.expression']).toMatch(
      /6 or 7 fields/,
    );
    expect(validateTiming(base({ type: 'CRON', expression: '0 0 9 ? * MON 2027' }), { now })).toEqual({});
  });
});

describe('helpers', () => {
  it('recognizes time zones', () => {
    expect(isValidTimezone('Asia/Jerusalem')).toBe(true);
    expect(isValidTimezone('Nowhere/Land')).toBe(false);
    expect(isValidTimezone(' ')).toBe(false);
  });

  it('lists the fields each timing type shows', () => {
    expect(timingFieldsOf('WEEKLY')).toEqual(['timing.days', 'timing.time', 'timing.timezone']);
  });
});
