import { formatLocaleInZone } from 'hq-shared-lib';
import {
  AuditEventRaw,
  AuditPageRaw,
  LifecycleActionVisual,
  LifecycleDocumentFilter,
  LifecycleEvent,
  LifecyclePage,
  LifecycleTone,
  TimelineError,
} from './lifecycle.models';
import {
  BYTE_UNITS,
  HOURS_PER_DAY,
  LIFECYCLE_ACTION_VISUALS,
  LIFECYCLE_DANGER_STATUSES,
  LIFECYCLE_MAX_PAGE_SIZE,
  LIFECYCLE_SUCCESS_STATUSES,
  LIFECYCLE_UNKNOWN_ACTION_VISUAL,
  LIFECYCLE_WARNING_STATUSES,
  MINUTES_PER_HOUR,
  MS_PER_SECOND,
  RELATIVE_TIME_MAX_DAYS,
  SECONDS_PER_MINUTE,
} from './lifecycle.constants';

export type LifecycleParams = Record<string, string | number | boolean>;

export const emptyDocumentFilter = (): LifecycleDocumentFilter => ({
  query: '', ownerId: '', category: '', documentType: '', status: '', scanStatus: '', includeDeleted: false, from: '', to: '',
});

/** Start of the picked day, as an ISO instant (local time). Empty or invalid gives ''. */
export function dayStartIso(date: string): string {
  const d = parseDay(date);
  return d ? new Date(d.y, d.m - 1, d.d, 0, 0, 0, 0).toISOString() : '';
}

/** End of the picked day, as an ISO instant (local time). */
export function dayEndIso(date: string): string {
  const d = parseDay(date);
  return d ? new Date(d.y, d.m - 1, d.d, 23, 59, 59, 999).toISOString() : '';
}

function parseDay(date: string): { y: number; m: number; d: number } | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec((date ?? '').trim());
  return match ? { y: Number(match[1]), m: Number(match[2]), d: Number(match[3]) } : null;
}

function put(params: LifecycleParams, key: string, value: string): void {
  const v = (value ?? '').trim();
  if (v) params[key] = v;
}

function clampSize(size: number): number {
  return Math.min(Math.max(Math.floor(size) || 1, 1), LIFECYCLE_MAX_PAGE_SIZE);
}

/**
 * Query parameters of the documents search. Blank filters are left out. Deleted documents are hidden unless
 * "include deleted" is ticked (deleted=false is sent in that case).
 */
export function buildDocumentParams(filter: LifecycleDocumentFilter, page: number, size: number): LifecycleParams {
  const params: LifecycleParams = {};
  put(params, 'query', filter.query);
  put(params, 'ownerId', filter.ownerId);
  put(params, 'category', filter.category);
  put(params, 'documentType', filter.documentType);
  put(params, 'status', filter.status);
  put(params, 'scanStatus', filter.scanStatus);
  if (!filter.includeDeleted) params['deleted'] = false;
  const from = dayStartIso(filter.from);
  const to = dayEndIso(filter.to);
  if (from) params['from'] = from;
  if (to) params['to'] = to;
  params['page'] = Math.max(page, 0);
  params['size'] = clampSize(size);
  return params;
}

/** A page from either a page object or a bare array; missing parts get safe defaults. */
export function normalizePageOf<T>(res: LifecyclePage<T> | T[] | null | undefined, page: number, size: number): LifecyclePage<T> {
  if (Array.isArray(res)) return { content: res, page, size, totalElements: res.length, totalPages: 1 };
  const content = res?.content ?? [];
  return {
    content,
    page: res?.page ?? page,
    size: res?.size ?? size,
    totalElements: res?.totalElements ?? content.length,
    totalPages: res?.totalPages ?? 1,
  };
}

/** 1536 -> "1.5 KB". Empty or negative gives "—". */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || !Number.isFinite(bytes) || bytes < 0) return '—';
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < BYTE_UNITS.length - 1) {
    value /= 1024;
    unit++;
  }
  const text = unit === 0 ? String(Math.round(value)) : (Math.round(value * 10) / 10).toString();
  return `${text} ${BYTE_UNITS[unit]}`;
}

/** Absolute date and time in the given language ('' when unknown or invalid). */
export function formatDateTime(iso: string | null | undefined, lang?: string, zone?: string | null): string {
  const d = toDate(iso);
  if (!d) return '';
  return formatLocaleInZone(iso, 'datetime', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }, lang || undefined, zone);
}

