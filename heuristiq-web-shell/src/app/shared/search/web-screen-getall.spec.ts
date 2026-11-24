import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HqDocumentService } from 'hq-shared-lib';
import { DocumentTypesService } from '../../document-management/document-types.service';
import { LifecycleService } from '../../document-management/lifecycle/lifecycle.service';
import { emptyDocumentFilter } from '../../document-management/lifecycle/lifecycle.helpers';
import { NmsTemplatesService } from '../../nms-templates/nms-templates.service';
import { SystemQaService } from '../../system-qa/system-qa.service';

describe('Operations screen Elasticsearch GetAll integration', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()], providers: [provideHttpClient(), provideHttpClientTesting(), { provide: HqDocumentService, useValue: {} }] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  const reply = (rows: unknown[], total = rows.length) => ({ success: true, object: { content: rows.map(payload => ({ payload })), page: { totalElements: total } } });
  const request = (model: string) => http.expectOne(req => req.url.endsWith('/' + model + '/getall'));

  it('loads document types with their existing filters and page DTO', () => {
    let result: any;
    TestBed.inject(DocumentTypesService).list({ category: ' KYC ', flow: ' onboarding ', active: 'false', page: 1, size: 20 }).subscribe(value => result = value);
    const req = request('WEB_DMS_DOCUMENT_TYPE');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.pageNo).toBe(1);
    expect(req.request.body.searchQuery.group.group.map((node: any) => node.value)).toEqual(['kyc', 'ONBOARDING', 'false']);
    req.flush(reply([{ id: 'type-1', code: 'PAN' }], 21));
    expect(result.content[0].id).toBe('type-1'); expect(result.totalElements).toBe(21);
  });
  it('keeps lifecycle owner, status and deleted filters in the explicit model query', () => {
    let result: any;
    TestBed.inject(LifecycleService).searchDocuments({ ...emptyDocumentFilter(), ownerId: 'owner-1', status: 'APPROVED' }, 0, 20).subscribe(value => result = value);
    const req = request('WEB_DMS_LIFECYCLE_DOCUMENT');
    const values = req.request.body.searchQuery.group.group.map((node: any) => node.value);
    expect(values).toContain('owner-1'); expect(values).toContain('approved'); expect(values).toContain('false');
    req.flush(reply([{ id: 'document-1' }])); expect(result.content[0].id).toBe('document-1');
  });
  it('loads NMS events from the index instead of silently using static defaults', () => {
    let result: any;
    TestBed.inject(NmsTemplatesService).getEventDefinitions().subscribe(value => result = value);
    const req = request('WEB_NMS_EVENT');
    req.flush(reply([{ id: 'event-1', value: 'CUSTOM_EVENT', label: 'Custom', attributes: [] }]));
    expect(result.map((row: any) => row.value)).toEqual(['CUSTOM_EVENT']);
  });
  it('uses the explicit parent filter for an event template list', () => {
    let result: any;
    TestBed.inject(NmsTemplatesService).getAll('BOOKING_CONFIRMED').subscribe(value => result = value);
    const req = request('WEB_NMS_EVENT_TEMPLATE');
    expect(req.request.body.searchQuery.group.group).toContainEqual({ field: 'text12', operator: 'EQ', value: 'BOOKING_CONFIRMED' });
    req.flush(reply([{ id: 'template-1', event: 'BOOKING_CONFIRMED', channel: 'EMAIL' }]));
    expect(result[0].id).toBe('template-1');
  });
  it('keeps QA run paging and environment filters', () => {
    let result: any;
    TestBed.inject(SystemQaService).runs(2, 10, 'uat').subscribe(value => result = value);
    const req = request('WEB_QA_RUN');
    expect(req.request.body.pageNo).toBe(2); expect(req.request.body.pageSize).toBe(10);
    expect(req.request.body.searchQuery.group.group.map((node: any) => node.value)).toContain('uat');
    req.flush(reply(Array.from({ length: 10 }, (_, index) => ({ id: 'run-' + (index + 1) })), 31));
    expect(result.number).toBe(2); expect(result.totalElements).toBe(31); expect(result.content[0].id).toBe('run-1');
  });
  it('returns QA documents as the array expected by the screen', () => {
    let result: any;
    TestBed.inject(SystemQaService).documentList().subscribe(value => result = value);
    request('WEB_QA_DOCUMENT').flush(reply([{ name: 'doc-1', title: 'Smoke' }]));
    expect(result[0].name).toBe('doc-1');
  });
});
