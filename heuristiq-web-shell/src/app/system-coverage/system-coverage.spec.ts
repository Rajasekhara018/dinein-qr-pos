import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqChartsModule, HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { NotificationService } from '../shared/services/notification-service';
import { stubHqCharts } from '../shared/testing/stub-charts.testing';
import { COVERAGE_AUTO_REFRESH_MS, COVERAGE_ERROR_KEYS, COVERAGE_MAX_UPLOAD_BYTES, COVERAGE_PRIVILEGES } from './system-coverage.constants';
import {
  CoverageDocument,
  CoverageProject,
  CoverageRunDetail,
  CoverageRunPage,
  CoverageRunSummary,
  CoverageTrendValue,
  CoverageUploadResult,
} from './system-coverage.models';
import { SystemCoverageService } from './system-coverage.service';
import { SystemCoverage } from './system-coverage';

const LIST = COVERAGE_PRIVILEGES.list;
const READ = COVERAGE_PRIVILEGES.read;
const CREATE = COVERAGE_PRIVILEGES.create;

const ago = (ms: number): string => new Date(Date.now() - ms).toISOString();
const HOUR = 3_600_000;
const DAY = 24 * HOUR;
const counts = { projectCount: 4, okCount: 3, missingCount: 1, belowFloorCount: 1, atTargetCount: 1 };

const summary = (runId: string, createdAt = ago(HOUR)): CoverageRunSummary => ({ runId, createdAt, uploadedBy: 'u1', gitBranch: 'dev', gitCommit: 'abc123', summary: counts });

function project(key: string, lines: number, branches: number, over: Partial<CoverageProject> = {}): CoverageProject {
  return {
    key,
    kind: 'web',
    status: 'ok',
    measuredAt: ago(DAY),
    metrics: { lines: { covered: lines, total: 100, pct: lines }, branches: { covered: branches, total: 100, pct: branches }, functions: { covered: 50, total: 100, pct: 50 } },
    groups: [{ name: 'auth', lines: { covered: 1, total: 10, pct: 10 } }],
    ...over,
  };
}

const coverageDocument = (): CoverageDocument => ({
  schemaVersion: 1,
  generatedAt: ago(HOUR),
  source: { gitBranch: 'dev', gitCommit: 'abc123' },
  thresholds: { floor: 70, target: 90 },
  projects: [
    project('Rider_Top', 95, 93),
    project('Rider_Low', 40, 80, { kind: 'backend', groups: [] }),
    project('Rider_Mid', 80, 75, { measuredAt: ago(10 * DAY) }),
    { key: 'Rider_Gone', kind: 'backend', status: 'missing', measuredAt: null, metrics: null, groups: [] },
  ],
});

const detail = (runId: string): CoverageRunDetail => ({ ...summary(runId), document: coverageDocument() });
const page = (runs: CoverageRunSummary[], over: Partial<CoverageRunPage> = {}): CoverageRunPage => ({ content: runs, totalElements: runs.length, totalPages: 1, size: 10, number: 0, ...over });

