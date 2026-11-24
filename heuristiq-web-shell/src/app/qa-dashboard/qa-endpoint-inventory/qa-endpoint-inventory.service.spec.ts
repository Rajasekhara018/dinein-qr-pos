import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { environment } from '../../../environments/environment';
import { QA_ENDPOINTS_EXPORT_PATH, QA_ENDPOINTS_FILTERS_PATH, QA_ENDPOINTS_PATH } from './qa-endpoint-inventory.constants';
import { emptyEndpointQuery } from './qa-endpoint-inventory.helpers';
import { QaEndpointService } from './qa-endpoint-inventory.service';

const url = (path: string): string => `${environment.apiUrl}${path}`;

describe('QaEndpointService', () => {
  let service: QaEndpointService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(QaEndpointService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists a page of endpoints with the filters that are set and the page capped at 50', () => {
    let page: { items: Array<{ method: string }>; coverage: { coveragePct: number }; page: number; size: number } | undefined;
    service.list(emptyEndpointQuery({ module: 'uam', quick: 'untested', q: 'block' }), 1, 500).subscribe((p) => (page = p));
    const req = http.expectOne((r) => r.url === url(QA_ENDPOINTS_PATH));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('module')).toBe('uam');
    expect(req.request.params.get('tested')).toBe('false');
    expect(req.request.params.get('q')).toBe('block');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.params.has('method')).toBe(false);
    req.flush({ responseObject: { coverage: { coveragePct: 75 }, items: [{ id: 'a', method: 'POST' }], totalElements: 1, totalPages: 1 } });
    expect(page?.items[0].method).toBe('POST');
    expect(page?.coverage.coveragePct).toBe(75);
    expect(page?.page).toBe(1);
    expect(page?.size).toBe(50);
  });

  it('gives an empty page when the server sends nothing', () => {
    let total = -1;
    service.list(emptyEndpointQuery(), 0, 20).subscribe((p) => (total = p.totalElements));
    http.expectOne((r) => r.url === url(QA_ENDPOINTS_PATH)).flush(null);
    expect(total).toBe(0);
  });

  it('reads one endpoint with its test cases, encoding the id', () => {
    let tests = 0;
    service.detail('a/b').subscribe((d) => (tests = d.tests.length));
    http.expectOne(url(`${QA_ENDPOINTS_PATH}/a%2Fb`)).flush({ responseObject: { id: 'a/b', tests: [{ testKey: 'k' }] } });
    expect(tests).toBe(1);
  });

  it('reads the modules and methods of the filter bar', () => {
    let result: unknown;
    service.filters().subscribe((f) => (result = f));
    http.expectOne(url(QA_ENDPOINTS_FILTERS_PATH)).flush({ responseObject: { methods: ['GET'] } });
    expect(result).toEqual({ modules: [], methods: ['GET'] });
  });

  it('exports the filtered list as a file, without paging', () => {
    let blob: Blob | undefined;
    service.exportCsv(emptyEndpointQuery({ quick: 'undocumented', method: 'GET' })).subscribe((b) => (blob = b));
    const req = http.expectOne((r) => r.url === url(QA_ENDPOINTS_EXPORT_PATH));
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.get('documented')).toBe('false');
    expect(req.request.params.get('method')).toBe('GET');
    expect(req.request.params.has('page')).toBe(false);
    req.flush(new Blob(['a,b']));
    expect(blob?.size).toBe(3);
  });

  it('reports a server failure as an error', () => {
    let failed = false;
    service.list(emptyEndpointQuery(), 0, 20).subscribe({ error: () => (failed = true) });
    http.expectOne((r) => r.url === url(QA_ENDPOINTS_PATH)).flush('x', { status: 503, statusText: 'Service Unavailable' });
    expect(failed).toBe(true);
  });
});
