import { Location, LocationQuery, LocationRequest, LocationType, PlaceRef, PlanQuery } from './franchise.models';
import {
  ALLOWED_PARENT_TYPES, IMPORT_CSV_EXAMPLE, IMPORT_CSV_HEADER, IMPORT_MAX_CHARS, IMPORT_MAX_ROWS, LOCATION_CODE_MAX, LOCATION_NAME_MAX,
  LOCATION_ORDER_MAX, LOCATION_TYPES, LOCATION_TYPE_LEVEL,
} from './franchise-locations.constants';

/** The query string (with leading ?) of a locations filter; empty values are left out, a 2-letter country is upper-cased. */
export function locationQueryString(query: LocationQuery): string {
  const params = new URLSearchParams();
  if (query.countryCode) { params.set('countryCode', query.countryCode.trim().toUpperCase()); }
  if (query.type) { params.set('type', query.type); }
  if (query.level) { params.set('level', query.level); }
  if (query.parentId) { params.set('parentId', query.parentId); }
  if (query.active !== undefined && query.active !== null) { params.set('active', String(query.active)); }
  if (query.usable) { params.set('usable', 'true'); }
  if (query.includeInactive) { params.set('includeInactive', 'true'); }
  if (query.q?.trim()) { params.set('q', query.q.trim()); }
  if (query.page !== undefined) { params.set('page', String(query.page)); }
  if (query.size !== undefined) { params.set('size', String(query.size)); }
  const text = params.toString();
  return text ? `?${text}` : '';
}

/** The query string of a plans filter. Always carries a page so the service answers with one capped page. */
export function planQueryString(query: PlanQuery): string {
  const params = new URLSearchParams();
  if (query.applicationCode) { params.set('applicationCode', query.applicationCode); }
  if (query.scope) { params.set('scope', query.scope); }
  if (query.locationType) { params.set('locationType', query.locationType); }
  if (query.level) { params.set('level', query.level); }
  if (query.stateId) { params.set('stateId', query.stateId); }
  if (query.districtId) { params.set('districtId', query.districtId); }
  if (query.q?.trim()) { params.set('q', query.q.trim()); }
  params.set('page', String(query.page ?? 0));
  if (query.size !== undefined) { params.set('size', String(query.size)); }
  return `?${params.toString()}`;
}

/** "Telangana > Krishna > Nuzvid". */
export function placePath(chain: readonly Pick<PlaceRef, 'name'>[] | null | undefined): string {
  return (chain ?? []).map((step) => step.name).join(' > ');
}

/** The types a place of this type may sit under; empty for a state or an unknown type. */
export function allowedParentTypes(type: LocationType | ''): readonly LocationType[] {
  return type ? ALLOWED_PARENT_TYPES[type] ?? [] : [];
}

/** True when a place of {@code type} may sit directly under a place of {@code parentType}. */
export function isParentAllowed(type: LocationType | '', parentType: LocationType | null | undefined): boolean {
  return !!parentType && allowedParentTypes(type).includes(parentType);
}

// ── The add / change form ────────────────────────────────────────────────────

export interface LocationForm {
  countryCode: string;
  type: LocationType | '';
  code: string;
  name: string;
  /** The parent chosen in the picker; null while none is chosen (an edit then keeps the parent it has). */
  parent: Location | null;
  active: boolean;
  sortOrder: number | null;
}

export function emptyLocationForm(countryCode = ''): LocationForm {
  return { countryCode, type: '', code: '', name: '', parent: null, active: true, sortOrder: 0 };
}

export function locationToForm(location: Location): LocationForm {
  return {
    countryCode: location.countryCode, type: location.type, code: location.code, name: location.name, parent: null, active: location.active,
    sortOrder: location.sortOrder,
  };
}

