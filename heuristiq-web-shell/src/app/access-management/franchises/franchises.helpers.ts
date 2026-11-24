import { HqAmountLike, hqAmountCode, hqAmountOrNull, hqAmountValue } from 'hq-shared-lib';
import {
  AddFranchiseAgentRequest, Franchise, FranchiseApplication, FranchiseStatus, FranchiseApplicationTerms, Districts, FranchisePlan, FranchisePlanRequest, OpenFranchiseRequest, PlaceOption, PlanResolution, Subdivisions,
} from './franchise.models';
import { PackageLineForm, PackageReferralForm, emptyReferralForm, linesError, lineToRequest, referralError, referralToRequest } from './franchise-packages.helpers';
import { PackageTemplate } from './franchise-packages.models';
import {
  COUNTRY_NAME_LANGUAGE, EMPTY_CELL, FALLBACK_CURRENCY, FEE_FRACTION_DIGITS, FRANCHISE_CURRENCIES, LOCATION_BASED_APPLICATIONS, MIN_AGENT_LIMIT, PLACE_NOTE_KEYS,
  TIME_ZONE_COUNTRIES,
} from './franchises.constants';

/** Whether a franchise for this application must cover a place (it is sold by place). */
export function isLocationBased(applicationCode: string | null | undefined): boolean {
  return !!applicationCode && LOCATION_BASED_APPLICATIONS.includes(applicationCode.trim().toUpperCase());
}

/**
 * Translation key of a missing place, or null: a location-based application needs a place, either chosen in the row or already
 * held by the application ({@code currentLocationId}).
 */
export function placeRequiredError(row: TermsRow, currentLocationId?: string | null): string | null {
  return isLocationBased(row.applicationCode) && !row.locationId && !currentLocationId
    ? 'accessManagement.franchises.errors.placeRequired' : null;
}

/** The editable row of one application while a franchise is being opened. */
export interface TermsRow {
  applicationCode: string;
  agentLimit: number | null;
  fee: number | null;
  /** ISO currency of the fee; null until an offer is chosen (then the default currency applies). */
  currency?: string | null;
  /** {@link planKey} of the offer chosen for this row, or '' for terms typed by hand. */
  planKey?: string;
  /** Id of the package template chosen for this row, or '' for none. */
  templateId?: string;
  /** An explicit commission block (wins over the template's lines); only sent when {@link customCommission} is on. */
  customCommission?: boolean;
  commissionLines?: PackageLineForm[];
  /** An explicit referral rate (wins over the template's); only sent when {@link customReferral} is on. */
  customReferral?: boolean;
  referral?: PackageReferralForm;
  /** The place this application covers ('' for none) and its name, for display. */
  locationId?: string;
  locationName?: string;
  /** What the plan lookup said about the place, shown under the picker (null until a place is chosen). */
  placeNote?: PlaceNote | null;
}

/** A translation key and its parameters, saying where a place's agent places and fee came from. */
export interface PlaceNote {
  key: string;
  params: { name: string };
}

/** The form of a new franchise. */
export interface OpenFranchiseForm {
  ownerEmail: string;
  ownerName: string;
  ownerPhone: string;
  rows: TermsRow[];
}

/** The owner field a server refusal is about, with the server's own words, to show right under that field. */
export interface OpenFieldErrors {
  ownerName?: string;
  ownerEmail?: string;
  ownerPhone?: string;
}

/**
 * Which owner field a refusal of the opening names ("Owner phone is required", "Owner email is required", ...). A message that
 * names none of them gives nothing (it stays a toast only). Matching is on the words the franchise service uses for its
 * validation errors, case-insensitively.
 */
export function openFieldErrors(message: string | null | undefined): OpenFieldErrors {
  const text = (message ?? '').trim();
  if (!text) {
    return {};
  }
  if (/owner phone/i.test(text)) {
    return { ownerPhone: text };
  }
  if (/owner email/i.test(text)) {
    return { ownerEmail: text };
  }
  if (/owner name/i.test(text)) {
    return { ownerName: text };
  }
  return {};
}

