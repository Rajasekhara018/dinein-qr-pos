/** IST helpers. All user-facing times are shown in Asia/Kolkata (UTC+05:30, no DST). */

export const IST_TIMEZONE = 'Asia/Kolkata';
/** Offset string for Angular's DatePipe. */
export const IST_OFFSET = '+0530';

/** Formats a backend `LocalTime` (`HH:mm` or `HH:mm:ss`, already IST) as e.g. `11:30 am`. */
export function formatLocalTime(value: string | null | undefined): string {
  if (!value) return '';
  const [h, m] = value.split(':').map(Number);
  if (!Number.isFinite(h) || !Number.isFinite(m)) return value;
  const date = new Date(Date.UTC(2000, 0, 1, h, m));
  return new Intl.DateTimeFormat('en-IN', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
    timeZone: 'UTC',
  }).format(date);
}

/** `11:00 am – 11:00 pm`, or '' when hours are not configured. */
export function formatOpeningHours(opening?: string | null, closing?: string | null): string {
  if (!opening || !closing) return '';
  return `${formatLocalTime(opening)} – ${formatLocalTime(closing)}`;
}
