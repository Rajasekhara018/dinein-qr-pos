import type { HqAmount } from 'hq-shared-lib';
import type { PackageLine, PackageReferral } from './franchise-packages.models';
/**
 * One application a franchise has taken: its fee, its agent places and how many of them are used. Money is `{ code, value }`
 * (money stage 3): the currency is inside the fee.
 */
export interface FranchiseApplication {
  applicationCode: string;
  agentLimit: number;
  agentCount: number;
  /** ACTIVE drivers of the franchise for this application (the driver application only; never counted in agentCount). */
  driverCount?: number;
  fee: HqAmount | null;
  /** The place this application of the franchise covers, when one was allocated. */
  locationId?: string | null;
  locationName?: string | null;
  /** The package template the application follows (null: none, it follows the rules that apply); absent on an older answer. */
  packageTemplateId?: string | null;
}

/** A franchise as UAM returns it; the totals are the sum over its applications. */
export interface Franchise {
  unitId: string;
  code: string;
  name: string;
  ownerUserId: string;
  /** The owner's name and email as the list shows them; absent on an older answer. */
  ownerName?: string | null;
  ownerEmail?: string | null;
  /** When the franchise was opened (ISO instant); absent on an older answer. */
  createdAt?: string | null;
  status: string;
  /** ONBOARDING until an administrator activates it (it cannot add agents before); absent on an older answer. */
  onboardingStatus?: 'ONBOARDING' | 'ONBOARDED' | null;
  /**
   * True when the franchise is still ACTIVE although its organization now runs its sales itself (operating model DIRECT
   * today); an administrator should close it. Nothing is closed automatically. Absent on an older answer.
   */
  activeInDirectOrganization?: boolean;
  totalAgentLimit: number;
  totalAgentCount: number;
  applications: FranchiseApplication[];
}

export interface FranchiseAgent {
  employeeId: string;
  userId: string;
  fullName: string;
  email: string;
  phone: string | null;
  applicationCode: string;
}

/** What a franchise takes for one application; the fee is `{ code, value }` (its currency inside it). */
export interface FranchiseApplicationTerms {
  applicationCode: string;
  /** Left out with a template: the agent places of its fee plan are used. */
  agentLimit?: number;
  fee: HqAmount | null;
  /** An active package template of the same application: its fee plan (fee and agent places), commission and referral fill what is not given. */
  templateId?: string;
  /** An explicit commission block; it wins over the template's lines. */
  commission?: { lines: PackageLine[] };
  /** An explicit referral rate; it wins over the template's. */
  referral?: PackageReferral;
  /** The place the franchise covers for this application; only one active franchise may cover a place per application. */
  locationId?: string;
}

/**
 * A franchise plan kept in UAM: the agent places and fee to suggest for an application in a country, state and city.
 * An empty country is the application's general plan; an empty state or city covers the wider area.
 */
export interface FranchisePlan {
  id: string;
  applicationCode: string;
  countryCode: string;
  countryName: string;
  stateCode: string;
  stateName: string;
  city: string;
  /** The district's code in the geography reference list; empty for a whole state or country, or a place typed by hand. */
  districtCode: string;
  agentLimit: number;
  /** `{ code, value }`: the currency is inside the fee. */
  fee: HqAmount | null;
  /** What the plan covers; absent on an older answer (then it follows from the country and place text). */
  scope?: PlanScope;
  locationId?: string | null;
  locationType?: LocationType | null;
  locationLevel?: LocationLevel | null;
  /** Type default only: the type it covers, and the state or district it is under (none: the whole country). */
  scopeType?: LocationType | null;
  scopeLocationId?: string | null;
  /** The path of the plan's place (or, for a type default, of its parent), top first. */
  chain?: PlaceRef[];
}

/** States by country: { IN: [['TG', 'Telangana'], ...] } (ISO 3166-2 code without the country prefix, English name). */
export type Subdivisions = Record<string, [string, string][]>;

/** The districts of one country by state code: { TG: [['540', 'Warangal Rural'], ...] }. */
export type Districts = Record<string, [string, string][]>;

/** A state or district to choose: its code and name. */
export interface PlaceOption {
  code: string;
  name: string;
}

/** What is sent to add or change a plan; the place fields are the new model, the text fields the deprecated first release. */
export type FranchisePlanRequest = Omit<FranchisePlan, 'id' | 'locationType' | 'locationLevel' | 'chain' | OldPlaceField> &
  Partial<Pick<FranchisePlan, OldPlaceField>>;

