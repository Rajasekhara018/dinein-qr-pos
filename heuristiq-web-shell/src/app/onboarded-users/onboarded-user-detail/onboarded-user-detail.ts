import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { RECORD_TYPE_ONBOARDED_USER } from '../../workspace/workspace.constants';
import { TranslateService } from '@ngx-translate/core';
import { OnboardingAdminService } from '../../shared/services/onboarding-admin-service';
import { NotificationService } from '../../shared/services/notification-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { MatDialog } from '@angular/material/dialog';
import { DocumentPreviewData, DocumentPreviewDialog } from '../document-preview-dialog/document-preview-dialog';
import { HqCommonTableColumn, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY_VALUE, FRANCHISE_LINKED_TYPE, ONBOARDING_ADMIN_PRIVILEGE, RELINK_STATUS } from '../onboarded-users.constants';
import { OnboardingDocumentRef, OnboardingSubmission, SubmissionProgress } from '../onboarded-users.models';
import { answerText, displayValue, formatBytes, formatDateTime, franchiseLabel, organizationLabel, placeLabel } from '../onboarded-users.utils';

interface AnswerRow {
  field: string;
  value: string;
}

/** Full view of one onboarding submission -- its own screen, reached from the list's eye icon. */
@Component({
  selector: 'app-onboarded-user-detail',
  standalone: false,
  templateUrl: './onboarded-user-detail.html',
  styleUrl: './onboarded-user-detail.scss',
})
export class OnboardedUserDetail implements OnInit {
  private readonly hqTimezone = inject(HqTimezoneService);
  submissionId = '';
  /** Type under which the team's notes on this record are filed. */
  readonly recordType = RECORD_TYPE_ONBOARDED_USER;
  submission: OnboardingSubmission | null = null;
  isLoading = false;
  progress: SubmissionProgress | null = null;
  progressLoading = false;
  progressFailed = false;

  answerRows: AnswerRow[] = [];
  answerColumns: HqCommonTableColumn<AnswerRow>[] = [];
  documentColumns: HqCommonTableColumn<OnboardingDocumentRef>[] = [];
  isRetrying = false;
  isRelinking = false;
  /** Privilege that unlocks the change actions of this screen (retry, re-link). */
  readonly adminPrivilege = ONBOARDING_ADMIN_PRIVILEGE;

