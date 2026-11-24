import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { PLATFORM_ADMIN_ROLE_CODE } from '../access-management-rules.constants';
import { ActivatedRoute, Router } from '@angular/router';
import { UamService } from '../../shared/services/uam-service';
import { SearchApiService, UAM_SEARCH_MODELS } from '../../shared/services/search-api-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamRole } from '../../shared/models/uam.models';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { TranslateService } from '@ngx-translate/core';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

@Component({
  selector: 'app-roles-list',
  standalone: false,
  templateUrl: './roles-list.html',
  styleUrl: './roles-list.scss',
})
export class RolesList implements OnInit {
  rows: UamRole[] = [];
  isLoading = false;
  hasError = false;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  page = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;

  filters = { code: '', name: '' };

  totalRoles = 0;
  systemRoles = 0;
  customRoles = 0;

  columns: HqCommonTableColumn<UamRole>[] = [];

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private uam: UamService,
    private searchApi: SearchApiService,
    private cdr: ChangeDetectorRef,
    public dialogs: AppDialogService,
    public notify: NotificationService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    const t = (key: string) => this.translate.instant(key);
    this.columns = [
      { key: 'code', label: t('accessManagement.roles.fields.code') },
      { key: 'name', label: t('accessManagement.roles.fields.name') },
      { key: 'description', label: t('accessManagement.roles.fields.description') },
      { key: 'systemRole', label: t('accessManagement.roles.fields.systemRole'), render: (row: UamRole) => t(row.systemRole ? 'shared.commonTable.yes' : 'shared.commonTable.no') },
    ];
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    // Every page, filter and search is answered by the search index (getall API), one page at a time.
    const source$ = this.searchApi.searchRows(UAM_SEARCH_MODELS.roles, this.filters, this.page, this.pageSize);
    source$.subscribe({
      next: (res) => {
        this.rows = res.content ?? [];
        // The cards are counted from this one response (no second request): Total is every match, System/Custom the rows shown.
        this.totalRoles = res.totalElements ?? this.rows.length;
        this.systemRoles = this.rows.filter((r) => r.systemRole).length;
        this.customRoles = this.rows.length - this.systemRoles;
        this.totalElements = res.totalElements ?? this.rows.length;
        this.totalPages = res.totalPages ?? 1;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.roles.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  clearFilters(): void {
    this.filters = { code: '', name: '' };
    this.applyFilters();
  }

  openCreate(): void {
    this.router.navigate(['new'], { relativeTo: this.route });
  }

  /** The platform administrator role cannot be edited, so its Edit action is not offered. */
  isPlatformRole(row: UamRole): boolean {
    return (row.code ?? '').toUpperCase() === PLATFORM_ADMIN_ROLE_CODE;
  }

  openEdit(row: UamRole): void {
    this.router.navigate([row.id, 'edit'], { relativeTo: this.route });
  }

  confirmDelete(row: UamRole): void {
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.roles.deleteDialog.title'),
      message: this.translate.instant('accessManagement.roles.deleteDialog.message', { name: row.name ?? row.code }),
      variant: 'danger',
      confirmText: this.translate.instant('shared.listPage.rowActions.delete'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.uam.remove('/roles', row.id).subscribe({
        next: () => { this.notify.success(this.translate.instant('accessManagement.roles.messages.deleted')); this.load(); },
        error: (err: any) => this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.roles.messages.deleteFailed'))),
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
