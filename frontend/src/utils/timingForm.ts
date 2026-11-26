import type { DayCode, IntervalUnit, Timing, TimingType } from '../types/api';
import dayjs, { type Dayjs } from './dayjs';
import { sortDays } from './days';

/**
 * Form model of the timing editor. Date/time pickers edit <b>wall-clock</b> values ("09:00 on Dec 1") that are
 * interpreted in the selected time zone only when the request is built. Changing the time zone therefore keeps
 * "09:00" and moves the instant, which is what a user expects.
 */
export interface TimingFormValues {
  type: TimingType;
  /** ONE_TIME: wall-clock date and time in `timezone`. */
  runAt: Dayjs | null;
  /** INTERVAL: kept as text while editing. */
  every: string;
  unit: IntervalUnit;
  /** INTERVAL: optional wall-clock start; null = start now. */
  startAt: Dayjs | null;
  /** WEEKLY */
  days: DayCode[];
  /** WEEKLY: wall-clock time of day. */
  time: Dayjs | null;
  /** CRON (Quartz syntax) */
  expression: string;
  timezone: string;
}

// Same limits as the backend's TimingValidator.
export const MAX_INTERVAL = 10_000;
export const MAX_CRON_LENGTH = 120;
export const TIMING_PREFIX = 'timing.';

export const CRON_EXAMPLES: ReadonlyArray<{ expression: string; label: string }> = [
  { expression: '0 0 9 ? * MON-FRI', label: 'Weekdays at 09:00' },
  { expression: '0 0/15 * * * ?', label: 'Every 15 minutes' },
  { expression: '0 30 18 ? * FRI', label: 'Fridays at 18:30' },
  { expression: '0 0 0 1 * ?', label: 'First day of the month' },
  { expression: '0 0 12 L * ?', label: 'Last day of the month at noon' },
];

const WALL_CLOCK_FORMAT = 'YYYY-MM-DDTHH:mm:ss';

export function isValidTimezone(timezone: string): boolean {
  if (!timezone.trim()) {
    return false;
  }
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: timezone.trim() });
    return true;
  } catch {
    return false;
  }
}

/** Instant → wall-clock value in the time zone (what the pickers show). */
export function toWallClock(iso: string, timezone: string): Dayjs {
  return dayjs(dayjs(iso).tz(timezone).format(WALL_CLOCK_FORMAT));
}

/** Wall-clock value in the time zone → ISO instant (what the API stores). */
export function fromWallClock(value: Dayjs, timezone: string): string {
  return dayjs.tz(value.format(WALL_CLOCK_FORMAT), timezone).toISOString();
}

function nextFullHour(timezone: string, now: Dayjs): Dayjs {
  const zone = isValidTimezone(timezone) ? timezone : 'UTC';
  return dayjs(now.tz(zone).format('YYYY-MM-DDTHH:00:00')).add(1, 'hour');
}

/** New scheduling: "every 15 minutes, starting now"; the other tabs start with sensible values. */
export function defaultTimingValues(timezone: string, now: Dayjs = dayjs()): TimingFormValues {
  return {
    type: 'INTERVAL',
    runAt: nextFullHour(timezone, now),
    every: '15',
    unit: 'MINUTES',
    startAt: null,
    days: [],
    time: dayjs('09:00', 'HH:mm'),
    expression: '',
    timezone,
  };
}

/** Existing timing → form values (only the timing's own type is filled from it). */
export function timingToForm(timing: Timing, now: Dayjs = dayjs()): TimingFormValues {
  const base = defaultTimingValues(timing.timezone, now);
  switch (timing.type) {
    case 'ONE_TIME':
      return { ...base, type: 'ONE_TIME', runAt: toWallClock(timing.runAt, timing.timezone) };
    case 'INTERVAL':
      return {
        ...base,
        type: 'INTERVAL',
        every: String(timing.every),
        unit: timing.unit,
        startAt: timing.startAt ? toWallClock(timing.startAt, timing.timezone) : null,
      };
    case 'WEEKLY':
      return { ...base, type: 'WEEKLY', days: sortDays(timing.days), time: dayjs(timing.time, 'HH:mm') };
    case 'CRON':
      return { ...base, type: 'CRON', expression: timing.expression };
  }
}

function requireValue<T>(value: T | null, field: string): T {
  if (value == null) {
    throw new Error(`${field} is required`);
  }
  return value;
}

