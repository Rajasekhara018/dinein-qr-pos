import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { WorkflowHistoryService } from '../../workflow/history/workflow-history.service';
import { TranslateService } from '@ngx-translate/core';
import { OnboardingAdminService } from '../../shared/services/onboarding-admin-service';
import { NotificationService } from '../../shared/services/notification-service';
import { HqCommonTableColumn, HqTimezoneService } from 'hq-shared-lib';
import {
  DEFAULT_PAGE_SIZE,
  EMPTY_VALUE,
  PAGE_SIZE_OPTIONS,
  STATUS_OPTIONS,
  TYPE_OPTIONS,
} from '../onboarded-users.constants';
import { OnboardingSubmission, OnboardingSubmissionFilters } from '../onboarded-users.models';
import { answerText, franchiseLabel, formatDateTime, organizationLabel, placeLabel } from '../onboarded-users.utils';

/** Everyone who has gone through onboarding (customers + drivers, any status) -- read-only. */
@Component({
  selector: 'app-onboarded-users-list',
  standalone: false,
  templateUrl: './onboarded-users-list.html',
  styleUrl: './onboarded-users-list.scss',
})
export class OnboardedUsersList implements OnInit {
  private readonly hqTimezone = inject(HqTimezoneService);
  private readonly historyDialog = inject(WorkflowHistoryService);
  readonly typeOptions = TYPE_OPTIONS;
  readonly statusOptions = STATUS_OPTIONS;
  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;

  rows: OnboardingSubmission[] = [];
  isLoading = false;
  hasError = false;
  page = 0;
  pageSize = DEFAULT_PAGE_SIZE;
  totalElements = 0;
  totalPages = 0;

  filters: OnboardingSubmissionFilters = { type: '', status: '', query: '' };
  columns: HqCommonTableColumn<OnboardingSubmission>[] = [];

  constructor(
    private router: Router,
    private onboarding: OnboardingAdminService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const t = (key: string) => this.translate.instant(key);
    this.columns = [
      { key: 'reference', label: t('onboardingSubmissions.columns.reference'), render: (row: OnboardingSubmission) => row.reference || '—' },
      { key: 'name', label: t('onboardingSubmissions.columns.name'), render: (row: OnboardingSubmission) => answerText(row, 'fullName') },
      {
        key: 'contact',
        label: t('onboardingSubmissions.columns.contact'),
        render: (row: OnboardingSubmission) =>
          [answerText(row, 'mobile'), answerText(row, 'email')].filter((v) => v !== EMPTY_VALUE).join(' · ') || EMPTY_VALUE,
      },
      { key: 'typeCode', label: t('onboardingSubmissions.columns.type'), render: (row: OnboardingSubmission) => t('onboardingSubmissions.types.' + row.typeCode) },
      { key: 'status', label: t('onboardingSubmissions.columns.status'), render: (row: OnboardingSubmission) => t('onboardingSubmissions.statuses.' + row.status) },
      { key: 'createdAt', label: t('onboardingSubmissions.columns.submitted'), render: (row: OnboardingSubmission) => formatDateTime(row.createdAt, this.hqTimezone.effectiveTimezone()) },
      { key: 'organization', label: t('onboardingSubmissions.columns.organization'), render: (row: OnboardingSubmission) => organizationLabel(row) },
      { key: 'franchise', label: t('onboardingSubmissions.columns.franchise'), render: (row: OnboardingSubmission) => franchiseLabel(row) },
      { key: 'place', label: t('onboardingSubmissions.columns.place'), render: (row: OnboardingSubmission) => placeLabel(row) },
    ];
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    this.onboarding.list(this.filters, this.page, this.pageSize).subscribe({
      next: (res) => {
        this.rows = res.items ?? [];
        this.totalElements = res.totalElements ?? this.rows.length;
        this.totalPages = res.totalPages ?? 1;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('onboardingSubmissions.loadFailed'));
        this.cdr.detectChanges();
      },
    });
  }

  applyFilters(): void {
    this.page = 0;
    this.load();
  }

  clearFilters(): void {
    this.filters = { type: '', status: '', query: '' };
    this.applyFilters();
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.page = 0;
    this.load();
  }

  /** hq-table-footer's (pageChange): an absolute 0-based page. */
  goToPage(page: number): void {
    this.page = page;
    this.load();
  }

  viewDetail(row: OnboardingSubmission): void {
    this.router.navigate(['/onboarded-users', row.id]);
  }

  /** Shows the request's history (who did what, when) in a dialog, without leaving the list. */
  history(row: OnboardingSubmission): void {
    if (!row.workflowInstanceId) return;
    this.historyDialog.open({ instanceId: row.workflowInstanceId, title: row.reference ?? null, subtitle: answerText(row, 'fullName') || null });
  }
}
