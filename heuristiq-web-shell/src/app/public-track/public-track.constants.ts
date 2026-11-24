/**
 * Public live-trip page (`/track#<token>`): an open route with no sign-in, read by anyone the rider shared the link
 * with (docs/RIDE_COMPLETION_SHARING_SUPPORT_CASH.md, decision 3 and section 7.4).
 */

/** Route path under the app root (no auth guard, like `vendor`); listed in shared/public-routes.constants.ts. */
export const PUBLIC_TRACK_ROUTE_PATH = 'track';

/**
 * Stable screen key (realboom-qa test case, route data). The page is anonymous, so it sends no audit screen-view
 * event: the audit service accepts only signed-in callers, and a viewer has no identity to record.
 */
export const PUBLIC_TRACK_SCREEN_KEY = 'public.track';

/** Ride service public read, appended to `environment.gatewayUrl` (`/api`). Plain JSON, no envelope. */
export const PUBLIC_TRACK_API_PATH = '/v1/ride/public/track';

/** Header carrying the share token (the token is never put in a URL path or query string). */
export const SHARE_TOKEN_HEADER = 'X-Share-Token';

/** Status the API answers once the trip is over (then 404 after the grace window). */
export const PUBLIC_TRACK_ENDED_STATUS = 'ENDED';

/** Normal refresh interval while the trip is live. */
export const PUBLIC_TRACK_POLL_INTERVAL_MS = 5000;

/** First wait after a 429 or a failed call; doubled on each further failure up to the maximum. */
export const PUBLIC_TRACK_BACKOFF_INITIAL_MS = 10000;
export const PUBLIC_TRACK_BACKOFF_MAX_MS = 60000;

/** Shape of a share token: base64url of 32 random bytes is 43 characters; a little slack either way. */
export const SHARE_TOKEN_PATTERN = /^[A-Za-z0-9_-]{16,256}$/;

/** HTTP statuses with a meaning of their own for this page. */
export const HTTP_STATUS_NOT_FOUND = 404;
export const HTTP_STATUS_TOO_MANY_REQUESTS = 429;

/** Map defaults: India-wide view until the first position arrives, then a street-level zoom. */
export const PUBLIC_TRACK_DEFAULT_CENTER: readonly [number, number] = [20.5937, 78.9629];
export const PUBLIC_TRACK_DEFAULT_ZOOM = 5;
export const PUBLIC_TRACK_FOCUS_ZOOM = 15;
/** Padding (px) when fitting the car, pickup and drop into view. */
export const PUBLIC_TRACK_FIT_PADDING = 48;

/** OpenStreetMap tiles (already allowed by the CSP img-src). The tile requests send the site origin only. */
export const PUBLIC_TRACK_TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
export const PUBLIC_TRACK_TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright" rel="noreferrer noopener" target="_blank">OpenStreetMap</a>';
export const PUBLIC_TRACK_TILE_MAX_ZOOM = 19;
/** OSM's tile policy asks for a Referer; the origin alone carries no path and never the `#token`. */
export const PUBLIC_TRACK_TILE_REFERRER_POLICY = 'strict-origin';

/** Marker colours (pickup green, drop red, car the theme blue). */
export const PUBLIC_TRACK_PICKUP_COLOR = '#16a34a';
export const PUBLIC_TRACK_DROP_COLOR = '#dc2626';
export const PUBLIC_TRACK_POINT_RADIUS = 8;

/** Meta tags set while the page is open (removed again when leaving it). */
export const PUBLIC_TRACK_META_TAGS: readonly { name: string; content: string }[] = [
  { name: 'robots', content: 'noindex, nofollow' },
  { name: 'referrer', content: 'no-referrer' },
];

/** Translation keys of the live ride statuses; any other code is shown as sent. */
export const PUBLIC_TRACK_STATUS_LABEL_KEYS: Record<string, string> = {
  DRIVER_ASSIGNED: 'publicTrack.status.driverAssigned',
  DRIVER_ACCEPTED: 'publicTrack.status.driverAssigned',
  DRIVER_ARRIVING: 'publicTrack.status.driverArriving',
  DRIVER_AT_PICKUP: 'publicTrack.status.driverAtPickup',
  RIDE_STARTED: 'publicTrack.status.rideStarted',
  EARLY_DROP_REQUESTED: 'publicTrack.status.rideStarted',
};

/** Statuses before the pickup: the ETA is to the pickup point; afterwards it is to the drop. */
export const PUBLIC_TRACK_PRE_PICKUP_STATUSES: readonly string[] = [
  'DRIVER_ASSIGNED',
  'DRIVER_ACCEPTED',
  'DRIVER_ARRIVING',
  'DRIVER_AT_PICKUP',
];
