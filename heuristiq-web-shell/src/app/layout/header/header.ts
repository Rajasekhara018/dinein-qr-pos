import {
  Component, ElementRef, HostListener, Input, OnDestroy, OnInit, Output, EventEmitter, ViewChild, inject,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../shared/services/auth-service';
import { InAppNotificationService } from '../../shared/services/in-app-notification-service';
import { ThemeId, ThemeService } from '../../shared/services/theme-service';
import { HQ_THEME_CATALOG, HqField } from 'hq-shared-lib';
import { FcmService } from '../../shared/services/fcm-service';
import { DeviceLocationService } from '../../shared/services/device-location-service';
import { ProfilePhotoService } from '../../shared/services/profile-photo-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { InAppNotification, InAppNotificationType } from '../../shared/models/models';

import { TranslateService } from '@ngx-translate/core';
import { BrandingService } from '../../shared/services/branding-service';
import { LanguageService } from '../../shared/services/language-service';
import { SessionPreferencesService } from '../../shared/services/session-preferences.service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { OrganizationChoice } from '../../shared/models/uam.models';
import { EXTRA_QUICK_NAV, NAV_ITEMS, NavSection } from '../nav-items.constants';
import { NavAreaService } from '../nav-area.service';
import { NavArea } from '../nav-areas.constants';
import { availableAreas } from '../nav-areas.helpers';
import { HomeApplicationsService } from '../../home/home-applications.service';
import { filterBySubscription } from '../nav-subscription.helpers';
import { filterByOperatingModel } from '../nav-operating-model.helpers';
import { SessionOperatingModelService } from '../session-operating-model.service';
import { isNavSectionHidden } from '../../shared/profile-visibility';
/** `label`/`section` are i18n keys (nav.items.* / nav.sections.*), shared with the sidebar. */
interface NavSuggestion { label: string; path: string; icon: string; section: string; }

/** A page the user may open, before its label and section are translated. */
interface NavEntry { label: string; path: string; icon: string; section: NavSection; }

@Component({
  selector: 'app-header',
  templateUrl: './header.html',
  standalone: false,
  styleUrl: './header.scss',
})
export class Header implements OnInit, OnDestroy {
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  readonly language = inject(LanguageService);
  private readonly sessionPreferences = inject(SessionPreferencesService);
  private readonly translate = inject(TranslateService);
  /** The organization this session works in, and the others the person may switch to. */
  readonly organizationContext = inject(OrganizationContextService);
  organizationMenuOpen = false;
  @Input() title = this.branding.appName;
  /** Hides the mobile hamburger button when Layout has hidden the sidebar (mandatory
   *  change-password) -- otherwise it's a dead tap with no sidebar to open. */
  @Input() hideMenuButton = false;
  @Input() breadcrumbs: Array<{ label: string }> = [];
  @Output() menuClick = new EventEmitter<void>();

  dropdownOpen = false;
  avatarMenuOpen = false;
  languageMenuOpen = false;
  themeMenuOpen = false;
  readonly themes = HQ_THEME_CATALOG;
  notifications: InAppNotification[] = [];
  unreadCount = 0;

  /** The app launcher (grid button): one tile per area of the menu the user may open. */
  launcherOpen = false;
  private readonly navArea = inject(NavAreaService);
  private readonly subscriptions = inject(HomeApplicationsService);
  private readonly operatingModel = inject(SessionOperatingModelService);

  searchQuery = '';
  searchOpen = false;
  searchFocusIndex = -1;

  /**
   * Quick-navigate entries: every menu item this user may open (the sidebar's own list, so a newly added
   * screen appears here automatically) plus the few legacy shortcuts that have no menu entry.
   */
  private get allNavItems(): NavEntry[] {
    // Only the items that fit the organization's operating model (the sidebar's rule).
    const permitted: NavEntry[] = filterByOperatingModel(NAV_ITEMS, this.operatingModel.status(), this.operatingModel.model())
      .filter(n => !n.hidden && !isNavSectionHidden(n.section) && (!n.permission || !this.auth.currentUser || this.auth.canAccess(n.permission)))
      .map(({ label, path, icon, section }) => ({ label, path, icon, section }));
    // Only the applications the organization is subscribed to (the sidebar's rule): quick navigate and the launcher follow it.
    const status = this.subscriptions.status();
    const codes = this.subscriptions.subscribedCodes();
    const fromMenu = filterBySubscription(permitted, status, codes);
    const known = new Set(permitted.map(n => n.path));
    return [...fromMenu, ...filterBySubscription(EXTRA_QUICK_NAV.filter(n => !known.has(n.path)), status, codes)];
  }

  private subs: Subscription[] = [];

  chooseOrganization(organization: OrganizationChoice, current: OrganizationChoice | null): void {
    this.organizationMenuOpen = false;
    if (organization.tenantId !== current?.tenantId || this.organizationContext.viewingAll) {
      this.organizationContext.switchTo(organization);
    }
  }

  /** Lists every organization's records (organization-wide people only). */
  chooseAllOrganizations(): void {
    this.organizationMenuOpen = false;
    if (!this.organizationContext.viewingAll) {
      this.organizationContext.viewAll();
    }
  }

  trackOrganization(_: number, organization: OrganizationChoice): string {
    return organization.tenantId;
  }

  constructor(
    public auth: AuthService,
    public inAppService: InAppNotificationService,
    public theme: ThemeService,
    private router: Router,
    private elRef: ElementRef,
    private fcm: FcmService,
    private deviceLocation: DeviceLocationService,
    private appDialog: AppDialogService,
  ) {}

  /** The user's uploaded profile photo, when they have one (falls back to the initial). */
  private readonly profilePhoto = inject(ProfilePhotoService);
  readonly photoUrl$ = this.profilePhoto.photoUrl$;

  ngOnInit(): void {
    this.subscriptions.ensureLoaded();
    this.operatingModel.ensureLoaded();
    if (this.auth.isAuthenticated()) {
      this.organizationContext.load().subscribe({ error: () => {} });
    }
    this.profilePhoto.load(this.auth.currentUser?.id);
    this.subs.push(
      this.inAppService.notifications$.subscribe(n => (this.notifications = n.slice(0, 8))),
      this.inAppService.unreadCount$.subscribe(c => (this.unreadCount = c)),
      // Set up FCM when a user session is active; clean up when they log out.
      this.auth.user$.subscribe(user => {
        if (user) {
          this.fcm.setup();
          // Where this device is, for the Active devices list (asked once per tab session).
          void this.deviceLocation.captureOnce(user.id);
        } else {
          this.fcm.cleanup();
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
  }

  get userInitial(): string {
    const name = this.auth.currentUser?.name;
    return name ? name[0].toUpperCase() : (this.auth.currentUser?.role?.[0] ?? 'R').toUpperCase();
  }

  get userName(): string {
    return this.auth.currentUser?.name ?? '';
  }

  get userEmail(): string {
    return this.auth.currentUser?.email ?? '';
  }

  get userRole(): string {
    return (this.auth.currentUser?.role ?? '').replace(/_/g, ' ');
  }

  /** Suggestions with label/section already translated into the active language. */
  get searchSuggestions(): NavSuggestion[] {
    const items = this.allNavItems.map(n => ({
      ...n,
      label: this.translate.instant(n.label) as string,
      section: this.translate.instant(n.section) as string,
    }));
    const q = this.searchQuery.trim().toLowerCase();
    if (!q) return items.slice(0, 6);
    return items.filter(n =>
      n.label.toLowerCase().includes(q) || n.section.toLowerCase().includes(q),
    );
  }

  onSearchFocus(): void {
    this.searchOpen = true;
    this.searchFocusIndex = -1;
    if (this.dropdownOpen) this.dropdownOpen = false;
    if (this.avatarMenuOpen) this.avatarMenuOpen = false;
    if (this.languageMenuOpen) this.languageMenuOpen = false;
    this.themeMenuOpen = false;
  }

  onSearchBlur(): void {
    setTimeout(() => { this.searchOpen = false; this.searchFocusIndex = -1; }, 150);
  }

  onSearchKeydown(event: KeyboardEvent): void {
    const items = this.searchSuggestions;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.searchFocusIndex = Math.min(this.searchFocusIndex + 1, items.length - 1);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.searchFocusIndex = Math.max(this.searchFocusIndex - 1, -1);
    } else if (event.key === 'Enter' && this.searchFocusIndex >= 0) {
      event.preventDefault();
      this.navigateTo(items[this.searchFocusIndex]);
    } else if (event.key === 'Escape') {
      this.searchOpen = false;
      this.searchQuery = '';
    }
  }

  navigateTo(item: NavSuggestion): void {
    this.searchOpen = false;
    this.searchQuery = '';
    this.router.navigate([item.path]);
  }

  toggleAvatarMenu(): void {
    this.avatarMenuOpen = !this.avatarMenuOpen;
    if (this.avatarMenuOpen) {
      this.dropdownOpen = false;
      this.languageMenuOpen = false;
      this.themeMenuOpen = false;
    }
  }

  toggleLanguageMenu(): void {
    this.languageMenuOpen = !this.languageMenuOpen;
    if (this.languageMenuOpen) {
      this.dropdownOpen = false;
      this.avatarMenuOpen = false;
      this.themeMenuOpen = false;
    }
  }

  toggleThemeMenu(): void {
    this.themeMenuOpen = !this.themeMenuOpen;
    if (this.themeMenuOpen) {
      this.dropdownOpen = false;
      this.avatarMenuOpen = false;
      this.languageMenuOpen = false;
    }
  }

  selectTheme(id: ThemeId): void {
    this.themeMenuOpen = false;
    this.theme.setTheme(id);
    this.sessionPreferences.saveTheme(id);
  }

  selectLanguage(code: string): void {
    this.languageMenuOpen = false;
    this.language.setLanguage(code);
    this.sessionPreferences.saveLanguage(code);
  }

  closeAvatarMenu(): void {
    this.avatarMenuOpen = false;
  }

  logout(): void {
    this.avatarMenuOpen = false;
    this.appDialog.confirm({
      title: this.translate.instant('common.logout.title'),
      message: this.translate.instant('common.logout.message'),
      icon: 'bi-box-arrow-right',
      variant: 'warning',
      confirmText: this.translate.instant('common.logout.confirm'),
    }).subscribe((confirmed) => {
      if (confirmed) this.auth.logout();
    });
  }

  get badgeLabel(): string {
    return this.unreadCount > 99 ? '99+' : String(this.unreadCount);
  }

  onMenuClick(): void {
    this.menuClick.emit();
  }

  toggleDropdown(): void {
    this.dropdownOpen = !this.dropdownOpen;
    if (this.dropdownOpen) {
      this.avatarMenuOpen = false;
      this.languageMenuOpen = false;
      this.themeMenuOpen = false;
    }
  }

  /** The areas this user may open, from the same permitted pages the quick-navigate lists. */
  get launcherAreas(): NavArea[] {
    return availableAreas(this.allNavItems);
  }

  toggleLauncher(): void {
    this.launcherOpen = !this.launcherOpen;
    if (this.launcherOpen) {
      this.dropdownOpen = false;
      this.avatarMenuOpen = false;
      this.languageMenuOpen = false;
      this.themeMenuOpen = false;
    }
  }

  /** Opens an area: the menu switches to it and the first page the user may open in it is shown. */
  openArea(area: NavArea): void {
    this.launcherOpen = false;
    const first = this.allNavItems.find((item) => area.sections.includes(item.section));
    this.navArea.select(area.key);
    if (first) void this.router.navigateByUrl(first.path);
  }

  @ViewChild('launcherStrip') private launcherStrip?: ElementRef<HTMLElement>;

  /** Scrolls the launcher's strip of areas one step left (-1) or right (1), smoothly; the arrows flip in a right-to-left page. */
  scrollLauncher(direction: -1 | 1): void {
    const strip = this.launcherStrip?.nativeElement;
    if (!strip) return;
    const rtl = getComputedStyle(strip).direction === 'rtl';
    strip.scrollBy({ left: direction * (rtl ? -1 : 1) * strip.clientWidth * 0.8, behavior: 'smooth' });
  }

  @ViewChild('quickSearch') private quickSearch?: HqField;

  /** Ctrl+K (Cmd+K on a Mac) jumps to the quick-navigate box from anywhere. */
  @HostListener('document:keydown', ['$event'])
  onShortcut(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && this.quickSearch) {
      event.preventDefault();
      this.quickSearch.focusControl();
      this.quickSearch.selectControl();
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    if (!this.elRef.nativeElement.contains(event.target)) {
      this.dropdownOpen = false;
      this.avatarMenuOpen = false;
      this.languageMenuOpen = false;
      this.launcherOpen = false;
    }
  }

  onNotificationClick(n: InAppNotification): void {
    if (!n.read) {
      this.inAppService.markAsRead(n.id);
    }
    this.dropdownOpen = false;
    const route = this.resolveRoute(n);
    if (route) this.router.navigate(route);
  }

  // TODO: extend once real booking/ride notification types exist for this app.
  private resolveRoute(n: InAppNotification): string[] | null {
    if (!n.referenceId) return null;
    const type = n.referenceType?.toUpperCase();
    if (type === 'BOOKING') return ['/my-bookings', n.referenceId];
    return null;
  }

  onMarkAllRead(): void {
    this.inAppService.markAllAsRead();
  }

  viewAll(): void {
    this.dropdownOpen = false;
    this.router.navigate(['/notifications']);
  }

  notificationIcon(type: InAppNotificationType): string {
    if (!type) return 'bi-bell-fill';
    if (type.startsWith('BOOKING')) return 'bi-ticket-perforated-fill';
    if (type.startsWith('DRIVER')) return 'bi-person-fill';
    if (type.startsWith('RIDE')) return 'bi-car-front-fill';
    if (type === 'REFUND_REQUESTED') return 'bi-arrow-return-left';
    return 'bi-bell-fill';
  }

  notificationIconColor(type: InAppNotificationType): string {
    if (!type) return 'icon-blue';
    if (type.includes('CONFIRMED') || type.includes('COMPLETED')) return 'icon-green';
    if (type.includes('CANCELLED')) return 'icon-red';
    if (type.includes('ALERT') || type.includes('REQUESTED')) return 'icon-amber';
    return 'icon-blue';
  }

  timeAgo(isoString: string): string {
    const diff = Date.now() - new Date(isoString).getTime();
    const m = Math.floor(diff / 60000);
    if (m < 1) return this.translate.instant('header.timeAgo.justNow');
    if (m < 60) return this.translate.instant('header.timeAgo.minutes', { count: m });
    const h = Math.floor(m / 60);
    if (h < 24) return this.translate.instant('header.timeAgo.hours', { count: h });
    return this.translate.instant('header.timeAgo.days', { count: Math.floor(h / 24) });
  }
}
