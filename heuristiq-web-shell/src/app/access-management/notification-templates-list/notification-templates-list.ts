import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Subject, debounceTime } from 'rxjs';
import { UamService } from '../../shared/services/uam-service';
import { AdminFormService } from '../admin-form.service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { NotificationTemplate } from '../../shared/models/uam.models';
import { NOTIFICATION_TEMPLATE_CRUD_CONFIG } from '../access-management-configs';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { TranslateService } from '@ngx-translate/core';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';

@Component({
  selector: 'app-notification-templates-list',
  standalone: false,
  templateUrl: './notification-templates-list.html',
  styleUrl: './notification-templates-list.scss',
})
export class NotificationTemplatesList implements OnInit {
  config = NOTIFICATION_TEMPLATE_CRUD_CONFIG;
  columns: HqCommonTableColumn<NotificationTemplate>[] = [];
  allRows: NotificationTemplate[] = [];
  rows: NotificationTemplate[] = [];
  isLoading = false;
  hasError = false;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  page = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;
  searchText = '';

  private searchSubject = new Subject<void>();

  constructor(
    private uam: UamService,
    private adminForm: AdminFormService,
    private cdr: ChangeDetectorRef,
    public dialogs: AppDialogService,
    public notify: NotificationService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    this.columns = this.config.columns.map((c) => ({ ...c, label: this.translate.instant(c.label) }));
    this.load();
    this.searchSubject.pipe(debounceTime(300)).subscribe(() => this.applySearch());
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    this.uam.list<NotificationTemplate>(this.config.resourcePath, this.page, this.pageSize).subscribe({
      next: (res) => {
        this.allRows = res.content ?? [];
        this.totalElements = res.totalElements ?? this.allRows.length;
        this.totalPages = res.totalPages ?? 1;
        this.isLoading = false;
        this.applySearch();
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.notificationTemplates.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  onSearchInput(): void {
    this.searchSubject.next();
  }

  applySearch(): void {
    const query = this.searchText.trim().toLowerCase();
    if (!query) {
      this.rows = this.allRows;
      return;
    }
    this.rows = this.allRows.filter((t) =>
      t.code?.toLowerCase().includes(query) || t.name?.toLowerCase().includes(query));
  }

  clearSearch(): void {
    this.searchText = '';
    this.applySearch();
  }

  openCreate(): void {
    this.adminForm.create(this.config);
  }

  openEdit(row: NotificationTemplate): void {
    this.adminForm.edit(this.config, row.id);
  }

  confirmDelete(row: NotificationTemplate): void {
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.notificationTemplates.deleteDialog.title'),
      message: this.translate.instant('accessManagement.notificationTemplates.deleteDialog.message', { name: row.name ?? row.code }),
      variant: 'danger',
      confirmText: this.translate.instant('shared.listPage.rowActions.delete'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.uam.remove(this.config.resourcePath, row.id).subscribe({
        next: () => { this.notify.success(this.translate.instant('accessManagement.notificationTemplates.messages.deleted')); this.load(); },
        error: (err: any) => this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.notificationTemplates.messages.deleteFailed'))),
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
