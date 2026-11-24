import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HqAmount, HqCommonTableColumn, HqPrivilegeAccessService } from 'hq-shared-lib';
import { LIST_PAGE_SIZE_OPTIONS } from '../../shared/list-page.constants';
import { ACCESS_MANAGEMENT_BASE_PATH } from '../admin-form-page/admin-form-page.constants';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { Application } from '../../shared/models/uam.models';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { FranchiseApi } from './franchise-api';
import { FranchisePlan, Location, LocationLevel, LocationType, PlanQuery, PlanResolution, PlanScope } from './franchise.models';
import { APPLICATION_OPTIONS_LIMIT, FALLBACK_CURRENCY, FRANCHISE_PLANS_ROUTE, FRANCHISE_PRIVILEGES } from './franchises.constants';
import {
  LOCATIONS_PAGE_SIZE, LOCATION_LEVELS, LOCATION_LEVEL_LABEL_KEYS, LOCATION_PRIVILEGES, LOCATION_TYPES, LOCATION_TYPE_LABEL_KEYS,
  PICKER_PAGE_SIZE, PLAN_RULE_LABEL_KEYS, PLAN_SCOPES, PLAN_SCOPE_LABEL_KEYS,
} from './franchise-locations.constants';
import { inferScope, planPlace } from './franchise-plan-scope.helpers';
import { CountryOption, countryOptions, currencySymbol, moneyText } from './franchises.helpers';
import { labelKey } from './franchise-earnings.helpers';

/** What the plans filter bar holds ('' = any). */
export interface PlanFilters {
  applicationCode: string;
  scope: PlanScope | '';
  locationType: LocationType | '';
  level: LocationLevel | '';
  stateId: string;
  districtId: string;
  q: string;
}

const NO_FILTERS: PlanFilters = { applicationCode: '', scope: '', locationType: '', level: '', stateId: '', districtId: '', q: '' };

/** The "Which plan applies?" panel: an application and a place (optional) and what the service answers. */
export interface ResolveForm {
  applicationCode: string;
  countryCode: string;
  locationId: string;
}

/**
 * Administrators keep the franchise plans here: for an application, the agent places and fee of one place, of every place of a
 * type under a state or district, of a whole country, or of everywhere. Opening a franchise offers these plans to fill its terms in.
 */
@Component({
  selector: 'app-franchise-plans-list',
  standalone: false,
  templateUrl: './franchise-plans-list.html',
  styleUrls: ['./franchises-list.scss', './franchise-plan-resolve.scss'],
})
export class FranchisePlansList implements OnInit {
  readonly privileges = FRANCHISE_PRIVILEGES;
  readonly locationPrivileges = LOCATION_PRIVILEGES;
  readonly scopes = PLAN_SCOPES;
  readonly types = LOCATION_TYPES;
  readonly levels = LOCATION_LEVELS;
  readonly pageSizeOptions = LIST_PAGE_SIZE_OPTIONS;
  pageSize = LOCATIONS_PAGE_SIZE;
  /** The grid's columns (headings translated once the screen opens); the cells that combine fields are drawn from the template. */
  columns: HqCommonTableColumn<FranchisePlan>[] = [];
  /** Every country the browser can name, and the ones plans may be set for (the backend's list; all when it names none). */
  private readonly allCountries: CountryOption[] = countryOptions();
  countries: CountryOption[] = this.allCountries;
  allowedCountries: string[] = [];
  plans: FranchisePlan[] = [];
  applications: Application[] = [];
  defaultCurrency = FALLBACK_CURRENCY;
  isLoading = false;
  hasError = false;
  page = 0;
  total = 0;

  filters: PlanFilters = { ...NO_FILTERS };
  filterStates: Location[] = [];
  filterDistricts: Location[] = [];

  resolveForm: ResolveForm = { applicationCode: '', countryCode: '', locationId: '' };
  resolution: PlanResolution | null = null;
  resolveError = '';
  resolving = false;

