import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqTimezoneService } from 'hq-shared-lib';
import { Subscription } from 'rxjs';

import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import {
  buildFormatsAndSizeSentence,
  buildRetentionSentence,
  localizedName,
} from '../document-type.helpers';
import { DocumentTypeFilter, DocumentTypeResponse } from '../document-type.models';
import { DocumentTypesService } from '../document-types.service';
import {
  DOCUMENT_TYPES_DEFAULT_PAGE_SIZE,
  DOCUMENT_TYPES_PAGE_SIZE_OPTIONS,
  DOCUMENT_TYPES_ROUTE,
  NO_VERIFICATION,
  VERIFICATION_METHOD_KEY_PREFIX,
  VERIFICATION_PROVIDERS,
} from '../document-types.constants';

@Component({
  selector: 'app-document-type-list',
  templateUrl: './document-type-list.html',
  styleUrl: './document-type-list.scss',
  standalone: false,
})
export class DocumentTypeList implements OnInit, OnDestroy {
  private readonly hqTimezone = inject(HqTimezoneService);
  private readonly svc = inject(DocumentTypesService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly notify = inject(NotificationService);
  readonly organizationName$ = inject(OrganizationContextService).currentName$;

  columns: HqCommonTableColumn<DocumentTypeResponse>[] = [];
  rows: DocumentTypeResponse[] = [];
  isLoading = false;
  hasError = false;
  filters: Pick<DocumentTypeFilter, 'category' | 'flow' | 'active'> = {
    category: '',
    flow: '',
    active: '',
  };

  readonly pageSizeOptions = DOCUMENT_TYPES_PAGE_SIZE_OPTIONS;
  page = 0;
  pageSize = DOCUMENT_TYPES_DEFAULT_PAGE_SIZE;
  totalElements = 0;
  totalPages = 0;

  private loadSub: Subscription | null = null;
  private langSub: Subscription | null = null;

  ngOnInit(): void {
    this.buildColumns();
    // Column headings and cell texts follow the language switcher.
    this.langSub = this.translate.onLangChange.subscribe(() => {
      this.buildColumns();
      this.rows = [...this.rows];
    });
    this.load();
  }

  ngOnDestroy(): void {
    this.loadSub?.unsubscribe();
    this.langSub?.unsubscribe();
  }

  private buildColumns(): void {
    const t = (key: string, params?: Record<string, unknown>): string =>
      this.translate.instant(key, params);
    this.columns = [
      {
        key: 'names',
        label: t('documentManagement.list.columns.name'),
        render: (row) =>
          localizedName(
            row.names,
            this.translate.currentLang || this.translate.defaultLang || 'en',
            row.code,
          ),
      },
      { key: 'code', label: t('documentManagement.list.columns.code') },
      { key: 'category', label: t('documentManagement.list.columns.category') },
      {
        key: 'flow',
        label: t('documentManagement.list.columns.flow'),
        render: (row) => row.flow || '—',
      },
      {
        key: 'summary',
        label: t('documentManagement.list.columns.rule'),
        render: (row) =>
          buildFormatsAndSizeSentence(
            { allowedFormats: row.allowedFormats ?? [], maxFileSizeMb: row.maxFileSizeMb },
            t,
          ),
      },
      {
        key: 'retentionDays',
        label: t('documentManagement.list.columns.retention'),
        render: (row) =>
          row.retentionDays != null
            ? buildRetentionSentence(row.retentionDays, t).replace(/\.$/, '')
            : t('documentManagement.list.retentionDefault'),
      },
      {
        key: 'verificationMethod',
        label: t('documentManagement.list.columns.verification'),
        render: (row) =>
          row.verificationRequired &&
          row.verificationMethod &&
          row.verificationMethod !== NO_VERIFICATION
            ? t(VERIFICATION_METHOD_KEY_PREFIX + row.verificationMethod)
            : t('documentManagement.list.noVerification'),
      },
      {
        key: 'verificationProvider',
        label: t('documentManagement.providers.label'),
        render: (row) =>
          !row.verificationRequired
            ? t('documentManagement.list.noVerification')
            : row.verificationMethod === 'MANUAL_REVIEW'
              ? t('documentManagement.providers.manual')
              : row.verificationProvider
                ? (VERIFICATION_PROVIDERS.find(
                    (provider) => provider.key === row.verificationProvider,
                  )?.label ?? row.verificationProvider)
                : t(
                    row.verificationRequired
                      ? 'documentManagement.providers.unconfigured'
                      : 'documentManagement.list.noVerification',
                  ),
      },
      {
        key: 'required',
        label: t('documentManagement.list.columns.required'),
        render: (row) =>
          t(row.required ? 'documentManagement.list.required' : 'documentManagement.list.optional'),
      },
      {
        key: 'active',
        label: t('documentManagement.list.columns.status'),
        render: (row) =>
          t(row.active ? 'documentManagement.list.active' : 'documentManagement.list.inactive'),
      },
      {
        key: 'updatedAt',
        label: t('documentManagement.list.columns.lastUpdated'),
        render: (row) =>
          row.updatedAt
            ? this.hqTimezone.formatLocale(row.updatedAt, 'date', {
                day: '2-digit',
                month: 'short',
                year: 'numeric',
              })
            : '—',
      },
    ];
  }

  get visibleColumns(): HqCommonTableColumn<DocumentTypeResponse>[] {
    return this.columns.filter(
      (column) => !['code', 'flow', 'verificationProvider', 'required'].includes(column.key),
    );
  }

  cellText(key: string, row: DocumentTypeResponse): string {
    const column = this.columns.find((item) => item.key === key);
    return column?.render ? String(column.render(row)) : '';
  }

  load(): void {
    this.loadSub?.unsubscribe();
    this.isLoading = true;
    this.hasError = false;
    this.loadSub = this.svc
      .list({ ...this.filters, page: this.page, size: this.pageSize })
      .subscribe({
        next: (res) => {
          this.rows = res.content;
          this.totalElements = res.totalElements;
          this.totalPages = res.totalPages;
          this.isLoading = false;
        },
        error: () => {
          this.hasError = true;
          this.isLoading = false;
          this.notify.error(this.translate.instant('documentManagement.list.messages.loadFailed'));
        },
      });
  }

  get hasActiveFilters(): boolean {
    return !!(this.filters.category.trim() || this.filters.flow.trim() || this.filters.active);
  }

  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  clearFilters(): void {
    this.filters = { category: '', flow: '', active: '' };
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

  create(): void {
    this.router.navigate([DOCUMENT_TYPES_ROUTE, 'new']);
  }

  edit(row: DocumentTypeResponse): void {
    this.router.navigate([DOCUMENT_TYPES_ROUTE, row.id, 'edit']);
  }
}
