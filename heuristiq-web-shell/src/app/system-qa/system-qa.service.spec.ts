import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HqDocument, HqDocumentService } from 'hq-shared-lib';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { environment } from '../../environments/environment';
import { QA_DOCUMENTS_PATH, QA_RUNS_PATH } from './system-qa.constants';
import { QaDocument, QaDocumentSummary, QaPdfFile, QaRunDetail, QaRunPage, QaRunSummary } from './system-qa.models';
import { SystemQaService } from './system-qa.service';

const RUNS = `${environment.apiUrl}${QA_RUNS_PATH}`;
const DOCS = `${environment.apiUrl}${QA_DOCUMENTS_PATH}`;

const summary = (id: string): QaRunSummary => ({
  id,
  environment: 'qa',
  tags: '@smoke',
  status: 'PASSED',
  total: 1,
  passed: 1,
  failed: 0,
  skipped: 0,
  durationMs: 10,
  startedAt: '2026-10-01T00:00:00Z',
  jenkinsJob: 'qa',
  jenkinsBuildNumber: 1,
  jenkinsUrl: null,
  documentId: null,
  createdAt: '2026-10-01T00:01:00Z',
});

describe('SystemQaService', () => {
  let service: SystemQaService;
  let http: HttpTestingController;
  const dms = {
    getMetadata: vi.fn((_id: string): Observable<HqDocument> => of({} as HqDocument)),
    fetchFile: vi.fn((_doc: HqDocument): Observable<Blob> => of(new Blob())),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: HqDocumentService, useValue: dms }],
    });
    service = TestBed.inject(SystemQaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists a page of runs, unwraps the envelope and sends page, size and environment', () => {
    let page: QaRunPage | undefined;
    service.runs(2, 10, 'uat').subscribe((p) => (page = p));
    const req = http.expectOne((r) => r.url === RUNS);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('environment')).toBe('uat');
    req.flush({ success: true, responseObject: { content: [summary('1')], totalElements: 31, totalPages: 4, size: 10, number: 2 } });
    expect(page).toEqual({ content: [summary('1')], totalElements: 31, totalPages: 4, size: 10, number: 2 });
  });

  it('leaves the environment out when none is chosen and uses the default page size', () => {
    service.runs().subscribe();
    const req = http.expectOne((r) => r.url === RUNS);
    expect(req.request.params.has('environment')).toBe(false);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
  });

  it('caps the page size at 50, never goes below page 0 or size 1, and fills in an empty answer', () => {
    let page: QaRunPage | undefined;
    service.runs(-3, 500).subscribe((p) => (page = p));
    const req = http.expectOne((r) => r.url === RUNS);
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ success: true });
    expect(page).toEqual({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 0 });

    service.runs(0, 0).subscribe();
    expect(http.expectOne((r) => r.url === RUNS).request.params.get('size')).toBe('1');
  });

  it('asks for the newest runs of one environment and returns just the summaries', () => {
    let runs: QaRunSummary[] | undefined;
    service.recentRuns('live', 30).subscribe((r) => (runs = r));
    const req = http.expectOne((r) => r.url === RUNS);
    expect(req.request.params.get('size')).toBe('30');
    expect(req.request.params.get('environment')).toBe('live');
    req.flush({ success: true, responseObject: { content: [summary('9')], totalElements: 1, totalPages: 1, size: 30, number: 0 } });
    expect(runs).toEqual([summary('9')]);
  });

  it('reads one run by encoded id and defaults missing scenarios to an empty list', () => {
    let detail: QaRunDetail | undefined;
    service.run('a/b').subscribe((d) => (detail = d));
    http.expectOne(`${RUNS}/a%2Fb`).flush({ success: true, responseObject: summary('1') });
    expect(detail?.id).toBe('1');
    expect(detail?.scenarios).toEqual([]);
  });

  it('keeps the scenarios a run comes with', () => {
    let detail: QaRunDetail | undefined;
    const scenarios = [{ feature: 'f', name: 'n', tags: [], pilotId: null, status: 'FAILED', durationMs: 1, failureMessage: 'x' }];
    service.run('1').subscribe((d) => (detail = d));
    http.expectOne(`${RUNS}/1`).flush({ success: true, responseObject: { ...summary('1'), scenarios } });
    expect(detail?.scenarios).toEqual(scenarios);
  });

  it('lists documents whether the server answers a bare array, a page or nothing', () => {
    const docs: QaDocumentSummary[] = [{ name: 'plan', title: 'Plan', updatedAt: '2026-10-01T00:00:00Z', sizeBytes: 5 }];
    const got: QaDocumentSummary[][] = [];
    service.documentList().subscribe((d) => got.push(d));
    http.expectOne(DOCS).flush({ success: true, responseObject: docs });
    service.documentList().subscribe((d) => got.push(d));
    http.expectOne(DOCS).flush({ success: true, responseObject: { content: docs } });
    service.documentList().subscribe((d) => got.push(d));
    http.expectOne(DOCS).flush({ success: true, responseObject: {} });
    service.documentList().subscribe((d) => got.push(d));
    http.expectOne(DOCS).flush({ success: true });
    expect(got).toEqual([docs, docs, [], []]);
  });

  it('reads one document by encoded name', () => {
    let doc: QaDocument | undefined;
    service.document('pilot script').subscribe((d) => (doc = d));
    http.expectOne(`${DOCS}/pilot%20script`).flush({ success: true, responseObject: { name: 'pilot script', title: 'T', markdown: '# T', updatedAt: '2026-10-01T00:00:00Z' } });
    expect(doc?.markdown).toBe('# T');
  });

  it('surfaces an HTTP error unchanged', () => {
    let error: { status?: number } | undefined;
    service.run('1').subscribe({ error: (e) => (error = e) });
    http.expectOne(`${RUNS}/1`).flush({ message: 'nope' }, { status: 403, statusText: 'Forbidden' });
    expect(error?.status).toBe(403);
  });

  describe('pdf', () => {
    it('reads the DMS metadata, then the bytes, and returns them with the original file name', () => {
      const doc = { id: 'd1', originalFileName: 'run-7.pdf', contentType: 'application/pdf' } as HqDocument;
      const blob = new Blob(['%PDF'], { type: 'application/pdf' });
      dms.getMetadata.mockReturnValue(of(doc));
      dms.fetchFile.mockReturnValue(of(blob));
      let file: QaPdfFile | undefined;
      service.pdf('d1').subscribe((f) => (file = f));
      expect(dms.getMetadata).toHaveBeenCalledWith('d1');
      expect(dms.fetchFile).toHaveBeenCalledWith(doc);
      expect(file).toEqual({ blob, fileName: 'run-7.pdf' });
    });

    it('fails when DMS cannot be read', () => {
      dms.getMetadata.mockReturnValue(throwError(() => new Error('dms down')));
      let failed = false;
      service.pdf('d1').subscribe({ error: () => (failed = true) });
      expect(failed).toBe(true);
      expect(dms.fetchFile).not.toHaveBeenCalled();
    });
  });
});
