import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FranchiseSearchApiService } from './franchise-search-api.service';
import { FRANCHISE_SEARCH_MODELS } from './franchise-search-models';

describe('Franchise SearchAPI GetAll integration', () => {
 let service: FranchiseSearchApiService;
 let http: HttpTestingController;
 const answer = (rows: unknown[], totalElements = rows.length) => ({
  requestType: 'LIST', success: true, errors: [], object: { content: rows.map(payload => ({ payload })), totalElements },
 });
 beforeEach(() => {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  service = TestBed.inject(FranchiseSearchApiService); http = TestBed.inject(HttpTestingController);
 });
 afterEach(() => http.verify());

 it('sends the same plain LIST request as UAM without a search group', () => {
  let page: unknown;
  service.page('FRANCHISE_PLAN', {}, 0, 10).subscribe(value => page = value);
  const req = http.expectOne(request => request.url.endsWith('/search/elastic/FRANCHISE_PLAN/getall'));
  expect(req.request.method).toBe('POST');
  expect(req.request.body).toEqual(expect.objectContaining({ requestType: 'LIST', pageNo: 0, pageSize: 10 }));
  expect(req.request.body.searchQuery).toBeUndefined();
  expect(req.request.headers.has('X-Table-Search')).toBe(false);
  req.flush(answer([{ id: 'plan-1', fee: { code: 'INR', value: 1234.56 } }], 1));
  expect(page).toEqual({ items: [{ id: 'plan-1', fee: { code: 'INR', value: 1234.56 } }], page: 0, size: 10, totalElements: 1 });
 });

 it('translates filters to the EAV AND group and keeps the requested page', () => {
  service.page('FRANCHISE_PLAN', { applicationCode: 'MATRIMONY', scope: 'COUNTRY', q: 'Gold' }, 2, 10).subscribe();
  const req = http.expectOne(request => request.url.endsWith('/FRANCHISE_PLAN/getall'));
  expect(req.request.body).toEqual(expect.objectContaining({ pageNo: 2, pageSize: 10,
   searchQuery: { group: { criteria1: 0, group: [
    { field: 'text1', operator: 'EQ', value: 'MATRIMONY' },
    { field: 'text2', operator: 'EQ', value: 'COUNTRY' },
    { field: 'text13', operator: 'LIKE', value: 'gold' },
   ] } },
  }));
  req.flush(answer([], 0));
 });

 it('keeps effective-date alternatives nested inside the AND filters', () => {
  service.page('FRANCHISE_COMMISSION_PLAN', { vertical: 'RIDE', activeOn: '2026-11-12' }, 0, 20).subscribe();
  const req = http.expectOne(request => request.url.endsWith('/FRANCHISE_COMMISSION_PLAN/getall'));
  const groups = req.request.body.searchQuery.group.group;
  expect(groups).toContainEqual({ field: 'text4', operator: 'EQ', value: 'RIDE' });
  expect(groups).toContainEqual({ criteria1: 1, group: [
   { field: 'date2', operator: 'GE', value: '2026-11-12T00:00:00Z' },
   { field: 'date2', operator: 'EMPTY' },
  ] });
  req.flush(answer([]));
 });

 it('collects every backend page for an existing unpaged picker', () => {
  let items: unknown[] = [];
  service.all('FRANCHISE_EVENT_TYPE').subscribe(rows => items = rows);
  const first = http.expectOne(request => request.url.endsWith('/FRANCHISE_EVENT_TYPE/getall'));
  expect(first.request.body.pageSize).toBe(50);
  first.flush(answer(Array.from({ length: 50 }, (_, id) => ({ id })), 51));
  const next = http.expectOne(request => request.url.endsWith('/FRANCHISE_EVENT_TYPE/getall'));
  expect(next.request.body.pageNo).toBe(1);
  next.flush(answer([{ id: 50 }], 51));
  expect(items.length).toBe(51);
 });

 it('stitches a page larger than SearchAPI maximum without changing the UI offset', () => {
  let items: unknown[] = [];
  service.page('FRANCHISE_LOCATION', {}, 1, 75).subscribe(result => items = result.items);
  const first = http.expectOne(request => request.url.endsWith('/FRANCHISE_LOCATION/getall'));
  expect(first.request.body).toEqual(expect.objectContaining({ pageNo: 1, pageSize: 50 }));
  first.flush(answer(Array.from({ length: 50 }, (_, i) => ({ id: i + 50 })), 200));
  const second = http.expectOne(request => request.url.endsWith('/FRANCHISE_LOCATION/getall'));
  expect(second.request.body.pageNo).toBe(2);
  second.flush(answer(Array.from({ length: 50 }, (_, i) => ({ id: i + 100 })), 200));
  expect(items).toHaveLength(75);
  expect(items[0]).toEqual({ id: 75 }); expect(items[74]).toEqual({ id: 149 });
 });

 it('routes every registered franchise list to its model, including scoped lists', () => {
  for (const model of FRANCHISE_SEARCH_MODELS) {
   const path = model.path.replace(/\{[^}]+}/g, 'parent');
   const result = service.readSource(path, { page: 0, size: 10 });
   expect(result).not.toBeNull(); result!.subscribe();
   const req = http.expectOne(request => request.url.endsWith('/' + model.model + '/getall'));
   expect(req.request.method).toBe('POST');
   if (path.includes('/parent/')) expect(req.request.body.searchQuery).toBeDefined();
   req.flush(answer([{ id: 'row-1', unitId: 'parent', applications: [] }]));
   if (model.model === 'FRANCHISE_PLAN') http.expectOne(request => request.url.endsWith('/franchise/plans/options'))
    .flush({ success: true, responseObject: { defaultCurrency: 'INR', countries: ['IN'], agentWalletMultiplier: 2 } });
   if (model.model === 'FRANCHISE_MEMBER' || model.model === 'FRANCHISE_ADMIN_MEMBER') {
    http.expectOne(request => request.url.endsWith('/parent/members/summary'))
     .flush({ success: true, responseObject: { franchiseId: 'parent', applications: [{ agentLimit: 5, agentCount: 1 }], totalAgentLimit: 5, totalAgentCount: 1 } });
   }
  }
 });

 it('leaves detail, write, resolver and live wallet contracts out of the list client', () => {
  for (const path of ['/plans/p1', '/plans/resolve?applicationCode=A', '/ledger/mine/statement', '/payouts/mine', '/locations/id']) {
   expect(service.readSource(path)).toBeNull();
  }
 });

 it('propagates index failures and incomplete records without reading a native table', () => {
  let failure: unknown;
  service.page('FRANCHISE_PLAN').subscribe({ error: error => failure = error });
  http.expectOne(request => request.url.endsWith('/FRANCHISE_PLAN/getall')).flush({ success: false, errors: ['Index unavailable'] });
  expect(failure).toEqual({ error: { message: 'Index unavailable', detail: 'Index unavailable' } });
  service.page('FRANCHISE_PLAN').subscribe({ error: error => failure = error });
  http.expectOne(request => request.url.endsWith('/FRANCHISE_PLAN/getall')).flush({ success: true, object: { content: [{ modelKey: 'partial' }] } });
  expect((failure as { error: { message: string } }).error.message).toContain('reindex');
 });
});
