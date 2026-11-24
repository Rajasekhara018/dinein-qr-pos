import { formatDate } from '@angular/common';
import {
  HQ_FORMATTER_CACHE_LIMIT,
  HQ_OFFSET_PROBE_LOCALE,
  HQ_OFFSET_TIMEZONE_PATTERN,
} from './hq-timezone.constants';

/*
 * Pure date/time display logic, with no Angular injection, so it can be tested on its own.
 *
 * Three kinds of temporal value reach the screens, and each is displayed differently:
 *  - moment     a point in time: a Date, an epoch number, or a string with 'Z' or a numeric offset. Converted to the
 *               display timezone.
 *  - wall-clock a date and time with no offset ('2026-10-18T10:00:00', from a backend LocalDateTime). It is a clock
 *               reading, not a point in time, so it is shown as written and never converted.
 *  - date-only  a calendar date ('2026-10-18'). Never shifted by any timezone.
 *
 * When the display timezone is the browser's own, the value is handed to Angular's formatDate untouched, so the text is
 * exactly what DatePipe produces today.
 */

/** Same pattern Angular's own parser uses, so classification and formatting agree on what a string means. */
const ISO8601_DATE_REGEX =
  /^(\d{4,})-?(\d\d)-?(\d\d)(?:T(\d\d)(?::?(\d\d)(?::?(\d\d)(?:\.(\d+))?)?)?(Z|([+-])(\d\d):?(\d\d))?)?$/;
const YEAR_MONTH_DAY_REGEX = /^(\d{4}(-\d{1,2}(-\d{1,2})?)?)$/;
const OFFSET_HINT_REGEX = /(?:Z|GMT|UTC|[+-]\d{2}:?\d{2})\s*(?:\([^)]*\))?$/i;
const NAMED_ZONE_REJECT_REGEX = /^[+-]\d/;

/** A clock reading: the fields exactly as written, month 1 to 12. */
export interface HqWallClock {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
  millisecond: number;
}

/** The kinds of temporal value; see the file comment. */
export type HqTemporalKind = 'moment' | 'wall-clock' | 'date-only';

/** A classified value. Only a moment carries an instant; the other kinds carry the fields as written. */
export type HqTemporal =
  | { kind: 'moment'; instant: Date }
  | { kind: 'wall-clock' | 'date-only'; wall: HqWallClock };

/** Which Intl method a toLocale-style call used. */
export type HqLocaleKind = 'datetime' | 'date' | 'time';

const formatterCache = new Map<string, Intl.DateTimeFormat>();
const canonicalCache = new Map<string, string>();

function isValidDate(value: unknown): value is Date {
  return value instanceof Date && !isNaN(value.valueOf());
}

/** Date fields in UTC: used to carry a clock reading through a UTC-only formatter without shifting it. */
export function wallClockToUtcDate(wall: HqWallClock): Date {
  const date = new Date(0);
  date.setUTCFullYear(wall.year, wall.month - 1, wall.day);
  date.setUTCHours(wall.hour, wall.minute, wall.second, wall.millisecond);
  return date;
}

/** Date fields in the browser's own zone: what `new Date('2026-10-18T10:00:00')` yields. */
export function wallClockToLocalDate(wall: HqWallClock): Date {
  const date = new Date(0);
  date.setFullYear(wall.year, wall.month - 1, wall.day);
  date.setHours(wall.hour, wall.minute, wall.second, wall.millisecond);
  return date;
}

function wallFromLocalDate(date: Date): HqWallClock {
  return {
    year: date.getFullYear(),
    month: date.getMonth() + 1,
    day: date.getDate(),
    hour: date.getHours(),
    minute: date.getMinutes(),
    second: date.getSeconds(),
    millisecond: date.getMilliseconds(),
  };
}

