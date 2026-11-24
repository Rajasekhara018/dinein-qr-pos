import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { endpointPage } from '../qa-dashboard.fixtures';
import { QaEndpointPage, QaEndpointQuery } from '../qa-endpoint-inventory/qa-endpoint-inventory.models';
import { QaEndpointService } from '../qa-endpoint-inventory/qa-endpoint-inventory.service';
import { QA_API_DOCUMENTS_PATH } from './qa-api-documents.constants';
import { QaApiDocumentsService } from './qa-api-documents.service';

const url = (path: string): string => `${environment.apiUrl}${path}`;

describe('QaApiDocumentsService', () => {
  const endpoints = { list: vi.fn((_q: QaEndpointQuery, _p: number, _s: number): Observable<QaEndpointPage> => of(endpointPage([], { totalElements: 5 }))) };
  let service: QaApiDocumentsService;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.clearAllMocks();
    endpoints.list.mockImplementation((query) => of(endpointPage([], { totalElements: query.module === 'uam' ? 12 : 3 })));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: QaEndpointService, useValue: endpoints }],
    });
    service = TestBed.inject(QaApiDocumentsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists only the API documents, as a plain list or a page', () => {
    let first: { name: string; category: string }[] | undefined;
    let second: { name: string }[] | undefined;
    service.list().subscribe((l) => (first = l));
    const req = http.expectOne((r) => r.url === url(QA_API_DOCUMENTS_PATH));
    expect(req.request.params.get('category')).toBe('API');
    req.flush({ responseObject: [{ name: 'api-uam', category: 'API', module: 'uam' }] });
    expect(first?.[0]).toMatchObject({ name: 'api-uam', category: 'API' });
    service.list().subscribe((l) => (second = l));
    http.expectOne((r) => r.url === url(QA_API_DOCUMENTS_PATH)).flush({ responseObject: { content: [{ name: 'api-wallet' }] } });
    expect(second?.[0].name).toBe('api-wallet');
  });

  it('gives an empty list when the server sends nothing', () => {
    let size = -1;
    service.list().subscribe((l) => (size = l.length));
    http.expectOne((r) => r.url === url(QA_API_DOCUMENTS_PATH)).flush(null);
    expect(size).toBe(0);
  });

  it('reads one document, encoding its name', () => {
    let markdown = '';
    service.get('api-uam').subscribe((d) => (markdown = d.markdown));
    http.expectOne(url(`${QA_API_DOCUMENTS_PATH}/api-uam`)).flush({ responseObject: { name: 'api-uam', markdown: '# UAM' } });
    expect(markdown).toBe('# UAM');
  });

  it('saves a document as category API with its module', () => {
    const input = { name: 'api-uam', title: 'API: uam', markdown: '# UAM', category: 'API' as const, module: 'uam' };
    let name = '';
    service.save(input).subscribe((d) => (name = d.name));
    const req = http.expectOne(url(QA_API_DOCUMENTS_PATH));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(input);
    req.flush({ responseObject: { name: 'api-uam', markdown: '# UAM' } });
    expect(name).toBe('api-uam');
  });

  it('deletes a document', () => {
    let done = false;
    service.delete('api-uam').subscribe(() => (done = true));
    const req = http.expectOne(url(`${QA_API_DOCUMENTS_PATH}/api-uam`));
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true });
    expect(done).toBe(true);
  });

  it('counts the endpoints of each module from the inventory, leaving out one that cannot be read', () => {
    endpoints.list.mockImplementation((query) => (query.module === 'broken' ? throwError(() => new Error('down')) : of(endpointPage([], { totalElements: query.module === 'uam' ? 12 : 3 }))));
    let counts: Map<string, number> | undefined;
    service.endpointCounts(['uam', 'wallet', 'broken', 'train', 'nms', 'search']).subscribe((c) => (counts = c));
    expect(Object.fromEntries(counts ?? [])).toEqual({ uam: 12, wallet: 3, train: 3, nms: 3, search: 3 });
    expect(endpoints.list).toHaveBeenCalledWith(expect.objectContaining({ module: 'uam' }), 0, 1);
  });

  it('gives an empty map for no modules', () => {
    let counts: Map<string, number> | undefined;
    service.endpointCounts([]).subscribe((c) => (counts = c));
    expect(counts?.size).toBe(0);
  });
});
