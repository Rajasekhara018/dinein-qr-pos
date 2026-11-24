import { TestBed } from '@angular/core/testing';
import { Event as RouterEvent, NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BrandingService } from '../../shared/services/branding-service';
import { PendingReviewsService } from '../../shared/services/pending-reviews-service';
import { PENDING_REVIEWS_BADGE, PENDING_REVIEWS_PERMISSION } from '../../shared/services/pending-reviews.constants';
import { ProfilePhotoService } from '../../shared/services/profile-photo-service';
import { NAV_ITEMS, NavItem, SIDEBAR_DEFAULT_COLLAPSED_SECTIONS, SIDEBAR_SECTION_STATE_KEY } from '../nav-items.constants';
import {
  NAV_AREA_STATE_KEY, NAV_FAVOURITES_MAX, NAV_FAVOURITES_STATE_KEY, NAV_RECENTS_STATE_KEY,
} from '../nav-areas.constants';
import { signal } from '@angular/core';
import { HomeApplicationsService } from '../../home/home-applications.service';
import { APP_CODE_BY_SUITE_KEY } from '../../home/home.constants';
import { HomeLoadStatus } from '../../home/home.models';
import { Sidebar } from './sidebar';

function make(opts: { url?: string; user?: object | null; allowed?: (p: string) => boolean; status?: HomeLoadStatus; codes?: string[] } = {}) {
  localStorage.removeItem(SIDEBAR_SECTION_STATE_KEY);
  const events = new Subject<RouterEvent>();
  const router = { events, url: opts.url ?? '/profile', navigateByUrl: vi.fn().mockResolvedValue(true) };
  const auth = {
    currentUser: opts.user === undefined ? { id: 'u1', name: 'asha', email: 'a@x', role: 'SUPER_ADMIN' } : opts.user,
    canAccess: vi.fn((p: string) => (opts.allowed ? opts.allowed(p) : true)),
    logout: vi.fn(),
  };
  const dialog = { confirm: vi.fn().mockReturnValue(of(true)) };
  const translate = { instant: (k: string) => k, currentLang: 'en' };
  const photo = { load: vi.fn(), photoUrl$: of(null) };
  const pending = { start: vi.fn(), stop: vi.fn(), count$: new BehaviorSubject(0) };
  const subscriptions = {
    status: signal<HomeLoadStatus>(opts.status ?? 'ready'),
    subscribedCodes: signal<string[]>(opts.codes ?? Object.values(APP_CODE_BY_SUITE_KEY)),
    ensureLoaded: vi.fn(),
  };
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: Router, useValue: router },
      { provide: BrandingService, useValue: { branding: { appName: 'Acme' } } },
      { provide: ProfilePhotoService, useValue: photo },
      { provide: PendingReviewsService, useValue: pending },
      { provide: HomeApplicationsService, useValue: subscriptions },
    ],
  });
  const s = TestBed.runInInjectionContext(() => new Sidebar(auth as never, dialog as never, translate as never));
  return { s, events, router, auth, dialog, photo, pending, subscriptions };
}

