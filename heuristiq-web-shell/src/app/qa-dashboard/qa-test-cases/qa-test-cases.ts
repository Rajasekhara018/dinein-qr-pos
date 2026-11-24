import { Component, inject } from '@angular/core';
import { HqCommonTableColumn, saveBlob } from 'hq-shared-lib';
import { finalize, takeUntil } from 'rxjs/operators';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { NotificationService } from '../../shared/services/notification-service';
import {
  QA_COLUMN_KEYS,
  QA_EXPORT_FILE_NAME,
  QA_MESSAGE_KEYS,
  QA_PAGE_SIZE,
  QA_TABLE_CONFIGS,
  QaColumn,
  QaTableConfig,
} from '../qa-dashboard.constants';
import { emptyFilters, hasActiveFilters, shortMessage, testCaseColumnSpecs } from '../qa-dashboard.helpers';
import { QaFilters, QaTableKind, QaTestCaseDetail, QaTestCasePage, QaTestCaseResult, QaTestCaseRow } from '../qa-dashboard.models';
import { QaScreenBase } from '../qa-screen-base';
import { QA_PRIVILEGES } from '../../system-qa/system-qa.constants';
import { QaEvidence } from '../../system-qa/system-qa.models';
import { evidenceLabelKey, showsAsThumbnail, usableEvidence } from '../qa-evidence/qa-evidence.helpers';
import { QaEvidenceService } from '../qa-evidence/qa-evidence.service';
import { QaEvidenceThumbnailService } from '../qa-evidence/qa-evidence-thumbnail.service';
import { QA_TEST_CASE_HISTORY_COLUMNS } from './qa-test-cases.constants';

/**
 * One table of test cases behind four screens, chosen by the route's `kind`: the Regression and Smoke suites, the
 * test case inventory (with delete) and the test-case execution summary (with the pass / fail / skip counts). Each
 * row is one test case in one environment.
 */
@Component({
  selector: 'app-qa-test-cases',
  standalone: false,
  templateUrl: './qa-test-cases.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-test-cases.scss'],
  // The failure screenshots shown as previews: the page's own queue (three downloads at a time) and cache, cleaned when it goes.
  providers: [QaEvidenceThumbnailService],
})
export class QaTestCasesPage extends QaScreenBase {
  private readonly dialogs = inject(AppDialogService);
  private readonly notify = inject(NotificationService);
  private readonly evidence = inject(QaEvidenceService);

  readonly columnKeys = QA_COLUMN_KEYS;
  /** Opening a screenshot goes through the DMS: its metadata, then a download address. */
  readonly dms = QA_PRIVILEGES;

  kind: QaTableKind = 'inventory';
  config: QaTableConfig = QA_TABLE_CONFIGS.inventory;
  filters: QaFilters = emptyFilters();
  result: QaTestCasePage | null = null;
  page = 0;
  selected: QaTestCaseDetail | null = null;
  isLoadingDetail = false;
  detailError = false;
  busyId: string | null = null;
  /** The screenshot being opened, so a second click does nothing meanwhile. */
  openingEvidence: string | null = null;
  /** A search the address asked for (the endpoint inventory links to one test case); used for the first load only. */
  private initialSearch = '';
  /** The columns of this screen's table (chosen by its kind) and of the detail panel's past results. */
  columns: HqCommonTableColumn<QaTestCaseRow>[] = [];
  historyColumns: HqCommonTableColumn<QaTestCaseResult>[] = [];
  /** Class of every test case row (the row hook the specs and the UI suite look for). */
  readonly rowClassOf = (): string => 'qa-row';

  override ngOnInit(): void {
    this.kind = (this.route.snapshot.data['kind'] as QaTableKind | undefined) ?? 'inventory';
    this.config = QA_TABLE_CONFIGS[this.kind];
    this.initialSearch = this.route.snapshot.queryParamMap?.get('q') ?? '';
    watchColumnsUntil(this.translate, testCaseColumnSpecs(this.config.columns), (columns) => (this.columns = columns), this.destroy$);
    watchColumnsUntil(this.translate, QA_TEST_CASE_HISTORY_COLUMNS, (columns) => (this.historyColumns = columns), this.destroy$);
    super.ngOnInit();
  }

  protected onWorkspaceChange(): void {
    this.filters = this.freshFilters();
    this.facets = null;
    this.selected = null;
    this.loadFacets();
    this.loadPage(0);
  }

