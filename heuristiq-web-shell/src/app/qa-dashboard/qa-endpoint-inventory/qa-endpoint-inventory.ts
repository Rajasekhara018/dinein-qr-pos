import { Component, OnInit, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { HqCommonTableColumn, saveBlob } from 'hq-shared-lib';
import { Subject } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';

import { NotificationService } from '../../shared/services/notification-service';
import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { QA_SEARCH_DEBOUNCE_MS } from '../qa-dashboard.constants';
import { QaScreenBase } from '../qa-screen-base';
import { QaEndpointDetailDialog } from './qa-endpoint-detail-dialog';
import {
  QA_ENDPOINT_COLUMNS,
  QA_ENDPOINT_DEFAULT_PAGE_SIZE,
  QA_ENDPOINT_DIALOG,
  QA_ENDPOINT_MESSAGE_KEYS,
  QA_ENDPOINT_PAGE_SIZES,
  QA_ENDPOINT_QUICK_FILTERS,
  QA_ENDPOINTS_EXPORT_FILE_NAME,
} from './qa-endpoint-inventory.constants';
import {
  emptyEndpointQuery,
  endpointSubModules,
  hasActiveEndpointQuery,
  mappedChips,
  methodClass,
  progressPercent,
} from './qa-endpoint-inventory.helpers';
import {
  QaEndpointCoverage,
  QaEndpointFilterOptions,
  QaEndpointItem,
  QaEndpointPage,
  QaEndpointQuery,
  QaEndpointQuick,
  QaMappedChips,
} from './qa-endpoint-inventory.models';
import { QaEndpointService } from './qa-endpoint-inventory.service';

/**
 * QA automation > API endpoint inventory: which documented API endpoints the automated API tests call, how often and
 * by which test cases, and the endpoints that were called without being documented. It is about API tests, so with the
 * UI workspace it only says so. The catalog is synced by the Jenkins publish: there is no sync here, only the time of the last one.
 */
@Component({
  selector: 'app-qa-endpoint-inventory',
  standalone: false,
  templateUrl: './qa-endpoint-inventory.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-endpoint-inventory.scss'],
})
export class QaEndpointInventoryPage extends QaScreenBase implements OnInit {
  private readonly endpoints = inject(QaEndpointService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly search$ = new Subject<string>();

  readonly pageSizes = QA_ENDPOINT_PAGE_SIZES;
  readonly quickFilters = QA_ENDPOINT_QUICK_FILTERS;

  query: QaEndpointQuery = emptyEndpointQuery();
  searchText = '';
  pageSize = QA_ENDPOINT_DEFAULT_PAGE_SIZE;
  result: QaEndpointPage | null = null;
  options: QaEndpointFilterOptions | null = null;
  columns: HqCommonTableColumn<QaEndpointItem>[] = [];
  /** Class of every endpoint row (the row hook the specs and the UI suite look for). */
  readonly rowClassOf = (): string => 'qa-endpoint-row';

  constructor() {
    super();
    this.defaultWorkspace = 'API';
  }

  override ngOnInit(): void {
    watchColumnsUntil(this.translate, QA_ENDPOINT_COLUMNS, (columns) => (this.columns = columns), this.destroy$);
    this.search$.pipe(debounceTime(QA_SEARCH_DEBOUNCE_MS), takeUntil(this.destroy$)).subscribe((q) => this.update({ q }));
    super.ngOnInit();
  }

  /** The inventory covers API tests: with the UI workspace the screen is empty. */
  get isApiWorkspace(): boolean {
    return this.workspace === 'API';
  }

  get coverage(): QaEndpointCoverage | null {
    return this.result?.coverage ?? null;
  }

  get progress(): number {
    return this.coverage ? progressPercent(this.coverage.tested, this.coverage.documented) : 0;
  }

  get subModules(): string[] {
    return endpointSubModules(this.options, this.query.module);
  }

  get isFiltered(): boolean {
    return hasActiveEndpointQuery(this.query);
  }

  /** True when there is no endpoint at all (the catalog was never synced) rather than none matching the filters. */
  get isCatalogEmpty(): boolean {
    return !!this.result && this.result.totalElements === 0 && !this.isFiltered && (this.coverage?.totalEndpoints ?? 0) === 0;
  }

  protected onWorkspaceChange(): void {
    this.query = emptyEndpointQuery();
    this.searchText = '';
    this.result = null;
    this.options = null;
    this.hasError = false;
    this.isLoading = false;
    if (!this.isApiWorkspace) return;
    this.loadOptions();
    this.loadPage(0);
  }

  reload(): void {
    if (this.isApiWorkspace) this.loadPage(this.result?.page ?? 0);
  }

  private loadOptions(): void {
    if (!this.access.can(this.privileges.endpointFilterRead)) return;
    this.endpoints
      .filters()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (options) => {
          this.options = options;
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });
  }

  loadPage(page: number): void {
    if (!this.access.can(this.privileges.endpointList)) return;
    this.load(
      this.endpoints.list(this.query, page, this.pageSize),
      (result) => (this.result = result),
      () => (this.result = null),
    );
  }

  // ---- filters ----

  onSearchInput(text: string): void {
    this.searchText = text;
    this.search$.next(text);
  }

  update(patch: Partial<QaEndpointQuery>): void {
    this.query = { ...this.query, ...patch };
    this.loadPage(0);
  }

  /** Changing the module forgets the sub-module (it belongs to the old module). */
  onModuleChange(module: string): void {
    this.update({ module, subModule: '' });
  }

  setQuick(quick: QaEndpointQuick): void {
    if (quick !== this.query.quick) this.update({ quick });
  }

  onPageSizeChange(size: number | string): void {
    this.pageSize = Number(size);
    this.loadPage(0);
  }

  clear(): void {
    this.searchText = '';
    this.query = emptyEndpointQuery();
    this.loadPage(0);
  }

  previousPage(): void {
    if (this.result && this.result.page > 0) this.loadPage(this.result.page - 1);
  }

  nextPage(): void {
    if (this.result && this.result.page + 1 < this.result.totalPages) this.loadPage(this.result.page + 1);
  }

  // ---- row actions ----

  openDetails(endpoint: QaEndpointItem): void {
    if (!this.access.can(this.privileges.endpointRead)) return;
    this.dialog.open(QaEndpointDetailDialog, { ...QA_ENDPOINT_DIALOG, data: endpoint });
  }

  /** Saves the filtered list as a CSV file (the server caps it at 5000 rows). */
  export(): void {
    if (!this.access.can(this.privileges.endpointExport)) return;
    this.endpoints
      .exportCsv(this.query)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (blob) => saveBlob(blob, QA_ENDPOINTS_EXPORT_FILE_NAME),
        error: () => this.notify.error(this.translate.instant(QA_ENDPOINT_MESSAGE_KEYS.exportFailed)),
      });
  }

  // ---- display ----

  methodClass(method: string): string {
    return methodClass(method);
  }

  chips(endpoint: QaEndpointItem): QaMappedChips {
    return mappedChips(endpoint.mappedTestKeys);
  }

  trackByEndpoint(_: number, endpoint: QaEndpointItem): string {
    return endpoint.id;
  }
}
