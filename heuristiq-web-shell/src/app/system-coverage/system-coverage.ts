import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqChartSpec, HqCommonTableColumn, HqDateRange, HqPrivilegeAccessService, HqTimezoneService } from 'hq-shared-lib';
import { isWithinRange } from '../shared/date-range.helpers';
import { watchColumns } from '../shared/table-columns.helpers';
import { Subject, Subscription, interval } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';

import { NotificationService } from '../shared/services/notification-service';
import {
  COVERAGE_AUTO_REFRESH_MS,
  COVERAGE_GROUP_COLUMNS,
  COVERAGE_ROW_COLUMNS,
  COVERAGE_ERROR_KEYS,
  COVERAGE_HISTORY_PAGE_SIZE,
  COVERAGE_KINDS,
  COVERAGE_KIND_KEYS,
  COVERAGE_LEVELS,
  COVERAGE_LEVEL_KEYS,
  COVERAGE_LEVEL_TONE,
  COVERAGE_MAX_UPLOAD_BYTES,
  COVERAGE_MESSAGE_KEYS,
  COVERAGE_PRIVILEGES,
  COVERAGE_SORT_KEYS,
  COVERAGE_SORT_MODES,
  COVERAGE_TREND_RUN_COUNT,
  COVERAGE_TREND_DAY_FORMAT,
  COVERAGE_TREND_HEIGHT,
  COVERAGE_TREND_SLOT,
  CoverageTone,
} from './system-coverage.constants';
import {
  ageOf,
  buildRows,
  countLevels,
  filterRows,
  parseCollectorText,
  readFileText,
  sortRows,
  uploadErrorOf,
} from './system-coverage.helpers';
import {
  CoverageAge,
  CoverageDocument,
  CoverageFilters,
  CoverageKind,
  CoverageLevel,
  CoverageGroup,
  CoverageRow,
  CoverageRunDetail,
  CoverageRunSummary,
  CoverageSortMode,
  CoverageTrendValue,
} from './system-coverage.models';
import { SystemCoverageService } from './system-coverage.service';

/**
 * System > Code coverage: the latest collector run (cards and per-project table with per-area gaps), the history of
 * runs with a trend of the average line coverage, and an upload of a collector file. Data comes from Rider_ConfigService.
 */
