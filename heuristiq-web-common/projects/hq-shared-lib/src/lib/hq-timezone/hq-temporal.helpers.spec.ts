import {
  describeTimezone,
  detectBrowserTimezone,
  formatOffsetCompact,
  formatOffsetLabel,
  formatTemporal,
  formatTemporalLocale,
  isOffsetTimezone,
  isSameTimezone,
  isValidIanaTimezone,
  parseTemporal,
  temporalKindOf,
  timezoneOffsetMinutes,
} from './hq-temporal.helpers';

const LOCALE = 'en-US';
const BROWSER = detectBrowserTimezone();

function show(value: unknown, format: string, zone: string | null): string | null {
  return formatTemporal(value, format, LOCALE, zone, BROWSER);
}

describe('parseTemporal / temporalKindOf', () => {
  it('classifies moments', () => {
    expect(temporalKindOf('2026-10-18T10:00:00Z')).toBe('moment');
    expect(temporalKindOf('2026-10-18T10:00:00.123Z')).toBe('moment');
    expect(temporalKindOf('2026-10-18T10:00:00+05:30')).toBe('moment');
    expect(temporalKindOf('2026-10-18T10:00:00-0400')).toBe('moment');
    expect(temporalKindOf(1760781600000)).toBe('moment');
    expect(temporalKindOf('1760781600000')).toBe('moment');
    expect(temporalKindOf(new Date(1760781600000))).toBe('moment');
  });

  it('classifies zone-less date-times as wall-clock', () => {
    expect(temporalKindOf('2026-10-18T10:00:00')).toBe('wall-clock');
    expect(temporalKindOf('2026-10-18T10:00')).toBe('wall-clock');
    expect(temporalKindOf('2026-10-18T10:00:00.250')).toBe('wall-clock');
  });

  it('classifies calendar dates as date-only', () => {
    expect(temporalKindOf('2026-10-18')).toBe('date-only');
    expect(temporalKindOf('2026-10')).toBe('date-only');
    expect(temporalKindOf('2026')).toBe('date-only');
  });

  it('returns null for empty and invalid values instead of throwing', () => {
    for (const bad of [null, undefined, '', '   ', NaN, new Date(NaN), 'garbage', {}, [], true]) {
      expect(parseTemporal(bad)).toBeNull();
    }
  });

  it('keeps the written fields of a wall-clock value', () => {
    const parsed = parseTemporal('2026-10-18T23:45:10.5');
    expect(parsed?.kind).toBe('wall-clock');
    if (parsed && parsed.kind !== 'moment') {
      expect(parsed.wall).toEqual({ year: 2026, month: 10, day: 18, hour: 23, minute: 45, second: 10, millisecond: 500 });
    }
  });
});

describe('timezone helpers', () => {
  it('validates named zones only', () => {
    expect(isValidIanaTimezone('Asia/Kolkata')).toBe(true);
    expect(isValidIanaTimezone('UTC')).toBe(true);
    expect(isValidIanaTimezone('Mars/Phobos')).toBe(false);
    expect(isValidIanaTimezone('')).toBe(false);
    expect(isValidIanaTimezone('+05:30')).toBe(false);
    expect(isValidIanaTimezone(null)).toBe(false);
    expect(isValidIanaTimezone(42)).toBe(false);
  });

  it('treats alias spellings as one zone', () => {
    expect(isSameTimezone('Asia/Kolkata', 'Asia/Calcutta')).toBe(true);
    expect(isSameTimezone('Asia/Kolkata', 'Asia/Dubai')).toBe(false);
    expect(isSameTimezone(null, 'Asia/Dubai')).toBe(false);
  });

  it('recognises numeric offsets', () => {
    expect(isOffsetTimezone('+0530')).toBe(true);
    expect(isOffsetTimezone('-04:00')).toBe(true);
    expect(isOffsetTimezone('UTC+05:30')).toBe(true);
    expect(isOffsetTimezone('Asia/Kolkata')).toBe(false);
  });

  it('computes the offset of a zone at an instant, honouring daylight saving', () => {
    const winter = new Date('2026-01-15T12:00:00Z');
    const summer = new Date('2026-07-15T12:00:00Z');
    expect(timezoneOffsetMinutes(winter, 'Asia/Kolkata')).toBe(330);
    expect(timezoneOffsetMinutes(summer, 'Asia/Kolkata')).toBe(330);
    expect(timezoneOffsetMinutes(winter, 'America/New_York')).toBe(-300);
    expect(timezoneOffsetMinutes(summer, 'America/New_York')).toBe(-240);
    expect(timezoneOffsetMinutes(winter, 'Europe/London')).toBe(0);
    expect(timezoneOffsetMinutes(summer, 'Europe/London')).toBe(60);
    expect(timezoneOffsetMinutes(summer, 'Asia/Dubai')).toBe(240);
    expect(timezoneOffsetMinutes(summer, 'Asia/Kathmandu')).toBe(345);
    expect(timezoneOffsetMinutes(summer, 'Pacific/Kiritimati')).toBe(840);
    expect(timezoneOffsetMinutes(summer, 'Pacific/Pago_Pago')).toBe(-660);
    expect(timezoneOffsetMinutes(summer, 'UTC')).toBe(0);
    expect(timezoneOffsetMinutes(summer, 'Mars/Phobos')).toBeNull();
  });

  it('formats offsets', () => {
    expect(formatOffsetCompact(330)).toBe('+0530');
    expect(formatOffsetCompact(-240)).toBe('-0400');
    expect(formatOffsetCompact(0)).toBe('+0000');
    expect(formatOffsetLabel(330)).toBe('UTC+05:30');
    expect(formatOffsetLabel(-570)).toBe('UTC-09:30');
    expect(describeTimezone('Asia/Kolkata', new Date('2026-10-18T00:00:00Z'))).toBe('Asia/Kolkata (UTC+05:30)');
    expect(describeTimezone('Mars/Phobos')).toBe('Mars/Phobos');
  });
});

