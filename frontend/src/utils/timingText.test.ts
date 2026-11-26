import { describe, expect, it } from 'vitest';
import { describeCron, describeDays, describeEvery, describeTiming } from './timingText';

describe('describeTiming', () => {
  it('describes a one-time run in its own time zone', () => {
    expect(describeTiming({ type: 'ONE_TIME', runAt: '2026-12-01T12:00:00Z', timezone: 'Asia/Jerusalem' })).toBe(
      'Once at Dec 1, 2026 14:00 (Asia/Jerusalem)',
    );
  });

  it('describes intervals, with and without a start time', () => {
    expect(
      describeTiming({ type: 'INTERVAL', every: 15, unit: 'MINUTES', startAt: '2026-11-20T08:00:00Z', timezone: 'UTC' }),
    ).toBe('Every 15 minutes from Nov 20, 2026 08:00 (UTC)');
    expect(describeTiming({ type: 'INTERVAL', every: 2, unit: 'HOURS', startAt: null, timezone: 'UTC' })).toBe(
      'Every 2 hours (UTC)',
    );
  });

  it('describes weekly timings with sorted day names', () => {
    expect(describeTiming({ type: 'WEEKLY', days: ['WED', 'MON'], time: '09:00', timezone: 'Asia/Jerusalem' })).toBe(
      'Mon, Wed at 09:00 (Asia/Jerusalem)',
    );
  });

  it('describes cron expressions in words, and falls back to the raw expression', () => {
    expect(describeTiming({ type: 'CRON', expression: '0 0 9 ? * MON', timezone: 'UTC' })).toBe(
      'At 09:00, only on Monday (UTC)',
    );
    expect(describeTiming({ type: 'CRON', expression: 'not a cron', timezone: 'UTC' })).toBe('Cron not a cron (UTC)');
  });
});

describe('describeEvery', () => {
  it('uses singular for one unit', () => {
    expect(describeEvery(1, 'MINUTES')).toBe('Every minute');
    expect(describeEvery(1, 'HOURS')).toBe('Every hour');
    expect(describeEvery(30, 'MINUTES')).toBe('Every 30 minutes');
  });
});

describe('describeDays', () => {
  it('names common day sets', () => {
    expect(describeDays(['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'])).toBe('Every day');
    expect(describeDays(['FRI', 'THU', 'WED', 'TUE', 'MON'])).toBe('Weekdays');
    expect(describeDays(['SUN', 'SAT'])).toBe('Weekends');
    expect(describeDays(['SUN', 'MON'])).toBe('Mon, Sun');
  });
});

describe('describeCron', () => {
  it('reads Quartz syntax (seconds first, 1 = Sunday)', () => {
    expect(describeCron('0 0/15 * * * ?')).toBe('Every 15 minutes');
    expect(describeCron('0 0 9 ? * 2')).toBe('At 09:00, only on Monday');
    expect(describeCron('0 0 9 ? * MON-FRI')).toBe('At 09:00, Monday through Friday');
  });

  it('returns null for blank or unreadable expressions', () => {
    expect(describeCron('   ')).toBeNull();
    expect(describeCron('nonsense')).toBeNull();
  });
});
