import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NotificationService } from '../../../shared/services/notification-service';
import { LIFECYCLE_EVENTS_PAGE_SIZE } from '../lifecycle.constants';
import { LifecycleDocumentDetail, LifecycleEvent, LifecyclePage } from '../lifecycle.models';
import { LifecycleAuditService } from '../lifecycle-audit.service';
import { LifecycleService } from '../lifecycle.service';
import { LifecycleFilesService } from '../lifecycle-files.service';
import { LifecyclePreviewDialog } from '../lifecycle-preview-dialog/lifecycle-preview-dialog';
import { LifecycleDetail } from './lifecycle-detail';

const detail = (over: Partial<LifecycleDocumentDetail> = {}): LifecycleDocumentDetail =>
  ({ id: 'd1', originalFileName: 'pan.pdf', documentType: 'PAN', category: 'KYC', ownerId: 'owner1', contentType: 'application/pdf', sizeBytes: 2048, status: 'ACTIVE', uploadedBy: 'u1', createdAt: '2026-01-02T10:00:00Z', tenantId: 'org1', provider: 'S3', storedFileName: 'x.pdf', objectKey: 'k/x.pdf', versionId: 'v1', checksumSha256: 'abc', encryption: 'AES256', scanStatus: 'CLEAN', scanMessage: null, retentionUntil: '2030-01-01T00:00:00Z', legalHold: true, legalHoldChangedBy: 'admin', legalHoldChangedAt: '2026-01-03T00:00:00Z', deleted: false, deletedBy: null, deletedAt: null, ...over } as LifecycleDocumentDetail);
const ev = (id: string): LifecycleEvent => ({ id, action: 'UPLOAD' } as LifecycleEvent);
const events = (rows: LifecycleEvent[], page: number, totalPages: number): LifecyclePage<LifecycleEvent> => ({ content: rows, page, size: LIFECYCLE_EVENTS_PAGE_SIZE, totalElements: 99, totalPages });

