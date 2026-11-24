import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Observable, map, switchMap } from 'rxjs';
import { HqDocument, HqDocumentService, HqOpenFile, HqOption, HqPrivilegeAccessService, HqStatusTone } from 'hq-shared-lib';
import { environment } from '../../../environments/environment';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { countryOptions, currencyOptions } from '../../shared/utils/regional-names.helpers';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { FRANCHISE_SETUP_ROUTE, FranchiseOnboardingSection } from '../franchise-setup/franchise-setup.constants';
import {
  AGREEMENT_DMS_DOCUMENT_TYPE, AGREEMENT_RENEWALS, BANK_INPUT_MAX, BANK_PROOF_DMS_DOCUMENT_TYPE, KYC_DMS_DOCUMENT_TYPES, KYC_DOCUMENT_TYPES,
  ONBOARDING_CHECKLIST_ITEMS, ONBOARDING_DMS_CATEGORY, ONBOARDING_LIMITS, ONBOARDING_STATUS_TONE, ONBOARDING_SUBJECTS, OnboardingSubject,
  OnboardingSubjectConfig, ROUTING_INPUT_MAX,
} from './franchise-onboarding.constants';
import {
  agreementRequestOf, bankRequestOf, compactCode, emptyAgreement, emptyBankAccount, ibanAccepted, ibanRequired, normalizedAgreementRequest,
  normalizedBankRequest, onboardingError, routingRuleFor, todayIso,
} from './franchise-onboarding.helpers';
import {
  FieldIssue, FieldIssues, HeldServerError, accountNumberRequired, agreementIssues, bankAccountIssues, documentIssues, fieldSnapshot,
  hasIssues, routingRequired, serverFieldError, shownIssues,
} from './franchise-onboarding.validation';
import { AgreementRequest, BankAccountRequest, FranchiseOnboarding, KycDocument, ReviewDecision } from './franchise-onboarding.models';
import { FranchiseOnboardingService } from './franchise-onboarding.service';
import { ONBOARDING_UPLOAD_ACCEPT, ONBOARDING_UPLOAD_MAX_SIZE_MB } from './franchise-onboarding-upload.constants';
import { ONBOARDING_DOCUMENT_PROVIDERS } from './franchise-onboarding-upload.service';

/** What a person may do on the page, worked out once from their privileges. */
interface OnboardingAbilities {
  addDocument: boolean;
  withdrawDocument: boolean;
  reviewDocument: boolean;
  saveBank: boolean;
  reviewBank: boolean;
  saveAgreement: boolean;
  activate: boolean;
}

/**
 * A franchise's onboarding on one page: the checklist (legal and tax details, KYC, payout bank account, agreement), the
 * KYC documents with their review, the bank account, the agreement and, for administrators, activation once everything is
 * complete. The owner sees the same page for their own franchise, without the review, agreement and activation actions.
 * Files go to DMS first (hq-doc-upload); the franchise service keeps their DMS ids. Inside the franchise setup stepper it is
 * embedded (no heading) and shows one section per step (`section`); one instance serves every onboarding step, so the checklist
 * of the activation step follows what the other steps saved.
 */
