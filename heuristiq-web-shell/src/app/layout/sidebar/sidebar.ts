import { Component, ElementRef, Input, Output, EventEmitter, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { AuthService } from '../../shared/services/auth-service';
import { AppDialogService } from '../../shared/services/app-dialog-service';

import { TranslateService } from '@ngx-translate/core';
import { BrandingService } from '../../shared/services/branding-service';
import { ProfilePhotoService } from '../../shared/services/profile-photo-service';
import { PendingReviewsService } from '../../shared/services/pending-reviews-service';
import {
  PENDING_REVIEWS_BADGE,
  PENDING_REVIEWS_BADGE_MAX,
  PENDING_REVIEWS_PERMISSION,
} from '../../shared/services/pending-reviews.constants';
import {
  NAV_ITEMS, NavItem, SIDEBAR_DEFAULT_COLLAPSED_SECTIONS, SIDEBAR_SECTION_STATE_KEY, findNavItemForUrl,
} from '../nav-items.constants';
import {
  NAV_AREA_ALL, NAV_FAVOURITES_MAX, NAV_FAVOURITES_STATE_KEY, NAV_RECENTS_MAX, NAV_RECENTS_SHOWN, NAV_RECENTS_STATE_KEY, NavArea,
} from '../nav-areas.constants';
import {
  areaOfSection, availableAreas, effectiveArea, inArea, parseStoredPaths, pushRecent, toggleFavourite,
} from '../nav-areas.helpers';
import { NavAreaService } from '../nav-area.service';
import { isNavSectionHidden } from '../../shared/profile-visibility';
import { HomeApplicationsService } from '../../home/home-applications.service';
import { awaitsSubscription, filterBySubscription } from '../nav-subscription.helpers';
import { filterByOperatingModel } from '../nav-operating-model.helpers';
import { SessionOperatingModelService } from '../session-operating-model.service';
import { TextSegment, highlightSegments, matchesTokens, searchTokens } from './sidebar-filter.helpers';

/** One line of the menu as drawn: the item plus its label split into matched and unmatched pieces. */
export interface NavRow {
  item: NavItem;
  segments: TextSegment[];
}

const ADMIN_ROLES = ['SUPER_ADMIN', 'MANAGER', 'ACCOUNTANT', 'SUPPORT', 'AUDITOR'];

@Component({
  selector: 'app-sidebar',
  templateUrl: './sidebar.html',
  standalone: false,
  styleUrl: './sidebar.scss',
})
export class Sidebar implements OnInit, OnDestroy {
  /** Per-deployment branding (app name, logo, tagline), loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  @Input() collapsed = false;
  @Input() mobileOpen = false;
  @Output() toggle = new EventEmitter<void>();
  @Output() closeMobile = new EventEmitter<void>();

  /** The menu comes from nav-items.constants.ts, shared with the header's quick-navigate and page title. */
  navItems: NavItem[] = NAV_ITEMS;

  /** What the user typed in the sidebar's search box; filtering never changes which sections are folded. */
  searchQuery = '';

  @ViewChild('navList') private navList?: ElementRef<HTMLElement>;

  private rowsCache: { key: string; rows: NavRow[] } = { key: '', rows: [] };

  constructor(
    public auth: AuthService,
    private appDialog: AppDialogService,
    private translate: TranslateService,
  ) {
    this.profilePhoto.load(this.auth.currentUser?.id);
  }

  private readonly router = inject(Router);
  private routerSubscription?: Subscription;
  /** Path of the menu item the current page belongs to: a page inside a screen (an edit form, a detail) keeps its menu item lit. */
  activePath: string | undefined = findNavItemForUrl(this.router.url)?.path;

  /** Sections the user has folded; remembered per browser, with the rarely used ones folded to begin with. */
  private readonly foldedSections = new Set<string>(this.loadFoldedSections());

  /** The area chosen with the switcher (or NAV_AREA_ALL), the starred pages and the recently opened ones; all remembered per browser. */
  private readonly navArea = inject(NavAreaService);
  private favourites: string[] = parseStoredPaths(this.readStored(NAV_FAVOURITES_STATE_KEY));
  private recents: string[] = parseStoredPaths(this.readStored(NAV_RECENTS_STATE_KEY));

  private readonly profilePhoto = inject(ProfilePhotoService);
  private readonly subscriptions = inject(HomeApplicationsService);
  private readonly operatingModel = inject(SessionOperatingModelService);
  private readonly pendingReviews = inject(PendingReviewsService);

  /** Maker-checker requests waiting for this user's decision (0 hides the badge). */
  readonly pendingReviews$ = this.pendingReviews.count$;

  ngOnInit(): void {
    this.routerSubscription = this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.activePath = findNavItemForUrl(event.urlAfterRedirects)?.path;
        this.unfoldActiveSection();
        this.followActivePage();
        this.subscriptions.ensureLoaded();
        this.operatingModel.ensureLoaded();
      });
    this.subscriptions.ensureLoaded();
    this.operatingModel.ensureLoaded();
    this.unfoldActiveSection();
    this.followActivePage();
    if (this.auth.currentUser && this.auth.canAccess(PENDING_REVIEWS_PERMISSION)) this.pendingReviews.start();
  }

  ngOnDestroy(): void {
    this.routerSubscription?.unsubscribe();
    this.pendingReviews.stop();
  }

  /** Count text for an item's badge, or '' when it has none or nothing is waiting. */
  badgeText(item: NavItem, pending: number | null): string {
    if (item.badge !== PENDING_REVIEWS_BADGE || !pending) return '';
    return pending > PENDING_REVIEWS_BADGE_MAX ? `${PENDING_REVIEWS_BADGE_MAX}+` : String(pending);
  }

  /** The user's uploaded profile photo, when they have one (falls back to the initial). */
  readonly photoUrl$ = this.profilePhoto.photoUrl$;

  isVisible(item: NavItem): boolean {
    // if (item.section === 'Admin') return this.auth.hasRole(...ADMIN_ROLES);
    if (!item.permission || !this.auth.currentUser) return true;
    return this.auth.canAccess(item.permission);
  }

  /** The areas this user can open (those with at least one permitted page). */
  get areas(): NavArea[] {
    return availableAreas(this.visibleNavItems);
  }

  /** The area whose pages are listed: the chosen one, or everything when the user has no such area. */
  get activeArea(): string {
    return effectiveArea(this.navArea.selected, this.areas);
  }

  readonly allAreasKey = NAV_AREA_ALL;

  setArea(key: string): void {
    this.navArea.select(key);
  }

  /** The starred pages the user may still open, in the order they were starred. */
  get favouriteItems(): NavItem[] {
    return this.itemsOf(this.favourites);
  }

  /** The recently opened pages the user may still open, newest first, without the ones already starred or the page being viewed. */
  get recentItems(): NavItem[] {
    return this.itemsOf(this.recents.filter((path) => !this.favourites.includes(path) && path !== this.activePath)).slice(0, NAV_RECENTS_SHOWN);
  }

  isFavourite(path: string): boolean {
    return this.favourites.includes(path);
  }

  /** Stars or unstars a page (the star is a button inside the link, so the click must not open the page). */
  toggleFavourite(path: string, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.favourites = toggleFavourite(this.favourites, path, NAV_FAVOURITES_MAX);
    this.writeStored(NAV_FAVOURITES_STATE_KEY, JSON.stringify(this.favourites));
  }

  get favouritesFull(): boolean {
    return this.favourites.length >= NAV_FAVOURITES_MAX;
  }

  /** Opening a page records it in the recents and brings that page's area up, so the lit item is always in the list. */
  private followActivePage(): void {
    const path = this.activePath;
    const item = path ? NAV_ITEMS.find((n) => n.path === path) : undefined;
    if (!item) return;
    this.recents = pushRecent(this.recents, item.path, NAV_RECENTS_MAX);
    this.writeStored(NAV_RECENTS_STATE_KEY, JSON.stringify(this.recents));
    const area = areaOfSection(item.section);
    // Stays put when the page already shows in the chosen area (a workspace page under the application it came from).
    if (area && this.navArea.selected !== NAV_AREA_ALL && !inArea(item, this.navArea.selected) && this.areas.some((a) => a.key === area.key)) this.setArea(area.key);
  }

  private itemsOf(paths: readonly string[]): NavItem[] {
    const visible = new Map(this.visibleNavItems.map((item) => [item.path, item]));
    return paths.map((path) => visible.get(path)).filter((item): item is NavItem => !!item);
  }

  private readStored(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private writeStored(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      // storage unavailable: the choice just lasts until reload
    }
  }

  /**
   * The items the user may open and that fit the organization's operating model (franchise-only items are left out for a
   * company-run organization), before the application subscriptions are applied.
   */
  private get permittedNavItems(): NavItem[] {
    const permitted = this.navItems.filter(item => !item.hidden && !isNavSectionHidden(item.section) && this.isVisible(item));
    return filterByOperatingModel(permitted, this.operatingModel.status(), this.operatingModel.model());
  }

  /**
   * The items shown anywhere in the sidebar (menu, areas, favourites, recents): permitted AND belonging to an application the
   * organization is subscribed to. Core areas show at once; the others wait for the lookup and stay hidden if it fails.
   */
  get visibleNavItems(): NavItem[] {
    return filterBySubscription(this.permittedNavItems, this.subscriptions.status(), this.subscriptions.subscribedCodes());
  }

  /** True while the subscription lookup is outstanding and applications are waiting on it: the menu shows a placeholder. */
  get appsLoading(): boolean {
    return awaitsSubscription(this.permittedNavItems, this.subscriptions.status());
  }

  /** True while a search is applied; the icon-only sidebar has no search box, so it never filters. */
  get searching(): boolean {
    return !this.iconOnly && searchTokens(this.searchQuery).length > 0;
  }

  private get iconOnly(): boolean {
    return this.collapsed && !this.mobileOpen;
  }

  /**
   * The lines drawn in the menu: every item the user may open, narrowed to the search when one is typed (by the
   * translated label or section, so it works in every language). Rebuilt only when the search, the language or the
   * set of allowed items changes, since the template reads it on every change-detection pass.
   */
  get rows(): NavRow[] {
    const tokens = this.iconOnly ? [] : searchTokens(this.searchQuery);
    const area = this.activeArea;
    // A search looks through every area; otherwise only the chosen area's pages are listed.
    const visible = tokens.length > 0 ? this.visibleNavItems : this.visibleNavItems.filter((item) => inArea(item, area));
    const key = `${tokens.join(' ')}|${this.translate.currentLang}|${visible.map((item) => item.path).join(',')}`;
    if (key === this.rowsCache.key) return this.rowsCache.rows;
    const rows: NavRow[] = [];
    for (const item of visible) {
      const label = this.translate.instant(item.label) as string;
      if (tokens.length === 0) {
        rows.push({ item, segments: [{ text: label, match: false }] });
        continue;
      }
      const section = this.translate.instant(item.section) as string;
      if (matchesTokens(label, section, tokens)) rows.push({ item, segments: highlightSegments(label, tokens) });
    }
    this.rowsCache = { key, rows };
    return rows;
  }

  /** Whether the section's items are listed: a search shows every match, the icon-only sidebar lists everything, otherwise folded sections are skipped. */
  isSectionOpen(item: NavItem): boolean {
    return this.searching || this.iconOnly || !this.foldedSections.has(item.section);
  }

  clearSearch(): void {
    this.searchQuery = '';
  }

  /** Enter opens the first match, the down arrow moves into the list, Escape clears the search. */
  onSearchKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.clearSearch();
      event.preventDefault();
      return;
    }
    if (event.key === 'ArrowDown') {
      this.navList?.nativeElement.querySelector<HTMLElement>('a.nav-item')?.focus();
      event.preventDefault();
      return;
    }
    if (event.key === 'Enter') {
      const first = this.rows[0]?.item;
      if (first) {
        void this.router.navigateByUrl(first.path);
        this.clearSearch();
        this.onCloseMobile();
      }
      event.preventDefault();
    }
  }

  toggleSection(section: string): void {
    if (!this.foldedSections.delete(section)) this.foldedSections.add(section);
    this.saveFoldedSections();
  }

  /** A page inside a folded section opens that section, so the lit menu item is never hidden. */
  private unfoldActiveSection(): void {
    const section = NAV_ITEMS.find((item) => item.path === this.activePath)?.section;
    if (section && this.foldedSections.delete(section)) this.saveFoldedSections();
  }

  private loadFoldedSections(): string[] {
    try {
      const saved = localStorage.getItem(SIDEBAR_SECTION_STATE_KEY);
      if (saved) {
        const parsed: unknown = JSON.parse(saved);
        if (Array.isArray(parsed)) return parsed.filter((v): v is string => typeof v === 'string');
      }
    } catch {
      // storage unavailable or corrupt: fall back to the defaults
    }
    return [...SIDEBAR_DEFAULT_COLLAPSED_SECTIONS];
  }

  private saveFoldedSections(): void {
    try {
      localStorage.setItem(SIDEBAR_SECTION_STATE_KEY, JSON.stringify([...this.foldedSections]));
    } catch {
      // storage unavailable: the choice just lasts until reload
    }
  }

  showSectionLabel(index: number): boolean {
    if (this.iconOnly) return false;
    const current = this.rows[index]?.item;
    const previous = this.rows[index - 1]?.item;
    return !!current && current.section !== previous?.section;
  }

  get userInitial(): string {
    const name = this.auth.currentUser?.name;
    return name ? name[0].toUpperCase() : (this.auth.currentUser?.role?.[0] ?? 'R').toUpperCase();
  }

  get userName(): string {
    return this.auth.currentUser?.name ?? this.auth.currentUser?.email ?? '';
  }

  get userEmail(): string {
    return this.auth.currentUser?.email ?? '';
  }

  get userRole(): string {
    return (this.auth.currentUser?.role ?? '').replace(/_/g, ' ');
  }

  onToggle(): void {
    this.toggle.emit();
  }

  onCloseMobile(): void {
    this.closeMobile.emit();
  }

  logout(): void {
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
}
