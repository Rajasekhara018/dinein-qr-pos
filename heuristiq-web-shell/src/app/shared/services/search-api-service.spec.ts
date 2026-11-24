import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { hasActiveFilters, SearchApiService, SearchDocument, UAM_SEARCH_MODELS } from './search-api-service';

const doc = (over: Record<string, unknown>): SearchDocument =>
  ({ id: 'i', model: 'X', modelKey: 'key1', ownerKey: null, createdAt: null, updatedAt: null, ...over }) as SearchDocument;

describe('hasActiveFilters', () => {
  it('is true only when a value is non-blank', () => {
    expect(hasActiveFilters({ a: '', b: '  ', c: null, d: undefined })).toBe(false);
    expect(hasActiveFilters({ a: '', b: 0 })).toBe(true);
    expect(hasActiveFilters({})).toBe(false);
  });
});

describe('UAM_SEARCH_MODELS row mappers', () => {
  it('maps a user document, splitting roles and applications and wrapping the phone', () => {
    const row = UAM_SEARCH_MODELS.users.toRow(doc({ text1: 'asha', text2: 'a@x.com', text3: '+919999', text4: 'ACTIVE', text5: 't1', text6: 'Asha', text7: 'Asha Rao', text8: 'A,B', text9: 'app1,app2' })) as unknown as Record<string, unknown>;
    expect(row).toMatchObject({
      id: 'key1', userName: 'asha', email: 'a@x.com', status: 'ACTIVE', tenantId: 't1', displayName: 'Asha',
      phoneNumber: { countryCode: null, number: '+919999' },
      fullName: { firstName: 'Asha Rao', middleName: null, lastName: null },
      roles: ['A', 'B'], applications: ['app1', 'app2'],
    });
  });

  it('maps a sparse user document to nulls and empty lists', () => {
    const row = UAM_SEARCH_MODELS.users.toRow(doc({})) as unknown as Record<string, unknown>;
    expect(row).toMatchObject({ userName: '', email: null, phoneNumber: null, fullName: null, roles: [], applications: [] });
  });

  it('maps role, organization, application, client, policy rows with boolean slots', () => {
    expect(UAM_SEARCH_MODELS.roles.toRow(doc({ text1: 'R', text2: 'Role', text5: 'true', text7: 'p1,p2', text8: 'false' }))).toMatchObject({ code: 'R', active: true, systemRole: false, permissions: ['p1', 'p2'] });
    expect(UAM_SEARCH_MODELS.organizations.toRow(doc({ text1: 'O', text8: 's@x' }))).toMatchObject({ code: 'O', supportEmail: 's@x' });
    expect(UAM_SEARCH_MODELS.applications.toRow(doc({ text7: 'true' }))).toMatchObject({ id: 'key1', active: true });
    expect(UAM_SEARCH_MODELS.applications.toRow(doc({}))).toMatchObject({ active: null });
    expect(UAM_SEARCH_MODELS.clients.toRow(doc({ text5: 'false', text1: 'cid' }))).toMatchObject({ clientId: 'cid', active: false });
    expect(UAM_SEARCH_MODELS.securityPolicies.toRow(doc({ text4: 'true', text6: 'true' }))).toMatchObject({ active: true, systemPolicy: true });
  });

  it('maps a notification template, converting epoch dates and the ACTIVE flag', () => {
    const row = UAM_SEARCH_MODELS.nmsTemplates.toRow(doc({ text1: 'n', text2: 'e', text3: 'SMS', text4: 'ACTIVE', ownerKey: 'org', date1: 0 + 86400000, date2: 172800000 })) as unknown as Record<string, unknown>;
    expect(row).toMatchObject({ name: 'n', event: 'e', channel: 'SMS', active: true, tenantId: 'org', createdAt: '1970-01-02T00:00:00.000Z', updatedAt: '1970-01-03T00:00:00.000Z' });
    const empty = UAM_SEARCH_MODELS.nmsTemplates.toRow(doc({})) as unknown as Record<string, unknown>;
    expect(empty).toMatchObject({ name: '', active: false, tenantId: null, createdAt: null, updatedAt: null });
  });
});

