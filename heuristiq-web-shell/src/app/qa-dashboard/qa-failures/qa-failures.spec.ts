import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqChartsModule, HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { accessMock, facets, failures, QA_SHARED_DECLARATIONS, routerStub, routeStub } from '../qa-dashboard.fixtures';
import { QaFacets, QaFailures, QaFilters, QaWorkspace } from '../qa-dashboard.models';
import { stubHqCharts } from '../../shared/testing/stub-charts.testing';
import { QaDashboardService } from '../qa-dashboard.service';
import { QaFailuresPage } from './qa-failures';

describe('QaFailuresPage', () => {
  const svc = {
    failures: vi.fn((_w: QaWorkspace, _f: QaFilters): Observable<QaFailures> => of(failures())),
    facets: vi.fn((_w: QaWorkspace): Observable<QaFacets> => of(facets())),
  };
  let granted: Set<string> | null;
  let route: ReturnType<typeof routeStub>;
  let fixture: ComponentFixture<QaFailuresPage>;

  function create(params: Record<string, string> = {}): QaFailuresPage {
    route = routeStub(params);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaFailuresPage, ...QA_SHARED_DECLARATIONS],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule, HqChartsModule],
      providers: [
        { provide: QaDashboardService, useValue: svc },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
        { provide: ActivatedRoute, useValue: route.route },
        { provide: Router, useValue: routerStub() },
      ],
    });
    stubHqCharts();
    fixture = TestBed.createComponent(QaFailuresPage);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = (selector: string): HTMLElement | null => el().querySelector(selector);

  beforeEach(() => {
    vi.clearAllMocks();
    svc.failures.mockImplementation(() => of(failures()));
    svc.facets.mockImplementation(() => of(facets()));
    granted = null;
  });

  it('shows the failures by cause and module, the worst tests and the unstable ones', () => {
    const page = create();
    expect(svc.failures).toHaveBeenCalledWith('UI', expect.anything());
    expect(q('.qa-total-failed')?.textContent).toBe('3');
    expect(el().querySelectorAll('hq-chart-card hq-bar-list')).toHaveLength(2);
    expect(page.categoryItems.map((i) => i.label)).toEqual([page.categoryLabel('TIMEOUT'), page.categoryLabel('MYSTERY')]);
    expect(el().querySelectorAll('.qa-top-failing tbody tr')).toHaveLength(1);
    expect(q('.qa-top-failing tbody')?.textContent).toContain('HQ_RW_036');
    expect(q('.qa-top-failing .qa-mono:last-of-type')).not.toBeNull();
    expect(el().querySelectorAll('.qa-flaky__list li')).toHaveLength(1);
    expect(q('.qa-flaky__list')?.textContent).toContain('uam:login:otp');
    expect(page.isEmpty).toBe(false);
  });

  it('turns the causes and modules into ranked bars with table columns', () => {
    const page = create();
    expect(page.categoryItems).toEqual([
      { label: page.categoryLabel('TIMEOUT'), value: 2 },
      { label: page.categoryLabel('MYSTERY'), value: 1 },
    ]);
    expect(page.moduleItems).toEqual([{ label: 'wallet', value: 2, hint: 'qaDashboard.failures.failedOfTotal' }]);
    expect(page.categoryColumns.map((c) => c.key)).toEqual(['label', 'value']);
    expect(page.categoryColumns[0].label).toBe('qaDashboard.failures.byCategory');
    expect(page.moduleColumns.map((c) => c.key)).toEqual(['label', 'hint']);
    expect(page.moduleColumns[0].label).toBe('qaDashboard.failures.byModule');
  });

  it('clears the bars when the analysis cannot be loaded', () => {
    const page = create();
    expect(page.categoryItems).toHaveLength(2);
    svc.failures.mockImplementation(() => throwError(() => new Error('down')));
    page.reload();
    expect(page.categoryItems).toEqual([]);
    expect(page.moduleItems).toEqual([]);
  });

  it('shows the first line of the last failure message', () => {
    const page = create();
    expect(page.message(page.failures.topFailing[0])).toBe('Timed out');
  });

  it('says nothing is failing when there are no failures and no unstable tests', () => {
    svc.failures.mockImplementation(() => of(failures({ totalFailed: 0, byCategory: [], byModule: [], topFailing: [], flaky: [] })));
    const page = create();
    expect(page.isEmpty).toBe(true);
    expect(q('hq-empty-state')?.textContent).toContain('qaDashboard.failures.empty');
    expect(q('.qa-tiles')).toBeNull();
  });

  it('shows the empty notes of a section that has no rows', () => {
    svc.failures.mockImplementation(() => of(failures({ byCategory: [], byModule: [], topFailing: [], flaky: [{ id: 'b', tcId: null, testKey: 'k', name: 'n', module: 'm', passCount: 1, failCount: 1 }] })));
    const page = create();
    expect(page.categoryItems).toEqual([]);
    expect(page.moduleItems).toEqual([]);
    expect(el().textContent?.split('qaDashboard.charts.empty').length ?? 0).toBeGreaterThanOrEqual(4);
    expect(q('.qa-top-failing')).toBeNull();
  });

  it('shows the note for no unstable tests', () => {
    svc.failures.mockImplementation(() => of(failures({ flaky: [] })));
    create();
    expect(q('.qa-flaky hq-empty-state')?.textContent).toContain('qaDashboard.failures.noFlaky');
  });

  it('reloads with the filters and when the workspace changes', () => {
    const page = create({ workspace: 'API' });
    expect(svc.failures).toHaveBeenLastCalledWith('API', expect.anything());
    page.onFiltersChange({ ...page.filters, release: 'QA_REG_OCT26_R2' });
    expect(svc.failures).toHaveBeenLastCalledWith('API', expect.objectContaining({ release: 'QA_REG_OCT26_R2' }));
    route.setParams({ workspace: 'UI' });
    expect(svc.failures).toHaveBeenLastCalledWith('UI', expect.objectContaining({ release: '' }));
  });

  it('shows an error message when the analysis cannot be loaded', () => {
    svc.failures.mockImplementation(() => throwError(() => new Error('down')));
    const page = create();
    expect(page.hasError).toBe(true);
    expect(page.loaded).toBe(false);
    expect(q('hq-error-state')).not.toBeNull();
  });

  it('shows a loading message while waiting', () => {
    svc.failures.mockImplementation(() => NEVER);
    create();
    expect(q('hq-loading-state')?.textContent).toContain('qaDashboard.loading');
  });

  it('asks for nothing without the failure privilege', () => {
    granted = new Set([QA_DASHBOARD_PRIVILEGES.overviewRead]);
    create();
    expect(svc.failures).not.toHaveBeenCalled();
    expect(q('app-qa-filter-bar')).toBeNull();
  });
});
