import { MEMBER_ERROR_KEYS, MEMBER_FALLBACK_ERROR_KEY } from './franchise-members.constants';

/** The translation key of the message for a failed member list, from the HTTP status of the answer. */
export function memberErrorKey(status: number | undefined): string {
  return (status !== undefined && MEMBER_ERROR_KEYS[status]) || MEMBER_FALLBACK_ERROR_KEY;
}

/** The yyyy-MM-dd day of an ISO instant (UTC); empty when there is none. */
export function dayOf(instant: string | null | undefined, zone?: string | null): string {
  if (!instant) {
    return '';
  }
  const date = new Date(instant);
  if (zone && !Number.isNaN(date.getTime())) {
    try {
      // en-CA writes yyyy-MM-dd; the day is the reader's day in the zone, not the UTC day (which lags India by five and a half hours)
      return new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
    } catch {
      // an unknown zone: fall back to the UTC day below
    }
  }
  return instant.slice(0, 10);
}

/**
 * The drivers' share of one page of the combined members table, where the agents come first and the drivers after them.
 * {@code offset} is the first driver on the page, {@code count} how many drivers fit, and {@code pages} the driver pages (of
 * the same size) to read for them. With no room for drivers, page 0 is still read so the table knows how many there are.
 */
export interface DriverWindow {
  offset: number;
  count: number;
  pages: number[];
}

export function driverWindow(page: number, size: number, memberTotal: number): DriverWindow {
  const start = page * size;
  const offset = Math.max(0, start - memberTotal);
  const count = Math.max(0, start + size - Math.max(start, memberTotal));
  if (count === 0) {
    return { offset, count, pages: [0] };
  }
  const first = Math.floor(offset / size);
  const last = Math.floor((offset + count - 1) / size);
  return { offset, count, pages: first === last ? [first] : [first, last] };
}

/** The drivers of the window, out of the driver pages read for it (in the order of {@code window.pages}). */
export function driversInWindow<T>(window: DriverWindow, size: number, pages: readonly (readonly T[])[]): T[] {
  if (window.count === 0) {
    return [];
  }
  const from = window.offset - window.pages[0] * size;
  return pages.flat().slice(from, from + window.count);
}
