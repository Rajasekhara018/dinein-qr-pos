import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { environment } from '../../../environments/environment';
import { studioCase } from '../qa-dashboard.fixtures';
import { QA_STUDIO_CASES_PATH } from './qa-studio.constants';
import { caseToForm, emptyStudioQuery, formToInput } from './qa-studio.helpers';
import { QaStudioService } from './qa-studio.service';

const url = (path: string): string => `${environment.apiUrl}${path}`;

describe('QaStudioService', () => {
  let service: QaStudioService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()], providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(QaStudioService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists a page of cases with only the filters that are set, the page capped at 50', () => {
    let page: { items: Array<{ tcId: string }>; page: number; size: number } | undefined;
    service.list(emptyStudioQuery({ module: 'uam', enabled: 'true', q: 'block' }), 1, 500).subscribe((p) => (page = p));
    const req = http.expectOne((r) => r.url === url(QA_STUDIO_CASES_PATH));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('module')).toBe('uam');
    expect(req.request.params.get('enabled')).toBe('true');
    expect(req.request.params.get('q')).toBe('block');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.has('suite')).toBe(false);
    req.flush({ responseObject: { items: [{ id: 'a', tcId: 'HQ_ST_001' }], totalElements: 1, totalPages: 1 } });
    expect(page?.items[0].tcId).toBe('HQ_ST_001');
    expect(page?.page).toBe(1);
    expect(page?.size).toBe(50);
  });

  it('gives an empty page when the server sends nothing', () => {
    let total = -1;
    service.list(emptyStudioQuery(), 0, 20).subscribe((p) => (total = p.totalElements));
    http.expectOne((r) => r.url === url(QA_STUDIO_CASES_PATH)).flush(null);
    expect(total).toBe(0);
  });

  it('reads one case, encoding its id', () => {
    let name = '';
    service.get('a/b').subscribe((c) => (name = c.name));
    http.expectOne(url(`${QA_STUDIO_CASES_PATH}/a%2Fb`)).flush({ responseObject: { id: 'a/b', name: 'Block' } });
    expect(name).toBe('Block');
  });

  it('creates a case without a version', () => {
    const input = formToInput(caseToForm(studioCase('a')));
    let tcId = '';
    service.create(input).subscribe((c) => (tcId = c.tcId));
    const req = http.expectOne(url(QA_STUDIO_CASES_PATH));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(input);
    expect('version' in req.request.body).toBe(false);
    req.flush({ responseObject: { id: 'new', tcId: 'HQ_ST_007' } });
    expect(tcId).toBe('HQ_ST_007');
  });

  it('replaces a case with the version the edit started from', () => {
    const input = formToInput(caseToForm(studioCase('a')), 3);
    service.update('a', input).subscribe();
    const req = http.expectOne(url(`${QA_STUDIO_CASES_PATH}/a`));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.version).toBe(3);
    req.flush({ responseObject: { id: 'a', version: 4 } });
  });

  it('passes a stale-version refusal on as an error with its status', () => {
    let status = 0;
    service.update('a', formToInput(caseToForm(studioCase('a')), 1)).subscribe({ error: (e: { status: number }) => (status = e.status) });
    http.expectOne(url(`${QA_STUDIO_CASES_PATH}/a`)).flush('stale', { status: 409, statusText: 'Conflict' });
    expect(status).toBe(409);
  });

  it('switches a case on or off and deletes it', () => {
    let enabled: boolean | undefined;
    service.setEnabled('a', false).subscribe((c) => (enabled = c.enabled));
    const patch = http.expectOne(url(`${QA_STUDIO_CASES_PATH}/a/enabled`));
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ enabled: false });
    // The API answers with the case as it is now.
    patch.flush({ responseObject: { id: 'a', enabled: false, version: 4 } });
    expect(enabled).toBe(false);

    service.delete('a').subscribe();
    const del = http.expectOne(url(`${QA_STUDIO_CASES_PATH}/a`));
    expect(del.request.method).toBe('DELETE');
    del.flush({ success: true });
  });
});
