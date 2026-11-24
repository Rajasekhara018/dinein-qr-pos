import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqBriefing, HqChart, HqChartCard, HqDecisionList, HqKpiCard, HqPrivilegeAccessService, HqSharedLibModule, HqTimeline } from 'hq-shared-lib';
import { stubHqCharts } from '../shared/testing/stub-charts.testing';
import { TodayInsightsService } from './today-insights.service';
import { EMPTY, Observable, Subject, of, throwError, timer } from 'rxjs';
import { map } from 'rxjs/operators';
import { vi } from 'vitest';
import { environment } from '../../environments/environment';
import { AuthService } from '../shared/services/auth-service';
import { HQ_PRIVILEGES_CHANGED_EVENT } from 'hq-shared-lib';
import { TODAY_REFRESH_MS, TODAY_SECTIONS } from './today.constants';
import { TODAY_INSIGHTS_PRIVILEGE, TODAY_RIDES_TREND_PRIVILEGE, TODAY_TRAIN_TREND_PRIVILEGE, TODAY_WALLET_TREND_PRIVILEGE } from './today-insights.constants';
import { WORKSPACE_PRIVILEGES } from '../workspace/workspace.constants';
import { Today } from './today';
import { WorkspaceItem } from '../workspace/workspace.models';
import { WorkspaceService } from '../workspace/workspace.service';
import { TodayService } from './today.service';