export function formatDate(iso: string | null | undefined, lang?: string, zone?: string | null): string {
  const d = toDate(iso);
  if (!d) return '';
  return formatLocaleInZone(iso, 'date', { day: '2-digit', month: 'short', year: 'numeric' }, lang || undefined, zone);
}

function toDate(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** "5 minutes ago" in the given language; older than 30 days (or invalid) gives ''. `now` is passed in so tests are exact. */
export function relativeTime(iso: string | null | undefined, now: Date, lang?: string): string {
  const d = toDate(iso);
  if (!d) return '';
  const seconds = Math.round((d.getTime() - now.getTime()) / MS_PER_SECOND);
  const rtf = new Intl.RelativeTimeFormat(lang || undefined, { numeric: 'auto' });
  const abs = Math.abs(seconds);
  if (abs < SECONDS_PER_MINUTE) return rtf.format(seconds, 'second');
  const minutes = Math.round(seconds / SECONDS_PER_MINUTE);
  if (Math.abs(minutes) < MINUTES_PER_HOUR) return rtf.format(minutes, 'minute');
  const hours = Math.round(minutes / MINUTES_PER_HOUR);
  if (Math.abs(hours) < HOURS_PER_DAY) return rtf.format(hours, 'hour');
  const days = Math.round(hours / HOURS_PER_DAY);
  if (Math.abs(days) <= RELATIVE_TIME_MAX_DAYS) return rtf.format(days, 'day');
  return '';
}

export function actionVisual(action: string | null | undefined): LifecycleActionVisual {
  return LIFECYCLE_ACTION_VISUALS[(action ?? '').toUpperCase()] ?? LIFECYCLE_UNKNOWN_ACTION_VISUAL;
}

/** Colour of a status word; failure beats success so "FAILED" is never green. */
export function statusTone(status: string | null | undefined): LifecycleTone {
  const s = (status ?? '').toUpperCase();
  if (LIFECYCLE_DANGER_STATUSES.has(s)) return 'danger';
  if (LIFECYCLE_SUCCESS_STATUSES.has(s)) return 'success';
  if (LIFECYCLE_WARNING_STATUSES.has(s)) return 'warning';
  return s ? 'info' : 'neutral';
}

/** Translated label of a code through a key map; the raw code when it is not in the map ('' when empty). */
export function labelFor(
  map: Readonly<Record<string, string>>,
  code: string | null | undefined,
  translate: (key: string) => string,
): string {
  const c = (code ?? '').trim();
  if (!c) return '';
  const key = map[c.toUpperCase()];
  return key ? translate(key) : c;
}

/** Newest-first events: more can be loaded while the loaded count is below the total. */
export function hasMoreEvents(page: LifecyclePage<unknown> | null): boolean {
  return !!page && page.page + 1 < page.totalPages;
}

/** An audit event as a timeline step. The action comes from the details (`action`), else the event type. */
export function toTimelineEvent(raw: AuditEventRaw): LifecycleEvent {
  const d = raw.details ?? {};
  const size = Number(d['sizeBytes']);
  return {
    id: String(raw.eventId ?? raw.id),
    occurredAt: raw.occurredAt ?? null,
    action: d['action'] || raw.eventType || '',
    status: raw.outcome ?? null,
    reasonCode: raw.reasonCode ?? d['reasonCode'] ?? null,
    actorId: raw.userId ?? null,
    actorRole: raw.role ?? null,
    correlationId: raw.correlationId ?? null,
    storageProvider: d['storageProvider'] ?? null,
    scanVerdict: d['scanVerdict'] ?? null,
    sizeBytes: d['sizeBytes'] !== undefined && Number.isFinite(size) ? size : null,
    durationMs: raw.durationMs ?? null,
  };
}

/** A Spring page of audit events (page number 0-based in `number`) as a page of timeline steps; missing parts get safe defaults. */
export function auditPageToTimeline(
  res: AuditPageRaw<AuditEventRaw> | null | undefined,
  page: number,
  size: number,
): LifecyclePage<LifecycleEvent> {
  const content = (res?.content ?? []).map(toTimelineEvent);
  return {
    content,
    page: res?.number ?? page,
    size: res?.size ?? size,
    totalElements: res?.totalElements ?? content.length,
    totalPages: res?.totalPages ?? 1,
  };
}

/** 401 / 403 = the user may not read the audit trail; anything else is a failure. */
export function timelineErrorFor(status: number): TimelineError {
  return status === 401 || status === 403 ? 'forbidden' : 'failed';
}