/** Form values (already validated) → API timing. */
export function formToTiming(values: TimingFormValues): Timing {
  const timezone = values.timezone.trim();
  switch (values.type) {
    case 'ONE_TIME':
      return { type: 'ONE_TIME', runAt: fromWallClock(requireValue(values.runAt, 'runAt'), timezone), timezone };
    case 'INTERVAL':
      return {
        type: 'INTERVAL',
        every: Number(values.every.trim()),
        unit: values.unit,
        startAt: values.startAt ? fromWallClock(values.startAt, timezone) : null,
        timezone,
      };
    case 'WEEKLY':
      return {
        type: 'WEEKLY',
        days: sortDays(values.days),
        time: requireValue(values.time, 'time').format('HH:mm'),
        timezone,
      };
    case 'CRON':
      return { type: 'CRON', expression: values.expression.trim().replace(/\s+/g, ' '), timezone };
  }
}

export interface TimingValidationOptions {
  now?: Dayjs;
  /** When editing a one-time scheduling: its saved run time, which may stay unchanged even if it has passed. */
  originalRunAt?: string | null;
}

/** Client-side copy of the backend's timing rules; errors keyed "timing.<field>" like the API. */
export function validateTiming(values: TimingFormValues, options: TimingValidationOptions = {}): Record<string, string> {
  const errors: Record<string, string> = {};
  const now = options.now ?? dayjs();
  const timezone = (values.timezone ?? '').trim();
  let zoneValid = true;
  if (!timezone) {
    errors[`${TIMING_PREFIX}timezone`] = 'is required';
    zoneValid = false;
  } else if (!isValidTimezone(timezone)) {
    errors[`${TIMING_PREFIX}timezone`] = 'is not a valid timezone';
    zoneValid = false;
  }

  switch (values.type) {
    case 'ONE_TIME': {
      if (!values.runAt || !values.runAt.isValid()) {
        errors[`${TIMING_PREFIX}runAt`] = 'is required';
      } else if (zoneValid) {
        const runAt = dayjs(fromWallClock(values.runAt, timezone));
        const unchanged = !!options.originalRunAt && runAt.isSame(dayjs(options.originalRunAt));
        if (!unchanged && !runAt.isAfter(now)) {
          errors[`${TIMING_PREFIX}runAt`] = 'must be in the future';
        }
      }
      break;
    }
    case 'INTERVAL': {
      const text = values.every.trim();
      const every = Number(text);
      if (!text) {
        errors[`${TIMING_PREFIX}every`] = 'is required';
      } else if (!/^\d+$/.test(text) || every < 1 || every > MAX_INTERVAL) {
        errors[`${TIMING_PREFIX}every`] = `must be between 1 and ${MAX_INTERVAL}`;
      }
      if (!values.unit) {
        errors[`${TIMING_PREFIX}unit`] = 'is required';
      }
      if (values.startAt && !values.startAt.isValid()) {
        errors[`${TIMING_PREFIX}startAt`] = 'is not a valid date';
      }
      break;
    }
    case 'WEEKLY':
      if (values.days.length === 0) {
        errors[`${TIMING_PREFIX}days`] = 'select at least one day';
      }
      if (!values.time || !values.time.isValid()) {
        errors[`${TIMING_PREFIX}time`] = 'is required';
      }
      break;
    case 'CRON': {
      const expression = values.expression.trim();
      const fields = expression.split(/\s+/).length;
      if (!expression) {
        errors[`${TIMING_PREFIX}expression`] = 'is required';
      } else if (expression.length > MAX_CRON_LENGTH) {
        errors[`${TIMING_PREFIX}expression`] = `must be at most ${MAX_CRON_LENGTH} characters`;
      } else if (fields < 6 || fields > 7) {
        errors[`${TIMING_PREFIX}expression`] =
          'must be a Quartz cron expression with 6 or 7 fields (seconds first), e.g. 0 0 9 ? * MON';
      }
      break;
    }
  }
  return errors;
}

/** Which timing fields the editor shows for a type (server errors for other paths go to the form banner). */
export function timingFieldsOf(type: TimingType): string[] {
  const own: Record<TimingType, string[]> = {
    ONE_TIME: ['runAt'],
    INTERVAL: ['every', 'unit', 'startAt'],
    WEEKLY: ['days', 'time'],
    CRON: ['expression'],
  };
  return [...own[type], 'timezone'].map((field) => TIMING_PREFIX + field);
}
