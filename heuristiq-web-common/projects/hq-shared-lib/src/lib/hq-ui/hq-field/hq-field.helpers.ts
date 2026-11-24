import { HQ_FIELD_DEFAULT_ROWS, HQ_FIELD_TYPES, HqFieldType } from './hq-field.constants';

/** What a field holds: text, a number (type "number") or nothing. */
export type HqFieldValue = string | number | null;

/** Whether `type` is one of the field types the component draws; anything else is drawn as plain text. */
export function hqFieldKnownType(type: string): type is HqFieldType {
  return (HQ_FIELD_TYPES as readonly string[]).includes(type);
}

/** The type to draw: the requested one, or "text" for one the component does not know. */
export function hqFieldResolveType(type: string | null | undefined): HqFieldType {
  return type && hqFieldKnownType(type) ? type : 'text';
}

/** The `inputmode` that brings up the right on-screen keyboard, null when the type's own keyboard is already right. */
export function hqFieldInputMode(type: HqFieldType): string | null {
  switch (type) {
    case 'email':
      return 'email';
    case 'tel':
      return 'tel';
    case 'url':
      return 'url';
    case 'search':
      return 'search';
    case 'number':
      return 'decimal';
    default:
      return null;
  }
}

/**
 * The browser's autofill hint: only what the screen asked for. Nothing is guessed, so a sign-in field keeps the browser's own
 * autofill and password manager; a screen that creates a password passes "new-password" itself.
 */
export function hqFieldAutocomplete(requested: string): string | null {
  return requested || null;
}

/**
 * The value a field reports for what the user typed: a number for "number" (null when empty or not a finite number),
 * the text itself for every other type.
 */
export function hqFieldParse(type: HqFieldType, raw: string): HqFieldValue {
  if (type !== 'number') return raw;
  if (raw.trim() === '') return null;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

/** The text to show for a value (empty for none). */
export function hqFieldText(value: HqFieldValue | undefined): string {
  return value === null || value === undefined ? '' : String(value);
}

/** Whether the "n / max" counter is shown: when asked for, or by default for a text or textarea field that has a limit. */
export function hqFieldCounterShown(type: HqFieldType, maxLength: number | null, showCounter: boolean | null): boolean {
  if (!maxLength || maxLength <= 0) return false;
  if (showCounter !== null) return showCounter;
  return type === 'text' || type === 'textarea';
}

/** Rows of a textarea: the requested number, or the default when it is missing or not a positive whole number. */
export function hqFieldRows(rows: number | null | undefined): number {
  return rows && Number.isInteger(rows) && rows > 0 ? rows : HQ_FIELD_DEFAULT_ROWS;
}

/**
 * The kind of date-time field a type is drawn as: "date" -> a day, "time" -> a time of day, "datetime-local" -> both; null for
 * every other type (those are drawn as an ordinary input).
 */
export function hqFieldDateMode(type: HqFieldType): 'date' | 'time' | 'datetime' | null {
  switch (type) {
    case 'date':
      return 'date';
    case 'time':
      return 'time';
    case 'datetime-local':
      return 'datetime';
    default:
      return null;
  }
}

/** A min or max for a date-time field: only text (`yyyy-MM-dd`) can be one; a number (a number field's limit) cannot. */
export function hqFieldDateLimit(limit: number | string | null | undefined): string | null {
  return typeof limit === 'string' && limit !== '' ? limit : null;
}

/** Today in the browser's own time zone as `YYYY-MM-DD`, the form of a date field's value and of its limits. */
export function hqFieldToday(now: Date = new Date()): string {
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** The later of two date limits (`YYYY-MM-DD`, or a date-time whose date part is compared); a null one is ignored. */
export function hqFieldLaterDate(a: string | null, b: string | null): string | null {
  if (!a) return b;
  if (!b) return a;
  return a.slice(0, 10) >= b.slice(0, 10) ? a : b;
}

/**
 * True when a date (or date-time) value lies before `today` and is not the value the field was loaded with: a record that
 * already started in the past keeps its date when it is edited, but a past date cannot be newly chosen.
 */
export function hqFieldIsNewPastDate(value: string | null | undefined, loaded: string | null, today: string): boolean {
  if (!value) return false;
  if (loaded !== null && value === loaded) return false;
  return value.slice(0, 10) < today;
}

/** A min or max for a number field: a number, or text that is one; null for nothing usable. */
export function hqFieldNumberLimit(limit: number | string | null | undefined): number | null {
  if (limit === null || limit === undefined || limit === '') return null;
  const parsed = Number(limit);
  return Number.isFinite(parsed) ? parsed : null;
}

/** The ids of the hint and the error under a field, for `aria-describedby` (only those that are shown). */
export function hqFieldDescribedBy(id: string, hasHint: boolean, hasError: boolean): string | null {
  const ids = [hasError ? `${id}-error` : '', hasHint ? `${id}-hint` : ''].filter(Boolean);
  return ids.length ? ids.join(' ') : null;
}
