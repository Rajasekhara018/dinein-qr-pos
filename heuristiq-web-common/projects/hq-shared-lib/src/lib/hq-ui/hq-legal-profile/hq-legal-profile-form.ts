import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { HqOption } from '../hq-option.model';
import { HQ_ENTITY_TYPES, HQ_GENERAL_TAX_ID, HQ_LEGAL_PROFILE_LIMITS } from './hq-legal-profile.constants';
import {
  HqLegalAddress, HqLegalAddressKind, HqLegalAddressLookup, HqLegalProfile, HqLegalProfileError, HqLegalProfileOption, HqTaxIdType,
} from './hq-legal-profile.model';
import { hqEmptyLegalProfile, hqHasNoPostalCodes, hqLegalProfileErrors, hqTaxIdType, hqTaxIdTypesFor } from './hq-legal-profile.rules';
import { HQ_LEGAL_PROFILE_DEFAULT_TEXTS, HQ_LEGAL_PROFILE_TEXTS, HqLegalProfileTexts } from './hq-legal-profile.texts';

/** Where a list of choices stands: not asked yet, on its way, in, or could not be read. */
export type HqLegalListStatus = 'idle' | 'loading' | 'ready' | 'failed';

/** The state and district choices of one address block, with where each list stands. */
interface AddressLists {
  states: HqOption[];
  statesStatus: HqLegalListStatus;
  districts: HqOption[];
  districtsStatus: HqLegalListStatus;
}

const ADDRESS_KINDS: readonly HqLegalAddressKind[] = ['REGISTERED', 'OPERATING'];
const emptyLists = (): AddressLists => ({ states: [], statesStatus: 'idle', districts: [], districtsStatus: 'idle' });

/**
 * Edits the legal entity behind an organization or a franchise (HqLegalProfile): entity, tax ids and the registered and
 * operating addresses, each checked by its country with the same rules as the services. A ControlValueAccessor and
 * Validator: the control is invalid while any rule fails. Errors show once a field was left, or all at once with
 * `showAllErrors` (after a save attempt). `countries` is the app's list of countries by name. Every list is a
 * hq-searchable-select. When the app passes an `addressLookup`, the state and the city / district of an address in a
 * country it supports are picked from its lists (read when the country or state is chosen); changing the country clears
 * the state and the district, changing the state clears the district. Any other country keeps plain text boxes.
 */
@Component({
  selector: 'hq-legal-profile-form',
  standalone: false,
  templateUrl: './hq-legal-profile-form.html',
  styleUrl: './hq-legal-profile-form.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqLegalProfileForm), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => HqLegalProfileForm), multi: true },
  ],
})
export class HqLegalProfileForm implements ControlValueAccessor, Validator, OnChanges, OnDestroy {
  readonly texts: HqLegalProfileTexts = inject(HQ_LEGAL_PROFILE_TEXTS, { optional: true }) ?? HQ_LEGAL_PROFILE_DEFAULT_TEXTS;
  private readonly cdr = inject(ChangeDetectorRef);

  @Input() countries: HqLegalProfileOption[] = [];
  @Input() disabled = false;
  @Input() showAllErrors = false;
  /** Display one section while retaining the complete form value and validation. */
  @Input() section: 'all' | 'entity' | 'tax' | 'addresses' = 'all';
  /** The country new addresses start in (the organization's), until the person picks one. */
  @Input() defaultCountry = '';
  /** Where states and districts come from for the countries it supports; none: plain text boxes everywhere. */
  @Input() addressLookup: HqLegalAddressLookup | null = null;

  readonly entityTypes = HQ_ENTITY_TYPES;
  readonly limits = HQ_LEGAL_PROFILE_LIMITS;

  profile: HqLegalProfile = hqEmptyLegalProfile();
  errors: HqLegalProfileError[] = [];
  taxTypes: HqTaxIdType[] = hqTaxIdTypesFor(null);
  private readonly touchedFields = new Set<string>();
  private readonly lists: Record<HqLegalAddressKind, AddressLists> = { REGISTERED: emptyLists(), OPERATING: emptyLists() };
  private readonly stateCache = new Map<string, HqOption[]>();
  private readonly districtCache = new Map<string, HqOption[]>();
  private readonly memo = new Map<string, { sig: string; items: HqOption[] }>();
  /** The country picked for an address that holds nothing else yet (so it is not stored), per address. */
  private chosenCountry: Partial<Record<HqLegalAddressKind, string>> = {};
  private readonly destroyed$ = new Subject<void>();

