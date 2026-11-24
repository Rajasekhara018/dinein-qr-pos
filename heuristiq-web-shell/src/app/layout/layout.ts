import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, NavigationEnd, NavigationStart, NavigationCancel, NavigationError } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

import { BrandingService } from '../shared/services/branding-service';
import { findNavItemForUrl } from './nav-items.constants';
interface Crumb { label: string; path?: string; }

// Real feature routes (booking, my-rides, wallet, profile, etc.) will each add
// an entry here as they're built — see AdminWeb's layout.ts for the pattern.
// `title` and crumb `label` are translation keys (pageMeta.*), resolved with the `translate` pipe
// in layout.html / header.html so a language switch updates the current page's title live.
const PAGE_META: Record<string, { title: string; crumbs: Crumb[] }> = {
  '/home': { title: 'pageMeta.home.title', crumbs: [{ label: 'pageMeta.home.breadcrumb' }] },
  '/payments': {
    title: 'pageMeta.payments.title',
    crumbs: [{ label: 'pageMeta.home.breadcrumb', path: '/home' }, { label: 'pageMeta.payments.breadcrumb' }],
  },
  '/nms-templates': { title: 'pageMeta.nmsTemplates.title', crumbs: [{ label: 'pageMeta.nmsTemplates.breadcrumb' }] },
  '/change-password': { title: 'nav.items.changePassword', crumbs: [{ label: 'pageMeta.home.breadcrumb', path: '/home' }] },
};

// Routes that render their own full-bleed hero layout (login-style) instead of a normal
// card-in-a-padded-page screen -- .page-content's default padding would otherwise leave an
// unwanted gap around them. Add a path here for any future route with the same treatment.
// '/food' is the food delivery console (remote 'railcab-food'): it brings its own canvas and page padding, so it fills the
// content area end to end. Matched on the first segment, so every /food/* screen is full bleed.
const FULL_BLEED_ROUTES = new Set<string>(['/change-password', '/food']);

@Component({
  selector: 'app-layout',
  templateUrl: './layout.html',
  standalone: false,
  styleUrl: './layout.scss',
})
export class Layout implements OnInit, OnDestroy {
  /** Per-deployment branding, loaded at runtime by BrandingService. */
  readonly branding = inject(BrandingService).branding;
  collapsed = false;
  mobileOpen = false;
  /** Translation key for the current route's title; null falls back to the branded app name. */
  pageTitleKey: string | null = null;
  breadcrumbs: Crumb[] = [];
  routeLoading = false;
  isFullBleed = false;
  /** True only for a mandatory (not voluntary) change-password visit -- see login.ts's
   *  forcePasswordChange redirect. Hides the nav sidebar so the user can't navigate away
   *  before completing it; the header (logo, language switcher, logout) stays visible. */
  hideSidebar = false;
  /**
   * False for an instant after a language change, so the page in the outlet is created again. Screens that built text once (table
   * headings, help boxes) would otherwise keep showing the language they were opened in until the page was reloaded.
   */
  showPage = true;
  private readonly translate = inject(TranslateService, { optional: true });
  private langSub?: Subscription;
  private rebuildTimer?: ReturnType<typeof setTimeout>;

  constructor(private router: Router) {}

  ngOnInit(): void {
    this.langSub = this.translate?.onLangChange.subscribe(() => this.rebuildPage());
    // The layout is created DURING the navigation that first shows it (a sign-in redirect, a page reload, a link opened
    // in a new tab), and that navigation's NavigationEnd has already fired by the time ngOnInit runs. So the URL it was
    // created on is resolved here; without this a reloaded /reports/reports-config/cre?productName=X kept the
    // branded fallback title and no breadcrumb until the next navigation (the sidebar already did the same with router.url).
    if (this.router.url) {
      this.applyUrl(this.router.url);
    }
    this.router.events.subscribe(e => {
      if (e instanceof NavigationStart) {
        this.routeLoading = true;
      } else if (e instanceof NavigationEnd) {
        this.routeLoading = false;
        this.applyUrl(e.urlAfterRedirects);
        this.mobileOpen = false;
      } else if (e instanceof NavigationCancel || e instanceof NavigationError) {
        this.routeLoading = false;
      }
    });
  }

  ngOnDestroy(): void {
    this.langSub?.unsubscribe();
    clearTimeout(this.rebuildTimer);
  }

  /** Removes the routed page and puts it back on the next tick; the router creates it again in the new language. */
  private rebuildPage(): void {
    this.showPage = false;
    clearTimeout(this.rebuildTimer);
    this.rebuildTimer = setTimeout(() => (this.showPage = true), 0);
  }

  /** Title, breadcrumb and page chrome of the screen at `url`. */
  private applyUrl(url: string): void {
    // Query string and fragment are dropped first, otherwise '/change-password?reason=forced' never matches '/change-password'.
    const path = '/' + url.split(/[?#]/)[0].split('/').slice(1, 2).join('');
    // Hand-written entries first; every other screen (all the /admin/* ones included) takes its title and
    // breadcrumb from the sidebar's own menu, so a newly added screen needs no second list to maintain.
    const meta = PAGE_META[path] ?? this.metaFromMenu(url);
    this.pageTitleKey = meta?.title ?? null;
    this.breadcrumbs = meta?.crumbs ?? [];
    this.isFullBleed = FULL_BLEED_ROUTES.has(path);
    const queryParams = this.router.parseUrl(url).queryParams;
    this.hideSidebar = path === '/change-password' && queryParams['reason'] === 'forced';
  }

  private metaFromMenu(url: string): { title: string; crumbs: Crumb[] } | undefined {
    const item = findNavItemForUrl(url);
    return item ? { title: item.label, crumbs: [{ label: item.section }, { label: item.label }] } : undefined;
  }

  toggleSidebar(): void {
    this.collapsed = !this.collapsed;
  }

  openMobile(): void {
    this.mobileOpen = true;
  }

  closeMobile(): void {
    this.mobileOpen = false;
  }
}
