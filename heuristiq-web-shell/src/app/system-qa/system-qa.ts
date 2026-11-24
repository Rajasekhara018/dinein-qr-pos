import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { HqChartSpec, HqCommonTableColumn, HqDateRange, HqOpenFile, HqPrivilegeAccessService, HqTimezoneService, saveBlob } from 'hq-shared-lib';
import { isWithinRange } from '../shared/date-range.helpers';
import { watchColumns } from '../shared/table-columns.helpers';
import { Subject, Subscription } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';

import { evidenceLabelKey, showsAsThumbnail, usableEvidence } from '../qa-dashboard/qa-evidence/qa-evidence.helpers';
import { QaEvidenceService } from '../qa-dashboard/qa-evidence/qa-evidence.service';
import { QaEvidenceThumbnailService } from '../qa-dashboard/qa-evidence/qa-evidence-thumbnail.service';
import { NotificationService } from '../shared/services/notification-service';
import {
  QA_DURATION_KEYS,
  QA_RUN_COLUMNS,
  QA_SCENARIO_COLUMNS,
  QA_ENVIRONMENTS,
  QA_ENVIRONMENT_KEYS,
  QA_LINK_REL,
  QA_MESSAGE_KEYS,
  QA_PDF_DIALOG,
  QA_PRIVILEGES,
  QA_RUNS_PAGE_SIZE,
  QA_SCENARIO_STATUSES,
  QA_SIZE_KEYS,
  QA_STATUS_KEYS,
  QA_STATUS_TONE,
  QA_TREND_RUN_COUNT,
  QA_TREND_DAY_FORMAT,
  QA_TREND_HEIGHT,
  QA_TREND_SLOT,
  QaTone,
} from './system-qa.constants';
import {
  distinctPilotIds,
  distinctTags,
  durationParts,
  filterScenarios,
  passRate,
  renderMarkdown,
  safeHttpUrl,
  sizeOf,
  sortDocuments,
  sortFailuresFirst,
  trendValues,
} from './system-qa.helpers';
import {
  QaDocument,
  QaDocumentSummary,
  QaEvidence,
  QaRunDetail,
  QaRunSummary,
  QaScenario,
  QaScenarioFilters,
  QaTab,
  QaTrendValue,
} from './system-qa.models';
import { SystemQaService } from './system-qa.service';

/**
 * System > QA automation: the runs Jenkins uploaded (with an environment filter and a pass-rate trend), one run with
 * its scenarios (failures first), and the QA documents (markdown). Data comes from Rider_ConfigService; a run's PDF
 * comes from DMS.
 */
