import type { DayCode } from '../types/api';

/** Monday first, matching the order used in the UI and by the backend. */
export const DAY_CODES: readonly DayCode[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

export const DAY_SHORT_LABEL: Record<DayCode, string> = {
  MON: 'Mon',
  TUE: 'Tue',
  WED: 'Wed',
  THU: 'Thu',
  FRI: 'Fri',
  SAT: 'Sat',
  SUN: 'Sun',
};

export const DAY_LONG_LABEL: Record<DayCode, string> = {
  MON: 'Monday',
  TUE: 'Tuesday',
  WED: 'Wednesday',
  THU: 'Thursday',
  FRI: 'Friday',
  SAT: 'Saturday',
  SUN: 'Sunday',
};

/** Removes duplicates and sorts Monday → Sunday. */
export function sortDays(days: readonly DayCode[]): DayCode[] {
  return DAY_CODES.filter((code) => days.includes(code));
}
