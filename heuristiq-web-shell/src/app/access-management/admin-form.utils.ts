import {
  HqAppDialogFormValues,
  HqAppDialogValue,
  hqIsValidPhone,
} from 'hq-shared-lib';
import { phoneToControlValue } from '../shared/phone.helpers';
import { apiErrorMessage } from '../shared/utils/api-error.helpers';
import { RPhoneNumber } from '../shared/models/uam.models';
import { ADMIN_FORM_TAGS_JOIN, ADMIN_FORM_TAGS_SEPARATOR } from './admin-form.constants';
import { AdminFormEntity, AdminFormField, AdminFormPayload } from './admin-form.types';

/*
 * Pure helpers behind the admin form page: reading a field's starting value and turning the form values into the UAM create/update payload. No Angular here so they stay
 * trivially unit-testable.
 */

/** Reads a dot-path (e.g. "passwordPolicy.minimumLength") from a nested object; undefined when any step is missing. */
export function getByPath(obj: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>(
    (acc, key) => (acc === null || acc === undefined ? undefined : (acc as Record<string, unknown>)[key]),
    obj,
  );
}

/** Writes `value` at a dot-path, creating the intermediate objects on the way. */
export function setByPath(obj: Record<string, unknown>, path: string, value: unknown): void {
  const keys = path.split('.');
  let target = obj;
  for (let i = 0; i < keys.length - 1; i++) {
    const key = keys[i];
    const next = target[key];
    if (next === null || typeof next !== 'object') target[key] = {};
    target = target[key] as Record<string, unknown>;
  }
  target[keys[keys.length - 1]] = value;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

/** Some API reads return nested objects (e.g. UamUser.fullName is RName {firstName, middleName,
 *  lastName}) where the form field is a flat text input. Names are joined into a display string;
 *  anything else falls back to JSON so nothing renders as "[object Object]". */
function flattenForDisplay(value: Record<string, unknown>): string {
  if ('firstName' in value || 'lastName' in value) {
    return [value['firstName'], value['middleName'], value['lastName']].filter(Boolean).join(' ');
  }
  return JSON.stringify(value);
}

function asString(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

/** The dialog value a field starts with: read from the entity being edited (via `readKey`), or empty. */
export function initialFieldValue(field: AdminFormField, entity: AdminFormEntity | null): HqAppDialogValue {
  if (!entity && field.defaultValue !== undefined) return field.defaultValue;
  const existing = entity ? (field.readValue ? field.readValue(entity) : getByPath(entity, field.readKey ?? field.key)) : undefined;
  switch (field.type) {
    case 'tags':
      return Array.isArray(existing) ? existing.join(ADMIN_FORM_TAGS_JOIN) : '';
    case 'multiselect':
    case 'entity-picker':
      return Array.isArray(existing) ? existing.map(String) : [];
    case 'checkbox':
      return !!existing;
    case 'phone':
      // GET returns RPhoneNumber {countryCode, number} (or an older bare number); the phone input
      // holds the international (E.164) string, which phoneToControlValue builds from either.
      return phoneToControlValue(isPlainObject(existing) ? (existing as unknown as RPhoneNumber) : asString(existing));
    default:
      return isPlainObject(existing) ? flattenForDisplay(existing) : asString(existing);
  }
}

/** The translation key of what is wrong with a typed value (wrong format, outside the minimum or maximum, or a rule of its own), or null when it is fine or empty. */
export function fieldError(field: AdminFormField, values: HqAppDialogFormValues): string | null {
  const text = asString(values[field.key]).trim();
  if (text === '') return null;
  if (field.pattern && !field.pattern.test(text)) return field.patternMessage ?? 'shared.adminCrud.invalidValue';
  if (field.type === 'number' && !Number.isFinite(Number(text))) return field.patternMessage ?? 'shared.adminCrud.invalidValue';
  if (field.min !== undefined && field.type === 'number' && Number(text) < field.min) return field.minMessage ?? 'shared.adminCrud.belowMinimum';
  if (field.max !== undefined && field.type === 'number' && Number(text) > field.max) return field.maxMessage ?? 'shared.adminCrud.aboveMaximum';
  return field.rule ? field.rule(values) : null;
}

/** The first `phone` field holding a malformed (non-empty) number, so the page can refuse to save it. */
export function invalidPhoneField(
  fields: readonly AdminFormField[],
  values: HqAppDialogFormValues,
  defaultCountry?: string,
): AdminFormField | null {
  return fields.find((field) => {
    if (field.type !== 'phone') return false;
    const value = asString(values[field.key]).trim();
    return value !== '' && !hqIsValidPhone(value, defaultCountry);
  }) ?? null;
}

/** Builds the create/update body: tags split on commas, numbers parsed, nested keys set along their dot-path. */
export function buildPayload(fields: readonly AdminFormField[], values: HqAppDialogFormValues): AdminFormPayload {
  const payload: AdminFormPayload = {};
  for (const field of fields) {
    const raw = values[field.key];
    let value: unknown = raw;
    if (field.booleanSelect) {
      if (raw === '' || raw === null || raw === undefined) value = null;
      else if (raw === true || raw === 'true') value = true;
      else if (raw === false || raw === 'false') value = false;
      else throw new Error('Invalid policy boolean');
    } else if (field.type === 'tags') {
      value = asString(raw)
        .split(ADMIN_FORM_TAGS_SEPARATOR)
        .map((s) => s.trim())
        .filter(Boolean);
    } else if (field.type === 'number') {
      const text = asString(raw).trim();
      value = text === '' ? null : Number(text);
    } else if (field.type === 'phone') {
      value = asString(raw).trim();
    }
      if (field.emptyAsNull && (value === '' || value === undefined)) value = null;
    setByPath(payload, field.key, value);
  }
  return payload;
}

/** The backend's error text from a failed HTTP call (envelope message, detail or first field error), when it sent one. */
export function errorDetail(err: unknown): string | undefined {
  return apiErrorMessage(err, '') || undefined;
}

/**
 * Copies text to the clipboard: the async Clipboard API when the browser offers it, otherwise a hidden textarea and
 * the legacy copy command. Resolves false (never throws) when neither works.
 */
export async function copyTextToClipboard(text: string, doc: Document = document): Promise<boolean> {
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // fall through to the legacy path (permission denied, insecure context)
  }
  try {
    const area = doc.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    doc.body.appendChild(area);
    area.select();
    const ok = doc.execCommand('copy');
    doc.body.removeChild(area);
    return ok;
  } catch {
    return false;
  }
}
