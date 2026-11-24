import { PUBLIC_TRACK_BACKOFF_INITIAL_MS, PUBLIC_TRACK_BACKOFF_MAX_MS } from './public-track.constants';
import {
  formatClockTime,
  isBeforePickup,
  nextBackoffMs,
  readShareToken,
  retryAfterMs,
  toMapView,
} from './public-track.helpers';

const TOKEN = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJ_-0123';

describe('public-track helpers', () => {
  it('reads a well-formed token from the fragment and rejects anything else', () => {
    expect(readShareToken(TOKEN)).toBe(TOKEN);
    expect(readShareToken(`#${TOKEN}`)).toBe(TOKEN);
    expect(readShareToken(` ${TOKEN} `)).toBe(TOKEN);
    expect(readShareToken('short')).toBeNull();
    expect(readShareToken('has spaces in the middle of it ok')).toBeNull();
    expect(readShareToken('<script>alert(1)</script>xxxxxxxx')).toBeNull();
    expect(readShareToken(null)).toBeNull();
    expect(readShareToken(undefined)).toBeNull();
  });

  it('doubles the back-off from the initial wait up to the cap', () => {
    expect(nextBackoffMs(0)).toBe(PUBLIC_TRACK_BACKOFF_INITIAL_MS);
    expect(nextBackoffMs(PUBLIC_TRACK_BACKOFF_INITIAL_MS)).toBe(PUBLIC_TRACK_BACKOFF_INITIAL_MS * 2);
    expect(nextBackoffMs(PUBLIC_TRACK_BACKOFF_MAX_MS)).toBe(PUBLIC_TRACK_BACKOFF_MAX_MS);
  });

  it('reads Retry-After seconds, capped, and ignores bad values', () => {
    expect(retryAfterMs('3')).toBe(3000);
    expect(retryAfterMs('9999')).toBe(PUBLIC_TRACK_BACKOFF_MAX_MS);
    expect(retryAfterMs('soon')).toBe(0);
    expect(retryAfterMs('-1')).toBe(0);
    expect(retryAfterMs(null)).toBe(0);
  });

  it('builds the map view and leaves out missing coordinates', () => {
    expect(toMapView({
      status: 'RIDE_STARTED', lat: 12.9, lng: 77.6, heading: 90,
      pickup: { lat: 12.8, lng: 77.5, label: 'Gate 1' }, drop: { lat: 13, lng: 77.7, label: '' },
    })).toEqual({ car: [12.9, 77.6], heading: 90, pickup: [12.8, 77.5], drop: [13, 77.7], pickupLabel: 'Gate 1', dropLabel: null });
    expect(toMapView({ status: 'DRIVER_ASSIGNED', lat: null, lng: 77, heading: Number.NaN, pickup: { label: 'x' }, drop: null }))
      .toEqual({ car: null, heading: null, pickup: null, drop: null, pickupLabel: 'x', dropLabel: null });
  });

  it('knows which statuses are before the pickup', () => {
    expect(isBeforePickup('driver_arriving')).toBe(true);
    expect(isBeforePickup('RIDE_STARTED')).toBe(false);
    expect(isBeforePickup(null)).toBe(false);
  });

  it('formats a clock time and returns empty for missing or bad dates', () => {
    expect(formatClockTime('2026-11-04T10:15:30Z', 'en')).toMatch(/\d{1,2}:\d{2}:\d{2}/);
    expect(formatClockTime('not a date', 'en')).toBe('');
    expect(formatClockTime(null, 'en')).toBe('');
    // an unusable language tag falls back to the browser's own format instead of throwing
    expect(formatClockTime('2026-11-04T10:15:30Z', '!!')).not.toBe('');
  });
});
