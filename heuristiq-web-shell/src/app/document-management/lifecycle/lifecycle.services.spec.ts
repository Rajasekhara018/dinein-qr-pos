import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { ApiService } from '../../shared/services/api-service';
import { AUDIT_EVENTS_PATH, LIFECYCLE_PATH } from './lifecycle.constants';
import { emptyDocumentFilter } from './lifecycle.helpers';
import { LifecycleAuditService } from './lifecycle-audit.service';
import { LifecycleService } from './lifecycle.service';

describe('LifecycleService', () => {
  const api = {
    get: vi.fn((_u: string, _p?: unknown): Observable<unknown> => of({})),
    getAll: vi.fn((_model: string, _p?: unknown): Observable<unknown> => of({})),
  };
  let service: LifecycleService;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [LifecycleService, { provide: ApiService, useValue: api }] });
    service = TestBed.inject(LifecycleService);
  });

  it('searches documents with only the filters that are set and hides deleted ones', () => {
    api.getAll.mockReturnValue(of({ content: [{ id: 'd1' }], page: 1, size: 20, totalElements: 41, totalPages: 3 }));
    let page: { totalPages: number } | undefined;
    service.searchDocuments({ ...emptyDocumentFilter(), query: 'pan', status: 'ACTIVE' }, 1, 20).subscribe((p) => (page = p as never));
    expect(api.getAll).toHaveBeenCalledWith('WEB_DMS_LIFECYCLE_DOCUMENT', { query: 'pan', status: 'ACTIVE', deleted: false, page: 1, size: 20 });
    expect(page?.totalPages).toBe(3);
  });

  it('wraps a bare array answer as one page', () => {
    api.getAll.mockReturnValue(of([{ id: 'a' }, { id: 'b' }]));
    let page: { totalElements: number; totalPages: number } | undefined;
    service.searchDocuments(emptyDocumentFilter(), 0, 10).subscribe((p) => (page = p as never));
    expect([page?.totalElements, page?.totalPages]).toEqual([2, 1]);
  });

  it('reads one document by encoded id', () => {
    service.getDocument('a/b').subscribe();
    expect(api.get).toHaveBeenCalledWith(`${LIFECYCLE_PATH}/documents/a%2Fb`);
  });
});

describe('LifecycleAuditService', () => {
  let service: LifecycleAuditService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(LifecycleAuditService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('asks the audit trail for the events of one document and maps them to a timeline', () => {
    let result: { content: { id: string; action: string; actorId: string | null }[]; page: number; totalPages: number } | undefined;
    service.documentEvents('doc1', 2, 25).subscribe((r) => (result = r as never));
    const req = http.expectOne((r) => r.url === `${environment.gatewayUrl}${AUDIT_EVENTS_PATH}`);
    expect(req.request.params.get('subjectType')).toBe('DOCUMENT');
    expect(req.request.params.get('subjectId')).toBe('doc1');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('25');
    req.flush({ responseObject: { content: [{ eventId: 'e1', eventType: 'UPLOAD', userId: 'u1' }], number: 2, size: 25, totalElements: 60, totalPages: 3 } });
    expect(result?.content[0]).toMatchObject({ id: 'e1', action: 'UPLOAD', actorId: 'u1' });
    expect([result?.page, result?.totalPages]).toEqual([2, 3]);
  });

  it('never sends a negative page and copes with an empty response', () => {
    let result: { content: unknown[]; totalPages: number } | undefined;
    service.documentEvents('doc1', -3, 10).subscribe((r) => (result = r as never));
    const req = http.expectOne((r) => r.url.endsWith(AUDIT_EVENTS_PATH));
    expect(req.request.params.get('page')).toBe('0');
    req.flush({});
    expect(result?.content).toEqual([]);
    expect(result?.totalPages).toBe(1);
  });

  it('tells missing privileges apart from real failures', () => {
    expect(service.classifyError(new HttpErrorResponse({ status: 403 }))).toBe('forbidden');
    expect(service.classifyError(new HttpErrorResponse({ status: 401 }))).toBe('forbidden');
    expect(service.classifyError(new HttpErrorResponse({ status: 500 }))).toBe('failed');
    expect(service.classifyError(new Error('network'))).toBe('failed');
  });
});
