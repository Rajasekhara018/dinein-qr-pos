import { ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqLegalProfile, HqPrivilegeAccessService, HqStatusTone } from 'hq-shared-lib';
import { Observable, Subscription, of } from 'rxjs';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { ONBOARDING_STATUS_TONE, OnboardingSubject } from '../franchise-onboarding/franchise-onboarding.constants';
import { FranchiseOnboarding, OnboardingStatus } from '../franchise-onboarding/franchise-onboarding.models';
import { FranchiseOnboardingPage } from '../franchise-onboarding/franchise-onboarding-page';
import { Franchise } from '../franchises/franchise.models';
import { FRANCHISE_STATUS_LABEL_KEYS } from '../franchises/franchises.constants';
import { OrganizationSetupStepForm } from '../organization-setup/organization-setup.constants';
import {
  OrganizationSetupStepStatus, legalProfileComplete, neighbourStep, resolveStep, setupProgressPercent, stepStatus, visibleSteps,
} from '../organization-setup/organization-setup.helpers';
import {
  FRANCHISE_ONBOARDING_SECTIONS, FRANCHISE_SETUP_BACK, FRANCHISE_SETUP_I18N, FRANCHISE_SETUP_ROUTE, FRANCHISE_SETUP_STEPS,
  FranchiseOnboardingSection, FranchiseSetupStep, FranchiseSetupStepId, OPEN_FRANCHISE_TITLE_KEY, SETUP_SHARED_I18N,
} from './franchise-setup.constants';
import { completedSteps, onboardingSectionOf } from './franchise-setup.helpers';
import { FRANCHISE_SETUP_GUIDES, FRANCHISE_SETUP_GUIDE_STORAGE_KEY } from './franchise-setup-guides.constants';
import { AdminFormGuide } from '../admin-form.types';

/**
 * One guided screen for a franchise: its applications and terms, legal and tax details, KYC documents, payout bank account,
 * agreement and activation as numbered steps, in place of the separate edit, legal-profile and onboarding pages. Every step keeps
 * its own save and endpoint (the step components are the ones those pages used). A step is shown only to someone holding its
 * main read privilege, and its change actions only to holders of the matching privileges. The onboarding steps share one read
 * model, read as soon as the screen opens, so the rail shows from the start which steps the franchise service counts as done.
 * Opened steps stay in place (hidden, not destroyed); leaving with unsaved edits asks first (shared pending-changes guard).
 * Opening a franchise (`franchises/new`, no id) is the same screen: its first step holds the owner and applications, and the
 * other steps stay locked until the franchise exists (opening it continues on its legal step).
 */