  constructor(
    private api: FranchiseApi,
    private uam: UamService,
    private dialogs: AppDialogService,
    private notify: NotificationService,
    private translate: TranslateService,
    private cdr: ChangeDetectorRef,
    private access: HqPrivilegeAccessService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(key);
    this.columns = [
      { key: 'applicationCode', label: heading('accessManagement.franchises.application') },
      { key: 'scope', label: heading('accessManagement.franchisePlans.scope') },
      { key: 'area', label: heading('accessManagement.franchisePlans.area') },
      { key: 'type', label: heading('accessManagement.franchiseLocations.type') },
      { key: 'agentLimit', label: heading('accessManagement.franchises.agentLimit') },
      { key: 'fee', label: heading('accessManagement.franchises.fee') },
    ];
    this.load();
    if (this.access.can(this.locationPrivileges.list)) {
      this.api.locations({ type: 'STATE', usable: true, size: PICKER_PAGE_SIZE }).subscribe({
        next: (page) => { this.filterStates = page?.items ?? []; this.cdr.detectChanges(); },
        error: () => { this.filterStates = []; },
      });
    }
    this.uam.list<Application>('/applications', 0, APPLICATION_OPTIONS_LIMIT).subscribe({
      next: (page) => { this.applications = page.content ?? []; this.cdr.detectChanges(); },
      error: () => this.notify.error(this.translate.instant('accessManagement.franchises.errors.applicationsUnavailable')),
    });
  }

  /** The place fields use the cascading picker only for someone who may read the places reference; others type the old fields. */
  get canPickPlaces(): boolean {
    return this.access.can(this.locationPrivileges.list) && this.access.can(this.locationPrivileges.children);
  }

  get pageCount(): number {
    return Math.max(1, Math.ceil(this.total / this.pageSize));
  }

  goToPage(page: number): void {
    this.load(page);
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.load(0);
  }

  query(page: number): PlanQuery {
    return {
      applicationCode: this.filters.applicationCode, scope: this.filters.scope, locationType: this.filters.locationType,
      level: this.filters.level, stateId: this.filters.stateId, districtId: this.filters.districtId, q: this.filters.q, page, size: this.pageSize,
    };
  }

  load(page = 0): void {
    this.page = Math.max(page, 0);
    this.isLoading = true;
    this.hasError = false;
    this.api.plansPage(this.query(this.page)).subscribe({
      next: (result) => {
        this.plans = result?.plans ?? [];
        this.total = result?.totalElements ?? this.plans.length;
        this.defaultCurrency = result?.defaultCurrency || FALLBACK_CURRENCY;
        this.allowedCountries = result?.countries ?? [];
        this.countries = this.allowedCountries.length > 0
          ? this.allCountries.filter((country) => this.allowedCountries.includes(country.code)) : this.allCountries;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.hasError = true;
        this.isLoading = false;
        this.notify.error(this.translate.instant('accessManagement.franchisePlans.errorMessage'));
        this.cdr.detectChanges();
      },
    });
  }

  clearFilters(): void {
    this.filters = { ...NO_FILTERS };
    this.filterDistricts = [];
    this.load();
  }

  /** A state was chosen in the filter: its districts are offered, and a district of the old state is dropped. */
  onFilterStateChange(): void {
    this.filters.districtId = '';
    this.filterDistricts = [];
    if (!this.filters.stateId || !this.access.can(this.locationPrivileges.list)) {
      return;
    }
    this.api.locations({ type: 'DISTRICT', parentId: this.filters.stateId, usable: true, size: PICKER_PAGE_SIZE }).subscribe({
      next: (page) => { this.filterDistricts = page?.items ?? []; this.cdr.detectChanges(); },
      error: () => { this.filterDistricts = []; },
    });
  }

  // ── Change: the plan has its own page ─────────────────────────────────────

  /** The tab that is open: the list of plans, the fees by place type, or the "which plan applies" check. */
  view: 'plans' | 'fees' | 'resolve' = 'plans';

  selectView(view: 'plans' | 'fees' | 'resolve'): void {
    this.view = view;
  }

  /** Whether the "fees by place type" grid is open. */
  get showTypeFees(): boolean {
    return this.view === 'fees';
  }

  toggleTypeFees(): void {
    this.view = this.view === 'fees' ? 'plans' : 'fees';
  }

