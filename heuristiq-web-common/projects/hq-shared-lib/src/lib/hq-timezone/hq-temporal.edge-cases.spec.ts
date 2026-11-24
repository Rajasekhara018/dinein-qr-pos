import { TestBed } from '@angular/core/testing';
import { HQ_FALLBACK_TIMEZONE, HQ_FORMATTER_CACHE_LIMIT } from './hq-timezone.constants';
import {
  canonicalTimezone,
  detectBrowserTimezone,
  formatTemporal,
  formatTemporalLocale,
  normalizeOffsetTimezone,
  parseTemporal,
  temporalKindOf,
  timezoneOffsetMinutes,
} from './hq-temporal.helpers';
import { HqTimezoneService } from './hq-timezone.service';

const BROWSER = detectBrowserTimezone();
const allZones = (): string[] =>
  (Intl as unknown as { supportedValuesOf: (k: string) => string[] }).supportedValuesOf('timeZone');

describe('parseTemporal edge cases', () => {
  it('reads ISO moments with the minutes or seconds left out', () => {
    expect(parseTemporal('2026-10-18T10Z')).toEqual({ kind: 'moment', instant: new Date('2026-10-18T10:00:00Z') });
    expect(parseTemporal('2026-10-18T10:30Z')).toEqual({ kind: 'moment', instant: new Date('2026-10-18T10:30:00Z') });
  });

  it('gives null for an ISO moment outside the range a Date can hold', () => {
    expect(parseTemporal('999999999-01-01T00:00:00Z')).toBeNull();
  });

  it('reads compact ISO dates as date-only and an hour-only time as wall-clock', () => {
    expect(parseTemporal('2026-1018')).toEqual({
      kind: 'date-only',
      wall: { year: 2026, month: 10, day: 18, hour: 0, minute: 0, second: 0, millisecond: 0 },
    });
    expect(parseTemporal('2026-10-18T07')).toEqual({
      kind: 'wall-clock',
      wall: { year: 2026, month: 10, day: 18, hour: 7, minute: 0, second: 0, millisecond: 0 },
    });
  });

  it('gives null for a numeric text that is not a usable epoch', () => {
    expect(parseTemporal('1e20')).toBeNull();
  });

  it('reads other date texts: with a zone hint as a moment, without one as a wall-clock reading', () => {
    const withHint = parseTemporal('Oct 18, 2026 10:00:00 GMT');
    expect(withHint).toEqual({ kind: 'moment', instant: new Date('2026-10-18T10:00:00Z') });
    expect(parseTemporal('Oct 18, 2026 10:30:15')).toEqual({
      kind: 'wall-clock',
      wall: { year: 2026, month: 10, day: 18, hour: 10, minute: 30, second: 15, millisecond: 0 },
    });
  });

  it('gives null (and no kind) for text that is not a date', () => {
    expect(parseTemporal('definitely not a date')).toBeNull();
    expect(temporalKindOf('definitely not a date')).toBeNull();
  });
});

describe('timezone helper edge cases', () => {
  afterEach(() => vi.restoreAllMocks());

  it('reports no browser timezone when the runtime gives none or fails', () => {
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({ timeZone: '' } as Intl.ResolvedDateTimeFormatOptions);
    expect(detectBrowserTimezone()).toBeNull();
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockImplementation(() => { throw new Error('no Intl'); });
    expect(detectBrowserTimezone()).toBeNull();
  });

  it('rejects an offset text it cannot normalize', () => {
    expect(normalizeOffsetTimezone('Asia/Kolkata')).toBeNull();
    expect(normalizeOffsetTimezone(' +05:30 ')).toBe('+0530');
  });

  it('keeps answering correctly after more zones than the caches hold', () => {
    const zones = allZones();
    expect(zones.length).toBeGreaterThan(HQ_FORMATTER_CACHE_LIMIT);
    const instant = new Date('2026-01-15T12:00:00Z');
    for (const zone of zones) {
      canonicalTimezone(zone);
      timezoneOffsetMinutes(instant, zone);
    }
    expect(canonicalTimezone('UTC')).toBe('UTC');
    expect(timezoneOffsetMinutes(instant, 'Asia/Kolkata')).toBe(330);
    expect(timezoneOffsetMinutes(instant, 'America/New_York')).toBe(-300);
  });

  it('formats a wall-clock value in the browser zone with the local-date toLocale call', () => {
    const text = formatTemporalLocale('2026-10-18T10:00:00', 'datetime', undefined, 'en-US', null, BROWSER);
    expect(text).toBe(new Date(2026, 9, 18, 10, 0, 0).toLocaleString('en-US'));
  });

  it('formats a parsed text moment in another zone', () => {
    expect(formatTemporal('Oct 18, 2026 10:00:00 GMT', 'HH:mm', 'en-US', 'Asia/Kolkata', 'UTC')).toBe('15:30');
  });
});

describe('HqTimezoneService edge cases', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  function fresh(): HqTimezoneService {
    TestBed.resetTestingModule();
    return TestBed.inject(HqTimezoneService);
  }

  it('falls back to the platform timezone when the browser reports none', () => {
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({ timeZone: '' } as Intl.ResolvedDateTimeFormatOptions);
    const service = fresh();
    vi.restoreAllMocks();
    expect(service.browserTimezone).toBeNull();
    expect(service.effectiveTimezone()).toBe(HQ_FALLBACK_TIMEZONE);
    expect(service.effectiveTimezoneFor(null)).toBe(HQ_FALLBACK_TIMEZONE);
    expect(service.effectiveTimezoneFor('Asia/Dubai')).toBe('Asia/Dubai');
  });

  it('builds the zone list once and reuses it', () => {
    const service = fresh();
    expect(service.supportedTimezones()).toBe(service.supportedTimezones());
  });

  it('still lists the curated zones when the browser cannot list its own', () => {
    const intl = Intl as unknown as { supportedValuesOf?: (k: string) => string[] };
    const original = intl.supportedValuesOf;
    try {
      intl.supportedValuesOf = undefined;
      const without = fresh().supportedTimezones();
      expect(without).toContain('UTC');
      expect(without).toContain('Asia/Kolkata');

      intl.supportedValuesOf = () => { throw new Error('unsupported'); };
      const failing = fresh().supportedTimezones();
      expect(failing).toEqual(without);
    } finally {
      intl.supportedValuesOf = original;
    }
  });

  it('describes the effective timezone when no zone is given', () => {
    const service = fresh();
    service.setUserTimezone('Asia/Kolkata');
    const at = new Date('2026-10-18T10:00:00Z');
    expect(service.describe(null, at)).toBe('Asia/Kolkata (UTC+05:30)');
    expect(service.describe(undefined, at)).toBe('Asia/Kolkata (UTC+05:30)');
  });
});
