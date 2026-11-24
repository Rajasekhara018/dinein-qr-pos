import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { Subscription } from 'rxjs';

import { OrganizationContextService } from '../../../shared/services/organization-context-service';
import { watchColumns } from '../../../shared/table-columns.helpers';
import {
  LIFECYCLE_DEFAULT_PAGE_SIZE,
  LIFECYCLE_DOCUMENT_COLUMNS,
  LIFECYCLE_DOCUMENT_STATUSES,
  LIFECYCLE_PAGE_SIZE_OPTIONS,
  LIFECYCLE_ROUTE,
  LIFECYCLE_SCAN_STATUSES,
} from '../lifecycle.constants';
import { emptyDocumentFilter } from '../lifecycle.helpers';
import { LifecycleDocumentFilter, LifecycleDocumentRow } from '../lifecycle.models';
import { LifecycleService } from '../lifecycle.service';
import { LifecycleViewBase } from '../lifecycle-view.base';

@Component({
  selector: 'app-lifecycle-list',
  templateUrl: './lifecycle-list.html',
  styleUrl: './lifecycle-list.scss',
  standalone: false,
})
export class LifecycleList extends LifecycleViewBase implements OnInit, OnDestroy {
  private readonly svc = inject(LifecycleService);
  private readonly router = inject(Router);
  readonly organizationName$ = inject(OrganizationContextService).currentName$;

  readonly documentStatuses = LIFECYCLE_DOCUMENT_STATUSES;
  readonly scanStatuses = LIFECYCLE_SCAN_STATUSES;
  readonly pageSizeOptions = LIFECYCLE_PAGE_SIZE_OPTIONS;

  filter: LifecycleDocumentFilter = emptyDocumentFilter();
  rows: LifecycleDocumentRow[] = [];
  columns: HqCommonTableColumn<LifecycleDocumentRow>[] = [];
  isLoading = false;
  hasError = false;
  page = 0;
  pageSize = LIFECYCLE_DEFAULT_PAGE_SIZE;
  totalElements = 0;
  totalPages = 0;
  searched = false;

  private sub: Subscription | null = null;
  private columnsSub?: Subscription;

  ngOnInit(): void {
    this.columnsSub = watchColumns(this.translate, LIFECYCLE_DOCUMENT_COLUMNS, (columns) => (this.columns = columns));
    this.load();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.columnsSub?.unsubscribe();
  }

  load(): void {
    this.sub?.unsubscribe();
    this.isLoading = true;
    this.hasError = false;
    this.sub = this.svc.searchDocuments(this.filter, this.page, this.pageSize).subscribe({
      next: (res) => {
        this.rows = res.content;
        this.totalElements = res.totalElements;
        this.totalPages = res.totalPages;
        this.isLoading = false;
        this.searched = true;
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('documentManagement.lifecycle.list.loadFailed'));
      },
    });
  }

  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  clearFilters(): void {
    this.filter = emptyDocumentFilter();
    this.applyFilters();
  }

  goToPage(page: number): void {
    this.page = page;
    this.load();
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.page = 0;
    this.load();
  }

  open(row: LifecycleDocumentRow): void {
    this.router.navigate([LIFECYCLE_ROUTE, row.id]);
  }
}
