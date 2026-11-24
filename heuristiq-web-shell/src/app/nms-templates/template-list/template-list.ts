import { Component, Input, OnChanges, OnInit, SimpleChanges, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NmsTemplatesService } from '../nms-templates.service';
import { NmsTemplate } from '../nms-template.model';
import { NmsChannel, getEventLabel } from '../event-attributes.config';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { HqCommonTableColumn, HqStatCardItem, HqTimezoneService } from 'hq-shared-lib';
import { TranslateService } from '@ngx-translate/core';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { forkJoin, map } from 'rxjs';
import { SearchApiService, UAM_SEARCH_MODELS } from '../../shared/services/search-api-service';
import { ACTIVE_ICON, INACTIVE_ICON, TOTAL_ICON } from '../../access-management/access-management-stats.constants';

@Component({
  selector: 'app-template-list',
  templateUrl: './template-list.html',
  styleUrl: './template-list.scss',
  standalone: false,
})
export class TemplateList implements OnInit, OnChanges {
  private readonly hqTimezone = inject(HqTimezoneService);
  /** True when the list sits under the channel tabs of the landing page: no header of its own, and the channel comes from the tab. */
  @Input() embedded = false;
  /** The channel being listed: chosen by the tab when embedded, otherwise read from the URL. */
  @Input() channel: NmsChannel | null = null;
  columns: HqCommonTableColumn<NmsTemplate>[] = [];
  /** Total / Active / Inactive for the channel being viewed -- unaffected by the filters below. */
  stats: HqStatCardItem[] = [];
  rows: NmsTemplate[] = [];
  isLoading = false;
  hasError = false;
  filters = { name: '', event: '', status: '' };

  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  page = 0;
  pageSize = 10;
  totalElements = 0;
  totalPages = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private svc: NmsTemplatesService,
    private searchApi: SearchApiService,
    private appDialog: AppDialogService,
    public notify: NotificationService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    this.columns = [
      { key: 'name', label: this.translate.instant('accessManagement.fields.name') },
      { key: 'event', label: this.translate.instant('nmsTemplates.list.columns.event'), render: (row: NmsTemplate) => this.eventLabel(row.event) },
      { key: 'channel', label: this.translate.instant('nmsTemplates.list.columns.channel') },
      {
        key: 'tenantId',
        label: this.translate.instant('nmsTemplates.list.columns.scope'),
        render: (row: NmsTemplate) => this.translate.instant(row.tenantId ? 'nmsTemplates.list.scope.organization' : 'nmsTemplates.list.scope.shared'),
      },
      {
        key: 'active',
        label: this.translate.instant('accessManagement.fields.status'),
        render: (row: NmsTemplate) => this.translate.instant(row.active ? 'nmsTemplates.list.stats.active' : 'nmsTemplates.list.stats.inactive'),
      },
      {
        key: 'updatedAt',
        label: this.translate.instant('nmsTemplates.list.columns.lastUpdated'),
        render: (row: NmsTemplate) => row.updatedAt ? this.hqTimezone.formatLocale(row.updatedAt, 'date', { day: '2-digit', month: 'short', year: 'numeric' }) : '—',
      },
    ];
    if (this.embedded) {
      this.load();
      return;
    }
    this.route.queryParamMap.subscribe(p => {
      this.channel = (p.get('channel') as NmsChannel) ?? null;
      this.load();
    });
  }

  /** A different tab was chosen: start that channel's list from its first page, with no filters left over from the last one. */
  ngOnChanges(changes: SimpleChanges): void {
    const change = changes['channel'];
    if (this.embedded && change && !change.firstChange) {
      this.page = 0;
      this.filters = { name: '', event: '', status: '' };
      this.load();
    }
  }

  /** The page, filters and the header counts all come from the search index (getall API), one page at a time. */
  load(): void {
    this.isLoading = true;
    this.hasError = false;
    const channel: Record<string, string> = this.channel ? { channel: this.channel } : {};
    this.searchApi.searchRows(UAM_SEARCH_MODELS.nmsTemplates, { ...this.filters, ...channel }, this.page, this.pageSize).subscribe({
      next: (res) => {
        this.rows = res.content ?? [];
        this.totalElements = res.totalElements ?? this.rows.length;
        this.totalPages = res.totalPages ?? 1;
        this.isLoading = false;
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('nmsTemplates.list.messages.loadFailed'));
      },
    });
    this.loadStats(channel);
  }

  /** Total / Active / Inactive for the channel: three one-row queries read for their totals only. */
  private loadStats(channel: Record<string, string>): void {
    const count = (extra: Record<string, string>) =>
      this.searchApi.searchRows(UAM_SEARCH_MODELS.nmsTemplates, { ...channel, ...extra }, 0, 1).pipe(map((res) => res.totalElements ?? 0));
    forkJoin({ total: count({}), active: count({ status: 'ACTIVE' }), inactive: count({ status: 'INACTIVE' }) }).subscribe({
      next: ({ total, active, inactive }) => {
        this.stats = [
          { label: this.translate.instant('accessManagement.stats.total'), value: total, icon: TOTAL_ICON.templates, tone: 'blue' },
          { label: this.translate.instant('accessManagement.stats.active'), value: active, icon: ACTIVE_ICON, tone: 'green' },
          { label: this.translate.instant('accessManagement.stats.inactive'), value: inactive, icon: INACTIVE_ICON, tone: 'orange' },
        ];
      },
      error: () => {},
    });
  }

  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  clearFilters(): void {
    this.filters = { name: '', event: '', status: '' };
    this.applyFilters();
  }

  get hasActiveFilters(): boolean {
    return !!(this.filters.name.trim() || this.filters.event.trim() || this.filters.status);
  }

  /** Text shown in the "none found matching …" empty message. */
  get filterSummary(): string {
    return [this.filters.name.trim(), this.filters.event.trim()].filter(Boolean).join(' / ')
      || this.translate.instant(this.filters.status === 'ACTIVE' ? 'nmsTemplates.list.stats.active' : 'nmsTemplates.list.stats.inactive');
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

  get pageTitle(): string {
    if (!this.channel) return this.translate.instant('nmsTemplates.list.pageTitle.all');
    const labels: Record<string, string> = {
      EMAIL: this.translate.instant('nmsTemplates.list.pageTitle.email'),
      SMS: this.translate.instant('nmsTemplates.list.pageTitle.sms'),
      WHATSAPP: this.translate.instant('nmsTemplates.list.pageTitle.whatsapp'),
      FCM_PUSH: this.translate.instant('nmsTemplates.list.pageTitle.fcmPush'),
    };
    return labels[this.channel] ?? this.translate.instant('nmsTemplates.list.pageTitle.generic', { channel: this.channel });
  }

  eventLabel(val: string): string { return getEventLabel(val); }

  create(): void {
    const qp: any = {};
    if (this.channel) qp['channel'] = this.channel;
    this.router.navigate(['/nms-templates/create'], { queryParams: qp });
  }

  edit(row: NmsTemplate): void {
    this.router.navigate(['/nms-templates/edit', row.id]);
  }

  delete(row: NmsTemplate): void {
    this.appDialog.confirm({
      title: this.translate.instant('nmsTemplates.list.deleteDialog.title'),
      message: this.translate.instant('nmsTemplates.list.deleteDialog.message', { name: row.name }),
      icon: 'bi-trash3',
      variant: 'danger',
      confirmText: this.translate.instant('shared.listPage.rowActions.delete'),
      cancelText: this.translate.instant('nmsTemplates.list.deleteDialog.keep'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.svc.remove(row.id!).subscribe({
        next: () => {
          this.notify.success(this.translate.instant('nmsTemplates.list.messages.deleted'));
          this.load();
        },
        error: () => this.notify.error(this.translate.instant('nmsTemplates.list.messages.deleteFailed')),
      });
    });
  }

  /** Back to the tabbed screen, on the tab of the channel being listed when there is one. */
  back(): void {
    if (this.channel) {
      this.router.navigate(['/nms-templates'], { queryParams: { channel: this.channel } });
      return;
    }
    this.router.navigate(['/nms-templates']);
  }
}
