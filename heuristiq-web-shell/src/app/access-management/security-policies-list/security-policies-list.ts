import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { UamService } from '../../shared/services/uam-service';
import { AdminFormService } from '../admin-form.service';
import { SearchApiService, UAM_SEARCH_MODELS } from '../../shared/services/search-api-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { SecurityPolicy } from '../../shared/models/uam.models';
import { SECURITY_POLICY_CRUD_CONFIG } from '../access-management-configs';
import { HqCommonTableColumn, HqStatCardItem } from 'hq-shared-lib';
import { TranslateService } from '@ngx-translate/core';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { activityStats } from '../access-management-stats';
import { TOTAL_ICON } from '../access-management-stats.constants';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

@Component({
  selector: 'app-security-policies-list',
  standalone: false,
  templateUrl: './security-policies-list.html',
  styleUrl: './security-policies-list.scss',
})
export class SecurityPoliciesList implements OnInit {
  config = SECURITY_POLICY_CRUD_CONFIG;
  columns: HqCommonTableColumn<SecurityPolicy>[] = [];
  stats: HqStatCardItem[] = [];
  rows: SecurityPolicy[] = [];
  isLoading = false;
  hasError = false;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  page = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;

  filters = { name: '', tenantId: '' };

  constructor(
    private uam: UamService,
    private adminForm: AdminFormService,
    private searchApi: SearchApiService,
    private cdr: ChangeDetectorRef,
    public dialogs: AppDialogService,
    public notify: NotificationService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    this.columns = this.config.columns.map((c) => ({ ...c, label: this.translate.instant(c.label) }));
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    // Every page, filter and search is answered by the search index (getall API), one page at a time.
    const source$ = this.searchApi.searchRows(UAM_SEARCH_MODELS.securityPolicies, this.filters, this.page, this.pageSize);
    source$.subscribe({
      next: (res) => {
        this.rows = res.content ?? [];
        // The cards are counted from this one response (no second request): Total is every match, Active/Inactive the rows shown.
        this.stats = activityStats(this.translate, res.totalElements ?? this.rows.length, this.rows, (row) => row.active !== false, TOTAL_ICON.securityPolicies);
        this.totalElements = res.totalElements ?? this.rows.length;
        this.totalPages = res.totalPages ?? 1;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.securityPolicies.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  clearFilters(): void {
    this.filters = { name: '', tenantId: '' };
    this.applyFilters();
  }

  openCreate(): void {
    this.adminForm.create(this.config);
  }

  openEdit(row: SecurityPolicy): void {
    this.adminForm.edit(this.config, row.id);
  }

  confirmDelete(row: SecurityPolicy): void {
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.securityPolicies.deleteDialog.title'),
      message: this.translate.instant('accessManagement.securityPolicies.deleteDialog.message', { name: row.name }),
      variant: 'danger',
      confirmText: this.translate.instant('shared.listPage.rowActions.delete'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.uam.remove(this.config.resourcePath, row.id).subscribe({
        next: () => { this.notify.success(this.translate.instant('accessManagement.securityPolicies.messages.deleted')); this.load(); },
        error: (err: any) => this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.securityPolicies.messages.deleteFailed'))),
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
}