describe('Today', () => {
  const sources: Record<string, unknown> = {
    waiting: { mine: 2, unassigned: 0, overdue: 1, reviews: 0, unread: 3 },
    workspace: { openTodos: 2, openAssigned: 0, overdue: 0, remindersDue: 0 },
    matrimony: { pendingReviewProfiles: 4, pendingReports: 0, pendingSuccessStories: 0, pendingInterests: 9, activeUsers: 120 },
    train: { metrics: { bookingsToday: 10, revenueToday: 250000, successRate: 98.5, pendingBookings: 0, failedBookings: 0, refundsToday: 0, pendingTdrs: 0 } },
    operations: {},
    wallet: { totalLiability: '1500' },
    agentDesk: { customers: 12, openLeads: 3, overdueRequests: 1, successes: 2, meetingsThisWeek: 4 },
    earnings: { available: 2500, pending: 600, settled: 9000 },
  };
  const service = { fetch: vi.fn() };
  const gridFetches = (): number => service.fetch.mock.calls.length;
  const workspace = { list: vi.fn() };
  const urgent = (id: string, over: Partial<WorkspaceItem> = {}): WorkspaceItem => ({
    id, kind: 'TODO', title: `urgent ${id}`, body: null, ownerId: 'alice', assigneeId: null, visibility: 'PRIVATE', status: 'OPEN',
    priority: 'HIGH', dueAt: null, remindAt: null, refType: null, refId: null, createdAt: '2026-10-24T10:00:00Z',
    updatedAt: '2026-10-24T10:00:00Z', completedAt: null, ...over,
  });
  const priorityPage = (items: WorkspaceItem[]) => of({ content: items, totalElements: items.length, totalPages: 1, size: 12, number: 0 });
  const user: { name?: string; email?: string } = { name: 'Asha Rao' };
  /** Completes when the privileges are known; a test can hold it back to see the page before they arrive. */
  let privilegesReady$: Observable<void>;
  const auth = { get currentUser() { return user; }, ensurePrivileges: () => privilegesReady$ };
  /** The cards only a franchise member or matrimony agent can read. */
  const AGENT_CARDS = ['agentDesk', 'earnings'];
  /** Every privilege behind the HQ cards and charts: the default user of these tests is an HQ user who holds them all. */
  const HQ = new Set<string>([
    ...TODAY_SECTIONS.filter((s) => !AGENT_CARDS.includes(s.id)).flatMap((s) => [s.privilege, ...s.metrics.map((m) => m.privilege)])
      .flat().filter((p): p is string => typeof p === 'string'),
    TODAY_INSIGHTS_PRIVILEGE, TODAY_TRAIN_TREND_PRIVILEGE, TODAY_WALLET_TREND_PRIVILEGE, TODAY_RIDES_TREND_PRIVILEGE, WORKSPACE_PRIVILEGES.list,
  ]);
  /** A set = exactly those privileges; null = not known yet (the real service then answers yes to everything). */
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    granted: (): ReadonlySet<string> | null => granted,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };
  const env = environment as unknown as { hiddenTodaySections?: string[] };
  let fixture: ComponentFixture<Today>;

  function create(): Today {
    stubHqCharts();
    TestBed.configureTestingModule({
      declarations: [Today],
      imports: [TranslateModule.forRoot(), RouterModule, HqSharedLibModule, HqKpiCard, HqChart, HqChartCard, HqBriefing, HqDecisionList, HqTimeline],
      providers: [
        provideRouter([]),
        { provide: TodayService, useValue: service },
        { provide: TodayInsightsService, useValue: { fetch: vi.fn(() => of(null)) } },
        { provide: WorkspaceService, useValue: workspace },
        { provide: AuthService, useValue: auth },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    fixture = TestBed.createComponent(Today);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  /** Lets the first timer tick (and any pending refresh) run, then renders. */
  function tick(ms = 1): void {
    vi.advanceTimersByTime(ms);
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
  }

  const text = (): string => fixture.nativeElement.textContent as string;
  const titles = (): string[] => Array.from(fixture.nativeElement.querySelectorAll('.today-card__title')).map((e) => (e as HTMLElement).textContent?.trim() ?? '');

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-03-04T10:00:00Z'));
    granted = new Set(HQ);
    privilegesReady$ = of(undefined);
    user.name = 'Asha Rao';
    user.email = undefined;
    workspace.list.mockReset();
    workspace.list.mockImplementation(() => priorityPage([]));
    service.fetch.mockReset();
    service.fetch.mockImplementation((source: string): Observable<unknown> => of(sources[source]));
  });

  afterEach(() => {
    delete env.hiddenTodaySections;
    vi.useRealTimers();
  });

  it('shows a running clock in the header that moves on every second and stops when the page closes', () => {
    vi.setSystemTime(new Date('2026-11-08T13:59:58Z'));
    const today = create();
    const clock = (): HTMLElement => fixture.nativeElement.querySelector('time.today__clock') as HTMLElement;
    expect(clock()).not.toBeNull();
    const first = new Date(clock().getAttribute('datetime') as string).getTime();

    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(new Date(clock().getAttribute('datetime') as string).getTime() - first).toBe(1000);
    expect(clock().textContent?.trim()).toBe(today.clockLabel);
    expect(today.clockLabel).toMatch(/\d{1,2}\D\d{2}\D\d{2}/);

    fixture.destroy();
    const stopped = today.now.getTime();
    vi.advanceTimersByTime(3000);
    expect(today.now.getTime()).toBe(stopped);
  });

  it('lists the waiting queues and failures in one strip, but only once every card has answered', () => {
    const today = create();
    expect(today.settled).toBe(false);
    expect(fixture.nativeElement.querySelector('hq-decision-list')).toBeNull();

    tick();

    expect(today.settled).toBe(true);
    const rows = Array.from(fixture.nativeElement.querySelectorAll('.dl__row')) as HTMLElement[];
    expect(rows.length).toBe(today.attentionItems.length);
    expect(rows.length).toBeGreaterThan(0);
    expect(text()).toContain('today.attentionTitle');
    expect(fixture.nativeElement.querySelector('.dl__tag--bad')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('hq-briefing .brief__headline strong')?.textContent).toContain('today.briefing.need');
  });

  it('draws the headline figures and the charts above the cards once every card has answered, from the same figures', () => {
    const today = create();
    expect(fixture.nativeElement.querySelector('.today-dash')).toBeNull();

    tick();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.today-dash')).not.toBeNull();
    expect(el.querySelectorAll('hq-kpi-card').length).toBe(today.dashboard.kpis.length);
    expect(today.dashboard.kpis.length).toBeGreaterThan(0);
    expect(el.querySelectorAll('hq-chart-card').length).toBeGreaterThan(0);
    expect(el.querySelector('hq-chart')).not.toBeNull();
  });

  it('keeps the same chart specs between checks, so a chart is not redrawn when nothing changed', () => {
    const today = create();
    tick();
    const donut = today.dashboard.byArea;

    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    expect(today.dashboard.byArea).toBe(donut);
  });

  it('says nothing needs attention when every figure is zero', () => {
    service.fetch.mockImplementation(() => of({}));
    const today = create();
    tick();

    expect(today.attentionItems).toEqual([]);
    expect(text()).toContain('today.attentionNone');
  });

  it('shows every card in the loading state before the first read completes', () => {
    const today = create();

    expect(today.sections.map((s) => s.id)).toEqual(['waiting', 'workspace', 'matrimony', 'train', 'operations', 'wallet']);
    expect(today.sections.every((s) => s.state === 'loading')).toBe(true);
    expect(fixture.nativeElement.querySelectorAll('.today-metric--loading').length).toBeGreaterThan(0);
  });

  it('renders figures, attention counts and the all-clear marker once the data arrives', () => {
    create();
    tick();

    expect(titles()).toEqual([
      'today.sections.waiting',
      'today.sections.workspace',
      'today.sections.matrimony',
      'today.sections.train',
      'today.sections.operations',
      'today.sections.wallet',
    ]);
    // waiting: overdue (bad) and mine (pending) are non-zero queues, plus unread; unassigned and reviews are zero.
    const sections = fixture.componentInstance.sections;
    expect(sections[0].attention).toBe(3);
    expect(sections[0].metrics.find((m) => m.key === 'mine')?.text).toBe('2');
    expect(sections[3].metrics.find((m) => m.key === 'revenue')?.text).toBe('₹3L');
    expect(sections[3].metrics.find((m) => m.key === 'success')?.text).toBe('98.5%');
    expect(sections[5].metrics.find((m) => m.key === 'liability')?.text).toContain('1,500');
    // train has nothing pending: all clear; operations supplied nothing: dashes, not zeros.
    expect(text()).toContain('today.allClear');
    expect(text()).toContain('—');
    expect(fixture.componentInstance.lastUpdated?.getTime()).toBe(new Date('2026-03-04T10:00:00Z').getTime());
  });

  it('only shows the figures the user may read and leaves out cards with none', () => {
    granted = new Set(['WORKFLOW_TASK_LIST', 'TRAIN_ADMIN_DASHBOARD_SUMMARY_READ']);
    const today = create();
    tick();

    expect(titles()).toEqual(['today.sections.waiting', 'today.sections.train']);
    // The change-request figure needs another privilege; the unread figure needs none.
    expect(today.sections[0].metrics.map((m) => m.key)).toEqual(['mine', 'unassigned', 'overdue', 'unread']);
    expect(service.fetch).not.toHaveBeenCalledWith('wallet');
    expect(service.fetch).not.toHaveBeenCalledWith('operations');
    expect(service.fetch).not.toHaveBeenCalledWith('matrimony');
  });

  describe('a franchise agent of the matrimony application', () => {
    /** The privileges a matrimony franchise agent's role carries that the Today page cares about. */
    const AGENT = ['MATRIMONY_AGENT_DASHBOARD_READ', 'COMMISSION_STATEMENT_MINE_READ'];

    it('sees their own desk and earnings, and none of the HQ cards', () => {
      granted = new Set(AGENT);
      const today = create();
      tick();

      expect(titles()).toEqual(['today.sections.waiting', 'today.sections.agentDesk', 'today.sections.earnings']);
      // Waiting keeps only the figure that needs no privilege: the unread notifications.
      expect(today.sections[0].metrics.map((m) => m.key)).toEqual(['unread']);
      expect(today.sections[1].metrics.map((m) => [m.key, m.text])).toEqual([
        ['customers', '12'], ['leads', '3'], ['overdue', '1'], ['meetings', '4'], ['successes', '2'],
      ]);
      expect(today.sections[2].metrics.find((m) => m.key === 'available')?.text).toContain('2,500');
      for (const hq of ['workspace', 'matrimony', 'train', 'operations', 'wallet']) {
        expect(service.fetch).not.toHaveBeenCalledWith(hq);
      }
    });

    it('waits for the privileges before choosing the cards, so nothing loads that the user may not read', () => {
      const ready = new Subject<void>();
      privilegesReady$ = ready;
      granted = null;
      const today = create();
      tick();

      expect(today.sections).toEqual([]);
      expect(service.fetch).not.toHaveBeenCalled();

      granted = new Set(AGENT);
      ready.next();
      tick();

      expect(titles()).toEqual(['today.sections.waiting', 'today.sections.agentDesk', 'today.sections.earnings']);
      expect(service.fetch).not.toHaveBeenCalledWith('wallet');
    });

    it('shows no privileged card when the privileges could not be read, instead of cards that cannot load', () => {
      granted = null;
      const today = create();
      tick();

      expect(today.sections.map((s) => s.id)).toEqual(['waiting']);
      expect(today.sections[0].metrics.map((m) => m.key)).toEqual(['unread']);
      expect(service.fetch.mock.calls.map(([source]) => source)).toEqual(['waiting']);
    });

    it('chooses the cards again when the privileges change, and stops reading the old ones', () => {
      granted = new Set(['COMMISSION_STATEMENT_MINE_READ']);
      const today = create();
      tick();
      expect(today.sections.map((s) => s.id)).toEqual(['waiting', 'earnings']);

      granted = new Set(['MATRIMONY_AGENT_DASHBOARD_READ']);
      window.dispatchEvent(new Event(HQ_PRIVILEGES_CHANGED_EVENT));
      tick();
      expect(today.sections.map((s) => s.id)).toEqual(['waiting', 'agentDesk']);

      service.fetch.mockClear();
      tick(TODAY_REFRESH_MS);
      expect(service.fetch.mock.calls.map(([source]) => source).sort()).toEqual(['agentDesk', 'waiting']);
    });

    it('ignores privilege changes after the page is gone', () => {
      granted = new Set(AGENT);
      create();
      tick();
      fixture.destroy();
      service.fetch.mockClear();

      window.dispatchEvent(new Event(HQ_PRIVILEGES_CHANGED_EVENT));
      vi.advanceTimersByTime(TODAY_REFRESH_MS);
      expect(service.fetch).not.toHaveBeenCalled();
    });
  });

  it('leaves out the cards the deployment profile hides and shows the empty message when none remain', () => {
    env.hiddenTodaySections = ['waiting', 'workspace', 'matrimony', 'train', 'operations', 'wallet'];
    const today = create();
    tick();

    expect(today.sections).toEqual([]);
    expect(text()).toContain('today.empty');
    expect(service.fetch).not.toHaveBeenCalled();
  });

  it('marks only the failing card with an error and retries every card on request', () => {
    service.fetch.mockImplementation((source: string) => (source === 'train' ? throwError(() => new Error('down')) : of(sources[source])));
    create();
    tick();

    const alerts = fixture.nativeElement.querySelectorAll('[role="alert"]');
    expect(alerts.length).toBe(1);
    expect(fixture.componentInstance.sections.map((s) => s.state)).toEqual(['ready', 'ready', 'ready', 'error', 'ready', 'ready']);
    expect(text()).toContain('today.error');

    service.fetch.mockImplementation((source: string) => of(sources[source]));
    (fixture.nativeElement.querySelector('.today-card__retry') as HTMLButtonElement).click();
    tick(0);

    expect(fixture.componentInstance.sections.every((s) => s.state === 'ready')).toBe(true);
    expect(fixture.componentInstance.refreshing).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('re-reads the figures every minute and stops after it is destroyed', () => {
    create();
    tick();
    expect(gridFetches()).toBe(6);

    tick(TODAY_REFRESH_MS);
    expect(gridFetches()).toBe(12);

    fixture.destroy();
    vi.advanceTimersByTime(TODAY_REFRESH_MS * 2);
    expect(gridFetches()).toBe(12);
  });

  it('refresh button reads every card again and is disabled while refreshing', () => {
    create();
    tick();
    service.fetch.mockClear();
    service.fetch.mockImplementation((source: string) => timer(5).pipe(map(() => sources[source])));
    const button = fixture.nativeElement.querySelector('.today__refresh') as HTMLButtonElement;

    button.click();
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    expect(fixture.componentInstance.refreshing).toBe(true);
    tick(10);

    expect(gridFetches()).toBe(6);
    expect(button.disabled).toBe(false);
  });

  it('greets the user by name, then by the part of the email before the @, then by a default', () => {
    const today = create();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { 'today.defaultName': 'there' });
    translate.use('en');

    expect(today.userName).toBe('Asha Rao');
    user.name = '';
    user.email = 'ravi.k@example.com';
    expect(today.userName).toBe('ravi.k');
    user.email = undefined;
    expect(today.userName).toBe('there');
  });

  it('shows today\'s date in the active language', () => {
    const today = create();

    expect(today.dateLabel).toContain('March');
    expect(today.dateLabel).toContain('Wednesday');
  });

  it('tracks cards by id', () => {
    const today = create();

    expect(today.trackSection(0, today.sections[3])).toBe('train');
  });

  it('links each card and tappable figure to its screen', () => {
    create();
    tick();

    const hrefs = Array.from(fixture.nativeElement.querySelectorAll('a')).map((a) => (a as HTMLAnchorElement).getAttribute('href'));
    expect(hrefs).toContain('/workflow');
    expect(hrefs).toContain('/admin/train-admin/bookings');
    expect(hrefs).toContain('/wallet/admin');
  });

  describe('today timeline', () => {
    const entries = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('.tl__item')) as HTMLElement[];

    it('asks only for the open high-priority to-dos and tasks the user is involved in', () => {
      create();
      tick();

      expect(workspace.list).toHaveBeenCalledWith({ kinds: ['TODO', 'TASK'], status: 'OPEN', priority: 'HIGH', scope: 'INVOLVED' }, 0, 12);
    });

    it('says so when nothing is high priority', () => {
      create();
      tick();

      expect(entries()).toHaveLength(0);
      expect(fixture.nativeElement.querySelector('.tl__empty')?.textContent).toContain('today.timeline.empty');
    });

    it('lists the high-priority items with when each is due, the overdue ones first and marked', () => {
      workspace.list.mockImplementation(() => priorityPage([
        urgent('later', { title: 'later one', dueAt: '2099-01-01T00:00:00Z' }), urgent('late', { title: 'late one', dueAt: '2000-01-01T00:00:00Z' }),
      ]));
      create();
      tick();

      expect(entries()).toHaveLength(2);
      expect(entries()[0].textContent).toContain('late one');
      expect(entries()[0].classList.contains('tl__item--bad')).toBe(true);
      expect(entries()[0].textContent).toContain('workspace.due.overdue');
      expect(entries()[1].textContent).toContain('later one');
    });

    it('shows an item with no due date as undated', () => {
      workspace.list.mockImplementation(() => priorityPage([urgent('a')]));
      create();
      tick();

      expect(entries()[0].textContent).toContain('today.timeline.noDue');
    });

    it('leaves the timeline empty when the items cannot be loaded and still shows the other cards', () => {
      workspace.list.mockImplementation(() => throwError(() => new Error('down')));
      create();
      tick();

      expect(entries()).toHaveLength(0);
      expect(titles().length).toBeGreaterThan(0);
    });

    it('does not ask without the privilege to list workspace items', () => {
      granted = new Set(['WORKFLOW_TASK_LIST']);
      create();
      tick();

      expect(workspace.list).not.toHaveBeenCalled();
    });
  });

  describe('application setup (removed)', () => {
    it('has no Application setup section and never asks for the readiness source', () => {
      create();
      tick();

      expect(fixture.nativeElement.querySelector('.today-setup')).toBeNull();
      expect(service.fetch.mock.calls.map(([source]) => source)).not.toContain('readiness');
      expect(titles().some((title) => title.includes('applicationSetup'))).toBe(false);
      expect(titles().filter((title) => title.startsWith('today.sections.')).length).toBe(6);
    });
  });
});
