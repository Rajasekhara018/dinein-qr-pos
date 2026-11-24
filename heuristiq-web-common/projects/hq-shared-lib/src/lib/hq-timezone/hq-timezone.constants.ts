/** Timezone used only when the browser reports none at all (the platform default region). */
export const HQ_FALLBACK_TIMEZONE = 'Asia/Kolkata';

/** Format used when a caller gives none; the same default as Angular's DatePipe. */
export const HQ_DEFAULT_DATE_FORMAT = 'mediumDate';

/** localStorage key of the last known user timezone. A convenience for the first paint only, never the source of truth. */
export const HQ_USER_TIMEZONE_STORAGE_KEY = 'rou.timezone.user';

/** Locale used for the internal offset calculation; the result is numeric and does not depend on it. */
export const HQ_OFFSET_PROBE_LOCALE = 'en-US';

/** Matches a numeric UTC offset such as +0530, +05:30, -0400 or UTC+05:30. */
export const HQ_OFFSET_TIMEZONE_PATTERN = /^(?:UTC|GMT)?([+-])(\d{2}):?(\d{2})$/;

/** Upper bound for cached Intl formatters (there are far fewer than this many zones in practice). */
export const HQ_FORMATTER_CACHE_LIMIT = 200;

/**
 * Zones offered when the runtime cannot list them (Intl.supportedValuesOf is missing). Covers every region the
 * platform serves plus the common business zones; the list is sorted by IANA name.
 */
export const HQ_FALLBACK_TIMEZONES: readonly string[] = [
  'Africa/Cairo',
  'Africa/Johannesburg',
  'Africa/Lagos',
  'Africa/Nairobi',
  'America/Anchorage',
  'America/Argentina/Buenos_Aires',
  'America/Bogota',
  'America/Chicago',
  'America/Denver',
  'America/Halifax',
  'America/Los_Angeles',
  'America/Mexico_City',
  'America/New_York',
  'America/Phoenix',
  'America/Sao_Paulo',
  'America/Toronto',
  'America/Vancouver',
  'Asia/Baghdad',
  'Asia/Bangkok',
  'Asia/Colombo',
  'Asia/Dhaka',
  'Asia/Dubai',
  'Asia/Hong_Kong',
  'Asia/Jakarta',
  'Asia/Karachi',
  'Asia/Kathmandu',
  'Asia/Kolkata',
  'Asia/Kuala_Lumpur',
  'Asia/Manila',
  'Asia/Muscat',
  'Asia/Qatar',
  'Asia/Riyadh',
  'Asia/Seoul',
  'Asia/Shanghai',
  'Asia/Singapore',
  'Asia/Tehran',
  'Asia/Tokyo',
  'Atlantic/Reykjavik',
  'Australia/Adelaide',
  'Australia/Brisbane',
  'Australia/Melbourne',
  'Australia/Perth',
  'Australia/Sydney',
  'Europe/Amsterdam',
  'Europe/Athens',
  'Europe/Berlin',
  'Europe/Dublin',
  'Europe/Istanbul',
  'Europe/Lisbon',
  'Europe/London',
  'Europe/Madrid',
  'Europe/Moscow',
  'Europe/Paris',
  'Europe/Rome',
  'Europe/Zurich',
  'Pacific/Auckland',
  'Pacific/Honolulu',
  'Pacific/Kiritimati',
  'Pacific/Pago_Pago',
  'UTC',
];
