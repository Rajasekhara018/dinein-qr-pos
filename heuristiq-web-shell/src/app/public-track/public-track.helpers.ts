import {
  PUBLIC_TRACK_BACKOFF_INITIAL_MS,
  PUBLIC_TRACK_BACKOFF_MAX_MS,
  PUBLIC_TRACK_PRE_PICKUP_STATUSES,
  SHARE_TOKEN_PATTERN,
} from './public-track.constants';
import { PublicTrackPoint, PublicTrackResponse, TrackLatLng, TrackMapView } from './public-track.models';

/** The share token from the URL fragment (`/track#<token>`), or null when there is none or it is malformed. */
export function readShareToken(fragment: string | null | undefined): string | null {
  const token = (fragment ?? '').replace(/^#/, '').trim();
  return SHARE_TOKEN_PATTERN.test(token) ? token : null;
}

/** Wait before the next try after a failure: starts at the initial back-off and doubles, capped. */
export function nextBackoffMs(previousMs: number): number {
  if (previousMs <= 0) return PUBLIC_TRACK_BACKOFF_INITIAL_MS;
  return Math.min(previousMs * 2, PUBLIC_TRACK_BACKOFF_MAX_MS);
}

/** A `Retry-After` header in seconds as milliseconds (capped at the maximum back-off); 0 when absent or unreadable. */
export function retryAfterMs(header: string | null | undefined): number {
  const seconds = Number(header);
  if (!header || !Number.isFinite(seconds) || seconds <= 0) return 0;
  return Math.min(seconds * 1000, PUBLIC_TRACK_BACKOFF_MAX_MS);
}

function isCoordinate(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function toLatLng(lat: unknown, lng: unknown): TrackLatLng | null {
  return isCoordinate(lat) && isCoordinate(lng) ? [lat, lng] : null;
}

function pointOf(point: PublicTrackPoint | null | undefined): TrackLatLng | null {
  return point ? toLatLng(point.lat, point.lng) : null;
}

/** What the map should draw for an API answer (missing coordinates are left out). */
export function toMapView(data: PublicTrackResponse): TrackMapView {
  return {
    car: toLatLng(data.lat, data.lng),
    heading: isCoordinate(data.heading) ? data.heading : null,
    pickup: pointOf(data.pickup),
    drop: pointOf(data.drop),
    pickupLabel: data.pickup?.label || null,
    dropLabel: data.drop?.label || null,
  };
}

/** True while the driver is still on the way to the pickup (ETA is to the pickup, not the drop). */
export function isBeforePickup(status: string | null | undefined): boolean {
  return PUBLIC_TRACK_PRE_PICKUP_STATUSES.includes((status ?? '').toUpperCase());
}

/**
 * A clock time (hours, minutes, seconds) in the viewer's language and the viewer's own (browser) timezone: the page is
 * anonymous, so there is no user or organization timezone to apply. '' when the value is not a date.
 */
export function formatClockTime(iso: string | null | undefined, language: string): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const options: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit', second: '2-digit' };
  try {
    return new Intl.DateTimeFormat(language, options).format(date);
  } catch {
    // an unusable language tag: the browser's default locale instead
    return new Intl.DateTimeFormat(undefined, options).format(date);
  }
}
