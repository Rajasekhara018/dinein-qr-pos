import { READINESS_MESSAGE_KEY_PATTERN, READINESS_STATUS_SUFFIX, READINESS_STATUS_TEXT_PREFIX } from './application-readiness.constants';
import { ReadinessFixLink, ReadinessStepStatus } from './application-readiness.models';

/** Tells whether a translation key has a text in the active language. */
export type HasTranslation = (key: string) => boolean;

/** Decides whether the user may open a Rider_Web route (best effort). */
export type CanOpenPath = (path: string) => boolean;

const STATUS_BY_SUFFIX: Readonly<Record<string, ReadinessStepStatus>> = {
  pass: 'PASS',
  fail: 'FAIL',
  warn: 'WARN',
  unknown: 'UNKNOWN',
  notapplicable: 'NOT_APPLICABLE',
  not_applicable: 'NOT_APPLICABLE',
};

/** The status segment of a messageKey as a step status: `not_applicable`, `notApplicable` and any case are accepted. */
export function statusFromSuffix(suffix: string | null | undefined): ReadinessStepStatus | null {
  if (!suffix) return null;
  return STATUS_BY_SUFFIX[suffix.toLowerCase()] ?? null;
}

/** Splits `readiness.<service>.<check>.<status>`; null when the key has another shape. */
export function parseMessageKey(messageKey: string | null | undefined): { service: string; check: string; status: ReadinessStepStatus | null } | null {
  const match = READINESS_MESSAGE_KEY_PATTERN.exec(messageKey ?? '');
  return match ? { service: match[1], check: match[2], status: statusFromSuffix(match[3]) } : null;
}

/**
 * The translation keys to try for a step, most specific first: the messageKey itself (status segment normalised to the
 * key names used in the JSON files), the check's neutral title, then the generic sentence for the status. The raw messageKey
 * is never shown: the generic sentence is the last resort.
 */
export function messageKeyCandidates(messageKey: string | null | undefined, status?: ReadinessStepStatus | null): string[] {
  const parsed = parseMessageKey(messageKey);
  const effective = status ?? parsed?.status ?? 'UNKNOWN';
  const generic = `${READINESS_STATUS_TEXT_PREFIX}.${READINESS_STATUS_SUFFIX[effective]}`;
  if (!parsed) return [generic];
  const base = `readiness.${parsed.service}.${parsed.check}`;
  const exact = parsed.status ? `${base}.${READINESS_STATUS_SUFFIX[parsed.status]}` : null;
  return [...(exact ? [exact] : []), `${base}.title`, generic];
}

/** The first candidate that has a text; the last one (always the generic status sentence) otherwise. */
export function resolveTranslationKey(candidates: readonly string[], has: HasTranslation): string {
  return candidates.find((key) => has(key)) ?? candidates[candidates.length - 1];
}

/** The translation key for a messageKey, with the fallbacks described in messageKeyCandidates. */
export function readinessTextKey(messageKey: string | null | undefined, has: HasTranslation, status?: ReadinessStepStatus | null): string {
  return resolveTranslationKey(messageKeyCandidates(messageKey, status), has);
}

/** A fix path as a router target. Only in-app paths (`/x?y=z`) are accepted; anything else (a full URL, `//host`) is null. */
export function parseFixPath(fixPath: string | null | undefined): ReadinessFixLink | null {
  const raw = (fixPath ?? '').trim();
  if (!raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\')) return null;
  const [beforeHash] = raw.split('#');
  const queryAt = beforeHash.indexOf('?');
  const path = queryAt >= 0 ? beforeHash.slice(0, queryAt) : beforeHash;
  const queryParams: Record<string, string> = {};
  if (queryAt >= 0) {
    new URLSearchParams(beforeHash.slice(queryAt + 1)).forEach((value, key) => {
      queryParams[key] = value;
    });
  }
  return path ? { path, queryParams } : null;
}

/**
 * The permission key of the menu entry that owns a path: the longest entry path equal to it or a parent of it. `undefined`
 * when no entry matches (the caller then cannot tell and shows the button), `null` when the entry is open to everyone.
 */
export function navPermissionFor(path: string, items: readonly { path: string; permission: string | null }[]): string | null | undefined {
  const match = items
    .filter((item) => path === item.path || path.startsWith(`${item.path}/`))
    .sort((a, b) => b.path.length - a.path.length)[0];
  return match ? match.permission : undefined;
}