/** The first release's free-typed place fields (deprecated). */
export type OldPlaceField = 'countryCode' | 'countryName' | 'stateCode' | 'stateName' | 'city' | 'districtCode';

/** The plans to choose from, and the currency used when a plan names none. */
export interface FranchisePlans {
  defaultCurrency: string;
  plans: FranchisePlan[];
  /** ISO codes of the countries plans may be set for; empty means any. */
  countries: string[];
  /** Present when the plans were asked for by page or filter. */
  page?: number;
  size?: number;
  totalElements?: number;
  /** How many times its fee a franchise is given to share between its agents' wallets (2 by default); set by the backend. */
  agentWalletMultiplier?: number;
}

/** Opens a franchise: either an existing owner's id, or the name and email of a new one. */
export interface OpenFranchiseRequest {
  applications: FranchiseApplicationTerms[];
  ownerUserId?: string;
  ownerEmail?: string;
  ownerName?: string;
  ownerPhone?: string;
}

export interface AddFranchiseAgentRequest {
  applicationCode: string;
  fullName: string;
  email: string;
  phone?: string;
}

/** One page of a list from Rider_FranchiseService; {@code page} is zero-based. */
export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}

/** Who a share of a payment goes to. */
export type CommissionParty = 'PLATFORM' | 'OWNER' | 'AGENT' | 'REFERRER' | 'CUSTOMER_POOL' | 'MEMBER_CASHBACK' | 'PROVIDER' | 'TAX';

/** One line of the commission ledger (shadow mode: nothing is paid). A correction is a negative entry naming the one it undoes. */
export interface LedgerEntry {
  id: string;
  franchiseId: string;
  eventId: string;
  eventType: string;
  party: CommissionParty;
  partyRef: string | null;
  amount: HqAmount | null;
  postedAt: string;
  reversesEntryId: string | null;
}

/** One party's total in one currency over the statement's period (the currency is inside the amount). */
export interface StatementLine {
  party: CommissionParty;
  amount: HqAmount | null;
  entries: number;
}

/** Per currency (inside each amount): net is everything booked to the parties, gst what was booked to tax, gross is their sum. */
export interface StatementSummary {
  net: HqAmount | null;
  gst: HqAmount | null;
  gross: HqAmount | null;
}

export interface CommissionStatement {
  franchiseId: string | null;
  fromDay: string | null;
  toDay: string | null;
  lines: StatementLine[];
  summaries: StatementSummary[];
}

/** What narrows the ledger and the statement; days are yyyy-MM-dd (UTC), both included. */
export interface LedgerFilter {
  franchiseId?: string;
  party?: CommissionParty | '';
  from?: string;
  to?: string;
}

export type PayoutStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED';

/** A person's request to be paid from the ledger; approving records a decision only. */
export interface PayoutRequest {
  id: string;
  franchiseId: string;
  party: CommissionParty;
  amount: HqAmount | null;
  feeGross: HqAmount | null;
  payable: HqAmount | null;
  status: PayoutStatus;
  decisionNote: string | null;
  decidedAt: string | null;
  createdAt: string;
}

/** What is sent to ask for a payout; the person is the signed-in caller. The amount carries its currency. */
export interface PayoutRequestBody {
  franchiseId: string;
  party: CommissionParty;
  amount: HqAmount;
  /** The application whose clearing account pays it; optional when the franchise has one. */
  appCode?: string;
  note?: string;
}

/** Whose wallet earnings: the person's own agent earnings, or a franchise they own. */
export type EarningsOwnerType = 'AGENT' | 'FRANCHISE';

/** The payable account (money arrived) or its pending twin (sales whose money has not arrived yet). */
export type EarningsState = 'AVAILABLE' | 'PENDING';

/** One wallet earnings account; available = settled less payouts waiting, pending is not payable yet. */
export interface EarningsAccount {
  ownerType: EarningsOwnerType;
  ownerId: string;
  franchiseCode: string | null;
  settled: HqAmount | null;
  pending: HqAmount | null;
  reserved: HqAmount | null;
  available: HqAmount | null;
}

/** "My earnings" as the wallet holds them. */
export interface MyEarnings {
  accounts: EarningsAccount[];
}

/** One wallet entry of an earnings account. */
export interface EarningsEntry {
  entryId: string;
  journalId: string | null;
  journalType: string | null;
  direction: string | null;
  amount: HqAmount | null;
  appCode: string | null;
  postedAt: string | null;
  description: string | null;
  referenceType: string | null;
  referenceId: string | null;
  journalStatus: string | null;
}

