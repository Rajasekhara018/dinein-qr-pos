import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { Application, SpringPage } from '../../shared/models/uam.models';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { AdminFormService } from '../admin-form.service';
import { ApplicationsList } from './applications-list';
import { ApplicationsListService } from './applications-list.service';

const app = (over: Partial<Application>): Application => ({ id: 'a1', code: 'APP1', name: 'App One', active: true, ...over }) as Application;
const page = (content: Application[], extra: Partial<SpringPage<Application>> = {}): SpringPage<Application> =>
  ({ content, totalElements: content.length, totalPages: 1, size: 10, number: 0, numberOfElements: content.length, first: true, last: true, empty: !content.length, ...extra });

describe('ApplicationsList', () => {
  const listService = { page: vi.fn() };
  const uam = { remove: vi.fn() };
  const adminForm = { create: vi.fn(), edit: vi.fn() };
  const dialogs = { confirm: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  let granted: Set<string> | null;
  let query: Record<string, string>;
  const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)) };
  let fixture: ComponentFixture<ApplicationsList>;

  function create(): ApplicationsList {
    TestBed.configureTestingModule({
      declarations: [ApplicationsList],
      imports: [FormsModule, RouterModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        provideRouter([]),
        { provide: ApplicationsListService, useValue: listService },
        { provide: UamService, useValue: uam },
        { provide: AdminFormService, useValue: adminForm },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
      ],
    });
    fixture = TestBed.createComponent(ApplicationsList);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement;
  const headers = (): string[] => Array.from(el().querySelectorAll('th')).map((th) => th.textContent?.trim() ?? '');

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    query = {};
    listService.page.mockReturnValue(of(page([app({})], { totalElements: 25, totalPages: 3 })));
    dialogs.confirm.mockReturnValue(of(true));
    uam.remove.mockReturnValue(of({}));
  });

  it('reads the organization-scoped UAM list (not the search index) and renders rows and stat cards', () => {
    const list = create();
    expect(listService.page).toHaveBeenCalledWith({ code: '', name: '', type: '' }, 0, 10);
    expect(list.rows.length).toBe(1);
    expect(list.totalElements).toBe(25);
    expect(list.totalPages).toBe(3);
    expect(list.stats.map((s) => s.value)).toEqual([25, 1, 0]);
    expect(el().textContent).toContain('APP1');
  });

  it('falls back to the row count when the page has no totals', () => {
    listService.page.mockReturnValue(of({ content: [app({}), app({ id: 'a2', code: 'APP2', active: false })] }));
    const list = create();
    expect(list.totalElements).toBe(2);
    expect(list.totalPages).toBe(1);
    expect(list.stats.map((s) => s.value)).toEqual([2, 1, 1]);
  });

  it('shows the error state and a toast when the list fails', () => {
    listService.page.mockReturnValue(throwError(() => new Error('down')));
    const list = create();
    expect(list.hasError).toBe(true);
    expect(notify.error).toHaveBeenCalledWith('accessManagement.applications.errorMessage');
    expect(el().querySelector('.app-state--error')).not.toBeNull();
  });

  it('filters, clears and pages through the scoped list', () => {
    const list = create();
    list.page = 3;
    list.filters = { code: 'APP1', name: 'One', type: 'WEB' };
    list.applyFilters();
    expect(listService.page).toHaveBeenLastCalledWith({ code: 'APP1', name: 'One', type: 'WEB' }, 0, 10);
    list.clearFilters();
    expect(list.filters).toEqual({ code: '', name: '', type: '' });
    list.goToPage(2);
    expect(listService.page).toHaveBeenLastCalledWith(expect.any(Object), 2, 10);
    list.onPageSizeChange(50);
    expect(listService.page).toHaveBeenLastCalledWith(expect.any(Object), 0, 50);
  });

  it('opens filtered on ?application=<CODE> ', () => {
    query = { application: 'FOOD_DELIVER_APP' };
    create();
    expect(listService.page).toHaveBeenCalledWith({ code: 'FOOD_DELIVER_APP', name: '', type: '' }, 0, 10);
  });

  it('keeps New, Edit and Delete behind their privileges and hands them to the form service', () => {
    granted = new Set(['APPLICATION_UPDATE']);
    create();
    expect(el().querySelector('.table-create-btn')).toBeNull();
    expect(el().querySelector('button.danger')).toBeNull();
    (el().querySelector('button[title="shared.listPage.rowActions.edit"]') as HTMLButtonElement).click();
    expect(adminForm.edit).toHaveBeenCalledWith(expect.objectContaining({ resourcePath: '/applications' }), 'a1');

    TestBed.resetTestingModule();
    granted = null;
    const list = create();
    (el().querySelector('.table-create-btn') as HTMLButtonElement).click();
    expect(adminForm.create).toHaveBeenCalledWith(list.config);
  });

  it('deletes after confirmation and reloads; reports failures', () => {
    const list = create();
    listService.page.mockClear();
    list.confirmDelete(app({ id: 'd1' }));
    expect(uam.remove).toHaveBeenCalledWith('/applications', 'd1');
    expect(notify.success).toHaveBeenCalledWith('accessManagement.applications.messages.deleted');
    expect(listService.page).toHaveBeenCalledTimes(1);

    uam.remove.mockReturnValueOnce(throwError(() => ({ error: { detail: 'In use' } })));
    list.confirmDelete(app({ id: 'd1' }));
    expect(notify.error).toHaveBeenLastCalledWith('In use');
    uam.remove.mockReturnValueOnce(throwError(() => ({ status: 409, error: { success: false, message: 'This application still has 1 roles and 2 users with access. Remove their access first.' } })));
    list.confirmDelete(app({ id: 'd1' }));
    expect(notify.error).toHaveBeenLastCalledWith('This application still has 1 roles and 2 users with access. Remove their access first.');
    uam.remove.mockReturnValueOnce(throwError(() => null));
    list.confirmDelete(app({ id: 'd1' }));
    expect(notify.error).toHaveBeenLastCalledWith('accessManagement.applications.messages.deleteFailed');

    dialogs.confirm.mockReturnValueOnce(of(false));
    uam.remove.mockClear();
    list.confirmDelete(app({ id: 'd1' }));
    expect(uam.remove).not.toHaveBeenCalled();
  });

  describe('go-live governance row action', () => {
    it('links each row to the go-live governance page', () => {
      create();

      const link = el().querySelector('a[href$="/access"]') as HTMLAnchorElement;
      expect(link.getAttribute('href')).toBe('/access-management/applications/APP1/access');
      expect(link.getAttribute('title')).toBe('readiness.governance.page.open');
    });

    it('is hidden without APPLICATION_READINESS_GOVERNANCE_READ', () => {
      granted = new Set(['APPLICATION_UPDATE']);
      create();

      expect(el().querySelector('a[href$="/access"]')).toBeNull();
    });

    it('has no readiness column, badge or summary request, and no application access page', () => {
      const list = create();

      expect(headers().join(' ')).not.toContain('readiness');
      expect(el().querySelector('.readiness-badge')).toBeNull();
      expect(list.governanceLink(app({ code: 'A B' }))).toBe('/access-management/applications/A%20B/access');
    });
  });
});

