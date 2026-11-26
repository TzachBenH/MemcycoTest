import dayjs from './dayjs';

export const DATE_TIME_FORMAT = 'MMM D, YYYY HH:mm';
export const DATE_TIME_SECONDS_FORMAT = 'MMM D, YYYY HH:mm:ss';
export const TIME_SECONDS_FORMAT = 'HH:mm:ss';

/** The browser's IANA time zone, e.g. "Asia/Jerusalem" (the default for new schedulings). */
export function browserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

/** Formats an instant; in the given time zone when one is provided, otherwise in the browser's. */
export function formatDateTime(
  iso: string | null | undefined,
  timezone?: string,
  format: string = DATE_TIME_FORMAT,
): string {
  if (!iso) {
    return '—';
  }
  const value = dayjs(iso);
  if (!value.isValid()) {
    return '—';
  }
  if (timezone) {
    try {
      return value.tz(timezone).format(format);
    } catch {
      // Unknown time zone: fall back to the browser's.
    }
  }
  return value.format(format);
}

/** "in 5 minutes", "2 hours ago". */
export function formatRelative(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) {
    return '—';
  }
  const value = dayjs(iso);
  return value.isValid() ? value.from(dayjs(now)) : '—';
}

/** Human-readable duration: "350 ms", "2.4 s", "1 min 5 s", "2 h 10 min". */
export function formatDuration(ms: number | null | undefined): string {
  if (ms == null || !Number.isFinite(ms) || ms < 0) {
    return '—';
  }
  if (ms < 1000) {
    return `${Math.round(ms)} ms`;
  }
  const seconds = ms / 1000;
  if (seconds < 60) {
    return `${seconds < 10 ? seconds.toFixed(1) : Math.floor(seconds)} s`;
  }
  const totalSeconds = Math.floor(seconds);
  const minutes = Math.floor(totalSeconds / 60);
  if (minutes < 60) {
    const rest = totalSeconds % 60;
    return rest ? `${minutes} min ${rest} s` : `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  return restMinutes ? `${hours} h ${restMinutes} min` : `${hours} h`;
}
