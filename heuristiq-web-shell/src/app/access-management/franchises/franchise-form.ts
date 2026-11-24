import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService, hqAmountCode, hqAmountValue } from 'hq-shared-lib';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { Application } from '../../shared/models/uam.models';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { FRANCHISE_SETUP_ROUTE } from '../franchise-setup/franchise-setup.constants';
import { FranchiseApi } from './franchise-api';
import { DEFAULT_LOCATION_COUNTRY, LOCATION_PRIVILEGES } from './franchise-locations.constants';
import { NAMED_PLAN_RULES_APPLY_KEY, PACKAGE_PRIVILEGES } from './franchise-packages.constants';
import { emptyReferralForm, pickedPlanName } from './franchise-packages.helpers';
import { PackageTemplate } from './franchise-packages.models';
import { GST_LABEL_KEYS } from './franchise-packages.constants';
import { PARTY_LABEL_KEYS } from '../wallet-statement/wallet-statement.constants';
import {
  RULE_IN_FORCE_KINDS, RULE_PREVIEW_KIND, RULE_RESOLVE_PRIVILEGE, RULES_IN_FORCE_I18N, RuleInForceKind,
} from './franchise-rules-in-force.constants';
import { FranchisePlanPreviewService } from './franchise-plan-preview.service';
import { PlanPreview } from './franchise-plan-preview.models';
import { commissionByEvent, ruleInForce, rulesByEvent } from './franchise-rules-in-force.helpers';
import { RuleInForce, RuleInForceLine, RulesInForce } from './franchise-rules-in-force.models';
import { PlanSummaryLabels, eventTypeLabel } from './franchise-named-plans.helpers';
import { LOCATION_TYPE_LABEL_KEYS } from './franchise-locations.constants';
import { Franchise, FranchiseApplication, FranchisePlan, FranchiseStatus, Location } from './franchise.models';
import {
  APPLICATION_OPTIONS_LIMIT, FALLBACK_CURRENCY, FRANCHISE_PACKAGE_I18N, FRANCHISES_ROUTE, FRANCHISE_PRIVILEGES, FRANCHISE_STATUS_ACTION_KEYS, FRANCHISE_STATUS_LABEL_KEYS,
  SIGN_IN_AGAIN_NOTICE_MS,
} from './franchises.constants';
import {
  OpenFieldErrors, OpenFranchiseForm, TermsRow, applicationCodeForLabel, applicationPickLabel, applyPlan, applyResolution, applyTemplate, countryFromTimeZone,
  currencySymbol, emptyOpenForm, emptyTermsRow, formatMoney, isLocationBased, moneyText, nextStatuses, noPlanNote, offerCountries, openFieldErrors, openFranchiseError,
  placeNote, placeRequiredError, planKey, planRegion, planPlaceType, plansForApplication, roundFee, rowFeeInvalid, rowLimitInvalid, termsExtrasError, toOpenRequest,
  toTerms, usagePercent,
} from './franchises.helpers';

/**
 * The page that opens a franchise (no id: the owner, then each application with its fee and agent places) or manages one (an id: change
 * the terms of its applications and add more). Opening has its own route; managing is the first step of the franchise setup, where
 * the form is embedded (no heading). Leaving with unsaved changes asks first.
 */
@Component({
  selector: 'app-franchise-form',
  standalone: false,
  templateUrl: './franchise-form.html',
  styleUrls: ['./franchise-form-page.scss', './franchises-list.scss'],
})
export class FranchiseForm implements OnInit, PendingChangesAware {
  readonly privileges = FRANCHISE_PRIVILEGES;
  readonly packagePrivileges = PACKAGE_PRIVILEGES;
  readonly usagePercent = usagePercent;
  readonly planKey = planKey;

  /** Inside the franchise setup stepper: no heading or back link (the stepper has them). */
  @Input() embedded = false;
  /** The franchise was read or changed (terms, applications, status): the stepper shows its code and status. */
  @Output() readonly franchiseLoaded = new EventEmitter<Franchise>();

