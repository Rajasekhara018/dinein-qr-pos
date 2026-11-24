/**
 * Patterns that bypass the user timezone when they display a date. Use `| hqDate` in templates and
 * HqTimezoneService (formatDateTime / formatLocale) in code instead; see docs/TIMEZONE_CONTRACT.md.
 * Each app's date-display guard spec scans its own sources with this list (plus the app's own allowlist).
 */
export const HQ_DATE_DISPLAY_FORBIDDEN: ReadonlyArray<{ name: string; pattern: RegExp }> = [
  { name: "the Angular 'date' pipe (use | hqDate)", pattern: /(?<!\|)\|\s*date\b/ },
  { name: 'DatePipe (use HqDatePipe or HqTimezoneService)', pattern: /\bDatePipe\b/ },
  { name: "formatDate from '@angular/common' (use HqTimezoneService.formatDateTime)", pattern: /import\s*\{[^}]*\bformatDate\b[^}]*\}\s*from\s*'@angular\/common'/ },
  { name: 'toLocaleDateString / toLocaleTimeString (use HqTimezoneService.formatLocale)', pattern: /\.toLocale(?:Date|Time)String\(/ },
  { name: 'toLocaleString with date options (use HqTimezoneService.formatLocale)', pattern: /\.toLocaleString\(\s*[^,)]+,\s*\{\s*(?:day|month|year|hour|dateStyle|timeStyle)/ },
  { name: 'new Date(...).toLocaleString (use HqTimezoneService.formatLocale)', pattern: /new Date\([^)]*\)\.toLocaleString\(/ },
];
