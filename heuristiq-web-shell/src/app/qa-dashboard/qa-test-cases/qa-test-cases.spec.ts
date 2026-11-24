import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqDocument, HqDocumentService, HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { QA_DASHBOARD_PRIVILEGES, QA_EXPORT_FILE_NAME, QA_MESSAGE_KEYS } from '../qa-dashboard.constants';
import { accessMock, facets, QA_SHARED_DECLARATIONS, routerStub, routeStub, row, testCasePage } from '../qa-dashboard.fixtures';
import { emptyFilters } from '../qa-dashboard.helpers';
import { QaFacets, QaFilters, QaTableKind, QaTestCaseDetail, QaTestCasePage, QaTestCaseResult, QaWorkspace } from '../qa-dashboard.models';
import { QaDashboardService } from '../qa-dashboard.service';
import { QaEvidenceService } from '../qa-evidence/qa-evidence.service';
import { QaEvidenceThumbnail } from '../qa-evidence/qa-evidence-thumbnail';
import { QaTestCasesPage } from './qa-test-cases';

const P = QA_DASHBOARD_PRIVILEGES;

describe('QaTestCasesPage', () => {
  const svc = {
    testCases: vi.fn((_w: QaWorkspace, _f: QaFilters, _p: number, _s: number): Observable<QaTestCasePage> => of(testCasePage([row('a'), row('b', { status: 'PASSED', failureCategory: null })]))),
    testCase: vi.fn((_id: string): Observable<QaTestCaseDetail> => of({ testCase: row('a'), history: [{ runId: 'r1', release: 'QA_REG_OCT26_R2', status: 'FAILED', durationMs: 5000, startedAt: '2026-10-26T10:00:00Z', failureMessage: 'x' }] })),
    deleteTestCase: vi.fn((_id: string): Observable<void> => of(undefined)),
    exportCsv: vi.fn((_w: QaWorkspace, _f: QaFilters): Observable<Blob> => of(new Blob(['a,b']))),
    facets: vi.fn((_w: QaWorkspace): Observable<QaFacets> => of(facets())),
  };
  const dialogs = { confirm: vi.fn((_d: unknown): Observable<boolean> => of(true)) };
  const notify = { success: vi.fn(), error: vi.fn() };
  const evidenceService = { open: vi.fn((_id: string): Observable<boolean> => of(true)) };
  const documents = {
    getMetadata: vi.fn((id: string): Observable<HqDocument> => of({ id, originalFileName: 'x.png', contentType: 'image/png', sizeBytes: 100 } as HqDocument)),
    fetchFile: vi.fn((_doc: HqDocument): Observable<Blob> => of(new Blob(['x'], { type: 'image/png' }))),
  };
  const router = routerStub();
  let granted: Set<string> | null;
  let route: ReturnType<typeof routeStub>;
  let fixture: ComponentFixture<QaTestCasesPage>;
  /** File names the browser was asked to download, and the blobs behind them (saveBlob clicks a hidden link). */
  let downloads: string[];
  const createObjectURL = vi.fn((_blob: Blob) => 'blob:qa');

  function create(kind: QaTableKind | undefined, params: Record<string, string> = {}): QaTestCasesPage {
    route = routeStub(params, kind ? { kind } : {});
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaTestCasesPage, ...QA_SHARED_DECLARATIONS],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule, QaEvidenceThumbnail],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: QaDashboardService, useValue: svc },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
        { provide: ActivatedRoute, useValue: route.route },
        { provide: Router, useValue: router },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
        { provide: QaEvidenceService, useValue: evidenceService },
        { provide: HqDocumentService, useValue: documents },
      ],
    });
    fixture = TestBed.createComponent(QaTestCasesPage);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = (selector: string): HTMLElement | null => el().querySelector(selector);
  afterEach(() => vi.restoreAllMocks());

  const refresh = (): void => {
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  };
  const headers = (): string[] => Array.from(el().querySelectorAll('thead th')).map((th) => th.textContent?.trim() ?? '');

  beforeEach(() => {
    vi.clearAllMocks();
    evidenceService.open.mockImplementation(() => of(true));
    documents.getMetadata.mockImplementation((id: string) => of({ id, originalFileName: 'x.png', contentType: 'image/png', sizeBytes: 100 } as HqDocument));
    documents.fetchFile.mockImplementation(() => of(new Blob(['x'], { type: 'image/png' })));
    URL.createObjectURL = vi.fn(() => 'blob:qa');
    URL.revokeObjectURL = vi.fn();
    downloads = [];
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloads.push(this.download);
    });
    svc.testCases.mockImplementation(() => of(testCasePage([row('a'), row('b', { status: 'PASSED', failureCategory: null })])));
    svc.testCase.mockImplementation(() => of({ testCase: row('a'), history: [{ runId: 'r1', release: 'QA_REG_OCT26_R2', status: 'FAILED', durationMs: 5000, startedAt: '2026-10-26T10:00:00Z', failureMessage: 'x' }] }));
    svc.deleteTestCase.mockImplementation(() => of(undefined));
    svc.exportCsv.mockImplementation(() => of(new Blob(['a,b'])));
    svc.facets.mockImplementation(() => of(facets()));
    dialogs.confirm.mockImplementation(() => of(true));
    granted = null;
  });

  it('shows the inventory by default, with its columns, and asks for every suite', () => {
    const page = create(undefined);
    expect(page.kind).toBe('inventory');
    expect(svc.testCases).toHaveBeenCalledWith('UI', expect.objectContaining({ suite: '' }), 0, 20);
    expect(headers()).toEqual([
      'qaDashboard.columns.tcId', 'qaDashboard.columns.testCase', 'qaDashboard.columns.module', 'qaDashboard.columns.subModule',
      'qaDashboard.columns.environment', 'qaDashboard.columns.iteration', 'qaDashboard.columns.status', 'qaDashboard.columns.release',
      'qaDashboard.columns.date', 'qaDashboard.columns.actions',
    ]);
    expect(el().querySelectorAll('.qa-row')).toHaveLength(2);
    expect(q('.qa-row .qa-mono')?.textContent).toContain('HQ_RW_036');
    expect(q('.qa-totals__total')?.textContent).toBe('2');
  });

  it.each([
    ['regression', 'REGRESSION'],
    ['smoke', 'SMOKE'],
  ] as const)('fixes the %s suite and keeps it when the filters are cleared', (kind, suite) => {
    const page = create(kind);
    expect(svc.testCases).toHaveBeenLastCalledWith('UI', expect.objectContaining({ suite }), 0, 20);
    expect(page.filters.suite).toBe(suite);
    expect(page.isFiltered).toBe(false);
    page.onFiltersChange(emptyFilters());
    expect(svc.testCases).toHaveBeenLastCalledWith('UI', expect.objectContaining({ suite }), 0, 20);
    page.onFiltersChange(emptyFilters({ status: 'FAILED' }));
    expect(page.isFiltered).toBe(true);
    expect(page.filters.suite).toBe(suite);
    expect(q('.qa-suite')).toBeNull();
  });

  it('shows the pass, fail and skip counts on the execution summary and no delete', () => {
    create('executions');
    expect(headers()).toContain('qaDashboard.columns.passCount');
    expect(headers()).toContain('qaDashboard.columns.category');
    expect(q('.qa-delete')).toBeNull();
    expect(q('.qa-suite')).not.toBeNull();
  });

  it('reads the workspace from the address and reloads from the first page when it changes', () => {
    create('smoke', { workspace: 'API' });
    expect(svc.testCases).toHaveBeenLastCalledWith('API', expect.anything(), 0, 20);
    route.setParams({ workspace: 'UI' });
    expect(svc.testCases).toHaveBeenLastCalledWith('UI', expect.anything(), 0, 20);
    expect(svc.facets).toHaveBeenCalledTimes(2);
  });

  it('pages through the results', () => {
    const page = create('inventory');
    svc.testCases.mockImplementation((_w, _f, p) => of(testCasePage([row('a')], { page: p, totalPages: 3, totalElements: 50 })));
    page.loadPage(0);
    refresh();
    expect(q('.qa-pager')).not.toBeNull();
    (q('.qa-next') as HTMLButtonElement).click();
    expect(svc.testCases).toHaveBeenLastCalledWith('UI', expect.anything(), 1, 20);
    refresh();
    (q('.qa-prev') as HTMLButtonElement).click();
    expect(svc.testCases).toHaveBeenLastCalledWith('UI', expect.anything(), 0, 20);
    // Already on the first page: no earlier page to ask for.
    page.previousPage();
    expect(svc.testCases).toHaveBeenCalledTimes(4);
    page.loadPage(2);
    page.nextPage();
    // Page 2 of 3 is the last one: no later page to ask for.
    expect(svc.testCases).toHaveBeenCalledTimes(5);
  });

  it('shows an empty message, a different one when filters are set', () => {
    svc.testCases.mockImplementation(() => of(testCasePage([])));
    const page = create('inventory');
    expect(q('.qa-empty')?.textContent).toContain('qaDashboard.testCases.empty');
    page.onFiltersChange(emptyFilters({ q: 'zzz' }));
    refresh();
    expect(q('.qa-empty')?.textContent).toContain('qaDashboard.testCases.emptyFiltered');
  });

  it('shows an error message when the list cannot be loaded', () => {
    svc.testCases.mockImplementation(() => throwError(() => new Error('down')));
    const page = create('inventory');
    expect(page.hasError).toBe(true);
    expect(page.result).toBeNull();
    expect(q('.qa-state--error')).not.toBeNull();
  });

  it('shows a loading message while waiting', () => {
    svc.testCases.mockImplementation(() => NEVER);
    create('inventory');
    expect(q('.qa-state')?.textContent).toContain('qaDashboard.loading');
  });

  describe('details', () => {
    it('shows the history of a test case and closes it again', () => {
      const page = create('inventory');
      (q('.qa-details') as HTMLButtonElement).click();
      refresh();
      expect(svc.testCase).toHaveBeenCalledWith('a');
      expect(q('.qa-detail h3')?.textContent).toContain('Screen wallet.agent_load opens');
      expect(el().querySelectorAll('.qa-table--history tbody tr')).toHaveLength(1);
      expect(q('.qa-failure')?.textContent).toContain('TIMEOUT');
      (q('.qa-detail__close') as HTMLButtonElement).click();
      refresh();
      expect(page.selected).toBeNull();
      expect(q('.qa-detail')).toBeNull();
    });

    it('says when there is no history', () => {
      svc.testCase.mockImplementation(() => of({ testCase: row('a', { failureMessage: null }), history: [] }));
      create('inventory');
      (q('.qa-details') as HTMLButtonElement).click();
      refresh();
      expect(q('.qa-detail .qa-state')?.textContent).toContain('qaDashboard.testCases.noHistory');
      expect(q('.qa-failure')).toBeNull();
    });

    it('shows an error when the details cannot be loaded', () => {
      svc.testCase.mockImplementation(() => throwError(() => new Error('down')));
      const page = create('inventory');
      (q('.qa-details') as HTMLButtonElement).click();
      refresh();
      expect(page.detailError).toBe(true);
      expect(q('.qa-detail .qa-state--error')).not.toBeNull();
      page.closeDetails();
      expect(page.detailError).toBe(false);
    });

    it('does not open details without the read privilege', () => {
      granted = new Set([P.testCaseList]);
      const page = create('inventory');
      expect(q('.qa-details')).toBeNull();
      page.openDetails(row('a'));
      expect(svc.testCase).not.toHaveBeenCalled();
    });
  });

  describe('delete', () => {
    it('asks first, then deletes and reloads the page', () => {
      const page = create('inventory');
      (q('.qa-delete') as HTMLButtonElement).click();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: QA_MESSAGE_KEYS.deleteTitle, variant: 'danger' }));
      expect(svc.deleteTestCase).toHaveBeenCalledWith('a');
      expect(notify.success).toHaveBeenCalledWith(QA_MESSAGE_KEYS.deleted);
      expect(svc.testCases).toHaveBeenCalledTimes(2);
      expect(page.busyId).toBeNull();
    });

    it('does nothing when the user declines', () => {
      dialogs.confirm.mockImplementation(() => of(false));
      create('inventory');
      (q('.qa-delete') as HTMLButtonElement).click();
      expect(svc.deleteTestCase).not.toHaveBeenCalled();
    });

    it('reports a failed delete and keeps the list', () => {
      svc.deleteTestCase.mockImplementation(() => throwError(() => new Error('down')));
      const page = create('inventory');
      (q('.qa-delete') as HTMLButtonElement).click();
      expect(notify.error).toHaveBeenCalledWith(QA_MESSAGE_KEYS.deleteFailed);
      expect(page.busyId).toBeNull();
      expect(svc.testCases).toHaveBeenCalledTimes(1);
    });

    it('goes back a page when the last row of a later page is deleted, and closes its details', () => {
      svc.testCases.mockImplementation((_w, _f, p) => of(testCasePage([row('a')], { page: p, totalPages: 2, totalElements: 21 })));
      const page = create('inventory');
      page.loadPage(1);
      page.selected = { testCase: row('a'), history: [] };
      page.confirmDelete(row('a'));
      expect(svc.testCases).toHaveBeenLastCalledWith('UI', expect.anything(), 0, 20);
      expect(page.selected).toBeNull();
    });

    it('hides the delete action from a user without the privilege and from the other tables', () => {
      granted = new Set([P.testCaseList, P.testCaseRead]);
      const page = create('inventory');
      expect(q('.qa-delete')).toBeNull();
      page.confirmDelete(row('a'));
      expect(dialogs.confirm).not.toHaveBeenCalled();
      granted = null;
      const regression = create('regression');
      regression.confirmDelete(row('a'));
      expect(dialogs.confirm).not.toHaveBeenCalled();
    });
  });

  describe('export', () => {
    it('saves the filtered table as a file', () => {
      const page = create('regression');
      page.onFiltersChange(emptyFilters({ status: 'FAILED' }));
      (q('.qa-export') as HTMLButtonElement).click();
      expect(svc.exportCsv).toHaveBeenCalledWith('UI', expect.objectContaining({ status: 'FAILED', suite: 'REGRESSION' }));
      expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
      expect(downloads).toEqual([QA_EXPORT_FILE_NAME]);
    });

    it('reports a failed export', () => {
      svc.exportCsv.mockImplementation(() => throwError(() => new Error('down')));
      create('regression');
      (q('.qa-export') as HTMLButtonElement).click();
      expect(notify.error).toHaveBeenCalledWith(QA_MESSAGE_KEYS.exportFailed);
      expect(downloads).toEqual([]);
    });

    it('has no export without the privilege', () => {
      granted = new Set([P.testCaseList]);
      const page = create('regression');
      expect(q('.qa-export')).toBeNull();
      page.export();
      expect(svc.exportCsv).not.toHaveBeenCalled();
    });
  });

  it('asks for nothing and shows nothing without the list privilege', () => {
    granted = new Set([P.testCaseRead]);
    create('inventory');
    expect(svc.testCases).not.toHaveBeenCalled();
    expect(q('app-qa-filter-bar')).toBeNull();
  });

  describe('full report', () => {
    it('opens the report of the run in a new tab, safely, only for rows that have a safe address', () => {
      svc.testCases.mockImplementation(() => of(testCasePage([
        row('a'),
        row('b', { reportUrl: 'javascript:alert(1)' }),
        row('c', { reportUrl: null }),
        row('d', { reportUrl: undefined }),
      ])));
      create('regression');
      const links = el().querySelectorAll('.qa-row .qa-report');
      expect(links).toHaveLength(1);
      expect(links[0].getAttribute('href')).toBe('https://reports.example.com/qa/run-1/index.html');
      expect(links[0].getAttribute('target')).toBe('_blank');
      expect(links[0].getAttribute('rel')).toBe('noopener noreferrer');
      expect(links[0].textContent).toContain('qaDashboard.openReport');
    });

    it('is offered also to a user who can only list', () => {
      granted = new Set([P.testCaseList]);
      create('inventory');
      expect(el().querySelectorAll('.qa-row .qa-report').length).toBeGreaterThan(0);
    });

    it('opens the report of an earlier run from the history', () => {
      svc.testCase.mockImplementation(() => of({
        testCase: row('a'),
        history: [
          { runId: 'r1', release: 'QA_REG_OCT26_R2', status: 'FAILED', durationMs: 1000, startedAt: '2026-10-26T10:00:00Z', failureMessage: null, reportUrl: 'https://reports.example.com/qa/r1/index.html' },
          { runId: 'r0', release: 'QA_REG_OCT25_R1', status: 'PASSED', durationMs: 1000, startedAt: '2026-10-25T10:00:00Z', failureMessage: null, reportUrl: 'ftp://nope' },
        ],
      }));
      create('inventory');
      (q('.qa-details') as HTMLButtonElement).click();
      refresh();
      const links = el().querySelectorAll('.qa-report-history');
      expect(links).toHaveLength(1);
      expect(links[0].getAttribute('href')).toBe('https://reports.example.com/qa/r1/index.html');
      expect(links[0].getAttribute('rel')).toBe('noopener noreferrer');
    });
  });

  describe('screenshots', () => {
    const history: QaTestCaseResult[] = [
      { runId: 'r1', release: 'QA_REG_OCT26_R2', status: 'FAILED', durationMs: 1000, startedAt: '2026-10-26T10:00:00Z', failureMessage: null, evidence: [{ documentId: 'shot-ok', kind: 'SCREEN', name: 'screen' }, { documentId: 'shot-fail', kind: 'FAILURE', name: 'failure' }] },
      { runId: 'r0', release: 'QA_REG_OCT25_R1', status: 'PASSED', durationMs: 1000, startedAt: '2026-10-25T10:00:00Z', failureMessage: null, evidence: [{ documentId: 'bad/id', kind: 'SCREEN', name: 'x' }] },
      { runId: 'r-1', release: 'QA_REG_OCT24_R1', status: 'PASSED', durationMs: 1000, startedAt: '2026-10-24T10:00:00Z', failureMessage: null },
    ];

    function openDetails(): QaTestCasesPage {
      svc.testCase.mockImplementation(() => of({ testCase: row('a'), history }));
      const page = create('inventory');
      (q('.qa-details') as HTMLButtonElement).click();
      refresh();
      return page;
    }

    it('shows both screenshots of a past result as pictures', () => {
      openDetails();
      refresh();
      const previews = Array.from(el().querySelectorAll('app-qa-evidence-thumbnail'));
      expect(previews).toHaveLength(2);
      expect(previews[0].querySelector('img')?.getAttribute('src')).toBe('blob:qa');
      expect(documents.getMetadata).toHaveBeenCalledWith('shot-fail');
      expect(documents.getMetadata).toHaveBeenCalledWith('shot-ok');
      expect(previews.map((p) => p.querySelector('button')?.getAttribute('data-kind'))).toEqual(['FAILURE', 'SCREEN']);
      expect(el().querySelectorAll('.qa-evidence-link')).toHaveLength(0);
      Array.from(el().querySelectorAll('img')).forEach((i) => expect(i.getAttribute('src')?.startsWith('blob:')).toBe(true));
    });

    it('shows the old button for the failure screenshot when it cannot be loaded as a picture', () => {
      documents.getMetadata.mockImplementation(() => throwError(() => new Error('gone')));
      openDetails();
      refresh();
      expect(el().querySelector('img')).toBeNull();
      const links = Array.from(el().querySelectorAll('.qa-evidence-link'));
      expect(links.map((l) => l.getAttribute('data-kind'))).toEqual(['FAILURE', 'SCREEN']);
      expect(links[0].textContent).toContain('qaDashboard.evidence.failure');
    });

    it('opens a screenshot with the shared opener, once at a time', () => {
      const page = openDetails();
      refresh();
      (el().querySelector('.qa-thumb__button') as HTMLButtonElement).click();
      expect(evidenceService.open).toHaveBeenCalledWith('shot-fail');
      (el().querySelectorAll('.qa-thumb__button')[1] as HTMLButtonElement).click();
      expect(evidenceService.open).toHaveBeenCalledWith('shot-ok');
      expect(page.openingEvidence).toBeNull();
      evidenceService.open.mockImplementation(() => NEVER);
      page.openEvidence({ documentId: 'a', kind: 'SCREEN', name: 'x' });
      page.openEvidence({ documentId: 'b', kind: 'SCREEN', name: 'x' });
      expect(evidenceService.open).toHaveBeenCalledTimes(3);
      expect(page.evidenceLabel({ documentId: 'a', kind: 'SCREEN', name: 'x' })).toBe('qaDashboard.evidence.screen');
    });

    it('hides them from a user who cannot read the DMS', () => {
      granted = new Set([P.testCaseList, P.testCaseRead]);
      openDetails();
      refresh();
      expect(el().querySelector('.qa-evidence-link')).toBeNull();
      expect(el().querySelector('app-qa-evidence-thumbnail')).toBeNull();
      expect(documents.getMetadata).not.toHaveBeenCalled();
    });
  });

  it('shows the short failure message of a row', () => {
    const page = create('inventory');
    expect(page.message(row('a'))).toBe('Timed out after 20 seconds');
    expect(page.has('tcId')).toBe(true);
    expect(page.has('category')).toBe(false);
    expect(page.trackByRow(0, row('z'))).toBe('z');
  });
});
