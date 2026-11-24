import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { environment } from '../../environments/environment';
import { COVERAGE_RUNS_PATH } from './system-coverage.constants';
import { CoverageDocument, CoverageRunDetail, CoverageRunSummary } from './system-coverage.models';
import { SystemCoverageService } from './system-coverage.service';

const URL = `${environment.apiUrl}${COVERAGE_RUNS_PATH}`;
const counts = { projectCount: 1, okCount: 1, missingCount: 0, belowFloorCount: 0, atTargetCount: 0 };

const docWith = (linesPct: number | null): CoverageDocument => ({
  schemaVersion: 1,
  generatedAt: '2026-10-21T10:00:00Z',
  source: { gitBranch: 'dev', gitCommit: 'abc' },
  thresholds: { floor: 70, target: 90 },
  projects:
    linesPct === null
      ? [{ key: 'm', kind: 'backend', status: 'missing', measuredAt: null, metrics: null, groups: [] }]
      : [{ key: 'p', kind: 'web', status: 'ok', measuredAt: '2026-10-20T00:00:00Z', metrics: { lines: { covered: linesPct, total: 100, pct: linesPct }, branches: null }, groups: [] }],
});

const summary = (runId: string): CoverageRunSummary => ({ runId, createdAt: `2026-10-2${runId}T00:00:00Z`, uploadedBy: 'u', gitBranch: 'dev', gitCommit: 'abc', summary: counts });
const detail = (runId: string, pct: number | null): CoverageRunDetail => ({ ...summary(runId), document: docWith(pct) });

describe('SystemCoverageService', () => {
  let service: SystemCoverageService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SystemCoverageService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists a page of runs and unwraps the response envelope', () => {
    let page: unknown;
    service.runs(2, 10).subscribe((p) => (page = p));
    const req = http.expectOne((r) => r.url === URL);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ success: true, responseObject: { content: [summary('1')], totalElements: 31, totalPages: 4, size: 10, number: 2 } });
    expect(page).toEqual({ content: [summary('1')], totalElements: 31, totalPages: 4, size: 10, number: 2 });
  });

  it('caps the page size at 50, never goes below page 0 or size 1, and fills in an empty answer', () => {
    let page: unknown;
    service.runs(-3, 500).subscribe((p) => (page = p));
    const req = http.expectOne((r) => r.url === URL);
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ success: true });
    expect(page).toEqual({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 0 });

    service.runs(0, 0).subscribe();
    expect(http.expectOne((r) => r.url === URL).request.params.get('size')).toBe('1');
  });

  it('uses the default page size and page 0', () => {
    service.runs().subscribe();
    const req = http.expectOne((r) => r.url === URL);
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('page')).toBe('0');
  });

  it('reads one run by encoded id and keeps it for the next look', () => {
    const got: CoverageRunDetail[] = [];
    service.run('a/b').subscribe((d) => got.push(d));
    http.expectOne(`${URL}/a%2Fb`).flush({ success: true, responseObject: detail('1', 80) });
    service.run('a/b').subscribe((d) => got.push(d));
    http.expectNone(`${URL}/a%2Fb`);
    expect(got).toHaveLength(2);
    expect(got[1]).toBe(got[0]);
  });

  it('posts the document as the raw body (no request object) and forgets the cache', () => {
    service.run('1').subscribe();
    http.expectOne(`${URL}/1`).flush({ success: true, responseObject: detail('1', 80) });

    let result: unknown;
    const document = docWith(80);
    service.upload(document).subscribe((r) => (result = r));
    const req = http.expectOne(URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBe(document);
    req.flush({ success: true, responseObject: { runId: 'new', createdAt: '2026-10-21T00:00:00Z', summary: counts } }, { status: 201, statusText: 'Created' });
    expect(result).toMatchObject({ runId: 'new' });

    service.run('1').subscribe();
    http.expectOne(`${URL}/1`).flush({ success: true, responseObject: detail('1', 80) });
  });

  it('surfaces an HTTP error unchanged so the screen can map the status', () => {
    let error: { status?: number } | undefined;
    service.upload(docWith(80)).subscribe({ error: (e) => (error = e) });
    http.expectOne(URL).flush({ message: 'bad' }, { status: 400, statusText: 'Bad Request' });
    expect(error?.status).toBe(400);
  });

  it('builds a trend of average line coverage, oldest first, skipping runs that fail or have nothing measured', () => {
    let values: unknown;
    service.trend([summary('3'), summary('2'), summary('1'), summary('4')]).subscribe((v) => (values = v));
    http.expectOne(`${URL}/3`).flush({ success: true, responseObject: detail('3', 90) });
    http.expectOne(`${URL}/2`).flush({ success: true, responseObject: detail('2', null) });
    http.expectOne(`${URL}/1`).flush({ message: 'x' }, { status: 500, statusText: 'Server Error' });
    http.expectOne(`${URL}/4`).flush({ success: true, responseObject: detail('4', 70) });
    expect(values).toEqual([
      { runId: '4', createdAt: summary('4').createdAt, value: 70 },
      { runId: '3', createdAt: summary('3').createdAt, value: 90 },
    ]);
  });

  it('answers an empty trend without calling the server', () => {
    let values: unknown;
    service.trend([]).subscribe((v) => (values = v));
    expect(values).toEqual([]);
  });
});
