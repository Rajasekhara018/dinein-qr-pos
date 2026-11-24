/** The kinds of rule the franchise setup shows per application, as the franchise service's rule resolver names them. */
export type RuleInForceKind = 'COMMISSION_PLAN' | 'REFERRAL_PLAN';
export const RULE_IN_FORCE_KINDS: readonly RuleInForceKind[] = ['COMMISSION_PLAN', 'REFERRAL_PLAN'];

/** The rule resolver's path under the franchise API, and the privilege of its call (privilege.json). */
export const RULE_RESOLVE_PATH = '/rules/resolve';
/** The event type catalogue's path under the franchise API. */
export const EVENT_TYPES_PATH = '/event-types';
export const RULE_RESOLVE_PRIVILEGE = 'FRANCHISE_RULE_RESOLVE_READ';

/** Translation namespace of the "commission and referral in force" column. */
export const RULES_IN_FORCE_I18N = 'accessManagement.franchises.rulesInForce';

/** The scope levels a rule can come from, mapped to the translation key of its source (anything else is an organization rule). */
export const RULE_SOURCE_KEYS: Readonly<Record<string, string>> = {
  FRANCHISE: `${RULES_IN_FORCE_I18N}.sources.franchise`,
  PLAN: `${RULES_IN_FORCE_I18N}.sources.plan`,
};
export const RULE_SOURCE_FALLBACK_KEY = `${RULES_IN_FORCE_I18N}.sources.organization`;
/** The source shown when no rule for every event is in force and the application's rules by event are listed instead. */
export const RULE_SOURCE_BY_EVENT_KEY = `${RULES_IN_FORCE_I18N}.sources.byEvent`;
/** The preview kind of each resolver kind. */
export const RULE_PREVIEW_KIND = { COMMISSION_PLAN: 'commission', REFERRAL_PLAN: 'referral' } as const;

/** The HTTP status of "no rule of that kind is in force". */
export const RULE_NOT_IN_FORCE_STATUS = 404;
