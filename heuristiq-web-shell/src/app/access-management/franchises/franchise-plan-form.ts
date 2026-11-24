import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { Observable, of } from 'rxjs';
import { PendingChangesAware } from '../../shared/guards/pending-changes.guard';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { Application } from '../../shared/models/uam.models';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { FranchiseApi } from './franchise-api';
import { FranchisePlan, Location } from './franchise.models';
import { APPLICATION_OPTIONS_LIMIT, FALLBACK_CURRENCY, FRANCHISE_PLANS_ROUTE } from './franchises.constants';
import {
  LOCATION_PRIVILEGES, LOCATION_TYPES, LOCATION_TYPE_LABEL_KEYS, PLAN_SCOPES, PLAN_SCOPE_LABEL_KEYS, TYPE_DEFAULT_PARENT_TYPES,
} from './franchise-locations.constants';
import { ScopedPlanForm, emptyScopedPlanForm, planToScopedForm, scopedPlanFormError, toScopedPlanRequest } from './franchise-plan-scope.helpers';
import { CountryOption, countryOptions, currencyOptions, currencySymbol, formatMoney, roundFee } from './franchises.helpers';
import { labelKey } from './franchise-earnings.helpers';

/**
 * The page that changes a franchise plan, opened from the plans list (its own route, like the UAM forms): the application, what the
 * plan covers (one place, every place of a type, a country or everywhere), and its agent places and fee. Saving returns to the
 * list; leaving with unsaved changes asks first.
 */
@Component({
  selector: 'app-franchise-plan-form',
  standalone: false,
  templateUrl: './franchise-plan-form.html',
  styleUrl: './franchise-form-page.scss',
})
export class FranchisePlanForm implements OnInit, PendingChangesAware {
  readonly locationPrivileges = LOCATION_PRIVILEGES;
  readonly scopes = PLAN_SCOPES;
  readonly types = LOCATION_TYPES;
  readonly underTypes = TYPE_DEFAULT_PARENT_TYPES;
  /** Every country the browser can name, and the ones plans may be set for (the backend's list; all when it names none). */
  private readonly allCountries: CountryOption[] = countryOptions();
  countries: CountryOption[] = this.allCountries;
  applications: Application[] = [];
  defaultCurrency = FALLBACK_CURRENCY;

