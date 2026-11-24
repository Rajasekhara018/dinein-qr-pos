import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { ApplicationAccessService } from './application-access.service';

describe('ApplicationAccessService', () => {
  let service: ApplicationAccessService;
  let http: HttpTestingController;
  const url = (path: string): string => `${environment.uamApiUrl}${path}`;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApplicationAccessService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the applications a user was given directly', () => {
    let result: unknown;
    service.listForUser('u1').subscribe((r) => (result = r));
    const req = http.expectOne((r) => r.url === url('/application-access'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('subjectType')).toBe('USER');
    expect(req.request.params.get('subjectId')).toBe('u1');
    req.flush({ success: true, responseObject: [{ id: 'g1' }] });
    expect(result).toEqual({ state: 'ready', grants: [{ id: 'g1' }] });
  });

  it('answers forbidden for 403, unavailable for other failures and for an unsuccessful envelope, never throwing', () => {
    const results: { state: string }[] = [];
    service.listForUser('A').subscribe((r) => results.push(r));
    http.expectOne((r) => r.url === url('/application-access')).flush({}, { status: 403, statusText: 'Forbidden' });
    service.listForUser('B').subscribe((r) => results.push(r));
    http.expectOne((r) => r.url === url('/application-access')).flush({}, { status: 500, statusText: 'Error' });
    service.listForUser('C').subscribe((r) => results.push(r));
    http.expectOne((r) => r.url === url('/application-access')).flush({ success: false, responseObject: null });
    service.listForUser('D').subscribe((r) => results.push(r));
    http.expectOne((r) => r.url === url('/application-access')).flush({ success: true, responseObject: null });
    expect(results.map((r) => r.state)).toEqual(['forbidden', 'unavailable', 'unavailable', 'ready']);
  });

  it('grants each subject with its own POST carrying the application, subject type, id and trimmed note', () => {
    let outcomes: unknown;
    service.grant('APP', 'ROLE', ['AGENT', 'STAFF'], '  go-live ').subscribe((o) => (outcomes = o));

    const first = http.expectOne(url('/application-access'));
    expect(first.request.method).toBe('POST');
    expect(first.request.body).toEqual({
      requestObject: { applicationCode: 'APP', subjectType: 'ROLE', subjectId: 'AGENT', note: 'go-live' },
      doesInputHaveEncryptedData: false,
    });
    first.flush({ success: true, responseObject: {} }, { status: 201, statusText: 'Created' });
    const second = http.expectOne(url('/application-access'));
    expect(second.request.body.requestObject.subjectId).toBe('STAFF');
    second.flush({ success: true, responseObject: {} }, { status: 201, statusText: 'Created' });

    expect(outcomes).toEqual([{ subjectId: 'AGENT', ok: true, status: 201 }, { subjectId: 'STAFF', ok: true, status: 201 }]);
  });

  it('sends no note when it is blank, and reports each failure by status without stopping the others', () => {
    let outcomes: unknown;
    service.grant('APP', 'USER', ['u1', 'u2', 'u3'], '   ').subscribe((o) => (outcomes = o));

    const one = http.expectOne(url('/application-access'));
    expect(one.request.body.requestObject.note).toBeNull();
    one.flush({}, { status: 409, statusText: 'Conflict' });
    http.expectOne(url('/application-access')).flush({}, { status: 404, statusText: 'Not Found' });
    http.expectOne(url('/application-access')).flush({ success: false, responseObject: null });

    expect(outcomes).toEqual([
      { subjectId: 'u1', ok: false, status: 409 },
      { subjectId: 'u2', ok: false, status: 404 },
      { subjectId: 'u3', ok: false, status: 0 },
    ]);
  });

  it('revokes a grant by id and surfaces failures as errors', () => {
    let done = false;
    let error: unknown;
    service.revoke('g/1').subscribe({ next: () => (done = true) });
    const req = http.expectOne(url('/application-access/g%2F1'));
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true, responseObject: {} });
    expect(done).toBe(true);

    service.revoke('g2').subscribe({ error: (e) => (error = e) });
    http.expectOne(url('/application-access/g2')).flush({}, { status: 404, statusText: 'Not Found' });
    expect((error as { status: number }).status).toBe(404);

    let unsuccessful: unknown;
    service.revoke('g3').subscribe({ error: (e) => (unsuccessful = e) });
    http.expectOne(url('/application-access/g3')).flush({ success: false, message: 'no' });
    expect(unsuccessful).toEqual({ status: 0, error: { message: 'no' } });
  });
});
