import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqDocument, HqDocumentService, HqOpenFile, HqChartsModule, HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { QaEvidenceService } from '../qa-dashboard/qa-evidence/qa-evidence.service';
import { QaEvidenceThumbnail } from '../qa-dashboard/qa-evidence/qa-evidence-thumbnail';
import { NotificationService } from '../shared/services/notification-service';
import { stubHqCharts } from '../shared/testing/stub-charts.testing';
import { QA_PRIVILEGES } from './system-qa.constants';
import { QaDocument, QaDocumentSummary, QaPdfFile, QaRunDetail, QaRunPage, QaRunSummary, QaScenario } from './system-qa.models';
import { SystemQaService } from './system-qa.service';
import { SystemQa } from './system-qa';

const P = QA_PRIVILEGES;

const run = (id: string, over: Partial<QaRunSummary> = {}): QaRunSummary => ({
  id,
  environment: 'qa',
  tags: '@smoke',
  status: 'FAILED',
  total: 4,
  passed: 2,
  failed: 1,
  skipped: 1,
  durationMs: 184_000,
  startedAt: '2026-10-01T10:00:00Z',
  jenkinsJob: 'qa-nightly',
  jenkinsBuildNumber: 42,
  jenkinsUrl: 'https://jenkins.example.com/job/qa-nightly/42/',
  documentId: 'dms-1',
  createdAt: '2026-10-01T10:05:00Z',
  ...over,
});

const scenarios = (): QaScenario[] => [
  { feature: 'Login', name: 'passes', tags: ['@smoke'], pilotId: null, status: 'PASSED', durationMs: 10, failureMessage: null },
  { feature: 'Login', name: 'bad otp', tags: ['@smoke', '@A10'], pilotId: 'A10', status: 'FAILED', durationMs: 20, failureMessage: '<img src=x onerror=alert(1)> expected 200',
    evidence: [{ documentId: 'shot-ok', kind: 'SCREEN', name: 'screen' }, { documentId: 'shot-fail', kind: 'FAILURE', name: 'failure' }, { documentId: 'bad/id', kind: 'SCREEN', name: 'x' }] },
  { feature: 'Payments', name: 'skipped one', tags: ['@A2'], pilotId: 'A2', status: 'SKIPPED', durationMs: 0, failureMessage: null },
  { feature: 'Payments', name: 'refund', tags: [], pilotId: null, status: 'PASSED', durationMs: 30, failureMessage: null },
];

const detail = (id: string, over: Partial<QaRunDetail> = {}): QaRunDetail => ({ ...run(id), scenarios: scenarios(), ...over });
const page = (runs: QaRunSummary[], over: Partial<QaRunPage> = {}): QaRunPage => ({ content: runs, totalElements: runs.length, totalPages: 1, size: 10, number: 0, ...over });
const docSummary = (name: string, updatedAt: string): QaDocumentSummary => ({ name, title: `Title ${name}`, updatedAt, sizeBytes: 2048 });

