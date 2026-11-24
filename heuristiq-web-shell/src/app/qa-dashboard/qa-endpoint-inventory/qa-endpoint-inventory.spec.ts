import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { offeredValues, searchableSelect } from '../../shared/testing/searchable-select.testing';
import { NotificationService } from '../../shared/services/notification-service';
import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { accessMock, coverage, endpoint, endpointOptions, endpointPage, QA_SHARED_DECLARATIONS, routerStub, routeStub } from '../qa-dashboard.fixtures';
import { QaDashboardService } from '../qa-dashboard.service';
import { QaEndpointDetailDialog } from './qa-endpoint-detail-dialog';
import { QA_ENDPOINT_MESSAGE_KEYS, QA_ENDPOINTS_EXPORT_FILE_NAME } from './qa-endpoint-inventory.constants';
import { QaEndpointFilterOptions, QaEndpointPage, QaEndpointQuery } from './qa-endpoint-inventory.models';
import { QaEndpointInventoryPage } from './qa-endpoint-inventory';
import { QaEndpointService } from './qa-endpoint-inventory.service';

const P = QA_DASHBOARD_PRIVILEGES;

describe('QaEndpointInventoryPage', () => {
  const endpoints = {
    list: vi.fn((_q: QaEndpointQuery, _p: number, _s: number): Observable<QaEndpointPage> => of(endpointPage([endpoint('a'), endpoint('b', { method: 'GET', hitCount: 0, mappedTestKeys: [], mappedCount: 0 })]))),
    filters: vi.fn((): Observable<QaEndpointFilterOptions> => of(endpointOptions())),
    exportCsv: vi.fn((_q: QaEndpointQuery): Observable<Blob> => of(new Blob(['a,b']))),
    detail: vi.fn(),
  };
  const dialog = { open: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const router = routerStub();
  let granted: Set<string> | null;
  let route: ReturnType<typeof routeStub>;
  let fixture: ComponentFixture<QaEndpointInventoryPage>;
  let downloads: string[];
  const createObjectURL = vi.fn((_blob: Blob) => 'blob:qa');

  function create(params: Record<string, string> = {}): QaEndpointInventoryPage {
    route = routeStub(params);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaEndpointInventoryPage, ...QA_SHARED_DECLARATIONS],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: QaEndpointService, useValue: endpoints },
        { provide: QaDashboardService, useValue: {} },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
        { provide: ActivatedRoute, useValue: route.route },
        { provide: Router, useValue: router },
        { provide: MatDialog, useValue: dialog },
        { provide: NotificationService, useValue: notify },
      ],
    });
    fixture = TestBed.createComponent(QaEndpointInventoryPage);
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
    downloads = [];
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloads.push(this.download);
    });
    endpoints.list.mockImplementation(() => of(endpointPage([endpoint('a'), endpoint('b', { method: 'GET', hitCount: 0, mappedTestKeys: [], mappedCount: 0 })])));
    endpoints.filters.mockImplementation(() => of(endpointOptions()));
    endpoints.exportCsv.mockImplementation(() => of(new Blob(['a,b'])));
    granted = null;
  });

  afterEach(() => vi.restoreAllMocks());

  describe('opening', () => {
    it('opens in the API workspace when the address names none, and loads the list and the filter options', () => {
      const page = create();
      expect(page.workspace).toBe('API');
      expect(endpoints.list).toHaveBeenCalledWith(expect.objectContaining({ q: '', quick: '' }), 0, 20);
      expect(endpoints.filters).toHaveBeenCalledTimes(1);
      expect(el().querySelectorAll('.qa-endpoint-row')).toHaveLength(2);
    });

    it('says the inventory is about API tests when the UI workspace is open, and loads nothing', () => {
      const page = create({ workspace: 'UI' });
      expect(page.isApiWorkspace).toBe(false);
      expect(endpoints.list).not.toHaveBeenCalled();
      expect(endpoints.filters).not.toHaveBeenCalled();
      expect(q('.qa-ui-empty')).not.toBeNull();
      expect(q('.qa-banner')).toBeNull();
      (q('.qa-switch-api') as HTMLButtonElement).click();
      expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { workspace: 'API' } }));
    });

    it('loads again when the workspace comes back to API, with a clean query', () => {
      const page = create({ workspace: 'UI' });
      route.setParams({ workspace: 'API' });
      expect(page.isApiWorkspace).toBe(true);
      expect(endpoints.list).toHaveBeenCalledTimes(1);
      page.update({ module: 'uam' });
      route.setParams({ workspace: 'UI' });
      route.setParams({ workspace: 'API' });
      expect(endpoints.list).toHaveBeenLastCalledWith(expect.objectContaining({ module: '' }), 0, 20);
    });

    it('asks for nothing and shows nothing without the list privilege', () => {
      granted = new Set([P.endpointRead]);
      create();
      expect(endpoints.list).not.toHaveBeenCalled();
      expect(q('.qa-ui-empty')).toBeNull();
      expect(q('.qa-banner')).toBeNull();
    });

    it('works without the privilege to read the filter options', () => {
      granted = new Set([P.endpointList]);
      const page = create();
      expect(endpoints.filters).not.toHaveBeenCalled();
      expect(page.options).toBeNull();
      expect(el().querySelectorAll('.qa-endpoint-row')).toHaveLength(2);
    });

    it('keeps working when the filter options cannot be loaded', () => {
      endpoints.filters.mockImplementation(() => throwError(() => new Error('down')));
      const page = create();
      expect(page.options).toBeNull();
      expect(page.result).not.toBeNull();
    });
  });

  describe('coverage banner and tiles', () => {
    it('shows the covered share, the bar, the counts and when the catalog was last synced', () => {
      const page = create();
      expect(q('.qa-banner__pct')?.textContent).toContain('qaDashboard.endpointInventory.coveragePct');
      expect(page.progress).toBe(75);
      const bar = q('.qa-progress') as HTMLElement;
      expect(bar.getAttribute('role')).toBe('progressbar');
      expect(bar.getAttribute('aria-valuenow')).toBe('75');
      expect(q('.qa-progress__fill')?.getAttribute('style')).toContain('75%');
      expect(q('.qa-banner__untested')?.textContent).toBe('50');
      expect(q('.qa-banner__undocumented')?.textContent).toBe('3');
      expect(q('.qa-banner__synced')?.textContent).toContain('2026');
      expect(Array.from(el().querySelectorAll('.qa-tile__value')).map((v) => v.textContent)).toEqual(['203', '12', '40', '88']);
    });

    it('says when the catalog was never synced', () => {
      endpoints.list.mockImplementation(() => of(endpointPage([endpoint('a')], { coverage: coverage({ lastSyncedAt: null }) })));
      create();
      expect(q('.qa-banner__synced')?.textContent).toContain('qaDashboard.endpointInventory.neverSynced');
    });

    it('has no sync button: the Jenkins publish syncs the catalog', () => {
      create();
      const buttons = Array.from(el().querySelectorAll('button')).map((b) => `${b.className} ${b.textContent}`).join(' ');
      expect(buttons).not.toMatch(/sync/i);
    });
  });

  describe('table', () => {
    it('writes the method in each badge, the path in monospace and the hits as executions', () => {
      create();
      const rows = el().querySelectorAll('.qa-endpoint-row');
      expect(rows[0].querySelector('.qa-method')?.textContent).toBe('POST');
      expect(rows[0].querySelector('.qa-method')?.className).toContain('qa-method--post');
      expect(rows[1].querySelector('.qa-method')?.className).toContain('qa-method--get');
      expect(rows[0].querySelector('.qa-endpoint-path')?.className).toContain('qa-mono');
      expect(rows[0].querySelector('.qa-hits')?.textContent).toContain('qaDashboard.endpointInventory.executionOther');
      expect(rows[0].querySelector('.qa-not-tested')).toBeNull();
    });

    it('uses the singular for one execution and says an endpoint was not tested', () => {
      endpoints.list.mockImplementation(() => of(endpointPage([endpoint('a', { hitCount: 1 }), endpoint('b', { hitCount: 0, mappedTestKeys: [], mappedCount: 0 })])));
      create();
      const rows = el().querySelectorAll('.qa-endpoint-row');
      expect(rows[0].querySelector('.qa-hits')?.textContent).toContain('qaDashboard.endpointInventory.executionOne');
      expect(rows[1].querySelector('.qa-not-tested')).not.toBeNull();
      expect(rows[1].querySelector('.qa-hits')).toBeNull();
    });

    it('lists the first three mapped test cases and "+n" for the rest', () => {
      create();
      const row = el().querySelectorAll('.qa-endpoint-row')[0];
      const chips = Array.from(row.querySelectorAll('.qa-chip')).map((c) => c.textContent?.trim());
      expect(chips).toHaveLength(4);
      expect(chips.slice(0, 3)).toEqual(['uam:users:block', 'uam:users:unblock', 'uam:login:otp']);
      expect(row.querySelector('.qa-chip--more')?.textContent).toContain('qaDashboard.endpointInventory.moreScenarios');
      expect(el().querySelectorAll('.qa-endpoint-row')[1].querySelectorAll('.qa-chip')).toHaveLength(0);
    });

    it('marks an endpoint that is not documented in words', () => {
      endpoints.list.mockImplementation(() => of(endpointPage([endpoint('a', { documented: false, privilegeName: null })])));
      create();
      expect(q('.qa-undocumented')?.textContent).toContain('qaDashboard.endpointInventory.undocumented');
    });

    it('says the catalog was never synced when there is nothing at all, and no match when filters are set', () => {
      endpoints.list.mockImplementation(() => of(endpointPage([], { coverage: coverage({ totalEndpoints: 0, documented: 0, tested: 0 }) })));
      const page = create();
      expect(page.isCatalogEmpty).toBe(true);
      expect(q('.qa-empty')?.textContent).toContain('qaDashboard.endpointInventory.emptyCatalog');
      page.update({ q: 'zzz' });
      refresh();
      expect(page.isCatalogEmpty).toBe(false);
      expect(q('.qa-empty')?.textContent).toContain('qaDashboard.endpointInventory.emptyFiltered');
    });

    it('shows an error message when the list cannot be loaded', () => {
      endpoints.list.mockImplementation(() => throwError(() => new Error('down')));
      const page = create();
      expect(page.hasError).toBe(true);
      expect(page.result).toBeNull();
      expect(q('.qa-state--error')).not.toBeNull();
    });

    it('shows a loading message while waiting', () => {
      endpoints.list.mockImplementation(() => NEVER);
      create();
      expect(q('.qa-state')?.textContent).toContain('qaDashboard.loading');
    });

    it('pages through the results and tracks rows by id', () => {
      endpoints.list.mockImplementation((_q, p) => of(endpointPage([endpoint('a')], { page: p, totalPages: 3, totalElements: 50 })));
      const page = create();
      expect(q('.qa-pager')).not.toBeNull();
      (q('.qa-next') as HTMLButtonElement).click();
      expect(endpoints.list).toHaveBeenLastCalledWith(expect.anything(), 1, 20);
      refresh();
      (q('.qa-prev') as HTMLButtonElement).click();
      expect(endpoints.list).toHaveBeenLastCalledWith(expect.anything(), 0, 20);
      page.previousPage();
      expect(endpoints.list).toHaveBeenCalledTimes(3);
      page.loadPage(2);
      page.nextPage();
      expect(endpoints.list).toHaveBeenCalledTimes(4);
      expect(page.trackByEndpoint(0, endpoint('z'))).toBe('z');
    });

    it('does not page without a result', () => {
      endpoints.list.mockImplementation(() => throwError(() => new Error('down')));
      const page = create();
      page.nextPage();
      page.previousPage();
      expect(endpoints.list).toHaveBeenCalledTimes(1);
    });
  });

  describe('toolbar', () => {
    it('shows the record count', () => {
      endpoints.list.mockImplementation(() => of(endpointPage([endpoint('a')], { totalElements: 203 })));
      create();
      expect(q('.qa-count')?.textContent).toContain('qaDashboard.endpointInventory.recordCount');
    });

    it('offers the modules, the sub-modules of the chosen module and the methods', async () => {
      const page = create();
      expect(offeredValues(fixture, '.qa-module')).toEqual(['uam', 'wallet']);
      expect(offeredValues(fixture, '.qa-method-filter')).toEqual(['GET', 'POST', 'PUT', 'DELETE']);
      expect(page.subModules).toEqual([]);
      page.onModuleChange('uam');
      expect(page.subModules).toEqual(['users', 'roles']);
      expect(endpoints.list).toHaveBeenLastCalledWith(expect.objectContaining({ module: 'uam', subModule: '' }), 0, 20);
      page.update({ subModule: 'roles' });
      page.onModuleChange('wallet');
      expect(page.query.subModule).toBe('');
      refresh();
      await fixture.whenStable();
      expect(searchableSelect(fixture, '.qa-sub-module').disabled).toBe(false);
    });

    it('filters by method and by tested, untested and undocumented from the first page', () => {
      const page = create();
      page.update({ method: 'GET' });
      expect(endpoints.list).toHaveBeenLastCalledWith(expect.objectContaining({ method: 'GET' }), 0, 20);
      (q('.qa-quick__option[data-quick="untested"]') as HTMLButtonElement).click();
      expect(endpoints.list).toHaveBeenLastCalledWith(expect.objectContaining({ quick: 'untested' }), 0, 20);
      refresh();
      expect(q('.qa-quick__option[data-quick="untested"]')?.getAttribute('aria-checked')).toBe('true');
      expect(q('.qa-quick__option[data-quick="all"]')?.getAttribute('aria-checked')).toBe('false');
      const calls = endpoints.list.mock.calls.length;
      page.setQuick('untested');
      expect(endpoints.list).toHaveBeenCalledTimes(calls);
      (q('.qa-quick__option[data-quick="undocumented"]') as HTMLButtonElement).click();
      expect(endpoints.list).toHaveBeenLastCalledWith(expect.objectContaining({ quick: 'undocumented' }), 0, 20);
    });

    it('waits for a pause in typing before it searches', () => {
      vi.useFakeTimers();
      try {
        const page = create();
        const calls = endpoints.list.mock.calls.length;
        page.onSearchInput('blo');
        vi.advanceTimersByTime(200);
        page.onSearchInput('block');
        vi.advanceTimersByTime(200);
        expect(endpoints.list).toHaveBeenCalledTimes(calls);
        vi.advanceTimersByTime(300);
        expect(endpoints.list).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'block' }), 0, 20);
      } finally {
        vi.useRealTimers();
      }
    });

    it('changes the page size and starts from the first page', () => {
      const page = create();
      (q('.qa-page-size') as HTMLSelectElement).value = '50';
      page.onPageSizeChange('50');
      expect(page.pageSize).toBe(50);
      expect(endpoints.list).toHaveBeenLastCalledWith(expect.anything(), 0, 50);
    });

    it('offers to clear the filters only when one is set, and clears them all', () => {
      const page = create();
      expect(q('.qa-clear')).toBeNull();
      page.update({ method: 'GET', q: 'x' });
      refresh();
      expect(page.isFiltered).toBe(true);
      (q('.qa-clear') as HTMLButtonElement).click();
      expect(page.query).toEqual({ q: '', module: '', subModule: '', method: '', quick: '' });
      expect(page.searchText).toBe('');
      expect(endpoints.list).toHaveBeenLastCalledWith(expect.objectContaining({ method: '' }), 0, 20);
    });

    it('refreshes the page the user is on', () => {
      endpoints.list.mockImplementation((_q, p) => of(endpointPage([endpoint('a')], { page: p, totalPages: 3 })));
      const page = create();
      page.loadPage(2);
      page.reload();
      expect(endpoints.list).toHaveBeenLastCalledWith(expect.anything(), 2, 20);
    });

    it('does not refresh while the UI workspace is open', () => {
      const page = create({ workspace: 'UI' });
      page.reload();
      expect(endpoints.list).not.toHaveBeenCalled();
    });
  });

  describe('export', () => {
    it('saves the filtered list as a file', () => {
      const page = create();
      page.update({ quick: 'undocumented' });
      (q('.qa-export-csv') as HTMLButtonElement).click();
      expect(endpoints.exportCsv).toHaveBeenCalledWith(expect.objectContaining({ quick: 'undocumented' }));
      expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
      expect(downloads).toEqual([QA_ENDPOINTS_EXPORT_FILE_NAME]);
    });

    it('reports a failed export', () => {
      endpoints.exportCsv.mockImplementation(() => throwError(() => new Error('down')));
      create();
      (q('.qa-export-csv') as HTMLButtonElement).click();
      expect(notify.error).toHaveBeenCalledWith(QA_ENDPOINT_MESSAGE_KEYS.exportFailed);
      expect(downloads).toEqual([]);
    });

    it('has no export without the privilege', () => {
      granted = new Set([P.endpointList]);
      const page = create();
      expect(q('.qa-export-csv')).toBeNull();
      page.export();
      expect(endpoints.exportCsv).not.toHaveBeenCalled();
    });
  });

  describe('details', () => {
    it('opens the details of a row in a dialog', () => {
      create();
      (q('.qa-details') as HTMLButtonElement).click();
      expect(dialog.open).toHaveBeenCalledWith(QaEndpointDetailDialog, expect.objectContaining({ data: expect.objectContaining({ id: 'a' }) }));
    });

    it('has no details action and opens nothing without the read privilege', () => {
      granted = new Set([P.endpointList]);
      const page = create();
      expect(q('.qa-details')).toBeNull();
      page.openDetails(endpoint('a'));
      expect(dialog.open).not.toHaveBeenCalled();
    });
  });
});
