import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqCommonTableColumn, HqPrivilegeAccessService, HqAmount } from 'hq-shared-lib';
import { forkJoin, Observable } from 'rxjs';
import { NotificationService } from '../../shared/services/notification-service';
import { Application } from '../../shared/models/uam.models';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { FranchiseApi } from './franchise-api';
import { FranchisePlan, Location, LocationType } from './franchise.models';
import { FALLBACK_CURRENCY, FRANCHISE_PRIVILEGES } from './franchises.constants';
import { LOCATION_TYPES, LOCATION_TYPE_LABEL_KEYS, TYPE_DEFAULT_PARENT_TYPES } from './franchise-locations.constants';
import { labelKey } from './franchise-earnings.helpers';
import { CountryOption, applicationCodeForLabel, applicationPickLabel } from './franchises.helpers';
import { emptyScopedPlanForm, scopedPlanFormError, toScopedPlanRequest } from './franchise-plan-scope.helpers';
import { DEFAULT_AGENT_WALLET_MULTIPLIER, TYPE_FEES_I18N, TYPE_FEES_PLANS_PAGE_SIZE, TYPE_FEES_STATE_KEYS } from './franchise-type-fees.constants';
import {
  TypeFeeRow, TypeFeeRowState, buildTypeFeeRows, typeFeeChanges, typeFeeRowState, walletAllocation, walletPerAgent,
} from './franchise-type-fees.helpers';

/**
 * Sets the max agents and the franchise fee for every kind of place at once: pick the application and country (and, to cover only
 * the places under a state or district, that place), then fill a line per type (state, district, town, village ...). Saving
 * creates or changes one "all places of a type" plan per filled line; a place with no plan of its own then takes its type's line.
 * Lines already saved are shown filled; clearing a line does not delete its plan (delete it from the plans list).
 */
@Component({
  selector: 'app-franchise-type-fees',
  standalone: false,
  templateUrl: './franchise-type-fees.html',
  styleUrl: './franchise-type-fees.scss',
})
export class FranchiseTypeFees implements OnInit {
  readonly i18n = TYPE_FEES_I18N;
  readonly privileges = FRANCHISE_PRIVILEGES;
  readonly underTypes = TYPE_DEFAULT_PARENT_TYPES;

  @Input() applications: Application[] = [];
  @Input() countries: CountryOption[] = [];
  @Input() defaultCurrency = FALLBACK_CURRENCY;
  /** Emitted after plans were saved, so the list underneath can reload. */
  @Output() saved = new EventEmitter<void>();

  applicationCode = '';
  countryCode = '';
  /** The state or district the lines are limited to; null covers the whole country. */
  under: Pick<Location, 'id' | 'type'> | null = null;
  rows: TypeFeeRow[] = [];
  columns: HqCommonTableColumn<TypeFeeRow>[] = [];
  loading = false;
  saving = false;
  failed = false;
  /** How many times its fee a franchise is given for its agents' wallets, as the backend reports it. */
  walletMultiplier = DEFAULT_AGENT_WALLET_MULTIPLIER;

  constructor(
    private api: FranchiseApi,
    private notify: NotificationService,
    private translate: TranslateService,
    private access: HqPrivilegeAccessService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const heading = (key: string): string => this.translate.instant(`${TYPE_FEES_I18N}.${key}`);
    this.columns = [
      { key: 'type', label: heading('type'), render: (row) => this.typeLabel(row.type) },
      { key: 'agentLimit', label: heading('agents') },
      { key: 'fee', label: heading('fee') },
      { key: 'wallet', label: heading('wallet') },
      { key: 'perAgent', label: heading('perAgent') },
      { key: 'state', label: heading('status') },
    ];
  }

  get ready(): boolean {
    return !!this.applicationCode && !!this.countryCode;
  }

  get changes(): ReturnType<typeof typeFeeChanges> {
    return typeFeeChanges(this.rows);
  }

  get canSave(): boolean {
    const { create, change } = this.changes;
    return !this.saving && (create.length > 0 || change.length > 0);
  }

  applicationLabels(): string[] {
    return this.applications.map(applicationPickLabel);
  }

  applicationLabel(): string {
    const application = this.applications.find((a) => a.code === this.applicationCode);
    return application ? applicationPickLabel(application) : '';
  }

  pickApplication(label: string): void {
    this.applicationCode = applicationCodeForLabel(this.applications, label);
    this.reload();
  }

  onCountryChange(): void {
    this.under = null;
    this.reload();
  }

  onUnderPicked(place: Location | null): void {
    this.under = place ? { id: place.id, type: place.type } : null;
    this.reload();
  }