  /** Opens the editor, which reads the current plan by id from the source API. */
  editPlan(plan: FranchisePlan): void {
    void this.router.navigate([ACCESS_MANAGEMENT_BASE_PATH, FRANCHISE_PLANS_ROUTE, plan.id, 'edit']);
  }

  confirmDelete(plan: FranchisePlan): void {
    this.dialogs.confirm({
      title: this.translate.instant('accessManagement.franchisePlans.deleteDialog.title'),
      message: this.translate.instant('accessManagement.franchisePlans.deleteDialog.message', { region: this.region(plan) }),
      variant: 'danger',
      confirmText: this.translate.instant('shared.listPage.rowActions.delete'),
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.deletePlan(plan.id).subscribe({
        next: () => { this.notify.success(this.translate.instant('accessManagement.franchisePlans.messages.deleted')); this.load(this.page); },
        error: (err: unknown) =>
          this.notify.error(apiErrorMessage(err, this.translate.instant('accessManagement.franchisePlans.messages.deleteFailed'))),
      });
    });
  }

  // ── Which plan applies? ────────────────────────────────────────────────────

  onResolveCountryChange(): void {
    this.resolveForm.locationId = '';
    this.resolution = null;
  }

  onResolvePlacePicked(place: Location | null): void {
    this.resolveForm.locationId = place?.id ?? '';
    this.resolution = null;
  }

  resolve(): void {
    if (!this.resolveForm.applicationCode) {
      this.resolveError = this.translate.instant('accessManagement.franchisePlans.errors.application');
      this.resolution = null;
      return;
    }
    this.resolving = true;
    this.resolveError = '';
    this.resolution = null;
    this.api.resolvePlan(this.resolveForm.applicationCode, this.resolveForm.locationId || undefined).subscribe({
      next: (result) => { this.resolution = result; this.resolving = false; this.cdr.detectChanges(); },
      error: (err: unknown) => {
        // "No franchise plan applies to this place for ..." and "The place is not active ..." come from the service as given.
        this.resolveError = apiErrorMessage(err, this.translate.instant('accessManagement.franchisePlans.resolve.failed'));
        this.resolving = false;
        this.cdr.detectChanges();
      },
    });
  }

  // ── Display ────────────────────────────────────────────────────────────────

  nameOf(code: string): string {
    return this.applications.find((application) => application.code === code)?.name ?? code;
  }

  scopeOf(plan: FranchisePlan): PlanScope {
    return inferScope(plan);
  }

  scopeLabel(scope: string): string {
    const key = labelKey(PLAN_SCOPE_LABEL_KEYS, scope);
    return key ? this.translate.instant(key) : scope;
  }

  ruleLabel(rule: string): string {
    const key = labelKey(PLAN_RULE_LABEL_KEYS, rule);
    return key ? this.translate.instant(key) : rule;
  }

  typeLabel(type: string | null | undefined): string {
    const key = labelKey(LOCATION_TYPE_LABEL_KEYS, type);
    return key ? this.translate.instant(key) : type ?? '';
  }

  levelLabel(level: string | null | undefined): string {
    const key = labelKey(LOCATION_LEVEL_LABEL_KEYS, level);
    return key ? this.translate.instant(key) : level ?? '';
  }

  /** Where a plan applies: the place path (state > district > city), "All TOWNs under X", the country, or "General". */
  region(plan: FranchisePlan): string {
    const scope = inferScope(plan);
    if (scope === 'TYPE_DEFAULT') {
      const type = this.typeLabel(plan.scopeType);
      const parent = plan.chain?.[plan.chain.length - 1];
      return parent
        ? this.translate.instant('accessManagement.franchisePlans.scopeDescription.underPlace', { type, name: parent.name })
        : this.translate.instant('accessManagement.franchisePlans.scopeDescription.inCountry', { type, country: plan.countryName || plan.countryCode });
    }
    return planPlace(plan, this.translate.instant('accessManagement.franchises.regionGeneral'));
  }

  /** A plan fee (`{ code, value }`; an older bare number is read in the default currency) as money text. */
  money(amount: HqAmount | null): string {
    return moneyText(amount, this.defaultCurrency);
  }

  symbol(currency: string): string {
    return currencySymbol(currency);
  }
}
