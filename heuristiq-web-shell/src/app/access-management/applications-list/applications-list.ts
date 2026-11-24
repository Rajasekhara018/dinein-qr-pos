import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqStatCardItem } from 'hq-shared-lib';
import { Subscription } from 'rxjs';
import { AdminFormService } from '../admin-form.service';
import { UamService } from '../../shared/services/uam-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { Application } from '../../shared/models/uam.models';
import { APPLICATION_CRUD_CONFIG } from '../access-management-configs';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { activityStats } from '../access-management-stats';
import { TOTAL_ICON } from '../access-management-stats.constants';
import { GOVERNANCE_PRIVILEGES, GOVERNANCE_TEXT_KEYS, goLiveGovernanceRoute } from '../../application-readiness/go-live-governance.constants';
import {
  APPLICATION_QUERY_PARAM,
  APPLICATION_TYPE_FILTER_VALUES,
  ApplicationsListFilters,
  EMPTY_APPLICATIONS_FILTERS,
} from './applications-list.constants';
import { ApplicationsListService } from './applications-list.service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

/**
 * Access management > Applications: the session organization's applications (UAM's organization-scoped list), with a row action to
 * the go-live governance page of each. `?application=<CODE>` opens the list filtered on that code.
 */
@Component({
  selector: 'app-applications-list',
  standalone: false,
  templateUrl: './applications-list.html',
  styleUrl: './applications-list.scss',
})
export class ApplicationsList implements OnInit, OnDestroy {
  private readonly uam = inject(UamService);
  private readonly adminForm = inject(AdminFormService);
  private readonly listService = inject(ApplicationsListService);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly translate = inject(TranslateService);
  readonly dialogs = inject(AppDialogService);
  readonly notify = inject(NotificationService);

  private subscriptions: Subscription[] = [];
  private listSub: Subscription | null = null;

  config = APPLICATION_CRUD_CONFIG;
  columns: HqCommonTableColumn<Application>[] = [];
  stats: HqStatCardItem[] = [];
  rows: Application[] = [];
  isLoading = false;
  hasError = false;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  readonly typeOptions = APPLICATION_TYPE_FILTER_VALUES;
  readonly governancePrivileges = GOVERNANCE_PRIVILEGES;
  readonly governanceTitleKey = GOVERNANCE_TEXT_KEYS.pageOpen;
  page = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;

  filters: ApplicationsListFilters = { ...EMPTY_APPLICATIONS_FILTERS };

  ngOnInit(): void {
    const scoped = this.route.snapshot?.queryParamMap?.get(APPLICATION_QUERY_PARAM);
    if (scoped) this.filters = { ...this.filters, code: scoped };
    this.columns = this.buildColumns();
    this.load();
  }

  ngOnDestroy(): void {
    this.listSub?.unsubscribe();
    this.subscriptions.forEach((s) => s.unsubscribe());
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    this.listSub?.unsubscribe();
    // Every page and filter is answered by UAM for the session organization only.
    this.listSub = this.listService.page(this.filters, this.page, this.pageSize).subscribe({
      next: (res) => {
        this.rows = res.content ?? [];
        // The cards are counted from this one response (no second request): Total is every match, Active/Inactive the rows shown.
        this.stats = activityStats(this.translate, res.totalElements ?? this.rows.length, this.rows, (row) => row.active !== false, TOTAL_ICON.applications);
        this.totalElements = res.totalElements ?? this.rows.length;
        this.totalPages = res.totalPages ?? 1;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.applications.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  clearFilters(): void {
    this.filters = { ...EMPTY_APPLICATIONS_FILTERS };
    this.applyFilters();
  }

  openCreate(): void {
    this.adminForm.create(this.config);
  }

  openEdit(row: Application): void {
    this.adminForm.edit(this.config, row.id);
  }

  confirmDelete(row: Application): void {
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.applications.deleteDialog.title'),
      message: this.translate.instant('accessManagement.applications.deleteDialog.message', { name: row.name ?? row.code }),
      variant: 'danger',
      confirmText: this.translate.instant('shared.listPage.rowActions.delete'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.uam.remove(this.config.resourcePath, row.id).subscribe({
        next: () => {
          this.notify.success(this.translate.instant('accessManagement.applications.messages.deleted'));
          this.load();
        },
        error: (err: unknown) => this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.applications.messages.deleteFailed'))),
      });
    });
  }

  /** hq-table-footer's (pageChange): an absolute 0-based page. */
  goToPage(page: number): void {
    this.page = page;
    this.load();
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.page = 0;
    this.load();
  }

  /** The go-live governance page of an application. */
  governanceLink(row: Application): string {
    return goLiveGovernanceRoute(row.code);
  }

  private buildColumns(): HqCommonTableColumn<Application>[] {
    return this.config.columns.map((c) => ({ ...c, label: this.translate.instant(c.label) })) as HqCommonTableColumn<Application>[];
  }
}
