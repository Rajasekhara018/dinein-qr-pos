import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqDocument, HqDocumentService, HqOpenFile, HqPrivilegeAccessService, HqStatusTone } from 'hq-shared-lib';
import { Observable, map, switchMap } from 'rxjs';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { AGENT_KYC_PRIVILEGES, AGENT_KYC_STATUS_TONE } from './agent-kyc.constants';
import { AgentKyc } from './agent-kyc.models';
import { AgentKycService } from './agent-kyc.service';
import {
  KYC_DMS_DOCUMENT_TYPES, KYC_DOCUMENT_TYPES, ONBOARDING_DMS_CATEGORY, ONBOARDING_LIMITS, OnboardingSubject,
} from './franchise-onboarding.constants';
import { ONBOARDING_UPLOAD_ACCEPT, ONBOARDING_UPLOAD_MAX_SIZE_MB } from './franchise-onboarding-upload.constants';
import { ONBOARDING_DOCUMENT_PROVIDERS } from './franchise-onboarding-upload.service';
import { documentError } from './franchise-onboarding.helpers';
import { KycDocument, ReviewDecision } from './franchise-onboarding.models';

/**
 * The KYC of one agent of a franchise: their documents with the review state, and a form to add one. An administrator adds and verifies
 * or rejects (a rejection needs a reason); the franchise owner (route under `my-franchises`) adds documents for their own agents only.
 * Files go to the document service first (hq-doc-upload); only their ids are sent.
 */
@Component({
  selector: 'app-agent-kyc-page',
  standalone: false,
  templateUrl: './agent-kyc-page.html',
  styleUrls: ['../franchises/franchise-form-page.scss', './franchise-onboarding-page.scss'],
  providers: [ONBOARDING_DOCUMENT_PROVIDERS],
})
export class AgentKycPage implements OnInit {
  readonly documentTypes = KYC_DOCUMENT_TYPES;
  readonly dmsCategory = ONBOARDING_DMS_CATEGORY;
  readonly limits = ONBOARDING_LIMITS;
  readonly uploadAccept = ONBOARDING_UPLOAD_ACCEPT;
  readonly uploadMaxSizeMB = ONBOARDING_UPLOAD_MAX_SIZE_MB;

  subject: OnboardingSubject = 'franchise';
  franchiseId = '';
  memberId = '';
  canAdd = false;
  canReview = false;
  data: AgentKyc | null = null;
  documentForm = { type: KYC_DOCUMENT_TYPES[0], number: '', file: null as HqDocument | null };
  isLoading = true;
  loadFailed = false;
  busy = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private api: AgentKycService,
    private access: HqPrivilegeAccessService,
    private documents: HqDocumentService,
    private dialog: MatDialog,
    private dialogs: AppDialogService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.subject = (this.route.snapshot.data['subject'] as OnboardingSubject | undefined) ?? 'franchise';
    this.franchiseId = this.route.snapshot.paramMap.get('id') ?? '';
    this.memberId = this.route.snapshot.paramMap.get('memberId') ?? '';
    const p = AGENT_KYC_PRIVILEGES[this.subject];
    this.canAdd = this.access.can(p.add);
    this.canReview = !!p.review && this.access.can(p.review);
    this.load();
  }

  tone(status: string | null | undefined): HqStatusTone {
    return AGENT_KYC_STATUS_TONE[status ?? 'NONE'] ?? 'neutral';
  }

  /** The DMS document type the chosen document is uploaded as (none for OTHER). */
  get dmsDocumentType(): string | null {
    return KYC_DMS_DOCUMENT_TYPES[this.documentForm.type] ?? null;
  }

  onUploaded(file: HqDocument): void {
    this.documentForm = { ...this.documentForm, file };
  }

  onRemoved(): void {
    this.documentForm = { ...this.documentForm, file: null };
  }

  addDocument(): void {
    const { type, number, file } = this.documentForm;
    const error = documentError(type, number, file?.id ?? '');
    if (error) {
      this.notify.error(this.translate.instant(`franchiseOnboarding.errors.${error}`));
      return;
    }
    const request = { type, number: number.trim() || null, dmsDocumentId: file!.id, fileName: file!.originalFileName, contentType: file!.contentType || null };
    this.run(this.api.add(this.subject, this.franchiseId, this.memberId, request), 'agentKyc.added', true);
  }

  reviewDocument(document: KycDocument, decision: ReviewDecision): void {
    if (!this.canReview) return;
    this.decide(decision).subscribe((reason) => {
      if (reason === null) return;
      this.run(this.api.review(this.franchiseId, this.memberId, document.id, { decision, reason: reason || null }), 'agentKyc.reviewed', false);
    });
  }

  /** Shows a stored file in the preview dialog. */
  viewFile(dmsDocumentId: string, fileName: string): void {
    this.documents.getMetadata(dmsDocumentId).pipe(switchMap((doc) => this.documents.fetchFile(doc))).subscribe({
      next: (blob) => this.dialog.open(HqOpenFile, { data: { blob, fileName }, width: '900px', maxWidth: '96vw' }),
      error: () => this.notify.error(this.translate.instant('franchiseOnboarding.messages.fileFailed')),
    });
  }

  back(): void {
    const area = this.subject === 'myFranchise' ? 'my-franchises' : 'franchises';
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, area, this.franchiseId, 'members']);
  }

  private load(): void {
    this.isLoading = true;
    this.loadFailed = false;
    this.api.get(this.subject, this.franchiseId, this.memberId).subscribe({
      next: (data) => {
        this.data = data;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoading = false;
        this.loadFailed = true;
        this.cdr.markForCheck();
      },
    });
  }

  /** Asks for the rejection reason (required); a verification needs only a confirmation. Null when cancelled. */
  private decide(decision: ReviewDecision): Observable<string | null> {
    if (decision === 'VERIFY') {
      return this.dialogs.confirm({
        title: this.translate.instant('franchiseOnboarding.review.verifyTitle'),
        message: this.translate.instant('franchiseOnboarding.review.verifyMessage'),
        confirmText: this.translate.instant('franchiseOnboarding.review.verify'),
        cancelText: this.translate.instant('franchiseOnboarding.cancel'),
      }).pipe(map((ok) => (ok ? '' : null)));
    }
    return this.dialogs.prompt({
      title: this.translate.instant('franchiseOnboarding.review.rejectTitle'),
      confirmText: this.translate.instant('franchiseOnboarding.review.reject'),
      cancelText: this.translate.instant('franchiseOnboarding.cancel'),
      variant: 'danger',
      field: { key: 'reason', label: this.translate.instant('franchiseOnboarding.review.reason'), type: 'textarea', required: true },
    }).pipe(map((reason) => (reason && reason.trim() ? reason.trim().slice(0, ONBOARDING_LIMITS.reason) : null)));
  }

  private run(call: Observable<AgentKyc>, successKey: string, clearForm: boolean): void {
    if (this.busy) return;
    this.busy = true;
    call.subscribe({
      next: (data) => {
        this.busy = false;
        this.data = data;
        if (clearForm) {
          this.documentForm = { type: KYC_DOCUMENT_TYPES[0], number: '', file: null };
        }
        this.notify.success(this.translate.instant(successKey));
        this.cdr.markForCheck();
      },
      error: (err: unknown) => {
        this.busy = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant('agentKyc.failed')));
        this.cdr.markForCheck();
      },
    });
  }
}
