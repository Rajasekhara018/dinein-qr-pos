import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { environment } from '../../environments/environment';
import { ApiService } from '../shared/services/api-service';
import { InAppNotificationService } from '../shared/services/in-app-notification-service';
import { PendingReviewsService } from '../shared/services/pending-reviews-service';
import { WorkflowService } from '../workflow/workflow-service';
import { WorkspaceService } from '../workspace/workspace.service';
import { TodayService } from './today.service';
import { FranchiseApi } from '../access-management/franchises/franchise-api';

describe('TodayService', () => {
  const api = { get: vi.fn() };
  const workflow = { tasks: vi.fn() };
  const workspace = { summary: vi.fn() };
  const franchises = { myEarnings: vi.fn() };
  const reviews$ = new BehaviorSubject<number>(0);
  const unread$ = new BehaviorSubject<number>(0);
  let service: TodayService;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.clearAllMocks();
    reviews$.next(0);
    unread$.next(0);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ApiService, useValue: api },
        { provide: WorkflowService, useValue: workflow },
        { provide: WorkspaceService, useValue: workspace },
        { provide: PendingReviewsService, useValue: { count$: reviews$ } },
        { provide: InAppNotificationService, useValue: { unreadCount$: unread$ } },
        { provide: FranchiseApi, useValue: franchises },
      ],
    });
    service = TestBed.inject(TodayService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the agent desk from the matrimony API and counts the meetings of the week', () => {
    let result: unknown;
    service.fetch('agentDesk').subscribe((r) => (result = r));
    const req = http.expectOne(`${environment.matrimonyApiUrl}/agent/dashboard`);
    expect(req.request.method).toBe('GET');
    req.flush({ customers: 5, openLeads: 2, overdueRequests: 0, successes: 1, meetingsThisWeek: [{ proposalId: 'p1' }] });
    expect(result).toEqual({ customers: 5, openLeads: 2, overdueRequests: 0, successes: 1, meetingsThisWeek: 1 });
  });

  it('reads the earnings statement of the signed-in person and adds up their accounts', () => {
    franchises.myEarnings.mockReturnValue(of({ accounts: [
      { available: { code: 'INR', value: 300 }, pending: { code: 'INR', value: 40 }, settled: null },
      { available: { code: 'INR', value: 200 }, pending: null, settled: { code: 'INR', value: 900 } },
    ] }));
    let result: unknown;
    service.fetch('earnings').subscribe((r) => (result = r));
    expect(result).toEqual({ available: 500, pending: 40, settled: 900 });
  });

  it('reads the matrimony summary directly from the matrimony API', () => {
    let result: unknown;
    service.fetch('matrimony').subscribe((r) => (result = r));

    const req = http.expectOne(`${environment.matrimonyApiUrl}/ops/analytics/summary`);
    expect(req.request.method).toBe('GET');
    req.flush({ pendingReports: 3 });

    expect(result).toEqual({ pendingReports: 3 });
  });

  it('reads the workspace figures from the workspace summary', () => {
    workspace.summary.mockReturnValue(of({ openTodos: 2, openAssigned: 1, overdue: 0, remindersDue: 3 }));
    let result: unknown;

    service.fetch('workspace').subscribe((r) => (result = r));

    expect(result).toEqual({ openTodos: 2, openAssigned: 1, overdue: 0, remindersDue: 3 });
  });

  it.each([
    ['train', '/v1/train/admin/dashboard/summary'],
    ['operations', '/v1/admin/dashboard/summary'],
    ['wallet', '/v1/wallet/admin/overview'],
  ] as const)('reads the %s figures through the gateway path %s', (source, path) => {
    api.get.mockReturnValue(of({ ok: true }));
    let result: unknown;

    service.fetch(source).subscribe((r) => (result = r));

    expect(api.get).toHaveBeenCalledWith(path);
    expect(result).toEqual({ ok: true });
  });

  it('combines the task totals, change requests and unread notifications for the waiting card', () => {
    workflow.tasks.mockImplementation((_p: unknown, _s: unknown, view: string) => of({ items: [], total: { MINE: 4, UNASSIGNED: 2, OVERDUE: 1 }[view] }));
    reviews$.next(5);
    unread$.next(7);
    const seen: unknown[] = [];

    service.fetch('waiting').subscribe((r) => seen.push(r));

    expect(workflow.tasks).toHaveBeenCalledWith(undefined, undefined, 'MINE', 0, 1);
    expect(seen).toEqual([{ mine: 4, unassigned: 2, overdue: 1, reviews: 5, unread: 7 }]);

    unread$.next(8);
    expect(seen[1]).toEqual({ mine: 4, unassigned: 2, overdue: 1, reviews: 5, unread: 8 });
  });

  it('reports a task count the user cannot read as null without failing the card', () => {
    workflow.tasks.mockImplementation((_p: unknown, _s: unknown, view: string) =>
      view === 'OVERDUE' ? throwError(() => new Error('403')) : of({ items: [], total: 3 }),
    );
    let result: unknown;

    service.fetch('waiting').subscribe((r) => (result = r));

    expect(result).toEqual({ mine: 3, unassigned: 3, overdue: null, reviews: 0, unread: 0 });
  });

  it('lets a failing source error so the screen can mark just that card', () => {
    api.get.mockReturnValue(throwError(() => new Error('boom')));
    let failed = false;

    service.fetch('train').subscribe({ error: () => (failed = true) });

    expect(failed).toBe(true);
  });
});