/** Translation key of the first thing wrong with the form, or null when it can be sent. */
export function openFranchiseError(form: OpenFranchiseForm): string | null {
  if (form.rows.length === 0) {
    return 'accessManagement.franchises.errors.noApplications';
  }
  if (!form.ownerEmail.trim() || !form.ownerName.trim()) {
    return 'accessManagement.franchises.errors.owner';
  }
  const seen = new Set<string>();
  for (const row of form.rows) {
    if (!row.applicationCode || rowLimitInvalid(row) || rowFeeInvalid(row)) {
      return 'accessManagement.franchises.errors.terms';
    }
    const extras = termsExtrasError(row) ?? placeRequiredError(row);
    if (extras) {
      return extras;
    }
    if (seen.has(row.applicationCode)) {
      return 'accessManagement.franchises.errors.duplicate';
    }
    seen.add(row.applicationCode);
  }
  return null;
}

/**
 * The request for a valid form; empty optional fields are left out so UAM does not receive blank strings. A row without a
 * currency of its own is priced in {@code defaultCurrency}.
 */
export function toOpenRequest(form: OpenFranchiseForm, defaultCurrency: string = FALLBACK_CURRENCY): OpenFranchiseRequest {
  const phone = form.ownerPhone.trim();
  return {
    applications: form.rows.map((row) => toTerms(row, defaultCurrency)),
    ownerEmail: form.ownerEmail.trim(),
    ownerName: form.ownerName.trim(),
    ...(phone ? { ownerPhone: phone } : {}),
  };
}

/** The terms of a row as the API takes them: the fee (and any fixed commission or referral amount) as `{ code, value }`. */
export function toTerms(row: TermsRow, defaultCurrency: string = FALLBACK_CURRENCY): FranchiseApplicationTerms {
  const currency = row.currency || defaultCurrency;
  return {
    applicationCode: row.applicationCode,
    ...(row.agentLimit !== null && row.agentLimit !== undefined ? { agentLimit: row.agentLimit } : {}),
    fee: hqAmountOrNull(roundFee(row.fee), currency),
    ...(row.locationId ? { locationId: row.locationId } : {}),
    ...(row.templateId ? { templateId: row.templateId } : {}),
    ...(row.customCommission && row.commissionLines && row.commissionLines.length > 0
      ? { commission: { lines: row.commissionLines.map((line) => lineToRequest(line, currency)) } } : {}),
    ...(row.customReferral && row.referral ? { referral: referralToRequest(row.referral, currency) } : {}),
  };
}

/** Places still free for an application (never negative). */
export function placesLeft(application: FranchiseApplication): number {
  return Math.max(0, application.agentLimit - application.agentCount);
}

/** The applications of a franchise that still have a place free: the ones an agent can be added for. */
export function applicationsWithRoom(franchise: Franchise | null): FranchiseApplication[] {
  return (franchise?.applications ?? []).filter((application) => placesLeft(application) > 0);
}

/** Share of the agent places used, 0 to 100, for the usage bar. */
/** How many franchises are still active although the organization is now DIRECT (the list's close-them notice). */
export function activeInDirectCount(franchises: readonly Franchise[] | null | undefined): number {
  return (franchises ?? []).filter((franchise) => franchise.activeInDirectOrganization === true).length;
}

export function usagePercent(used: number, limit: number): number {
  return limit <= 0 ? 0 : Math.min(100, Math.round((used / limit) * 100));
}

/** Translation key of what is wrong with a new agent, or null when it can be sent. */
export function addAgentError(request: AddFranchiseAgentRequest): string | null {
  if (!request.applicationCode) {
    return 'accessManagement.franchises.errors.agentApplication';
  }
  if (!request.fullName.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(request.email.trim())) {
    return 'accessManagement.franchises.errors.agent';
  }
  return null;
}

