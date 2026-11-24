import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { NAV_AREAS } from '../layout/nav-areas.constants';
import { NAV_ITEMS } from '../layout/nav-items.constants';
import { ApiService } from '../shared/services/api-service';
import { AuthService } from '../shared/services/auth-service';
import { BrandingService } from '../shared/services/branding-service';
import { NotificationService } from '../shared/services/notification-service';
import { HqPrivilegeAccessService } from 'hq-shared-lib';
import { APP_FILTERS, APP_KINDS, AppFilter, AppKind, FEATURE_PREVIEW_COUNT, HUB_EXTRA_APPS, PLATFORM_CAPABILITIES } from './home.constants';
import { AppFeature } from './home.models';
import { HomeApplicationsService } from './home-applications.service';
import {
  appsOfKind,
  buildSuiteApps,
  CapabilityTile,
  filterSuiteApps,
  resolveCapabilities,
  SuiteApp,
  SuiteStats,
  suiteStats,
  visibleFeatures,
  hubSuiteApps,
} from './home.helpers';
import { HomeLoadStatus } from './home.models';
import { apiErrorMessage } from '../shared/utils/api-error.helpers';

/**
 * The Applications hub: every application of the suite (Rider app, Train (shown here only), Matrimony, Wallet, Reports, Food delivery, Access,
 * QA automation, Developer) with what the signed-in user may open in it, and the platform capabilities they all share. Built from
 * the sidebar's areas and menu entries, so it always matches the menu and the user's privileges.
 */
@Component({
  selector: 'app-home',
  standalone: false,
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit {
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  private readonly translate = inject(TranslateService);
  private readonly subscriptions = inject(HomeApplicationsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly privileges = inject(HqPrivilegeAccessService);

  readonly filters = APP_FILTERS;
  readonly kinds = APP_KINDS;
  /** Applications whose full feature list is open. */
  private readonly expanded = new Set<string>();
  query = '';
  filter: AppFilter = 'all';
  sendingTestNotification = false;

  constructor(
    public auth: AuthService,
    private notifications: NotificationService,
    private api: ApiService,
  ) {}

  ngOnInit(): void {
    this.loadApplications(false);
  }

  /** Reads the organization's subscriptions; the retry button forces a fresh read. */
  loadApplications(force: boolean): void {
    this.subscriptions.load(force).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  get loadStatus(): HomeLoadStatus {
    return this.subscriptions.status();
  }

  get userName(): string {
    return this.auth.currentUser?.name || this.auth.currentUser?.email?.split('@')[0] || this.translate.instant('home.defaultName');
  }

  /** Screens with no permission are open to everyone; the others follow the user's privileges (hidden while those load). */
  private readonly canOpen = (permission: string | null): boolean => !permission || this.auth.canAccess(permission);

  /**
   * The applications the user sees: subscribed by their organization and allowed for them. Empty until the subscriptions are
   * known and when they could not be read (fail closed).
   */
  get apps(): SuiteApp[] {
    if (this.subscriptions.status() !== 'ready') return [];
    return hubSuiteApps(
      buildSuiteApps(NAV_ITEMS, NAV_AREAS, this.canOpen, HUB_EXTRA_APPS),
      this.subscriptions.subscribedCodes(),
      this.subscriptions.grantedCodes(),
      (appKey) => visibleFeatures(appKey, (codes) => this.privileges.can(codes)).length > 0,
    );
  }

  get stats(): SuiteStats {
    return suiteStats(this.apps);
  }

  /** The applications left after the filter chip and the search text. */
  get visibleApps(): SuiteApp[] {
    return filterSuiteApps(this.apps, this.filter, this.query, (key) => this.translate.instant(key));
  }

  /** The filtered applications of one group: products, or the shared applications. */
  appsOf(kind: AppKind): SuiteApp[] {
    return appsOfKind(this.visibleApps, kind);
  }

  /** The features of an application that the signed-in user may use, for its card. */
  featuresOf(app: SuiteApp): AppFeature[] {
    return visibleFeatures(app.key, (codes) => this.privileges.can(codes));
  }

  get capabilities(): CapabilityTile[] {
    return resolveCapabilities(PLATFORM_CAPABILITIES, NAV_ITEMS, this.canOpen);
  }

  /** The features a card shows: a few, or all of them once the user opens the list. */
  shownFeaturesOf(app: SuiteApp): AppFeature[] {
    const all = this.featuresOf(app);
    return this.isExpanded(app) ? all : all.slice(0, FEATURE_PREVIEW_COUNT);
  }

  /** How many features the card hides until the list is opened. */
  hiddenFeatureCount(app: SuiteApp): number {
    return Math.max(0, this.featuresOf(app).length - FEATURE_PREVIEW_COUNT);
  }

  isExpanded(app: SuiteApp): boolean {
    return this.expanded.has(app.key);
  }

  toggleFeatures(app: SuiteApp): void {
    if (!this.expanded.delete(app.key)) this.expanded.add(app.key);
  }

  setFilter(filter: AppFilter): void {
    this.filter = filter;
  }

  clearSearch(): void {
    this.query = '';
    this.filter = 'all';
  }

  trackByKey(_index: number, item: { key: string }): string {
    return item.key;
  }

  /** QA button: fires a real end-to-end test via Rider_NMS's generic notify endpoint (TEST_NOTIFICATION event, seeded IN_APP +
   *  FCM_PUSH templates) -- in-app arrives over the existing STOMP subscription (chime + bell badge), push arrives via FCM and
   *  deep-links to /notifications on click. Kept for checking that delivery works after a deployment. */
  sendTestNotification(): void {
    const userId = this.auth.currentUser?.id;
    if (!userId) {
      this.notifications.error(this.translate.instant('home.messages.noUserId'));
      return;
    }
    this.sendingTestNotification = true;
    this.api.post('/v1/nms/notify', {
      requestObject: {
        event: 'TEST_NOTIFICATION',
        userIds: [userId],
        channels: ['IN_APP', 'FCM_PUSH'],
        variables: {
          message: 'This is a test notification -- if you can see this and heard a sound, delivery is working correctly.',
        },
      },
    }).subscribe({
      next: () => {
        this.sendingTestNotification = false;
        this.notifications.success(this.translate.instant('home.messages.testSent'));
      },
      error: (err) => {
        this.sendingTestNotification = false;
        this.notifications.error(apiErrorMessage(err, this.translate.instant('home.messages.testFailed')));
      },
    });
  }
}