function parseIsoString(match: RegExpMatchArray): HqTemporal | null {
  const hasTime = match[4] !== undefined;
  if (match[8]) {
    const instant = new Date(0);
    const tzHour = match[9] ? Number(match[9] + match[10]) : 0;
    const tzMinute = match[9] ? Number(match[9] + match[11]) : 0;
    instant.setUTCFullYear(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
    instant.setUTCHours(
      Number(match[4] || 0) - tzHour,
      Number(match[5] || 0) - tzMinute,
      Number(match[6] || 0),
      Math.floor(parseFloat('0.' + (match[7] || 0)) * 1000),
    );
    return isValidDate(instant) ? { kind: 'moment', instant } : null;
  }
  const wall: HqWallClock = {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
    hour: Number(match[4] || 0),
    minute: Number(match[5] || 0),
    second: Number(match[6] || 0),
    millisecond: Math.floor(parseFloat('0.' + (match[7] || 0)) * 1000),
  };
  return { kind: hasTime ? 'wall-clock' : 'date-only', wall };
}

/**
 * Classifies a value the way a display must treat it. Returns null for null, undefined, '', NaN and anything that is not
 * a date, so callers show nothing instead of throwing.
 */
export function parseTemporal(value: unknown): HqTemporal | null {
  if (value instanceof Date) {
    return isValidDate(value) ? { kind: 'moment', instant: value } : null;
  }
  if (typeof value === 'number') {
    const instant = new Date(value);
    return isValidDate(instant) ? { kind: 'moment', instant } : null;
  }
  if (typeof value !== 'string') {
    return null;
  }
  const text = value.trim();
  if (!text) {
    return null;
  }
  if (YEAR_MONTH_DAY_REGEX.test(text)) {
    const [year, month = 1, day = 1] = text.split('-').map((part) => +part);
    return { kind: 'date-only', wall: { year, month, day, hour: 0, minute: 0, second: 0, millisecond: 0 } };
  }
  const numeric = parseFloat(text);
  if (!isNaN(Number(text) - numeric)) {
    const instant = new Date(numeric);
    return isValidDate(instant) ? { kind: 'moment', instant } : null;
  }
  const iso = text.match(ISO8601_DATE_REGEX);
  if (iso) {
    return parseIsoString(iso);
  }
  const parsed = new Date(text);
  if (!isValidDate(parsed)) {
    return null;
  }
  return OFFSET_HINT_REGEX.test(text)
    ? { kind: 'moment', instant: parsed }
    : { kind: 'wall-clock', wall: wallFromLocalDate(parsed) };
}

/** The kind of a value, or null when it is empty or not a date. */
export function temporalKindOf(value: unknown): HqTemporalKind | null {
  return parseTemporal(value)?.kind ?? null;
}

/** The browser's timezone, or null when the browser reports none. */
export function detectBrowserTimezone(): string | null {
  try {
    const zone = new Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (zone) {
      return zone;
    }
  } catch {
    /* fall through to the fallback */
  }
  return null;
}

/** True for a named IANA zone the runtime knows. Numeric offsets and non-strings are not named zones. */
export function isValidIanaTimezone(zone: unknown): zone is string {
  if (typeof zone !== 'string') {
    return false;
  }
  const trimmed = zone.trim();
  if (!trimmed || NAMED_ZONE_REJECT_REGEX.test(trimmed)) {
    return false;
  }
  try {
    new Intl.DateTimeFormat(HQ_OFFSET_PROBE_LOCALE, { timeZone: trimmed });
    return true;
  } catch {
    return false;
  }
}

/** The runtime's canonical spelling of a valid zone (Asia/Kolkata may be 'Asia/Calcutta'), or null when it is not valid. */
export function canonicalTimezone(zone: unknown): string | null {
  if (!isValidIanaTimezone(zone)) {
    return null;
  }
  const trimmed = zone.trim();
  let canonical = canonicalCache.get(trimmed);
  if (!canonical) {
    if (canonicalCache.size >= HQ_FORMATTER_CACHE_LIMIT) {
      canonicalCache.clear();
    }
    canonical = new Intl.DateTimeFormat(HQ_OFFSET_PROBE_LOCALE, { timeZone: trimmed }).resolvedOptions().timeZone;
    canonicalCache.set(trimmed, canonical);
  }
  return canonical;
}

/** The zone as the person chose it (trimmed), or null when it is not a valid named zone. */
export function validTimezoneOrNull(zone: unknown): string | null {
  return isValidIanaTimezone(zone) ? zone.trim() : null;
}

/** True when two zone names are the same zone, whatever their spelling ('Asia/Kolkata' and 'Asia/Calcutta'). */
export function isSameTimezone(first: string | null | undefined, second: string | null | undefined): boolean {
  if (!first || !second) {
    return false;
  }
  return first === second || (canonicalTimezone(first) !== null && canonicalTimezone(first) === canonicalTimezone(second));
}

/** True for a numeric offset such as '+0530', '-04:00' or 'UTC+05:30'. */
export function isOffsetTimezone(zone: unknown): zone is string {
  return typeof zone === 'string' && HQ_OFFSET_TIMEZONE_PATTERN.test(zone.trim());
}

/** '+0530' for any accepted offset spelling, or null. */
export function normalizeOffsetTimezone(zone: string): string | null {
  const match = zone.trim().match(HQ_OFFSET_TIMEZONE_PATTERN);
  return match ? `${match[1]}${match[2]}${match[3]}` : null;
}

function offsetFormatter(zone: string): Intl.DateTimeFormat {
  let formatter = formatterCache.get(zone);
  if (!formatter) {
    if (formatterCache.size >= HQ_FORMATTER_CACHE_LIMIT) {
      formatterCache.clear();
    }
    formatter = new Intl.DateTimeFormat(HQ_OFFSET_PROBE_LOCALE, {
      timeZone: zone,
      hourCycle: 'h23',
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      second: 'numeric',
    });
    formatterCache.set(zone, formatter);
  }
  return formatter;
}

/**
 * Minutes east of UTC for a zone at one instant (+330 for Asia/Kolkata, -240 for New York in summer), or null when the
 * zone is not valid. Computed per instant, so daylight saving changes are honoured.
 */
export function timezoneOffsetMinutes(instant: Date, zone: string): number | null {
  if (!isValidDate(instant) || !isValidIanaTimezone(zone)) {
    return null;
  }
  const fields: Record<string, number> = {};
  for (const part of offsetFormatter(zone.trim()).formatToParts(instant)) {
    if (part.type !== 'literal') {
      fields[part.type] = Number(part.value);
    }
  }
  const shown = new Date(0);
  shown.setUTCFullYear(fields['year'], fields['month'] - 1, fields['day']);
  shown.setUTCHours(fields['hour'] % 24, fields['minute'], fields['second'], 0);
  const wholeSecond = Math.floor(instant.getTime() / 1000) * 1000;
  return Math.round((shown.getTime() - wholeSecond) / 60000);
}

/** '+0530' style offset, the form Angular's formatDate accepts as a timezone. */
export function formatOffsetCompact(offsetMinutes: number): string {
  const sign = offsetMinutes < 0 ? '-' : '+';
  const absolute = Math.abs(offsetMinutes);
  const hours = String(Math.floor(absolute / 60)).padStart(2, '0');
  const minutes = String(absolute % 60).padStart(2, '0');
  return `${sign}${hours}${minutes}`;
}

/** 'UTC+05:30' style label. */
export function formatOffsetLabel(offsetMinutes: number): string {
  const compact = formatOffsetCompact(offsetMinutes);
  return `UTC${compact.slice(0, 3)}:${compact.slice(3)}`;
}

/** 'Asia/Kolkata (UTC+05:30)' for a zone at one instant (now by default), or just the name when the zone is unknown. */
export function describeTimezone(zone: string, at: Date = new Date()): string {
  const offset = timezoneOffsetMinutes(at, zone);
  return offset === null ? zone : `${zone} (${formatOffsetLabel(offset)})`;
}

function isBrowserZone(zone: string | null | undefined, browserTimezone: string | null): boolean {
  return !zone || isSameTimezone(zone, browserTimezone);
}

/**
 * Formats a value like Angular's DatePipe, but in `zone` (an IANA name or a numeric offset) when that differs from the
 * browser's. Returns null for empty or non-date values. An invalid format string throws, as DatePipe does.
 */
export function formatTemporal(
  value: unknown,
  format: string,
  locale: string,
  zone: string | null | undefined,
  browserTimezone: string | null,
): string | null {
  const parsed = parseTemporal(value);
  if (!parsed) {
    return null;
  }
  if (isBrowserZone(zone, browserTimezone)) {
    return formatDate(value as string | number | Date, format, locale);
  }
  if (parsed.kind !== 'moment') {
    return formatDate(wallClockToUtcDate(parsed.wall), format, locale, 'UTC');
  }
  let offset: string | null = null;
  if (isOffsetTimezone(zone)) {
    offset = normalizeOffsetTimezone(zone);
  } else {
    const minutes = typeof zone === 'string' ? timezoneOffsetMinutes(parsed.instant, zone) : null;
    offset = minutes === null ? null : formatOffsetCompact(minutes);
  }
  return offset === null
    ? formatDate(parsed.instant, format, locale)
    : formatDate(parsed.instant, format, locale, offset);
}

/**
 * The toLocaleString / toLocaleDateString / toLocaleTimeString equivalent. In the browser's own zone it calls the same
 * Intl method on the same Date those calls use; in another zone it adds the timeZone option (moments) or formats the
 * fields as written in UTC (wall-clock and date-only values). Returns '' for empty or non-date values.
 */
export function formatTemporalLocale(
  value: unknown,
  kind: HqLocaleKind,
  options: Intl.DateTimeFormatOptions | undefined,
  locale: string | string[] | undefined,
  zone: string | null | undefined,
  browserTimezone: string | null,
): string {
  const parsed = parseTemporal(value);
  if (!parsed) {
    return '';
  }
  let date: Date;
  let resolved: Intl.DateTimeFormatOptions | undefined = options;
  if (isBrowserZone(zone, browserTimezone) || !isValidIanaTimezone(zone)) {
    date = parsed.kind === 'moment' ? parsed.instant : wallClockToLocalDate(parsed.wall);
  } else if (parsed.kind === 'moment') {
    date = parsed.instant;
    resolved = { ...options, timeZone: zone };
  } else {
    date = wallClockToUtcDate(parsed.wall);
    resolved = { ...options, timeZone: 'UTC' };
  }
  switch (kind) {
    case 'date':
      return date.toLocaleDateString(locale, resolved);
    case 'time':
      return date.toLocaleTimeString(locale, resolved);
    default:
      return date.toLocaleString(locale, resolved);
  }
}

let cachedBrowserTimezone: string | null | undefined;

function browserTimezoneOnce(): string | null {
  if (cachedBrowserTimezone === undefined) {
    cachedBrowserTimezone = detectBrowserTimezone();
  }
  return cachedBrowserTimezone;
}

/**
 * formatTemporal against the browser's own zone, for plain functions that have no service. `zone` is the effective
 * timezone (HqTimezoneService.effectiveTimezone()); without it the browser's zone is used. '' for empty or invalid values.
 */
export function formatDateInZone(value: unknown, format: string, locale: string, zone?: string | null): string {
  return formatTemporal(value, format, locale, zone, browserTimezoneOnce()) ?? '';
}

/** formatTemporalLocale against the browser's own zone, for plain functions that have no service. */
export function formatLocaleInZone(
  value: unknown,
  kind: HqLocaleKind,
  options: Intl.DateTimeFormatOptions | undefined,
  locale: string | string[] | undefined,
  zone?: string | null,
): string {
  return formatTemporalLocale(value, kind, options, locale, zone, browserTimezoneOnce());
}
