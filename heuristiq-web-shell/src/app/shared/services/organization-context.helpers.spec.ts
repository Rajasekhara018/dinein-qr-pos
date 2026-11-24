import { describe, expect, it } from 'vitest';
import { OrganizationChoice } from '../models/uam.models';
import {
  agentFranchiseIdsFromToken, applicationsFromToken, currentOrganization, franchiseIdsFromToken, isOrgWideFromToken, ownedFranchiseIdsFromToken,
  tenantIdFromToken, isViewingAllOrganizations, viewAllOrganizationsHeaders,
} from './organization-context.helpers';

const tokenWith = (claims: object): string => `h.${btoa(JSON.stringify(claims))}.s`;

describe('organization context helpers', () => {
  it('reads the organization from the token', () => {
    expect(tenantIdFromToken(tokenWith({ tenantId: 'acme' }))).toBe('acme');
  });

  it('gives null for a missing, malformed or organization-less token', () => {
    expect(tenantIdFromToken(null)).toBeNull();
    expect(tenantIdFromToken('garbage')).toBeNull();
    expect(tenantIdFromToken(tokenWith({ sub: 'u1' }))).toBeNull();
  });

  it('treats only a token with the orgWide claim set to true as organization-wide', () => {
    expect(isOrgWideFromToken(tokenWith({ tenantId: 'acme', orgWide: true }))).toBe(true);
    expect(isOrgWideFromToken(tokenWith({ tenantId: 'acme', orgWide: false }))).toBe(false);
    expect(isOrgWideFromToken(tokenWith({ tenantId: 'acme', orgWide: 'true' }))).toBe(false);
    expect(isOrgWideFromToken(tokenWith({ tenantId: 'acme' }))).toBe(false);
    expect(isOrgWideFromToken(null)).toBe(false);
    expect(isOrgWideFromToken('garbage')).toBe(false);
  });

  it('picks the current organization from the list', () => {
    const list: OrganizationChoice[] = [
      { tenantId: 'default', code: 'DEFAULT', name: 'Default', status: 'ACTIVE' },
      { tenantId: 'acme', code: 'ACME', name: 'Acme', status: 'ACTIVE' },
    ];
    expect(currentOrganization(list, 'acme')?.name).toBe('Acme');
    expect(currentOrganization(list, 'other')).toBeNull();
  });

  it('reads the granted application codes from the token, upper-cased, ignoring non-text entries', () => {
    expect(applicationsFromToken(tokenWith({ apps: ['food_deliver_app', 'BEST_PURCHASE_APP', 7] }))).toEqual(['FOOD_DELIVER_APP', 'BEST_PURCHASE_APP']);
    expect(applicationsFromToken(tokenWith({ apps: 'nope' }))).toEqual([]);
    expect(applicationsFromToken(tokenWith({ sub: 'u1' }))).toEqual([]);
    expect(applicationsFromToken(null)).toEqual([]);
  });

  it('reads the franchises the token says the account owns or works for', () => {
    expect(franchiseIdsFromToken(tokenWith({ franchiseIds: ['f1', 'f2'] }))).toEqual(['f1', 'f2']);
    expect(franchiseIdsFromToken(tokenWith({ franchiseIds: ['f1', 7, ''] }))).toEqual(['f1']);
  });

  it('gives no franchises for an administrator with none, a missing or a malformed token', () => {
    expect(franchiseIdsFromToken(tokenWith({ franchiseIds: [] }))).toEqual([]);
    expect(franchiseIdsFromToken(tokenWith({ franchiseIds: 'f1' }))).toEqual([]);
    expect(franchiseIdsFromToken(tokenWith({ sub: 'u1' }))).toEqual([]);
    expect(franchiseIdsFromToken(null)).toEqual([]);
    expect(franchiseIdsFromToken('garbage')).toEqual([]);
  });

  it('tells the franchises an account owns from the ones it works in as an agent', () => {
    const token = tokenWith({ franchiseIds: ['f1', 'f2'], ownedFranchiseIds: ['f1'], agentFranchiseIds: ['f2'] });
    expect(ownedFranchiseIdsFromToken(token)).toEqual(['f1']);
    expect(agentFranchiseIdsFromToken(token)).toEqual(['f2']);
  });

  it('gives neither list for a token without the claims', () => {
    const old = tokenWith({ franchiseIds: ['f1'] });
    expect(ownedFranchiseIdsFromToken(old)).toEqual([]);
    expect(agentFranchiseIdsFromToken(old)).toEqual([]);
    expect(ownedFranchiseIdsFromToken(null)).toEqual([]);
    expect(agentFranchiseIdsFromToken('garbage')).toEqual([]);
  });

  it('views all organizations only for an organization-wide token that chose it', () => {
    const wide = tokenWith({ tenantId: 'acme', orgWide: true });
    const narrow = tokenWith({ tenantId: 'acme' });
    expect(isViewingAllOrganizations(wide, 'true')).toBe(true);
    expect(isViewingAllOrganizations(wide, null)).toBe(false);
    expect(isViewingAllOrganizations(narrow, 'true')).toBe(false);
    expect(isViewingAllOrganizations(null, 'true')).toBe(false);
  });

  it('asks for every organization on reads only, and only when viewing all', () => {
    const wide = tokenWith({ tenantId: 'acme', orgWide: true });
    const all = { 'X-View-All-Organizations': 'true' };
    const config = 'https://ops.example/api/v1/config/settings';
    const search = 'https://ops.example/api/v1/search/elastic/FRANCHISE/getall';
    expect(viewAllOrganizationsHeaders('GET', config, wide, 'true')).toEqual(all);
    expect(viewAllOrganizationsHeaders('get', config, wide, 'true')).toEqual(all);
    // List screens POST their query to the search service: still a read.
    expect(viewAllOrganizationsHeaders('POST', search, wide, 'true')).toEqual(all);
    expect(viewAllOrganizationsHeaders('POST', 'https://ops.example/api/v1/search/internal/projections', wide, 'true')).toEqual({});
    for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) {
      expect(viewAllOrganizationsHeaders(method, config, wide, 'true')).toEqual({});
    }
    expect(viewAllOrganizationsHeaders('PUT', search, wide, 'true')).toEqual({});
    expect(viewAllOrganizationsHeaders('GET', config, wide, null)).toEqual({});
    expect(viewAllOrganizationsHeaders('GET', config, tokenWith({ tenantId: 'acme' }), 'true')).toEqual({});
  });
});
