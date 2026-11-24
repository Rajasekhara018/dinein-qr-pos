import { Component, OnInit, inject } from '@angular/core';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { Subject } from 'rxjs';
import { debounceTime, switchMap, takeUntil } from 'rxjs/operators';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { watchColumnsUntil } from '../../shared/table-columns.helpers';
import { QA_SEARCH_DEBOUNCE_MS } from '../qa-dashboard.constants';
import { methodClass } from '../qa-endpoint-inventory/qa-endpoint-inventory.helpers';
import { QaScreenBase } from '../qa-screen-base';
import {
  QA_STUDIO_AUTH_KEYS,
  QA_STUDIO_LIST_COLUMNS,
  QA_STUDIO_MESSAGE_KEYS,
  QA_STUDIO_MODULES,
  QA_STUDIO_PAGE_SIZE,
  QA_STUDIO_PRIVILEGES,
  QA_STUDIO_ROUTES,
  QA_STUDIO_SUITES,
} from './qa-studio.constants';
import { duplicateInput, emptyStudioQuery, hasActiveStudioQuery } from './qa-studio.helpers';
import { QaStudioCaseItem, QaStudioPage, QaStudioQuery } from './qa-studio.models';
import { QaStudioService } from './qa-studio.service';

/**
 * QA automation > Test case studio: the API test cases staff wrote in the portal. Filter and search them, switch one on
 * or off, open it, copy it or delete it. Like the endpoint inventory it is about API tests, so with the UI workspace it
 * only says so. Every control is behind its own privilege.
 */
@Component({
  selector: 'app-qa-studio-list',
  standalone: false,
  templateUrl: './qa-studio-list.html',
  styleUrls: ['../qa-dashboard.shared.scss', './qa-studio.scss'],
})
export class QaStudioListPage extends QaScreenBase implements OnInit {
  private readonly studio = inject(QaStudioService);
  private readonly dialogs = inject(AppDialogService);
  private readonly notify = inject(NotificationService);
  private readonly search$ = new Subject<void>();

  readonly studioPrivileges = QA_STUDIO_PRIVILEGES;
  readonly studioRoutes = QA_STUDIO_ROUTES;
  readonly modules = QA_STUDIO_MODULES;
  readonly suites = QA_STUDIO_SUITES;
  readonly authKeys = QA_STUDIO_AUTH_KEYS;

  query: QaStudioQuery = emptyStudioQuery();
  searchText = '';
  subModuleText = '';
  result: QaStudioPage | null = null;
  /** The case a row action is working on, so a second click does nothing meanwhile. */
  busyId: string | null = null;
  columns: HqCommonTableColumn<QaStudioCaseItem>[] = [];
  /** Class of every case row (the row hook the specs and the UI suite look for). */
  readonly rowClassOf = (): string => 'qa-case-row';

  constructor() {
    super();
    this.defaultWorkspace = 'API';
  }

  override ngOnInit(): void {
    watchColumnsUntil(this.translate, QA_STUDIO_LIST_COLUMNS, (columns) => (this.columns = columns), this.destroy$);
    this.search$.pipe(debounceTime(QA_SEARCH_DEBOUNCE_MS), takeUntil(this.destroy$)).subscribe(() => {
      this.query = { ...this.query, q: this.searchText, subModule: this.subModuleText };
      this.loadPage(0);
    });
    super.ngOnInit();
  }

  get isApiWorkspace(): boolean {
    return this.workspace === 'API';
  }

  /** True when the user may change cases, so the row link says "edit" rather than "view". */
  get canUpdate(): boolean {
    return this.access.can(QA_STUDIO_PRIVILEGES.update);
  }

  /** True when the user may switch cases on and off; otherwise the state is only shown as text. */
  get canEnable(): boolean {
    return this.access.can(QA_STUDIO_PRIVILEGES.enable);
  }

  get isFiltered(): boolean {
    return hasActiveStudioQuery(this.query);
  }

  protected onWorkspaceChange(): void {
    this.query = emptyStudioQuery();
    this.searchText = '';
    this.subModuleText = '';
    this.result = null;
    this.hasError = false;
    this.isLoading = false;
    if (this.isApiWorkspace) this.loadPage(0);
  }

  reload(): void {
    if (this.isApiWorkspace) this.loadPage(this.result?.page ?? 0);
  }

