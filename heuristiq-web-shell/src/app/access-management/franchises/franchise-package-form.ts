import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { Observable, Subject, catchError, forkJoin, map, of, switchMap, takeUntil } from 'rxjs';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { Application } from '../../shared/models/uam.models';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { PARTY_LABEL_KEYS } from '../wallet-statement/wallet-statement.constants';
import { labelKey } from './franchise-earnings.helpers';
import { FranchiseApi } from './franchise-api';
import { LOCATION_TYPES, LOCATION_TYPE_LABEL_KEYS } from './franchise-locations.constants';
import {
  appliedRuleRows, emptyPlanPicker, eventTypeLabel, hasPlanOption, isoDay, namedPlanOptions, planSummary, withPickedOption,
} from './franchise-named-plans.helpers';
import {
  GST_LABEL_KEYS, NAMED_PLAN_FOLLOW_RULES_KEY, NAMED_PLAN_HINT_KEYS, NAMED_PLAN_KINDS, NAMED_PLAN_LABEL_KEYS, NAMED_PLAN_LIST_PRIVILEGES,
  NAMED_PLAN_NO_ACCESS_KEY, NAMED_PLAN_SCREEN_LABEL_KEYS, NAMED_PLAN_SCREEN_PATHS, PACKAGE_PRIVILEGES,
} from './franchise-packages.constants';
import {
  PackageForm, emptyPackageForm, packageFormError, packageToForm, pickedPlanName, planField, toPackageRequest,
} from './franchise-packages.helpers';
import { AppliedRule, NamedPlanKind, NamedPlanLoad, NamedPlanPicker, PackageTemplate } from './franchise-packages.models';
import { APPLICATION_OPTIONS_LIMIT, FRANCHISE_PACKAGES_ROUTE } from './franchises.constants';

/** One plan load: the application whose plans are wanted, and whether picks it does not list are to be cleared. */
interface PlanRequest {
  applicationCode: string;
  clearUnlisted: boolean;
}

/**
 * The page that adds or changes a franchise package template, opened from the packages list (its own route, like the UAM forms).
 * A package picks a named fee, commission and referral plan of its application (or none: the rules that apply); a franchise opened
 * from it is linked to them. Saving returns to the list; leaving with unsaved changes asks first.
 */
@Component({
  selector: 'app-franchise-package-form',
  standalone: false,
  templateUrl: './franchise-package-form.html',
  styleUrl: './franchise-form-page.scss',
})
export class FranchisePackageForm implements OnInit, OnDestroy, PendingChangesAware {
  readonly privileges = PACKAGE_PRIVILEGES;
  readonly planKinds = NAMED_PLAN_KINDS;
  readonly planLabelKeys = NAMED_PLAN_LABEL_KEYS;
  readonly planHintKeys = NAMED_PLAN_HINT_KEYS;
  readonly planListPrivileges = NAMED_PLAN_LIST_PRIVILEGES;
  readonly planScreenPaths = NAMED_PLAN_SCREEN_PATHS;
  readonly planScreenLabelKeys = NAMED_PLAN_SCREEN_LABEL_KEYS;
  readonly followRulesKey = NAMED_PLAN_FOLLOW_RULES_KEY;
  readonly noAccessKey = NAMED_PLAN_NO_ACCESS_KEY;
  /** The types of place a package can be tied to (backend codes; shown through their translated labels). */
  readonly placeTypes = LOCATION_TYPES;

