/**
 * Patterns that bypass the user timezone when they display a date (shared list from hq-shared-lib) and this
 * app's justified exceptions; see docs/TIMEZONE_CONTRACT.md.
 */
export { HQ_DATE_DISPLAY_FORBIDDEN as DATE_DISPLAY_FORBIDDEN } from 'hq-shared-lib';

/** Justified exceptions: file path (relative to the app root, forward slashes) mapped to the reason it is allowed. */
export const DATE_DISPLAY_ALLOWLIST: Readonly<Record<string, string>> = {
  'src/app/payments/payment-invoice/payment-invoice.ts':
    "Prints the sale time with a fixed ' IST' suffix; the zone is part of the invoice text, so it must stay in IST. Needs a product decision.",
};