/** Which earnings account and which entries; days are yyyy-MM-dd, both included. */
export interface EarningsFilter {
  account: EarningsOwnerType;
  franchiseId?: string;
  state?: EarningsState;
  from?: string;
  to?: string;
}

/** A payout as the wallet keeps it (statuses PENDING_APPROVAL, APPROVED, PAID, RETURNED, REJECTED). */
export interface WalletPayout {
  payoutId: string;
  ownerType: EarningsOwnerType | null;
  ownerId: string | null;
  appCode: string | null;
  amount: HqAmount | null;
  status: string;
  note: string | null;
  statusReason: string | null;
  bankReference: string | null;
  createdAt: string | null;
  replayed: boolean;
}

/** The status a franchise can be put in; CLOSED is final. */
export type FranchiseStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED';

/** One person who uses a place of a franchise (an ACTIVE member). */
export interface FranchiseMember {
  memberId: string;
  userId: string;
  fullName: string;
  email: string;
  phone: string | null;
  applicationCode: string;
  memberType: string;
  status: string;
  roleCode: string | null;
  onboardedAt: string;
}

/** A driver of a franchise (a DRIVER member), as its owner sees them on "My drivers". */
export interface FranchiseDriver {
  memberId: string;
  userId: string;
  name: string;
  phone: string | null;
  email: string | null;
  onboardedAt: string | null;
}

/** A franchise's places per application and a page of the members that use them (owner only). */
export interface FranchiseMembers {
  franchiseId: string;
  totalAgentLimit: number;
  totalAgentCount: number;
  applications: FranchiseApplication[];
  members: PageResult<FranchiseMember>;
}

// ── Locations (the shared places reference) and plans by place ───────────────

export type LocationLevel = 'REGION' | 'URBAN' | 'LOCAL';
export type LocationType = 'STATE' | 'DISTRICT' | 'CITY' | 'MUNICIPALITY' | 'TOWN' | 'MANDAL' | 'WARD' | 'VILLAGE' | 'PINCODE';
export type PlanScope = 'GENERAL' | 'COUNTRY' | 'PLACE' | 'TYPE_DEFAULT';
export type PlanRule = 'OWN' | 'TYPE_DEFAULT' | 'PARENT' | 'GENERAL';

/** A short reference to a place: one step of a path. */
export interface PlaceRef {
  id: string;
  type: LocationType;
  level: LocationLevel;
  code: string;
  name: string;
}

/** A place as the locations API returns it. */
export interface Location {
  id: string;
  countryCode: string;
  type: LocationType;
  level: LocationLevel;
  levelNumber: number;
  code: string;
  name: string;
  parentId: string | null;
  parent: PlaceRef | null;
  active: boolean;
  sortOrder: number;
  version: number;
  createdAt?: string;
  updatedAt?: string;
}

/** What narrows the locations list; every field is optional. */
export interface LocationQuery {
  countryCode?: string;
  type?: LocationType | '';
  level?: LocationLevel | '';
  parentId?: string;
  active?: boolean | null;
  usable?: boolean;
  q?: string;
  includeInactive?: boolean;
  page?: number;
  size?: number;
}

/** Create sends every field; update sends name, code, parent, active, sortOrder and the version read. */
export interface LocationRequest {
  countryCode?: string;
  type?: LocationType;
  code?: string;
  name?: string;
  parentId?: string;
  active?: boolean;
  sortOrder?: number;
  version?: number;
}

/** Turns a place on or off; the version is optional. */
export interface LocationStatusRequest {
  active: boolean;
  version?: number;
}

/** One problem found in an import; {@code row} counts data rows from 1, {@code line} is the line of the file (header = 1). */
export interface LocationImportError {
  row: number;
  line: number;
  field: string | null;
  message: string;
}

export interface LocationImportResult {
  dryRun: boolean;
  applied: boolean;
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  errorCount: number;
  errorsTruncated: boolean;
  errors: LocationImportError[];
}

/** What narrows the plans list (server paging). */
export interface PlanQuery {
  applicationCode?: string;
  scope?: PlanScope | '';
  locationType?: LocationType | '';
  level?: LocationLevel | '';
  stateId?: string;
  districtId?: string;
  q?: string;
  page?: number;
  size?: number;
}

/** The answer to "which plan applies to this place". */
export interface PlanResolution {
  plan: FranchisePlan;
  rule: PlanRule;
  inherited: boolean;
  source: PlaceRef | null;
  place: PlaceRef[];
}