describe('SystemQa', () => {
  const svc = {
    runs: vi.fn((_p?: number, _s?: number, _e?: string): Observable<QaRunPage> => of(page([]))),
    recentRuns: vi.fn((_e: string, _n: number): Observable<QaRunSummary[]> => of([])),
    run: vi.fn((_id: string): Observable<QaRunDetail> => of(detail('1'))),
    documentList: vi.fn((): Observable<QaDocumentSummary[]> => of([])),
    document: vi.fn((_n: string): Observable<QaDocument> => EMPTY),
    pdf: vi.fn((_id: string): Observable<QaPdfFile> => EMPTY),
  };
  const notify = { success: vi.fn(), error: vi.fn() };
  const dialog = { open: vi.fn() };
  const evidenceService = { open: vi.fn((_id: string): Observable<boolean> => of(true)) };
  const documents = {
    getMetadata: vi.fn((id: string): Observable<HqDocument> => of({ id, originalFileName: 'x.png', contentType: 'image/png', sizeBytes: 100 } as HqDocument)),
    fetchFile: vi.fn((_doc: HqDocument): Observable<Blob> => of(new Blob(['x'], { type: 'image/png' }))),
  };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };
  let fixture: ComponentFixture<SystemQa>;
  let translate: TranslateService;

  function create(): SystemQa {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [SystemQa],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule, HqChartsModule, QaEvidenceThumbnail],
      providers: [
        { provide: SystemQaService, useValue: svc },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: MatDialog, useValue: dialog },
        { provide: QaEvidenceService, useValue: evidenceService },
        { provide: HqDocumentService, useValue: documents },
      ],
    });
    stubHqCharts();
    translate = TestBed.inject(TranslateService);
    fixture = TestBed.createComponent(SystemQa);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function refresh(): void {
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = (selector: string): HTMLElement | null => el().querySelector(selector);
  const all = (selector: string): HTMLElement[] => Array.from(el().querySelectorAll(selector)) as HTMLElement[];
  const scenarioNames = (): string[] => all('.qa-scenario-row .qa-scenario-name').map((e) => e.textContent?.trim() ?? '');

  beforeEach(() => {
    vi.clearAllMocks();
    evidenceService.open.mockImplementation(() => of(true));
    documents.getMetadata.mockImplementation((id: string) => of({ id, originalFileName: 'x.png', contentType: 'image/png', sizeBytes: 100 } as HqDocument));
    documents.fetchFile.mockImplementation(() => of(new Blob(['x'], { type: 'image/png' })));
    URL.createObjectURL = vi.fn(() => 'blob:qa');
    URL.revokeObjectURL = vi.fn();
    granted = null;
    svc.runs.mockImplementation(() => of(page([run('1'), run('2', { documentId: null, jenkinsUrl: null, status: 'PASSED', environment: 'mystery' })])));
    svc.recentRuns.mockImplementation(() => of([]));
    svc.run.mockImplementation((id: string) => of(detail(id)));
    svc.documentList.mockImplementation(() => of([]));
    svc.document.mockImplementation(() => EMPTY);
    svc.pdf.mockImplementation(() => EMPTY);
  });

  afterEach(() => vi.restoreAllMocks());

  describe('runs tab with every privilege', () => {
    it('loads the first page of runs and shows a row with a status chip per run', () => {
      const c = create();
      expect(svc.runs).toHaveBeenCalledWith(0, 10, '');
      expect(c.tab).toBe('runs');
      expect(all('.qa-run-row')).toHaveLength(2);
      expect(all('.qa-run-row hq-status-badge .hq-badge').map((e) => e.className.match(/hq-badge--(\w+)/)?.[1])).toEqual(['danger', 'success']);
      expect(el().textContent).toContain('systemQa.duration.m');
    });

    it('shows labels from the translations and keeps unknown codes readable', () => {
      const c = create();
      translate.setTranslation('en', { systemQa: { status: { FAILED: 'Failed' }, environments: { qa: 'QA env' }, duration: { m: '{{value}} min', s: '{{value}} s' }, size: { KB: '{{value}} KB' } } });
      translate.use('en');
      refresh();
      expect(c.statusLabel('FAILED')).toBe('Failed');
      expect(c.statusLabel('PASSED')).toBe('PASSED');
      expect(c.statusLabel('WEIRD')).toBe('WEIRD');
      expect(c.environmentLabel('qa')).toBe('QA env');
      expect(c.environmentLabel('mystery')).toBe('mystery');
      expect(c.statusTone('UNSTABLE')).toBe('warning');
      expect(c.statusTone('WEIRD')).toBe('neutral');
      expect(c.durationText(184_000)).toBe('3 min 4 s');
      expect(c.sizeText(2048)).toBe('2 KB');
    });

    it('shows the Jenkins link only for a safe address, with rel=noopener', () => {
      create();
      const links = all('.qa-run-row .qa-jenkins') as HTMLAnchorElement[];
      expect(links).toHaveLength(1);
      expect(links[0].getAttribute('rel')).toContain('noopener');
      expect(links[0].getAttribute('target')).toBe('_blank');
      expect(links[0].getAttribute('href')).toBe('https://jenkins.example.com/job/qa-nightly/42/');
      const c = fixture.componentInstance;
      expect(c.jenkinsLink(run('x', { jenkinsUrl: 'javascript:alert(1)' }))).toBeNull();
    });

    it('offers the full report only for a run with a safe report address, in a new tab with rel=noopener', () => {
      svc.runs.mockImplementation(() => of(page([
        run('1', { reportUrl: 'https://reports.example.com/qa/42/index.html' }),
        run('2', { reportUrl: 'javascript:alert(1)' }),
        run('3'),
      ])));
      const c = create();
      const links = all('.qa-run-row .qa-report') as HTMLAnchorElement[];
      expect(links).toHaveLength(1);
      expect(links[0].getAttribute('href')).toBe('https://reports.example.com/qa/42/index.html');
      expect(links[0].getAttribute('target')).toBe('_blank');
      expect(links[0].getAttribute('rel')).toBe('noopener noreferrer');
      expect(links[0].textContent).toContain('qaDashboard.openReport');
      expect(c.reportLink(run('x', { reportUrl: 'file:///etc/passwd' }))).toBeNull();
      expect(c.reportLink(run('x', { reportUrl: null }))).toBeNull();
      expect(c.reportLink(run('x'))).toBeNull();
    });

    it('shows the QA document button only on runs that have a document id', () => {
      create();
      const rows = all('.qa-run-row');
      expect(rows[0].querySelector('.qa-pdf')).not.toBeNull();
      expect(rows[1].querySelector('.qa-pdf')).toBeNull();
    });

    it('reloads from the first page with the chosen environment and builds the trend only for an environment', () => {
      const trend = [run('3', { passed: 3, failed: 1 }), run('2', { passed: 1, failed: 1 }), run('1', { passed: 0, failed: 0, skipped: 2 })];
      svc.recentRuns.mockImplementation(() => of(trend));
      const c = create();
      expect(svc.recentRuns).not.toHaveBeenCalled();
      expect(el().textContent).toContain('systemQa.trend.pickEnvironment');

      c.environment = 'uat';
      c.onEnvironmentChange();
      refresh();
      expect(svc.runs).toHaveBeenLastCalledWith(0, 10, 'uat');
      expect(svc.recentRuns).toHaveBeenCalledWith('uat', 30);
      expect(c.trendShown.map((p) => p.value)).toEqual([50, 75]);
      expect(c.trendSpec?.series).toEqual([expect.objectContaining({ data: [50, 75] })]);
      expect(c.trendSpec?.labels).toHaveLength(2);
      expect(c.trendRows.map((r) => r.value)).toEqual([50, 75]);
      expect(c.latestTrendValue).toBe(75);
      expect(q('.qa-trend-box hq-chart')).not.toBeNull();
      expect(el().textContent).not.toContain('systemQa.trend.pickEnvironment');

      c.environment = '';
      c.reload();
      refresh();
      expect(c.trendShown).toEqual([]);
      expect(c.latestTrendValue).toBeNull();
    });

    it('shows an empty-trend message when the environment has no executed runs', () => {
      const c = create();
      c.environment = 'pilot';
      c.reload();
      refresh();
      expect(el().textContent).toContain('systemQa.trend.empty');
    });

    it('ignores a trend answer that arrives after the environment changed again', () => {
      const c = create();
      svc.recentRuns.mockImplementation(() => {
        c.environment = 'live';
        return of([run('9')]);
      });
      c.environment = 'dev';
      c.reload();
      expect(c.trendShown).toEqual([]);
    });

    it('keeps the screen working when the trend cannot be loaded', () => {
      svc.recentRuns.mockImplementation(() => throwError(() => new Error('x')));
      const c = create();
      c.environment = 'dev';
      expect(() => c.reload()).not.toThrow();
      expect(c.trendShown).toEqual([]);
      expect(c.runsError).toBe(false);
    });

    it('pages forwards and backwards within the limits', () => {
      svc.runs.mockImplementation((p = 0) => of(page([run(`p${p}`)], { number: p, totalPages: 3, totalElements: 25 })));
      const c = create();
      c.previousPage();
      expect(svc.runs).toHaveBeenCalledTimes(1);
      c.nextPage();
      expect(svc.runs).toHaveBeenLastCalledWith(1, 10, '');
      c.nextPage();
      expect(c.page).toBe(2);
      c.nextPage();
      expect(svc.runs).toHaveBeenCalledTimes(3);
      c.previousPage();
      expect(c.page).toBe(1);
      refresh();
      expect(q('.qa-pager')).not.toBeNull();
      expect(svc.runs).toHaveBeenCalledTimes(4);
    });

    it('does not show a pager for a single page', () => {
      create();
      expect(q('.qa-pager')).toBeNull();
    });

    it('shows an error when the runs cannot be loaded and an empty message when there are none', () => {
      svc.runs.mockImplementation(() => throwError(() => new Error('x')));
      const c = create();
      expect(c.runsError).toBe(true);
      expect(q('hq-error-state')).not.toBeNull();
      expect(c.isLoadingRuns).toBe(false);

      svc.runs.mockImplementation(() => of(page([])));
      c.reload();
      refresh();
      expect(c.runsError).toBe(false);
      expect(el().textContent).toContain('systemQa.runs.empty');
    });

    it('computes the pass rate of a run', () => {
      const c = create();
      expect(c.rateOf(run('1'))).toBe(66.7);
      expect(c.rateOf(run('1', { passed: 0, failed: 0 }))).toBeNull();
    });
  });

  describe('run detail tab', () => {
    function openRun(): SystemQa {
      const c = create();
      (q('.qa-open') as HTMLButtonElement).click();
      refresh();
      return c;
    }

    it('shows the full report button in the run detail when the run has one', () => {
      svc.run.mockImplementation(() => of(detail('1', { reportUrl: 'https://reports.example.com/qa/1/index.html' })));
      openRun();
      const link = q('.qa-detail-actions .qa-report') as HTMLAnchorElement;
      expect(link.getAttribute('href')).toBe('https://reports.example.com/qa/1/index.html');
      expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    });

    it('has no report button in the run detail when the run has no report', () => {
      svc.run.mockImplementation(() => of(detail('1')));
      openRun();
      expect(q('.qa-detail-actions .qa-report')).toBeNull();
    });

    it('shows both screenshots of a scenario as pictures in the row, and none for an id that is not a plain id', () => {
      openRun();
      refresh();
      const previews = all('.qa-scenario-row app-qa-evidence-thumbnail');
      expect(previews).toHaveLength(2);
      const img = previews[0].querySelector('img') as HTMLImageElement;
      expect(img.getAttribute('src')).toBe('blob:qa');
      expect(documents.getMetadata).toHaveBeenCalledWith('shot-fail');
      expect(documents.getMetadata).toHaveBeenCalledWith('shot-ok');
      expect(documents.getMetadata).not.toHaveBeenCalledWith('bad/id');
      expect(previews.map((p) => p.querySelector('button')?.getAttribute('data-kind'))).toEqual(['FAILURE', 'SCREEN']);
      expect(all('.qa-scenario-row .qa-evidence-link')).toHaveLength(0);
      // Only the failed scenario has screenshots, and the only pictures are Blob URLs made from downloaded bytes.
      expect(el().querySelectorAll('.qa-scenario-row .qa-evidence')).toHaveLength(1);
      all('img').forEach((i) => expect(i.getAttribute('src')?.startsWith('blob:')).toBe(true));
    });

    it('shows the old button for the failure screenshot when it cannot be loaded as a picture', () => {
      documents.getMetadata.mockImplementation(() => throwError(() => new Error('gone')));
      openRun();
      refresh();
      expect(el().querySelector('.qa-scenario-row img')).toBeNull();
      const links = all('.qa-scenario-row .qa-evidence-link');
      expect(links.map((l) => l.getAttribute('data-kind'))).toEqual(['FAILURE', 'SCREEN']);
      expect(links[0].textContent).toContain('qaDashboard.evidence.failure');
    });

    it('opens a screenshot with the shared opener, once at a time', () => {
      const c = openRun();
      refresh();
      (el().querySelector('.qa-thumb__button') as HTMLButtonElement).click();
      expect(evidenceService.open).toHaveBeenCalledWith('shot-fail');
      (all('.qa-thumb__button')[1] as HTMLButtonElement).click();
      expect(evidenceService.open).toHaveBeenCalledWith('shot-ok');
      expect(c.openingEvidence).toBeNull();
      evidenceService.open.mockImplementation(() => NEVER);
      c.openEvidence({ documentId: 'a', kind: 'SCREEN', name: 'x' });
      c.openEvidence({ documentId: 'b', kind: 'SCREEN', name: 'x' });
      expect(evidenceService.open).toHaveBeenCalledTimes(3);
      expect(c.openingEvidence).toBe('a');
      expect(c.evidenceLabel({ documentId: 'a', kind: 'FAILURE', name: 'x' })).toBe('qaDashboard.evidence.failure');
    });

    it('hides the screenshots from a user who cannot read the DMS', () => {
      granted = new Set([P.runList, P.runRead]);
      openRun();
      refresh();
      expect(el().querySelector('.qa-evidence-link')).toBeNull();
      expect(el().querySelector('app-qa-evidence-thumbnail')).toBeNull();
      expect(documents.getMetadata).not.toHaveBeenCalled();
    });

    it('opens a run from the list and shows its summary and the scenarios, failures first', () => {
      const c = openRun();
      expect(svc.run).toHaveBeenCalledWith('1');
      expect(c.tab).toBe('detail');
      expect(all('.qa-card').length).toBeGreaterThanOrEqual(8);
      expect(q('.qa-card--rate')?.textContent).toContain('66.7%');
      expect(scenarioNames()).toEqual(['bad otp', 'skipped one', 'passes', 'refund']);
      expect(all('.qa-row--failed')).toHaveLength(1);
      expect(c.tags).toEqual(['@A10', '@A2', '@smoke']);
      expect(c.pilotIds).toEqual(['A2', 'A10']);
    });

    it('renders the failure message as plain text inside a collapsible block', () => {
      openRun();
      const blocks = all('details.qa-failure');
      expect(blocks).toHaveLength(1);
      const text = blocks[0].querySelector('pre') as HTMLElement;
      expect(text.textContent).toBe('<img src=x onerror=alert(1)> expected 200');
      expect(text.querySelector('img')).toBeNull();
    });

    it('filters by status, text, tag and pilot id, and clears the filters', () => {
      const c = openRun();
      c.filters.status = 'PASSED';
      c.applyFilters();
      refresh();
      expect(scenarioNames()).toEqual(['passes', 'refund']);
      expect(c.hasActiveFilters).toBe(true);

      c.clearFilters();
      c.filters.query = 'payments';
      c.applyFilters();
      refresh();
      expect(scenarioNames()).toEqual(['skipped one', 'refund']);

      c.clearFilters();
      c.filters.tag = '@smoke';
      c.applyFilters();
      refresh();
      expect(scenarioNames()).toEqual(['bad otp', 'passes']);

      c.clearFilters();
      c.filters.pilotId = 'A2';
      c.applyFilters();
      refresh();
      expect(scenarioNames()).toEqual(['skipped one']);

      c.filters.query = 'zzz';
      c.applyFilters();
      refresh();
      expect(scenarioNames()).toEqual([]);
      expect(el().textContent).toContain('systemQa.detail.noneMatching');

      c.clearFilters();
      refresh();
      expect(scenarioNames()).toHaveLength(4);
      expect(c.hasActiveFilters).toBe(false);
    });

    it('says so when a run has no scenarios', () => {
      svc.run.mockImplementation(() => of(detail('1', { scenarios: [] })));
      openRun();
      expect(el().textContent).toContain('systemQa.detail.noScenarios');
    });

    it('resets the filters when another run is opened', () => {
      const c = openRun();
      c.filters.query = 'x';
      c.openRun('2');
      expect(c.filters.query).toBe('');
    });

    it('shows an error tab when the run cannot be loaded, and can go back to the runs', () => {
      svc.run.mockImplementation(() => throwError(() => new Error('x')));
      const c = openRun();
      expect(c.detailError).toBe(true);
      expect(c.tab).toBe('detail');
      expect(q('hq-error-state')).not.toBeNull();
      (q('.qa-tab--runs button') as HTMLButtonElement).click();
      refresh();
      expect(c.tab).toBe('runs');
    });

    it('switches between the runs and the detail tab', () => {
      const c = openRun();
      c.showRuns();
      refresh();
      expect(q('.qa-run-row')).not.toBeNull();
      (q('.qa-tab--detail button') as HTMLButtonElement).click();
      refresh();
      expect(c.tab).toBe('detail');
      c.showRuns();
      c.showDetail();
      expect(c.tab).toBe('detail');
    });

    it('does not open the detail tab before a run was opened', () => {
      const c = create();
      c.showDetail();
      expect(c.tab).toBe('runs');
      expect(q('.qa-tab--detail')).toBeNull();
    });

    it('shows the QA document button in the detail only when the run has a document id', () => {
      svc.run.mockImplementation(() => of(detail('1', { documentId: null })));
      openRun();
      expect(q('.qa-detail-actions .qa-pdf')).toBeNull();

      svc.run.mockImplementation(() => of(detail('1', { documentId: 'dms-9' })));
      openRun();
      expect(q('.qa-detail-actions .qa-pdf')).not.toBeNull();
    });
  });

  describe('QA document (PDF) button', () => {
    const pdf = (type: string): QaPdfFile => ({ blob: new Blob(['x'], { type }), fileName: 'run-42.pdf' });

    it('fetches the PDF by its DMS id and opens it in the shared viewer', () => {
      svc.pdf.mockImplementation(() => of(pdf('application/pdf')));
      create();
      (q('.qa-run-row .qa-pdf') as HTMLButtonElement).click();
      expect(svc.pdf).toHaveBeenCalledWith('dms-1');
      expect(dialog.open).toHaveBeenCalledWith(HqOpenFile, expect.objectContaining({ data: expect.objectContaining({ fileName: 'run-42.pdf' }), maxWidth: '95vw' }));
    });

    it('saves a file the viewer cannot show instead of opening the viewer', () => {
      svc.pdf.mockImplementation(() => of(pdf('application/octet-stream')));
      const create_ = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
      vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
      create();
      (q('.qa-run-row .qa-pdf') as HTMLButtonElement).click();
      expect(dialog.open).not.toHaveBeenCalled();
      expect(create_).toHaveBeenCalled();
    });

    it('tells the user when the PDF cannot be opened and allows a new try', () => {
      svc.pdf.mockImplementation(() => throwError(() => new Error('dms')));
      const c = create();
      c.openPdf(run('1'));
      expect(notify.error).toHaveBeenCalledWith('systemQa.messages.pdfFailed');
      expect(c.openingPdfFor).toBeNull();
    });

    it('does nothing for a run without a document and ignores a second click while one is opening', () => {
      svc.pdf.mockImplementation(() => NEVER);
      const c = create();
      c.openPdf(run('x', { documentId: null }));
      expect(svc.pdf).not.toHaveBeenCalled();
      c.openPdf(run('1'));
      c.openPdf(run('1'));
      expect(svc.pdf).toHaveBeenCalledTimes(1);
      expect(c.openingPdfFor).toBe('1');
    });

    it('is hidden without the DMS privileges, even when the run has a document', () => {
      granted = new Set([P.runList, P.runRead]);
      create();
      expect(q('.qa-pdf')).toBeNull();
      granted = new Set([P.runList, P.dmsMetadataRead]);
      create();
      expect(q('.qa-pdf')).toBeNull();
      granted = new Set([P.runList, P.dmsMetadataRead, P.dmsDownloadUrl]);
      create();
      expect(q('.qa-pdf')).not.toBeNull();
    });
  });

  describe('documents tab', () => {
    const docs = (): QaDocumentSummary[] => [docSummary('plan', '2026-10-01T00:00:00Z'), docSummary('pilot', '2026-10-05T00:00:00Z')];

    function openTab(): SystemQa {
      svc.documentList.mockImplementation(() => of(docs()));
      const c = create();
      (q('.qa-tab--documents button') as HTMLButtonElement).click();
      refresh();
      return c;
    }

    it('lists the documents newest first', () => {
      const c = openTab();
      expect(c.tab).toBe('documents');
      expect(all('.qa-doc-item strong').map((e) => e.textContent)).toEqual(['Title pilot', 'Title plan']);
      expect(el().textContent).toContain('systemQa.size.KB');
    });

    it('loads the list once and reloads it only when it was empty', () => {
      const c = openTab();
      c.showDocuments();
      expect(svc.documentList).toHaveBeenCalledTimes(1);
    });

    it('renders the markdown of the selected document through the sanitizing innerHTML binding', () => {
      svc.document.mockImplementation((name: string) =>
        of({ name, title: 'T', updatedAt: '2026-10-05T00:00:00Z', markdown: '# Pilot\n\n[site](https://example.com)\n\n<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>' }),
      );
      const c = openTab();
      (all('.qa-doc-item')[0] as HTMLButtonElement).click();
      refresh();
      expect(svc.document).toHaveBeenCalledWith('pilot');
      const view = q('.qa-markdown') as HTMLElement;
      expect(view.querySelector('h1')?.textContent).toBe('Pilot');
      const link = view.querySelector('a') as HTMLAnchorElement;
      expect(link.getAttribute('rel')).toBe('noopener noreferrer');
      expect(link.getAttribute('target')).toBe('_blank');
      expect(view.querySelector('script')).toBeNull();
      expect(view.querySelector('img')).toBeNull();
      expect(view.innerHTML).not.toContain('onerror=alert(1)>');
      expect(q('.qa-doc-item--active')).not.toBeNull();
      expect(c.currentDocument?.name).toBe('pilot');
    });

    it('shows an error when a document cannot be opened', () => {
      svc.document.mockImplementation(() => throwError(() => new Error('x')));
      const c = openTab();
      c.openDocument('plan');
      refresh();
      expect(c.documentError).toBe(true);
      expect(el().textContent).toContain('systemQa.documents.openFailed');
    });

    it('shows an error when the list cannot be loaded, and an empty message when there are no documents', () => {
      svc.documentList.mockImplementation(() => throwError(() => new Error('x')));
      const c = create();
      c.showDocuments();
      refresh();
      expect(c.documentsError).toBe(true);
      expect(q('hq-error-state')).not.toBeNull();

      svc.documentList.mockImplementation(() => of([]));
      c.loadDocuments();
      refresh();
      expect(el().textContent).toContain('systemQa.documents.empty');
    });
  });

  describe('privileges', () => {
    it('starts on the documents tab when the user may list documents but not runs', () => {
      granted = new Set([P.documentList, P.documentRead]);
      svc.documentList.mockImplementation(() => of([docSummary('plan', '2026-10-01T00:00:00Z')]));
      const c = create();
      expect(c.tab).toBe('documents');
      expect(svc.runs).not.toHaveBeenCalled();
      expect(q('.qa-tab--runs')).toBeNull();
      expect(q('.qa-tab--documents')).not.toBeNull();
      expect(q('.qa-doc-item')).not.toBeNull();
    });

    it('loads nothing and shows no tabs without any QA privilege', () => {
      granted = new Set();
      const c = create();
      expect(svc.runs).not.toHaveBeenCalled();
      expect(svc.documentList).not.toHaveBeenCalled();
      expect(all('.qa-tab')).toHaveLength(0);
      expect(c.tab).toBe('runs');
      c.loadRuns(0);
      c.loadDocuments();
      c.openRun('1');
      c.openDocument('plan');
      expect(svc.runs).not.toHaveBeenCalled();
      expect(svc.documentList).not.toHaveBeenCalled();
      expect(svc.run).not.toHaveBeenCalled();
      expect(svc.document).not.toHaveBeenCalled();
    });

    it('hides the Open button without the read privilege but still lists the runs', () => {
      granted = new Set([P.runList]);
      create();
      expect(all('.qa-run-row')).toHaveLength(2);
      expect(q('.qa-open')).toBeNull();
    });

    it('hides the document buttons without the document read privilege', () => {
      granted = new Set([P.documentList]);
      svc.documentList.mockImplementation(() => of([docSummary('plan', '2026-10-01T00:00:00Z')]));
      create();
      expect(q('.qa-doc-item')).toBeNull();
    });

    it('never offers an upload: runs and documents are created by Jenkins', () => {
      create();
      expect(q('input[type=file]')).toBeNull();
    });
  });

  it('stops listening when it is destroyed', () => {
    const c = create();
    const spy = vi.spyOn(c['destroy$'], 'complete');
    fixture.destroy();
    expect(spy).toHaveBeenCalled();
  });

  it('tracks rows by id and by name', () => {
    const c = create();
    expect(c.trackByRun(0, run('7'))).toBe('7');
    expect(c.trackByDocument(0, docSummary('plan', ''))).toBe('plan');
  });
});