/** The request for a valid agent form, trimmed and without a blank phone. */
export function toAgentRequest(request: AddFranchiseAgentRequest): AddFranchiseAgentRequest {
  const phone = request.phone?.trim();
  return {
    applicationCode: request.applicationCode,
    fullName: request.fullName.trim(),
    email: request.email.trim(),
    ...(phone ? { phone } : {}),
  };
}

/** A blank application row. */
export function emptyTermsRow(): TermsRow {
  return {
    applicationCode: '', agentLimit: null, fee: null, currency: null, planKey: '', templateId: '',
    customCommission: false, commissionLines: [], customReferral: false, referral: emptyReferralForm(),
  };
}

/** A blank new-franchise form with one application row to fill in. */
export function emptyOpenForm(): OpenFranchiseForm {
  return { ownerEmail: '', ownerName: '', ownerPhone: '', rows: [emptyTermsRow()] };
}

/** A blank new-agent form. */
export function emptyAgentRequest(): AddFranchiseAgentRequest {
  return { applicationCode: '', fullName: '', email: '', phone: '' };
}

// ── Money ────────────────────────────────────────────────────────────────────

/** A fee rounded to two decimals (what is shown and kept), or null while nothing is typed. */
export function roundFee(fee: number): number;
export function roundFee(fee: number | null): number | null;
export function roundFee(fee: number | null): number | null {
  if (fee === null || fee === undefined || Number.isNaN(fee)) {
    return null;
  }
  return Math.round((fee + Number.EPSILON) * 10 ** FEE_FRACTION_DIGITS) / 10 ** FEE_FRACTION_DIGITS;
}

/** A fee as money in its currency, with the currency's symbol and two decimals, grouped the way the user's locale does. */
export function formatMoney(amount: number | null, currency: string, locale?: string): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return '';
  }
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency', currency, minimumFractionDigits: FEE_FRACTION_DIGITS, maximumFractionDigits: FEE_FRACTION_DIGITS,
    }).format(amount);
  } catch {
    return `${amount.toFixed(FEE_FRACTION_DIGITS)} ${currency}`;
  }
}

/**
 * An amount from the API (`{ code, value }`, or an older bare number in {@code fallbackCurrency}) as money text; '' when there is
 * no value.
 */
export function moneyText(amount: HqAmountLike, fallbackCurrency: string, locale?: string): string {
  return formatMoney(hqAmountValue(amount), hqAmountCode(amount, fallbackCurrency), locale);
}

/** The symbol of a currency (₹ for INR, $ for USD), or its code when the browser knows no symbol. */
export function currencySymbol(currency: string, locale?: string): string {
  try {
    const symbol = new Intl.NumberFormat(locale, { style: 'currency', currency }).formatToParts(0).find((part) => part.type === 'currency');
    return symbol?.value ?? currency;
  } catch {
    return currency;
  }
}

// ── Preconfigured offers ─────────────────────────────────────────────────────

/** Identity of a plan: its id. */
export function planKey(plan: FranchisePlan): string {
  return plan.id;
}

/** "India - Telangana - Hyderabad", "India - Telangana", "India", or "General" for a plan with no country. */
export function planRegion(plan: FranchisePlan, general: string): string {
  const parts = [plan.countryName || plan.countryCode, plan.stateName || plan.stateCode, plan.city].filter((part) => !!part);
  return parts.length > 0 ? parts.join(' - ') : general;
}

/**
 * The country (ISO 3166-1 alpha-2) a time zone belongs to, looked for among {@code candidates} (the countries that have
 * offers). The browser's own table is used where it exists, else a short built-in list; null when it is not known.
 */
