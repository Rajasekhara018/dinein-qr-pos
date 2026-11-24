import { ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqAppDialogFormValues, HqLegalProfile, HqPrivilegeAccessService } from 'hq-shared-lib';
import { Observable, Subscription, of } from 'rxjs';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import {
  ORGANIZATION_SETUP_I18N, ORGANIZATION_SETUP_ROUTE, ORGANIZATION_SETUP_STEPS, OrganizationSetupStep, OrganizationSetupStepForm,
  OrganizationSetupStepId,
} from './organization-setup.constants';
import {
  OrganizationSetupStepStatus, legalProfileComplete, neighbourStep, resolveStep, setupProgressPercent, stepStatus, visibleSteps,
} from './organization-setup.helpers';

/**
 * One guided screen for an organization: its details, legal and tax details, operating model, applications and members as
 * numbered steps. Every step keeps its own save and its own endpoint (the step components are the same ones the separate pages
 * used), so a failed save in one step never touches another's edits. A step is shown only to someone who holds its main read
 * privilege and offers its change actions only to those who hold the matching privileges. A step that was opened stays in
 * place (hidden, not destroyed) so what was typed in it survives moving to another step; leaving the screen with unsaved edits
 * asks first, through the shared pending-changes guard.
 */
@Component({
  selector: 'app-organization-setup-page',
  standalone: false,
  templateUrl: './organization-setup.html',
  styleUrl: './organization-setup.scss',
})
export class OrganizationSetupPage implements OnInit, OnDestroy, PendingChangesAware {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly dialogs = inject(AppDialogService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly subscription = new Subscription();

  /** The step pages, found by template reference: all the shell needs from them is whether they hold unsaved edits. */
  @ViewChild('detailsStep') private details?: OrganizationSetupStepForm;
  @ViewChild('legalStep') private legal?: OrganizationSetupStepForm;

  readonly i18n = ORGANIZATION_SETUP_I18N;
  organizationId = '';
  organizationName = '';
  /** The organization's status as its details step read or saved it (a chip in the heading). */
  organizationStatus = '';
  /** The steps this person may open, in order. */
  steps: OrganizationSetupStep[] = [];
  current: OrganizationSetupStepId | null = null;
  /** Steps opened so far; each is created on its first visit and then kept. */
  private readonly opened = new Set<OrganizationSetupStepId>();
  /** Steps whose data was read and holds what the step needs. */
  private readonly complete = new Set<OrganizationSetupStepId>();

  ngOnInit(): void {
    this.organizationId = this.route.snapshot.paramMap.get('id') ?? '';
    this.organizationName = (history.state?.name as string | undefined) ?? '';
    this.steps = visibleSteps(ORGANIZATION_SETUP_STEPS, (codes) => this.access.can(codes));
    this.subscription.add(this.route.paramMap.subscribe((params) => {
      this.current = resolveStep(params.get('step'), this.steps);
      if (this.current) this.opened.add(this.current);
      // The steps with nothing to fill in (an action list) count as done once looked at.
      if (this.current && !['details', 'legal'].includes(this.current)) this.complete.add(this.current);
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

  get previous(): OrganizationSetupStepId | null {
    return neighbourStep(this.steps, this.current, -1);
  }

  get next(): OrganizationSetupStepId | null {
    return neighbourStep(this.steps, this.current, 1);
  }

  get currentStep(): OrganizationSetupStep | null {
    return this.steps.find((step) => step.id === this.current) ?? null;
  }

  /** The current step's number, from 1 ("Step 2 of 5"). */
  get currentNumber(): number {
    const step = this.currentStep;
    return step ? this.numberOf(step) : 0;
  }

  /** Share of the steps that are done, 0..100 (the rail's progress bar). */
  get percent(): number {
    return setupProgressPercent(this.steps.map((step) => this.statusOf(step)));
  }

  /**
   * What the action bar tells the person: the required fields of this step that are still open, or that edits wait for the
   * step's own Save; empty when there is nothing to say.
   */
  get hint(): string {
    const form = this.formOf(this.current);
    if (!form) return '';
    const missing = form.missingFields();
    if (missing.length) return this.translate.instant(`${ORGANIZATION_SETUP_I18N}.fillIn`, { fields: missing.join(', ') });
    return form.hasPendingChanges() ? this.translate.instant(`${ORGANIZATION_SETUP_I18N}.saveFirst`) : '';
  }

  private formOf(id: OrganizationSetupStepId | null): OrganizationSetupStepForm | undefined {
    if (id === 'details') return this.details;
    if (id === 'legal') return this.legal;
    return undefined;
  }

  /** The step's place in the order, from 1, for its number. */
  numberOf(step: OrganizationSetupStep): number {
    return this.steps.indexOf(step) + 1;
  }

  /** True once the step was opened (its component exists). */
  isOpened(id: OrganizationSetupStepId): boolean {
    return this.opened.has(id) && this.steps.some((step) => step.id === id);
  }

  /** The step holds edits that are not saved yet. */
  isUnsaved(id: OrganizationSetupStepId): boolean {
    if (id === 'details') return this.details?.hasPendingChanges() ?? false;
    if (id === 'legal') return this.legal?.hasPendingChanges() ?? false;
    return false;
  }

  statusOf(step: OrganizationSetupStep): OrganizationSetupStepStatus {
    return stepStatus({ unsaved: this.isUnsaved(step.id), visited: this.opened.has(step.id), complete: this.complete.has(step.id) });
  }

  statusLabel(step: OrganizationSetupStep): string {
    return this.translate.instant(`${ORGANIZATION_SETUP_I18N}.status.${this.statusOf(step)}`);
  }

  /** The rail's compact "go to step" list (narrow screens). */
  onJump(event: Event): void {
    const id = (event.target as HTMLSelectElement | null)?.value as OrganizationSetupStepId | undefined;
    if (id) this.go(id);
  }

  /** Opens a step; nothing is lost by leaving one, because opened steps stay in place. */
  go(id: OrganizationSetupStepId | null): void {
    if (!id || id === this.current) return;
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, 'organizations', this.organizationId, ORGANIZATION_SETUP_ROUTE, id], {
      state: { name: this.organizationName },
    });
  }

  /** The last step's button: back to the organizations list. */
  finish(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, 'organizations']);
  }

  back(): void {
    this.finish();
  }

  // ---- what the steps report --------------------------------------------------------------------

  /** The organization's details were read or saved: the name in the heading follows them. */
  onDetails(values: HqAppDialogFormValues, loaded: boolean): void {
    const name = String(values['name'] ?? '').trim();
    if (name) this.organizationName = name;
    const status = String(values['status'] ?? '').trim();
    if (status) this.organizationStatus = status;
    this.complete.add('details');
    if (!loaded) this.cdr.markForCheck();
  }

  onLegal(profile: HqLegalProfile): void {
    if (legalProfileComplete(profile)) this.complete.add('legal');
    else this.complete.delete('legal');
    this.cdr.markForCheck();
  }

  // ---- leaving with unsaved edits ---------------------------------------------------------------

  hasPendingChanges(): boolean {
    return this.steps.some((step) => this.isUnsaved(step.id));
  }

  confirmDiscardChanges(): Observable<boolean> {
    if (!this.hasPendingChanges()) return of(true);
    const entity = this.translate.instant('accessManagement.organizations.entityName').toLowerCase();
    return this.dialogs.confirm({
      title: this.translate.instant('shared.adminCrud.discard.title', { entity }),
      message: this.translate.instant('shared.adminCrud.discard.message', { entity }),
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
      variant: 'danger',
    });
  }
}