@Component({
  selector: 'app-franchise-setup-page',
  standalone: false,
  templateUrl: './franchise-setup.html',
  styleUrl: '../organization-setup/organization-setup.scss',
})
export class FranchiseSetupPage implements OnInit, OnDestroy, PendingChangesAware {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly dialogs = inject(AppDialogService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly subscription = new Subscription();

  /** The step pages with a form: all the shell needs from them is whether they hold unsaved edits and what is still missing. */
  @ViewChild('detailsStep') private details?: OrganizationSetupStepForm;
  @ViewChild('legalStep') private legal?: OrganizationSetupStepForm;
  /** The one onboarding page behind the KYC, bank, agreement and activation steps. */
  @ViewChild('onboardingStep') private onboarding?: FranchiseOnboardingPage;

  readonly i18n = FRANCHISE_SETUP_I18N;
  readonly shared = SETUP_SHARED_I18N;
  subject: OnboardingSubject = 'franchise';
  franchiseId = '';
  /** The franchise's code, from the list that opened the screen and then from what the steps read. */
  franchiseCode = '';
  franchiseStatus = '';
  onboardingStatus: OnboardingStatus | null = null;
  steps: FranchiseSetupStep[] = [];
  current: FranchiseSetupStepId | null = null;
  private readonly opened = new Set<FranchiseSetupStepId>();
  /** Steps the franchise service counts as done. */
  private complete = new Set<FranchiseSetupStepId>();
  /** The legal profile as the legal step last read or saved it (more recent than the onboarding checklist). */
  private legalDone: boolean | null = null;

  ngOnInit(): void {
    this.subject = (this.route.snapshot.data['subject'] as OnboardingSubject | undefined) ?? 'franchise';
    this.franchiseId = this.route.snapshot.paramMap.get('id') ?? '';
    this.franchiseCode = (history.state?.name as string | undefined) ?? '';
    if (!this.creating) this.complete.add('details');
    this.steps = visibleSteps(FRANCHISE_SETUP_STEPS[this.subject], (codes) => this.access.can(codes));
    this.subscription.add(this.route.paramMap.subscribe((params) => {
      this.current = resolveStep(params.get('step'), this.steps);
      if (this.current) this.opened.add(this.current);
      this.cdr.markForCheck();
    }));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  // ---- steps ------------------------------------------------------------------------------------

  get hasSteps(): boolean {
    return this.steps.length > 0;
  }

  get previous(): FranchiseSetupStepId | null {
    return neighbourStep(this.steps, this.current, -1);
  }

  get next(): FranchiseSetupStepId | null {
    return neighbourStep(this.steps, this.current, 1);
  }

  get currentStep(): FranchiseSetupStep | null {
    return this.steps.find((step) => step.id === this.current) ?? null;
  }

  /** Where the reader's choice to fold the step guides away is kept (one choice for every step). */
  readonly guideStorageKey = FRANCHISE_SETUP_GUIDE_STORAGE_KEY;

  /** The guide of the step on screen. */
  get stepGuide(): AdminFormGuide | null {
    return this.current ? FRANCHISE_SETUP_GUIDES[this.current] : null;
  }

  get currentNumber(): number {
    const step = this.currentStep;
    return step ? this.numberOf(step) : 0;
  }

  get percent(): number {
    return setupProgressPercent(this.steps.map((step) => this.statusOf(step)));
  }

  /** The onboarding section on screen, or null while a step outside the onboarding is showing. */
  get onboardingSection(): FranchiseOnboardingSection | null {
    return onboardingSectionOf(this.current);
  }

  /** The onboarding is read once for all its steps, as soon as the person may see any of them. */
  get hasOnboarding(): boolean {
    return !this.creating && this.steps.some((step) => (FRANCHISE_ONBOARDING_SECTIONS as readonly string[]).includes(step.id));
  }

  /** What the action bar says: the required fields of this step still open, or that edits wait for the step's own Save. */
  get hint(): string {
    if (this.creating) return this.translate.instant(`${FRANCHISE_SETUP_I18N}.openFirst`);
    const form = this.formOf(this.current);
    if (!form) return '';
    const missing = form.missingFields();
    if (missing.length) return this.translate.instant(`${SETUP_SHARED_I18N}.fillIn`, { fields: missing.join(', ') });
    return form.hasPendingChanges() ? this.translate.instant(`${SETUP_SHARED_I18N}.saveFirst`) : '';
  }

  private formOf(id: FranchiseSetupStepId | null): OrganizationSetupStepForm | undefined {
    if (id === 'details') return this.details;
    if (id === 'legal') return this.legal;
    return undefined;
  }

  numberOf(step: FranchiseSetupStep): number {
    return this.steps.indexOf(step) + 1;
  }

  isOpened(id: FranchiseSetupStepId): boolean {
    return this.opened.has(id) && this.steps.some((step) => step.id === id);
  }

  isUnsaved(id: FranchiseSetupStepId): boolean {
    return this.formOf(id)?.hasPendingChanges() ?? false;
  }

  isComplete(id: FranchiseSetupStepId): boolean {
    if (id === 'legal' && this.legalDone !== null) return this.legalDone;
    return this.complete.has(id);
  }

  statusOf(step: FranchiseSetupStep): OrganizationSetupStepStatus {
    // Done comes from the franchise service's own record, so a step counts whether or not it was opened here.
    return stepStatus({ unsaved: this.isUnsaved(step.id), visited: true, complete: this.isComplete(step.id) });
  }

  statusLabel(step: FranchiseSetupStep): string {
    return this.translate.instant(`${SETUP_SHARED_I18N}.status.${this.statusOf(step)}`);
  }

  /** The franchise's status chip: its translated label, or the code when it has none. */
  get franchiseStatusLabel(): string {
    const key = FRANCHISE_STATUS_LABEL_KEYS[this.franchiseStatus];
    return key ? this.translate.instant(key) : this.franchiseStatus;
  }

  onboardingTone(status: OnboardingStatus): HqStatusTone {
    return ONBOARDING_STATUS_TONE[status] ?? 'neutral';
  }

  /** True while a franchise is being opened: there is no franchise yet (route `franchises/new`). */
  get creating(): boolean {
    return !this.franchiseId;
  }

  /** While opening a franchise, every step but the first waits for the franchise to exist. */
  isLocked(id: FranchiseSetupStepId): boolean {
    return this.creating && id !== 'details';
  }

  /** The screen's title: opening a franchise, or setting one up. */
  get titleKey(): string {
    return this.creating ? OPEN_FRANCHISE_TITLE_KEY : `${FRANCHISE_SETUP_I18N}.title`;
  }

  go(id: FranchiseSetupStepId | null): void {
    if (!id || id === this.current || this.isLocked(id)) return;
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_SETUP_BACK[this.subject].route, this.franchiseId, FRANCHISE_SETUP_ROUTE, id], {
      state: { name: this.franchiseCode },
    });
  }

  get listKey(): string {
    return FRANCHISE_SETUP_BACK[this.subject].listKey;
  }

  /** The last step's button, Cancel and the back arrow: back to the list that opened the setup. */
  finish(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_SETUP_BACK[this.subject].route]);
  }

  back(): void {
    this.finish();
  }

  // ---- what the steps report --------------------------------------------------------------------

  onFranchise(franchise: Franchise): void {
    if (franchise.code) this.franchiseCode = franchise.code;
    this.franchiseStatus = franchise.status ?? '';
    this.cdr.markForCheck();
  }

  onLegal(profile: HqLegalProfile): void {
    this.legalDone = legalProfileComplete(profile);
    this.cdr.markForCheck();
  }

  /** The legal details were saved: the onboarding checklist (and so activation) is read again to take them in. */
  onLegalSaved(profile: HqLegalProfile): void {
    this.onLegal(profile);
    this.onboarding?.load();
  }

  onOnboarding(onboarding: FranchiseOnboarding): void {
    this.complete = completedSteps(onboarding);
    this.onboardingStatus = onboarding.onboardingStatus;
    if (onboarding.code) this.franchiseCode = onboarding.code;
    this.cdr.markForCheck();
  }

  // ---- leaving with unsaved edits ---------------------------------------------------------------

  hasPendingChanges(): boolean {
    return this.steps.some((step) => this.isUnsaved(step.id));
  }

  confirmDiscardChanges(): Observable<boolean> {
    if (!this.hasPendingChanges()) return of(true);
    const entity = this.translate.instant('accessManagement.franchises.entity').toLowerCase();
    return this.dialogs.confirm({
      title: this.translate.instant('shared.adminCrud.discard.title', { entity }),
      message: this.translate.instant('shared.adminCrud.discard.message', { entity }),
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
      variant: 'danger',
    });
  }
}