describe('SearchApiService', () => {
  let svc: SearchApiService;
  let http: HttpTestingController;
  const URL = (m: string) => `${environment.gatewayUrl}/v1/search/elastic/${m}/getall`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useValue: { instant: (k: string) => k } }],
    });
    svc = TestBed.inject(SearchApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  const cond = (field: string, value: string) => ({ field, operator: 'EQ' as const, value });

  it('getAll with no conditions sends no query and no sort', () => {
    svc.getAll('USER', [], [], 0, 10).subscribe();
    const req = http.expectOne(URL('USER'));
    expect(req.request.body).toEqual({ requestType: 'LIST', searchQuery: undefined, pageNo: 0, pageSize: 10, sort: undefined });
    req.flush({ success: true, object: { content: [] } });
  });

  it('getAll ANDs the conditions and adds the OR group as a nested criteria', () => {
    svc.getAll('USER', [cond('text1', 'a')], [cond('text2', 'b'), cond('text3', 'c')], 1, 5, 'text9').subscribe();
    const req = http.expectOne(URL('USER'));
    expect(req.request.body.searchQuery).toEqual({
      group: { criteria1: 0, group: [
        { field: 'text1', operator: 'EQ', value: 'a' },
        { criteria1: 1, group: [{ field: 'text2', operator: 'EQ', value: 'b' }, { field: 'text3', operator: 'EQ', value: 'c' }] },
      ] },
    });
    expect(req.request.body.sort).toEqual({ field1: 'text9', directionField1: 'ASC' });
    req.flush({ success: true, object: { content: [] } });
  });

  it('search uses AND by default and OR when asked', () => {
    svc.search('ROLE', [cond('text1', 'a')], 0, 5).subscribe();
    const and = http.expectOne(URL('ROLE'));
    expect(and.request.body.searchQuery.group.group).toEqual([{ field: 'text1', operator: 'EQ', value: 'a' }]);
    and.flush({ success: true, object: { content: [] } });
    svc.search('ROLE', [cond('text1', 'a')], 0, 5, 'OR').subscribe();
    const or = http.expectOne(URL('ROLE'));
    expect(or.request.body.searchQuery.group.group).toEqual([{ criteria1: 1, group: [{ field: 'text1', operator: 'EQ', value: 'a' }] }]);
    or.flush({ success: true, object: { content: [] } });
  });

  it('encodes the model in the URL', () => {
    svc.getAll('A/B', [], [], 0, 1).subscribe();
    http.expectOne(URL('A%2FB')).flush({ success: true, object: {} });
  });

  it('normalises a flat page', () => {
    let page: unknown;
    svc.getAll('USER', [], [], 0, 2).subscribe((p) => (page = p));
    http.expectOne(URL('USER')).flush({ success: true, object: { content: [{ id: 1 }, { id: 2 }], totalElements: 5, totalPages: 3, size: 2, number: 0 } });
    expect(page).toEqual({ content: [{ id: 1 }, { id: 2 }], totalElements: 5, totalPages: 3, size: 2, number: 0, numberOfElements: 2, first: true, last: false, empty: false });
  });

  it('normalises a PagedModel page (meta under `page`) and marks the last page', () => {
    let page: unknown;
    svc.getAll('USER', [], [], 2, 2).subscribe((p) => (page = p));
    http.expectOne(URL('USER')).flush({ success: true, object: { content: [{ id: 1 }], page: { size: 2, number: 2, totalElements: 5, totalPages: 3 } } });
    expect(page).toMatchObject({ first: false, last: true, number: 2, totalPages: 3 });
  });

  it('derives totals when the page has no metadata, and copes with no object', () => {
    let page: { totalElements: number; totalPages: number; empty: boolean } | undefined;
    svc.getAll('USER', [], [], 0, 2).subscribe((p) => (page = p as never));
    http.expectOne(URL('USER')).flush({ success: true, object: { content: [{ id: 1 }, { id: 2 }, { id: 3 }] } });
    expect(page).toMatchObject({ totalElements: 3, totalPages: 1 });
    svc.getAll('USER', [], [], 0, 2).subscribe((p) => (page = p as never));
    http.expectOne(URL('USER')).flush({ success: true });
    expect(page).toMatchObject({ totalElements: 0, empty: true });
  });

  it('raises the first server error, or the translated generic message', () => {
    const errs: unknown[] = [];
    svc.getAll('USER', [], [], 0, 1).subscribe({ error: (e) => errs.push(e) });
    http.expectOne(URL('USER')).flush({ success: false, errors: ['bad query', 'x'] });
    svc.getAll('USER', [], [], 0, 1).subscribe({ error: (e) => errs.push(e) });
    http.expectOne(URL('USER')).flush({ success: false });
    expect(errs).toEqual([
      { error: { detail: 'bad query', message: 'bad query' } },
      { error: { detail: 'shared.errors.requestFailed', message: 'shared.errors.requestFailed' } },
    ]);
  });

  it('searchRows maps only known, non-blank filters to conditions and rows back through the binding', () => {
    let rows: unknown;
    svc.searchRows(UAM_SEARCH_MODELS.users, { userName: ' asha ', email: '', status: 'ACTIVE', unknown: 'x', roleCode: null }, 0, 10)
      .subscribe((p) => (rows = p));
    const req = http.expectOne(URL('USER'));
    expect(req.request.body.searchQuery.group.group).toEqual([
      { field: 'text1', operator: 'LIKE', value: 'asha' },
      { field: 'text4', operator: 'EQ', value: 'ACTIVE' },
    ]);
    req.flush({ success: true, object: { content: [{ modelKey: 'u9', text1: 'asha' }], totalElements: 1 } });
    expect((rows as { content: Array<{ id: string; userName: string }> }).content[0]).toMatchObject({ id: 'u9', userName: 'asha' });
  });
});