describe('LifecycleDetail', () => {
  const router = { navigate: vi.fn() };
  const svc = { getDocument: vi.fn((_id: string): Observable<LifecycleDocumentDetail> => of(detail())) };
  const audit = {
    documentEvents: vi.fn((_id: string, _p: number, _s: number): Observable<LifecyclePage<LifecycleEvent>> => of(events([ev('e1')], 0, 2))),
    classifyError: vi.fn((_e: unknown): 'forbidden' | 'failed' => 'failed'),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  const files = { versions: vi.fn(), url: vi.fn(), download: vi.fn() };
  const dialog = { open: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };
  let translate: TranslateService;

  function create(): ComponentFixture<LifecycleDetail> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [LifecycleDetail],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'd1' }) } } },
        { provide: Router, useValue: router },
        { provide: LifecycleService, useValue: svc },
        { provide: LifecycleAuditService, useValue: audit },
        { provide: NotificationService, useValue: notify },
        { provide: HqTimezoneService, useValue: { effectiveTimezone: () => 'UTC' } },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: MatDialog, useValue: dialog },
      ],
    });
    TestBed.overrideComponent(LifecycleDetail, { set: { providers: [{ provide: LifecycleFilesService, useValue: files }] } });
    translate = TestBed.inject(TranslateService);
    const fixture = TestBed.createComponent(LifecycleDetail);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    svc.getDocument.mockImplementation(() => of(detail()));
    audit.documentEvents.mockImplementation(() => of(events([ev('e1')], 0, 2)));
    audit.classifyError.mockReturnValue('failed');
    files.versions.mockReturnValue(of([]));
    files.url.mockReturnValue(of('blob:x'));
  });

  describe('file and versions', () => {
    const upload = (id: string, createdAt: string, contentType = 'image/png') =>
      ({ id, originalFileName: id + '.png', contentType, createdAt, category: 'KYC', ownerId: 'owner1', sizeBytes: 10 } as never);

    it("lists every upload of the same owner and category, newest first, with this document marked, and reads image thumbnails", () => {
      svc.getDocument.mockImplementation(() => of(detail({ contentType: 'image/png', createdAt: '2026-01-02T10:00:00Z' })));
      files.versions.mockReturnValue(of([upload('old', '2025-12-01T00:00:00Z'), upload('d1', '2026-01-02T10:00:00Z'), upload('new', '2026-02-01T00:00:00Z')]));
      const fixture = create();
      const c = fixture.componentInstance;
      expect(files.versions).toHaveBeenCalledWith('KYC', 'owner1');
      expect(c.versions.map((v) => [v.doc.id, v.current])).toEqual([['new', false], ['d1', true], ['old', false]]);
      expect(c.thumbs['new']).toBe('blob:x');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('[data-testid="lifecycle-version"]').length).toBe(3);
    });

    it('opens the preview dialog at the chosen version', () => {
      files.versions.mockReturnValue(of([upload('d1', '2026-01-02T10:00:00Z', 'application/pdf'), upload('new', '2026-02-01T00:00:00Z')]));
      const c = create().componentInstance;
      c.openPreview(1);
      expect(dialog.open).toHaveBeenCalledWith(LifecyclePreviewDialog, expect.objectContaining({
        data: expect.objectContaining({ index: 1, versions: c.versions, files }),
      }));
    });

    it('shows only this document without the list privilege, and nothing without the download privilege', () => {
      granted = new Set(['DMS_DOCUMENT_LIFECYCLE_READ', 'DMS_DOCUMENT_DOWNLOAD_URL']);
      let c = create().componentInstance;
      expect(files.versions).not.toHaveBeenCalled();
      expect(c.versions.map((v) => v.doc.id)).toEqual(['d1']);

      granted = new Set(['DMS_DOCUMENT_LIFECYCLE_READ']);
      const fixture = create();
      c = fixture.componentInstance;
      expect(c.canPreview).toBe(false);
      expect(c.versions).toEqual([]);
      c.openPreview(0);
      expect(dialog.open).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('[data-testid="lifecycle-versions"]')).toBeNull();
    });

    it('keeps this document when the list cannot be read, and leaves a deleted one out', () => {
      files.versions.mockReturnValue(throwError(() => ({ status: 503 })));
      let c = create().componentInstance;
      expect(c.versions.map((v) => v.doc.id)).toEqual(['d1']);
      expect(c.versionsLoading).toBe(false);

      svc.getDocument.mockImplementation(() => of(detail({ deleted: true })));
      files.versions.mockReturnValue(of([upload('other', '2026-03-01T00:00:00Z')]));
      c = create().componentInstance;
      expect(c.versions.map((v) => v.doc.id)).toEqual(['other']);
    });
  });

  it('loads the document and arranges its facts into five groups', () => {
    const c = create().componentInstance;
    expect(svc.getDocument).toHaveBeenCalledWith('d1');
    expect(c.detail?.id).toBe('d1');
    expect(c.groups.map((g) => g.titleKey.split('.').pop())).toEqual(['file', 'storage', 'scan', 'retention', 'deletion']);
    const rows = c.groups.flatMap((g) => g.rows);
    const value = (suffix: string) => rows.find((r) => r.labelKey.endsWith('.' + suffix))?.value;
    expect(value('originalFileName')).toBe('pan.pdf');
    expect(value('size')).toBe('2 KB');
    expect(value('uploaded')).toContain('u1 · ');
    expect(value('legalHold')).toBe('documentManagement.lifecycle.yes');
    expect(value('deleted')).toBe('documentManagement.lifecycle.no');
    expect(value('scanMessage')).toBe('');
    expect(rows.find((r) => r.labelKey.endsWith('.objectKey'))).toMatchObject({ mono: true, copyable: true });
    expect(rows.find((r) => r.labelKey.endsWith('.tenant'))?.copyable).toBeUndefined();
  });

  it('leaves the size empty when unknown', () => {
    svc.getDocument.mockReturnValue(of(detail({ sizeBytes: null })));
    const c = create().componentInstance;
    expect(c.groups[0].rows.find((r) => r.labelKey.endsWith('.size'))?.value).toBe('');
  });

  it('reports a document that cannot be loaded and shows no history', () => {
    svc.getDocument.mockReturnValue(throwError(() => new Error('x')));
    const c = create().componentInstance;
    expect([c.loadFailed, c.loading, c.groups]).toEqual([true, false, []]);
    expect(notify.error).toHaveBeenCalledWith('documentManagement.lifecycle.detail.loadFailed');
    expect(audit.documentEvents).not.toHaveBeenCalled();
  });

  it('rebuilds the facts when the language changes', () => {
    const c = create().componentInstance;
    const before = c.groups;
    translate.setTranslation('hi', {}); translate.use('hi');
    expect(c.groups).not.toBe(before);
    expect(c.groups).toHaveLength(5);
  });

  describe('history', () => {
    it('loads the first page of events and offers more, appending the next page', () => {
      const c = create().componentInstance;
      expect(audit.documentEvents).toHaveBeenCalledWith('d1', 0, LIFECYCLE_EVENTS_PAGE_SIZE);
      expect(c.canReadAudit).toBe(true);
      expect(c.moreEvents).toBe(true);
      audit.documentEvents.mockReturnValueOnce(of(events([ev('e2')], 1, 2)));
      c.loadEvents(false);
      expect(audit.documentEvents).toHaveBeenLastCalledWith('d1', 1, LIFECYCLE_EVENTS_PAGE_SIZE);
      expect(c.events.map((e) => e.id)).toEqual(['e1', 'e2']);
      expect(c.moreEvents).toBe(false);
      audit.documentEvents.mockReturnValueOnce(of(events([ev('r1')], 0, 1)));
      c.loadEvents(true);
      expect(c.events.map((e) => e.id)).toEqual(['r1']);
    });

    it('treats a missing privilege as a note, not an error', () => {
      audit.documentEvents.mockReturnValue(throwError(() => ({ status: 403 })));
      audit.classifyError.mockReturnValue('forbidden');
      const c = create().componentInstance;
      expect([c.eventsForbidden, c.eventsFailed, c.eventsLoading]).toEqual([true, false, false]);
    });

    it('marks a real failure so it can be retried', () => {
      audit.documentEvents.mockReturnValue(throwError(() => ({ status: 500 })));
      const c = create().componentInstance;
      expect([c.eventsForbidden, c.eventsFailed]).toEqual([false, true]);
      audit.documentEvents.mockReturnValue(of(events([ev('e1')], 0, 1)));
      c.loadEvents(true);
      expect(c.eventsFailed).toBe(false);
      expect(c.events).toHaveLength(1);
    });

    it('does not read the audit trail without the audit privilege', () => {
      granted = new Set(['DMS_DOCUMENT_LIFECYCLE_READ']);
      const c = create().componentInstance;
      expect(c.canReadAudit).toBe(false);
      expect(audit.documentEvents).not.toHaveBeenCalled();
    });
  });

  it('goes back to the list', () => {
    create().componentInstance.back();
    expect(router.navigate).toHaveBeenCalledWith(['/document-management/lifecycle']);
  });

  it('renders the facts on screen and stops listening when destroyed', () => {
    const fixture = create();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('pan.pdf');
    const groups = fixture.componentInstance.groups;
    fixture.destroy();
    translate.setTranslation('ta', {}); translate.use('ta');
    expect(fixture.componentInstance.groups).toBe(groups);
  });
});
