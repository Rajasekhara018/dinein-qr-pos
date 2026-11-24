import { ChangeDetectorRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DomSanitizer } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { HqDocument, HqDocumentService, HqTimezoneService } from 'hq-shared-lib';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { LIFECYCLE_MAX_VERSIONS } from './lifecycle.constants';
import { LifecycleDocumentDetail } from './lifecycle.models';
import { LifecycleFilesService } from './lifecycle-files.service';
import { DocumentVersion, asDocument, previewKind, versionsOf } from './lifecycle-versions.helpers';
import { LifecyclePreviewDialog } from './lifecycle-preview-dialog/lifecycle-preview-dialog';

const doc = (id: string, createdAt: string, contentType = 'image/png'): HqDocument =>
  ({ id, originalFileName: id + '.png', contentType, createdAt, sizeBytes: 1024, scanStatus: 'CLEAN' } as HqDocument);
const detail = (over: Partial<LifecycleDocumentDetail> = {}): LifecycleDocumentDetail =>
  ({ id: 'd1', originalFileName: 'bank.png', contentType: 'image/png', category: 'bank_proof', ownerId: 'o1', createdAt: '2026-01-02T00:00:00Z', ...over } as LifecycleDocumentDetail);

describe('lifecycle versions helpers', () => {
  it('tells an image, a PDF and anything else apart', () => {
    expect(previewKind('image/jpeg')).toBe('image');
    expect(previewKind('APPLICATION/PDF')).toBe('pdf');
    expect(previewKind('text/csv')).toBe('other');
    expect(previewKind(null)).toBe('other');
  });

  it('orders versions newest first, marks this document and adds it when the list lacks it', () => {
    expect(versionsOf(detail(), [doc('a', '2025-01-01'), doc('d1', '2026-01-02T00:00:00Z'), doc('b', '2026-05-01')]).map((v) => [v.doc.id, v.current]))
      .toEqual([['b', false], ['d1', true], ['a', false]]);
    const added = versionsOf(detail(), [doc('a', '2025-01-01')]);
    expect(added.map((v) => v.doc.id)).toEqual(['d1', 'a']);
    expect(added[0].doc).toMatchObject({ id: 'd1', contentType: 'image/png', originalFileName: 'bank.png' });
  });

  it('caps the versions shown', () => {
    const many = Array.from({ length: LIFECYCLE_MAX_VERSIONS + 5 }, (_, i) => doc('x' + i, `2025-01-${String((i % 28) + 1).padStart(2, '0')}`));
    expect(versionsOf(detail(), many)).toHaveLength(LIFECYCLE_MAX_VERSIONS);
  });

  it('fills a document from the page detail with safe defaults', () => {
    expect(asDocument({ id: 'z' } as LifecycleDocumentDetail)).toMatchObject({ id: 'z', originalFileName: 'z', contentType: '', sizeBytes: 0, legalHold: false });
  });
});

describe('LifecycleFilesService', () => {
  const documents = { list: vi.fn(), fetchFile: vi.fn(), download: vi.fn() };
  let service: LifecycleFilesService;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [LifecycleFilesService, { provide: HqDocumentService, useValue: documents }] });
    service = TestBed.inject(LifecycleFilesService);
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:1');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
  });

  afterEach(() => vi.restoreAllMocks());

  it('lists the uploads of an owner and category, and nothing without both', () => {
    documents.list.mockReturnValue(of([doc('a', '2025-01-01')]));
    let rows: HqDocument[] = [];
    service.versions('bank_proof', 'o1').subscribe((r) => (rows = r));
    expect(documents.list).toHaveBeenCalledWith('bank_proof', 'o1');
    expect(rows).toHaveLength(1);
    service.versions('', 'o1').subscribe((r) => (rows = r));
    expect(rows).toEqual([]);
  });

  it('reads each file once, and releases the object URLs when the page closes', () => {
    documents.fetchFile.mockReturnValue(of(new Blob(['x'], { type: 'image/png' })));
    let url = '';
    service.url(doc('a', '2025')).subscribe((u) => (url = u));
    service.url(doc('a', '2025')).subscribe((u) => (url = u));
    expect(url).toBe('blob:1');
    expect(documents.fetchFile).toHaveBeenCalledTimes(1);
    service.ngOnDestroy();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1');
  });

  it('downloads under the original name', () => {
    documents.download.mockReturnValue(of(undefined));
    service.download(doc('a', '2025')).subscribe();
    expect(documents.download).toHaveBeenCalled();
  });
});

describe('LifecyclePreviewDialog', () => {
  const files = { url: vi.fn(), download: vi.fn() };
  const ref = { close: vi.fn() };
  const notify = { error: vi.fn() };
  const versions: DocumentVersion[] = [
    { doc: doc('new', '2026-02-01'), current: false },
    { doc: doc('d1', '2026-01-02', 'application/pdf'), current: true },
    { doc: doc('old', '2025-01-01', 'text/csv'), current: false },
  ];

  function make(index: number): LifecyclePreviewDialog {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { versions, index, files } },
        { provide: MatDialogRef, useValue: ref },
        { provide: DomSanitizer, useValue: { bypassSecurityTrustResourceUrl: (u: string) => ({ trusted: u }) } },
        { provide: TranslateService, useValue: { instant: (k: string) => k, currentLang: 'en' } },
        { provide: HqTimezoneService, useValue: { effectiveTimezone: () => 'UTC' } },
        { provide: NotificationService, useValue: notify },
        { provide: ChangeDetectorRef, useValue: { markForCheck: vi.fn() } },
      ],
    });
    const d = TestBed.runInInjectionContext(() => new LifecyclePreviewDialog());
    d.ngOnInit();
    return d;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    files.url.mockImplementation((d: HqDocument) => of('blob:' + d.id));
    files.download.mockReturnValue(of(undefined));
  });

  it('opens on the chosen version: a PDF in the viewer, image thumbnails read', () => {
    const d = make(1);
    expect(d.current?.doc.id).toBe('d1');
    expect(d.kind).toBe('pdf');
    expect(d.pdfUrl).toEqual({ trusted: 'blob:d1' });
    expect(d.urls['new']).toBe('blob:new');
    expect(d.loading).toBe(false);
  });

  it('moves between versions with previous / next and the arrow keys, within bounds', () => {
    const d = make(0);
    expect(d.kind).toBe('image');
    d.previous();
    expect(d.index).toBe(0);
    d.onKey(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    expect(d.index).toBe(1);
    d.next();
    expect(d.index).toBe(2);
    expect(d.kind).toBe('other');
    expect(files.url).not.toHaveBeenCalledWith(versions[2].doc);
    d.next();
    expect(d.index).toBe(2);
    d.onKey(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    expect(d.index).toBe(1);
  });

  it('says when the shown file cannot be opened', () => {
    files.url.mockImplementation((d: HqDocument) => (d.id === 'd1' ? throwError(() => new Error('x')) : of('blob:' + d.id)));
    const d = make(1);
    expect(d.failed).toBe(true);
    expect(d.loading).toBe(false);
  });

  it('downloads the shown version, reports a failed download, and closes', () => {
    const d = make(0);
    d.download();
    expect(files.download).toHaveBeenCalledWith(versions[0].doc);
    files.download.mockReturnValue(throwError(() => new Error('x')));
    d.download();
    expect(notify.error).toHaveBeenCalledWith('documentManagement.lifecycle.preview.failed');
    d.close();
    expect(ref.close).toHaveBeenCalled();
    expect(d.size(2048)).toBeTruthy();
    expect(d.when(null)).toBe('—');
    expect(d.trackVersion(0, versions[0])).toBe('new');
  });
});
