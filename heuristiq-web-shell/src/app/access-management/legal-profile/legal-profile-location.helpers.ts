import { HqLegalProfileOption } from 'hq-shared-lib';
import { Location } from '../franchises/franchise.models';

/** A country as the places are keyed by it: trimmed, upper case ISO 3166 alpha-2. */
export function normalizeCountry(country: string | null | undefined): string {
  return (country ?? '').trim().toUpperCase();
}

/**
 * Places as choices: the name is both what the person sees and what the profile stores. Inactive places and blank names are
 * left out, a name that repeats is listed once, and the names sort alphabetically.
 */
export function locationOptions(rows: readonly Location[]): HqLegalProfileOption[] {
  const names = new Map<string, string>();
  for (const row of rows) {
    const name = row.name?.trim();
    if (row.active === false || !name) continue;
    const key = name.toLowerCase();
    if (!names.has(key)) names.set(key, name);
  }
  return [...names.values()].sort((a, b) => a.localeCompare(b)).map((name) => ({ value: name, label: name }));
}

/** The id of the state with this stored name (compared ignoring case and surrounding spaces), or '' when there is none. */
export function stateIdByName(rows: readonly Location[], name: string): string {
  const wanted = (name ?? '').trim().toLowerCase();
  if (!wanted) return '';
  return rows.find((row) => row.name?.trim().toLowerCase() === wanted)?.id ?? '';
}
