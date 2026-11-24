import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { HqDocument, HqDocumentService, HqOpenFile } from 'hq-shared-lib';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { NotificationService } from '../../shared/services/notification-service';
import { QaEvidence } from '../../system-qa/system-qa.models';
import { QA_EVIDENCE_MESSAGE_KEYS } from './qa-evidence.constants';
import { evidenceLabelKey, isValidEvidenceId, usableEvidence } from './qa-evidence.helpers';
import { QaEvidenceService } from './qa-evidence.service';

const item = (documentId: string, kind: QaEvidence['kind'] = 'SCREEN'): QaEvidence => ({ documentId, kind, name: 'screen' });

describe('evidence helpers', () => {
  it.each([
    ['abc-123', true],
    ['A'.repeat(100), true],
    ['A'.repeat(101), false],
    ['', false],
    ['a/b', false],
    ['a b', false],
    ['<img>', false],
    ['../x', false],
  ])('checks the document id %j', (id, valid) => {
    expect(isValidEvidenceId(id)).toBe(valid);
  });

  it('refuses anything that is not a text as an id', () => {
    expect(isValidEvidenceId(42)).toBe(false);
    expect(isValidEvidenceId(null)).toBe(false);
    expect(isValidEvidenceId(undefined)).toBe(false);
  });

  it('keeps the screenshots that can be opened, the failure one first, at most three', () => {
    const list = [item('a'), item('bad/id'), item('f', 'FAILURE'), item('c'), item('d'), { documentId: 'z', kind: 'ODD', name: 'x' } as never];
    expect(usableEvidence(list).map((e) => e.documentId)).toEqual(['f', 'a', 'c']);
    expect(usableEvidence(null)).toEqual([]);
    expect(usableEvidence(undefined)).toEqual([]);
    expect(usableEvidence([null as never])).toEqual([]);
  });

  it('labels a screenshot, and reads an unknown kind as a plain screenshot', () => {
    expect(evidenceLabelKey('FAILURE')).toBe('qaDashboard.evidence.failure');
    expect(evidenceLabelKey('SCREEN')).toBe('qaDashboard.evidence.screen');
    expect(evidenceLabelKey('???')).toBe('qaDashboard.evidence.screen');
  });
});

describe('QaEvidenceService', () => {
  const documents = {
    getMetadata: vi.fn((_id: string): Observable<HqDocument> => of({ originalFileName: 'screen.png' } as HqDocument)),
    fetchFile: vi.fn((_doc: HqDocument): Observable<Blob> => of(new Blob(['x'], { type: 'image/png' }))),
  };
  const dialog = { open: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  let service: QaEvidenceService;
  let downloads: string[];

  beforeEach(() => {
    vi.clearAllMocks();
    documents.getMetadata.mockImplementation(() => of({ originalFileName: 'screen.png' } as HqDocument));
    documents.fetchFile.mockImplementation(() => of(new Blob(['x'], { type: 'image/png' })));
    downloads = [];
    URL.createObjectURL = vi.fn(() => 'blob:qa');
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloads.push(this.download);
    });
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        { provide: HqDocumentService, useValue: documents },
        { provide: MatDialog, useValue: dialog },
        { provide: NotificationService, useValue: notify },
      ],
    });
    service = TestBed.inject(QaEvidenceService);
  });

  afterEach(() => vi.restoreAllMocks());

  it('opens an image in the shared file viewer with its real file name', () => {
    let opened: boolean | undefined;
    service.open('doc-1').subscribe((o) => (opened = o));
    expect(documents.getMetadata).toHaveBeenCalledWith('doc-1');
    expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.objectContaining({ data: { blob: expect.any(Blob), fileName: 'screen.png' } }));
    expect(opened).toBe(true);
  });

  it('opens a PDF in the viewer too, and saves any other type as a file', () => {
    documents.fetchFile.mockImplementation(() => of(new Blob(['x'], { type: 'application/pdf' })));
    service.open('doc-1').subscribe();
    expect(dialog.open).toHaveBeenCalledTimes(1);
    documents.fetchFile.mockImplementation(() => of(new Blob(['x'], { type: 'application/zip' })));
    service.open('doc-1').subscribe();
    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(downloads).toEqual(['screen.png']);
  });

  it('refuses an id that is not a plain id without asking the DMS anything', () => {
    let opened: boolean | undefined;
    service.open('../etc/passwd').subscribe((o) => (opened = o));
    expect(opened).toBe(false);
    expect(documents.getMetadata).not.toHaveBeenCalled();
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('says so, and answers false, when the screenshot cannot be fetched', () => {
    documents.getMetadata.mockImplementation(() => throwError(() => new Error('gone')));
    let opened: boolean | undefined;
    service.open('doc-1').subscribe((o) => (opened = o));
    expect(opened).toBe(false);
    expect(notify.error).toHaveBeenCalledWith(QA_EVIDENCE_MESSAGE_KEYS.openFailed);
    expect(dialog.open).not.toHaveBeenCalled();
  });
});
