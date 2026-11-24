import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { accessMock, facets, QA_SHARED_DECLARATIONS, routerStub, routeStub, row, totals } from '../qa-dashboard.fixtures';
import { QaExecutiveSummary, QaFacets, QaFilters, QaWorkspace } from '../qa-dashboard.models';
import { QaDashboardService } from '../qa-dashboard.service';
import { QaExecutiveSummaryPage } from './qa-executive-summary';

const summary = (over: Partial<QaExecutiveSummary> = {}): QaExecutiveSummary => ({
  generatedAt: '2026-10-26T10:00:00Z',
  workspace: 'UI',
  release: 'QA_REG_OCT26_R2',
  environment: 'qa',
  totals: totals(),
  previousRelease: { release: 'QA_REG_OCT25_R1', totals: totals({ failed: 4 }) },
  headline: ['FAILURES_DOWN', 'SOMETHING_NEW'],
  topRisks: [row('a'), row('b', { tcId: null, failureCategory: null })],
  slowest: [row('c', { durationMs: 300_000 })],
  modules: [{ module: 'wallet', passed: 3, failed: 2, total: 5 }],
  ...over,
});

describe('QaExecutiveSummaryPage', () => {
  const svc = {
    executiveSummary: vi.fn((_w: QaWorkspace, _f: QaFilters): Observable<QaExecutiveSummary> => of(summary())),
    facets: vi.fn((_w: QaWorkspace): Observable<QaFacets> => of(facets())),
  };
  let print: ReturnType<typeof vi.spyOn>;
  let granted: Set<string> | null;
  let route: ReturnType<typeof routeStub>;
  let fixture: ComponentFixture<QaExecutiveSummaryPage>;

  function create(params: Record<string, string> = {}): QaExecutiveSummaryPage {
    route = routeStub(params);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaExecutiveSummaryPage, ...QA_SHARED_DECLARATIONS],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: QaDashboardService, useValue: svc },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
        { provide: ActivatedRoute, useValue: route.route },
        { provide: Router, useValue: routerStub() },
      ],
    });
    fixture = TestBed.createComponent(QaExecutiveSummaryPage);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = (selector: string): HTMLElement | null => el().querySelector(selector);

  afterEach(() => print.mockRestore());

  beforeEach(() => {
    vi.clearAllMocks();
    print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    svc.executiveSummary.mockImplementation(() => of(summary()));
    svc.facets.mockImplementation(() => of(facets()));
    granted = null;
  });

  it('shows the headline, the numbers, the modules, the risks and the slowest tests', () => {
    const page = create();
    expect(svc.executiveSummary).toHaveBeenCalledWith('UI', expect.anything());
    expect(page.hasRuns).toBe(true);
    const headline = Array.from(el().querySelectorAll('.qa-headline__item')).map((li) => li.textContent?.trim());
    // A code with a translation shows its text (here the key itself, no translations are loaded); an unknown code shows as it is.
    expect(headline).toEqual(['FAILURES_DOWN', 'SOMETHING_NEW']);
    expect(el().querySelectorAll('.qa-tile')).toHaveLength(5);
    expect(el().querySelectorAll('.qa-modules tbody tr')).toHaveLength(1);
    expect(el().querySelectorAll('.qa-risks li')).toHaveLength(2);
    expect(q('.qa-risks')?.textContent).toContain('HQ_RW_036');
    expect(q('.qa-risks')?.textContent).toContain('riderweb:wallet.agent_load');
    expect(el().querySelectorAll('.qa-slowest li')).toHaveLength(1);
  });

  it('compares the failures with the previous release', () => {
    const page = create();
    expect(page.failureDelta).toBe(-2);
    expect(q('.qa-tile--fail .qa-tile__sub')?.textContent).toContain('qaDashboard.executive.failureDelta');
  });

  it('has no comparison when there is no previous release', () => {
    svc.executiveSummary.mockImplementation(() => of(summary({ previousRelease: null })));
    const page = create();
    expect(page.failureDelta).toBeNull();
    expect(q('.qa-tile--fail .qa-tile__sub')).toBeNull();
  });

  it('limits the lists', () => {
    const many = Array.from({ length: 15 }, (_, i) => row(`r${i}`));
    svc.executiveSummary.mockImplementation(() => of(summary({ topRisks: many, slowest: many })));
    const page = create();
    expect(page.risks).toHaveLength(10);
    expect(page.slowest).toHaveLength(5);
  });

  it('says there are no risks, no modules and no slow tests when the lists are empty', () => {
    svc.executiveSummary.mockImplementation(() => of(summary({ topRisks: [], slowest: [], modules: [] })));
    create();
    expect(q('.qa-risks')).toBeNull();
    expect(q('.qa-modules')).toBeNull();
    expect(el().querySelectorAll('.qa-card .qa-state').length).toBe(3);
  });

  it('says there is nothing to summarise when no tests ran', () => {
    svc.executiveSummary.mockImplementation(() => of(summary({ totals: totals({ total: 0 }), headline: ['NO_RUNS'] })));
    const page = create();
    expect(page.hasRuns).toBe(false);
    expect(q('.qa-empty')).not.toBeNull();
    expect(q('.qa-page')).toBeNull();
  });

  it('prints the page', () => {
    const page = create();
    (q('.qa-print') as HTMLButtonElement).click();
    expect(print).toHaveBeenCalledTimes(1);
    page.print();
    expect(print).toHaveBeenCalledTimes(2);
  });

  it('does not offer to print before there is something to print', () => {
    svc.executiveSummary.mockImplementation(() => throwError(() => new Error('down')));
    create();
    expect((q('.qa-print') as HTMLButtonElement).disabled).toBe(true);
  });

  it('reloads with the filters and when the workspace changes', () => {
    const page = create({ workspace: 'API' });
    page.onFiltersChange({ ...page.filters, environment: 'uat' });
    expect(svc.executiveSummary).toHaveBeenLastCalledWith('API', expect.objectContaining({ environment: 'uat' }));
    route.setParams({ workspace: 'UI' });
    expect(svc.executiveSummary).toHaveBeenLastCalledWith('UI', expect.objectContaining({ environment: '' }));
  });

  it('shows an error when the summary cannot be loaded', () => {
    svc.executiveSummary.mockImplementation(() => throwError(() => new Error('down')));
    const page = create();
    expect(page.hasError).toBe(true);
    expect(page.loaded).toBe(false);
    expect(q('.qa-state--error')).not.toBeNull();
  });

  it('shows a loading message while waiting', () => {
    svc.executiveSummary.mockImplementation(() => NEVER);
    create();
    expect(q('.qa-state')?.textContent).toContain('qaDashboard.loading');
  });

  it('asks for nothing without the executive summary privilege', () => {
    granted = new Set([QA_DASHBOARD_PRIVILEGES.overviewRead]);
    create();
    expect(svc.executiveSummary).not.toHaveBeenCalled();
    expect(q('.qa-print')).toBeNull();
  });
});
