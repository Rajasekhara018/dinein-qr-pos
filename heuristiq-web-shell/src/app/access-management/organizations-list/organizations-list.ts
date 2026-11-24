import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { UamService } from '../../shared/services/uam-service';
import { AdminFormService } from '../admin-form.service';
import { SearchApiService, UAM_SEARCH_MODELS } from '../../shared/services/search-api-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { Tenant } from '../../shared/models/uam.models';
import { ORGANIZATION_CRUD_CONFIG } from '../access-management-configs';
import { HqCommonTableColumn, HqStatCardItem } from 'hq-shared-lib';
import { TranslateService } from '@ngx-translate/core';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { activityStats } from '../access-management-stats';
import { TOTAL_ICON } from '../access-management-stats.constants';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { LEGAL_PROFILE_SUBJECTS } from '../legal-profile/legal-profile.constants';
import { ORGANIZATION_SETUP_ROUTE, OrganizationSetupStepId } from '../organization-setup/organization-setup.constants';

@Component({
  selector: 'app-organizations-list',
  standalone: false,
  templateUrl: './organizations-list.html',
  styleUrl: './organizations-list.scss',
})
export class OrganizationsList implements OnInit {
  config = ORGANIZATION_CRUD_CONFIG;
  columns: HqCommonTableColumn<Tenant>[] = [];
  stats: HqStatCardItem[] = [];
  rows: Tenant[] = [];
  isLoading = false;
  hasError = false;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  page = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;

  filters = { code: '', name: '', type: '', status: '' };

  constructor(
    private uam: UamService,
    private adminForm: AdminFormService,
    private searchApi: SearchApiService,
    private cdr: ChangeDetectorRef,
    public dialogs: AppDialogService,
    public notify: NotificationService,
    private translate: TranslateService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.columns = this.config.columns.map((c) => ({ ...c, label: this.translate.instant(c.label) }));
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    // Every page, filter and search is answered by the search index (getall API), one page at a time.
    const source$ = this.searchApi.searchRows(UAM_SEARCH_MODELS.organizations, this.filters, this.page, this.pageSize);
    source$.subscribe({
      next: (res) => {
        this.rows = res.content ?? [];
        // The cards are counted from this one response (no second request): Total is every match, Active/Inactive the rows shown.
        this.stats = activityStats(this.translate, res.totalElements ?? this.rows.length, this.rows, (row) => (row.status ? row.status === 'ACTIVE' : row.active !== false), TOTAL_ICON.organizations);
        this.totalElements = res.totalElements ?? this.rows.length;
        this.totalPages = res.totalPages ?? 1;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.organizations.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  clearFilters(): void {
    this.filters = { code: '', name: '', type: '', status: '' };
    this.applyFilters();
  }

  openCreate(): void {
    this.adminForm.create(this.config);
  }

  /** The organization's setup, on its first step (the details). */
  openEdit(row: Tenant): void {
    this.openSetup(row, 'details');
  }

  private openSetup(row: Tenant, step: OrganizationSetupStepId): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, LEGAL_PROFILE_SUBJECTS.organization.backRoute, row.id, ORGANIZATION_SETUP_ROUTE, step],
      { state: { name: row.name ?? row.code } });
  }

  confirmDelete(row: Tenant): void {
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.organizations.deleteDialog.title'),
      message: this.translate.instant('accessManagement.organizations.deleteDialog.message', { name: row.name ?? row.code }),
      variant: 'danger',
      confirmText: this.translate.instant('shared.listPage.rowActions.delete'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.uam.remove(this.config.resourcePath, row.id).subscribe({
        next: () => { this.notify.success(this.translate.instant('accessManagement.organizations.messages.deleted')); this.load(); },
        error: (err: any) => this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.organizations.messages.deleteFailed'))),
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