@Component({
  selector: 'app-system-qa',
  standalone: false,
  templateUrl: './system-qa.html',
  styleUrl: './system-qa.scss',
  // The failure screenshots shown as previews: the page's own queue (three downloads at a time) and cache, cleaned when it goes.
  providers: [QaEvidenceThumbnailService],
})
export class SystemQa implements OnInit, OnDestroy {
  private readonly svc = inject(SystemQaService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly translate = inject(TranslateService);
  private readonly notify = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly evidence = inject(QaEvidenceService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly timezone = inject(HqTimezoneService);
  private readonly destroy$ = new Subject<void>();

  readonly privileges = QA_PRIVILEGES;
  readonly environments = QA_ENVIRONMENTS;
  readonly scenarioStatuses = QA_SCENARIO_STATUSES;
  readonly linkRel = QA_LINK_REL;

  tab: QaTab = 'runs';

  // Runs tab.
  runColumns: HqCommonTableColumn<QaRunSummary>[] = [];
  scenarioColumns: HqCommonTableColumn<QaScenario>[] = [];
  private columnSubs: Subscription[] = [];
  /** Row hooks of the run history and of the scenario table (failed scenarios are tinted). */
  readonly runRowClass = (): string => 'qa-run-row';
  readonly scenarioRowClass = (scenario: QaScenario): string[] =>
    scenario.status === 'FAILED' ? ['qa-scenario-row', 'qa-row--failed'] : ['qa-scenario-row'];
  runs: QaRunSummary[] = [];
  environment = '';
  page = 0;
  totalPages = 0;
  totalElements = 0;
  isLoadingRuns = false;
  runsError = false;

  // Trend of the selected environment.
  /** The loaded pass rates (oldest first) and the ones inside the chosen period, which the chart and its table show. */
  trendValues: QaTrendValue[] = [];
  trendShown: QaTrendValue[] = [];
  trendRange: HqDateRange | null = null;
  trendSpec: HqChartSpec | null = null;
  trendColumns: HqCommonTableColumn<{ day: string; value: number }>[] = [];
  trendRows: { day: string; value: number }[] = [];

  // Run detail tab.
  selected: QaRunDetail | null = null;
  isLoadingDetail = false;
  detailError = false;
  filters: QaScenarioFilters = { status: '', query: '', tag: '', pilotId: '' };
  visibleScenarios: QaScenario[] = [];
  tags: string[] = [];
  pilotIds: string[] = [];
  openingPdfFor: string | null = null;
  /** The screenshot being opened, so a second click does nothing meanwhile. */
  openingEvidence: string | null = null;

  // Documents tab.
  documents: QaDocumentSummary[] = [];
  isLoadingDocuments = false;
  documentsError = false;
  currentDocument: QaDocument | null = null;
  documentHtml = '';
  isLoadingDocument = false;
  documentError = false;

  ngOnInit(): void {
    this.columnSubs = [
      watchColumns(this.translate, QA_RUN_COLUMNS, (columns) => (this.runColumns = columns)),
      watchColumns(this.translate, QA_SCENARIO_COLUMNS, (columns) => (this.scenarioColumns = columns)),
    ];
    if (this.access.can(QA_PRIVILEGES.runList)) {
      this.showRuns();
    } else if (this.access.can(QA_PRIVILEGES.documentList)) {
      this.showDocuments();
    }
  }

  ngOnDestroy(): void {
    this.columnSubs.forEach((sub) => sub.unsubscribe());
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ---- tabs ----

  /** A tab of the strip was picked: runs and documents (re)load their list, the detail tab just shows the chosen run. */
  selectTab(tab: QaTab): void {
    if (tab === 'runs') this.showRuns();
    else if (tab === 'documents') this.showDocuments();
    else this.tab = tab;
  }

  showRuns(): void {
    this.tab = 'runs';
    if (this.runs.length === 0 && !this.isLoadingRuns) this.loadRuns(0);
  }

  showDetail(): void {
    if (this.selected) this.tab = 'detail';
  }

  showDocuments(): void {
    this.tab = 'documents';
    if (this.documents.length === 0 && !this.isLoadingDocuments) this.loadDocuments();
  }

  // ---- runs ----

  /** Reloads the first page and the trend (also used when the environment filter changes). */
  reload(): void {
    this.loadRuns(0);
  }

  onEnvironmentChange(): void {
    this.loadRuns(0);
  }

  loadRuns(page: number): void {
    if (!this.access.can(QA_PRIVILEGES.runList)) return;
    this.isLoadingRuns = true;
    this.runsError = false;
    this.svc
      .runs(page, QA_RUNS_PAGE_SIZE, this.environment)
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
          if (page === 0) this.loadTrend();
          this.cdr.markForCheck();
        },
        error: () => {
          this.runsError = true;
          this.runs = [];
          this.cdr.markForCheck();
        },
      });
  }