  private onChange: (value: HqLegalProfile) => void = () => {};
  private onTouched: () => void = () => {};

  ngOnChanges(): void {
    this.loadLists();
  }

  ngOnDestroy(): void {
    this.destroyed$.next();
    this.destroyed$.complete();
  }

  writeValue(value: HqLegalProfile | null): void {
    const profile = { ...hqEmptyLegalProfile(), ...(value ?? {}) };
    this.profile = {
      ...profile,
      taxIds: (profile.taxIds ?? []).map((t) => ({ ...t })),
      addresses: (profile.addresses ?? []).map((a) => ({ ...a })),
    };
    this.touchedFields.clear();
    for (const kind of ADDRESS_KINDS) this.lists[kind] = emptyLists();
    this.chosenCountry = {};
    this.refresh();
    this.cdr.markForCheck();
  }

  registerOnChange(fn: (value: HqLegalProfile) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    this.cdr.markForCheck();
  }

  validate(): ValidationErrors | null {
    return this.errors.length ? { legalProfile: this.errors } : null;
  }

  setField(field: 'entityType' | 'legalName' | 'tradingName' | 'registrationNumber' | 'registrationCountry', value: string): void {
    this.profile = { ...this.profile, [field]: value || null };
    this.changed();
  }

  setTaxRegistered(value: boolean): void {
    this.profile = { ...this.profile, taxRegistered: value };
    this.changed();
  }

  addTaxId(): void {
    const used = new Set(this.profile.taxIds.map((t) => t.type));
    const next = this.taxTypes.find((t) => !used.has(t.code))?.code ?? HQ_GENERAL_TAX_ID;
    this.profile = { ...this.profile, taxIds: [...this.profile.taxIds, { type: next, value: '' }] };
    this.changed();
  }

  setTaxId(index: number, field: 'type' | 'value', value: string): void {
    const taxIds = this.profile.taxIds.map((t, i) => (i === index ? { ...t, [field]: value } : t));
    this.profile = { ...this.profile, taxIds };
    this.changed();
  }

  removeTaxId(index: number): void {
    this.profile = { ...this.profile, taxIds: this.profile.taxIds.filter((_, i) => i !== index) };
    this.changed();
  }

  /** The example number for a tax id type, shown under its field. */
  taxExample(type: string): string {
    const example = hqTaxIdType(type)?.example;
    return example ? this.texts.taxExample(example) : '';
  }

  address(kind: HqLegalAddressKind): HqLegalAddress | undefined {
    return this.profile.addresses.find((a) => a.kind === kind);
  }

  /** The registered address always shows (it is saved once something is typed in it); the operating one only on request. */
  addressView(kind: HqLegalAddressKind): HqLegalAddress {
    return this.address(kind) ?? this.blankAddress(kind);
  }

  get hasOperatingAddress(): boolean {
    return !!this.address('OPERATING');
  }

  setOperatingDifferent(different: boolean): void {
    const others = this.profile.addresses.filter((a) => a.kind !== 'OPERATING');
    delete this.chosenCountry.OPERATING;
    this.lists.OPERATING = emptyLists();
    this.profile = { ...this.profile, addresses: different ? [...others, this.blankAddress('OPERATING')] : others };
    this.changed();
  }

  setAddress(kind: HqLegalAddressKind, field: keyof Omit<HqLegalAddress, 'kind'>, value: string): void {
    const current = this.addressView(kind);
    let next: HqLegalAddress = { ...current, [field]: value };
    if (field === 'country' && hqHasNoPostalCodes(value)) next = { ...next, postalCode: null };
    // The state belongs to its country and the district to its state: a new parent empties what hangs below it.
    if (field === 'country' && value !== current.country) {
      next = { ...next, region: null, city: '' };
      this.lists[kind] = emptyLists();
    }
    if (field === 'region' && (value || null) !== (current.region || null)) {
      next = { ...next, city: '' };
      this.lists[kind] = { ...this.lists[kind], districts: [], districtsStatus: 'idle' };
    }
    const others = this.profile.addresses.filter((a) => a.kind !== kind);
    const keep = kind === 'OPERATING' || !this.isBlank(next);
    // An address with nothing typed yet is not stored, but the country just picked for it must stay on screen (its state and
    // district lists depend on it), so it is remembered until the address holds something.
    if (field === 'country') this.chosenCountry[kind] = value;
    const addresses = keep ? [...others, next] : others;
    addresses.sort((a, b) => (a.kind === 'REGISTERED' ? -1 : b.kind === 'REGISTERED' ? 1 : 0));
    this.profile = { ...this.profile, addresses };
    this.changed();
  }