  /** Active package templates per application code, loaded when an application is chosen. */
  templatesByApplication: Record<string, PackageTemplate[]> = {};
  /** The preconfigured offers (per application, country and state) and the currency used when an offer names none. */
  plans: FranchisePlan[] = [];
  defaultCurrency = FALLBACK_CURRENCY;
  /** The country of the signed-in person's time zone, when it is one the offers cover: their offers are listed first. */
  userCountry: string | null = null;
  applications: Application[] = [];

  /** The franchise being managed; null while opening a new one. */
  franchise: Franchise | null = null;
  form: OpenFranchiseForm = emptyOpenForm();
  /** The editable copy of the terms of each application of the managed franchise, and the application being added to it. */
  edits: Record<string, TermsRow> = {};
  newApplication: TermsRow = emptyTermsRow();
  columns: HqCommonTableColumn<FranchiseApplication>[] = [];
  readonly ruleKinds = RULE_IN_FORCE_KINDS;
  readonly rulesI18n = RULES_IN_FORCE_I18N;
  readonly rulePrivilege = RULE_RESOLVE_PRIVILEGE;
  /**
   * The commission and referral in force for each application of the managed franchise (by application code): the plans its package
   * linked or its own plans, else the organization's rules. Read only for a caller who may resolve rules.
   */
  rulesInForce: Record<string, RulesInForce> = {};
  /** The preview of each row of the open form (fee, agent places, commission and referral of its choice), while it is open. */
  private readonly previews = new Map<TermsRow, { open: boolean; loading: boolean; data: PlanPreview | null }>();

  isLoading = true;
  loadFailed = false;
  saving = false;
  changingStatus = false;
  private saved = false;
  private initial = '';