describe('formatTemporal in explicit zones', () => {
  const FORMAT = 'yyyy-MM-dd HH:mm:ss';

  it('converts a moment to Asia/Kolkata', () => {
    expect(show('2026-10-18T10:00:00Z', FORMAT, 'Asia/Kolkata')).toBe('2026-10-18 15:30:00');
    expect(show('2026-10-18T10:00:00+05:30', FORMAT, 'Asia/Kolkata')).toBe('2026-10-18 10:00:00');
    expect(show(Date.UTC(2026, 9, 18, 10), FORMAT, 'Asia/Kolkata')).toBe('2026-10-18 15:30:00');
    expect(show(new Date('2026-10-18T10:00:00Z'), FORMAT, 'Asia/Kolkata')).toBe('2026-10-18 15:30:00');
  });

  it('converts across the New York daylight saving changes', () => {
    expect(show('2026-03-08T06:59:59Z', FORMAT, 'America/New_York')).toBe('2026-03-08 01:59:59');
    expect(show('2026-03-08T07:00:00Z', FORMAT, 'America/New_York')).toBe('2026-03-08 03:00:00');
    expect(show('2026-11-01T05:59:59Z', FORMAT, 'America/New_York')).toBe('2026-11-01 01:59:59');
    expect(show('2026-11-01T06:00:00Z', FORMAT, 'America/New_York')).toBe('2026-11-01 01:00:00');
  });

  it('converts to Dubai, London (summer and winter) and UTC', () => {
    expect(show('2026-10-18T10:00:00Z', FORMAT, 'Asia/Dubai')).toBe('2026-10-18 14:00:00');
    expect(show('2026-10-24T23:30:00Z', FORMAT, 'Europe/London')).toBe('2026-10-25 00:30:00');
    expect(show('2026-10-25T00:59:59Z', FORMAT, 'Europe/London')).toBe('2026-10-25 01:59:59');
    expect(show('2026-10-25T01:00:00Z', FORMAT, 'Europe/London')).toBe('2026-10-25 01:00:00');
    expect(show('2026-12-31T23:30:00+05:30', FORMAT, 'UTC')).toBe('2026-12-31 18:00:00');
  });

  it('moves the calendar day with the zone', () => {
    expect(show('2026-10-18T22:00:00Z', 'yyyy-MM-dd', 'Asia/Kolkata')).toBe('2026-10-19');
    expect(show('2026-10-18T02:00:00Z', 'yyyy-MM-dd', 'America/New_York')).toBe('2026-10-17');
  });

  it('accepts a numeric offset as the zone', () => {
    expect(show('2026-10-18T10:00:00Z', FORMAT, '+0530')).toBe('2026-10-18 15:30:00');
    expect(show('2026-10-18T10:00:00Z', FORMAT, '-04:00')).toBe('2026-10-18 06:00:00');
  });

  it('shows named formats in the zone', () => {
    expect(show('2026-10-18T10:00:00Z', 'shortTime', 'Asia/Kolkata')?.replace(/ /g, ' ')).toBe('3:30 PM');
    expect(show('2026-10-18T10:00:00Z', 'mediumDate', 'Asia/Kolkata')).toBe('Oct 18, 2026');
  });

  it('shows wall-clock values as written in every zone', () => {
    for (const zone of ['Asia/Kolkata', 'America/New_York', 'Pacific/Kiritimati', 'Pacific/Pago_Pago', 'UTC', 'Asia/Dubai']) {
      expect(show('2026-10-18T10:00:00', FORMAT, zone), zone).toBe('2026-10-18 10:00:00');
      expect(show('2026-12-31T23:59:59', FORMAT, zone), zone).toBe('2026-12-31 23:59:59');
    }
  });

  it('shows a wall-clock value that does not exist in New York (the spring-forward hour) as written', () => {
    expect(show('2026-03-08T02:30:00', FORMAT, 'Pacific/Kiritimati')).toBe('2026-03-08 02:30:00');
  });

  it('never shifts a date-only value, in any zone', () => {
    const zones = ['Pacific/Kiritimati', 'Pacific/Pago_Pago', 'Asia/Kolkata', 'America/New_York', 'Europe/London', 'UTC', 'Pacific/Auckland', 'America/Anchorage'];
    for (const zone of zones) {
      expect(show('2026-10-18', 'yyyy-MM-dd', zone), zone).toBe('2026-10-18');
      expect(show('2026-01-01', 'dd MMM yyyy', zone), zone).toBe('01 Jan 2026');
      expect(show('2026-12-31', 'EEEE, MMMM d, y', zone), zone).toBe('Thursday, December 31, 2026');
      expect(show('2000-02-29', 'mediumDate', zone), zone).toBe('Feb 29, 2000');
    }
  });

  it('gives null for empty and invalid values in any zone', () => {
    for (const bad of [null, undefined, '', 'garbage', NaN]) {
      expect(show(bad, FORMAT, 'Asia/Kolkata')).toBeNull();
    }
  });

  it('falls back to the browser zone when the zone is unusable', () => {
    expect(show('2026-10-18T10:00:00Z', FORMAT, 'Mars/Phobos')).toBe(show('2026-10-18T10:00:00Z', FORMAT, null));
  });
});