  noPostalCodes(kind: HqLegalAddressKind): boolean {
    return hqHasNoPostalCodes(this.addressView(kind).country);
  }

  touch(field: string): void {
    this.touchedFields.add(field);
    this.onTouched();
  }

  /** The message for a field, once it was left (or after a save attempt). */
  fieldError(field: string): string {
    if (!this.showAllErrors && !this.touchedFields.has(field)) return '';
    const error = this.errors.find((e) => e.field === field);
    return error ? this.texts.error(error.key) : '';
  }

  // ---- choices ------------------------------------------------------------------------------------

  /** The entity types as choices (value = code, label = its translated name); the same array while the words are the same. */
  get entityTypeItems(): HqOption[] {
    return this.memoItems('entity', this.entityTypes.map((code) => ({ value: code, label: this.texts.entityType(code) })));
  }

  /** The tax id types of the registration country as choices. */
  get taxTypeItems(): HqOption[] {
    return this.memoItems('tax', this.taxTypes.map((t) => ({ value: t.code, label: this.texts.taxType(t.code) })));
  }

  // ---- state and district lists ------------------------------------------------------------------

  /** True when the state and city / district of this address are picked from the app's lists (its country is supported). */
  listsActive(kind: HqLegalAddressKind): boolean {
    const country = this.addressView(kind).country;
    return !!this.addressLookup && !!country && this.addressLookup.supports(country);
  }

  stateItems(kind: HqLegalAddressKind): HqOption[] {
    return this.lists[kind].states;
  }

  districtItems(kind: HqLegalAddressKind): HqOption[] {
    return this.lists[kind].districts;
  }

  statesStatus(kind: HqLegalAddressKind): HqLegalListStatus {
    return this.lists[kind].statesStatus;
  }

  districtsStatus(kind: HqLegalAddressKind): HqLegalListStatus {
    return this.lists[kind].districtsStatus;
  }

  /** The hint under the district picker while no state is chosen (the picker is blocked until then). */
  districtBlockedHint(kind: HqLegalAddressKind): string | null {
    if (this.addressView(kind).region) return null;
    return this.texts.chooseStateFirst?.() ?? HQ_LEGAL_PROFILE_DEFAULT_TEXTS.chooseStateFirst?.() ?? '';
  }

  /** The label of the city box: "City / district" when it is a picker, "City" when it is typed. */
  cityLabel(kind: HqLegalAddressKind): string {
    if (!this.listsActive(kind)) return this.texts.cityLabel();
    return this.texts.districtLabel?.() ?? HQ_LEGAL_PROFILE_DEFAULT_TEXTS.districtLabel?.() ?? this.texts.cityLabel();
  }

  /** Reads the states again after a failure (the select's retry action). */
  retryStates(kind: HqLegalAddressKind): void {
    this.stateCache.delete(this.addressView(kind).country);
    this.lists[kind] = { ...this.lists[kind], states: [], statesStatus: 'idle' };
    this.loadStates(kind);
  }

  /** Reads the districts again after a failure. */
  retryDistricts(kind: HqLegalAddressKind): void {
    const view = this.addressView(kind);
    this.districtCache.delete(this.districtKey(view.country, view.region ?? ''));
    this.lists[kind] = { ...this.lists[kind], districts: [], districtsStatus: 'idle' };
    this.loadDistricts(kind);
  }

  trackIndex(index: number): number {
    return index;
  }

  private districtKey(country: string, state: string): string {
    return `${country}|${state}`;
  }

  /** Reads what the visible address blocks need: the states of their country and the districts of their state. */
  private loadLists(): void {
    for (const kind of ADDRESS_KINDS) {
      if (kind === 'OPERATING' && !this.hasOperatingAddress) continue;
      if (!this.listsActive(kind)) continue;
      this.loadStates(kind);
      this.loadDistricts(kind);
    }
  }

