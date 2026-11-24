/** A preset period of a dashboard, or a custom one. */
export type HqDateRangePreset = 'today' | 'last7Days' | 'last30Days' | 'lastMonth' | 'currentMonth' | 'currentYear' | 'custom';

/** A period: the preset it came from and its first and last day (ISO dates YYYY-MM-DD, both inclusive). */
export interface HqDateRange {
  preset: HqDateRangePreset;
  from: string;
  to: string;
}

/** The x-axis granularity a period wants. */
export type HqDateRangeBucket = 'day' | 'week' | 'month';

/** The presets a range picker offers when the screen does not choose its own. */
export const HQ_DATE_RANGE_DEFAULT_PRESETS: readonly HqDateRangePreset[] = ['last7Days', 'lastMonth', 'currentMonth', 'currentYear'];

/** The period a range picker starts with when the screen gives none. */
export const HQ_DATE_RANGE_DEFAULT_PRESET: HqDateRangePreset = 'currentMonth';

/** Longest period, in days, still drawn per day; and per week. Longer ones are drawn per month. */
export const HQ_DATE_RANGE_DAY_LIMIT = 31;
export const HQ_DATE_RANGE_WEEK_LIMIT = 92;

const MS_PER_DAY = 86_400_000;

/** YYYY-MM-DD of a calendar date (read from the Date's local fields). */
export function hqRangeIsoDay(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** The calendar date of an ISO day (local fields, midnight); null when it is not a valid YYYY-MM-DD. */
export function hqRangeDay(iso: string | null | undefined): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '');
  if (!match) {
    return null;
  }
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return Number.isNaN(date.getTime()) || hqRangeIsoDay(date) !== iso ? null : date;
}

/** Today's calendar date in an IANA timezone (falls back to the browser's day when the zone is unknown). */
export function hqTodayInZone(zone: string | null | undefined, now: Date = new Date()): Date {
  if (zone) {
    try {
      const parts = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
      const parsed = hqRangeDay(parts);
      if (parsed) {
        return parsed;
      }
    } catch {
      // An unknown zone falls through to the browser's own day.
    }
  }
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/**
 * The period of a preset, counted from `today` (its calendar fields; pass the day in the user's timezone):
 * today = today..today; last7Days = today-6..today; last30Days = today-29..today; lastMonth = the whole previous calendar month;
 * currentMonth = the 1st of this month..today; currentYear = 1 January..today. `custom` has no period of its own and returns today.
 */
export function hqDateRangeFor(preset: HqDateRangePreset, today: Date = new Date()): HqDateRange {
  const day = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const range = (from: Date, to: Date): HqDateRange => ({ preset, from: hqRangeIsoDay(from), to: hqRangeIsoDay(to) });
  switch (preset) {
    case 'last7Days':
      return range(addDays(day, -6), day);
    case 'last30Days':
      return range(addDays(day, -29), day);
    case 'lastMonth':
      return range(new Date(day.getFullYear(), day.getMonth() - 1, 1), new Date(day.getFullYear(), day.getMonth(), 0));
    case 'currentMonth':
      return range(new Date(day.getFullYear(), day.getMonth(), 1), day);
    case 'currentYear':
      return range(new Date(day.getFullYear(), 0, 1), day);
    case 'today':
    case 'custom':
    default:
      return range(day, day);
  }
}

/** Days in a period, both ends included (0 when it cannot be read or ends before it starts). */
export function hqDateRangeDays(range: HqDateRange | null | undefined): number {
  const from = hqRangeDay(range?.from);
  const to = hqRangeDay(range?.to);
  if (!from || !to || to < from) {
    return 0;
  }
  // Rounded, so a daylight-saving change inside the period does not lose a day.
  return Math.round((to.getTime() - from.getTime()) / MS_PER_DAY) + 1;
}

/** The x-axis granularity for a period: up to 31 days per day, up to 92 per week, longer per month. */
export function hqDateRangeBucket(range: HqDateRange | null | undefined): HqDateRangeBucket {
  const days = hqDateRangeDays(range);
  if (days <= HQ_DATE_RANGE_DAY_LIMIT) {
    return 'day';
  }
  return days <= HQ_DATE_RANGE_WEEK_LIMIT ? 'week' : 'month';
}

/** True when both are the same period. */
export function hqSameDateRange(a: HqDateRange | null | undefined, b: HqDateRange | null | undefined): boolean {
  return !!a && !!b && a.preset === b.preset && a.from === b.from && a.to === b.to;
}
