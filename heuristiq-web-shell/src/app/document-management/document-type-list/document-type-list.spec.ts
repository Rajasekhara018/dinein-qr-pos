import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { DocumentTypePage, DocumentTypeResponse } from '../document-type.models';
import { DocumentTypesService } from '../document-types.service';
import { DOCUMENT_TYPES_DEFAULT_PAGE_SIZE } from '../document-types.constants';
import { DocumentTypeList } from './document-type-list';

const type = (id: string, over: Partial<DocumentTypeResponse> = {}): DocumentTypeResponse =>
  ({ id, code: 'PAN_' + id, category: 'KYC', flow: 'onboarding', names: { en: 'PAN card ' + id }, allowedFormats: ['PDF'], maxFileSizeMb: 5, required: true, expiryYears: null, retentionDays: 365, scanRequired: true, active: true, version: 1, updatedAt: '2026-02-03T00:00:00Z', ...over } as DocumentTypeResponse);
const page = (rows: DocumentTypeResponse[], over: Partial<DocumentTypePage> = {}): DocumentTypePage =>
  ({ content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1, ...over });

describe('DocumentTypeList', () => {
  const router = { navigate: vi.fn() };
  const svc = { list: vi.fn((_f: unknown): Observable<DocumentTypePage> => of(page([type('1'), type('2', { active: false, flow: '', retentionDays: null, required: false, updatedAt: undefined })], { totalElements: 45, totalPages: 3 }))) };
  const notify = { error: vi.fn(), success: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };
  let translate: TranslateService;

  function create(): ComponentFixture<DocumentTypeList> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [DocumentTypeList],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: Router, useValue: router },
        { provide: DocumentTypesService, useValue: svc },
        { provide: NotificationService, useValue: notify },
        { provide: OrganizationContextService, useValue: { currentName$: of('Acme') } },
        { provide: HqTimezoneService, useValue: { formatLocale: (v: string) => 'd:' + v } },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    translate = TestBed.inject(TranslateService);
    const fixture = TestBed.createComponent(DocumentTypeList);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    svc.list.mockImplementation(() => of(page([type('1'), type('2', { active: false, flow: '', retentionDays: null, required: false, updatedAt: undefined })], { totalElements: 45, totalPages: 3 })));
  });

  it('loads the first page with the default size', () => {
    const c = create().componentInstance;
    expect(svc.list).toHaveBeenCalledWith({ category: '', flow: '', active: '', page: 0, size: DOCUMENT_TYPES_DEFAULT_PAGE_SIZE });
    expect(c.rows).toHaveLength(2);
    expect([c.totalElements, c.totalPages, c.isLoading, c.hasError]).toEqual([45, 3, false, false]);
  });

  it('flags a failed load', () => {
    svc.list.mockReturnValue(throwError(() => new Error('x')));
    const c = create().componentInstance;
    expect(c.hasError).toBe(true);
    expect(notify.error).toHaveBeenCalledWith('documentManagement.list.messages.loadFailed');
  });

  it('renders the cells: localized name, flow dash, rule sentence, retention, required, status, date', () => {
    const c = create().componentInstance;
    const col = (key: string) => c.columns.find((x) => x.key === key)!;
    expect(col('names').render?.(c.rows[0])).toBe('PAN card 1');
    expect(col('flow').render?.(c.rows[0])).toBe('onboarding');
    expect(col('flow').render?.(c.rows[1])).toBe('—');
    expect(col('summary').render?.(c.rows[0])).toContain('documentManagement.summary');
    expect(col('retentionDays').render?.(c.rows[1])).toBe('documentManagement.list.retentionDefault');
    expect(col('retentionDays').render?.(c.rows[0])).not.toBe('documentManagement.list.retentionDefault');
    expect(col('required').render?.(c.rows[0])).toBe('documentManagement.list.required');
    expect(col('required').render?.(c.rows[1])).toBe('documentManagement.list.optional');
    expect(col('active').render?.(c.rows[0])).toBe('documentManagement.list.active');
    expect(col('active').render?.(c.rows[1])).toBe('documentManagement.list.inactive');
    expect(col('updatedAt').render?.(c.rows[0])).toBe('d:2026-02-03T00:00:00Z');
    expect(col('updatedAt').render?.(c.rows[1])).toBe('—');
  });

  it('shows how a type is verified, or that it needs no verification', () => {
    const c = create().componentInstance;
    const col = c.columns.find((x) => x.key === 'verificationMethod')!;
    expect(col.render?.(type('9', { verificationRequired: true, verificationMethod: 'VEHICLE_RC_VAHAN' }))).toBe('documentManagement.verification.methods.VEHICLE_RC_VAHAN');
    expect(col.render?.(type('9'))).toBe('documentManagement.list.noVerification');
    expect(col.render?.(type('9', { verificationRequired: false, verificationMethod: 'MANUAL_REVIEW' }))).toBe('documentManagement.list.noVerification');
  });

  it('shows the one assigned provider next to the verification method', () => {
    const c = create().componentInstance;
    const column = c.columns.find(value => value.key === 'verificationProvider')!;
    expect(column.render?.(type('bank', { verificationRequired: true, verificationMethod: 'BANK_PENNY_DROP', verificationProvider: 'CASHFREE' }))).toBe('Cashfree');
    expect(column.render?.(type('manual', { verificationRequired: true, verificationMethod: 'MANUAL_REVIEW' }))).toBe('documentManagement.providers.manual');
    expect(column.render?.(type('missing', { verificationRequired: true, verificationMethod: 'PAN_VERIFICATION' }))).toBe('documentManagement.providers.unconfigured');
  });

  it('rebuilds the columns when the language changes', () => {
    const c = create().componentInstance;
    const before = c.columns;
    translate.setTranslation('hi', {}); translate.use('hi');
    expect(c.columns).not.toBe(before);
    expect(c.columns).toHaveLength(before.length);
  });

  it('filters from page one, clears, pages and resizes', () => {
    const c = create().componentInstance;
    c.page = 2;
    expect(c.hasActiveFilters).toBe(false);
    c.filters = { category: ' KYC ', flow: '', active: 'true' };
    expect(c.hasActiveFilters).toBe(true);
    c.applyFilters();
    expect(c.page).toBe(0);
    expect(svc.list).toHaveBeenLastCalledWith({ category: ' KYC ', flow: '', active: 'true', page: 0, size: DOCUMENT_TYPES_DEFAULT_PAGE_SIZE });
    c.clearFilters();
    expect(c.filters).toEqual({ category: '', flow: '', active: '' });
    c.goToPage(2);
    expect(svc.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }));
    c.onPageSizeChange(50);
    expect(svc.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 50 }));
  });

  it('a newer load replaces an older one still in flight', () => {
    const c = create().componentInstance;
    c.load();
    c.load();
    expect(c.isLoading).toBe(false);
    c.ngOnDestroy();
  });

  it('navigates to the editor for create and edit', () => {
    const c = create().componentInstance;
    c.create();
    expect(router.navigate).toHaveBeenLastCalledWith(['/document-management/document-types', 'new']);
    c.edit(type('7'));
    expect(router.navigate).toHaveBeenLastCalledWith(['/document-management/document-types', '7', 'edit']);
  });

  describe('privileges', () => {
    it('shows New and Edit to someone who may create and update', () => {
      const el: HTMLElement = create().nativeElement;
      expect(el.querySelector('.table-create-btn')).not.toBeNull();
      expect(el.querySelectorAll('.row-action-btn')).toHaveLength(2);
      (el.querySelector('.table-create-btn') as HTMLElement).click();
      expect(router.navigate).toHaveBeenCalledWith(['/document-management/document-types', 'new']);
    });

    it('a viewer sees the rows but neither New nor Edit', () => {
      granted = new Set(['DMS_DOCUMENT_TYPE_LIST']);
      const el: HTMLElement = create().nativeElement;
      expect(el.querySelectorAll('tbody tr')).toHaveLength(2);
      expect(el.querySelector('.table-create-btn')).toBeNull();
      expect(el.querySelectorAll('.row-action-btn')).toHaveLength(0);
    });

    it('create and update are separate privileges', () => {
      granted = new Set(['DMS_DOCUMENT_TYPE_UPDATE']);
      let el: HTMLElement = create().nativeElement;
      expect(el.querySelector('.table-create-btn')).toBeNull();
      expect(el.querySelectorAll('.row-action-btn')).toHaveLength(2);
      granted = new Set(['DMS_DOCUMENT_TYPE_CREATE']);
      el = create().nativeElement;
      expect(el.querySelector('.table-create-btn')).not.toBeNull();
      expect(el.querySelectorAll('.row-action-btn')).toHaveLength(0);
    });
  });
});
