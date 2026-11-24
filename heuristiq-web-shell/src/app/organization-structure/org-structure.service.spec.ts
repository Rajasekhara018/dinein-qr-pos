import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { environment } from '../../environments/environment';
import { ORG_API_BASE } from './org-structure.constants';
import { OrgStructureService } from './org-structure.service';

const ok = <T>(responseObject: T) => ({ success: true, responseObject });

describe('OrgStructureService', () => {
  let service: OrgStructureService;
  let http: HttpTestingController;
  const base = `${environment.uamApiUrl}${ORG_API_BASE}`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TranslateService, useValue: { instant: (key: string) => `t:${key}` } },
      ],
    });
    service = TestBed.inject(OrgStructureService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Subscribes, answers the one expected request and returns what the caller received. */
  function call<T>(call$: import('rxjs').Observable<T>, method: string, url: string, body: object = ok(null)) {
    let result: T | undefined;
    call$.subscribe((value) => (result = value));
    const req = http.expectOne((r) => r.url === `${base}${url}`);
    expect(req.request.method).toBe(method);
    req.flush(body);
    return { req, result: () => result };
  }

  it('wraps a POST body in the UAM request envelope and unwraps the response', () => {
    const { req, result } = call(service.openDraft('first'), 'POST', '/drafts', ok({ id: 'd1' }));
    expect(req.request.body.requestObject).toEqual({ note: 'first' });
    expect(req.request.body.doesInputHaveEncryptedData).toBe(false);
    expect(result()).toEqual({ id: 'd1' });
  });

  it('opens a draft with a null note by default', () => {
    const { req } = call(service.openDraft(), 'POST', '/drafts', ok({ id: 'd1' }));
    expect(req.request.body.requestObject).toEqual({ note: null });
  });

  it('reads the current draft and its changes', () => {
    expect(call(service.currentDraft(), 'GET', '/drafts/current', ok({ id: 'd1' })).result()).toEqual({ id: 'd1' });
    expect(call(service.changes('d1'), 'GET', '/drafts/d1/changes', ok([{ id: 'c1' }])).result()).toEqual([{ id: 'c1' }]);
  });

  it('adds a change with its type and payload', () => {
    const { req, result } = call(service.addChange('d1', 'UNIT_RENAME', { id: 'u', name: 'X' }), 'POST', '/drafts/d1/changes', ok({ id: 'c9' }));
    expect(req.request.body.requestObject).toEqual({ type: 'UNIT_RENAME', payload: { id: 'u', name: 'X' } });
    expect(result()).toEqual({ id: 'c9' });
  });

  it('removes a change with DELETE', () => {
    call(service.removeChange('d1', 'c1'), 'DELETE', '/drafts/d1/changes/c1', ok(null));
  });

  it('previews, publishes, discards and rolls back with empty POSTs', () => {
    expect(call(service.preview('d1'), 'POST', '/drafts/d1/preview', ok({ applied: false })).req.request.body).toEqual({});
    expect(call(service.publish('d1'), 'POST', '/drafts/d1/publish', ok({ applied: true })).result()).toEqual({ applied: true });
    call(service.discard('d1'), 'POST', '/drafts/d1/discard');
    call(service.rollbackLast(), 'POST', '/rollback');
  });

  it('lists top-level units without a parent param and sub-units with one', () => {
    const top = call(service.children(), 'GET', '/units', ok([]));
    expect(top.req.request.params.has('parentId')).toBe(false);
    const sub = call(service.children('p1'), 'GET', '/units', ok([]));
    expect(sub.req.request.params.get('parentId')).toBe('p1');
  });

  it('searches units with query and limit', () => {
    const { req } = call(service.searchUnits('ops', 5), 'GET', '/units/search', ok([]));
    expect(req.request.params.get('q')).toBe('ops');
    expect(req.request.params.get('limit')).toBe('5');
  });

  it('pages people with and without a cursor', () => {
    const first = call(service.people('u1', null, 10), 'GET', '/units/u1/people', ok([]));
    expect(first.req.request.params.has('afterId')).toBe(false);
    expect(first.req.request.params.get('limit')).toBe('10');
    const next = call(service.people('u1', 'e7', 10), 'GET', '/units/u1/people', ok([]));
    expect(next.req.request.params.get('afterId')).toBe('e7');
  });

  it('reads ladders and positions', () => {
    call(service.ladders(), 'GET', '/ladders', ok([]));
    call(service.positions('l1'), 'GET', '/ladders/l1/positions', ok([]));
  });

  it('creates and searches employees', () => {
    const created = call(service.createEmployee({ employeeCode: 'E1', fullName: 'A' } as never), 'POST', '/employees', ok({ id: 'e1' }));
    expect(created.req.request.body.requestObject.employeeCode).toBe('E1');
    const found = call(service.searchEmployees('E1', 20), 'GET', '/employees', ok([]));
    expect(found.req.request.params.get('search')).toBe('E1');
  });

  it('drives the import lifecycle', () => {
    expect(call(service.createImport('doc1'), 'POST', '/imports', ok({ id: 'i1' })).req.request.body.requestObject).toEqual({ documentId: 'doc1' });
    expect(call(service.recentImports(3), 'GET', '/imports', ok([])).req.request.params.get('limit')).toBe('3');
    call(service.importBatch('i1'), 'GET', '/imports/i1', ok({ id: 'i1' }));
    const errors = call(service.importErrors('i1', 4, 50), 'GET', '/imports/i1/errors', ok([]));
    expect(errors.req.request.params.get('afterRow')).toBe('4');
    call(service.confirmImport('i1'), 'POST', '/imports/i1/confirm', ok({ id: 'i1' }));
    call(service.cancelImport('i1'), 'POST', '/imports/i1/cancel');
  });

  it('drives the department import lifecycle on its own paths, never the employee ones', () => {
    expect(call(service.createDepartmentImport('doc1'), 'POST', '/department-imports', ok({ id: 'd1' })).req.request.body.requestObject).toEqual({ documentId: 'doc1' });
    expect(call(service.recentDepartmentImports(3), 'GET', '/department-imports', ok([])).req.request.params.get('limit')).toBe('3');
    call(service.departmentImportBatch('d1'), 'GET', '/department-imports/d1', ok({ id: 'd1' }));
    const errors = call(service.departmentImportErrors('d1', 7, 50), 'GET', '/department-imports/d1/errors', ok([]));
    expect(errors.req.request.params.get('afterRow')).toBe('7');
    expect(errors.req.request.params.get('limit')).toBe('50');
    call(service.confirmDepartmentImport('d1'), 'POST', '/department-imports/d1/confirm', ok({ id: 'd1' }));
    call(service.cancelDepartmentImport('d1'), 'POST', '/department-imports/d1/cancel');
  });

  it('drives the ladder import lifecycle on its own paths', () => {
    expect(call(service.createLadderImport('doc1'), 'POST', '/ladder-imports', ok({ id: 'l1' })).req.request.body.requestObject).toEqual({ documentId: 'doc1' });
    expect(call(service.recentLadderImports(3), 'GET', '/ladder-imports', ok([])).req.request.params.get('limit')).toBe('3');
    call(service.ladderImportBatch('l1'), 'GET', '/ladder-imports/l1', ok({ id: 'l1' }));
    const errors = call(service.ladderImportErrors('l1', 7, 50), 'GET', '/ladder-imports/l1/errors', ok([]));
    expect(errors.req.request.params.get('afterRow')).toBe('7');
    expect(errors.req.request.params.get('limit')).toBe('50');
    call(service.confirmLadderImport('l1'), 'POST', '/ladder-imports/l1/confirm', ok({ id: 'l1' }));
    call(service.cancelLadderImport('l1'), 'POST', '/ladder-imports/l1/cancel');
  });

  it('manages role rules', () => {
    call(service.roleRules(), 'GET', '/role-rules', ok([]));
    const created = call(service.createRoleRule('u1', null, 'HR'), 'POST', '/role-rules', ok({ id: 'r1' }));
    expect(created.req.request.body.requestObject).toEqual({ unitId: 'u1', positionId: null, roleCode: 'HR' });
    call(service.deleteRoleRule('r1'), 'DELETE', '/role-rules/r1');
  });

  it('asks for login candidates, adding the optional filters only when given', () => {
    const bare = call(service.loginCandidates(null, null, 100), 'GET', '/logins/candidates', ok([]));
    expect(bare.req.request.params.has('unitId')).toBe(false);
    expect(bare.req.request.params.has('afterId')).toBe(false);
    const full = call(service.loginCandidates('u1', 'e2', 100), 'GET', '/logins/candidates', ok([]));
    expect(full.req.request.params.get('unitId')).toBe('u1');
    expect(full.req.request.params.get('afterId')).toBe('e2');
  });

  it('creates logins', () => {
    const { req } = call(service.createLogins({ unitId: null } as never), 'POST', '/logins', ok({ id: 'run' }));
    expect(req.request.body.requestObject).toEqual({ unitId: null });
  });

  it('surfaces the server message when the envelope reports failure', () => {
    let error: unknown;
    service.currentDraft().subscribe({ error: (e) => (error = e) });
    http.expectOne(`${base}/drafts/current`).flush({ success: false, message: 'No draft' });
    expect(error).toEqual({ error: { detail: 'No draft', message: 'No draft' } });
  });

  it('falls back to the translated generic message when the server gives none', () => {
    let error: unknown;
    service.ladders().subscribe({ error: (e) => (error = e) });
    http.expectOne(`${base}/ladders`).flush({ success: false });
    expect(error).toEqual({ error: { detail: 't:shared.errors.requestFailed', message: 't:shared.errors.requestFailed' } });
  });

  it('drops empty-valued params instead of sending them', () => {
    const { req } = call(service.searchUnits('', 5), 'GET', '/units/search', ok([]));
    expect(req.request.params.has('q')).toBe(false);
  });
});
