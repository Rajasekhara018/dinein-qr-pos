/** Header that names the organization a sign-in or refresh should work in (its code or id). */
export const ORGANIZATION_HEADER = 'X-Organization-Code';

/** Claim of the access token that carries the organization the session works in. */
export const TENANT_CLAIM = 'tenantId';

/** Claim of the access token that marks an organization-wide caller: the only one who may switch organizations. */
export const ORG_WIDE_CLAIM = 'orgWide';

export const MY_ORGANIZATIONS_PATH = '/identity/me/organizations';

/** Claim of the access token that lists the codes of the applications the account may open. */
export const APPS_CLAIM = 'apps';

/** Access-token claim listing the active franchises the account owns or works for (UAM asks the franchise service); empty for everyone else. */
export const FRANCHISE_IDS_CLAIM = 'franchiseIds';

/**
 * Screen permission keys of the franchise partner's own pages (my franchises, my earnings, my payouts, my float). They are about the
 * signed-in person's own franchise business, so they only open for someone who owns or works for a franchise: an operations administrator
 * of the whole system holds the privileges but has no franchise of their own, and uses the franchise ledger, payout approvals and
 * partner floats screens to oversee everyone's instead.
 */
export const FRANCHISE_PARTNER_PERMISSIONS: readonly string[] = ['view_my_franchises', 'view_my_earnings', 'view_my_payouts', 'view_my_float'];

/** Access-token claim: the franchises the account owns (a subset of {@link FRANCHISE_IDS_CLAIM}); absent when none. */
export const OWNED_FRANCHISE_IDS_CLAIM = 'ownedFranchiseIds';

/** Access-token claim: the franchises where the account is an active agent (a subset of {@link FRANCHISE_IDS_CLAIM}); absent when none. */
export const AGENT_FRANCHISE_IDS_CLAIM = 'agentFranchiseIds';

/**
 * Request header asking the services to look across every organization instead of the session's one. The services only honour
 * it for an organization-wide caller (the gateway vouches for that from the token), so sending it changes nothing for anyone else.
 */
export const VIEW_ALL_ORGANIZATIONS_HEADER = 'X-View-All-Organizations';

/** Browser storage key remembering that an organization-wide person chose "All organizations" in the header. */
export const VIEW_ALL_ORGANIZATIONS_STORAGE_KEY = 'view_all_organizations';
