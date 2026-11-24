import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { WorkflowHistoryService } from '../../workflow/history/workflow-history.service';
import { TranslateService } from '@ngx-translate/core';
import { OnboardingAdminService } from '../../shared/services/onboarding-admin-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { HqCommonTableColumn } from 'hq-shared-lib';
import { EMPTY_VALUE } from '../../onboarded-users/onboarded-users.constants';
import { answerText, franchiseLabel, formatDateTime, organizationLabel, placeLabel } from '../../onboarded-users/onboarded-users.utils';
import { OnboardingReviewItem } from '../onboarding-reviews.models';

/** Onboarding submissions waiting on a human decision (open Rider_WorkflowService review tasks). */
@Component({
  selector: 'app-onboarding-reviews-list',
  standalone: false,
  templateUrl: './onboarding-reviews-list.html',
  styleUrl: './onboarding-reviews-list.scss',
})
export class OnboardingReviewsList implements OnInit {
  private readonly historyDialog = inject(WorkflowHistoryService);
  rows: OnboardingReviewItem[] = [];
  isLoading = false;
  hasError = false;
  /** Submission currently being approved/rejected -- its row actions are disabled meanwhile. */
  busySubmissionId: string | null = null;

  columns: HqCommonTableColumn<OnboardingReviewItem>[] = [];

  constructor(
    private router: Router,
    private onboarding: OnboardingAdminService,
    private dialogs: AppDialogService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const t = (key: string) => this.translate.instant(key);
    this.columns = [
      { key: 'reference', label: t('onboardingSubmissions.columns.reference'), render: (row: OnboardingReviewItem) => row.reference || '—' },
      { key: 'name', label: t('onboardingSubmissions.columns.name'), render: (row: OnboardingReviewItem) => this.answer(row, 'fullName') },
      {
        key: 'contact',
        label: t('onboardingSubmissions.columns.contact'),
        render: (row: OnboardingReviewItem) =>
          [this.answer(row, 'mobile'), this.answer(row, 'email')].filter((v) => v !== EMPTY_VALUE).join(' · ') || EMPTY_VALUE,
      },
      { key: 'typeCode', label: t('onboardingSubmissions.columns.type'), render: (row: OnboardingReviewItem) => t('onboardingSubmissions.types.' + row.typeCode) },
      { key: 'status', label: t('onboardingSubmissions.columns.status'), render: (row: OnboardingReviewItem) => t('onboardingSubmissions.statuses.' + row.status) },
      { key: 'createdAt', label: t('onboardingSubmissions.columns.submitted'), render: (row: OnboardingReviewItem) => formatDateTime(row.createdAt) },
      { key: 'organization', label: t('onboardingSubmissions.columns.organization'), render: (row: OnboardingReviewItem) => organizationLabel(row) },
      { key: 'franchise', label: t('onboardingSubmissions.columns.franchise'), render: (row: OnboardingReviewItem) => franchiseLabel(row) },
      { key: 'place', label: t('onboardingSubmissions.columns.place'), render: (row: OnboardingReviewItem) => placeLabel(row) },
    ];
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.hasError = false;
    this.onboarding.listOpenReviews().subscribe({
      next: (items) => {
        this.rows = items ?? [];
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.rows = [];
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('onboardingReviews.loadFailed'));
        this.cdr.detectChanges();
      },
    });
  }

  viewDetail(row: OnboardingReviewItem): void {
    this.router.navigate(['/onboarded-users', row.submissionId]);
  }

  /** Shows the request's history (who did what, when) in a dialog, without leaving the list. */
  history(row: OnboardingReviewItem): void {
    if (!row.workflowInstanceId) return;
    this.historyDialog.open({ instanceId: row.workflowInstanceId, title: row.reference ?? null, subtitle: this.answer(row, 'fullName') || null });
  }

  approve(row: OnboardingReviewItem): void {
    if (this.busySubmissionId) return;
    this.dialogs
      .confirm({
        title: this.translate.instant('onboardingReviews.approveDialog.title'),
        message: this.translate.instant('onboardingReviews.approveDialog.message', { name: this.answer(row, 'fullName') }),
        icon: 'bi-check-circle',
        confirmText: this.translate.instant('onboardingReviews.approveDialog.confirm'),
      })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.busySubmissionId = row.submissionId;
        this.onboarding.approveReview(row.submissionId).subscribe({
          next: () => this.finish('onboardingReviews.messages.approved'),
          error: () => this.fail('onboardingReviews.messages.approveFailed'),
        });
      });
  }

  /** Sends the submission back to the applicant to fix, with a required reason. */
  returnForCorrection(row: OnboardingReviewItem): void {
    if (this.busySubmissionId) return;
    this.dialogs
      .prompt({
        title: this.translate.instant('onboardingReviews.returnDialog.title'),
        message: this.translate.instant('onboardingReviews.returnDialog.message', { name: this.answer(row, 'fullName') }),
        icon: 'bi-arrow-return-left',
        variant: 'warning',
        confirmText: this.translate.instant('onboardingReviews.returnDialog.confirm'),
        field: {
          key: 'reason',
          label: this.translate.instant('onboardingReviews.returnDialog.reasonLabel'),
          placeholder: this.translate.instant('onboardingReviews.returnDialog.reasonPlaceholder'),
          type: 'textarea',
          required: true,
        },
      })
      .subscribe((reason) => {
        if (!reason?.trim()) return;
        this.busySubmissionId = row.submissionId;
        this.onboarding.returnReview(row.submissionId, reason.trim()).subscribe({
          next: () => this.finish('onboardingReviews.messages.returned'),
          error: () => this.fail('onboardingReviews.messages.returnFailed'),
        });
      });
  }

  reject(row: OnboardingReviewItem): void {
    if (this.busySubmissionId) return;
    this.dialogs
      .prompt({
        title: this.translate.instant('onboardingReviews.rejectDialog.title'),
        message: this.translate.instant('onboardingReviews.rejectDialog.message', { name: this.answer(row, 'fullName') }),
        icon: 'bi-x-circle',
        variant: 'danger',
        confirmText: this.translate.instant('onboardingReviews.rejectDialog.confirm'),
        field: {
          key: 'reason',
          label: this.translate.instant('onboardingReviews.rejectDialog.reasonLabel'),
          placeholder: this.translate.instant('onboardingReviews.rejectDialog.reasonPlaceholder'),
          type: 'textarea',
          required: true,
        },
      })
      .subscribe((reason) => {
        if (!reason?.trim()) return;
        this.busySubmissionId = row.submissionId;
        this.onboarding.rejectReview(row.submissionId, reason.trim()).subscribe({
          next: () => this.finish('onboardingReviews.messages.rejected'),
          error: () => this.fail('onboardingReviews.messages.rejectFailed'),
        });
      });
  }

  private answer(row: OnboardingReviewItem, key: string): string {
    return answerText(row, key);
  }

  private finish(messageKey: string): void {
    this.busySubmissionId = null;
    this.notify.success(this.translate.instant(messageKey));
    this.load();
  }

  private fail(messageKey: string): void {
    this.busySubmissionId = null;
    this.notify.error(this.translate.instant(messageKey));
    this.cdr.detectChanges();
  }
}
