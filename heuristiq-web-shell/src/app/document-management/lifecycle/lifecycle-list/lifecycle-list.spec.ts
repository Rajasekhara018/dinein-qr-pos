import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NotificationService } from '../../../shared/services/notification-service';
import { OrganizationContextService } from '../../../shared/services/organization-context-service';
import { LIFECYCLE_DEFAULT_PAGE_SIZE } from '../lifecycle.constants';
import { emptyDocumentFilter } from '../lifecycle.helpers';
import { LifecycleDocumentRow, LifecyclePage } from '../lifecycle.models';
import { LifecycleService } from '../lifecycle.service';
import { LifecycleList } from './lifecycle-list';

const doc = (id: string, over: Partial<LifecycleDocumentRow> = {}): LifecycleDocumentRow =>
  ({ id, originalFileName: id + '.pdf', documentType: 'PAN', category: 'KYC', ownerId: 'owner1', sizeBytes: 2048, status: 'ACTIVE', scanStatus: 'CLEAN', createdAt: '2026-01-02T10:00:00Z', legalHold: false, deleted: false, ...over } as LifecycleDocumentRow);
const pageOf = (rows: LifecycleDocumentRow[], over: Partial<LifecyclePage<LifecycleDocumentRow>> = {}): LifecyclePage<LifecycleDocumentRow> =>
  ({ content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1, ...over });

