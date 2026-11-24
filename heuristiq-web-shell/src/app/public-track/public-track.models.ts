/** A pickup or drop point of the shared trip. */
export interface PublicTrackPoint {
  lat?: number | null;
  lng?: number | null;
  label?: string | null;
}

/**
 * `GET /api/v1/ride/public/track` (contract section 7.4). Unknown values are left out by the server; after the trip it
 * is only `{status: 'ENDED'}`.
 */
export interface PublicTrackResponse {
  status: string;
  driverFirstName?: string | null;
  driverPhotoUrl?: string | null;
  vehicleModel?: string | null;
  vehicleNumber?: string | null;
  vehicleType?: string | null;
  lat?: number | null;
  lng?: number | null;
  heading?: number | null;
  locationUpdatedAt?: string | null;
  pickup?: PublicTrackPoint | null;
  drop?: PublicTrackPoint | null;
  etaMinutes?: number | null;
}

/** What the page shows. */
export type PublicTrackState = 'loading' | 'live' | 'ended' | 'invalid' | 'unavailable';

/** One step of the polling loop. */
export type PublicTrackUpdate =
  | { kind: 'live'; data: PublicTrackResponse }
  | { kind: 'ended' }
  | { kind: 'invalid' }
  /** A failed read (network, 5xx or 429); `retryInMs` is the wait before the next try. */
  | { kind: 'unavailable'; retryInMs: number; rateLimited: boolean };

/** A map point as [lat, lng]. */
export type TrackLatLng = readonly [number, number];

/** What the map draws. */
export interface TrackMapView {
  car: TrackLatLng | null;
  heading: number | null;
  pickup: TrackLatLng | null;
  drop: TrackLatLng | null;
  pickupLabel: string | null;
  dropLabel: string | null;
}

/** The map as the page uses it (Leaflet behind it, loaded on demand). */
export interface PublicTrackMap {
  update(view: TrackMapView, fit: boolean): void;
  invalidateSize(): void;
  destroy(): void;
}
