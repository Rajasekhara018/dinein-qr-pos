import { formatLocaleInZone } from 'hq-shared-lib';
import { EMPTY_VALUE } from './onboarded-users.constants';
import { OnboardingPlacement } from './onboarded-users.models';

/** Anything carrying an answers map -- a submission or an open review item. */
export interface HasAnswers {
  answers?: Record<string, unknown> | null;
}

/** Renders any answer value as display text ("—" when empty, JSON for objects/arrays). */
export function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return EMPTY_VALUE;
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

export function answerText(source: HasAnswers, key: string): string {
  return displayValue(source.answers?.[key]);
}

function joined(parts: (string | null | undefined)[]): string {
  return parts.filter((p): p is string => !!p && p.trim() !== '').join(' · ');
}

/** "City Cabs (CITYCABS)", else the organization id the submission stores, else "—". */
export function organizationLabel(row: OnboardingPlacement): string {
  if (row.organizationName) return row.organizationCode ? `${row.organizationName} (${row.organizationCode})` : row.organizationName;
  return row.organizationCode || row.tenantId || EMPTY_VALUE;
}

/** "HYD1 · Hyd One", else the franchise id (Franchise could not answer), else "—" (company-run / not a driver). */
export function franchiseLabel(row: OnboardingPlacement): string {
  // Franchise may answer the code as the name too: show it once.
  const name = row.franchiseName && row.franchiseName !== row.franchiseCode ? row.franchiseName : null;
  return joined([row.franchiseCode, name]) || row.franchiseId || EMPTY_VALUE;
}

/** The place the applicant picked, else its id, else "—". */
export function placeLabel(row: OnboardingPlacement): string {
  return row.placeName || row.placeId || EMPTY_VALUE;
}

/** 1536 -> "1.5 KB"; null/0 -> "—". */
export function formatBytes(bytes: number | null | undefined): string {
  if (!bytes) return EMPTY_VALUE;
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatDateTime(iso: string | null | undefined, zone?: string | null): string {
  if (!iso) return EMPTY_VALUE;
  return formatLocaleInZone(iso, 'datetime', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }, 'en-IN', zone);
}
