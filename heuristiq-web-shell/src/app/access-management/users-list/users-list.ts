import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HqCommonTableColumn, HqStatCardItem } from 'hq-shared-lib';
import { UamService } from '../../shared/services/uam-service';
import { SearchApiService, UAM_SEARCH_MODELS } from '../../shared/services/search-api-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamUser } from '../../shared/models/uam.models';
import { TranslateService } from '@ngx-translate/core';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { activityStats, countedStats } from '../access-management-stats';
import { TOTAL_ICON } from '../access-management-stats.constants';
import { AdminFormService } from '../admin-form.service';
import { USER_FORM_CONFIG } from './users-list.constants';
import { catchError, of } from 'rxjs';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { TenantApplicationView } from '../../shared/models/uam.models';
import { organizationApplicationsPath } from '../organization-applications/organization-applications.constants';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

@Component({
  selector: 'app-users-list',
  standalone: false,
  templateUrl: './users-list.html',
  styleUrl: './users-list.scss',
})
export class UsersList implements OnInit {
  stats: HqStatCardItem[] = [];
  rows: UamUser[] = [];
  isLoading = false;
  hasError = false;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  page = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;

  // Keys map onto UamUser's search slots (see UAM_SEARCH_MODELS.users in search-api-service.ts).
  // Username/email are separate filters rather than one free-text box: SearchAPI's query DSL is a
  // single flat AND/OR group, so an "any of these fields" OR can't be combined with status/role ANDs.
  filters = { userName: '', email: '', roleCode: '', status: '', application: '' };

  /** The applications the current organization has (active subscriptions), offered as the Application filter. */
  applicationChoices: { code: string; name: string }[] = [];

  columns: HqCommonTableColumn<UamUser>[] = [];

  constructor(
    private router: Router,
    private uam: UamService,
    private searchApi: SearchApiService,
    private adminForm: AdminFormService,
    private cdr: ChangeDetectorRef,
    public dialogs: AppDialogService,
    public notify: NotificationService,
    private translate: TranslateService,
    private organizations: OrganizationContextService,
  ) {}

  ngOnInit(): void {
    const t = (key: string) => this.translate.instant(key);
    this.columns = [
      { key: 'userName', label: t('accessManagement.users.fields.username') },
      { key: 'email', label: t('profile.form.email.label') },
      { key: 'phoneNumber', label: t('profile.form.phoneNumber.label'), render: (row) => row.phoneNumber?.number ?? '—' },
      { key: 'fullName', label: t('profile.form.fullName.label'), render: (row) => [row.fullName?.firstName, row.fullName?.lastName].filter(Boolean).join(' ') || '—' },
      { key: 'status', label: t('accessManagement.users.fields.status') },
      { key: 'roles', label: t('accessManagement.users.fields.roles.label') },
      { key: 'applications', label: t('accessManagement.users.fields.applications'), render: (row) => (row.applications ?? []).join(', ') || '—' },
    ];
    this.loadApplicationChoices();
    this.loadDataScope();
  }

  /** The departments the signed-in person is limited to, when the organization limits people to their own department. */
  private departmentIds: string[] | null = null;

  /**
   * Asks UAM how much of the organization this person may see before the first load. A failure leaves the list as it was
   * (the whole organization), since this limit is a convenience of the screen, not where access is decided.
   */
  private loadDataScope(): void {
    this.uam.get<{ restricted: boolean; unitIds: string[] }>('/identity/data-scope').pipe(
      catchError(() => of({ restricted: false, unitIds: [] as string[] })),
    ).subscribe((scope) => {
      this.departmentIds = scope.restricted ? scope.unitIds ?? [] : null;
      this.load();
    });
  }

