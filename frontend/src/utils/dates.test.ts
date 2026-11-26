import { describe, expect, it } from 'vitest';
import { browserTimezone, formatDateTime, formatDuration, formatRelative, TIME_SECONDS_FORMAT } from './dates';

describe('formatDateTime', () => {
  it('formats in the given time zone', () => {
    expect(formatDateTime('2026-12-01T12:00:00Z', 'Asia/Jerusalem')).toBe('Dec 1, 2026 14:00');
    expect(formatDateTime('2026-12-01T12:00:05Z', 'UTC', TIME_SECONDS_FORMAT)).toBe('12:00:05');
  });

  it('uses the browser time zone by default and when the zone is unknown', () => {
    expect(formatDateTime('2026-12-01T12:00:00Z')).toBe('Dec 1, 2026 12:00');
    expect(formatDateTime('2026-12-01T12:00:00Z', 'Mars/Olympus')).toBe('Dec 1, 2026 12:00');
  });

  it('shows a dash for missing or invalid values', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime('not a date')).toBe('—');
  });
});

describe('formatRelative', () => {
  const now = Date.parse('2026-12-01T12:00:00Z');

  it('describes future and past instants', () => {
    expect(formatRelative('2026-12-01T12:05:00Z', now)).toBe('in 5 minutes');
    expect(formatRelative('2026-12-01T10:00:00Z', now)).toBe('2 hours ago');
  });

  it('shows a dash for missing values', () => {
    expect(formatRelative(undefined, now)).toBe('—');
    expect(formatRelative('garbage', now)).toBe('—');
  });
});

describe('formatDuration', () => {
  it.each([
    [350, '350 ms'],
    [2400, '2.4 s'],
    [15_000, '15 s'],
    [65_000, '1 min 5 s'],
    [120_000, '2 min'],
    [3_600_000, '1 h'],
    [7_800_000, '2 h 10 min'],
  ])('%i ms → %s', (ms, text) => {
    expect(formatDuration(ms)).toBe(text);
  });

  it('shows a dash for missing or negative durations', () => {
    expect(formatDuration(null)).toBe('—');
    expect(formatDuration(-5)).toBe('—');
  });
});

describe('browserTimezone', () => {
  it('returns the runtime time zone (UTC in tests)', () => {
    expect(browserTimezone()).toBe('UTC');
  });
});
