import { OrganizationChoice } from '../models/uam.models';
import { AGENT_FRANCHISE_IDS_CLAIM, APPS_CLAIM, FRANCHISE_IDS_CLAIM, ORG_WIDE_CLAIM, OWNED_FRANCHISE_IDS_CLAIM, TENANT_CLAIM, VIEW_ALL_ORGANIZATIONS_HEADER } from './organization-context.constants';

/** The claims of an access token, or null when the token is missing or unreadable. */
function claimsOf(token: string | null): Record<string, unknown> | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    return null;
  }
}

/** The organization id stored in an access token, or null when the token is missing or unreadable. */
export function tenantIdFromToken(token: string | null): string | null {
  const value = claimsOf(token)?.[TENANT_CLAIM];
  return typeof value === 'string' && value ? value : null;
}

/** True when the access token marks an organization-wide caller, the only kind who may switch or add organizations. */
export function isOrgWideFromToken(token: string | null): boolean {
  return claimsOf(token)?.[ORG_WIDE_CLAIM] === true;
}

/** The application codes the access token grants the account (upper case); empty when the token is missing or has none. */
export function applicationsFromToken(token: string | null): string[] {
  const value = claimsOf(token)?.[APPS_CLAIM];
  return Array.isArray(value) ? value.filter((code): code is string => typeof code === 'string').map((code) => code.toUpperCase()) : [];
}

function idsClaim(token: string | null, claim: string): string[] {
  const value = claimsOf(token)?.[claim];
  return Array.isArray(value) ? value.filter((id): id is string => typeof id === 'string' && !!id) : [];
}

/** The ids of the franchises the access token says the account owns or works for; empty when the token is missing, unreadable or has none. */
export function franchiseIdsFromToken(token: string | null): string[] {
  return idsClaim(token, FRANCHISE_IDS_CLAIM);
}

/** The franchises the account owns, from the token; empty when none (or when the session was issued before the claim existed). */
export function ownedFranchiseIdsFromToken(token: string | null): string[] {
  return idsClaim(token, OWNED_FRANCHISE_IDS_CLAIM);
}

/** The franchises where the account is an active agent, from the token; empty when none (or when the session was issued before the claim existed). */
export function agentFranchiseIdsFromToken(token: string | null): string[] {
  return idsClaim(token, AGENT_FRANCHISE_IDS_CLAIM);
}

/** The organization the session works in, picked from the account's list by the token's organization id. */
export function currentOrganization(list: OrganizationChoice[], tenantId: string | null): OrganizationChoice | null {
  return list.find((o) => o.tenantId === tenantId) ?? null;
}

/**
 * True while an organization-wide person has chosen "All organizations": lists then show every organization's records. The choice is
 * ignored for anyone else (their token is not organization-wide), so a stale value left in storage widens nothing.
 */
export function isViewingAllOrganizations(token: string | null, stored: string | null): boolean {
  return stored === 'true' && isOrgWideFromToken(token);
}

/** Path of the search service, whose POSTs are queries (list screens call its `/elastic/{model}/getall`), never changes. */
const SEARCH_PATH = '/api/v1/search/';
const SEARCH_INTERNAL_PATH = '/api/v1/search/internal/';

/** True for a request that only reads: any GET, or a query POSTed to the search service. */
export function isReadRequest(method: string, url: string): boolean {
  const verb = method.toUpperCase();
  return verb === 'GET' || (verb === 'POST' && url.includes(SEARCH_PATH) && !url.includes(SEARCH_INTERNAL_PATH));
}

/**
 * The header asking for every organization's records, for a read by an organization-wide person who chose "All organizations".
 * Changes are never sent with it: they always apply to the session's own organization.
 */
export function viewAllOrganizationsHeaders(method: string, url: string, token: string | null, stored: string | null): Record<string, string> {
  return isReadRequest(method, url) && isViewingAllOrganizations(token, stored) ? { [VIEW_ALL_ORGANIZATIONS_HEADER]: 'true' } : {};
}