export function countryFromTimeZone(timeZone: string | undefined, candidates: readonly string[]): string | null {
  if (!timeZone) {
    return null;
  }
  for (const country of candidates) {
    try {
      const locale = new Intl.Locale(`und-${country}`) as Intl.Locale & { getTimeZones?: () => string[] };
      if (locale.getTimeZones?.().includes(timeZone)) {
        return country;
      }
    } catch {
      // not a region this browser knows: try the next one
    }
  }
  const known = TIME_ZONE_COUNTRIES[timeZone];
  return known && candidates.includes(known) ? known : null;
}

/**
 * The plans of one application, the ones for {@code country} first (the whole country's plan, then its states', then its
 * cities'; the time zone tells the country only), then the application's general plan, then the rest. Within a group by
 * country, state and city name. Nothing is hidden: any plan can be chosen.
 */
export function plansForApplication(plans: readonly FranchisePlan[], applicationCode: string, country: string | null): FranchisePlan[] {
  const rank = (plan: FranchisePlan): number => {
    if (country && plan.countryCode === country) {
      return plan.city ? 2 : plan.stateCode ? 1 : 0;
    }
    return plan.countryCode ? 4 : 3;
  };
  return plans
    .filter((plan) => plan.applicationCode === applicationCode)
    .sort((a, b) => rank(a) - rank(b)
      || (a.countryName || a.countryCode).localeCompare(b.countryName || b.countryCode)
      || (a.stateName || a.stateCode).localeCompare(b.stateName || b.stateCode)
      || a.city.localeCompare(b.city));
}

/** The row with an offer's places, fee and currency filled in (the row keeps being editable by hand). */
export function applyPlan(row: TermsRow, plan: FranchisePlan): TermsRow {
  return { ...row, agentLimit: plan.agentLimit, fee: roundFee(hqAmountValue(plan.fee)), currency: hqAmountCode(plan.fee) || row.currency || null, planKey: planKey(plan) };
}

/** The distinct countries that have an offer, to match the user's time zone against. */
export function offerCountries(plans: readonly FranchisePlan[]): string[] {
  return [...new Set(plans.map((plan) => plan.countryCode).filter((code) => !!code))];
}

// ── The franchise plan form ──────────────────────────────────────────────────

/** The editable plan while it is being added or changed. */
export interface PlanForm {
  applicationCode: string;
  countryCode: string;
  stateCode: string;
  stateName: string;
  city: string;
  districtCode: string;
  agentLimit: number | null;
  fee: number | null;
  currency: string;
}

export function emptyPlanForm(currency: string): PlanForm {
  return { applicationCode: '', countryCode: '', stateCode: '', stateName: '', city: '', districtCode: '', agentLimit: null, fee: null, currency };
}

/** The form of an existing plan, to change it; an older bare-number fee is read in {@code fallbackCurrency}. */
export function planToForm(plan: FranchisePlan, fallbackCurrency: string = FALLBACK_CURRENCY): PlanForm {
  return {
    applicationCode: plan.applicationCode, countryCode: plan.countryCode, stateCode: plan.stateCode, stateName: plan.stateName,
    city: plan.city, districtCode: plan.districtCode ?? '', agentLimit: plan.agentLimit, fee: hqAmountValue(plan.fee),
    currency: hqAmountCode(plan.fee, fallbackCurrency),
  };
}

/** Translation key of the first thing wrong with the plan, or null when it can be sent. */
export function planFormError(form: PlanForm): string | null {
  if (!form.applicationCode) {
    return 'accessManagement.franchisePlans.errors.application';
  }
  if (form.agentLimit === null || form.agentLimit < MIN_AGENT_LIMIT || form.fee === null || form.fee < 0) {
    return 'accessManagement.franchises.errors.terms';
  }
  if (!form.countryCode && (form.stateCode.trim() || form.stateName.trim() || form.city.trim())) {
    return 'accessManagement.franchisePlans.errors.area';
  }
  return null;
}