  /** The pass-rate trend needs one environment, because runs of different environments are not comparable. */
  private loadTrend(): void {
    this.trendValues = [];
    this.applyTrendRange();
    if (!this.environment) return;
    const requested = this.environment;
    this.svc
      .recentRuns(requested, QA_TREND_RUN_COUNT)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (runs) => {
          if (requested !== this.environment) return;
          this.trendValues = trendValues(runs);
          this.applyTrendRange();
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });
  }

  previousPage(): void {
    if (this.page > 0) this.loadRuns(this.page - 1);
  }

  nextPage(): void {
    if (this.page + 1 < this.totalPages) this.loadRuns(this.page + 1);
  }

  /** The newest point of the trend, for the caption. */
  get latestTrendValue(): number | null {
    return this.trendShown.length ? this.trendShown[this.trendShown.length - 1].value : null;
  }

  /** A new period for the trend card: the runs started inside it are drawn (filtered here; each run carries its start time). */
  onTrendRange(range: HqDateRange): void {
    this.trendRange = range;
    this.applyTrendRange();
  }

  private applyTrendRange(): void {
    this.trendShown = this.trendValues.filter((v) => isWithinRange(v.startedAt, this.trendRange, this.timezone));
    const days = this.trendShown.map((v) => this.timezone.formatDateTime(v.startedAt, QA_TREND_DAY_FORMAT));
    this.trendSpec = {
      type: 'line',
      height: QA_TREND_HEIGHT,
      format: 'percent',
      labels: days,
      series: [{ name: this.translate.instant('systemQa.trend.title'), data: this.trendShown.map((v) => v.value), slot: QA_TREND_SLOT }],
    };
    this.trendRows = this.trendShown.map((v, i) => ({ day: days[i], value: v.value }));
    this.trendColumns = [
      { key: 'day', label: this.translate.instant('systemQa.columns.started') },
      { key: 'value', label: this.translate.instant('systemQa.trend.title'), render: (row) => `${row.value.toFixed(1)}%` },
    ];
  }

  // ---- run detail ----

  /** Opens one run in the detail tab. */
  openRun(runId: string): void {
    if (!this.access.can(QA_PRIVILEGES.runRead)) return;
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
          this.tags = distinctTags(detail.scenarios);
          this.pilotIds = distinctPilotIds(detail.scenarios);
          this.filters = { status: '', query: '', tag: '', pilotId: '' };
          this.applyFilters();
          this.tab = 'detail';
          this.cdr.markForCheck();
        },
        error: () => {
          this.detailError = true;
          this.tab = 'detail';
          this.cdr.markForCheck();
        },
      });
  }

  applyFilters(): void {
    this.visibleScenarios = sortFailuresFirst(filterScenarios(this.selected?.scenarios ?? [], this.filters));
  }

  clearFilters(): void {
    this.filters = { status: '', query: '', tag: '', pilotId: '' };
    this.applyFilters();
  }

  get hasActiveFilters(): boolean {
    const f = this.filters;
    return !!(f.status || f.tag || f.pilotId || f.query.trim());
  }

  /** Opens the per-run PDF in the shared viewer (DMS metadata, then the bytes). Only runs with a document have the button. */
  openPdf(run: QaRunSummary): void {
    if (!run.documentId || this.openingPdfFor) return;
    this.openingPdfFor = run.id;
    this.svc
      .pdf(run.documentId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.openingPdfFor = null;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: ({ blob, fileName }) => {
          if (blob.type === 'application/pdf' || blob.type.startsWith('image/')) {
            this.dialog.open(HqOpenFile, { data: { blob, fileName }, ...QA_PDF_DIALOG });
          } else {
            saveBlob(blob, fileName);
          }
        },
        error: () => this.notify.error(this.translate.instant(QA_MESSAGE_KEYS.pdfFailed)),
      });
  }

  /** The screenshots of a scenario that can be opened (a plain id and a known kind), the failure one first. */
  evidenceOf(scenario: QaScenario): QaEvidence[] {
    return usableEvidence(scenario.evidence);
  }

  evidenceLabel(item: QaEvidence): string {
    return evidenceLabelKey(item.kind);
  }

  /** True for a screenshot shown as a picture in the row (the failure one); the others stay a link. */
  isPreview(item: QaEvidence): boolean {
    return showsAsThumbnail(item.kind);
  }

  /** Opens a screenshot in the shared viewer, the same way the run PDF is opened. */
  openEvidence(item: QaEvidence): void {
    if (this.openingEvidence) return;
    this.openingEvidence = item.documentId;
    this.evidence
      .open(item.documentId)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.openingEvidence = null;
          this.cdr.markForCheck();
        }),
      )
      .subscribe();
  }

  // ---- documents ----

  loadDocuments(): void {
    if (!this.access.can(QA_PRIVILEGES.documentList)) return;
    this.isLoadingDocuments = true;
    this.documentsError = false;
    this.svc
      .documentList()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.isLoadingDocuments = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (list) => {
          this.documents = sortDocuments(list);
          this.cdr.markForCheck();
        },
        error: () => {
          this.documentsError = true;
          this.documents = [];
          this.cdr.markForCheck();
        },
      });
  }

  /** Loads one document and renders its markdown (the HTML is sanitized by Angular when bound with [innerHTML]). */
  openDocument(name: string): void {
    if (!this.access.can(QA_PRIVILEGES.documentRead)) return;
    this.isLoadingDocument = true;
    this.documentError = false;
    this.svc
      .document(name)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.isLoadingDocument = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (doc) => {
          this.currentDocument = doc;
          this.documentHtml = renderMarkdown(doc.markdown);
          this.cdr.markForCheck();
        },
        error: () => {
          this.documentError = true;
          this.cdr.markForCheck();
        },
      });
  }

  // ---- display helpers ----

  /** Pass rate of a run, or null when nothing was executed. */
  rateOf(run: QaRunSummary): number | null {
    return passRate(run);
  }

  /** The Jenkins link, only when it is a plain http(s) address. */
  jenkinsLink(run: QaRunSummary): string | null {
    return safeHttpUrl(run.jenkinsUrl);
  }

  /** The run's full report, only when it is a plain http(s) address. */
  reportLink(run: QaRunSummary): string | null {
    return safeHttpUrl(run.reportUrl);
  }

  durationText(ms: number): string {
    return durationParts(ms)
      .map((p) => this.translate.instant(QA_DURATION_KEYS[p.unit], { value: p.value }))
      .join(' ');
  }

  sizeText(bytes: number): string {
    const size = sizeOf(bytes);
    return this.translate.instant(QA_SIZE_KEYS[size.unit], { value: size.value });
  }

  statusLabel(status: string): string {
    return this.labelFor(QA_STATUS_KEYS[status], status);
  }

  environmentLabel(environment: string): string {
    return this.labelFor(QA_ENVIRONMENT_KEYS[environment], environment);
  }

  statusTone(status: string): QaTone {
    return QA_STATUS_TONE[status] ?? 'neutral';
  }

  private labelFor(key: string | undefined, fallback: string): string {
    if (!key) return fallback;
    const label = this.translate.instant(key);
    return label === key ? fallback : label;
  }

  trackByRun(_: number, run: QaRunSummary): string {
    return run.id;
  }

  trackByDocument(_: number, doc: QaDocumentSummary): string {
    return doc.name;
  }
}