  typeLabel(type: string): string {
    const key = labelKey(LOCATION_TYPE_LABEL_KEYS, type);
    return key ? this.translate.instant(key) : type;
  }

  /** One stable amount per line for the fee field, rebuilt only when the line's fee or currency changes. */
  private readonly feeAmounts = new WeakMap<TypeFeeRow, HqAmount>();

  feeAmount(row: TypeFeeRow): HqAmount {
    const cached = this.feeAmounts.get(row);
    if (cached && cached.value === row.fee && cached.code === row.currency) {
      return cached;
    }
    const amount: HqAmount = { code: row.currency, value: row.fee };
    this.feeAmounts.set(row, amount);
    return amount;
  }

  /** The fee field emits the amount already rounded to the currency's decimals. */
  setFee(row: TypeFeeRow, amount: HqAmount | null): void {
    row.fee = amount?.value ?? null;
    this.feeAmounts.set(row, { code: row.currency, value: row.fee });
  }

  /** The franchise's wallet allocation for a line (read-only, follows the fee as it is typed). */
  wallet(row: TypeFeeRow): number | null {
    return walletAllocation(row.fee, this.walletMultiplier);
  }

  /** What each new agent of such a franchise is credited to their wallet (read-only). */
  perAgent(row: TypeFeeRow): number | null {
    return walletPerAgent(row.fee, row.agentLimit, this.walletMultiplier);
  }

  stateOf(row: TypeFeeRow): TypeFeeRowState {
    return typeFeeRowState(row);
  }

  stateLabel(row: TypeFeeRow): string {
    const key = TYPE_FEES_STATE_KEYS[this.stateOf(row)];
    return key ? this.translate.instant(key) : '';
  }

  /** A saved line is changed with the update privilege, a new one is created with the create privilege. */
  canEdit(row: TypeFeeRow): boolean {
    return this.access.can(row.planId ? this.privileges.updatePlan : this.privileges.createPlan);
  }

  /** Reads the saved type defaults of the application and fills a line per type; nothing is read until application and country are set. */
  reload(): void {
    this.rows = [];
    this.failed = false;
    if (!this.ready) {
      return;
    }
    this.loading = true;
    this.api.plansPage({ applicationCode: this.applicationCode, scope: 'TYPE_DEFAULT', page: 0, size: TYPE_FEES_PLANS_PAGE_SIZE }).subscribe({
      next: (result) => {
        this.rows = buildTypeFeeRows(LOCATION_TYPES, result?.plans ?? [], this.countryCode, this.under?.id ?? '',
          result?.defaultCurrency || this.defaultCurrency);
        this.walletMultiplier = result?.agentWalletMultiplier ?? DEFAULT_AGENT_WALLET_MULTIPLIER;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err: unknown) => {
        this.failed = true;
        this.loading = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant(`${TYPE_FEES_I18N}.loadFailed`)));
        this.cdr.detectChanges();
      },
    });
  }

  save(): void {
    const { create, change, incomplete } = this.changes;
    if (incomplete.length > 0) {
      this.notify.error(this.translate.instant(`${TYPE_FEES_I18N}.incompleteRows`, { types: incomplete.map((row) => this.typeLabel(row.type)).join(', ') }));
      return;
    }
    const calls: Observable<FranchisePlan>[] = [];
    for (const row of [...create, ...change]) {
      const form = {
        ...emptyScopedPlanForm(row.currency), applicationCode: this.applicationCode, countryCode: this.countryCode, scope: 'TYPE_DEFAULT' as const,
        scopeType: row.type as LocationType, scopeLocationId: this.under?.id ?? '', scopeParentType: (this.under?.type ?? '') as LocationType | '',
        agentLimit: row.agentLimit, fee: row.fee,
      };
      const error = scopedPlanFormError(form, true);
      if (error) {
        this.notify.error(this.translate.instant(error));
        return;
      }
      const request = toScopedPlanRequest(form, this.countries, true);
      calls.push(row.planId ? this.api.changePlan(row.planId, request) : this.api.addPlan(request));
    }
    if (calls.length === 0) {
      return;
    }
    this.saving = true;
    forkJoin(calls).subscribe({
      next: () => {
        this.saving = false;
        this.notify.success(this.translate.instant(`${TYPE_FEES_I18N}.saved`, { count: calls.length }));
        this.reload();
        this.saved.emit();
      },
      error: (err: unknown) => {
        this.saving = false;
        this.notify.error(apiErrorMessage(err, this.translate.instant(`${TYPE_FEES_I18N}.saveFailed`)));
        this.reload();
      },
    });
  }
}
