import { HQ_DATE_TIME_GRID_CELLS, HqDateTimeMode } from './hq-date-time.constants';

/** A calendar day. `month` is 1-12 (not the 0-11 of the JavaScript Date), so the value reads the way it is written. */
export interface HqDateParts {
  year: number;
  month: number;
  day: number;
}

/** A time of day on the 24-hour clock. */
export interface HqTimeParts {
  hour: number;
  minute: number;
}

/** One cell of the month grid. */
export interface HqGridDay extends HqDateParts {
  /** Whether the day belongs to the month shown (the grid also shows the end of the month before and the start of the next). */
  inMonth: boolean;
  /** The day as `yyyy-MM-dd`. */
  iso: string;
}

const pad = (value: number, width = 2): string => String(value).padStart(width, '0');

/** Days in a month (month 1-12), leap years included. */
export function hqDaysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

/** Whether the parts are a real day: month 1-12, and a day that exists in that month and year. */
export function hqIsRealDate(parts: HqDateParts): boolean {
  const { year, month, day } = parts;
  return (
    Number.isInteger(year) && Number.isInteger(month) && Number.isInteger(day) &&
    year >= 1 && year <= 9999 && month >= 1 && month <= 12 && day >= 1 && day <= hqDaysInMonth(year, month)
  );
}

/** `yyyy-MM-dd` for a day. */
export function hqFormatIsoDate(parts: HqDateParts): string {
  return `${pad(parts.year, 4)}-${pad(parts.month)}-${pad(parts.day)}`;
}

/** The day in a `yyyy-MM-dd` text (what a native date input holds), null when it is empty or not a real day. */
export function hqParseIsoDate(text: string | null | undefined): HqDateParts | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec((text ?? '').trim());
  if (!match) return null;
  const parts = { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
  return hqIsRealDate(parts) ? parts : null;
}

/** `HH:mm` for a time. */
export function hqFormatIsoTime(parts: HqTimeParts): string {
  return `${pad(parts.hour)}:${pad(parts.minute)}`;
}

/** The time in a `HH:mm` or `HH:mm:ss` text (what a native time input holds), null when it is empty or not a real time. */
export function hqParseIsoTime(text: string | null | undefined): HqTimeParts | null {
  const match = /^(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/.exec((text ?? '').trim());
  if (!match) return null;
  return hqValidTime(Number(match[1]), Number(match[2]));
}

function hqValidTime(hour: number, minute: number): HqTimeParts | null {
  return Number.isInteger(hour) && Number.isInteger(minute) && hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59
    ? { hour, minute }
    : null;
}

/** `yyyy-MM-ddTHH:mm` for a day and a time. */
export function hqFormatIsoDateTime(date: HqDateParts, time: HqTimeParts): string {
  return `${hqFormatIsoDate(date)}T${hqFormatIsoTime(time)}`;
}

/** The day and time in a `yyyy-MM-ddTHH:mm` text (what a native datetime-local input holds); a space may stand in for the `T`. */
export function hqParseIsoDateTime(text: string | null | undefined): { date: HqDateParts; time: HqTimeParts } | null {
  const match = /^(\S+?)[T ](\S+)$/.exec((text ?? '').trim());
  if (!match) return null;
  const date = hqParseIsoDate(match[1]);
  const time = hqParseIsoTime(match[2]);
  return date && time ? { date, time } : null;
}

/** The day shown to the user as `dd-MM-yyyy`. */
export function hqFormatDisplayDate(parts: HqDateParts): string {
  return `${pad(parts.day)}-${pad(parts.month)}-${pad(parts.year, 4)}`;
}

/**
 * The day a user typed: day, month, year (`dd-mm-yyyy`, with `-`, `/`, `.` or a space between), or the ISO order
 * (`yyyy-mm-dd`). A one-digit day or month is accepted; a two-digit year is not (it would be a guess). Null when it is not a real day.
 */
export function hqParseTypedDate(text: string | null | undefined): HqDateParts | null {
  const value = (text ?? '').trim();
  const iso = /^(\d{4})[-/. ](\d{1,2})[-/. ](\d{1,2})$/.exec(value);
  if (iso) {
    const parts = { year: Number(iso[1]), month: Number(iso[2]), day: Number(iso[3]) };
    return hqIsRealDate(parts) ? parts : null;
  }
  const dmy = /^(\d{1,2})[-/. ](\d{1,2})[-/. ](\d{4})$/.exec(value);
  if (!dmy) return null;
  const parts = { year: Number(dmy[3]), month: Number(dmy[2]), day: Number(dmy[1]) };
  return hqIsRealDate(parts) ? parts : null;
}

/** The time a user typed: `HH:mm`, `H:mm` or `HHmm`. Null when it is not a real time. */
export function hqParseTypedTime(text: string | null | undefined): HqTimeParts | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec((text ?? '').trim()) ?? /^(\d{2})(\d{2})$/.exec((text ?? '').trim());
  return match ? hqValidTime(Number(match[1]), Number(match[2])) : null;
}

