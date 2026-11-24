import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { QA_SEARCH_DEBOUNCE_MS } from '../qa-dashboard.constants';
import { accessMock, QA_SHARED_DECLARATIONS, routerStub, routeStub, studioCase, studioItem, studioPage } from '../qa-dashboard.fixtures';
import { QaDashboardService } from '../qa-dashboard.service';
import { QA_STUDIO_MESSAGE_KEYS, QA_STUDIO_PRIVILEGES as P, QA_STUDIO_ROUTES } from './qa-studio.constants';
import { QaStudioCase, QaStudioCaseItem, QaStudioInput, QaStudioPage, QaStudioQuery } from './qa-studio.models';
import { QaStudioListPage } from './qa-studio-list';
import { QaStudioService } from './qa-studio.service';

describe('QaStudioListPage', () => {
  const studio = {
    list: vi.fn((_q: QaStudioQuery, _p: number, _s: number): Observable<QaStudioPage> => of(studioPage([studioItem('a'), studioItem('b', { tcId: 'HQ_ST_002', enabled: false })]))),
    get: vi.fn((_id: string): Observable<QaStudioCase> => of(studioCase('a'))),
    create: vi.fn((_i: QaStudioInput): Observable<QaStudioCase> => of(studioCase('copy', { tcId: 'HQ_ST_009' }))),
    setEnabled: vi.fn((_id: string, _e: boolean): Observable<QaStudioCase> => of(studioCase('a'))),
    delete: vi.fn((_id: string): Observable<void> => of(undefined)),
  };
  const dialogs = { confirm: vi.fn((_d: unknown): Observable<boolean> => of(true)) };
  const notify = { success: vi.fn(), error: vi.fn() };
  const router = routerStub();
  let granted: Set<string> | null;
  let route: ReturnType<typeof routeStub>;
  let fixture: ComponentFixture<QaStudioListPage>;

  function create(params: Record<string, string> = {}): QaStudioListPage {
    route = routeStub(params);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaStudioListPage, ...QA_SHARED_DECLARATIONS],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: QaStudioService, useValue: studio },
        { provide: QaDashboardService, useValue: {} },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
        { provide: ActivatedRoute, useValue: route.route },
        { provide: Router, useValue: router },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
      ],
    });
    fixture = TestBed.createComponent(QaStudioListPage);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = (selector: string): HTMLElement | null => el().querySelector(selector);
  const refresh = (): void => {
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  };

  beforeEach(() => {
    vi.clearAllMocks();
    studio.list.mockImplementation(() => of(studioPage([studioItem('a'), studioItem('b', { tcId: 'HQ_ST_002', enabled: false })])));
    studio.get.mockImplementation(() => of(studioCase('a')));
    studio.create.mockImplementation(() => of(studioCase('copy', { tcId: 'HQ_ST_009' })));
    studio.setEnabled.mockImplementation(() => of(studioCase('a')));
    studio.delete.mockImplementation(() => of(undefined));
    dialogs.confirm.mockImplementation(() => of(true));
    granted = null;
  });

  describe('opening', () => {
    it('opens in the API workspace and lists the cases', () => {
      const page = create();
      expect(page.workspace).toBe('API');
      expect(studio.list).toHaveBeenCalledWith(expect.objectContaining({ q: '' }), 0, 20);
      const rows = el().querySelectorAll('.qa-case-row');
      expect(rows).toHaveLength(2);
      expect(rows[0].textContent).toContain('HQ_ST_001');
      expect(rows[0].querySelector('.qa-method')?.textContent).toBe('POST');
      expect(rows[0].querySelector('.qa-case-path')?.textContent).toBe('/api/v1/uam/users/42/block');
    });

    it('says the studio is about API tests when the UI workspace is open, and loads nothing', () => {
      const page = create({ workspace: 'UI' });
      expect(page.isApiWorkspace).toBe(false);
      expect(studio.list).not.toHaveBeenCalled();
      expect(q('.qa-ui-empty')).not.toBeNull();
      expect(q('.qa-new-case')).toBeNull();
      (q('.qa-switch-api') as HTMLButtonElement).click();
      expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { workspace: 'API' } }));
    });

    it('starts from a clean query when the workspace comes back to API', () => {
      const page = create({ workspace: 'UI' });
      route.setParams({ workspace: 'API' });
      page.update({ module: 'uam' });
      route.setParams({ workspace: 'UI' });
      route.setParams({ workspace: 'API' });
      expect(studio.list).toHaveBeenLastCalledWith(expect.objectContaining({ module: '' }), 0, 20);
    });

    it('asks for nothing without the list privilege', () => {
      granted = new Set([P.read]);
      create();
      expect(studio.list).not.toHaveBeenCalled();
      expect(q('.qa-studio-table')).toBeNull();
    });

    it('shows an empty message, another one when filters are set', () => {
      studio.list.mockImplementation(() => of(studioPage([])));
      const page = create();
      expect(q('.qa-empty')?.textContent).toContain('qaDashboard.studio.list.empty');
      page.update({ q: 'zzz' });
      refresh();
      expect(q('.qa-empty')?.textContent).toContain('qaDashboard.studio.list.emptyFiltered');
    });

    it('shows an error message when the list cannot be loaded, and a loading message while waiting', () => {
      studio.list.mockImplementation(() => throwError(() => new Error('down')));
      const page = create();
      expect(page.hasError).toBe(true);
      expect(page.result).toBeNull();
      expect(q('.qa-state--error')).not.toBeNull();
      studio.list.mockImplementation(() => NEVER);
      create();
      expect(q('.qa-state')?.textContent).toContain('qaDashboard.loading');
    });
  });

  describe('filters and paging', () => {
    it('searches after a pause in typing, with the search and sub-module together', () => {
      vi.useFakeTimers();
      try {
        const page = create();
        const calls = studio.list.mock.calls.length;
        page.onSearchInput('blo');
        vi.advanceTimersByTime(200);
        page.onSearchInput('block');
        page.onSubModuleInput('users');
        vi.advanceTimersByTime(QA_SEARCH_DEBOUNCE_MS - 100);
        expect(studio.list).toHaveBeenCalledTimes(calls);
        vi.advanceTimersByTime(200);
        expect(studio.list).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'block', subModule: 'users' }), 0, 20);
      } finally {
        vi.useRealTimers();
      }
    });

    it('filters by module, suite and enabled from the first page, and clears them', () => {
      const page = create();
      page.update({ module: 'uam' });
      page.update({ suite: 'SMOKE' });
      page.update({ enabled: 'false' });
      expect(studio.list).toHaveBeenLastCalledWith({ q: '', module: 'uam', subModule: '', suite: 'SMOKE', enabled: 'false' }, 0, 20);
      refresh();
      expect(page.isFiltered).toBe(true);
      (q('.qa-clear') as HTMLButtonElement).click();
      expect(page.query).toEqual({ q: '', module: '', subModule: '', suite: '', enabled: '' });
      expect(page.searchText).toBe('');
      expect(page.subModuleText).toBe('');
    });

    it('pages through the results', () => {
      studio.list.mockImplementation((_q, p) => of(studioPage([studioItem('a')], { page: p, totalPages: 3, totalElements: 50 })));
      const page = create();
      (q('.qa-next') as HTMLButtonElement).click();
      expect(studio.list).toHaveBeenLastCalledWith(expect.anything(), 1, 20);
      refresh();
      (q('.qa-prev') as HTMLButtonElement).click();
      expect(studio.list).toHaveBeenLastCalledWith(expect.anything(), 0, 20);
      page.previousPage();
      expect(studio.list).toHaveBeenCalledTimes(3);
      page.loadPage(2);
      page.nextPage();
      expect(studio.list).toHaveBeenCalledTimes(4);
      expect(page.trackByCase(0, studioItem('z'))).toBe('z');
    });

    it('does not page without a result, and reloads the page it is on', () => {
      studio.list.mockImplementation(() => throwError(() => new Error('down')));
      const page = create();
      page.nextPage();
      page.previousPage();
      expect(studio.list).toHaveBeenCalledTimes(1);
      studio.list.mockImplementation((_q, p) => of(studioPage([studioItem('a')], { page: p, totalPages: 3 })));
      page.loadPage(2);
      page.reload();
      expect(studio.list).toHaveBeenLastCalledWith(expect.anything(), 2, 20);
    });

    it('does not reload while the UI workspace is open', () => {
      const page = create({ workspace: 'UI' });
      page.reload();
      expect(studio.list).not.toHaveBeenCalled();
    });
  });

  describe('enable switch', () => {
    it('switches a case on or off at once and tells the server', () => {
      const page = create();
      const sw = el().querySelectorAll('.qa-enabled-switch')[1] as HTMLInputElement;
      expect(sw.getAttribute('role')).toBe('switch');
      expect(sw.checked).toBe(false);
      sw.checked = true;
      sw.dispatchEvent(new Event('change'));
      expect(studio.setEnabled).toHaveBeenCalledWith('b', true);
      expect(page.result?.items[1].enabled).toBe(true);
      expect(page.busyId).toBeNull();
    });

    it('puts the switch back and says so when the server refuses', () => {
      studio.setEnabled.mockImplementation(() => throwError(() => new Error('down')));
      const page = create();
      page.toggleEnabled(page.result!.items[0], false);
      expect(page.result?.items[0].enabled).toBe(true);
      expect(notify.error).toHaveBeenCalledWith(QA_STUDIO_MESSAGE_KEYS.enableFailed);
      expect(page.busyId).toBeNull();
    });

    it('shows the state as text, without a switch, to a user who may not change it', () => {
      granted = new Set([P.list, P.read]);
      const page = create();
      expect(q('.qa-enabled-switch')).toBeNull();
      expect(el().querySelectorAll('.qa-enabled-text')).toHaveLength(2);
      page.toggleEnabled(page.result!.items[0], false);
      expect(studio.setEnabled).not.toHaveBeenCalled();
    });
  });

  describe('row actions', () => {
    it('links to the editor, as "edit" for a user who can change cases and "view" for one who cannot', () => {
      const page = create();
      expect(page.canUpdate).toBe(true);
      expect(q('.qa-edit')?.getAttribute('aria-label')).toBe('qaDashboard.studio.list.edit');
      granted = new Set([P.list, P.read]);
      const readOnly = create();
      expect(readOnly.canUpdate).toBe(false);
      expect(q('.qa-edit')?.getAttribute('aria-label')).toBe('qaDashboard.studio.list.view');
    });

    it('offers each action only behind its own privilege', () => {
      granted = new Set([P.list]);
      create();
      expect(q('.qa-edit')).toBeNull();
      expect(q('.qa-duplicate')).toBeNull();
      expect(q('.qa-delete')).toBeNull();
      expect(q('.qa-new-case')).toBeNull();
      granted = new Set([P.list, P.create]);
      create();
      expect(q('.qa-duplicate')).not.toBeNull();
      expect(q('.qa-new-case')).not.toBeNull();
      expect(q('.qa-delete')).toBeNull();
      granted = new Set([P.list, P.delete]);
      create();
      expect(q('.qa-delete')).not.toBeNull();
      expect(q('.qa-duplicate')).toBeNull();
    });

    it('copies a case as a switched-off copy and opens it', () => {
      create();
      (q('.qa-duplicate') as HTMLButtonElement).click();
      expect(studio.get).toHaveBeenCalledWith('a');
      const sent = studio.create.mock.calls[0][0];
      expect(sent.enabled).toBe(false);
      expect(sent.name).toBe(`${studioCase('a').name} ${QA_STUDIO_MESSAGE_KEYS.copySuffix}`);
      expect(notify.success).toHaveBeenCalledWith(QA_STUDIO_MESSAGE_KEYS.duplicated);
      expect(router.navigate).toHaveBeenCalledWith([QA_STUDIO_ROUTES.list, 'copy']);
    });

    it('reports a copy that failed, at either step', () => {
      studio.get.mockImplementation(() => throwError(() => new Error('down')));
      const page = create();
      page.duplicate(page.result!.items[0]);
      expect(notify.error).toHaveBeenCalledWith(QA_STUDIO_MESSAGE_KEYS.duplicateFailed);
      studio.get.mockImplementation(() => of(studioCase('a')));
      studio.create.mockImplementation(() => throwError(() => new Error('down')));
      page.duplicate(page.result!.items[0]);
      expect(notify.error).toHaveBeenCalledTimes(2);
      expect(page.busyId).toBeNull();
    });

    it('does not copy without the create privilege', () => {
      granted = new Set([P.list]);
      const page = create();
      page.duplicate(studioItem('a'));
      expect(studio.get).not.toHaveBeenCalled();
    });

    it('asks first, then deletes and reloads', () => {
      create();
      (q('.qa-delete') as HTMLButtonElement).click();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: QA_STUDIO_MESSAGE_KEYS.deleteTitle, variant: 'danger' }));
      expect(studio.delete).toHaveBeenCalledWith('a');
      expect(notify.success).toHaveBeenCalledWith(QA_STUDIO_MESSAGE_KEYS.deleted);
      expect(studio.list).toHaveBeenCalledTimes(2);
    });

    it('does nothing when the user declines, and reports a failed delete', () => {
      dialogs.confirm.mockImplementation(() => of(false));
      const page = create();
      page.confirmDelete(studioItem('a'));
      expect(studio.delete).not.toHaveBeenCalled();
      dialogs.confirm.mockImplementation(() => of(true));
      studio.delete.mockImplementation(() => throwError(() => new Error('down')));
      page.confirmDelete(studioItem('a'));
      expect(notify.error).toHaveBeenCalledWith(QA_STUDIO_MESSAGE_KEYS.deleteFailed);
      expect(page.busyId).toBeNull();
    });

    it('goes back a page when the last row of a later page is deleted', () => {
      studio.list.mockImplementation((_q, p) => of(studioPage([studioItem('a')], { page: p, totalPages: 2, totalElements: 21 })));
      const page = create();
      page.loadPage(1);
      page.confirmDelete(studioItem('a'));
      expect(studio.list).toHaveBeenLastCalledWith(expect.anything(), 0, 20);
    });

    it('does not delete without the privilege', () => {
      granted = new Set([P.list]);
      const page = create();
      page.confirmDelete(studioItem('a'));
      expect(dialogs.confirm).not.toHaveBeenCalled();
    });
  });

  it('labels the sign-in profile and keeps an unknown one as it is', () => {
    const page = create();
    expect(page.authLabel('QA_ADMIN')).toBe('qaDashboard.studio.auth.QA_ADMIN');
    expect(page.authLabel('SOMETHING')).toBe('SOMETHING');
    expect(page.methodClass('PATCH')).toBe('qa-method--patch');
  });
});