describe('Sidebar', () => {
  beforeEach(() => localStorage.clear());

  it('loads the profile photo of the signed-in user', () => {
    const { photo } = make();
    expect(photo.load).toHaveBeenCalledWith('u1');
  });

  it('shows items without a permission, and items whose permission the user holds', () => {
    const { s } = make({ allowed: () => false });
    const paths = s.visibleNavItems.map((i) => i.path);
    expect(paths).toContain('/profile');
    expect(NAV_ITEMS.some((i) => i.permission)).toBe(true);
    for (const item of s.visibleNavItems) expect(item.permission === null || false).toBe(true);
    const all = make({ allowed: () => true }).s.visibleNavItems;
    expect(all.length).toBeGreaterThan(paths.length);
  });

  it('never lists hidden items', () => {
    const { s } = make();
    expect(s.visibleNavItems.some((i) => i.hidden)).toBe(false);
  });

  it('asks the auth service about each permission and shows everything before a user is known', () => {
    const { s, auth } = make({ user: null });
    expect(s.isVisible({ permission: 'x' } as NavItem)).toBe(true);
    expect(auth.canAccess).not.toHaveBeenCalled();
    const known = make({ allowed: (p) => p === 'only_this' });
    expect(known.s.isVisible({ permission: 'only_this' } as NavItem)).toBe(true);
    expect(known.s.isVisible({ permission: 'other' } as NavItem)).toBe(false);
  });

  describe('pending reviews badge', () => {
    const item = { badge: PENDING_REVIEWS_BADGE } as NavItem;
    it('shows the count, capped at 99+, and nothing for zero or other items', () => {
      const { s } = make();
      expect(s.badgeText(item, 5)).toBe('5');
      expect(s.badgeText(item, 99)).toBe('99');
      expect(s.badgeText(item, 100)).toBe('99+');
      expect(s.badgeText(item, 0)).toBe('');
      expect(s.badgeText(item, null)).toBe('');
      expect(s.badgeText({} as NavItem, 5)).toBe('');
    });

    it('starts polling only for a user who may review changes, and stops on destroy', () => {
      const yes = make({ allowed: (p) => p === PENDING_REVIEWS_PERMISSION });
      yes.s.ngOnInit();
      expect(yes.pending.start).toHaveBeenCalled();
      yes.s.ngOnDestroy();
      expect(yes.pending.stop).toHaveBeenCalled();
      const no = make({ allowed: () => false });
      no.s.ngOnInit();
      expect(no.pending.start).not.toHaveBeenCalled();
      const anon = make({ user: null });
      anon.s.ngOnInit();
      expect(anon.pending.start).not.toHaveBeenCalled();
    });
  });

  describe('sections', () => {
    it('starts with the rarely used sections folded and shows labels once per section', () => {
      const { s } = make();
      const folded = NAV_ITEMS.find((i) => SIDEBAR_DEFAULT_COLLAPSED_SECTIONS.includes(i.section))!;
      expect(s.isSectionOpen(folded)).toBe(false);
      expect(s.isSectionOpen(NAV_ITEMS.find((i) => i.path === '/profile')!)).toBe(true);
      const rows = s.rows;
      expect(s.showSectionLabel(0)).toBe(true);
      const sameAsPrevious = rows.findIndex((r, i) => i > 0 && r.item.section === rows[i - 1].item.section);
      expect(s.showSectionLabel(sameAsPrevious)).toBe(false);
      expect(s.showSectionLabel(9999)).toBe(false);
    });

    it('toggling a section folds it and remembers the choice per browser', () => {
      const { s } = make();
      const profile = NAV_ITEMS.find((i) => i.path === '/profile')!;
      s.toggleSection(profile.section);
      expect(s.isSectionOpen(profile)).toBe(false);
      expect(JSON.parse(localStorage.getItem(SIDEBAR_SECTION_STATE_KEY)!)).toContain(profile.section);
      s.toggleSection(profile.section);
      expect(s.isSectionOpen(profile)).toBe(true);
    });

    it('uses the saved folds, ignoring corrupt or malformed storage', () => {
      localStorage.setItem(SIDEBAR_SECTION_STATE_KEY, JSON.stringify(['nav.sections.account', 5]));
      TestBed.resetTestingModule();
      const profile = NAV_ITEMS.find((i) => i.path === '/profile')!;
      const router = { events: new Subject(), url: '/x', navigateByUrl: vi.fn() };
      TestBed.configureTestingModule({
        providers: [{ provide: Router, useValue: router }, { provide: BrandingService, useValue: { branding: {} } },
          { provide: ProfilePhotoService, useValue: { load: vi.fn(), photoUrl$: of(null) } },
          { provide: PendingReviewsService, useValue: { count$: of(0) } },
          { provide: HomeApplicationsService, useValue: { status: signal<HomeLoadStatus>('ready'), subscribedCodes: signal<string[]>([]), ensureLoaded: vi.fn() } }],
      });
      const s = TestBed.runInInjectionContext(() => new Sidebar({ currentUser: null } as never, {} as never, { instant: (k: string) => k } as never));
      expect(s.isSectionOpen(profile)).toBe(false);
      localStorage.setItem(SIDEBAR_SECTION_STATE_KEY, '{bad');
      const s2 = TestBed.runInInjectionContext(() => new Sidebar({ currentUser: null } as never, {} as never, { instant: (k: string) => k } as never));
      expect(s2.isSectionOpen(NAV_ITEMS.find((i) => SIDEBAR_DEFAULT_COLLAPSED_SECTIONS.includes(i.section))!)).toBe(false);
    });

    it('opens the section of the page being visited, and follows navigation', () => {
      const { s, events } = make({ url: '/workflow' });
      s.ngOnInit();
      expect(s.activePath).toBe('/workflow');
      expect(s.isSectionOpen(NAV_ITEMS.find((i) => i.path === '/workflow')!)).toBe(true);
      events.next(new NavigationEnd(1, '/profile/edit', '/profile/edit'));
      expect(s.activePath).toBe('/profile');
      s.ngOnDestroy();
      events.next(new NavigationEnd(2, '/workflow', '/workflow'));
      expect(s.activePath).toBe('/profile');
    });
  });

  describe('search', () => {
    it('narrows the rows by translated label or section and marks the match', () => {
      const { s } = make();
      const total = s.rows.length;
      expect(s.searching).toBe(false);
      s.searchQuery = 'myprofile';
      expect(s.searching).toBe(true);
      expect(s.rows.map((r) => r.item.path)).toEqual(['/profile']);
      expect(s.rows[0].segments.some((seg) => seg.match)).toBe(true);
      s.searchQuery = 'zzzzzz';
      expect(s.rows).toEqual([]);
      s.clearSearch();
      expect(s.rows).toHaveLength(total);
    });

    it('reuses the built rows until the inputs change', () => {
      const { s } = make();
      expect(s.rows).toBe(s.rows);
    });

    it('does not filter, and lists every section, in the icon-only sidebar', () => {
      const { s } = make();
      s.collapsed = true;
      s.searchQuery = 'zzzzzz';
      expect(s.searching).toBe(false);
      expect(s.rows.length).toBeGreaterThan(0);
      expect(s.isSectionOpen(NAV_ITEMS.find((i) => SIDEBAR_DEFAULT_COLLAPSED_SECTIONS.includes(i.section))!)).toBe(true);
      expect(s.showSectionLabel(0)).toBe(false);
      s.searchQuery = '';
      s.mobileOpen = true;
      expect(s.showSectionLabel(0)).toBe(true);
    });

    it('a search shows matches even inside folded sections', () => {
      const { s } = make();
      s.searchQuery = 'workflow';
      expect(s.isSectionOpen(NAV_ITEMS.find((i) => i.path === '/workflow')!)).toBe(true);
    });

    it('Escape clears, ArrowDown moves into the list, Enter opens the first match and closes the mobile menu', () => {
      const { s, router } = make();
      const closed = vi.fn();
      s.closeMobile.subscribe(closed);
      const key = (k: string) => ({ key: k, preventDefault: vi.fn() }) as unknown as KeyboardEvent;
      s.searchQuery = 'abc';
      const esc = key('Escape');
      s.onSearchKeydown(esc);
      expect(s.searchQuery).toBe('');
      expect(esc.preventDefault).toHaveBeenCalled();

      const focus = vi.fn();
      (s as unknown as { navList: unknown }).navList = { nativeElement: { querySelector: () => ({ focus }) } };
      s.onSearchKeydown(key('ArrowDown'));
      expect(focus).toHaveBeenCalled();

      s.searchQuery = 'myprofile';
      s.onSearchKeydown(key('Enter'));
      expect(router.navigateByUrl).toHaveBeenCalledWith('/profile');
      expect(s.searchQuery).toBe('');
      expect(closed).toHaveBeenCalled();

      router.navigateByUrl.mockClear();
      s.searchQuery = 'zzzzzz';
      const enter = key('Enter');
      s.onSearchKeydown(enter);
      expect(router.navigateByUrl).not.toHaveBeenCalled();
      expect(enter.preventDefault).toHaveBeenCalled();
      const other = key('a');
      s.onSearchKeydown(other);
      expect(other.preventDefault).not.toHaveBeenCalled();
    });
  });

  describe('user block and actions', () => {
    it('shows initial, name, email and role, with fallbacks', () => {
      const { s, auth } = make();
      expect(s.userInitial).toBe('A');
      expect(s.userName).toBe('asha');
      expect(s.userEmail).toBe('a@x');
      expect(s.userRole).toBe('SUPER ADMIN');
      auth.currentUser = { role: 'DRIVER_OPS', email: 'e@x' } as never;
      expect(s.userInitial).toBe('D');
      expect(s.userName).toBe('e@x');
      auth.currentUser = null;
      expect(s.userInitial).toBe('R');
      expect(s.userName).toBe('');
      expect(s.userEmail).toBe('');
      expect(s.userRole).toBe('');
    });

    it('emits toggle and close events', () => {
      const { s } = make();
      const toggle = vi.fn();
      const close = vi.fn();
      s.toggle.subscribe(toggle);
      s.closeMobile.subscribe(close);
      s.onToggle();
      s.onCloseMobile();
      expect(toggle).toHaveBeenCalled();
      expect(close).toHaveBeenCalled();
    });

    it('signs out only after the confirmation', () => {
      const { s, auth, dialog } = make();
      dialog.confirm.mockReturnValue(of(false));
      s.logout();
      expect(auth.logout).not.toHaveBeenCalled();
      dialog.confirm.mockReturnValue(of(true));
      s.logout();
      expect(auth.logout).toHaveBeenCalled();
    });
  });

  describe('areas, favourites and recents', () => {
    const navigate = (events: Subject<RouterEvent>, url: string) => events.next(new NavigationEnd(1, url, url));

    it('lists every page at first and only the chosen area (plus the main section) after choosing one', () => {
      const { s } = make();
      expect(s.activeArea).toBe('all');
      const all = s.rows.length;

      s.setArea('reports');

      const sections = new Set(s.rows.map((r) => r.item.section));
      expect(sections.has('nav.sections.reports')).toBe(true);
      expect(sections.has('nav.sections.wallet')).toBe(false);
      expect(s.rows.length).toBeLessThan(all);
      expect(localStorage.getItem(NAV_AREA_STATE_KEY)).toBe('reports');
    });

    it('offers only the areas the user may open and falls back to everything for one they may not', () => {
      localStorage.setItem(NAV_AREA_STATE_KEY, 'reports');
      const { s } = make({ allowed: () => false });

      expect(s.areas.map((a) => a.key)).not.toContain('reports');
      expect(s.activeArea).toBe('all');
    });

    it('a search looks through every area', () => {
      const { s } = make();
      s.setArea('reports');
      s.searchQuery = 'nav.sections.wallet';

      expect(s.rows.some((r) => r.item.section === 'nav.sections.wallet')).toBe(true);
    });

    it('opening a page of another area switches to that area, and "all" stays "all"', () => {
      const { s, events } = make();
      s.ngOnInit();
      s.setArea('reports');

      navigate(events, '/wallet/home');
      expect(s.activeArea).toBe('wallet');

      s.setArea('all');
      navigate(events, '/wallet/agent');
      expect(s.activeArea).toBe('all');
    });

    it('records recently opened pages, newest first, and remembers them', () => {
      const { s, events } = make();
      s.ngOnInit();

      navigate(events, '/wallet/home');
      navigate(events, '/wallet/agent');

      expect(s.recentItems.map((i) => i.path)).toEqual(['/wallet/home', '/profile']);
      expect(JSON.parse(localStorage.getItem(NAV_RECENTS_STATE_KEY)!)).toEqual(['/wallet/agent', '/wallet/home', '/profile']);
    });

    it('stars and unstars a page without opening it, keeps starred pages out of the recents and stops at the limit', () => {
      const { s, events } = make();
      s.ngOnInit();
      navigate(events, '/wallet/home');
      const event = { preventDefault: vi.fn(), stopPropagation: vi.fn() } as unknown as Event;

      s.toggleFavourite('/wallet/home', event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(event.stopPropagation).toHaveBeenCalled();
      expect(s.isFavourite('/wallet/home')).toBe(true);
      expect(s.favouriteItems.map((i) => i.path)).toEqual(['/wallet/home']);
      expect(s.recentItems.map((i) => i.path)).not.toContain('/wallet/home');

      s.toggleFavourite('/wallet/home', event);
      expect(s.isFavourite('/wallet/home')).toBe(false);

      const paths = NAV_ITEMS.filter((i) => !i.hidden).map((i) => i.path).slice(0, NAV_FAVOURITES_MAX + 2);
      for (const path of paths) s.toggleFavourite(path, event);
      expect(s.favouritesFull).toBe(true);
      expect(s.favouriteItems.length).toBe(NAV_FAVOURITES_MAX);
    });

    it('drops starred and recent pages the user may no longer open and ignores corrupt storage', () => {
      localStorage.setItem(NAV_FAVOURITES_STATE_KEY, JSON.stringify(['/wallet/home']));
      localStorage.setItem(NAV_RECENTS_STATE_KEY, 'not json');
      const { s } = make({ allowed: () => false });

      expect(s.favouriteItems).toEqual([]);
      expect(s.recentItems).toEqual([]);
    });
  });

  describe('applications the organization is onboarded to', () => {
    const areaKeys = (s: Sidebar) => s.areas.map((a) => a.key);

    it('lists only subscribed applications and the core areas, and never Train', () => {
      const { s } = make({ codes: [APP_CODE_BY_SUITE_KEY['wallet']] });

      expect(areaKeys(s)).toEqual(['access', 'agent', 'wallet', 'accounting', 'reports', 'developer', 'qa']);
      expect(s.visibleNavItems.some((i) => i.section.startsWith('nav.sections.train'))).toBe(false);
    });

    it('keeps Train out of the All view and the search', () => {
      const { s } = make();
      s.searchQuery = 'train';
      expect(s.rows.some((r) => r.item.section.startsWith('nav.sections.train'))).toBe(false);
      s.searchQuery = '';
      expect(s.rows.some((r) => r.item.section.startsWith('nav.sections.train'))).toBe(false);
    });

    it('shows a placeholder and only core areas while the lookup loads', () => {
      const { s } = make({ status: 'loading' });

      expect(s.appsLoading).toBe(true);
      expect(areaKeys(s)).toEqual(['access', 'agent', 'wallet', 'accounting', 'reports', 'developer', 'qa']);
    });

    it('fails closed without a placeholder when the lookup failed', () => {
      const { s } = make({ status: 'failed' });

      expect(s.appsLoading).toBe(false);
      expect(areaKeys(s)).toEqual(['access', 'agent', 'wallet', 'accounting', 'reports', 'developer', 'qa']);
    });

    it('does not list favourites or recents of a hidden application', () => {
      localStorage.setItem(NAV_FAVOURITES_STATE_KEY, JSON.stringify(['/matrimony/platform-config', '/reports/home']));
      localStorage.setItem(NAV_RECENTS_STATE_KEY, JSON.stringify(['/matrimony/dashboard']));
      const { s } = make({ codes: [] });

      expect(s.favouriteItems.map((i) => i.path)).not.toContain('/matrimony/platform-config');
      expect(s.recentItems).toEqual([]);
    });

    it('falls back to All when the stored area is hidden', () => {
      localStorage.setItem(NAV_AREA_STATE_KEY, 'train');
      const { s } = make();

      expect(s.activeArea).toBe('all');
    });

    it('asks for the subscriptions on start and after each navigation (quiet retry)', () => {
      const { s, events, subscriptions } = make();
      s.ngOnInit();
      events.next(new NavigationEnd(1, '/profile', '/profile'));

      expect(subscriptions.ensureLoaded).toHaveBeenCalledTimes(2);
    });
  });
});