describe('ApplicationsListService', () => {
  let service: ApplicationsListService;
  let http: HttpTestingController;
  const url = `${environment.uamApiUrl}/applications`;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()], providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ApplicationsListService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends page, size and only the filters that are set', () => {
    let result: SpringPage<Application> | undefined;
    service.page({ code: ' APP1 ', name: '', type: 'WEB' }, 2, 25).subscribe((p) => (result = p));
    const req = http.expectOne((r) => r.url === url);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('25');
    expect(req.request.params.get('code')).toBe('APP1');
    expect(req.request.params.get('type')).toBe('WEB');
    expect(req.request.params.has('name')).toBe(false);
    req.flush({ success: true, message: '', responseObject: page([app({})]) });
    expect(result?.content.length).toBe(1);
  });

  it('gives an empty page for a success without a page, and an error for a refused answer', () => {
    let result: SpringPage<Application> | undefined;
    service.page({ code: '', name: '', type: '' }, 0, 10).subscribe((p) => (result = p));
    http.expectOne((r) => r.url === url).flush({ success: true, message: '', responseObject: null });
    expect(result).toEqual(expect.objectContaining({ content: [], totalElements: 0, number: 0, size: 10 }));

    let error: unknown;
    service.page({ code: '', name: '', type: '' }, 0, 10).subscribe({ error: (e) => (error = e) });
    http.expectOne((r) => r.url === url).flush({ success: false, message: 'Not allowed', responseObject: null });
    expect(error).toEqual({ error: { detail: 'Not allowed', message: 'Not allowed' } });

    service.page({ code: '', name: '', type: '' }, 0, 10).subscribe({ error: (e) => (error = e) });
    http.expectOne((r) => r.url === url).flush({ success: false, message: '', responseObject: null });
    expect(error).toEqual({ error: { detail: 'shared.errors.requestFailed', message: 'shared.errors.requestFailed' } });
  });
});