  constructor(
    private route: ActivatedRoute,
    private onboarding: OnboardingAdminService,
    private notify: NotificationService,
    private dialogs: AppDialogService,
    private matDialog: MatDialog,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const t = (key: string) => this.translate.instant(key);
    this.answerColumns = [
      { key: 'field', label: t('onboardingSubmissions.fields.field') },
      { key: 'value', label: t('onboardingSubmissions.fields.value') },
    ];
    this.documentColumns = [
      { key: 'label', label: t('onboardingSubmissions.documents.field') },
      {
        key: 'fileName',
        label: t('onboardingSubmissions.documents.file'),
        render: (row: OnboardingDocumentRef) => row.fileName || (row.documentId ? row.documentId : t('onboardingSubmissions.documents.notUploaded')),
      },
      {
        key: 'contentType',
        label: t('onboardingSubmissions.documents.type'),
        render: (row: OnboardingDocumentRef) => (row.stored === false ? t('onboardingSubmissions.documents.placeholderOnly') : row.contentType || EMPTY_VALUE),
      },
      { key: 'sizeBytes', label: t('onboardingSubmissions.documents.size'), render: (row: OnboardingDocumentRef) => formatBytes(row.sizeBytes) },
    ];
    this.submissionId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  get displayName(): string {
    return this.submission ? answerText(this.submission, 'fullName') : EMPTY_VALUE;
  }

  get subtitle(): string {
    if (!this.submission) return '';
    const status = this.translate.instant('onboardingSubmissions.statuses.' + this.submission.status);
    const contact = [answerText(this.submission, 'mobile'), answerText(this.submission, 'email')]
      .filter((v) => v !== EMPTY_VALUE)
      .join(' · ');
    return [contact, status].filter(Boolean).join(' · ');
  }

  date(iso: string | null | undefined): string {
    return formatDateTime(iso, this.hqTimezone.effectiveTimezone());
  }

  /**
   * Account setup can be (re)run for an approved or failed submission, and also for one still under review whose reviewers have
   * all approved it (its approval could not finish when the last decision arrived); the server runs the same checks either way.
   */
  get canRetryProvisioning(): boolean {
    const status = this.submission?.status;
    if (status === 'APPROVED' || status === 'ACCOUNT_CREATION_FAILED') return true;
    return (status === 'SUBMITTED' || status === 'UNDER_REVIEW') && this.progress?.reviewStatus === 'APPROVED';
  }

  /** Organization, franchise and place labels of the Placement section. */
  get organization(): string {
    return this.submission ? organizationLabel(this.submission) : EMPTY_VALUE;
  }

  get franchise(): string {
    return this.submission ? franchiseLabel(this.submission) : EMPTY_VALUE;
  }

  get place(): string {
    return this.submission ? placeLabel(this.submission) : EMPTY_VALUE;
  }

  /** An approved driver with an account and a franchise: their franchise membership can be repaired. */
  get canRelinkFranchise(): boolean {
    const s = this.submission;
    return !!s && s.typeCode === FRANCHISE_LINKED_TYPE && s.status === RELINK_STATUS && !!s.franchiseId && !!s.subjectId;
  }

  relinkFranchise(): void {
    if (this.isRelinking || !this.canRelinkFranchise) return;
    this.dialogs
      .confirm({
        title: this.translate.instant('onboardingSubmissions.relink.title'),
        message: this.translate.instant('onboardingSubmissions.relink.message', { name: this.displayName, franchise: this.franchise }),
        icon: 'bi-link-45deg',
        confirmText: this.translate.instant('onboardingSubmissions.relink.confirm'),
      })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.isRelinking = true;
        this.onboarding.relinkFranchise(this.submissionId).subscribe({
          next: (result) => {
            this.isRelinking = false;
            this.notify.success(this.translate.instant(result.created ? 'onboardingSubmissions.relink.success' : 'onboardingSubmissions.relink.already'));
            this.cdr.detectChanges();
          },
          error: (err: { error?: { message?: string } }) => {
            this.isRelinking = false;
            this.notify.error(err?.error?.message || this.translate.instant('onboardingSubmissions.relink.failed'));
            this.cdr.detectChanges();
          },
        });
      });
  }

  get documents(): OnboardingDocumentRef[] {
    return this.submission?.documents ?? [];
  }

  documentLabel(fieldKey: string): string {
    return this.documents.find(d => d.fieldKey === fieldKey)?.label ?? fieldKey;
  }

  get approvedReviewCount(): number {
    return this.progress?.reviews.filter(r => r.status === 'APPROVED').length ?? 0;
  }

  /** Refresh the submission and its gates together after operational work in another screen. */
  refreshProgress(): void {
    if (!this.isLoading && !this.progressLoading) this.load();
  }

  private loadProgress(): void {
    this.progress = null;
    this.progressLoading = true;
    this.progressFailed = false;
    this.onboarding.progress(this.submissionId).subscribe({
      next: progress => {
        this.progress = progress;
        this.progressLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.progressLoading = false;
        this.progressFailed = true;
        this.cdr.markForCheck();
      },
    });
  }

  /** Shows the stored file (fetched from Rider_DMS through the onboarding service) in a modal. */
  openDocument(doc: OnboardingDocumentRef): void {
    const data: DocumentPreviewData = {
      title: doc.label,
      fileName: doc.fileName,
      blob$: this.onboarding.downloadDocument(this.submissionId, doc.fieldKey),
    };
    this.matDialog.open(DocumentPreviewDialog, {
      width: 'min(920px, 94vw)',
      maxWidth: '94vw',
      autoFocus: false,
      restoreFocus: true,
      panelClass: 'app-dialog-panel',
      data,
    });
  }

  retryProvisioning(): void {
    if (this.isRetrying || !this.submission) return;
    this.dialogs
      .confirm({
        title: this.translate.instant('onboardingSubmissions.retry.title'),
        message: this.translate.instant('onboardingSubmissions.retry.message', { name: this.displayName }),
        icon: 'bi-arrow-repeat',
        confirmText: this.translate.instant('onboardingSubmissions.retry.confirm'),
      })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.isRetrying = true;
        this.onboarding.retryProvisioning(this.submissionId).subscribe({
          next: (updated) => {
            this.isRetrying = false;
            if (updated.status === 'APPROVED') {
              this.notify.success(this.translate.instant('onboardingSubmissions.retry.success'));
            } else {
              this.notify.error(updated.rejectionReason || this.translate.instant('onboardingSubmissions.retry.failed'));
            }
            this.load();
          },
          error: (err: unknown) => {
            this.isRetrying = false;
            // The server names what still blocks approval (e.g. a document not yet verified): show it, else the generic text.
            const body = (err as { error?: { message?: unknown } } | null)?.error;
            const reason = typeof body?.message === 'string' && body.message ? body.message : null;
            this.notify.error(reason ?? this.translate.instant('onboardingSubmissions.retry.failed'));
            this.cdr.detectChanges();
          },
        });
      });
  }

  private load(): void {
    this.isLoading = true;
    this.onboarding.get(this.submissionId).subscribe({
      next: (submission) => {
        this.submission = submission;
        this.loadProgress();
        this.answerRows = Object.entries(submission.answers ?? {}).map(([field, value]) => ({
          field,
          value: displayValue(value),
        }));
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isLoading = false;
        this.notify.error(this.translate.instant('onboardingSubmissions.detailLoadFailed'));
        this.cdr.detectChanges();
      },
    });
  }
}
