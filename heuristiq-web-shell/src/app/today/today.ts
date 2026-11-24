import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HQ_PRIVILEGES_CHANGED_EVENT, HqPrivilegeAccessService, HqTimezoneService } from 'hq-shared-lib';
import { Observable, Subject, Subscription, merge, timer } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { AuthService } from '../shared/services/auth-service';
import {
  TODAY_PRIORITY_LIMIT,
  TODAY_CLOCK_TICK_MS,
  TODAY_REFRESH_MS,
  TODAY_SECTIONS,
} from './today.constants';
import { WORKSPACE_PRIVILEGES } from '../workspace/workspace.constants';
import { dueState } from '../workspace/workspace.helpers';
import { DueState, WorkspaceItem } from '../workspace/workspace.models';
import { WorkspaceService } from '../workspace/workspace.service';
import { isTodaySectionHidden } from '../shared/profile-visibility';
import { attentionItemsOf, toMetricView } from './today.helpers';
import { EMPTY_TODAY_DASHBOARD, TodayDashboard, buildTodayDashboard } from './today-dashboard.helpers';
import { TodayBriefing, briefingOf, decisionItemsOf, timelineOf } from './today-home.helpers';
import { TodayInsightsService } from './today-insights.service';
import { EMPTY_INSIGHT_CHARTS, TodayInsightCharts, buildInsightCharts } from './today-insights.helpers';
import { TodayInsights } from './today-insights.models';
import { TODAY_INSIGHTS_I18N, TODAY_INSIGHTS_PRIVILEGE, TODAY_RIDES_SECTION_ID, TODAY_RIDES_TREND_PRIVILEGE, TODAY_TRAIN_TREND_PRIVILEGE, TODAY_WALLET_TREND_PRIVILEGE } from './today-insights.constants';
import { HqDecisionItem, HqTimelineEntry } from 'hq-shared-lib';
import { TODAY_DASHBOARD_I18N } from './today-dashboard.constants';
import { AttentionItem, MetricSpec, SectionSpec, SectionView, SourceData } from './today.models';
import { TodayService } from './today.service';

/**
 * Today: one glance at what needs doing across the applications the signed-in user may work in. Each card is one
 * application's own summary (read-only, from its existing endpoint); cards and figures the user has no privilege
 * for are not shown. The figures refresh every minute and on demand, and a failing source marks only its card.
 */
