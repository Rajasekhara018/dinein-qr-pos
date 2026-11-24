import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { NEVER, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { QA_DASHBOARD_PRIVILEGES } from '../qa-dashboard.constants';
import { accessMock, QA_SHARED_DECLARATIONS, routerStub, routeStub, testSetExecution } from '../qa-dashboard.fixtures';
import { QaTestSetExecution } from '../qa-dashboard.models';
import { QaDashboardService } from '../qa-dashboard.service';
import { QaTestSetRunsPage } from './qa-test-set-runs';

describe('QaTestSetRunsPage', () => {
  const svc = {
    testSetExecutions: vi.fn((): Observable<QaTestSetExecution[]> => of([testSetExecution('s1'), testSetExecution('s2', { name: 'Smoke API', lastRun: null })])),
    facets: vi.fn(),
  };
  let granted: Set<string> | null;
  let route: ReturnType<typeof routeStub>;
  let fixture: ComponentFixture<QaTestSetRunsPage>;

  function create(params: Record<string, string> = {}): QaTestSetRunsPage {
    route = routeStub(params);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [QaTestSetRunsPage, ...QA_SHARED_DECLARATIONS],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: QaDashboardService, useValue: svc },
        { provide: HqPrivilegeAccessService, useValue: accessMock(granted) },
        { provide: ActivatedRoute, useValue: route.route },
        { provide: Router, useValue: routerStub() },
      ],
    });
    fixture = TestBed.createComponent(QaTestSetRunsPage);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const q = (selector: string): HTMLElement | null => el().querySelector(selector);

  beforeEach(() => {
    vi.clearAllMocks();
    svc.testSetExecutions.mockImplementation(() => of([testSetExecution('s1'), testSetExecution('s2', { name: 'Smoke API', lastRun: null })]));
    granted = null;
  });

  it('lists each test set with the result of its latest run', () => {
    create();
    expect(svc.testSetExecutions).toHaveBeenCalledWith();
    const rows = el().querySelectorAll('.qa-set-row');
    expect(rows).toHaveLength(2);
    const first = Array.from(rows[0].querySelectorAll('td')).map((td) => td.textContent?.trim());
    expect(first[0]).toBe('Nightly UI');
    expect(first).toContain('QA_REG_OCT26_R2');
    expect(first).toContain('8');
    expect(rows[0].querySelector('.qa-pill--danger')).not.toBeNull();
  });

  it('opens the full report of the latest run, safely, and only when there is one', () => {
    const run = testSetExecution('s1').lastRun!;
    svc.testSetExecutions.mockImplementation(() => of([
      testSetExecution('s1'),
      testSetExecution('s2', { lastRun: { ...run, reportUrl: 'javascript:alert(1)' } }),
      testSetExecution('s3', { lastRun: { ...run, reportUrl: null } }),
    ]));
    create();
    const links = el().querySelectorAll('.qa-set-row .qa-report');
    expect(links).toHaveLength(1);
    expect(links[0].getAttribute('href')).toBe('https://reports.example.com/qa/r1/index.html');
    expect(links[0].getAttribute('target')).toBe('_blank');
    expect(links[0].getAttribute('rel')).toBe('noopener noreferrer');
  });

  it('says a test set that never ran has not been run', () => {
    create();
    const second = el().querySelectorAll('.qa-set-row')[1];
    expect(second.textContent).toContain('qaDashboard.testSetRuns.neverRun');
    expect(second.querySelector('.qa-pill')).toBeNull();
  });

  it('says there are no test sets', () => {
    svc.testSetExecutions.mockImplementation(() => of([]));
    create();
    expect(q('.qa-empty')).not.toBeNull();
    expect(q('.qa-sets')).toBeNull();
  });

  it('shows only the test sets of the open workspace (the API lists them all)', () => {
    svc.testSetExecutions.mockImplementation(() => of([testSetExecution('s1'), testSetExecution('s2', { workspace: 'API', name: 'Smoke API' })]));
    const page = create();
    expect(page.executions.map((e) => e.testSetId)).toEqual(['s1']);
    route.setParams({ workspace: 'API' });
    expect(page.executions.map((e) => e.testSetId)).toEqual(['s2']);
    expect(svc.testSetExecutions).toHaveBeenCalledTimes(2);
  });

  it('shows an error when the executions cannot be loaded', () => {
    svc.testSetExecutions.mockImplementation(() => throwError(() => new Error('down')));
    const page = create();
    expect(page.hasError).toBe(true);
    expect(page.all).toEqual([]);
    expect(q('.qa-state--error')).not.toBeNull();
  });

  it('shows a loading message while waiting and tracks rows by test set', () => {
    svc.testSetExecutions.mockImplementation(() => NEVER);
    const page = create();
    expect(q('.qa-state')?.textContent).toContain('qaDashboard.loading');
    expect(page.trackBySet(0, testSetExecution('s9'))).toBe('s9');
  });

  it('asks for nothing without the execution privilege', () => {
    granted = new Set([QA_DASHBOARD_PRIVILEGES.testSetList]);
    create();
    expect(svc.testSetExecutions).not.toHaveBeenCalled();
    expect(q('.qa-sets')).toBeNull();
  });
});
