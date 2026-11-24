import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { vi } from 'vitest';
import { ApiService } from '../shared/services/api-service';
import { DocumentTypeFilter, DocumentTypeRequest, DocumentTypeResponse } from './document-type.models';
import { DOCUMENT_TYPES_MAX_PAGE_SIZE, DOCUMENT_TYPES_PATH } from './document-types.constants';
import { DocumentTypesService, normalizeAck, normalizePage } from './document-types.service';

const row = (id: string): DocumentTypeResponse => ({ id, code: 'PAN', category: 'KYC' } as DocumentTypeResponse);
const filter = (over: Partial<DocumentTypeFilter> = {}): DocumentTypeFilter => ({ category: '', flow: '', active: '', page: 0, size: 20, ...over });

describe('DocumentTypesService', () => {
  const api = {
    getAll: vi.fn((_model: string, _params?: unknown): Observable<unknown> => of([])),
    get: vi.fn((_u: string, _p?: unknown): Observable<unknown> => of([])),
    post: vi.fn((_u: string, _b: unknown): Observable<unknown> => of(undefined)),
    put: vi.fn((_u: string, _b: unknown): Observable<unknown> => of(undefined)),
  };
  let service: DocumentTypesService;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [DocumentTypesService, { provide: ApiService, useValue: api }] });
    service = TestBed.inject(DocumentTypesService);
  });

  it('lists with trimmed filters and the page size capped to what the API allows', () => {
    api.getAll.mockReturnValue(of({ content: [row('1')], page: 2, size: 100, totalElements: 120, totalPages: 2 }));
    let page: unknown;
    service.list(filter({ category: ' KYC ', flow: ' onboarding ', active: 'true', page: 2, size: 500 })).subscribe((p) => (page = p));
    expect(api.getAll).toHaveBeenCalledWith('WEB_DMS_DOCUMENT_TYPE', { category: 'KYC', flow: 'onboarding', active: 'true', page: 2, size: DOCUMENT_TYPES_MAX_PAGE_SIZE });
    expect(page).toEqual({ content: [row('1')], page: 2, size: 100, totalElements: 120, totalPages: 2 });
  });

  it('never asks for a page smaller than one', () => {
    service.list(filter({ size: 0 })).subscribe();
    expect((api.getAll.mock.calls[0][1] as { size: number }).size).toBe(1);
  });

  it('reads one type by encoded id', () => {
    service.getById('a/b').subscribe();
    expect(api.get).toHaveBeenCalledWith(`${DOCUMENT_TYPES_PATH}/a%2Fb`);
  });

  it('creates and updates, answering with the change request even when the body is empty', () => {
    const request = { code: 'PAN' } as DocumentTypeRequest;
    let ack: unknown;
    service.create(request).subscribe((a) => (ack = a));
    expect(api.post).toHaveBeenCalledWith(DOCUMENT_TYPES_PATH, request);
    expect(ack).toEqual({ changeRequestId: null, status: 'PENDING' });
    api.put.mockReturnValue(of({ changeRequestId: 'cr1', status: 'PENDING_APPROVAL' }));
    service.update('t 1', request).subscribe((a) => (ack = a));
    expect(api.put).toHaveBeenCalledWith(`${DOCUMENT_TYPES_PATH}/t%201`, request);
    expect(ack).toEqual({ changeRequestId: 'cr1', status: 'PENDING_APPROVAL' });
  });

  describe('checkFile', () => {
    it('uploads the file with its type code and optional category', () => {
      api.post.mockReturnValue(of({ accepted: true, reasons: [{ code: 'X', message: 'm' }], rule: { maxFileSizeMb: 5, allowedFormats: ['PDF'], allowedExtensions: ['.pdf'] } }));
      const file = new File(['abc'], 'doc.pdf');
      let result: unknown;
      service.checkFile(file, 'PAN', 'KYC').subscribe((r) => (result = r));
      const [url, body] = api.post.mock.calls[0] as [string, FormData];
      expect(url).toBe(`${DOCUMENT_TYPES_PATH}/check-file`);
      expect((body.get('file') as File).name).toBe('doc.pdf');
      expect(body.get('documentType')).toBe('PAN');
      expect(body.get('category')).toBe('KYC');
      expect(result).toMatchObject({ accepted: true, reasons: [{ code: 'X', message: 'm' }] });
    });

    it('omits the category when none is given and tolerates an empty answer', () => {
      api.post.mockReturnValue(of(undefined));
      let result: unknown;
      service.checkFile(new File([''], 'a.txt'), 'PAN').subscribe((r) => (result = r));
      expect((api.post.mock.calls[0][1] as FormData).has('category')).toBe(false);
      expect(result).toEqual({ accepted: false, reasons: [], rule: undefined });
    });
  });

  describe('normalizePage', () => {
    it('wraps a bare array as one page', () => {
      expect(normalizePage([row('1'), row('2')], 3, 10)).toEqual({ content: [row('1'), row('2')], page: 3, size: 10, totalElements: 2, totalPages: 1 });
    });

    it('fills the gaps of a partial page and of no answer', () => {
      expect(normalizePage({ content: [row('1')] } as never, 1, 10)).toEqual({ content: [row('1')], page: 1, size: 10, totalElements: 1, totalPages: 1 });
      expect(normalizePage(null, 0, 20)).toEqual({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 });
    });
  });

  it('normalizeAck defaults a missing id and status', () => {
    expect(normalizeAck(undefined)).toEqual({ changeRequestId: null, status: 'PENDING' });
    expect(normalizeAck({ changeRequestId: 'c', status: 'DONE' })).toEqual({ changeRequestId: 'c', status: 'DONE' });
  });
});
