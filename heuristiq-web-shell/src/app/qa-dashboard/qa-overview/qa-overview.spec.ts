import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { accessMock, facets, overview, QA_SHARED_DECLARATIONS, routerStub, routeStub, totals } from '../qa-dashboard.fixtures';
import { QaFacets, QaFilters, QaOverview, QaOverviewMode, QaWorkspace } from '../qa-dashboard.models';
import { QaDashboardService } from '../qa-dashboard.service';
import { QaOverviewPage } from './qa-overview';

describe('QaOverviewPage', () => {
  const svc = {
    overview: vi.fn((_w: QaWorkspace, _f: QaFilters, _m: QaOverviewMode): Observable<QaOverview> => of(overview())),
    facets: vi.fn((_w: QaWorkspace): Observable<QaFacets> => of(facets())),
  };
  const router = routerStub();
  let granted: Set<string> | null;
  let route: ReturnType<typeof routeStub>;
  let fixture: ComponentFixture<QaOverviewPage>;

  function create(params: Record<string, string> = {}): QaOverviewPage {
    route = routeStub(params);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaOverviewPage, ...QA_SHARED_DECLARATIONS],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: QaDashboardService, useValue: svc },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
        { provide: ActivatedRoute, useValue: route.route },
        { provide: Router, useValue: router },
      ],
    });
    fixture = TestBed.createComponent(QaOverviewPage);
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
    svc.overview.mockImplementation(() => of(overview()));
    svc.facets.mockImplementation(() => of(facets()));
    granted = null;
  });

  it('loads the overview and the filter options of the UI workspace on entry', () => {
    const page = create();
    expect(page.workspace).toBe('UI');
    expect(svc.overview).toHaveBeenCalledWith('UI', expect.objectContaining({ release: '' }), 'unique');
    expect(svc.facets).toHaveBeenCalledWith('UI');
    expect(page.hasData).toBe(true);
    expect(el().querySelectorAll('.qa-tile')).toHaveLength(5);
    expect(q('.qa-tile--total .qa-tile__value')?.textContent).toContain('10');
    expect(q('.qa-tile--fail .qa-tile__value')?.textContent).toContain('2');
    expect(el().querySelectorAll('app-qa-release-chart, app-qa-donut-chart, app-qa-line-chart')).toHaveLength(4);
  });

  it('builds the trend and volume lines from the days the server sent', () => {
    const page = create();
    expect(page.trendSeries.map((s) => s.key)).toEqual(['PASSED', 'FAILED']);
    expect(page.trendSeries[0].values).toEqual([{ label: '2026-10-25', value: 5 }, { label: '2026-10-26', value: 7 }]);
    expect(page.volumeSeries[0].values.map((v) => v.value)).toEqual([10, 20]);
  });

  it('reads the workspace from the address and reloads when it changes', () => {
    const page = create({ workspace: 'API' });
    expect(page.workspace).toBe('API');
    expect(svc.overview).toHaveBeenLastCalledWith('API', expect.anything(), 'unique');
    route.setParams({ workspace: 'UI' });
    expect(svc.overview).toHaveBeenLastCalledWith('UI', expect.anything(), 'unique');
    expect(svc.overview).toHaveBeenCalledTimes(2);
    expect(svc.facets).toHaveBeenCalledTimes(2);
  });

  it('puts a new workspace in the address through the header switch', () => {
    create();
    (q('.qa-switch__option[data-workspace="API"]') as HTMLButtonElement).click();
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { workspace: 'API' }, queryParamsHandling: 'merge' }));
    router.navigate.mockClear();
    (q('.qa-switch__option[data-workspace="UI"]') as HTMLButtonElement).click();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('switches between overall and unique counting', () => {
    const page = create();
    (q('.qa-mode__option[data-mode="overall"]') as HTMLButtonElement).click();
    expect(page.mode).toBe('overall');
    expect(svc.overview).toHaveBeenLastCalledWith('UI', expect.anything(), 'overall');
    svc.overview.mockClear();
    page.setMode('overall');
    expect(svc.overview).not.toHaveBeenCalled();
  });

  it('reloads with the filters the filter bar sends', () => {
    const page = create();
    page.onFiltersChange({ ...page.filters, release: 'QA_REG_OCT26_R2', from: '2026-10-01' });
    expect(svc.overview).toHaveBeenLastCalledWith('UI', expect.objectContaining({ release: 'QA_REG_OCT26_R2', from: '2026-10-01' }), 'unique');
  });

  it('shows an empty message and no charts when there are no runs', () => {
    svc.overview.mockImplementation(() => of(overview({ totals: totals({ total: 0, passed: 0, failed: 0, skipped: 0 }), releases: [], trend: [], volume: [] })));
    const page = create();
    expect(page.hasData).toBe(false);
    expect(q('.qa-empty')).not.toBeNull();
    expect(q('.qa-tiles')).toBeNull();
  });

  it('shows an error message when the overview cannot be loaded', () => {
    svc.overview.mockImplementation(() => throwError(() => new Error('down')));
    const page = create();
    expect(page.hasError).toBe(true);
    expect(page.isLoading).toBe(false);
    expect(q('.qa-state--error')).not.toBeNull();
    expect(page.hasData).toBe(false);
  });

  it('shows a loading message while the first answer is awaited', () => {
    svc.overview.mockImplementation(() => NEVER);
    const page = create();
    expect(page.isLoading).toBe(true);
    expect(q('.qa-state')?.textContent).toContain('qaDashboard.loading');
  });

  it('keeps working when the filter options cannot be loaded', () => {
    svc.facets.mockImplementation(() => throwError(() => new Error('down')));
    const page = create();
    expect(page.facets).toBeNull();
    expect(page.hasData).toBe(true);
  });

  it('asks for nothing and shows nothing without the overview privilege', () => {
    granted = new Set([QA_DASHBOARD_PRIVILEGES.filterRead]);
    create();
    expect(svc.overview).not.toHaveBeenCalled();
    expect(q('.qa-tiles')).toBeNull();
    expect(q('app-qa-filter-bar')).toBeNull();
  });

  it('does not ask for filter options without the filter privilege', () => {
    granted = new Set([QA_DASHBOARD_PRIVILEGES.overviewRead]);
    create();
    expect(svc.facets).not.toHaveBeenCalled();
    expect(svc.overview).toHaveBeenCalled();
    refresh();
  });
});
