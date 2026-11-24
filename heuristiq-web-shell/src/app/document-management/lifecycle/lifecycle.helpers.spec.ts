import { describe, expect, it } from 'vitest';
import {
  actionVisual,
  auditPageToTimeline,
  buildDocumentParams,
  dayEndIso,
  dayStartIso,
  emptyDocumentFilter,
  formatBytes,
  hasMoreEvents,
  labelFor,
  normalizePageOf,
  relativeTime,
  statusTone,
  timelineErrorFor,
  toTimelineEvent,
} from './lifecycle.helpers';
import { LIFECYCLE_REASON_LABEL_KEYS } from './lifecycle.constants';

describe('lifecycle helpers', () => {
  it('sends only the filters that are filled, trimmed, and hides deleted documents by default', () => {
    const filter = { ...emptyDocumentFilter(), query: '  aadhaar ', category: 'Identity', scanStatus: 'CLEAN' };
    expect(buildDocumentParams(filter, 0, 20)).toEqual({
      query: 'aadhaar', category: 'Identity', scanStatus: 'CLEAN', deleted: false, page: 0, size: 20,
    });
  });

  it('omits the deleted filter when deleted documents are included, and caps the page size at 100', () => {
    const params = buildDocumentParams({ ...emptyDocumentFilter(), includeDeleted: true }, -1, 500);
    expect(params['deleted']).toBeUndefined();
    expect(params['size']).toBe(100);
    expect(params['page']).toBe(0);
  });

  it('turns the picked days into the start and the end of those days', () => {
    const from = new Date(dayStartIso('2026-03-05'));
    const to = new Date(dayEndIso('2026-03-05'));
    expect([from.getFullYear(), from.getMonth(), from.getDate(), from.getHours(), from.getMinutes()]).toEqual([2026, 2, 5, 0, 0]);
    expect([to.getDate(), to.getHours(), to.getMinutes(), to.getSeconds()]).toEqual([5, 23, 59, 59]);
    expect(dayStartIso('')).toBe('');
    expect(dayEndIso('not a date')).toBe('');
    const params = buildDocumentParams({ ...emptyDocumentFilter(), from: '2026-03-05', to: '2026-03-06' }, 0, 10);
    expect(params['from']).toBe(dayStartIso('2026-03-05'));
    expect(params['to']).toBe(dayEndIso('2026-03-06'));
  });

  it('turns an audit event into a timeline step, reading the action and storage facts from the details', () => {
    const step = toTimelineEvent({
      id: 12, eventId: 'evt-12', eventType: 'DOMAIN', occurredAt: '2026-06-15T10:00:00Z', userId: 'u-1', role: 'AGENT',
      outcome: 'FAILURE', durationMs: 42, correlationId: 'corr-1', reasonCode: 'FILE_TOO_LARGE', subjectType: 'DOCUMENT', subjectId: 'd-1',
      details: { action: 'UPLOAD_REJECTED', storageProvider: 'S3', scanVerdict: 'CLEAN', sizeBytes: '2048', documentType: 'PAN_CARD' },
    });
    expect(step).toMatchObject({
      id: 'evt-12', action: 'UPLOAD_REJECTED', status: 'FAILURE', reasonCode: 'FILE_TOO_LARGE', actorId: 'u-1', actorRole: 'AGENT',
      correlationId: 'corr-1', storageProvider: 'S3', scanVerdict: 'CLEAN', sizeBytes: 2048, durationMs: 42,
    });
  });

  it('falls back to the event type, the numeric id and null for what is missing', () => {
    const step = toTimelineEvent({ id: 5, eventType: 'DOMAIN' });
    expect(step.id).toBe('5');
    expect(step.action).toBe('DOMAIN');
    expect(step.reasonCode).toBeNull();
    expect(step.sizeBytes).toBeNull();
    expect(step.actorId).toBeNull();
    expect(toTimelineEvent({ id: 6, details: { reasonCode: 'EMPTY_FILE', sizeBytes: 'abc' } })).toMatchObject({ reasonCode: 'EMPTY_FILE', sizeBytes: null, action: '' });
  });

  it('maps an audit page (0-based number) to a timeline page and survives a missing one', () => {
    const page = auditPageToTimeline({ content: [{ id: 1, details: { action: 'UPLOAD' } }], number: 2, size: 20, totalElements: 41, totalPages: 3 }, 0, 20);
    expect(page).toMatchObject({ page: 2, size: 20, totalElements: 41, totalPages: 3 });
    expect(page.content[0].action).toBe('UPLOAD');
    expect(auditPageToTimeline(null, 1, 20)).toEqual({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 1 });
  });

  it('treats 401 and 403 from the audit trail as a missing privilege, everything else as a failure', () => {
    expect(timelineErrorFor(403)).toBe('forbidden');
    expect(timelineErrorFor(401)).toBe('forbidden');
    expect(timelineErrorFor(500)).toBe('failed');
    expect(timelineErrorFor(0)).toBe('failed');
  });

  it('normalizes a page, a bare array and a missing answer', () => {
    expect(normalizePageOf<number>([1, 2], 0, 20)).toEqual({ content: [1, 2], page: 0, size: 20, totalElements: 2, totalPages: 1 });
    expect(normalizePageOf<number>(null, 3, 20).content).toEqual([]);
    expect(normalizePageOf<number>({ content: [1], page: 1, size: 1, totalElements: 5, totalPages: 5 }, 0, 1).totalPages).toBe(5);
  });

  it('formats sizes in B, KB, MB and GB', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5 MB');
    expect(formatBytes(3.25 * 1024 * 1024 * 1024)).toBe('3.3 GB');
    expect(formatBytes(null)).toBe('—');
    expect(formatBytes(-1)).toBe('—');
  });

  it('describes times relative to now, and gives nothing for old or invalid dates', () => {
    const now = new Date('2026-06-15T12:00:00Z');
    expect(relativeTime('2026-06-15T11:55:00Z', now, 'en')).toBe('5 minutes ago');
    expect(relativeTime('2026-06-15T09:00:00Z', now, 'en')).toBe('3 hours ago');
    expect(relativeTime('2026-06-14T12:00:00Z', now, 'en')).toBe('yesterday');
    expect(relativeTime('2026-01-01T00:00:00Z', now, 'en')).toBe('');
    expect(relativeTime('garbage', now, 'en')).toBe('');
    expect(relativeTime(null, now, 'en')).toBe('');
  });

  it('gives each known action an icon and colour, and a neutral fallback for unknown ones', () => {
    expect(actionVisual('UPLOAD_REJECTED').tone).toBe('danger');
    expect(actionVisual('upload').icon).toBe('bi-cloud-arrow-up');
    const unknown = actionVisual('SOMETHING_NEW');
    expect(unknown.labelKey).toBeNull();
    expect(unknown.tone).toBe('neutral');
    expect(actionVisual(undefined).labelKey).toBeNull();
  });

  it('colours statuses, with failure winning over success', () => {
    expect(statusTone('SUCCESS')).toBe('success');
    expect(statusTone('failed')).toBe('danger');
    expect(statusTone('PENDING')).toBe('warning');
    expect(statusTone('WEIRD')).toBe('info');
    expect(statusTone(null)).toBe('neutral');
  });

  it('translates a reason code through the key map and falls back to the raw code', () => {
    const t = (key: string): string => `T(${key})`;
    expect(labelFor(LIFECYCLE_REASON_LABEL_KEYS, 'FILE_TOO_LARGE', t)).toBe('T(documentManagement.lifecycle.reasons.FILE_TOO_LARGE)');
    expect(labelFor(LIFECYCLE_REASON_LABEL_KEYS, 'NEW_REASON', t)).toBe('NEW_REASON');
    expect(labelFor(LIFECYCLE_REASON_LABEL_KEYS, '', t)).toBe('');
    expect(labelFor(LIFECYCLE_REASON_LABEL_KEYS, null, t)).toBe('');
  });

  it('knows when more events can be loaded', () => {
    expect(hasMoreEvents(null)).toBe(false);
    expect(hasMoreEvents({ content: [], page: 0, size: 20, totalElements: 30, totalPages: 2 })).toBe(true);
    expect(hasMoreEvents({ content: [], page: 1, size: 20, totalElements: 30, totalPages: 2 })).toBe(false);
  });
});
