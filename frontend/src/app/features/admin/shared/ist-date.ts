import { IsoLocalDate } from '../../../core/api/models';
import { IST_TIMEZONE } from '../../../core/util/time';

const IST_DATE = new Intl.DateTimeFormat('en-CA', {
  timeZone: IST_TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** The IST business date (`YYYY-MM-DD`) of an instant. */
export function istDate(now: Date = new Date()): IsoLocalDate {
  // en-CA formats as YYYY-MM-DD.
  return IST_DATE.format(now);
}

/** Adds (or subtracts) whole days to a `YYYY-MM-DD` date (calendar arithmetic, no time zone involved). */
export function addDays(date: IsoLocalDate, days: number): IsoLocalDate {
  const [y, m, d] = date.split('-').map(Number);
  const utc = new Date(Date.UTC(y, m - 1, d + days));
  return utc.toISOString().slice(0, 10);
}

/** Whole days from `from` to `to` (inclusive count = result + 1). */
export function daysBetween(from: IsoLocalDate, to: IsoLocalDate): number {
  const toUtc = (s: string) => {
    const [y, m, d] = s.split('-').map(Number);
    return Date.UTC(y, m - 1, d);
  };
  return Math.round((toUtc(to) - toUtc(from)) / 86_400_000);
}

/** `2026-09-27` → `27 Sep 2026` (or `27 Sep` with `short`). */
export function formatIsoDate(date: IsoLocalDate, short = false): string {
  const [y, m, d] = date.split('-').map(Number);
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    ...(short ? {} : { year: 'numeric' }),
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(y, m - 1, d)));
}