  /** The screen of one suite always asks for that suite, whatever the user clears. */
  private freshFilters(): QaFilters {
    const search = this.initialSearch;
    this.initialSearch = '';
    return emptyFilters({ ...(this.config.suite ? { suite: this.config.suite } : {}), ...(search ? { q: search } : {}) });
  }

  get isFiltered(): boolean {
    return hasActiveFilters(this.filters, this.config.suite ? ['suite'] : []);
  }

  get exportPrivilege(): string | null {
    return this.config.allowExport ? this.privileges.testCaseExport : null;
  }

  reload(): void {
    this.loadPage(this.page);
  }

  onFiltersChange(filters: QaFilters): void {
    this.filters = this.config.suite ? { ...filters, suite: this.config.suite } : filters;
    this.loadPage(0);
  }

  loadPage(page: number): void {
    if (!this.access.can(this.privileges.testCaseList)) return;
    this.load(
      this.svc.testCases(this.workspace, this.filters, page, QA_PAGE_SIZE),
      (result) => {
        this.result = result;
        this.page = result.page;
      },
      () => (this.result = null),
    );
  }

  previousPage(): void {
    if (this.page > 0) this.loadPage(this.page - 1);
  }

  nextPage(): void {
    if (this.result && this.page + 1 < this.result.totalPages) this.loadPage(this.page + 1);
  }

  // ---- row actions ----

  openDetails(row: QaTestCaseRow): void {
    if (!this.access.can(this.privileges.testCaseRead)) return;
    this.isLoadingDetail = true;
    this.detailError = false;
    this.selected = null;
    this.svc
      .testCase(row.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (detail) => {
          this.selected = detail;
          this.isLoadingDetail = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.detailError = true;
          this.isLoadingDetail = false;
          this.cdr.markForCheck();
        },
      });
  }

  closeDetails(): void {
    this.selected = null;
    this.detailError = false;
  }

  /** Asks first, then forgets the test case; the next run of it will list it again. */
  confirmDelete(row: QaTestCaseRow): void {
    if (!this.config.allowDelete || !this.access.can(this.privileges.testCaseDelete) || this.busyId) return;
    this.dialogs
      .confirm({
        title: this.translate.instant(QA_MESSAGE_KEYS.deleteTitle),
        message: this.translate.instant(QA_MESSAGE_KEYS.deleteMessage, { name: row.name }),
        confirmText: this.translate.instant(QA_MESSAGE_KEYS.deleteConfirm),
        variant: 'danger',
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((confirmed) => {
        if (confirmed) this.delete(row);
      });
  }

  private delete(row: QaTestCaseRow): void {
    this.busyId = row.id;
    this.svc
      .deleteTestCase(row.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.busyId = null;
          this.notify.success(this.translate.instant(QA_MESSAGE_KEYS.deleted));
          if (this.selected?.testCase.id === row.id) this.selected = null;
          // The page may now be one row short (or empty when it was the last row of the last page).
          const last = this.result && this.result.items.length === 1 && this.page > 0 ? this.page - 1 : this.page;
          this.loadPage(last);
        },
        error: () => {
          this.busyId = null;
          this.notify.error(this.translate.instant(QA_MESSAGE_KEYS.deleteFailed));
          this.cdr.markForCheck();
        },
      });
  }

  /** Saves the filtered table as a CSV file (the server caps it at 5000 rows). */
  export(): void {
    if (!this.access.can(this.privileges.testCaseExport)) return;
    this.svc
      .exportCsv(this.workspace, this.filters)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (blob) => saveBlob(blob, QA_EXPORT_FILE_NAME),
        error: () => this.notify.error(this.translate.instant(QA_MESSAGE_KEYS.exportFailed)),
      });
  }

  /** The screenshots of one past result that can be opened, the failure one first. */
  evidenceOf(result: QaTestCaseResult): QaEvidence[] {
    return usableEvidence(result.evidence);
  }

  evidenceLabel(item: QaEvidence): string {
    return evidenceLabelKey(item.kind);
  }

  /** True for a screenshot shown as a picture in the row (the failure one); the others stay a link. */
  isPreview(item: QaEvidence): boolean {
    return showsAsThumbnail(item.kind);
  }

  /** Opens a screenshot in the shared viewer. */
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

  // ---- display ----

  has(column: QaColumn): boolean {
    return this.config.columns.includes(column);
  }

  message(row: QaTestCaseRow): string {
    return shortMessage(row.failureMessage);
  }

  trackByRow(_: number, row: QaTestCaseRow): string {
    return row.id;
  }
}
