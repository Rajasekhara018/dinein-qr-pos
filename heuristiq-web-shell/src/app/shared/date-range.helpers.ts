import { HqDateRange, HqTimezoneService } from 'hq-shared-lib';

/** ISO calendar-day pattern used to compare a timestamp with a date range (the range's from / to are 'YYYY-MM-DD'). */
export const DAY_KEY_FORMAT = 'yyyy-MM-dd';

/**
 * Whether a timestamp falls inside a date range (both ends inclusive), compared as calendar days in the user's timezone.
 * No range, or an empty value, counts as inside, so a screen shows everything until a period is chosen.
 */
export function isWithinRange(value: string | null | undefined, range: HqDateRange | null, timezone: HqTimezoneService): boolean {
  if (!range || !value) return true;
  const day = timezone.formatDateTime(value, DAY_KEY_FORMAT);
  if (!day) return true;
  return (!range.from || day >= range.from) && (!range.to || day <= range.to);
}