@Component({
  selector: 'app-system-coverage',
  standalone: false,
  templateUrl: './system-coverage.html',
  styleUrl: './system-coverage.scss',
})
export class SystemCoverage implements OnInit, OnDestroy {
  private readonly svc = inject(SystemCoverageService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly translate = inject(TranslateService);
  private readonly notify = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly timezone = inject(HqTimezoneService);
  private readonly destroy$ = new Subject<void>();

  readonly privileges = COVERAGE_PRIVILEGES;
  columns: HqCommonTableColumn<CoverageRow>[] = [];
  private columnsSub?: Subscription;
  /** Columns of the areas table inside an expanded project row. */
  groupColumns: HqCommonTableColumn<CoverageGroup>[] = [];
  private groupColumnsSub?: Subscription;
  readonly kinds = COVERAGE_KINDS;
  readonly levels = COVERAGE_LEVELS;
  readonly sortModes = COVERAGE_SORT_MODES;
  readonly maxUploadBytes = COVERAGE_MAX_UPLOAD_BYTES;

  // History.
  runs: CoverageRunSummary[] = [];
  page = 0;
  totalPages = 0;
  totalElements = 0;
  isLoadingRuns = false;
  runsError = false;

  // The run on show.
  selected: CoverageRunDetail | null = null;
  isLoadingDetail = false;
  detailError = false;
  levelCounts: Record<CoverageLevel, number> = { BELOW_FLOOR: 0, OK: 0, AT_TARGET: 0, MISSING: 0 };

  // Table.
  rows: CoverageRow[] = [];
  visibleRows: CoverageRow[] = [];
  filters: CoverageFilters = { kind: '', level: '', query: '' };
  sortMode: CoverageSortMode = 'worst';
  private readonly expanded = new Set<string>();

  // Trend.
  /** Every loaded trend value (oldest first) and the ones inside the chosen period, which the chart and its table show. */
  trendValues: CoverageTrendValue[] = [];
  trendShown: CoverageTrendValue[] = [];
  trendRange: HqDateRange | null = null;
  trendSpec: HqChartSpec | null = null;
  trendColumns: HqCommonTableColumn<{ day: string; value: number }>[] = [];
  trendRows: { day: string; value: number }[] = [];

  isUploading = false;
  nowMs = Date.now();

  /** Shown next to the refresh button: how often the screen looks for a newer run by itself. */
  readonly autoRefreshSeconds = COVERAGE_AUTO_REFRESH_MS / 1000;

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, COVERAGE_ROW_COLUMNS, (columns) => (this.columns = columns));
    this.groupColumnsSub = watchColumns(this.translate, COVERAGE_GROUP_COLUMNS, (columns) => (this.groupColumns = columns));
    this.reload();
    interval(COVERAGE_AUTO_REFRESH_MS)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.autoRefresh());
  }

  ngOnDestroy(): void {
    this.columnsSub?.unsubscribe();
    this.groupColumnsSub?.unsubscribe();
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Loads the first page of the history, shows its newest run (or `selectRunId`) and rebuilds the trend. */
  reload(selectRunId?: string): void {
    this.nowMs = Date.now();
    if (!this.access.can(COVERAGE_PRIVILEGES.list)) return;
    this.loadRuns(0, true, selectRunId ?? null);
  }

  /**
   * The 30-second check. It asks only for the first page of the history and acts when a newer run has arrived (then it
   * reloads and shows it), otherwise it just refreshes the ages ("2 hours ago"). It stays quiet while the tab is hidden,
   * while something is loading or uploading, and when the reader is paging through older runs, and it never shows an error.
   */
  private autoRefresh(): void {
    if (this.isLoadingRuns || this.isUploading || this.page !== 0) return;
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
    if (!this.access.can(COVERAGE_PRIVILEGES.list)) return;
    this.svc
      .runs(0, COVERAGE_HISTORY_PAGE_SIZE)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (res) => {
          const newest = res.content[0]?.runId;
          if (newest && newest !== this.runs[0]?.runId) {
            this.reload();
            return;
          }
          this.nowMs = Date.now();
          if (this.selected) {
            this.rows = buildRows(this.selected.document, this.nowMs);
            this.applyFilters();
          }
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });
  }

  loadRuns(page: number, initial = false, selectRunId: string | null = null): void {
    this.isLoadingRuns = true;
    this.runsError = false;
    this.svc
      .runs(page, COVERAGE_HISTORY_PAGE_SIZE)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.isLoadingRuns = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (res) => {
          this.runs = res.content;
          this.page = res.number;
          this.totalPages = res.totalPages;
          this.totalElements = res.totalElements;
          if (initial) {
            this.loadTrend(res.content);
            const first = selectRunId ?? res.content[0]?.runId;
            if (first) this.select(first);
            else this.clearSelection();
          }
          this.cdr.markForCheck();
        },
        error: () => {
          this.runsError = true;
          this.runs = [];
          this.cdr.markForCheck();
        },
      });
  }

  /** Shows one run (the document comes from the server, or from the cache when it was read before). */
  select(runId: string): void {
    if (!this.access.can(COVERAGE_PRIVILEGES.read)) return;
    this.isLoadingDetail = true;
    this.detailError = false;
    this.svc
      .run(runId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.isLoadingDetail = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (detail) => {
          this.selected = detail;
          this.rows = buildRows(detail.document, this.nowMs);
          this.levelCounts = countLevels(this.rows);
          this.expanded.clear();
          this.applyFilters();
          this.cdr.markForCheck();
        },
        error: () => {
          this.detailError = true;
          this.cdr.markForCheck();
        },
      });
  }

  private clearSelection(): void {
    this.selected = null;
    this.rows = [];
    this.visibleRows = [];
    this.levelCounts = countLevels([]);
  }

  private loadTrend(newest: readonly CoverageRunSummary[]): void {
    if (!this.access.can(COVERAGE_PRIVILEGES.read)) {
      this.trendValues = [];
      this.applyTrendRange();
      return;
    }
    this.svc
      .trend(newest.slice(0, COVERAGE_TREND_RUN_COUNT))
      .pipe(takeUntil(this.destroy$))
      .subscribe((values) => {
        this.trendValues = values;
        this.applyTrendRange();
        this.cdr.markForCheck();
      });
  }

  applyFilters(): void {
    this.visibleRows = sortRows(filterRows(this.rows, this.filters), this.sortMode);
  }

  clearFilters(): void {
    this.filters = { kind: '', level: '', query: '' };
    this.applyFilters();
  }

  get hasActiveFilters(): boolean {
    return !!(this.filters.kind || this.filters.level || this.filters.query.trim());
  }

  toggle(key: string): void {
    if (!this.expanded.delete(key)) this.expanded.add(key);
  }

  /** Row classes of the project table: the row hook and the open tint. */
  readonly rowClasses = (row: CoverageRow): string[] => (this.isExpanded(row.key) ? ['cov-row', 'cov-row--open'] : ['cov-row']);

  /** Whether a project row shows its areas underneath. */
  readonly isRowExpanded = (row: CoverageRow): boolean => this.isExpanded(row.key);

  isExpanded(key: string): boolean {
    return this.expanded.has(key);
  }

  previousPage(): void {
    if (this.page > 0) this.loadRuns(this.page - 1);
  }

  nextPage(): void {
    if (this.page + 1 < this.totalPages) this.loadRuns(this.page + 1);
  }

  /** Whether the user may open a run (its document). */
  get canRead(): boolean {
    return this.access.can(COVERAGE_PRIVILEGES.read);
  }

  get document(): CoverageDocument | null {
    return this.selected?.document ?? null;
  }

  ageOfRun(createdAt: string): CoverageAge | null {
    return ageOf(createdAt, this.nowMs);
  }

  /** The latest value of the trend (the newest point), for the caption. */
  get latestTrendValue(): number | null {
    return this.trendShown.length ? this.trendShown[this.trendShown.length - 1].value : null;
  }

  /** A new period for the trend card: the runs measured inside it are drawn (filtered here; the runs carry their time). */
  onTrendRange(range: HqDateRange): void {
    this.trendRange = range;
    this.applyTrendRange();
  }

  private applyTrendRange(): void {
    this.trendShown = this.trendValues.filter((v) => isWithinRange(v.createdAt, this.trendRange, this.timezone));
    const days = this.trendShown.map((v) => this.timezone.formatDateTime(v.createdAt, COVERAGE_TREND_DAY_FORMAT));
    const title = this.translate.instant('systemCoverage.trend.title');
    this.trendSpec = {
      type: 'line',
      height: COVERAGE_TREND_HEIGHT,
      format: 'percent',
      labels: days,
      series: [{ name: title, data: this.trendShown.map((v) => v.value), slot: COVERAGE_TREND_SLOT }],
    };
    this.trendRows = this.trendShown.map((v, i) => ({ day: days[i], value: v.value }));
    this.trendColumns = [
      { key: 'day', label: this.translate.instant('systemCoverage.columns.measured') },
      { key: 'value', label: this.translate.instant('systemCoverage.columns.lines'), className: 'cov-num', render: (row) => `${row.value.toFixed(2)}%` },
    ];
  }

  levelLabel(level: string): string {
    return this.labelFor(COVERAGE_LEVEL_KEYS[level], level);
  }

  kindLabel(kind: CoverageKind | string): string {
    return this.labelFor(COVERAGE_KIND_KEYS[kind], kind);
  }

  sortLabel(mode: CoverageSortMode): string {
    return this.labelFor(COVERAGE_SORT_KEYS[mode], mode);
  }

  levelTone(level: CoverageLevel): CoverageTone {
    return COVERAGE_LEVEL_TONE[level] ?? 'neutral';
  }

  private labelFor(key: string | undefined, fallback: string): string {
    if (!key) return fallback;
    const label = this.translate.instant(key);
    return label === key ? fallback : label;
  }

  trackByRow(_: number, row: CoverageRow): string {
    return row.key;
  }

  trackByRun(_: number, run: CoverageRunSummary): string {
    return run.runId;
  }

  /** The file input's change handler: reads the chosen collector file, checks it and uploads it. */
  /** The coverage file picked with the upload button (hq-file-drop): checked for size, read and sent. */
  onFileSelected(files: File[]): void {
    const file = files[0];
    if (!file) return;
    if (file.size > COVERAGE_MAX_UPLOAD_BYTES) {
      this.fail(COVERAGE_ERROR_KEYS.tooLarge);
      return;
    }
    readFileText(file).then(
      (text) => this.uploadText(text),
      () => this.fail(COVERAGE_ERROR_KEYS.unreadable),
    );
  }

  /** Validates the text of a collector file on the client and posts it; reloads the history when the server accepts it. */
  uploadText(text: string): void {
    const parsed = parseCollectorText(text);
    if (!parsed.document) {
      this.fail(parsed.errorKey ?? COVERAGE_ERROR_KEYS.failed);
      return;
    }
    this.isUploading = true;
    this.svc
      .upload(parsed.document)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.isUploading = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (res) => {
          this.notify.success(this.translate.instant(COVERAGE_MESSAGE_KEYS.uploaded, { runId: res.runId }));
          this.reload(res.runId);
        },
        error: (err: unknown) => {
          const { key, detail } = uploadErrorOf(err);
          this.fail(key, detail);
        },
      });
  }

  private fail(key: string, detail = ''): void {
    this.notify.error(this.translate.instant(key, { detail }));
    this.cdr.markForCheck();
  }
}