  /** Only the applications this organization has: an organization's admin never sees another organization's. */
  private loadApplicationChoices(): void {
    const tenantId = this.organizations.currentTenantId();
    (tenantId ? this.uam.get<TenantApplicationView[]>(organizationApplicationsPath(tenantId)) : of([] as TenantApplicationView[])).pipe(
      catchError(() => of([] as TenantApplicationView[]))).subscribe((rows) => {
      this.applicationChoices = (rows ?? [])
        .filter((r) => r.subscription.status === 'ACTIVE')
        .map((r) => ({ code: r.subscription.applicationCode, name: r.applicationName || r.subscription.applicationCode }));
      this.cdr.detectChanges();
    });
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    // Every page, filter and search is answered by the search index (getall API), one page at a time.
    if (this.departmentIds !== null && this.departmentIds.length === 0) {
      // Limited to a department but placed in none: nobody to show.
      this.rows = [];
      this.totalElements = 0;
      this.totalPages = 1;
      this.stats = activityStats(this.translate, 0, this.rows, (row) => row.status === 'ACTIVE', TOTAL_ICON.users);
      this.isLoading = false;
      this.cdr.detectChanges();
      return;
    }
    const filters = this.departmentIds === null ? this.filters : { ...this.filters, department: this.departmentIds.join(',') };
    const source$ = this.searchApi.searchRows(UAM_SEARCH_MODELS.users, filters, this.page, this.pageSize);
    source$.subscribe({
      next: (res) => {
        this.rows = res.content ?? [];
        // The cards are counted from this one response (no second request): Total is every match, Active/Inactive the rows shown.
        this.stats = activityStats(this.translate, res.totalElements ?? this.rows.length, this.rows, (row) => row.status === 'ACTIVE', TOTAL_ICON.users);
        this.totalElements = res.totalElements ?? this.rows.length;
        this.totalPages = res.totalPages ?? 1;
        this.isLoading = false;
        this.cdr.detectChanges();
        this.countActive(filters, this.totalElements);
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.users.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  /**
   * The Active and Inactive cards count every match, not only the rows of the page shown: one more request asks the search for the
   * active matches (a page of one, only its total is read). Until it answers, the cards show the counts of the page.
   */
  private countActive(filters: Record<string, string>, total: number): void {
    if (filters['status']) {
      // the list is already limited to one status: every match is in it or none is
      this.stats = countedStats(this.translate, total, filters['status'] === 'ACTIVE' ? total : 0, TOTAL_ICON.users);
      this.cdr.detectChanges();
      return;
    }
    this.searchApi.searchRows(UAM_SEARCH_MODELS.users, { ...filters, status: 'ACTIVE' }, 0, 1).subscribe({
      next: (res) => {
        this.stats = countedStats(this.translate, total, res.totalElements ?? 0, TOTAL_ICON.users);
        this.cdr.detectChanges();
      },
      error: () => undefined,
    });
  }

  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  clearFilters(): void {
    this.filters = { userName: '', email: '', roleCode: '', status: '', application: '' };
    this.applyFilters();
  }

  openCreate(): void {
    this.adminForm.create(USER_FORM_CONFIG);
  }

  openEdit(row: UamUser): void {
    this.adminForm.edit(USER_FORM_CONFIG, row.id);
  }

  viewDetail(row: UamUser): void {
    this.router.navigate(['/access-management/users', row.id]);
  }

  setPassword(row: UamUser): void {
    const t = (key: string, params?: object) => this.translate.instant(key, params);
    this.dialogs.form({
      title: t('accessManagement.users.setPasswordDialog.title'),
      message: t('accessManagement.users.setPasswordDialog.message', { userName: row.userName }),
      fields: [{ key: 'password', label: t('accessManagement.users.setPasswordDialog.fieldLabel'), type: 'text', required: true }],
      confirmText: t('accessManagement.users.setPasswordDialog.confirm'),
    }).subscribe((result) => {
      if (!result) return;
      this.uam.setUserPassword(row.id, { password: result['password'], temporary: false }).subscribe({
        next: () => this.notify.success(t('accessManagement.users.messages.passwordUpdated')),
        error: () => this.notify.error(t('accessManagement.users.messages.passwordSetFailed')),
      });
    });
  }

  requireTotp(row: UamUser): void {
    this.uam.setTotpRequired(row.id, true).subscribe({
      next: () => this.notify.success(this.translate.instant('accessManagement.users.messages.totpRequired')),
      error: () => this.notify.error(this.translate.instant('accessManagement.users.messages.totpUpdateFailed')),
    });
  }

  disableTotp(row: UamUser): void {
    this.uam.setTotpRequired(row.id, false).subscribe({
      next: () => this.notify.success(this.translate.instant('accessManagement.users.messages.totpDisabled')),
      error: () => this.notify.error(this.translate.instant('accessManagement.users.messages.totpUpdateFailed')),
    });
  }

  confirmDelete(row: UamUser): void {
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.users.deleteDialog.title'),
      message: this.translate.instant('accessManagement.users.deleteDialog.message', { userName: row.userName }),
      variant: 'danger',
      confirmText: this.translate.instant('shared.listPage.rowActions.delete'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.uam.deleteUser(row.id).subscribe({
        next: () => { this.notify.success(this.translate.instant('accessManagement.users.messages.deleted')); this.reload(); },
        error: (err: any) => this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.users.messages.deleteFailed'))),
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

  private reload(): void {
    this.load();
  }
}