/** The request for a valid plan; the country's name comes from {@code countries} (its code is what is stored as the key). */
export function toPlanRequest(form: PlanForm, countries: readonly CountryOption[]): FranchisePlanRequest {
  const country = countries.find((option) => option.code === form.countryCode);
  return {
    applicationCode: form.applicationCode,
    countryCode: form.countryCode,
    countryName: country?.name ?? '',
    stateCode: form.stateCode.trim().toUpperCase(),
    stateName: form.stateName.trim(),
    city: form.city.trim(),
    districtCode: form.city.trim() ? form.districtCode : '',
    agentLimit: form.agentLimit as number,
    fee: hqAmountOrNull(roundFee(form.fee), form.currency),
  };
}

export interface CountryOption {
  code: string;
  name: string;
}

/**
 * Every country the browser has a name for, by name. Region codes the browser does not know come back unchanged from
 * Intl.DisplayNames and are left out, so only real countries are offered.
 */
export function countryOptions(language: string = COUNTRY_NAME_LANGUAGE): CountryOption[] {
  let names: Intl.DisplayNames;
  try {
    names = new Intl.DisplayNames([language], { type: 'region' });
  } catch {
    return [];
  }
  const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  const options: CountryOption[] = [];
  for (const first of letters) {
    for (const second of letters) {
      const code = first + second;
      const name = names.of(code);
      if (name && name !== code) {
        options.push({ code, name });
      }
    }
  }
  return options.sort((a, b) => a.name.localeCompare(b.name));
}

/** The currencies a franchise may be priced in, plus the one a plan already carries so editing an older plan never loses it. */
export function currencyOptions(current?: string | null): string[] {
  const codes = [...FRANCHISE_CURRENCIES];
  return current && !codes.includes(current) ? [...codes, current] : codes;
}

/**
 * The states to choose for a country, by name. A state the plan already carries that is not in the list (typed before the
 * list existed, or newer than it) is kept as an option so editing a plan never loses it. Empty when the country has no list.
 */
export function stateOptions(subdivisions: Subdivisions, country: string, current?: { code: string; name: string }): PlaceOption[] {
  const listed = (subdivisions[country] ?? []).map(([code, name]) => ({ code, name }));
  if (listed.length === 0) {
    return [];
  }
  if (current?.code && !listed.some((state) => state.code === current.code.toUpperCase())) {
    return [...listed, { code: current.code.toUpperCase(), name: current.name || current.code }]
      .sort((a, b) => a.name.localeCompare(b.name));
  }
  return listed;
}

/** The districts of a state, by name; empty when the state (or the country) has no list, so the district is typed instead. */
export function districtOptions(districts: Districts, state: string): PlaceOption[] {
  return (districts[state] ?? []).map(([code, name]) => ({ code, name }));
}

/** Translation key of what is wrong with a row's explicit commission or referral block, or null. */
export function termsExtrasError(row: TermsRow): string | null {
  if (row.customCommission) {
    if (!row.commissionLines || row.commissionLines.length === 0) { return 'accessManagement.franchisePackages.errors.noLines'; }
    const error = linesError(row.commissionLines);
    if (error) { return error; }
  }
  return row.customReferral && row.referral ? referralError(row.referral) : null;
}

/**
 * The row after a package template is chosen. The franchise is linked to the package's plans, so when the package picks a fee plan
 * the typed fee is cleared (a blank fee lets the server take it from that plan); a fee typed afterwards still wins over it. A
 * package no longer carries agent places: they come from its fee plan (or, for a package for one type of place, from the franchise
 * plan of the place), so the typed places are cleared and the server fills them in; places typed afterwards still win over it. A
 * package for one type of place keeps what the row holds, which is filled in when the place is picked.
 */
export function applyTemplate(row: TermsRow, template: PackageTemplate | undefined): TermsRow {
  // A package picks the commission and referral plans; the agent places, fee and offer already in the row (typed, or from the
  // place's plan) stay as they are. A blank fee or limit is still filled by the server from the package's fee plan.
  return { ...row, templateId: template?.id ?? '' };
}

/**
 * Whether a row's agent places are missing or wrong. A row opened from a package may leave them blank: the server then takes them
 * from the package's fee plan.
 */
