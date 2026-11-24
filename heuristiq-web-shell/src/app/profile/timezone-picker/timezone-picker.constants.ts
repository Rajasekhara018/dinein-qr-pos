/** Most zones listed at once in the picker; typing narrows the list. */
export const TIMEZONE_PICKER_MAX_OPTIONS = 60;

/** How often the "Now:" preview refreshes, in milliseconds. */
export const TIMEZONE_PICKER_PREVIEW_REFRESH_MS = 30_000;

/** Angular date pattern of the preview, for example "18 Oct 2026, 10:30". */
export const TIMEZONE_PICKER_PREVIEW_FORMAT = 'd MMM yyyy, HH:mm';

/** A zone shown as an example in hints; passed to the translations as a parameter so it is never translated. */
export const TIMEZONE_PICKER_EXAMPLE = 'Asia/Kolkata';

/** Translation keys used by the picker. */
export const TIMEZONE_PICKER_KEYS = {
  invalid: 'timezone.picker.invalid',
  preview: 'timezone.picker.preview',
} as const;