/** Order of two days: negative when `a` is earlier, 0 when they are the same day, positive when `a` is later. */
export function hqCompareDate(a: HqDateParts, b: HqDateParts): number {
  return a.year - b.year || a.month - b.month || a.day - b.day;
}

/** Whether a day lies outside the allowed range (`min` and `max` are inclusive; either may be missing). */
export function hqIsDayDisabled(day: HqDateParts, min: HqDateParts | null, max: HqDateParts | null): boolean {
  return (min !== null && hqCompareDate(day, min) < 0) || (max !== null && hqCompareDate(day, max) > 0);
}

/** The day moved into the allowed range: the nearest allowed day. */
export function hqClampDate(day: HqDateParts, min: HqDateParts | null, max: HqDateParts | null): HqDateParts {
  if (min !== null && hqCompareDate(day, min) < 0) return min;
  if (max !== null && hqCompareDate(day, max) > 0) return max;
  return day;
}

/** The month `delta` months away from a month (month 1-12), keeping the year right across January and December. */
export function hqAddMonths(year: number, month: number, delta: number): { year: number; month: number } {
  const index = year * 12 + (month - 1) + delta;
  return { year: Math.floor(index / 12), month: (((index % 12) + 12) % 12) + 1 };
}

/** The day `delta` days away. */
export function hqAddDays(day: HqDateParts, delta: number): HqDateParts {
  const moved = new Date(Date.UTC(day.year, day.month - 1, day.day + delta));
  return { year: moved.getUTCFullYear(), month: moved.getUTCMonth() + 1, day: moved.getUTCDate() };
}

/**
 * The six weeks (42 days) that show a month, starting on `weekStart` (0 = Sunday ... 6 = Saturday). Always six rows, so the
 * calendar keeps one height from month to month.
 */
export function hqMonthGrid(year: number, month: number, weekStart: number): HqGridDay[] {
  const first = new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
  const lead = (first - weekStart + 7) % 7;
  const cells: HqGridDay[] = [];
  for (let i = 0; i < HQ_DATE_TIME_GRID_CELLS; i++) {
    const day = hqAddDays({ year, month, day: 1 }, i - lead);
    cells.push({ ...day, inMonth: day.year === year && day.month === month, iso: hqFormatIsoDate(day) });
  }
  return cells;
}

/** Today in the browser's time zone. */
export function hqToday(now: Date = new Date()): HqDateParts {
  return { year: now.getFullYear(), month: now.getMonth() + 1, day: now.getDate() };
}

/** The current time of day in the browser's time zone, rounded down to the minute step. */
export function hqNowTime(now: Date = new Date(), minuteStep = 1): HqTimeParts {
  const step = hqNormalStep(minuteStep);
  return { hour: now.getHours(), minute: Math.floor(now.getMinutes() / step) * step };
}

/** A minute step that divides the hour (1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30 or 60); anything else is 1. */
export function hqNormalStep(step: number | null | undefined): number {
  return step && Number.isInteger(step) && step >= 1 && step <= 60 && 60 % step === 0 ? step : 1;
}

/** The minutes a minute column offers (0, step, 2*step ...). */
export function hqMinuteOptions(step: number | null | undefined): number[] {
  const size = hqNormalStep(step);
  const options: number[] = [];
  for (let minute = 0; minute < 60; minute += size) options.push(minute);
  return options;
}

/** The names of the seven weekdays in the language, starting on `weekStart`, from the browser's own locale data. */
export function hqWeekdayLabels(locale: string, weekStart: number): string[] {
  const format = new Intl.DateTimeFormat(locale, { weekday: 'short', timeZone: 'UTC' });
  // 2023-01-01 was a Sunday.
  return Array.from({ length: 7 }, (_, i) => format.format(new Date(Date.UTC(2023, 0, 1 + ((weekStart + i) % 7)))));
}

/** The name of a month (1-12) and year in the language, for the calendar's heading. */
export function hqMonthLabel(locale: string, year: number, month: number): string {
  return new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(Date.UTC(year, month - 1, 1)));
}

/** The day in the language's own long form, for a day button's accessible name. */
export function hqDayLabel(locale: string, day: HqDateParts): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'full', timeZone: 'UTC' }).format(new Date(Date.UTC(day.year, day.month - 1, day.day)));
}

/** Which kind of value a mode holds, from the tag a component is used as (`hq-time-input` is a time field). */
export function hqModeOfTag(tagName: string): HqDateTimeMode {
  const tag = tagName.toLowerCase();
  if (tag === 'hq-time-input') return 'time';
  if (tag === 'hq-datetime-input') return 'datetime';
  return 'date';
}