export function rowLimitInvalid(row: TermsRow): boolean {
  if (row.agentLimit === null || row.agentLimit === undefined) { return !row.templateId; }
  return row.agentLimit < MIN_AGENT_LIMIT;
}

/**
 * Whether a row's fee is missing or wrong. A row opened from a package may leave the fee blank: the server then takes it from
 * the package's fee plan, or from the fee schedule that applies.
 */
export function rowFeeInvalid(row: TermsRow): boolean {
  if (row.fee === null || row.fee === undefined) { return !row.templateId; }
  return row.fee < 0;
}

/**
 * The agent places, fee and currency of the plan that applies to a place; the row's other fields stay. The offer list then
 * shows that plan when it is one of {@code offered}, else it falls back to "custom" (the filled terms stay editable).
 */
export function applyResolution(row: TermsRow, resolution: PlanResolution, offered: readonly FranchisePlan[] = []): TermsRow {
  const plan = resolution.plan;
  const key = plan.id && offered.some((p) => planKey(p) === plan.id) ? plan.id : '';
  return { ...row, agentLimit: plan.agentLimit, fee: roundFee(hqAmountValue(plan.fee)), currency: hqAmountCode(plan.fee) || row.currency || null, planKey: key };
}

/** Where the plan that applies to a place comes from: the place's own, one inherited from a place above it, or the country's. */
export function placeNote(resolution: PlanResolution): PlaceNote {
  const name = resolution.source?.name ?? '';
  if (!resolution.inherited) {
    return { key: PLACE_NOTE_KEYS.own, params: { name } };
  }
  if (resolution.source) {
    return { key: PLACE_NOTE_KEYS.inheritedFrom, params: { name } };
  }
  return { key: PLACE_NOTE_KEYS.inheritedCountry, params: { name } };
}

/** The note for a place that no plan covers: the person types the terms. */
export function noPlanNote(): PlaceNote {
  return { key: PLACE_NOTE_KEYS.none, params: { name: '' } };
}

/** What the searchable pickers show for a registered application: "Name (CODE)", or just the code when it has no name. */
export function applicationPickLabel(application: { code: string; name?: string | null }): string {
  return application.name ? `${application.name} (${application.code})` : application.code;
}

/** The code of the application whose pick label is {@code label}, or '' when none matches (the box was cleared). */
export function applicationCodeForLabel(applications: ReadonlyArray<{ code: string; name?: string | null }>, label: string): string {
  return applications.find((application) => applicationPickLabel(application) === label)?.code ?? '';
}

// ── The franchise list and its status ───────────────────────────────────────

/** The owner as the list shows them: the name, else the email, else a dash. */
export function ownerLabel(franchise: Pick<Franchise, 'ownerName' | 'ownerEmail'>): string {
  return franchise.ownerName?.trim() || franchise.ownerEmail?.trim() || EMPTY_CELL;
}

/** The places the franchise covers (one per application, each name once), or a dash when none was allocated. */
export function placeLabel(franchise: Pick<Franchise, 'applications'>): string {
  const names = (franchise.applications ?? []).map((application) => application.locationName?.trim() ?? '').filter((name) => name !== '');
  return names.length > 0 ? [...new Set(names)].join(', ') : EMPTY_CELL;
}

/** The statuses an administrator can move a franchise to from {@code status}: a closed one stays closed. */
export function nextStatuses(status: string): FranchiseStatus[] {
  switch (status) {
    case 'ACTIVE': return ['SUSPENDED', 'CLOSED'];
    case 'SUSPENDED': return ['ACTIVE', 'CLOSED'];
    default: return [];
  }
}

/** The place type of an offer (its place's type, or the type a type default covers), or null for a country or general plan. */
export function planPlaceType(plan: Pick<FranchisePlan, 'locationType' | 'scopeType'>): string | null {
  return plan.locationType ?? plan.scopeType ?? null;
}
