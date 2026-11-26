import cronstrue from 'cronstrue';
import type { DayCode, IntervalUnit, Timing } from '../types/api';
import { formatDateTime } from './dates';
import { DAY_CODES, DAY_SHORT_LABEL, sortDays } from './days';

const WEEKDAYS: readonly DayCode[] = ['MON', 'TUE', 'WED', 'THU', 'FRI'];
const WEEKEND: readonly DayCode[] = ['SAT', 'SUN'];

/** "Every minute", "Every 15 minutes", "Every hour", "Every 2 hours". */
export function describeEvery(every: number, unit: IntervalUnit): string {
  const singular = unit === 'HOURS' ? 'hour' : 'minute';
  return every === 1 ? `Every ${singular}` : `Every ${every} ${singular}s`;
}

/** "Every day", "Weekdays", "Weekends", or "Mon, Wed". */
export function describeDays(days: readonly DayCode[]): string {
  const sorted = sortDays(days);
  const same = (set: readonly DayCode[]) => sorted.length === set.length && set.every((d) => sorted.includes(d));
  if (same(DAY_CODES)) {
    return 'Every day';
  }
  if (same(WEEKDAYS)) {
    return 'Weekdays';
  }
  if (same(WEEKEND)) {
    return 'Weekends';
  }
  return sorted.map((d) => DAY_SHORT_LABEL[d]).join(', ');
}

/**
 * Readable text for a Quartz cron expression ("At 09:00, only on Monday"), or null if it cannot be described.
 * Quartz numbers days of the week 1 (Sunday) to 7 (Saturday).
 */
export function describeCron(expression: string): string | null {
  const trimmed = expression.trim();
  if (!trimmed) {
    return null;
  }
  try {
    return cronstrue.toString(trimmed, {
      use24HourTimeFormat: true,
      dayOfWeekStartIndexZero: false,
      throwExceptionOnParseError: true,
      verbose: false,
    });
  } catch {
    return null;
  }
}

/** Timing as a sentence, without the time zone. */
export function describeTimingRule(timing: Timing): string {
  switch (timing.type) {
    case 'ONE_TIME':
      return `Once at ${formatDateTime(timing.runAt, timing.timezone)}`;
    case 'INTERVAL': {
      const every = describeEvery(timing.every, timing.unit);
      return timing.startAt ? `${every} from ${formatDateTime(timing.startAt, timing.timezone)}` : every;
    }
    case 'WEEKLY':
      return `${describeDays(timing.days)} at ${timing.time}`;
    case 'CRON':
      return describeCron(timing.expression) ?? `Cron ${timing.expression}`;
  }
}

/** Timing as shown in the table, e.g. "Mon, Wed at 09:00 (Asia/Jerusalem)". */
export function describeTiming(timing: Timing): string {
  return `${describeTimingRule(timing)} (${timing.timezone})`;
}
