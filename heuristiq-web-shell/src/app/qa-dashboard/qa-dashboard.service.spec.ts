import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { environment } from '../../environments/environment';
import {
  QA_EXECUTIONS_TEST_SETS_PATH,
  QA_EXECUTIVE_SUMMARY_PATH,
  QA_FAILURES_PATH,
  QA_FILTERS_PATH,
  QA_OVERVIEW_PATH,
  QA_TEST_CASES_EXPORT_PATH,
  QA_TEST_CASES_PATH,
  QA_TEST_SETS_PATH,
  QA_TRIGGER_PATH,
} from './qa-dashboard.constants';
import { emptyFilters } from './qa-dashboard.helpers';
import { QaTestSet, QaTestSetInput } from './qa-dashboard.models';
import { QaDashboardService } from './qa-dashboard.service';

const url = (path: string): string => `${environment.apiUrl}${path}`;

describe('QaDashboardService', () => {
  let service: QaDashboardService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(QaDashboardService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for the overview with the workspace, the mode and only the filters that are set', () => {
    let result: unknown;
    service.overview('API', emptyFilters({ release: 'QA_REG_OCT26_R2', from: '2026-10-01' }), 'overall').subscribe((o) => (result = o));
    const req = http.expectOne((r) => r.url === url(QA_OVERVIEW_PATH));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('workspace')).toBe('API');
    expect(req.request.params.get('mode')).toBe('overall');
    expect(req.request.params.get('release')).toBe('QA_REG_OCT26_R2');
    expect(req.request.params.get('from')).toBe('2026-10-01');
    expect(req.request.params.has('environment')).toBe(false);
    req.flush({ responseObject: { mode: 'overall', totals: { total: 3, passed: 2, failed: 1 } } });
    expect(result).toMatchObject({ mode: 'overall', totals: { total: 3, passed: 2, failed: 1, skipped: 0 }, releases: [], trend: [], volume: [] });
  });

  it('gives an empty overview when the server sends nothing', () => {
    let result: { totals: { total: number }; releases: unknown[] } | undefined;
    service.overview('UI', emptyFilters(), 'unique').subscribe((o) => (result = o));
    http.expectOne((r) => r.url === url(QA_OVERVIEW_PATH)).flush(null);
    expect(result?.totals.total).toBe(0);
    expect(result?.releases).toEqual([]);
  });

  it('reads the filter options of a workspace', () => {
    let result: unknown;
    service.facets('UI').subscribe((f) => (result = f));
    const req = http.expectOne((r) => r.url === url(QA_FILTERS_PATH));
    expect(req.request.params.get('workspace')).toBe('UI');
    req.flush({ responseObject: { modules: [{ module: 'uam', subModules: ['login'] }], environments: ['qa'] } });
    expect(result).toEqual({ modules: [{ module: 'uam', subModules: ['login'] }], environments: ['qa'], releases: [], suites: [] });
  });

  it('lists test cases a page at a time, capping the page size', () => {
    let page: { totalPages: number; items: Array<{ tcId: string | null }> } | undefined;
    service.testCases('UI', emptyFilters({ suite: 'SMOKE', q: 'wallet' }), 1, 500).subscribe((p) => (page = p));
    const req = http.expectOne((r) => r.url === url(QA_TEST_CASES_PATH));
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.get('suite')).toBe('SMOKE');
    expect(req.request.params.get('q')).toBe('wallet');
    req.flush({ responseObject: { items: [{ id: 'a', tcId: 'HQ_RW_036' }], totalElements: 1, totalPages: 1, page: 1, size: 50, totals: { total: 1, passed: 1, failed: 0, skipped: 0 } } });
    expect(page?.items[0].tcId).toBe('HQ_RW_036');
    expect(page?.totalPages).toBe(1);
  });

  it('reads one test case with its history and tolerates a missing history', () => {
    let detail: { testCase: { id: string }; history: unknown[] } | undefined;
    service.testCase('a b').subscribe((d) => (detail = d));
    http.expectOne(url(`${QA_TEST_CASES_PATH}/a%20b`)).flush({ responseObject: { testCase: { id: 'a b' } } });
    expect(detail?.testCase.id).toBe('a b');
    expect(detail?.history).toEqual([]);
  });

  it('deletes a test case', () => {
    let done = false;
    service.deleteTestCase('tc-1').subscribe(() => (done = true));
    const req = http.expectOne(url(`${QA_TEST_CASES_PATH}/tc-1`));
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true });
    expect(done).toBe(true);
  });

  it('exports the filtered table as a file', () => {
    let blob: Blob | undefined;
    service.exportCsv('API', emptyFilters({ status: 'FAILED' })).subscribe((b) => (blob = b));
    const req = http.expectOne((r) => r.url === url(QA_TEST_CASES_EXPORT_PATH));
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.get('workspace')).toBe('API');
    expect(req.request.params.get('status')).toBe('FAILED');
    req.flush(new Blob(['a,b']));
    expect(blob?.size).toBe(3);
  });

  it('reads the failure analysis and the executive summary', () => {
    let failures: { totalFailed: number } | undefined;
    let summary: { headline: string[] } | undefined;
    service.failures('UI', emptyFilters()).subscribe((f) => (failures = f));
    http.expectOne((r) => r.url === url(QA_FAILURES_PATH)).flush({ responseObject: { totalFailed: 4 } });
    service.executiveSummary('UI', emptyFilters({ environment: 'qa' })).subscribe((s) => (summary = s));
    const req = http.expectOne((r) => r.url === url(QA_EXECUTIVE_SUMMARY_PATH));
    expect(req.request.params.get('environment')).toBe('qa');
    req.flush({ responseObject: { headline: ['ALL_GREEN'] } });
    expect(failures?.totalFailed).toBe(4);
    expect(summary?.headline).toEqual(['ALL_GREEN']);
  });

  it('reads the test set executions as a plain list or as a page', () => {
    let first: unknown[] | undefined;
    let second: unknown[] | undefined;
    service.testSetExecutions().subscribe((l) => (first = l));
    http.expectOne((r) => r.url === url(QA_EXECUTIONS_TEST_SETS_PATH)).flush({ responseObject: [{ testSetId: 'a' }] });
    service.testSetExecutions().subscribe((l) => (second = l));
    http.expectOne((r) => r.url === url(QA_EXECUTIONS_TEST_SETS_PATH)).flush({ responseObject: { content: [{ testSetId: 'b' }] } });
    expect(first).toEqual([{ testSetId: 'a' }]);
    expect(second).toEqual([{ testSetId: 'b' }]);
  });

  it('gives an empty list when there are no test sets', () => {
    let sets: QaTestSet[] | undefined;
    service.testSets().subscribe((l) => (sets = l));
    http.expectOne((r) => r.url === url(QA_TEST_SETS_PATH)).flush({ responseObject: undefined });
    expect(sets).toEqual([]);
  });

  it('creates, updates and deletes a test set', () => {
    const input: QaTestSetInput = { name: 'Nightly', workspace: 'UI', suite: 'REGRESSION', tagExpression: '@regression', description: '', version: 3 };
    service.createTestSet(input).subscribe();
    const create = http.expectOne(url(QA_TEST_SETS_PATH));
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(input);
    create.flush({ success: true });

    service.updateTestSet('s/1', input).subscribe();
    const update = http.expectOne(url(`${QA_TEST_SETS_PATH}/s%2F1`));
    expect(update.request.method).toBe('PUT');
    update.flush({ success: true });

    service.deleteTestSet('s1').subscribe();
    const del = http.expectOne(url(`${QA_TEST_SETS_PATH}/s1`));
    expect(del.request.method).toBe('DELETE');
    del.flush({ success: true });
  });

  it('starts a run of a test set and of a bare tag expression', () => {
    let release = '';
    service.runTestSet('s1', 'qa').subscribe((r) => (release = r.release));
    const run = http.expectOne(url(`${QA_TEST_SETS_PATH}/s1/run`));
    expect(run.request.method).toBe('POST');
    expect(run.request.body).toEqual({ environment: 'qa' });
    run.flush({ responseObject: { release: 'QA_REG_OCT26_R1', queued: true, queueUrl: null } });
    expect(release).toBe('QA_REG_OCT26_R1');

    service.trigger({ workspace: 'API', suite: 'SMOKE', environment: 'dev', tagExpression: '@smoke' }).subscribe();
    const trigger = http.expectOne(url(QA_TRIGGER_PATH));
    expect(trigger.request.body).toEqual({ workspace: 'API', suite: 'SMOKE', environment: 'dev', tagExpression: '@smoke' });
    trigger.flush({ success: true });
  });

  it('reports a server failure as an error', () => {
    let failed = false;
    service.facets('UI').subscribe({ error: () => (failed = true) });
    http.expectOne((r) => r.url === url(QA_FILTERS_PATH)).flush('x', { status: 503, statusText: 'Service Unavailable' });
    expect(failed).toBe(true);
  });
});
