import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import { FranchiseApi } from './franchise-api';

describe('FranchiseApi source writes and indexed lists', () => {
 let api: FranchiseApi;
 let http: HttpTestingController;
 const base = environment.franchiseApiUrl;
 beforeEach(() => {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  api = new FranchiseApi(TestBed.inject(HttpClient), { instant: (key: string) => key } as TranslateService);
  http = TestBed.inject(HttpTestingController);
 });
 afterEach(() => http.verify());
 const indexed = (rows: unknown[]) => ({ requestType: 'LIST', success: true, errors: [], object: { content: rows.map(payload => ({ payload })), totalElements: rows.length } });

 it('uses the franchise source URL for details', () => {
  const cases: [() => void, string][] = [
   [() => api.byId('f 1').subscribe(), '/f%201'],
   [() => api.plan('p 1').subscribe(), '/plans/p%201'],
   [() => api.location('l/1').subscribe(), '/locations/l%2F1'],
   [() => api.packageTemplate('p 1').subscribe(), '/package-templates/p%201'],
   [() => api.hierarchy('f 1').subscribe(), '/f%201/hierarchy'],
   [() => api.drivers('f 1', 0, 20).subscribe(), '/f%201/drivers?page=0&size=20'],
   [() => api.adminDrivers('f1', 1, 25).subscribe(), '/admin/f1/drivers?page=1&size=25'],
   [() => api.livePlaces(true).subscribe(), ''],
   [() => api.livePlaces(false).subscribe(), '/mine'],
  ];
  for (const [call, path] of cases) { call(); const req = http.expectOne(base + path); expect(req.request.method).toBe('GET'); req.flush({ success: true, responseObject: {} }); }
 });

 it('sends every franchise table list to its own SearchAPI model', () => {
  const cases: [() => void, string][] = [
   [() => api.list().subscribe(), 'FRANCHISE_FRANCHISE'],
   [() => api.mine().subscribe(), 'FRANCHISE_MINE'],
   [() => api.agents('f1').subscribe(), 'FRANCHISE_AGENT'],
   [() => api.locations({ type: 'STATE', size: 20 }).subscribe(), 'FRANCHISE_LOCATION'],
   [() => api.locationChildren('l1', { size: 20 }).subscribe(), 'FRANCHISE_LOCATION_CHILD'],
   [() => api.placeOptions({ countryCode: 'IN', size: 20 }).subscribe(), 'FRANCHISE_PLACE_OPTION'],
   [() => api.placeOptionChildren('l1', { size: 20 }).subscribe(), 'FRANCHISE_PLACE_OPTION_CHILD'],
  ];
  for (const [call, model] of cases) {
   call(); const req = http.expectOne(request => request.url.endsWith('/search/elastic/' + model + '/getall'));
   expect(req.request.method).toBe('POST'); expect(req.request.body.requestType).toBe('LIST');
   req.flush(indexed([{ id: 'row', unitId: 'f1' }]));
  }
 });

 it('reads plans, plan resolution and package templates live, so a record just saved is listed at once', () => {
  const cases: [() => void, string][] = [
   [() => api.plans().subscribe(), '/plans'],
   [() => api.plansPage({ applicationCode: 'APP', scope: 'TYPE_DEFAULT', page: 0, size: 200 }).subscribe(),
    '/plans?applicationCode=APP&scope=TYPE_DEFAULT&page=0&size=200'],
   [() => api.resolvePlan('APP', 'l1').subscribe(), '/plans/resolve?applicationCode=APP&locationId=l1'],
   [() => api.packageTemplates({ applicationCode: 'APP', page: 0, size: 20 }).subscribe(), '/package-templates?applicationCode=APP&page=0&size=20'],
  ];
  for (const [call, path] of cases) {
   call();
   const req = http.expectOne(base + path);
   expect(req.request.method).toBe('GET');
   req.flush({ success: true, responseObject: {} });
  }
  http.expectNone(request => request.url.includes('/search/elastic/'));
 });

 it('combines indexed member rows with indexed franchise capacity', () => {
  let result: unknown;
  api.members('f1', 'MATRIMONY', 0, 20).subscribe(value => result = value);
  const members = http.expectOne(request => request.url.endsWith('/FRANCHISE_MEMBER/getall'));
  expect(members.request.body.searchQuery.group.group).toContainEqual({ field: 'text2', operator: 'EQ', value: 'MATRIMONY' });
  members.flush(indexed([{ memberId: 'm1' }]));
  http.expectOne(request => request.url.endsWith('/f1/members/summary')).flush({ success: true, responseObject: { franchiseId: 'f1', applications: [{ agentLimit: 5, agentCount: 1 }], totalAgentLimit: 5, totalAgentCount: 1 } });
  expect(result).toEqual(expect.objectContaining({ franchiseId: 'f1', totalAgentLimit: 5, totalAgentCount: 1, members: expect.objectContaining({ items: [{ memberId: 'm1' }] }) }));
 });

 it('uses admin models for administrator member lists', () => {
  api.adminMembers('f1', '', 0, 20).subscribe();
  http.expectOne(request => request.url.endsWith('/FRANCHISE_ADMIN_MEMBER/getall')).flush(indexed([]));
  http.expectOne(request => request.url.endsWith('/admin/f1/members/summary')).flush({ success: true, responseObject: { franchiseId: 'f1', applications: [], totalAgentLimit: 0, totalAgentCount: 0 } });
 });

 it('reads named plan choices from their indexed rule models', () => {
  for (const [kind, model] of [['fee', 'FRANCHISE_FEE_SCHEDULE'], ['commission', 'FRANCHISE_COMMISSION_PLAN'], ['referral', 'FRANCHISE_REFERRAL_PLAN']] as const) {
   api.namedPlans(kind, 'APP').subscribe();
   const req = http.expectOne(request => request.url.endsWith('/' + model + '/getall'));
   expect(req.request.body.searchQuery.group.group).toContainEqual({ field: 'text1', operator: 'EQ', value: 'PLAN' });
   req.flush(indexed([]));
  }
 });

 it('keeps CRUD bodies in the UAM request envelope on the source API', () => {
  const plan = { applicationCode: 'APP', countryCode: 'IN', agentLimit: 5, fee: { code: 'INR', value: 100 } };
  const cases: [() => void, string, string, unknown][] = [
   [() => api.addPlan(plan).subscribe(), '/plans', 'POST', plan],
   [() => api.changePlan('p1', plan).subscribe(), '/plans/p1', 'PUT', plan],
   [() => api.setStatus('f1', 'SUSPENDED').subscribe(), '/f1/status', 'PUT', { status: 'SUSPENDED' }],
   [() => api.addLocation({ countryCode: 'IN', type: 'STATE', code: 'AP', name: 'Andhra' }).subscribe(), '/locations', 'POST', { countryCode: 'IN', type: 'STATE', code: 'AP', name: 'Andhra' }],
   [() => api.changeLocation('l1', { name: 'New', version: 2 }).subscribe(), '/locations/l1', 'PUT', { name: 'New', version: 2 }],
   [() => api.setLocationActive('l1', { active: false, version: 2 }).subscribe(), '/locations/l1/status', 'PUT', { active: false, version: 2 }],
   [() => api.importLocations('csv', true).subscribe(), '/locations/imports', 'POST', { csv: 'csv', dryRun: true }],
   [() => api.requestMoreAgents('f1', 'APP', 5, 'Need more').subscribe(), '/f1/applications/APP/more-agents', 'POST', { agents: 5, note: 'Need more' }],
   [() => api.addAgent('f1', { applicationCode: 'APP', fullName: 'Agent', email: 'agent@example.com' }).subscribe(), '/f1/agents', 'POST', { applicationCode: 'APP', fullName: 'Agent', email: 'agent@example.com' }],
  ];
  for (const [call, path, method, payload] of cases) {
   call(); const req = http.expectOne(base + path); expect(req.request.method).toBe(method);
   expect(req.request.body).toEqual(expect.objectContaining({ requestObject: payload, doesInputHaveEncryptedData: false }));
   req.flush({ success: true, responseObject: {} });
  }
 });

 it('deletes records on the source with encoded identities', () => {
  api.deletePlan('p 1').subscribe(); const plan = http.expectOne(base + '/plans/p 1'); expect(plan.request.method).toBe('DELETE'); plan.flush({ success: true });
  api.removeAgent('f 1', 'm 1').subscribe(); const agent = http.expectOne(base + '/f%201/agents/m%201'); expect(agent.request.method).toBe('DELETE'); agent.flush({ success: true });
 });

 it('keeps live wallet reads and plan resolution on their authoritative APIs', () => {
  api.myEarnings().subscribe(); http.expectOne(base + '/ledger/mine/statement').flush({ success: true, responseObject: {} });
  api.myPayouts({ account: 'AGENT' }, 0, 20).subscribe(); http.expectOne(base + '/payouts/mine?account=AGENT&page=0&size=20').flush({ success: true, responseObject: {} });
  api.resolvePlan('APP', 'l1').subscribe(); http.expectOne(base + '/plans/resolve?applicationCode=APP&locationId=l1').flush({ success: true, responseObject: {} });
 });

 it('retains payout idempotency and the exact request amount', () => {
  const body = { franchiseId: 'f1', party: 'OWNER' as const, amount: { code: 'INR', value: 10.25 } };
  api.requestPayout(body, 'request-key').subscribe();
  const req = http.expectOne(base + '/payouts');
  expect(req.request.headers.get('Idempotency-Key')).toBe('request-key');
  expect(req.request.body.requestObject).toEqual(body);
  req.flush({ success: true, responseObject: {} });
 });

 it('preserves source errors and translated fallbacks', () => {
  const failures: unknown[] = [];
  for (const message of ['Unknown plan', '']) {
   api.plan('missing').subscribe({ error: error => failures.push(error) });
   http.expectOne(base + '/plans/missing').flush({ success: false, message });
  }
  expect(failures).toEqual([
   { error: { detail: 'Unknown plan', message: 'Unknown plan' } },
   { error: { detail: 'shared.errors.requestFailed', message: 'shared.errors.requestFailed' } },
  ]);
 });
});