describe('LifecycleList', () => {
  const router = { navigate: vi.fn() };
  const svc = { searchDocuments: vi.fn((_f: unknown, _p: number, _s: number): Observable<LifecyclePage<LifecycleDocumentRow>> => of(pageOf([doc('d1'), doc('d2', { legalHold: true, deleted: true })], { totalElements: 30, totalPages: 2 }))) };
  const notify = { error: vi.fn(), success: vi.fn() };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };
  let translate: TranslateService;

  function create(): ComponentFixture<LifecycleList> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [LifecycleList],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: Router, useValue: router },
        { provide: LifecycleService, useValue: svc },
        { provide: NotificationService, useValue: notify },
        { provide: OrganizationContextService, useValue: { currentName$: of('Acme') } },
        { provide: HqTimezoneService, useValue: { effectiveTimezone: () => 'UTC' } },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    translate = TestBed.inject(TranslateService);
    const fixture = TestBed.createComponent(LifecycleList);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    svc.searchDocuments.mockImplementation(() => of(pageOf([doc('d1'), doc('d2', { legalHold: true, deleted: true })], { totalElements: 30, totalPages: 2 })));
  });

  it('searches on open with the default filter and page size', () => {
    const c = create().componentInstance;
    expect(svc.searchDocuments).toHaveBeenCalledWith(emptyDocumentFilter(), 0, LIFECYCLE_DEFAULT_PAGE_SIZE);
    expect(c.rows).toHaveLength(2);
    expect([c.totalElements, c.totalPages, c.searched, c.hasError]).toEqual([30, 2, true, false]);
  });

  it('reports a failed search', () => {
    svc.searchDocuments.mockReturnValue(throwError(() => new Error('x')));
    const c = create().componentInstance;
    expect([c.hasError, c.isLoading]).toEqual([true, false]);
    expect(notify.error).toHaveBeenCalledWith('documentManagement.lifecycle.list.loadFailed');
  });

  it('says there are no documents only after a search came back empty', () => {
    svc.searchDocuments.mockReturnValue(of(pageOf([])));
    const el: HTMLElement = create().nativeElement;
    expect(el.textContent).toContain('documentManagement.lifecycle.list.empty');
  });

  it('applies, clears, pages and resizes', () => {
    const c = create().componentInstance;
    c.page = 3;
    c.filter = { ...emptyDocumentFilter(), query: 'pan' };
    c.applyFilters();
    expect(svc.searchDocuments).toHaveBeenLastCalledWith(expect.objectContaining({ query: 'pan' }), 0, LIFECYCLE_DEFAULT_PAGE_SIZE);
    c.clearFilters();
    expect(c.filter).toEqual(emptyDocumentFilter());
    c.goToPage(1);
    expect(svc.searchDocuments).toHaveBeenLastCalledWith(expect.anything(), 1, LIFECYCLE_DEFAULT_PAGE_SIZE);
    c.onPageSizeChange(50);
    expect(svc.searchDocuments).toHaveBeenLastCalledWith(expect.anything(), 0, 50);
  });

  it('drops the answer of a search that was superseded or abandoned', () => {
    const c = create().componentInstance;
    const first = new Subject<LifecyclePage<LifecycleDocumentRow>>();
    svc.searchDocuments.mockReturnValueOnce(first);
    c.load();
    svc.searchDocuments.mockReturnValueOnce(of(pageOf([doc('new')])));
    c.load();
    first.next(pageOf([doc('stale')]));
    expect(c.rows.map((r) => r.id)).toEqual(['new']);
    c.ngOnDestroy();
  });

  it('opens the detail screen of a row', () => {
    create().componentInstance.open(doc('d9'));
    expect(router.navigate).toHaveBeenCalledWith(['/document-management/lifecycle', 'd9']);
  });

  describe('shared display helpers', () => {
    it('formats sizes, dates and labels, with dashes for nothing', () => {
      const c = create().componentInstance;
      expect(c.size(1536)).toBe('1.5 KB');
      expect(c.size(null)).toBe('—');
      expect(c.absolute('2026-01-02T10:00:00Z')).toContain('2026');
      expect(c.day('2026-01-02T10:00:00Z')).toContain('2026');
      expect(c.relative(null)).toBe('');
      expect(c.tone('ACTIVE')).toBeDefined();
      expect(c.statusLabel('')).toBe('');
      expect(c.statusLabel('SOMETHING_ELSE')).toBe('SOMETHING_ELSE');
      expect(c.reasonLabel(null)).toBe('');
      expect(c.visual('UNKNOWN_ACTION').labelKey).toBeNull();
      expect(c.actionLabel('UNKNOWN_ACTION')).toBe('UNKNOWN_ACTION');
      expect(c.actionLabel(null)).toBe('');
      expect(translate).toBeDefined();
    });

    it('copies text to the clipboard and says so', async () => {
      const c = create().componentInstance;
      const writeText = vi.fn(() => Promise.resolve());
      Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
      await c.copy('abc');
      expect(writeText).toHaveBeenCalledWith('abc');
      expect(notify.success).toHaveBeenCalledWith('documentManagement.lifecycle.copied');
      await c.copy('');
      expect(writeText).toHaveBeenCalledTimes(1);
    });

    it('says so when copying is not possible, without throwing', async () => {
      const c = create().componentInstance;
      Object.defineProperty(navigator, 'clipboard', { value: { writeText: () => Promise.reject(new Error('denied')) }, configurable: true });
      await c.copy('abc');
      expect(notify.error).toHaveBeenCalledWith('documentManagement.lifecycle.copyFailed');
    });
  });

  describe('privileges', () => {
    it('shows the view button and the audit link to someone who may read documents and audit', () => {
      const el: HTMLElement = create().nativeElement;
      expect(el.querySelectorAll('tbody .row-action-btn')).toHaveLength(2);
      expect(el.querySelector('.lc-tab-link')).not.toBeNull();
      (el.querySelector('tbody .row-action-btn') as HTMLElement).click();
      expect(router.navigate).toHaveBeenCalledWith(['/document-management/lifecycle', 'd1']);
    });

    it('hides the view buttons without the read privilege and the audit link without the audit privilege', () => {
      granted = new Set(['DMS_DOCUMENT_LIFECYCLE_LIST']);
      const el: HTMLElement = create().nativeElement;
      expect(el.querySelectorAll('tbody tr')).toHaveLength(2);
      expect(el.querySelectorAll('tbody .row-action-btn')).toHaveLength(0);
      expect(el.querySelector('.lc-tab-link')).toBeNull();
    });

    it('each privilege unlocks only its own control', () => {
      granted = new Set(['AUDIT_EVENT_LIST']);
      let el: HTMLElement = create().nativeElement;
      expect(el.querySelector('.lc-tab-link')).not.toBeNull();
      expect(el.querySelectorAll('tbody .row-action-btn')).toHaveLength(0);
      granted = new Set(['DMS_DOCUMENT_LIFECYCLE_READ']);
      el = create().nativeElement;
      expect(el.querySelector('.lc-tab-link')).toBeNull();
      expect(el.querySelectorAll('tbody .row-action-btn')).toHaveLength(2);
    });
  });
});