  applications: Application[] = [];
  form: PackageForm = emptyPackageForm();
  /** The options of each plan picker, for the chosen application. */
  pickers: Record<NamedPlanKind, NamedPlanPicker> = { fee: emptyPlanPicker(), commission: emptyPlanPicker(), referral: emptyPlanPicker() };
  /**
   * What a franchise opened from this package follows for each kind when no plan is picked: the application's plans in force today
   * (null while loading or when they cannot be read), so "Follow the rules that apply" shows what those rules are.
   */
  appliedRules: Record<NamedPlanKind, AppliedRule[] | null> = { fee: null, commission: null, referral: null };
  editingId: string | null = null;
  isLoading = true;
  loadFailed = false;
  saving = false;
  private saved = false;
  private initial = '';
  /** The names of the plans the opened template had picked, so a pick that is not listed still shows its name. */
  private pickedNames: Record<NamedPlanKind, string> = { fee: '', commission: '', referral: '' };
  private readonly planRequests$ = new Subject<PlanRequest>();
  private readonly destroy$ = new Subject<void>();

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
  ) {}

  ngOnInit(): void {
    // Only the answer for the latest application counts: a slower answer for an application left behind is dropped.
    this.planRequests$.pipe(
      switchMap((request) => this.loadPlans(request.applicationCode).pipe(map((loads) => ({ request, loads })))),
      takeUntil(this.destroy$),
    ).subscribe(({ request, loads }) => this.showPlans(request, loads));

    const id = this.route.snapshot.paramMap.get('id');
    this.editingId = id;
    this.uam.list<Application>('/applications', 0, APPLICATION_OPTIONS_LIMIT).subscribe({
      next: (page) => { this.applications = page.content ?? []; this.cdr.detectChanges(); },
      error: () => this.notify.error(this.translate.instant('accessManagement.franchises.errors.applicationsUnavailable')),
    });
    if (!id) {
      this.open(null);
      return;
    }
    // The fresh copy (with its version) is read when the caller may read one; otherwise the row the list handed over is used.
    const given = (history.state as { template?: PackageTemplate } | null)?.template;
    if (!this.access.can(this.privileges.read)) {
      if (given && given.id === id) {
        this.open(given);
      } else {
        this.fail(null);
      }
      return;
    }
    this.api.packageTemplate(id).subscribe({
      next: (fresh) => { this.open(fresh); this.cdr.detectChanges(); },
      error: (err: unknown) => { this.fail(err); this.cdr.detectChanges(); },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private open(template: PackageTemplate | null): void {
    this.form = template ? packageToForm(template) : emptyPackageForm();
    for (const kind of this.planKinds) {
      this.pickedNames[kind] = template ? pickedPlanName(template, kind) : '';
    }
    this.isLoading = false;
    this.initial = this.snapshot();
    this.requestPlans(false);
  }

  private fail(err: unknown): void {
    this.isLoading = false;
    this.loadFailed = true;
    this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchisePackages.messages.loadFailed')));
  }

  get titleKey(): string {
    return this.editingId ? 'accessManagement.franchisePackages.editTitle' : 'accessManagement.franchisePackages.newTitle';
  }

  // ── plan pickers ──────────────────────────────────────────────────────────

  /** Whether the caller may list the named plans of a kind; without it the picker is shown disabled. */
  canList(kind: NamedPlanKind): boolean {
    return this.access.can(this.planListPrivileges[kind]);
  }

  /** The code picked for a kind ('' = follow the rules that apply). */
  planCode(kind: NamedPlanKind): string {
    return this.form[planField(kind)];
  }

  pickPlan(kind: NamedPlanKind, code: string | null | undefined): void {
    this.form[planField(kind)] = code ?? '';
  }

  /** The application changed: its own plans are loaded and picks it does not list are cleared. */
  onApplicationChange(): void {
    this.requestPlans(true);
  }

  /** The translated name of a type of place (the code itself when it has no label). */
  placeTypeLabel(type: string): string {
    return this.label(LOCATION_TYPE_LABEL_KEYS, type);
  }

  /** A one-line summary of the picked plan's current terms ('' when nothing is picked or its terms are not known). */
  planSummaryText(kind: NamedPlanKind): string {
    const code = this.planCode(kind);
    if (!code) { return ''; }
    const option = this.pickers[kind].options.find((candidate) => candidate.code === code);
    return planSummary(kind, option?.current ?? null, {
      gst: (value) => this.label(GST_LABEL_KEYS, value),
      party: (value) => this.label(PARTY_LABEL_KEYS, value),
    });
  }

  private requestPlans(clearUnlisted: boolean): void {
    const applicationCode = this.form.applicationCode;
    for (const kind of this.planKinds) {
      this.pickers[kind] = { options: [], loading: !!applicationCode && this.canList(kind), loadFailed: false };
    }
    this.planRequests$.next({ applicationCode, clearUnlisted });
    this.loadAppliedRules(applicationCode);
  }

  /** The plans that apply without a named plan, for each kind the caller may list; an answer for an application left behind is dropped. */
  private loadAppliedRules(applicationCode: string): void {
    for (const kind of this.planKinds) {
      this.appliedRules[kind] = null;
      if (!applicationCode || !this.canList(kind)) { continue; }
      this.api.appliedRules(kind, applicationCode).pipe(catchError(() => of(null)), takeUntil(this.destroy$)).subscribe((rows) => {
        if (this.form.applicationCode !== applicationCode) { return; }
        this.appliedRules[kind] = rows === null ? null : appliedRuleRows(rows, isoDay(new Date())).map((row) => ({
          eventType: row.scope.eventType ? eventTypeLabel(row.scope.eventType) : '',
          placeType: row.scope.placeType ? this.placeTypeLabel(row.scope.placeType) : '',
          summary: planSummary(kind, row, {
            gst: (value) => this.label(GST_LABEL_KEYS, value),
            party: (value) => this.label(PARTY_LABEL_KEYS, value),
          }),
        }));
        this.cdr.detectChanges();
      });
    }
  }

  /** The plan rows of each kind for an application; a kind the caller may not list (or no application) is not asked for. */
  private loadPlans(applicationCode: string): Observable<Record<NamedPlanKind, NamedPlanLoad>> {
    const load = (kind: NamedPlanKind): Observable<NamedPlanLoad> => {
      if (!applicationCode || !this.canList(kind)) {
        return of({ rows: [], listed: false, failed: false });
      }
      return this.api.namedPlans(kind, applicationCode).pipe(
        map((rows) => ({ rows, listed: true, failed: false })),
        catchError(() => of({ rows: [], listed: true, failed: true })),
      );
    };
    return forkJoin({ fee: of<NamedPlanLoad>({ rows: [], listed: false, failed: false }), commission: load('commission'), referral: load('referral') });
  }

  private showPlans(request: PlanRequest, loads: Record<NamedPlanKind, NamedPlanLoad>): void {
    const today = isoDay(new Date());
    for (const kind of this.planKinds) {
      const load = loads[kind];
      const options = namedPlanOptions(load.rows, today);
      const field = planField(kind);
      // After a change of application a pick stays only when the new application lists it; when the list could not be
      // read the pick is kept and the service checks it on save.
      if (request.clearUnlisted && this.form[field] && !load.failed && !hasPlanOption(options, this.form[field])) {
        this.form[field] = '';
      }
      this.pickers[kind] = {
        options: withPickedOption(options, this.form[field], this.pickedNames[kind]), loading: false, loadFailed: load.failed,
      };
    }
    this.cdr.detectChanges();
  }

  /** The text for a code from a key map, or the code itself when the map has none. */
  private label(map: Readonly<Record<string, string>>, code: string | null | undefined): string {
    const key = labelKey(map, code);
    return key ? this.translate.instant(key) : code ?? '';
  }

  // ── saving ────────────────────────────────────────────────────────────────

  save(): void {
    const error = packageFormError(this.form);
    if (error) {
      this.notify.error(this.translate.instant(error));
      return;
    }
    const request = toPackageRequest(this.form);
    const editing = this.editingId;
    const call$ = editing ? this.api.changePackageTemplate(editing, request) : this.api.addPackageTemplate(request);
    this.saving = true;
    call$.subscribe({
      next: () => {
        this.notify.success(this.translate.instant(editing
          ? 'accessManagement.franchisePackages.messages.changed' : 'accessManagement.franchisePackages.messages.added'));
        this.saving = false;
        this.saved = true;
        this.back();
      },
      error: (err: unknown) => {
        this.saving = false;
        // Duplicate names, stale versions and unknown plans (or plans of another application) come from the service as given.
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchisePackages.messages.saveFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  cancel(): void {
    this.back();
  }

  private back(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_PACKAGES_ROUTE]);
  }

  // ── leaving with unsaved changes ──────────────────────────────────────────

  hasPendingChanges(): boolean {
    return !this.saved && !this.isLoading && this.snapshot() !== this.initial;
  }

  confirmDiscardChanges(): Observable<boolean> {
    if (!this.hasPendingChanges()) {
      return of(true);
    }
    const entity = this.translate.instant('accessManagement.franchisePackages.entity').toLowerCase();
    return this.dialogs.confirm({
      title: this.translate.instant('shared.adminCrud.discard.title', { entity }),
      message: this.translate.instant('shared.adminCrud.discard.message', { entity }),
      confirmText: this.translate.instant('shared.adminCrud.discard.confirm'),
      cancelText: this.translate.instant('shared.adminCrud.discard.cancel'),
      variant: 'danger',
    });
  }

  private snapshot(): string {
    return JSON.stringify(this.form);
  }
}