describe('formatTemporalLocale', () => {
  it('matches toLocale* output when the zone is the browser zone', () => {
    const iso = '2026-10-18T10:00:00Z';
    const options: Intl.DateTimeFormatOptions = { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' };
    expect(formatTemporalLocale(iso, 'datetime', options, 'en-IN', null, BROWSER)).toBe(new Date(iso).toLocaleString('en-IN', options));
    expect(formatTemporalLocale(iso, 'date', options, 'en-IN', BROWSER, BROWSER)).toBe(new Date(iso).toLocaleDateString('en-IN', options));
    expect(formatTemporalLocale(iso, 'time', undefined, 'en-IN', null, BROWSER)).toBe(new Date(iso).toLocaleTimeString('en-IN'));
    expect(formatTemporalLocale(1760781600000, 'datetime', undefined, undefined, null, BROWSER)).toBe(new Date(1760781600000).toLocaleString());
  });

  it('uses the zone for moments and shows wall-clock and date-only values as written', () => {
    const options: Intl.DateTimeFormatOptions = { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false };
    expect(formatTemporalLocale('2026-10-18T10:00:00Z', 'datetime', options, 'en-GB', 'Asia/Kolkata', 'UTC')).toBe('18/10/2026, 15:30');
    expect(formatTemporalLocale('2026-10-18T10:00:00', 'datetime', options, 'en-GB', 'Asia/Kolkata', 'UTC')).toBe('18/10/2026, 10:00');
    expect(formatTemporalLocale('2026-10-18', 'date', { day: '2-digit', month: '2-digit', year: 'numeric' }, 'en-GB', 'Pacific/Pago_Pago', 'UTC')).toBe('18/10/2026');
  });

  it('gives an empty text for empty and invalid values', () => {
    expect(formatTemporalLocale(null, 'datetime', undefined, undefined, null, BROWSER)).toBe('');
    expect(formatTemporalLocale('garbage', 'date', undefined, undefined, null, BROWSER)).toBe('');
  });
});