  constructor(
    private api: FranchiseApi,
    private uam: UamService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
    private router: Router,
    private route: ActivatedRoute,
    private dialogs: AppDialogService,
    private planPreview: FranchisePlanPreviewService,
  ) {}

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`accessManagement.franchises.${key}`);
    this.columns = [
      { key: 'applicationCode', label: heading('application') },
      { key: 'locationName', label: heading('place') },
      { key: 'agentLimit', label: heading('agents') },
      { key: 'fee', label: heading('fee') },
    ];
    this.columns.push({ key: 'package', label: this.translate.instant(`${FRANCHISE_PACKAGE_I18N}.column`) });
    if (this.canSeeRules) {
      this.columns.push({ key: 'rules', label: this.translate.instant(`${RULES_IN_FORCE_I18N}.column`) });
    }
    // The offers only help fill the form in: without them (or without permission to list them) terms are typed by hand.
    this.api.plans().subscribe({
      next: (offers) => {
        this.plans = offers?.plans ?? [];
        this.defaultCurrency = offers?.defaultCurrency || FALLBACK_CURRENCY;
        this.userCountry = countryFromTimeZone(Intl.DateTimeFormat().resolvedOptions().timeZone, offerCountries(this.plans));
        this.cdr.detectChanges();
      },
      error: () => { this.plans = []; },
    });
    this.uam.list<Application>('/applications', 0, APPLICATION_OPTIONS_LIMIT).subscribe({
      next: (page) => { this.applications = page.content ?? []; this.cdr.detectChanges(); },
      error: () => this.notify.error(this.translate.instant('accessManagement.franchises.errors.applicationsUnavailable')),
    });
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.settle();
      return;
    }
    // The list hands the franchise over; opened directly, it is found among the organization's franchises.
    const given = (history.state as { franchise?: Franchise } | null)?.franchise;
    if (given && given.unitId === id) {
      this.manage(given);
      return;
    }
    this.api.list().subscribe({
      next: (rows) => {
        const found = (rows ?? []).find((row) => row.unitId === id);
        if (found) {
          this.manage(found);
        } else {
          this.fail(null);
        }
        this.cdr.detectChanges();
      },
      error: (err: unknown) => { this.fail(err); this.cdr.detectChanges(); },
    });
  }

  get isNew(): boolean {
    return this.route.snapshot.paramMap.get('id') === null;
  }

  get titleKey(): string {
    return this.isNew ? 'accessManagement.franchises.openTitle' : 'accessManagement.franchises.editTitle';
  }

  private manage(franchise: Franchise): void {
    this.franchise = franchise;
    this.newApplication = emptyTermsRow();
    this.edits = {};
    this.franchiseLoaded.emit(franchise);
    this.loadRulesInForce(franchise);
    franchise.applications.forEach(({ applicationCode }) => this.loadTemplates(applicationCode));
    this.packageEdits = Object.fromEntries(franchise.applications.map((application) => [application.applicationCode, application.packageTemplateId ?? '']));
    franchise.applications.forEach((application) => {
      this.edits[application.applicationCode] = {
        applicationCode: application.applicationCode, agentLimit: application.agentLimit, fee: hqAmountValue(application.fee),
        currency: hqAmountCode(application.fee) || null, planKey: '',
      };
    });
    this.settle();
  }

  /** The caller may see which commission and referral apply (the rule resolver's privilege). */
  get canSeeRules(): boolean {
    return this.access.can(RULE_RESOLVE_PRIVILEGE);
  }

  /** Reads the commission and referral in force for every application of the franchise; a failed read shows as such, per rule. */
  private loadRulesInForce(franchise: Franchise): void {
    if (!this.canSeeRules || !franchise.applications.length) return;
    const labels = { gst: (code: string) => this.labelOf(GST_LABEL_KEYS, code), party: (code: string) => this.labelOf(PARTY_LABEL_KEYS, code) };
    const one = (kind: RuleInForceKind, applicationCode: string): Observable<RuleInForce | null | 'failed'> =>
      this.api.ruleInForce(kind, franchise.unitId, applicationCode).pipe(
        map((resolution) => ruleInForce(resolution, labels)),
        // No rule for every event is in force: the commission of each event type is what applies, so it is listed instead.
        switchMap((rule) => (rule ? of(rule) : this.byEvent(kind, franchise.unitId, applicationCode, labels))),
        catchError(() => of('failed' as const)),
      );
    franchise.applications.forEach(({ applicationCode }) => {
      forkJoin({ COMMISSION_PLAN: one('COMMISSION_PLAN', applicationCode), REFERRAL_PLAN: one('REFERRAL_PLAN', applicationCode) })
        .subscribe((rules) => {
          if (this.franchise?.unitId !== franchise.unitId) return;
          this.rulesInForce = { ...this.rulesInForce, [applicationCode]: rules };
          this.cdr.detectChanges();
        });
    });
  }

  /**
   * What applies when no rule for every event is in force: for a commission, the plan in force for this franchise and each event
   * type of the application (its own, its package's or the organization's); otherwise, or without the event type catalogue, the
   * application's rules by event.
   */
  private byEvent(kind: RuleInForceKind, franchiseId: string, applicationCode: string, labels: PlanSummaryLabels): Observable<RuleInForce | null> {
    const rulesByEventOf = () => this.planPreview.rules(RULE_PREVIEW_KIND[kind], applicationCode).pipe(map(rulesByEvent));
    if (kind !== 'COMMISSION_PLAN' || !this.access.can(this.privileges.listEventTypes)) return rulesByEventOf();
    return this.api.eventTypes(applicationCode).pipe(
      switchMap((events) => {
        if (!events.length) return rulesByEventOf();
        return forkJoin(events.map((event) => this.api.ruleInForce(kind, franchiseId, applicationCode, event.code).pipe(
          map((resolution) => ruleInForce(resolution, labels)),
          catchError(() => of(null)),
          map((rule): RuleInForceLine | null => (rule
            ? { eventType: eventTypeLabel(event.code), summary: rule.summary, sourceKey: rule.sourceKey, sourceParams: rule.sourceParams }
            : null)),
        ))).pipe(map((lines) => commissionByEvent(lines.filter((line): line is RuleInForceLine => !!line))));
      }),
    );
  }

  // ── The package of each application of the managed franchise ─────────────

  /** The package chosen in each application's picker (by application code; '' for none); set from the franchise as it is read. */
  packageEdits: Record<string, string> = {};

  /** The caller may switch an application to another package (FRANCHISE_APPLICATION_PACKAGE_UPDATE). */
  get canChangePackage(): boolean {
    return this.access.can(this.privileges.changePackage);
  }

  /** The package an application follows now ('' for none). */
  currentPackage(application: FranchiseApplication): string {
    return application.packageTemplateId ?? '';
  }

  /** The name of a package of the application, or its id when it is not among the ones listed (switched off, say). */
  packageName(applicationCode: string, templateId: string): string {
    if (!templateId) return this.translate.instant(`${FRANCHISE_PACKAGE_I18N}.none`);
    return this.packagesOf(applicationCode).find((template) => template.id === templateId)?.name ?? templateId;
  }

  /** The active packages of an application, for its picker. */
  packagesOf(applicationCode: string): PackageTemplate[] {
    return this.templatesByApplication[applicationCode] ?? [];
  }

  /** True when the package is among the active ones listed for the application. */
  hasTemplate(applicationCode: string, templateId: string): boolean {
    return this.packagesOf(applicationCode).some((template) => template.id === templateId);
  }

  /** The package picked for an application: '' (none) or a template id. */
  pickPackage(application: FranchiseApplication, templateId: string): void {
    this.packageEdits[application.applicationCode] = templateId ?? '';
  }

  /** The package picked for an application ('' for none; the picker may report none as null). */
  pickedPackage(application: FranchiseApplication): string {
    return application.applicationCode in this.packageEdits
      ? this.packageEdits[application.applicationCode] || '' : this.currentPackage(application);
  }

  packageChanged(application: FranchiseApplication): boolean {
    return this.pickedPackage(application) !== this.currentPackage(application);
  }

  /** Asks first, then switches the application to the picked package; its plans apply from now on. */
  applyPackage(application: FranchiseApplication): void {
    const franchise = this.franchise;
    if (!franchise || !this.packageChanged(application) || this.saving) return;
    const templateId = this.pickedPackage(application);
    this.dialogs.confirm({
      title: this.translate.instant(`${FRANCHISE_PACKAGE_I18N}.confirmTitle`),
      message: this.translate.instant(`${FRANCHISE_PACKAGE_I18N}.confirmMessage`, {
        application: this.nameOf(application.applicationCode), name: this.packageName(application.applicationCode, templateId),
      }),
      confirmText: this.translate.instant(`${FRANCHISE_PACKAGE_I18N}.apply`),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.saving = true;
      this.api.changePackage(franchise.unitId, application.applicationCode, templateId || null).subscribe({
        next: (updated) => this.afterChange(updated, `${FRANCHISE_PACKAGE_I18N}.done`),
        error: (err) => this.failed(err, `${FRANCHISE_PACKAGE_I18N}.failed`),
      });
    });
  }

  // ── Preview of an opening row ─────────────────────────────────────────────

  previewOf(row: TermsRow): { open: boolean; loading: boolean; data: PlanPreview | null } | undefined {
    return this.previews.get(row);
  }

  /** Opens (reading what the row's application and package give) or closes the preview of an opening row. */
  togglePreview(row: TermsRow): void {
    const shown = this.previews.get(row);
    if (shown?.open) {
      this.previews.set(row, { ...shown, open: false });
      return;
    }
    this.refreshPreview(row, true);
  }

  /** Reads the preview again after the row's application or package changed, when it is open. */
  private refreshPreview(row: TermsRow, open = this.previews.get(row)?.open ?? false): void {
    if (!open) return;
    if (!row.applicationCode) {
      this.previews.set(row, { open: true, loading: false, data: null });
      return;
    }
    this.previews.set(row, { open: true, loading: true, data: null });
    this.planPreview.preview(row.applicationCode, this.chosenTemplate(row) ?? null).subscribe((data) => {
      if (this.previews.get(row)?.open) this.previews.set(row, { open: true, loading: false, data });
      this.cdr.detectChanges();
    });
  }

  /** The row's fee as text for the preview ('' while none is set). */
  previewFee(row: TermsRow): string {
    return row.fee === null || row.fee === undefined ? '' : this.money(row.fee, this.currencyOf(row));
  }

  /** The rule of one kind in force for an application: a rule, null (none in force), 'failed', or undefined while it is read. */
  ruleOf(applicationCode: string, kind: RuleInForceKind): RuleInForce | null | 'failed' | undefined {
    return this.rulesInForce[applicationCode]?.[kind];
  }

  private labelOf(keys: Readonly<Record<string, string>>, code: string): string {
    const key = keys[code];
    return key ? this.translate.instant(key) : code;
  }

  private settle(): void {
    this.isLoading = false;
    this.initial = this.snapshot();
  }

  private fail(err: unknown): void {
    this.isLoading = false;
    this.loadFailed = true;
    this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchises.errorMessage')));
  }

  // ── Suspending, reactivating and closing (administrators) ────────────────

  /** The statuses the franchise can be moved to from where it is. */
  statusTargets(franchise: Franchise): FranchiseStatus[] {
    return nextStatuses(franchise.status);
  }

  statusLabel(code: string): string {
    const key = FRANCHISE_STATUS_LABEL_KEYS[code];
    return key ? this.translate.instant(key) : code;
  }

  statusActionLabel(target: string): string {
    return this.translate.instant(`${FRANCHISE_STATUS_ACTION_KEYS[target]}.button`);
  }

  /** Asks first (closing says it is final), then moves the franchise; the page shows what the server answered. */
  changeStatus(target: FranchiseStatus): void {
    const franchise = this.franchise;
    if (!franchise || this.changingStatus) {
      return;
    }
    const prefix = FRANCHISE_STATUS_ACTION_KEYS[target];
    this.dialogs.confirm({
      title: this.translate.instant(`${prefix}.title`),
      message: this.translate.instant(`${prefix}.message`, { code: franchise.code }),
      confirmText: this.translate.instant(`${prefix}.button`),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
      variant: 'danger',
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.changingStatus = true;
      this.api.setStatus(franchise.unitId, target).subscribe({
        next: (updated) => {
          this.changingStatus = false;
          this.franchise = { ...franchise, ...updated };
          this.franchiseLoaded.emit(this.franchise);
          this.notify.success(this.translate.instant(`${prefix}.done`, { code: franchise.code }));
          this.cdr.detectChanges();
        },
        error: (err: { error?: { detail?: string; message?: string } }) => {
          this.changingStatus = false;
          this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchises.statusActions.failed')));
          this.cdr.detectChanges();
        },
      });
    });
  }

  // ── Opening a franchise ───────────────────────────────────────────────────

  addRow(): void {
    this.form.rows.push(emptyTermsRow());
  }

  removeRow(index: number): void {
    this.form.rows.splice(index, 1);
  }

  /** Applications not yet picked in another row of the form, so the same one cannot be chosen twice. */
  optionsFor(index: number): Application[] {
    const taken = new Set(this.form.rows.filter((_, i) => i !== index).map((row) => row.applicationCode));
    return this.applications.filter((application) => !taken.has(application.code));
  }

  // ── Offers, currency and fee formatting ───────────────────────────────────

  /** The offers of the row's application, the user's country first. */
  plansFor(row: TermsRow): FranchisePlan[] {
    return plansForApplication(this.plans, row.applicationCode, this.userCountry);
  }

  /** "India - Telangana (₹1,00,000.00, 20 agents)" for the region dropdown. */
  planLabel(plan: FranchisePlan): string {
    const type = planPlaceType(plan);
    const typeKey = type ? LOCATION_TYPE_LABEL_KEYS[type] : undefined;
    const place = planRegion(plan, this.translate.instant('accessManagement.franchises.regionGeneral'));
    // The place type tells apart offers for places that share a name (a district and the city of the same name).
    const region = typeKey ? `${place} · ${this.translate.instant(typeKey)}` : place;
    return this.translate.instant('accessManagement.franchises.planOption', {
      region, fee: moneyText(plan.fee, this.defaultCurrency), agents: plan.agentLimit,
    });
  }

  /**
   * The row's application was chosen: the offer stays on "custom" (no offer, no fee filled in). An offer is filled in only when the person
   * picks one, or when the chosen place has one, so a franchise is never opened with a fee nobody chose.
   */
  onApplicationChange(row: TermsRow): void {
    Object.assign(row, { planKey: '', currency: null, templateId: '' });
    this.loadTemplates(row.applicationCode);
    this.refreshPreview(row);
    // A place chosen before the application was: its plan for this application now applies.
    this.fillFromPlace(row);
  }

  // ── Place of the franchise ────────────────────────────────────────────────

  /**
   * The place pickers need a list of places: the franchise form's own (FRANCHISE_PLACE_OPTION_*, meant for everyone who opens or
   * changes franchises) or the Locations screen's. Without either the terms are typed by hand and no place is recorded.
   */
  get canPickPlaces(): boolean {
    return this.franchisePlaceOptions
      || (this.access.can(LOCATION_PRIVILEGES.list) && this.access.can(LOCATION_PRIVILEGES.children));
  }

  /** The caller may read the franchise form's own place list, so the pickers use it. */
  get franchisePlaceOptions(): boolean {
    return this.access.can(this.privileges.placeOptions) && this.access.can(this.privileges.placeOptionChildren);
  }

  /** The application is sold by place: its place is required (the server refuses it without one). */
  placeRequired(applicationCode: string): boolean {
    return isLocationBased(applicationCode);
  }

  /** The country the place pickers start from: the signed-in person's own, when the offers cover it. */
  get placeCountry(): string {
    return this.userCountry ?? DEFAULT_LOCATION_COUNTRY;
  }

  /** A place was chosen (or cleared) for a new application row: its plan fills the agent places and fee, which stay editable. */
  onPlacePicked(row: TermsRow, place: Location | null): void {
    row.locationId = place?.id ?? '';
    row.locationName = place?.name ?? '';
    row.placeNote = null;
    if (!place) {
      // The place set the offer (and the list is locked while one is chosen): clearing the place frees the choice again.
      row.planKey = '';
    }
    this.fillFromPlace(row);
  }

  /** A place was chosen for an application the franchise already has: only the place is recorded, its terms stay as they are. */
  onEditPlacePicked(row: TermsRow, place: Location | null): void {
    row.locationId = place?.id ?? '';
    row.locationName = place?.name ?? '';
  }

  /** Asks which plan applies to the row's place for its application and puts that plan's places and fee in the row. */
  private fillFromPlace(row: TermsRow): void {
    const wanted = row.locationId;
    if (!wanted || !row.applicationCode || !this.access.can(LOCATION_PRIVILEGES.resolvePlan)) {
      return;
    }
    this.api.resolvePlan(row.applicationCode, wanted).subscribe({
      next: (resolution) => {
        if (row.locationId === wanted) {
          Object.assign(row, applyResolution(row, resolution, this.plansFor(row)), { placeNote: placeNote(resolution) });
          this.cdr.detectChanges();
        }
      },
      // No plan covers this place (or it cannot be resolved): the terms are typed by hand and the place is still recorded.
      error: () => {
        if (row.locationId === wanted) {
          row.placeNote = noPlanNote();
          this.cdr.detectChanges();
        }
      },
    });
  }

  placeNoteText(row: TermsRow): string {
    return row.placeNote ? this.translate.instant(row.placeNote.key, row.placeNote.params) : '';
  }

  /** The hint under a row's place picker: what a place is for, that it is needed (when it is), and the picked place's note. */
  placeHints(row: TermsRow): string {
    return [
      this.translate.instant('accessManagement.franchises.placeHint'),
      this.placeRequired(row.applicationCode) ? this.translate.instant('accessManagement.franchises.placeRequiredHint') : '',
      this.placeNoteText(row),
    ].filter(Boolean).join(' ');
  }

  /** The active package templates of an application, for the picker (only for a caller who may list them). */
  loadTemplates(applicationCode: string): void {
    if (!applicationCode || this.templatesByApplication[applicationCode] || !this.access.can(this.packagePrivileges.list)) {
      return;
    }
    this.api.packageTemplates({ applicationCode, active: true, size: 100 }).subscribe({
      next: (page) => { this.templatesByApplication[applicationCode] = page?.items ?? []; this.cdr.detectChanges(); },
      error: () => { this.templatesByApplication[applicationCode] = []; },
    });
  }

  templatesFor(row: TermsRow): PackageTemplate[] {
    return this.templatesByApplication[row.applicationCode] ?? [];
  }

  /** A template was chosen (or none): its terms fill the row; explicit commission / referral stay the caller's to switch on. */
  onTemplateChange(row: TermsRow): void {
    const chosen = this.templatesFor(row).find((template) => template.id === row.templateId);
    Object.assign(row, applyTemplate(row, chosen));
    this.refreshPreview(row);
  }

  chosenTemplate(row: TermsRow): PackageTemplate | undefined {
    return this.templatesFor(row).find((template) => template.id === row.templateId);
  }

  /** The plans the chosen package links the franchise to, by name ("Rules that apply" for a kind it picked none of). */
  templatePlansText(row: TermsRow): string {
    const chosen = this.chosenTemplate(row);
    if (!chosen) { return ''; }
    const rules = this.translate.instant(NAMED_PLAN_RULES_APPLY_KEY);
    return this.translate.instant('accessManagement.franchisePackages.templatePlans', {
      commission: pickedPlanName(chosen, 'commission') || rules,
      referral: pickedPlanName(chosen, 'referral') || rules,
    });
  }

  onCustomReferralChange(row: TermsRow): void {
    if (row.customReferral) { row.referral = emptyReferralForm(); }
  }

  /** An offer was chosen (or "custom", the empty choice, which keeps whatever is typed). */
  onPlanChange(row: TermsRow): void {
    const chosen = this.plansFor(row).find((plan) => planKey(plan) === row.planKey);
    if (chosen) {
      Object.assign(row, applyPlan(row, chosen));
    }
  }

  /** The fee is shown with exactly two decimals once typing is done. */
  roundFee(row: TermsRow): void {
    row.fee = roundFee(row.fee);
  }

  currencyOf(row: TermsRow): string {
    return row.currency ?? this.defaultCurrency;
  }

  symbolOf(row: TermsRow): string {
    return currencySymbol(this.currencyOf(row));
  }

  money(amount: number | null, currency: string): string {
    return formatMoney(amount, currency);
  }

  /** The labels the application pickers list, for the applications in {@code list}. */
  applicationLabels(list: Application[]): string[] {
    return list.map(applicationPickLabel);
  }

  /** The label the picker shows for an application code ('' while none is chosen). */
  applicationLabelOf(code: string): string {
    const application = this.applications.find((a) => a.code === code);
    return application ? applicationPickLabel(application) : '';
  }

  /** An application was chosen (or cleared) in a row of the open form. */
  pickApplication(row: TermsRow, label: string): void {
    row.applicationCode = applicationCodeForLabel(this.applications, label);
    this.onApplicationChange(row);
  }

  /** An application was chosen (or cleared) in the add-an-application box of the managed franchise. */
  pickNewApplication(label: string): void {
    this.newApplication.applicationCode = applicationCodeForLabel(this.applications, label);
    this.loadTemplates(this.newApplication.applicationCode);
    this.refreshPreview(this.newApplication);
  }

  nameOf(code: string): string {
    return this.applications.find((application) => application.code === code)?.name ?? code;
  }

  /** @param phoneInvalid the owner's phone number was typed but is not a valid number (the phone field says so) */
  submitOpening(phoneInvalid = false): void {
    this.openErrors = {};
    if (phoneInvalid) {
      this.notify.error(this.translate.instant('accessManagement.franchises.errors.phone'));
      return;
    }
    const error = openFranchiseError(this.form);
    if (error) {
      this.notify.error(this.translate.instant(error));
      return;
    }
    this.saving = true;
    this.api.open(toOpenRequest(this.form, this.defaultCurrency)).subscribe({
      next: (franchise) => {
        this.saving = false;
        this.saved = true;
        this.notify.success(this.translate.instant('accessManagement.franchises.messages.opened', { code: franchise.code }));
        // The owner's franchise appears in their session only after they sign in again (the token carries it).
        this.notify.info(this.translate.instant('accessManagement.franchises.messages.signInAgain'), SIGN_IN_AGAIN_NOTICE_MS);
        if (franchise.activeInDirectOrganization) {
          this.notify.warning(this.translate.instant('accessManagement.franchises.messages.openedInDirect'), SIGN_IN_AGAIN_NOTICE_MS);
        }
        this.continueSetup(franchise);
      },
      error: (err) => {
        // A refusal that names an owner field (the phone of a new owner, say) is shown under that field as well as in the toast.
        this.openErrors = openFieldErrors(apiErrorMessage(err, ''));
        this.failed(err, 'accessManagement.franchises.messages.openFailed');
      },
    });
  }

  /** What the server said about each owner field on the last opening; cleared when that field is edited or the form is sent again. */
  openErrors: OpenFieldErrors = {};

  /** The owner field was edited: its refusal no longer applies. */
  clearOpenError(field: keyof OpenFieldErrors): void {
    if (this.openErrors[field]) {
      this.openErrors = { ...this.openErrors, [field]: undefined };
    }
  }

  // ── Managing the applications of a franchise ──────────────────────────────

  /** Registered applications this franchise does not have yet. */
  addable(): Application[] {
    const has = new Set((this.franchise?.applications ?? []).map((application) => application.applicationCode));
    return this.applications.filter((application) => !has.has(application.code));
  }

  saveTerms(application: FranchiseApplication): void {
    const franchise = this.franchise;
    const row = this.edits[application.applicationCode];
    if (!franchise || !row || row.agentLimit === null || row.agentLimit < 1 || row.fee === null || row.fee < 0) {
      this.notify.error(this.translate.instant('accessManagement.franchises.errors.terms'));
      return;
    }
    const missingPlace = placeRequiredError(row, application.locationId);
    if (missingPlace) {
      this.notify.error(this.translate.instant(missingPlace));
      return;
    }
    this.saving = true;
    this.api.updateApplication(franchise.unitId, toTerms(row, this.defaultCurrency)).subscribe({
      next: (updated) => this.afterChange(updated, 'accessManagement.franchises.messages.termsSaved'),
      error: (err) => this.failed(err, 'accessManagement.franchises.messages.termsFailed'),
    });
  }

  addApplication(): void {
    const franchise = this.franchise;
    const row = this.newApplication;
    if (!franchise || !row.applicationCode || rowLimitInvalid(row) || rowFeeInvalid(row)) {
      this.notify.error(this.translate.instant('accessManagement.franchises.errors.terms'));
      return;
    }
    const extras = termsExtrasError(row) ?? placeRequiredError(row);
    if (extras) {
      this.notify.error(this.translate.instant(extras));
      return;
    }
    this.saving = true;
    this.api.addApplication(franchise.unitId, toTerms(row, this.defaultCurrency)).subscribe({
      next: (updated) => this.afterChange(updated, 'accessManagement.franchises.messages.applicationAdded'),
      error: (err) => this.failed(err, 'accessManagement.franchises.messages.applicationFailed'),
    });
  }

  /** The service answers with the franchise as it is now: the page shows it, so the next application can be changed too. */
  private afterChange(updated: Franchise, messageKey: string): void {
    this.saving = false;
    this.notify.success(this.translate.instant(messageKey));
    this.manage(updated);
    this.cdr.detectChanges();
  }

  private failed(err: { error?: { detail?: string; message?: string } } | undefined, fallbackKey: string): void {
    this.saving = false;
    this.notify.error(apiErrorMessage(err, this.translate.instant(fallbackKey)));
    this.cdr.detectChanges();
  }

  cancel(): void {
    this.back();
  }

  private back(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISES_ROUTE]);
  }

  /** A franchise was opened: its setup continues with the legal details, KYC, bank account, agreement and activation. */
  private continueSetup(franchise: Franchise): void {
    if (!franchise.unitId) {
      this.back();
      return;
    }
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISES_ROUTE, franchise.unitId, FRANCHISE_SETUP_ROUTE, 'legal'], {
      state: { name: franchise.code },
    });
  }

  // ── leaving with unsaved changes ──────────────────────────────────────────

  /** The setup action bar lists required fields; the terms are checked on each row's own save, so there are none to list. */
  missingFields(): string[] {
    return [];
  }

  hasPendingChanges(): boolean {
    return !this.saved && !this.isLoading && this.snapshot() !== this.initial;
  }

  confirmDiscardChanges(): Observable<boolean> {
    if (!this.hasPendingChanges()) {
      return of(true);
    }
    const entity = this.translate.instant('accessManagement.franchises.entity').toLowerCase();
    return this.dialogs.confirm({
      title: this.translate.instant('shared.adminCrud.discard.title', { entity }),
      message: this.translate.instant('shared.adminCrud.discard.message', { entity }),
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
      variant: 'danger',
    });
  }

  private snapshot(): string {
    return JSON.stringify({ form: this.form, edits: this.edits, newApplication: this.newApplication, packageEdits: Object.entries(this.packageEdits).map(([code, id]) => [code, id || '']) });
  }
}
