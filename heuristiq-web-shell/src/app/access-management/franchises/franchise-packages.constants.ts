import { NamedPlanKind, PackageValueType } from './franchise-packages.models';
import { CommissionParty } from './franchise.models';

/** Screen permission key and audit screen key (never renamed once released). */
export const FRANCHISE_PACKAGES_PERMISSION = 'view_franchise_package_templates';
export const FRANCHISE_PACKAGES_SCREEN_KEY = 'franchise.package_templates';
/** The screen key of the page that adds or changes a package template. */
export const FRANCHISE_PACKAGE_FORM_SCREEN_KEY = 'franchise.package_template_form';

/**
 * Exact privilege names (privilege.json) behind the package screen, the plan pickers of the package form and the commission /
 * referral editors of the open form.
 */
export const PACKAGE_PRIVILEGES = {
  list: 'FRANCHISE_PACKAGE_TEMPLATE_LIST',
  read: 'FRANCHISE_PACKAGE_TEMPLATE_READ',
  create: 'FRANCHISE_PACKAGE_TEMPLATE_CREATE',
  update: 'FRANCHISE_PACKAGE_TEMPLATE_UPDATE',
  commissionCreate: 'COMMISSION_PLAN_CREATE',
  referralCreate: 'REFERRAL_PLAN_CREATE',
  feePlanList: 'FRANCHISE_FEE_SCHEDULE_LIST',
  commissionPlanList: 'COMMISSION_PLAN_LIST',
  referralPlanList: 'REFERRAL_PLAN_LIST',
} as const;

export const PACKAGES_PAGE_SIZE = 20;
export const PACKAGE_NAME_MAX = 120;
export const PACKAGE_DESCRIPTION_MAX = 500;
export const PACKAGE_MAX_LINES = 20;
export const MAX_BASIS_POINTS = 10000;

export const PACKAGE_VALUE_TYPES: readonly PackageValueType[] = ['PERCENTAGE', 'FIXED'];
/** Parties a commission line may name (TAX is never allowed on a plan). */
export const PACKAGE_PARTIES: readonly CommissionParty[] = ['PLATFORM', 'OWNER', 'AGENT', 'REFERRER', 'CUSTOMER_POOL', 'MEMBER_CASHBACK', 'PROVIDER'];

const NS = 'accessManagement.franchisePackages';
export const GST_LABEL_KEYS: Readonly<Record<string, string>> = {
  INCLUSIVE: `${NS}.gst.INCLUSIVE`, EXCLUSIVE: `${NS}.gst.EXCLUSIVE`, NOT_APPLICABLE: `${NS}.gst.NOT_APPLICABLE`,
};
export const VALUE_TYPE_LABEL_KEYS: Readonly<Record<string, string>> = {
  PERCENTAGE: `${NS}.valueTypes.PERCENTAGE`, FIXED: `${NS}.valueTypes.FIXED`,
};

// ── named plans a package picks ───────────────────────────────────────────

/** The plan kinds a package picks, in the order the form and the list show them. A package never picks a fee plan: the joining fee
 * and agent places always come from the franchise plan of the franchise's place (Franchise plans). */
export const NAMED_PLAN_KINDS: readonly NamedPlanKind[] = ['commission', 'referral'];
/** The scope level of a named, reusable plan (backend code, never translated). */
export const NAMED_PLAN_SCOPE_LEVEL = 'PLAN';
/** How many plan rows (all versions) one picker asks for. */
export const NAMED_PLAN_OPTIONS_LIMIT = 100;
/** The levels whose plans apply to every franchise of an application when its package picks no plan (most general last when sorted). */
export const APPLIED_RULE_LEVELS: readonly string[] = ['VERTICAL', 'EVENT_TYPE_VERTICAL', 'PLACE_TYPE_VERTICAL'];

/** The list endpoint of each kind, under the franchise API base. */
export const NAMED_PLAN_PATHS: Readonly<Record<NamedPlanKind, string>> = {
  fee: '/fee-schedules', commission: '/commission-plans', referral: '/referral-plans',
};
/** The privilege of each kind's list call: without it the picker is shown disabled. */
export const NAMED_PLAN_LIST_PRIVILEGES: Readonly<Record<NamedPlanKind, string>> = {
  fee: PACKAGE_PRIVILEGES.feePlanList, commission: PACKAGE_PRIVILEGES.commissionPlanList, referral: PACKAGE_PRIVILEGES.referralPlanList,
};
/** The screen where named plans of each kind are made (scope "Named plan"). */
export const NAMED_PLAN_SCREEN_PATHS: Readonly<Record<NamedPlanKind, string>> = {
  fee: '/admin/franchise-admin/fee-schedule',
  commission: '/admin/franchise-admin/commission-plans',
  referral: '/admin/franchise-admin/referral-plans',
};
/** The menu name of each of those screens (existing nav keys). */
export const NAMED_PLAN_SCREEN_LABEL_KEYS: Readonly<Record<NamedPlanKind, string>> = {
  fee: 'nav.items.franchiseFeeSchedule',
  commission: 'nav.items.franchiseCommissionPlans',
  referral: 'nav.items.franchiseReferralPlans',
};
/** Translation keys of each picker's label and hint. */
export const NAMED_PLAN_LABEL_KEYS: Readonly<Record<NamedPlanKind, string>> = {
  fee: `${NS}.plans.fee`, commission: `${NS}.plans.commission`, referral: `${NS}.plans.referral`,
};
export const NAMED_PLAN_HINT_KEYS: Readonly<Record<NamedPlanKind, string>> = {
  fee: `${NS}.plans.feeHint`, commission: `${NS}.plans.commissionHint`, referral: `${NS}.plans.referralHint`,
};
/** The text of the "no pick" choice and of a package that picked nothing. */
export const NAMED_PLAN_FOLLOW_RULES_KEY = `${NS}.plans.followRules`;
export const NAMED_PLAN_RULES_APPLY_KEY = `${NS}.plans.rulesApply`;
export const NAMED_PLAN_NO_ACCESS_KEY = `${NS}.plans.noAccess`;