  private loadStates(kind: HqLegalAddressKind): void {
    const lookup = this.addressLookup;
    const country = this.addressView(kind).country;
    if (!lookup || !country || !lookup.supports(country)) return;
    const cached = this.stateCache.get(country);
    if (cached) {
      if (this.lists[kind].states !== cached) this.lists[kind] = { ...this.lists[kind], states: cached, statesStatus: 'ready' };
      return;
    }
    // A failed read is repeated only by the retry action, not on every edit.
    if (this.lists[kind].statesStatus === 'loading' || this.lists[kind].statesStatus === 'failed') return;
    this.lists[kind] = { ...this.lists[kind], states: [], statesStatus: 'loading' };
    lookup.states(country).pipe(takeUntil(this.destroyed$)).subscribe({
      next: (options) => {
        const items = options.map((o) => ({ value: o.value, label: o.label }));
        this.stateCache.set(country, items);
        if (this.addressView(kind).country === country) this.lists[kind] = { ...this.lists[kind], states: items, statesStatus: 'ready' };
        this.cdr.markForCheck();
      },
      error: () => {
        if (this.addressView(kind).country === country) this.lists[kind] = { ...this.lists[kind], states: [], statesStatus: 'failed' };
        this.cdr.markForCheck();
      },
    });
  }

  private loadDistricts(kind: HqLegalAddressKind): void {
    const lookup = this.addressLookup;
    const view = this.addressView(kind);
    const state = view.region ?? '';
    if (!lookup || !view.country || !state || !lookup.supports(view.country)) {
      if (this.lists[kind].districtsStatus !== 'idle') this.lists[kind] = { ...this.lists[kind], districts: [], districtsStatus: 'idle' };
      return;
    }
    const key = this.districtKey(view.country, state);
    const cached = this.districtCache.get(key);
    if (cached) {
      if (this.lists[kind].districts !== cached) this.lists[kind] = { ...this.lists[kind], districts: cached, districtsStatus: 'ready' };
      return;
    }
    if (this.lists[kind].districtsStatus === 'loading' || this.lists[kind].districtsStatus === 'failed') return;
    this.lists[kind] = { ...this.lists[kind], districts: [], districtsStatus: 'loading' };
    lookup.districts(view.country, state).pipe(takeUntil(this.destroyed$)).subscribe({
      next: (options) => {
        const items = options.map((o) => ({ value: o.value, label: o.label }));
        this.districtCache.set(key, items);
        const now = this.addressView(kind);
        if (this.districtKey(now.country, now.region ?? '') === key) this.lists[kind] = { ...this.lists[kind], districts: items, districtsStatus: 'ready' };
        this.cdr.markForCheck();
      },
      error: () => {
        const now = this.addressView(kind);
        if (this.districtKey(now.country, now.region ?? '') === key) this.lists[kind] = { ...this.lists[kind], districts: [], districtsStatus: 'failed' };
        this.cdr.markForCheck();
      },
    });
  }

  /** The same array while the choices are the same, so the select does not see a new list on every check. */
  private memoItems(key: string, items: HqOption[]): HqOption[] {
    const sig = items.map((i) => `${i.value}=${i.label}`).join('|');
    const hit = this.memo.get(key);
    if (hit && hit.sig === sig) return hit.items;
    this.memo.set(key, { sig, items });
    return items;
  }

  private blankAddress(kind: HqLegalAddressKind): HqLegalAddress {
    const chosen = this.chosenCountry[kind];
    const country = chosen ?? (this.address('REGISTERED')?.country || this.profile.registrationCountry || this.defaultCountry || '');
    return { kind, line1: '', line2: null, city: '', region: null, postalCode: null, country };
  }

  private isBlank(address: HqLegalAddress): boolean {
    return ![address.line1, address.line2, address.city, address.region, address.postalCode].some((v) => (v ?? '').trim());
  }

  private changed(): void {
    this.refresh();
    this.onChange(this.profile);
    this.cdr.markForCheck();
  }

  private refresh(): void {
    this.errors = hqLegalProfileErrors(this.profile);
    this.taxTypes = hqTaxIdTypesFor(this.profile.registrationCountry);
    this.loadLists();
  }
}