@Component({
  selector: 'app-franchise-onboarding-page',
  standalone: false,
  templateUrl: './franchise-onboarding-page.html',
  styleUrls: ['../franchises/franchise-form-page.scss', './franchise-onboarding-page.scss'],
  providers: [ONBOARDING_DOCUMENT_PROVIDERS],
})
export class FranchiseOnboardingPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(FranchiseOnboardingService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly organizations = inject(OrganizationContextService);
  private readonly documents = inject(HqDocumentService);
  private readonly dialog = inject(MatDialog);
  private readonly dialogs = inject(AppDialogService);
  private readonly notify = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  /** Inside the franchise setup stepper: no heading or back link (the stepper has them). */
  @Input() embedded = false;
  /** The one section to show (a setup step); null shows them all. */
  @Input() section: FranchiseOnboardingSection | null = null;
  /** The onboarding was read or changed: the stepper follows its checklist and status. */
  @Output() readonly onboardingChanged = new EventEmitter<FranchiseOnboarding>();

  readonly checklistItems = ONBOARDING_CHECKLIST_ITEMS;
  readonly documentTypes = KYC_DOCUMENT_TYPES;
  readonly renewals = AGREEMENT_RENEWALS;
  readonly limits = ONBOARDING_LIMITS;
  readonly uploadAccept = ONBOARDING_UPLOAD_ACCEPT;
  readonly uploadMaxSizeMB = ONBOARDING_UPLOAD_MAX_SIZE_MB;
  readonly bankInputMax = BANK_INPUT_MAX;
  readonly routingInputMax = ROUTING_INPUT_MAX;
  readonly dmsCategory = ONBOARDING_DMS_CATEGORY;
  readonly bankProofDocumentType = BANK_PROOF_DMS_DOCUMENT_TYPE;
  readonly agreementDocumentType = AGREEMENT_DMS_DOCUMENT_TYPE;

  subject: OnboardingSubject = 'franchise';
  config: OnboardingSubjectConfig = ONBOARDING_SUBJECTS.franchise;
  franchiseId = '';
  can: OnboardingAbilities = { addDocument: false, withdrawDocument: false, reviewDocument: false, saveBank: false, reviewBank: false, saveAgreement: false, activate: false };
  countries: HqOption[] = [];
  currencies: HqOption[] = [];
  private defaultCountry = '';
  private defaultCurrency = '';

  data: FranchiseOnboarding | null = null;
  isLoading = true;
  loadFailed = false;
  busy = false;

  documentForm = { type: KYC_DOCUMENT_TYPES[0], number: '', file: null as HqDocument | null };
  bankForm: BankAccountRequest = emptyBankAccount('', '');
  bankEditing = false;
  agreementForm: AgreementRequest = emptyAgreement('');
  agreementEditing = false;

  /** Whether a save of each form was tried: its own checks show from then on. */
  documentTried = false;
  bankTried = false;
  agreementTried = false;
  /** The service's field error of the last failed save of each form, shown until that field changes. */
  private documentServer: HeldServerError | null = null;
  private bankServer: HeldServerError | null = null;
  private agreementServer: HeldServerError | null = null;
  private readonly issueCache = new Map<string, { key: string; issues: FieldIssues }>();

  ngOnInit(): void {
    this.subject = (this.route.snapshot.data['subject'] as OnboardingSubject | undefined) ?? 'franchise';
    this.config = ONBOARDING_SUBJECTS[this.subject];
    this.franchiseId = this.route.snapshot.paramMap.get('id') ?? '';
    const p = this.config.privileges;
    const may = (code: string | null) => !!code && this.access.can(code);
    this.can = {
      addDocument: may(p.addDocument), withdrawDocument: may(p.withdrawDocument), reviewDocument: may(p.reviewDocument),
      saveBank: may(p.saveBank), reviewBank: may(p.reviewBank), saveAgreement: may(p.saveAgreement), activate: may(p.activate),
    };
    const locale = document.documentElement.lang || 'en';
    this.countries = countryOptions(locale);
    this.currencies = currencyOptions(locale);
    this.defaultCountry = this.organizations.currentCountry() ?? environment.defaultPhoneCountry;
    this.defaultCurrency = this.organizations.currentCurrency() ?? environment.defaultCurrency;
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.loadFailed = false;
    this.api.get(this.subject, this.franchiseId).subscribe({
      next: (data) => this.show(data),
      error: () => {
        this.isLoading = false;
        this.loadFailed = true;
        this.cdr.markForCheck();
      },
    });
  }

  /** True when the section is on screen: the one chosen by the stepper, or every section on the full page. */
  shows(section: FranchiseOnboardingSection): boolean {
    return !this.section || this.section === section;
  }

  // ── checklist and activation ─────────────────────────────────────────────

  tone(status: string | null | undefined): HqStatusTone {
    return ONBOARDING_STATUS_TONE[status ?? 'MISSING'] ?? 'neutral';
  }

  /** The legal and tax details are a step of the franchise setup. */
  get legalProfileLink(): string[] {
    return [ACCESS_MANAGEMENT_BASE_PATH, this.config.legalProfileRoute, this.franchiseId, FRANCHISE_SETUP_ROUTE, 'legal'];
  }

  activate(): void {
    if (!this.can.activate || !this.data?.canActivate) return;
    this.dialogs.confirm({
      title: this.translate.instant('franchiseOnboarding.activate.title'),
      message: this.translate.instant('franchiseOnboarding.activate.message', { code: this.data.code }),
      confirmText: this.translate.instant('franchiseOnboarding.activate.confirm'),
      cancelText: this.translate.instant('franchiseOnboarding.cancel'),
    }).subscribe((ok) => {
      if (ok) this.run(this.api.activate(this.franchiseId), 'franchiseOnboarding.messages.activated');
    });
  }

  // ── KYC documents ────────────────────────────────────────────────────────

  /** The DMS document type the chosen KYC document is uploaded as (none for OTHER). */
  get kycDocumentType(): string | null {
    return KYC_DMS_DOCUMENT_TYPES[this.documentForm.type] ?? null;
  }

  onDocumentUploaded(file: HqDocument): void {
    this.documentForm = { ...this.documentForm, file };
  }

  onDocumentRemoved(): void {
    this.documentForm = { ...this.documentForm, file: null };
  }

  addDocument(): void {
    const { type, number, file } = this.documentForm;
    this.documentTried = true;
    this.documentServer = null;
    if (!file || hasIssues(this.documentChecks())) {
      this.notify.error(this.translate.instant('franchiseOnboarding.validation.fixErrors'));
      return;
    }
    const request = { type, number: number.trim() || null, dmsDocumentId: file.id, fileName: file.originalFileName, contentType: file.contentType || null };
    this.run(this.api.addDocument(this.subject, this.franchiseId, request), 'franchiseOnboarding.messages.documentAdded',
      (held) => (this.documentServer = held), this.documentForm);
  }

  /** The inline messages of the KYC document form. */
  get documentIssues(): FieldIssues {
    return this.cached('document', [this.documentForm, this.documentTried, this.documentServer],
      () => shownIssues(this.documentChecks(), this.documentTried, this.documentServer, this.documentForm));
  }

  private documentChecks(): FieldIssues {
    const { type, number, file } = this.documentForm;
    return documentIssues(type, number, file?.id ?? '', KYC_DOCUMENT_TYPES);
  }

  withdraw(document: KycDocument): void {
    if (!this.can.withdrawDocument || document.status === 'VERIFIED') return;
    this.dialogs.confirm({
      title: this.translate.instant('franchiseOnboarding.withdraw.title'),
      message: this.translate.instant('franchiseOnboarding.withdraw.message', { name: document.fileName }),
      confirmText: this.translate.instant('franchiseOnboarding.withdraw.confirm'),
      cancelText: this.translate.instant('franchiseOnboarding.cancel'),
      variant: 'danger',
    }).subscribe((ok) => {
      if (ok) this.run(this.api.withdrawDocument(this.franchiseId, document.id), 'franchiseOnboarding.messages.documentWithdrawn');
    });
  }

  reviewDocument(document: KycDocument, decision: ReviewDecision): void {
    if (!this.can.reviewDocument) return;
    this.decide(decision).subscribe((reason) => {
      if (reason === null) return;
      this.run(this.api.reviewDocument(this.franchiseId, document.id, { decision, reason: reason || null }), 'franchiseOnboarding.messages.reviewed');
    });
  }

  // ── bank account ─────────────────────────────────────────────────────────

  get routingType(): string {
    return routingRuleFor(this.bankForm.country).type;
  }

  get ibanOnly(): boolean {
    return ibanRequired(this.bankForm.country);
  }

  /** The IBAN field is offered only where the bank's country uses IBANs. */
  get ibanOffered(): boolean {
    return ibanAccepted(this.bankForm.country);
  }

  get accountNumberRequired(): boolean {
    return accountNumberRequired(this.bankForm);
  }

  get routingRequired(): boolean {
    return routingRequired(routingRuleFor(this.bankForm.country), this.ibanOffered && !!compactCode(this.bankForm.iban));
  }

  /** The inline messages of the bank account form. */
  get bankIssues(): FieldIssues {
    return this.cached('bank', [this.bankForm, this.bankTried, this.bankServer],
      () => shownIssues(bankAccountIssues(this.bankForm), this.bankTried, this.bankServer, this.bankForm));
  }

  editBank(): void {
    this.bankEditing = true;
  }

  onBankProofUploaded(file: HqDocument): void {
    this.bankForm = { ...this.bankForm, proofDmsDocumentId: file.id, proofFileName: file.originalFileName };
  }

  saveBank(): void {
    if (!this.can.saveBank) return;
    this.bankTried = true;
    this.bankServer = null;
    if (hasIssues(bankAccountIssues(this.bankForm))) {
      this.notify.error(this.translate.instant('franchiseOnboarding.validation.fixErrors'));
      return;
    }
    this.run(this.api.saveBankAccount(this.subject, this.franchiseId, normalizedBankRequest(this.bankForm)), 'franchiseOnboarding.messages.bankSaved',
      (held) => (this.bankServer = held), this.bankForm);
  }

  cancelBank(): void {
    this.resetBank();
  }

  reviewBank(decision: ReviewDecision): void {
    if (!this.can.reviewBank || !this.data?.bankAccount) return;
    this.decide(decision).subscribe((reason) => {
      if (reason === null) return;
      this.run(this.api.reviewBankAccount(this.franchiseId, { decision, reason: reason || null }), 'franchiseOnboarding.messages.reviewed');
    });
  }

  // ── agreement ────────────────────────────────────────────────────────────

  editAgreement(): void {
    this.agreementEditing = true;
  }

  onSignedCopyUploaded(file: HqDocument): void {
    this.agreementForm = { ...this.agreementForm, signedCopyDmsDocumentId: file.id, signedCopyFileName: file.originalFileName };
  }

  saveAgreement(): void {
    if (!this.can.saveAgreement) return;
    this.agreementTried = true;
    this.agreementServer = null;
    if (hasIssues(agreementIssues(this.agreementForm, todayIso(), AGREEMENT_RENEWALS))) {
      this.notify.error(this.translate.instant('franchiseOnboarding.validation.fixErrors'));
      return;
    }
    this.run(this.api.saveAgreement(this.franchiseId, normalizedAgreementRequest(this.agreementForm)), 'franchiseOnboarding.messages.agreementSaved',
      (held) => (this.agreementServer = held), this.agreementForm);
  }

  /** The inline messages of the agreement form. */
  get agreementIssues(): FieldIssues {
    const today = todayIso();
    return this.cached('agreement', [this.agreementForm, this.agreementTried, this.agreementServer, today],
      () => shownIssues(agreementIssues(this.agreementForm, today, AGREEMENT_RENEWALS), this.agreementTried, this.agreementServer, this.agreementForm));
  }

  /** The translated text of an inline message, or null when there is none. */
  message(issue: FieldIssue | undefined): string | null {
    return issue ? this.translate.instant(issue.key, issue.params) : null;
  }

  cancelAgreement(): void {
    this.resetAgreement();
  }

  // ── files and navigation ─────────────────────────────────────────────────

  /** Shows a stored file (KYC document, bank proof, signed agreement) in the preview dialog. */
  viewFile(dmsDocumentId: string, fileName: string): void {
    this.documents.getMetadata(dmsDocumentId).pipe(switchMap((doc) => this.documents.fetchFile(doc))).subscribe({
      next: (blob) => this.dialog.open(HqOpenFile, { data: { blob, fileName }, width: '900px', maxWidth: '96vw' }),
      error: () => this.notify.error(this.translate.instant('franchiseOnboarding.messages.fileFailed')),
    });
  }

  back(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, this.config.backRoute]);
  }

  // ── internals ────────────────────────────────────────────────────────────

  /** The same messages object while a form's inputs are unchanged, so change detection sees a stable value. */
  private cached(slot: string, inputs: unknown[], compute: () => FieldIssues): FieldIssues {
    const key = JSON.stringify(inputs);
    const hit = this.issueCache.get(slot);
    if (hit && hit.key === key) return hit.issues;
    const issues = compute();
    this.issueCache.set(slot, { key, issues });
    return issues;
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

  /**
   * Sends a change and shows the result. When the service refuses a rule it names the field at fault: `onFieldError` keeps that
   * error (with the field's value in `form` at that moment) so the message also shows under the field until it is changed.
   */
  private run(call: Observable<FranchiseOnboarding>, successKey: string, onFieldError?: (held: HeldServerError) => void, form?: object): void {
    if (this.busy) return;
    this.busy = true;
    call.subscribe({
      next: (data) => {
        this.busy = false;
        this.show(data);
        this.notify.success(this.translate.instant(successKey));
      },
      error: (err: unknown) => {
        this.busy = false;
        const fieldError = serverFieldError(err);
        if (fieldError && onFieldError && form) onFieldError({ error: fieldError, snapshot: fieldSnapshot(form, fieldError.field) });
        this.notify.error(onboardingError(err, this.translate, 'franchiseOnboarding.messages.failed'));
        this.cdr.markForCheck();
      },
    });
  }

  private show(data: FranchiseOnboarding): void {
    this.data = data;
    this.isLoading = false;
    this.documentForm = { type: KYC_DOCUMENT_TYPES[0], number: '', file: null };
    this.documentTried = false;
    this.documentServer = null;
    this.resetBank();
    this.resetAgreement();
    this.onboardingChanged.emit(data);
    this.cdr.markForCheck();
  }

  private resetBank(): void {
    const account = this.data?.bankAccount;
    this.bankForm = account ? bankRequestOf(account) : emptyBankAccount(this.defaultCountry, this.defaultCurrency);
    // The owner sees a masked number; sending it back would replace the real one, so they type it again when editing.
    if (account && this.subject === 'myFranchise') this.bankForm = { ...this.bankForm, accountNumber: null, iban: null };
    this.bankEditing = !account;
    this.bankTried = false;
    this.bankServer = null;
  }

  private resetAgreement(): void {
    const agreement = this.data?.agreement;
    this.agreementForm = agreement ? agreementRequestOf(agreement, this.defaultCurrency) : emptyAgreement(this.defaultCurrency);
    this.agreementEditing = !agreement;
    this.agreementTried = false;
    this.agreementServer = null;
  }
}