  form: ScopedPlanForm = emptyScopedPlanForm(FALLBACK_CURRENCY);
  editingId: string | null = null;
  isLoading = true;
  loadFailed = false;
  saving = false;
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
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.editingId = id;
    this.uam.list<Application>('/applications', 0, APPLICATION_OPTIONS_LIMIT).subscribe({
      next: (page) => { this.applications = page.content ?? []; this.cdr.detectChanges(); },
      error: () => this.notify.error(this.translate.instant('accessManagement.franchises.errors.applicationsUnavailable')),
    });
    // Which countries a plan may be for, and the currency a new one starts in, come with any page of plans.
    this.api.plansPage({ page: 0, size: 1 }).subscribe({
      next: (result) => {
        this.defaultCurrency = result?.defaultCurrency || FALLBACK_CURRENCY;
        const allowed = result?.countries ?? [];
        this.countries = allowed.length > 0 ? this.allCountries.filter((country) => allowed.includes(country.code)) : this.allCountries;
        this.cdr.detectChanges();
      },
      error: () => {},
    });
    if (!id) {
      this.open(null);
      return;
    }
    // Read the current source record, including after navigation from a search/list row.
    this.api.plan(id).subscribe({
      next: (plan) => {
        if (plan?.id === id) this.open(plan);
        else this.fail(null);
        this.cdr.detectChanges();
      },
      error: (err: unknown) => { this.fail(err); this.cdr.detectChanges(); },
    });
  }

  private open(plan: FranchisePlan | null): void {
    this.form = plan ? planToScopedForm(plan, this.defaultCurrency) : emptyScopedPlanForm(this.defaultCurrency);
    this.isLoading = false;
    this.initial = this.snapshot();
  }

  private fail(err: unknown): void {
    this.isLoading = false;
    this.loadFailed = true;
    this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchisePlans.errorMessage')));
  }

  /** The place fields use the cascading picker only for someone who may read the places reference; others type the old fields. */
  get canPickPlaces(): boolean {
    return this.access.can(this.locationPrivileges.list) && this.access.can(this.locationPrivileges.children);
  }

  get titleKey(): string {
    return this.editingId ? 'accessManagement.franchisePlans.editTitle' : 'accessManagement.franchisePlans.newTitle';
  }

  /** The currencies the form's plan may use. */
  currenciesOf(form: ScopedPlanForm): string[] {
    return currencyOptions(form.currency);
  }

  scopeLabel(scope: string): string {
    const key = labelKey(PLAN_SCOPE_LABEL_KEYS, scope);
    return key ? this.translate.instant(key) : scope;
  }

  typeLabel(type: string | null | undefined): string {
    const key = labelKey(LOCATION_TYPE_LABEL_KEYS, type);
    return key ? this.translate.instant(key) : type ?? '';
  }

  money(amount: number | null, currency: string): string {
    return formatMoney(amount, currency);
  }

  symbol(currency: string): string {
    return currencySymbol(currency);
  }

  /** A new scope starts the place choice again: what was chosen for the old scope means nothing for the new one. */
  onScopeChange(): void {
    this.clearPlaces();
    this.form.scopeType = '';
  }

  /** A new country starts again: a place means nothing outside its country. */
  onCountryChange(): void {
    this.clearPlaces();
  }

  /** A new type default type: the parent chosen for the old one may no longer be allowed. */
  onScopeTypeChange(): void {
    this.form.scopeLocationId = '';
    this.form.scopeParentType = '';
    this.form.chain = [];
  }

  onPlacePicked(place: Location | null): void {
    this.form.locationId = place?.id ?? '';
  }

  onUnderPicked(place: Location | null): void {
    this.form.scopeLocationId = place?.id ?? '';
    this.form.scopeParentType = place?.type ?? '';
  }

  private clearPlaces(): void {
    this.form.locationId = '';
    this.form.scopeLocationId = '';
    this.form.scopeParentType = '';
    this.form.chain = [];
  }

  roundFee(): void {
    this.form.fee = roundFee(this.form.fee);
  }

  save(): void {
    const error = scopedPlanFormError(this.form, this.canPickPlaces);
    if (error) {
      this.notify.error(this.translate.instant(error));
      return;
    }
    const request = toScopedPlanRequest(this.form, this.allCountries, this.canPickPlaces);
    const editing = this.editingId;
    const call$ = editing ? this.api.changePlan(editing, request) : this.api.addPlan(request);
    this.saving = true;
    call$.subscribe({
      next: () => {
        this.notify.success(this.translate.instant(editing
          ? 'accessManagement.franchisePlans.messages.changed' : 'accessManagement.franchisePlans.messages.added'));
        this.saving = false;
        this.saved = true;
        this.back();
      },
      error: (err: unknown) => {
        this.saving = false;
        // The service's own text (for example the duplicate "A plan for this application and area already exists") is shown as given.
        this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchisePlans.messages.saveFailed')));
        this.cdr.detectChanges();
      },
    });
  }

  cancel(): void {
    this.back();
  }

  private back(): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_PLANS_ROUTE]);
  }

  // ── leaving with unsaved changes ──────────────────────────────────────────

  hasPendingChanges(): boolean {
    return !this.saved && !this.isLoading && this.snapshot() !== this.initial;
  }

  confirmDiscardChanges(): Observable<boolean> {
    if (!this.hasPendingChanges()) {
      return of(true);
    }
    const entity = this.translate.instant('accessManagement.franchisePlans.entity').toLowerCase();
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