describe('SystemCoverage', () => {
  const svc = {
    runs: vi.fn((_page?: number, _size?: number): Observable<CoverageRunPage> => of(page([]))),
    run: vi.fn((_id: string): Observable<CoverageRunDetail> => of(detail('1'))),
    trend: vi.fn((_runs: readonly CoverageRunSummary[]): Observable<CoverageTrendValue[]> => of([])),
    upload: vi.fn((_d: CoverageDocument): Observable<CoverageUploadResult> => EMPTY),
  };
  const notify = { success: vi.fn(), error: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };
  let fixture: ComponentFixture<SystemCoverage>;
  let translate: TranslateService;

  function create(): SystemCoverage {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [SystemCoverage],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule, HqChartsModule],
      providers: [
        { provide: SystemCoverageService, useValue: svc },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    stubHqCharts();
    translate = TestBed.inject(TranslateService);
    fixture = TestBed.createComponent(SystemCoverage);
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
  const projectKeys = (): string[] => all('.cov-row .cov-mono').map((e) => e.textContent?.trim() ?? '');

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    svc.runs.mockImplementation(() => of(page([summary('1'), summary('2', ago(2 * DAY))])));
    svc.run.mockImplementation((id: string) => of(detail(id)));
    svc.trend.mockImplementation(() => of([]));
    svc.upload.mockImplementation(() => EMPTY);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('with every privilege', () => {
    it('loads the newest run, its trend and shows the cards and the table worst-first', { timeout: 30_000 }, () => {
      svc.trend.mockImplementation(() => of([{ runId: '2', createdAt: ago(DAY), value: 60 }, { runId: '1', createdAt: ago(HOUR), value: 70.5 }]));
      const c = create();
      expect(svc.runs).toHaveBeenCalledWith(0, 10);
      expect(svc.run).toHaveBeenCalledWith('1');
      expect(svc.trend).toHaveBeenCalledTimes(1);
      expect(c.levelCounts).toEqual({ BELOW_FLOOR: 1, OK: 1, AT_TARGET: 1, MISSING: 1 });
      expect(projectKeys()).toEqual(['Rider_Low', 'Rider_Mid', 'Rider_Top', 'Rider_Gone']);
      expect(all('.cov-card')).toHaveLength(6);
      expect(el().textContent).toContain('abc123');
      expect(c.trendShown.map((v) => v.value)).toEqual([60, 70.5]);
      expect(c.trendSpec).toEqual(expect.objectContaining({ type: 'line', format: 'percent' }));
      expect(c.trendSpec?.labels).toHaveLength(2);
      expect(c.trendSpec?.series).toEqual([expect.objectContaining({ data: [60, 70.5] })]);
      expect(c.trendRows.map((r) => r.value)).toEqual([60, 70.5]);
      expect(c.latestTrendValue).toBe(70.5);
      expect(q('.cov-trend-box hq-chart')).not.toBeNull();
      expect(all('.cov-run')).toHaveLength(2);
      expect(all('.cov-run--active')).toHaveLength(1);
      expect(q('.cov-upload')).not.toBeNull();
    });

    it('colours each project with its status chip and keeps unknown codes readable', () => {
      const c = create();
      translate.setTranslation('en', { systemCoverage: { levels: { BELOW_FLOOR: 'Below floor' }, kinds: { web: 'Web' }, sort: { worst: 'Worst first' } } });
      translate.use('en');
      refresh();
      const chips = all('.cov-row hq-status-badge:not(.cov-stale) .hq-badge');
      expect(chips.map((e) => e.className.match(/hq-badge--(\w+)/)?.[1])).toEqual(['danger', 'warning', 'success', 'neutral']);
      expect(chips[0].textContent?.trim()).toBe('Below floor');
      expect(c.levelLabel('BELOW_FLOOR')).toBe('Below floor');
      expect(c.levelLabel('OK')).toBe('OK');
      expect(c.levelLabel('WEIRD')).toBe('WEIRD');
      expect(c.kindLabel('web')).toBe('Web');
      expect(c.kindLabel('backend')).toBe('backend');
      expect(c.kindLabel('mainframe')).toBe('mainframe');
      expect(c.sortLabel('worst')).toBe('Worst first');
      expect(c.sortLabel('name')).toBe('name');
      expect(c.levelTone('MISSING')).toBe('neutral');
      expect(c.levelTone('NOPE' as never)).toBe('neutral');
    });

    it('marks a project measured more than 7 days ago as stale', () => {
      create();
      const stale = all('.cov-stale');
      expect(stale).toHaveLength(1);
      expect(stale[0].closest('tr')?.textContent).toContain('Rider_Mid');
    });

    it('filters by kind, status and text, and clears the filters', () => {
      const c = create();
      c.filters.kind = 'backend';
      c.applyFilters();
      refresh();
      expect(projectKeys()).toEqual(['Rider_Low', 'Rider_Gone']);
      expect(c.hasActiveFilters).toBe(true);

      c.filters.level = 'MISSING';
      c.applyFilters();
      refresh();
      expect(projectKeys()).toEqual(['Rider_Gone']);

      c.filters.level = 'AT_TARGET';
      c.applyFilters();
      refresh();
      expect(projectKeys()).toEqual([]);
      expect(el().textContent).toContain('systemCoverage.noneMatching');

      c.clearFilters();
      c.filters.query = 'mid';
      c.applyFilters();
      refresh();
      expect(projectKeys()).toEqual(['Rider_Mid']);

      c.clearFilters();
      refresh();
      expect(projectKeys()).toHaveLength(4);
      expect(c.hasActiveFilters).toBe(false);
    });

    it('re-sorts when the sort mode changes', () => {
      const c = create();
      c.sortMode = 'name';
      c.applyFilters();
      refresh();
      expect(projectKeys()).toEqual(['Rider_Gone', 'Rider_Low', 'Rider_Mid', 'Rider_Top']);
      c.sortMode = 'best';
      c.applyFilters();
      refresh();
      expect(projectKeys()).toEqual(['Rider_Top', 'Rider_Mid', 'Rider_Low', 'Rider_Gone']);
    });

    it('expands a row to show the gaps per area, or a note when there are none', () => {
      const c = create();
      expect(q('.cov-groups')).toBeNull();
      all('.cov-toggle')[2].click(); // Rider_Top has one area
      refresh();
      expect(c.isExpanded('Rider_Top')).toBe(true);
      expect(all('.cov-groups__table tbody tr')).toHaveLength(1);
      expect(q('.cov-groups__table')?.textContent).toContain('auth');
      expect(q('.cov-groups__table')?.textContent).toContain('1 / 10');
      expect(all('.cov-toggle')[2].getAttribute('aria-expanded')).toBe('true');

      all('.cov-toggle')[0].click(); // Rider_Low has none
      refresh();
      expect(q('.cov-groups hq-empty-state')?.textContent).toContain('systemCoverage.noGroups');

      all('.cov-toggle')[0].click();
      all('.cov-toggle')[2].click();
      refresh();
      expect(q('.cov-groups')).toBeNull();
    });

    it('opens an older run from the history', () => {
      const c = create();
      svc.run.mockClear();
      all('.cov-run')[1].click();
      refresh();
      expect(svc.run).toHaveBeenCalledWith('2');
      expect(c.selected?.runId).toBe('2');
      expect(all('.cov-run')[1].getAttribute('aria-current')).toBe('true');
    });

    it('pages through the history without touching the trend or the open run', () => {
      svc.runs.mockImplementation((p = 0) => of(page([summary('p' + p)], { number: p, totalPages: 3, totalElements: 21 })));
      const c = create();
      expect(q('.cov-pager')).not.toBeNull();
      expect((all('.cov-pager button')[0] as HTMLButtonElement).disabled).toBe(true);
      svc.trend.mockClear();
      svc.run.mockClear();

      c.nextPage();
      refresh();
      expect(svc.runs).toHaveBeenLastCalledWith(1, 10);
      expect(c.page).toBe(1);
      c.nextPage();
      c.nextPage(); // already on the last page: ignored
      expect(svc.runs).toHaveBeenLastCalledWith(2, 10);
      expect(svc.runs).toHaveBeenCalledTimes(3);
      c.previousPage();
      expect(svc.runs).toHaveBeenLastCalledWith(1, 10);
      c.previousPage();
      c.previousPage(); // already on the first page: ignored
      expect(svc.runs).toHaveBeenCalledTimes(5);
      expect(svc.trend).not.toHaveBeenCalled();
      expect(svc.run).not.toHaveBeenCalled();
    });

    it('reloads on demand', () => {
      const c = create();
      svc.runs.mockClear();
      c.reload();
      expect(svc.runs).toHaveBeenCalledTimes(1);
      (q('.cov-actions > button') as HTMLButtonElement).click();
      expect(svc.runs).toHaveBeenCalledTimes(2);
    });

    it('formats the age of a run and ignores an unreadable date', () => {
      const c = create();
      expect(c.ageOfRun(ago(2 * HOUR + 1000))?.key).toBe('systemCoverage.age.hours');
      expect(c.ageOfRun('x')).toBeNull();
      expect(c.document?.projects).toHaveLength(4);
    });
  });

  describe('live update', () => {
    const EVERY = COVERAGE_AUTO_REFRESH_MS;

    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('asks for the history every 30 seconds and keeps the open run when nothing is new', () => {
      const c = create();
      svc.runs.mockClear();
      svc.run.mockClear();
      vi.advanceTimersByTime(EVERY);
      expect(svc.runs).toHaveBeenCalledTimes(1);
      expect(svc.runs).toHaveBeenCalledWith(0, 10);
      expect(svc.run).not.toHaveBeenCalled();
      expect(c.selected?.runId).toBe('1');
      vi.advanceTimersByTime(EVERY);
      expect(svc.runs).toHaveBeenCalledTimes(2);
    });

    it('refreshes the ages of the open run without reloading it', () => {
      const c = create();
      const before = c.nowMs;
      vi.advanceTimersByTime(EVERY);
      expect(c.nowMs).toBeGreaterThanOrEqual(before + EVERY);
      expect(c.rows.length).toBe(4);
    });

    it('loads and shows a run that arrived since', () => {
      const c = create();
      svc.runs.mockImplementation(() => of(page([summary('3', ago(1000)), summary('1')])));
      svc.run.mockClear();
      vi.advanceTimersByTime(EVERY);
      expect(svc.run).toHaveBeenCalledWith('3');
      expect(c.selected?.runId).toBe('3');
      expect(c.runs[0].runId).toBe('3');
    });

    it('shows nothing new for an empty history', () => {
      svc.runs.mockImplementation(() => of(page([])));
      const c = create();
      vi.advanceTimersByTime(EVERY);
      expect(c.selected).toBeNull();
      expect(c.runsError).toBe(false);
    });

    it('stays quiet while the tab is hidden', () => {
      create();
      svc.runs.mockClear();
      const hidden = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
      vi.advanceTimersByTime(EVERY);
      expect(svc.runs).not.toHaveBeenCalled();
      hidden.mockRestore();
      vi.advanceTimersByTime(EVERY);
      expect(svc.runs).toHaveBeenCalledTimes(1);
    });

    it('leaves older pages, loading and uploading alone', () => {
      const c = create();
      svc.runs.mockClear();
      c.page = 1;
      vi.advanceTimersByTime(EVERY);
      c.page = 0;
      c.isUploading = true;
      vi.advanceTimersByTime(EVERY);
      c.isUploading = false;
      c.isLoadingRuns = true;
      vi.advanceTimersByTime(EVERY);
      expect(svc.runs).not.toHaveBeenCalled();
    });

    it('does not ask without the list privilege', () => {
      granted = new Set<string>([READ]);
      create();
      svc.runs.mockClear();
      vi.advanceTimersByTime(EVERY);
      expect(svc.runs).not.toHaveBeenCalled();
    });

    it('does not show an error when the check fails and keeps what is on screen', () => {
      const c = create();
      svc.runs.mockImplementation(() => throwError(() => new Error('down')));
      vi.advanceTimersByTime(EVERY);
      expect(c.runsError).toBe(false);
      expect(c.runs).toHaveLength(2);
      expect(c.selected?.runId).toBe('1');
    });

    it('stops checking once the screen is closed', () => {
      create();
      fixture.destroy();
      svc.runs.mockClear();
      vi.advanceTimersByTime(EVERY * 3);
      expect(svc.runs).not.toHaveBeenCalled();
    });

    it('tells the reader how often the screen updates', () => {
      const c = create();
      expect(c.autoRefreshSeconds).toBe(30);
      expect(q('.cov-live')?.textContent).toContain('systemCoverage.autoRefresh');
    });
  });

  describe('states', () => {
    it('shows the empty state when no run was uploaded yet', () => {
      svc.runs.mockImplementation(() => of(page([])));
      const c = create();
      expect(el().textContent).toContain('systemCoverage.empty');
      expect(c.selected).toBeNull();
      expect(q('.cov-section')).toBeNull();
      expect(q('.cov-history')).toBeNull();
      expect(svc.run).not.toHaveBeenCalled();
    });

    it('clears a previously shown run when the history becomes empty', () => {
      const c = create();
      expect(c.rows.length).toBeGreaterThan(0);
      svc.runs.mockImplementation(() => of(page([])));
      c.reload();
      refresh();
      expect(c.selected).toBeNull();
      expect(c.rows).toEqual([]);
      expect(c.levelCounts).toEqual({ BELOW_FLOOR: 0, OK: 0, AT_TARGET: 0, MISSING: 0 });
    });

    it('shows an error when the history cannot be loaded', () => {
      svc.runs.mockImplementation(() => throwError(() => new Error('down')));
      const c = create();
      expect(c.runsError).toBe(true);
      expect(c.isLoadingRuns).toBe(false);
      expect(q('hq-error-state')?.textContent).toContain('systemCoverage.loadFailed');
    });

    it('shows an error when the run cannot be loaded', () => {
      svc.run.mockImplementation(() => throwError(() => new Error('down')));
      const c = create();
      expect(c.detailError).toBe(true);
      expect(c.selected).toBeNull();
      expect(el().textContent).toContain('systemCoverage.messages.runLoadFailed');
    });

    it('shows loading while the history is on its way', () => {
      const pending = new Subject<CoverageRunPage>();
      svc.runs.mockImplementation(() => pending);
      const c = create();
      expect(c.isLoadingRuns).toBe(true);
      expect(el().textContent).toContain('systemCoverage.loading');
      pending.next(page([summary('1')]));
      pending.complete();
      refresh();
      expect(c.isLoadingRuns).toBe(false);
    });

    it('shows a note when the run lists no projects', () => {
      svc.run.mockImplementation(() => of({ ...detail('1'), document: { ...coverageDocument(), projects: [] } }));
      create();
      expect(el().textContent).toContain('systemCoverage.noProjects');
      expect(q('.cov-table')).toBeNull();
    });

    it('draws no trend line without data', () => {
      create();
      expect(q('.cov-trend')).toBeNull();
      expect(el().textContent).toContain('systemCoverage.trend.empty');
    });

    it('skips the trend when the reader lacks the read privilege and stops listening after destroy', () => {
      granted = new Set([LIST]);
      const c = create();
      expect(svc.trend).not.toHaveBeenCalled();
      expect(c.trendShown).toEqual([]);
      expect(c.latestTrendValue).toBeNull();
      fixture.destroy();
      c.loadRuns(0);
      expect(c.isLoadingRuns).toBe(false);
    });

    it('ignores a late answer after the screen is destroyed', () => {
      const pending = new Subject<CoverageRunPage>();
      svc.runs.mockImplementation(() => pending);
      const c = create();
      fixture.destroy();
      pending.next(page([summary('9')]));
      expect(c.runs).toEqual([]);
    });
  });

  describe('privileges', () => {
    it('shows only the history to someone who may list but not read', () => {
      granted = new Set([LIST]);
      const c = create();
      expect(svc.runs).toHaveBeenCalled();
      expect(svc.run).not.toHaveBeenCalled();
      expect(q('.cov-section')).toBeNull();
      expect(all('.cov-run')).toHaveLength(2);
      expect(all('.cov-run').every((b) => (b as HTMLButtonElement).disabled)).toBe(true);
      expect(q('.cov-upload')).toBeNull();
      expect(c.canRead).toBe(false);
      c.select('1');
      expect(svc.run).not.toHaveBeenCalled();
    });

    it('shows neither the history nor a request to someone who may only read', () => {
      granted = new Set([READ]);
      create();
      expect(svc.runs).not.toHaveBeenCalled();
      expect(q('.cov-history')).toBeNull();
      expect(q('.cov-section')).toBeNull();
      expect(q('.cov-upload')).toBeNull();
    });

    it('shows nothing and calls nothing without any privilege', () => {
      granted = new Set();
      create();
      expect(svc.runs).not.toHaveBeenCalled();
      expect(q('.cov-section')).toBeNull();
      expect(q('.cov-history')).toBeNull();
      expect(q('.cov-upload')).toBeNull();
      expect(q('.cov-state')).toBeNull();
    });

    it('shows the upload button only with the create privilege', () => {
      granted = new Set([LIST, READ]);
      create();
      expect(q('.cov-upload')).toBeNull();
      expect(q('hq-file-drop')).toBeNull();
      expect(q('.cov-section')).not.toBeNull();

      granted = new Set([CREATE]);
      create();
      expect(q('.cov-upload')).not.toBeNull();
      expect(q('hq-file-drop')).not.toBeNull();
      expect(q('.cov-section')).toBeNull();

      granted = new Set([LIST, READ, CREATE]);
      create();
      expect(q('.cov-upload')).not.toBeNull();
      expect(q('.cov-section')).not.toBeNull();
      expect(q('.cov-history')).not.toBeNull();
    });
  });

  describe('upload', () => {
    const goodText = (): string => JSON.stringify(coverageDocument());
    const uploaded: CoverageUploadResult = { runId: 'new-run', createdAt: ago(0), summary: counts };

    it('validates, posts, confirms and reloads showing the new run', () => {
      svc.upload.mockImplementation(() => of(uploaded));
      const c = create();
      svc.runs.mockClear();
      svc.run.mockClear();
      c.uploadText(goodText());
      expect(svc.upload).toHaveBeenCalledTimes(1);
      expect((svc.upload.mock.calls[0][0] as CoverageDocument).schemaVersion).toBe(1);
      expect(notify.success).toHaveBeenCalledWith('systemCoverage.upload.success');
      expect(svc.runs).toHaveBeenCalledTimes(1);
      expect(svc.run).toHaveBeenCalledWith('new-run');
      expect(c.isUploading).toBe(false);
    });

    it('passes the run id to the confirmation text', () => {
      svc.upload.mockImplementation(() => of(uploaded));
      const c = create();
      translate.setTranslation('en', { systemCoverage: { upload: { success: 'Run {{runId}} stored' } } });
      translate.use('en');
      c.uploadText(goodText());
      expect(notify.success).toHaveBeenCalledWith('Run new-run stored');
    });

    it('rejects a file that is not a collector document without calling the server', () => {
      const c = create();
      c.uploadText('{nope');
      c.uploadText(JSON.stringify({ ...coverageDocument(), schemaVersion: 2 }));
      expect(svc.upload).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenNthCalledWith(1, COVERAGE_ERROR_KEYS.notJson);
      expect(notify.error).toHaveBeenNthCalledWith(2, COVERAGE_ERROR_KEYS.schemaVersion);
    });

    it('maps server refusals to a message and keeps the screen as it was', () => {
      const c = create();
      svc.runs.mockClear();
      svc.upload.mockImplementation(() => throwError(() => ({ status: 403 })));
      c.uploadText(goodText());
      expect(notify.error).toHaveBeenLastCalledWith(COVERAGE_ERROR_KEYS.forbidden);
      svc.upload.mockImplementation(() => throwError(() => ({ status: 400, error: { message: 'bad body' } })));
      c.uploadText(goodText());
      expect(notify.error).toHaveBeenLastCalledWith(COVERAGE_ERROR_KEYS.rejected);
      svc.upload.mockImplementation(() => throwError(() => ({ status: 0 })));
      c.uploadText(goodText());
      expect(notify.error).toHaveBeenLastCalledWith(COVERAGE_ERROR_KEYS.failed);
      expect(c.isUploading).toBe(false);
      expect(svc.runs).not.toHaveBeenCalled();
      expect(notify.success).not.toHaveBeenCalled();
    });

    it('disables the button while uploading', () => {
      const pending = new Subject<CoverageUploadResult>();
      svc.upload.mockImplementation(() => pending);
      const c = create();
      c.uploadText(goodText());
      refresh();
      expect((q('.cov-upload') as HTMLButtonElement).disabled).toBe(true);
      pending.next(uploaded);
      pending.complete();
      refresh();
      expect((q('.cov-upload') as HTMLButtonElement).disabled).toBe(false);
    });

    it('reads the chosen file and uploads it', async () => {
      svc.upload.mockImplementation(() => of(uploaded));
      const c = create();
      c.onFileSelected([new File([goodText()], 'coverage-report.json', { type: 'application/json' })]);
      await vi.waitFor(() => expect(svc.upload).toHaveBeenCalledTimes(1));
    });

    it('does nothing when no file was chosen', () => {
      const c = create();
      c.onFileSelected([]);
      expect(svc.upload).not.toHaveBeenCalled();
      expect(notify.error).not.toHaveBeenCalled();
    });

    it('refuses a file over the size limit before reading it', () => {
      const c = create();
      const big = new File(['x'], 'big.json');
      Object.defineProperty(big, 'size', { value: COVERAGE_MAX_UPLOAD_BYTES + 1 });
      c.onFileSelected([big]);
      expect(notify.error).toHaveBeenCalledWith(COVERAGE_ERROR_KEYS.tooLarge);
      expect(svc.upload).not.toHaveBeenCalled();
    });

    it('reports a file the browser cannot read', async () => {
      class FailingReader {
        error = new Error('boom');
        onload: (() => void) | null = null;
        onerror: (() => void) | null = null;
        readAsText(): void {
          this.onerror?.();
        }
      }
      const original = globalThis.FileReader;
      (globalThis as unknown as { FileReader: unknown }).FileReader = FailingReader;
      try {
        const c = create();
        c.onFileSelected([new File(['{}'], 'r.json')]);
        await vi.waitFor(() => expect(notify.error).toHaveBeenCalledWith(COVERAGE_ERROR_KEYS.unreadable));
      } finally {
        globalThis.FileReader = original;
      }
    });
  });
});