/** Translation key of the first thing wrong with the form, or null when it can be sent. */
export function locationFormError(form: LocationForm, editing: boolean): string | null {
  if (!editing && !/^[A-Za-z]{2}$/.test(form.countryCode.trim())) { return 'accessManagement.franchiseLocations.errors.country'; }
  if (!editing && !form.type) { return 'accessManagement.franchiseLocations.errors.type'; }
  const code = form.code.trim();
  if (!code || code.length > LOCATION_CODE_MAX) { return 'accessManagement.franchiseLocations.errors.code'; }
  const name = form.name.trim();
  if (!name || name.length > LOCATION_NAME_MAX) { return 'accessManagement.franchiseLocations.errors.name'; }
  if (form.sortOrder !== null && (!Number.isInteger(form.sortOrder) || form.sortOrder < 0 || form.sortOrder > LOCATION_ORDER_MAX)) {
    return 'accessManagement.franchiseLocations.errors.order';
  }
  if (form.type && form.parent && !isParentAllowed(form.type, form.parent.type)) { return 'accessManagement.franchiseLocations.errors.parent'; }
  if (form.parent && form.countryCode && form.parent.countryCode !== form.countryCode.trim().toUpperCase()) {
    return 'accessManagement.franchiseLocations.errors.parentCountry';
  }
  return null;
}

/** The request for a valid form: country and type only on create (they are fixed afterwards), the version only on update. */
export function toLocationRequest(form: LocationForm, editingVersion: number | null): LocationRequest {
  const request: LocationRequest = {
    code: form.code.trim(), name: form.name.trim(), active: form.active, sortOrder: form.sortOrder ?? 0,
    ...(form.parent ? { parentId: form.parent.id } : {}),
  };
  if (editingVersion === null) {
    return { ...request, countryCode: form.countryCode.trim().toUpperCase(), type: form.type as LocationType };
  }
  return { ...request, version: editingVersion };
}

/** The level of a type, or '' for a value that is not one of the eight. */
export function levelOfType(type: string): string {
  return (LOCATION_TYPES as readonly string[]).includes(type) ? LOCATION_TYPE_LEVEL[type as LocationType] : '';
}

// ── The cascading picker ─────────────────────────────────────────────────────

/** The deepest chosen place whose type may be the value ({@code selectable} null: any type), walking back up the chosen path. */
export function deepestSelectable(chosen: readonly (Location | null)[], selectable: readonly LocationType[] | null): Location | null {
  for (let i = chosen.length - 1; i >= 0; i--) {
    const place = chosen[i];
    if (place && (!selectable || selectable.includes(place.type))) {
      return place;
    }
  }
  return null;
}

// ── Import ───────────────────────────────────────────────────────────────────

/** The template download: the header and one example line. */
export function importTemplateCsv(): string {
  return `${IMPORT_CSV_HEADER}\n${IMPORT_CSV_EXAMPLE}\n`;
}

/** Data rows of a CSV text (the header and blank lines not counted); a line break inside a quoted value does not start a row. */
export function importRowCount(csv: string): number {
  let rows = 0;
  let inQuotes = false;
  let lineHasText = false;
  for (const ch of csv) {
    if (ch === '"') {
      inQuotes = !inQuotes;
      lineHasText = true;
    } else if ((ch === '\n' || ch === '\r') && !inQuotes) {
      if (lineHasText) { rows++; }
      lineHasText = false;
    } else if (ch.trim() !== '' && ch !== '﻿') {
      lineHasText = true;
    }
  }
  if (lineHasText) { rows++; }
  return Math.max(0, rows - 1);
}

/** Translation key of what stops a CSV text from being sent (empty, too big, too many rows), or null. */
export function importInputError(csv: string): string | null {
  if (!csv.trim()) { return 'accessManagement.franchiseLocations.import.errors.empty'; }
  if (csv.length > IMPORT_MAX_CHARS) { return 'accessManagement.franchiseLocations.import.errors.tooBig'; }
  const rows = importRowCount(csv);
  if (rows === 0) { return 'accessManagement.franchiseLocations.import.errors.noRows'; }
  if (rows > IMPORT_MAX_ROWS) { return 'accessManagement.franchiseLocations.import.errors.tooManyRows'; }
  return null;
}

/** Offers text as a downloaded file (UTF-8 CSV). */
export function downloadTextFile(name: string, text: string, mime = 'text/csv;charset=utf-8'): void {
  const url = URL.createObjectURL(new Blob([text], { type: mime }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}

/** Reads a chosen file as text in the browser (the service takes the text, not an upload). */
export function readFileText(file: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(file);
  });
}