@Component({
  selector: 'app-today',
  standalone: false,
  templateUrl: './today.html',
  styleUrl: './today.scss',
})
export class Today implements OnInit, OnDestroy {
  private readonly hqTimezone = inject(HqTimezoneService);
  private readonly access = inject(HqPrivilegeAccessService);
  private readonly auth = inject(AuthService);
  private readonly service = inject(TodayService);
  private readonly insightsService = inject(TodayInsightsService);
  private readonly workspace = inject(WorkspaceService);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);

  private readonly refresh$ = new Subject<void>();
  private subscriptions: Subscription[] = [];
  private clockSubscription?: Subscription;
  private langSubscription?: Subscription;
  private privilegesSubscription?: Subscription;
  /** Whether the cards were chosen at least once (privilege changes before that are covered by the first start). */
  private started = false;
  /** The moment the header clock shows; moved forward every second. */
  now = new Date();

  sections: SectionView[] = [];

  /** The headline figures and charts above the cards. Rebuilt when a card answers or the language changes, never on every check, so the charts do not redraw needlessly. */
  dashboard: TodayDashboard = EMPTY_TODAY_DASHBOARD;
  /** The briefing banner, the "needs your decision" rows and the timeline: rebuilt together with the dashboard. */
  briefing: TodayBriefing = { headline: '', emphasis: '', notes: [] };
  decisions: HqDecisionItem[] = [];
  /** The analytics charts (activity, audience, revenue, funnel); a chart whose data did not arrive is null. */
  insightCharts: TodayInsightCharts = EMPTY_INSIGHT_CHARTS;
  readonly insightsKey = TODAY_INSIGHTS_I18N;
  private insights: TodayInsights | null = null;
  timeline: HqTimelineEntry[] = [];
  readonly dashboardKey = TODAY_DASHBOARD_I18N;

  /** The open high-priority to-dos and tasks of the user (their own or assigned to them): the Priority card. */
  priorityItems: WorkspaceItem[] = [];
  readonly priorityPrivilege = WORKSPACE_PRIVILEGES.list;
  lastUpdated: Date | null = null;
  refreshing = false;

  /** Every figure across the cards that is a waiting queue or a failure, biggest-effort first as listed: what the user should act on. */
  get attentionItems(): AttentionItem[] {
    return attentionItemsOf(this.sections);
  }

  /** True once every card has answered, so "nothing needs attention" is not claimed while figures are still loading. */
  get settled(): boolean {
    return this.sections.length > 0 && this.sections.every((s) => s.state !== 'loading');
  }

  /** Today's date in the active language, for the page header. */
  get dateLabel(): string {
    return this.hqTimezone.formatLocale(new Date(), 'date', { weekday: 'long', day: 'numeric', month: 'long' }, this.translate.getCurrentLang() || 'en');
  }

  /** The current time (hours, minutes, seconds) in the organization's time zone and the active language, for the header clock. */
  get clockLabel(): string {
    return this.hqTimezone.formatLocale(this.now, 'time', { hour: '2-digit', minute: '2-digit', second: '2-digit' },
      this.translate.getCurrentLang() || 'en');
  }

  get userName(): string {
    return this.auth.currentUser?.name || this.auth.currentUser?.email?.split('@')[0] || this.translate.instant('today.defaultName');
  }

  ngOnInit(): void {
    this.clockSubscription = timer(TODAY_CLOCK_TICK_MS, TODAY_CLOCK_TICK_MS).subscribe(() => {
      this.now = new Date();
      this.cdr.markForCheck();
    });
    this.langSubscription = this.translate.onLangChange.subscribe(() => { this.rebuildDashboard(); this.cdr.markForCheck(); });
    // Today is the landing page, so it usually opens before the user's privileges have arrived. Until they have, the access
    // service would allow every card (it cannot tell yet), so the cards are only chosen once the privileges are known, and
    // chosen again whenever they change (a role was granted, or another organization was opened).
    this.privilegesSubscription = this.auth.ensurePrivileges().subscribe(() => this.start());
    window.addEventListener(HQ_PRIVILEGES_CHANGED_EVENT, this.onPrivilegesChanged);
  }

  /** Chooses the cards again once they were first chosen; a change before that is picked up by the first start. */
  private readonly onPrivilegesChanged = (): void => {
    if (!this.started) return;
    this.start();
    this.cdr.markForCheck();
  };

  /** Builds the cards the user may see and starts reading their figures (stopping what an earlier start read). */
  private start(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.subscriptions = [];
    this.started = true;
    this.insights = null;
    const specs = this.visibleSpecs();
    this.sections = specs.map(({ section, metrics }) => this.view(section, metrics, 'loading', null));
    this.rebuildDashboard();
    const ticks$ = merge(timer(0, TODAY_REFRESH_MS), this.refresh$);
    this.subscriptions = specs.map(({ section, metrics }, index) =>
      ticks$
        .pipe(
          switchMap(() =>
            this.service.fetch(section.id).pipe(
              map((data) => this.view(section, metrics, 'ready', data)),
              catchError(() => of(this.view(section, metrics, 'error', null))),
            ),
          ),
        )
        .subscribe((view) => {
          this.sections[index] = view;
          this.rebuildDashboard();
          this.lastUpdated = new Date();
          this.refreshing = false;
          this.cdr.markForCheck();
        }),
    );
    if (this.canSee(WORKSPACE_PRIVILEGES.list)) {
      this.subscriptions.push(this.watchPriority(ticks$));
    }
    const sources = {
      matrimony: this.canSee(TODAY_INSIGHTS_PRIVILEGE) && !isTodaySectionHidden('matrimony'),
      train: this.canSee(TODAY_TRAIN_TREND_PRIVILEGE) && !isTodaySectionHidden('train'),
      wallet: this.canSee(TODAY_WALLET_TREND_PRIVILEGE) && !isTodaySectionHidden('wallet'),
      rides: this.canSee(TODAY_RIDES_TREND_PRIVILEGE) && !isTodaySectionHidden(TODAY_RIDES_SECTION_ID),
    };
    if (sources.matrimony || sources.train || sources.wallet || sources.rides) {
      this.subscriptions.push(ticks$.pipe(switchMap(() => this.insightsService.fetch(sources).pipe(catchError(() => of(null))))).subscribe((insights) => {
        this.insights = insights;
        this.rebuildDashboard();
        this.cdr.markForCheck();
      }));
    }
  }

  private rebuildDashboard(): void {
    const label = (key: string): string => this.translate.instant(key);
    this.dashboard = buildTodayDashboard(this.sections, label);
    const waiting = this.attentionItems;
    this.decisions = decisionItemsOf(waiting, label, label('today.briefing.review'));
    this.briefing = briefingOf(waiting, label, {
      headline: label('today.briefing.headline'), clear: label('today.briefing.clear'),
      need: this.translate.instant('today.briefing.need', { count: waiting.length }),
    });
    this.insightCharts = this.insights ? buildInsightCharts(this.insights, label, this.translate.getCurrentLang() || 'en') : EMPTY_INSIGHT_CHARTS;
    this.timeline = timelineOf(this.priorityItems, (item) => this.due(item), (item, due) => this.whenLabel(item, due), (kind) => label(`today.timeline.kind.${kind}`));
  }

  /** The time text of a timeline entry: "Overdue", "Today", the date, or that it has no due date. */
  private whenLabel(item: WorkspaceItem, due: DueState): string {
    if (due === 'overdue') return this.translate.instant('workspace.due.overdue', { date: '' }).trim();
    if (due === 'today') return this.translate.instant('workspace.due.today', { date: '' }).trim();
    if (due === 'none' || !item.dueAt) return this.translate.instant('today.timeline.noDue');
    return this.hqTimezone.formatLocale(new Date(item.dueAt), 'date', { day: 'numeric', month: 'short' }, this.translate.getCurrentLang() || 'en');
  }

  /** Loads the Priority card's items now and on every refresh; a failure just leaves the card out. */
  private watchPriority(ticks$: Observable<unknown>): Subscription {
    return ticks$
      .pipe(
        switchMap(() =>
          this.workspace
            .list({ kinds: ['TODO', 'TASK'], status: 'OPEN', priority: 'HIGH', scope: 'INVOLVED' }, 0, TODAY_PRIORITY_LIMIT)
            .pipe(
              map((page) => page.content),
              catchError(() => of([] as WorkspaceItem[])),
            ),
        ),
      )
      .subscribe((items) => {
        this.priorityItems = items;
        this.rebuildDashboard();
        this.cdr.markForCheck();
      });
  }

  /** How soon a priority item is due, for its chip. */
  due(item: WorkspaceItem): DueState {
    return dueState(item, new Date());
  }

  trackPriority(index: number, item: WorkspaceItem): string {
    return `${item.id}:${index}`;
  }

  ngOnDestroy(): void {
    window.removeEventListener(HQ_PRIVILEGES_CHANGED_EVENT, this.onPrivilegesChanged);
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.clockSubscription?.unsubscribe();
    this.langSubscription?.unsubscribe();
    this.privilegesSubscription?.unsubscribe();
    this.refresh$.complete();
  }

  /** Reads every card again now. */
  refresh(): void {
    this.refreshing = true;
    this.refresh$.next();
  }

  trackSection(_: number, section: SectionView): string {
    return section.id;
  }

  /** The cards (with the figures) this user may see; a card with no readable figure is left out. */
  private visibleSpecs(): { section: SectionSpec; metrics: readonly MetricSpec[] }[] {
    return TODAY_SECTIONS.filter((section) => !isTodaySectionHidden(section.id)).map((section) => ({
      section,
      metrics: section.metrics.filter((m) => this.canSee(m.privilege ?? section.privilege)),
    })).filter((entry) => entry.metrics.length > 0);
  }

  /**
   * Whether the user may read a figure. A figure that needs a privilege is hidden while the privileges are unknown (the access
   * service would allow it then): a card that cannot load is worse than a card that is not there.
   */
  private canSee(privilege: string | readonly string[] | null | undefined): boolean {
    if (!privilege) return true;
    return this.access.granted() !== null && this.access.can(privilege);
  }

  private view(section: SectionSpec, metrics: readonly MetricSpec[], state: SectionView['state'], data: SourceData | null): SectionView {
    const views = metrics.map((m) => toMetricView(m, data));
    return {
      id: section.id,
      titleKey: section.titleKey,
      icon: section.icon,
      link: section.link,
      state,
      metrics: views,
      attention: views.filter((v) => v.highlighted).length,
    };
  }
}