  loadPage(page: number): void {
    if (!this.access.can(QA_STUDIO_PRIVILEGES.list)) return;
    this.load(
      this.studio.list(this.query, page, QA_STUDIO_PAGE_SIZE),
      (result) => (this.result = result),
      () => (this.result = null),
    );
  }

  // ---- filters ----

  onSearchInput(text: string): void {
    this.searchText = text;
    this.search$.next();
  }

  onSubModuleInput(text: string): void {
    this.subModuleText = text;
    this.search$.next();
  }

  update(patch: Partial<QaStudioQuery>): void {
    this.query = { ...this.query, ...patch };
    this.loadPage(0);
  }

  clear(): void {
    this.searchText = '';
    this.subModuleText = '';
    this.query = emptyStudioQuery();
    this.loadPage(0);
  }

  previousPage(): void {
    if (this.result && this.result.page > 0) this.loadPage(this.result.page - 1);
  }

  nextPage(): void {
    if (this.result && this.result.page + 1 < this.result.totalPages) this.loadPage(this.result.page + 1);
  }

  // ---- row actions ----

  /** Switches a case on or off; the switch moves at once and goes back when the server refuses. */
  toggleEnabled(item: QaStudioCaseItem, enabled: boolean): void {
    if (!this.access.can(QA_STUDIO_PRIVILEGES.enable) || this.busyId) return;
    const before = item.enabled;
    item.enabled = enabled;
    this.busyId = item.id;
    this.studio
      .setEnabled(item.id, enabled)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.busyId = null;
          this.cdr.markForCheck();
        },
        error: () => {
          item.enabled = before;
          this.busyId = null;
          this.notify.error(this.translate.instant(QA_STUDIO_MESSAGE_KEYS.enableFailed));
          this.cdr.markForCheck();
        },
      });
  }

  /** Copies a case under a new name, switched off, and opens the copy. */
  duplicate(item: QaStudioCaseItem): void {
    if (!this.access.can(QA_STUDIO_PRIVILEGES.create) || this.busyId) return;
    this.busyId = item.id;
    this.studio
      .get(item.id)
      .pipe(
        switchMap((c) => this.studio.create(duplicateInput(c, this.translate.instant(QA_STUDIO_MESSAGE_KEYS.copySuffix)))),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (copy) => {
          this.busyId = null;
          this.notify.success(this.translate.instant(QA_STUDIO_MESSAGE_KEYS.duplicated, { tcId: copy.tcId }));
          void this.router.navigate([QA_STUDIO_ROUTES.list, copy.id]);
        },
        error: () => {
          this.busyId = null;
          this.notify.error(this.translate.instant(QA_STUDIO_MESSAGE_KEYS.duplicateFailed));
          this.cdr.markForCheck();
        },
      });
  }

  confirmDelete(item: QaStudioCaseItem): void {
    if (!this.access.can(QA_STUDIO_PRIVILEGES.delete) || this.busyId) return;
    this.dialogs
      .confirm({
        title: this.translate.instant(QA_STUDIO_MESSAGE_KEYS.deleteTitle),
        message: this.translate.instant(QA_STUDIO_MESSAGE_KEYS.deleteMessage, { tcId: item.tcId, name: item.name }),
        confirmText: this.translate.instant(QA_STUDIO_MESSAGE_KEYS.deleteConfirm),
        variant: 'danger',
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((confirmed) => {
        if (confirmed) this.delete(item);
      });
  }

  private delete(item: QaStudioCaseItem): void {
    this.busyId = item.id;
    this.studio
      .delete(item.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.busyId = null;
          this.notify.success(this.translate.instant(QA_STUDIO_MESSAGE_KEYS.deleted));
          // The page may now be one row short (or empty when it was the last row of the last page).
          const page = this.result && this.result.items.length === 1 && this.result.page > 0 ? this.result.page - 1 : (this.result?.page ?? 0);
          this.loadPage(page);
        },
        error: () => {
          this.busyId = null;
          this.notify.error(this.translate.instant(QA_STUDIO_MESSAGE_KEYS.deleteFailed));
          this.cdr.markForCheck();
        },
      });
  }

  // ---- display ----

  methodClass(method: string): string {
    return methodClass(method);
  }

  authLabel(auth: string): string {
    const key = QA_STUDIO_AUTH_KEYS[auth];
    return key ? this.translate.instant(key) : auth;
  }

  trackByCase(_: number, item: QaStudioCaseItem): string {
    return item.id;
  }
}
