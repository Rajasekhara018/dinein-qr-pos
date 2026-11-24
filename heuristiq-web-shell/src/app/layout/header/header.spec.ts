import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BrandingService } from '../../shared/services/branding-service';
import { LanguageService } from '../../shared/services/language-service';
import { SessionPreferencesService } from '../../shared/services/session-preferences.service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { ProfilePhotoService } from '../../shared/services/profile-photo-service';
import { InAppNotification } from '../../shared/models/models';
import { signal } from '@angular/core';
import { HomeApplicationsService } from '../../home/home-applications.service';
import { APP_CODE_BY_SUITE_KEY } from '../../home/home.constants';
import { HomeLoadStatus } from '../../home/home.models';
import { Header } from './header';

const note = (over: Partial<InAppNotification> = {}): InAppNotification =>
  ({ id: 'n1', adminUserId: 'u', title: 't', message: 'm', type: 'SYSTEM_ALERT', read: false, createdAt: new Date().toISOString(), ...over }) as InAppNotification;

function make(opts: { authenticated?: boolean; user?: object | null; allowed?: (p: string) => boolean; status?: HomeLoadStatus; codes?: string[] } = {}) {
  const user$ = new BehaviorSubject<object | null>(opts.user === undefined ? { id: 'u1', name: 'asha', email: 'a@x', role: 'SUPER_ADMIN' } : opts.user);
  const auth = {
    user$,
    get currentUser() { return user$.value as { id?: string; name?: string; email?: string; role?: string } | null; },
    isAuthenticated: vi.fn(() => opts.authenticated ?? true),
    canAccess: vi.fn((p: string) => (opts.allowed ? opts.allowed(p) : true)),
    logout: vi.fn(),
  };
  const inApp = {
    notifications$: new BehaviorSubject<InAppNotification[]>([]),
    unreadCount$: new BehaviorSubject<number>(0),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
  };
  const theme = { setTheme: vi.fn() };
  const router = { navigate: vi.fn().mockResolvedValue(true), navigateByUrl: vi.fn().mockResolvedValue(true) };
  const host = document.createElement('div');
  const inside = document.createElement('span');
  host.appendChild(inside);
  const fcm = { setup: vi.fn(), cleanup: vi.fn() };
  const location = { captureOnce: vi.fn().mockResolvedValue(undefined) };
  const dialog = { confirm: vi.fn().mockReturnValue(of(true)) };
  const orgCtx = { load: vi.fn().mockReturnValue(of([])), switchTo: vi.fn() };
  const lang = { setLanguage: vi.fn() };
  const sessionPrefs = { saveTheme: vi.fn(), saveLanguage: vi.fn() };
  const photo = { load: vi.fn(), photoUrl$: of(null) };
  const subscriptions = {
    status: signal<HomeLoadStatus>(opts.status ?? 'ready'),
    subscribedCodes: signal<string[]>(opts.codes ?? Object.values(APP_CODE_BY_SUITE_KEY)),
    ensureLoaded: vi.fn(),
  };
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: BrandingService, useValue: { branding: { appName: 'Acme' } } },
      { provide: LanguageService, useValue: lang },
      { provide: SessionPreferencesService, useValue: sessionPrefs },
      { provide: TranslateService, useValue: { instant: (k: string, p?: object) => (p ? `${k}|${JSON.stringify(p)}` : k) } },
      { provide: OrganizationContextService, useValue: orgCtx },
      { provide: ProfilePhotoService, useValue: photo },
      { provide: HomeApplicationsService, useValue: subscriptions },
    ],
  });
  const h = TestBed.runInInjectionContext(() =>
    new Header(auth as never, inApp as never, theme as never, router as never, { nativeElement: host } as never, fcm as never, location as never, dialog as never));
  return { h, subscriptions, auth, inApp, theme, router, host, inside, fcm, location, dialog, orgCtx, lang, photo, user$, sessionPrefs };
}

describe('Header', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  describe('init', () => {
    it('loads organizations for a signed-in user and the profile photo', () => {
      const { h, orgCtx, photo } = make();
      h.ngOnInit();
      expect(orgCtx.load).toHaveBeenCalled();
      expect(photo.load).toHaveBeenCalledWith('u1');
      expect(h.title).toBe('Acme');
    });

    it('does not load organizations when not signed in, and tolerates a failure', () => {
      const anon = make({ authenticated: false });
      anon.h.ngOnInit();
      expect(anon.orgCtx.load).not.toHaveBeenCalled();
      const failing = make();
      failing.orgCtx.load.mockReturnValue(throwError(() => new Error('x')));
      expect(() => failing.h.ngOnInit()).not.toThrow();
    });

    it('keeps at most 8 notifications and the unread count in step with the service', () => {
      const { h, inApp } = make();
      h.ngOnInit();
      inApp.notifications$.next(Array.from({ length: 12 }, (_, i) => note({ id: `n${i}` })));
      inApp.unreadCount$.next(4);
      expect(h.notifications).toHaveLength(8);
      expect(h.unreadCount).toBe(4);
    });

    it('sets up push and device location for a user, and cleans up push on sign-out', () => {
      const { h, fcm, location, user$ } = make();
      h.ngOnInit();
      expect(fcm.setup).toHaveBeenCalled();
      expect(location.captureOnce).toHaveBeenCalledWith('u1');
      user$.next(null);
      expect(fcm.cleanup).toHaveBeenCalled();
    });

    it('stops listening on destroy', () => {
      const { h, inApp } = make();
      h.ngOnInit();
      h.ngOnDestroy();
      inApp.unreadCount$.next(9);
      expect(h.unreadCount).toBe(0);
    });
  });

  describe('user block', () => {
    it('derives initial, name, email and role with fallbacks', () => {
      const { h, user$ } = make();
      expect(h.userInitial).toBe('A');
      expect(h.userName).toBe('asha');
      expect(h.userEmail).toBe('a@x');
      expect(h.userRole).toBe('SUPER ADMIN');
      user$.next({ role: 'staff' });
      expect(h.userInitial).toBe('S');
      expect(h.userName).toBe('');
      user$.next(null);
      expect(h.userInitial).toBe('R');
      expect(h.userRole).toBe('');
      expect(h.userEmail).toBe('');
    });

    it('formats the unread badge as 99+ above 99', () => {
      const { h } = make();
      h.unreadCount = 5;
      expect(h.badgeLabel).toBe('5');
      h.unreadCount = 100;
      expect(h.badgeLabel).toBe('99+');
    });
  });

  describe('quick-navigate search', () => {
    it('Ctrl+K or Cmd+K focuses and selects the quick-navigate box, other keys do nothing', () => {
      const { h } = make();
      const input = { focus: vi.fn(), select: vi.fn() };
      (h as unknown as { quickSearch: unknown }).quickSearch = { nativeElement: input };
      const press = (init: Partial<KeyboardEvent>) => ({ preventDefault: vi.fn(), key: 'k', ctrlKey: false, metaKey: false, ...init }) as unknown as KeyboardEvent;

      const ctrl = press({ ctrlKey: true });
      h.onShortcut(ctrl);
      expect(ctrl.preventDefault).toHaveBeenCalled();
      expect(input.focus).toHaveBeenCalledTimes(1);
      expect(input.select).toHaveBeenCalledTimes(1);

      h.onShortcut(press({ metaKey: true, key: 'K' }));
      expect(input.focus).toHaveBeenCalledTimes(2);

      h.onShortcut(press({ key: 'k' }));
      h.onShortcut(press({ ctrlKey: true, key: 'j' }));
      expect(input.focus).toHaveBeenCalledTimes(2);
    });

    it('the app launcher lists the areas the user may open, closes the other menus, and opens an area on its first permitted page', () => {
      const { h, router } = make();
      localStorage.removeItem('sidebar.area');
      expect(h.launcherAreas.map((a) => a.key)).toContain('wallet');
      h.avatarMenuOpen = h.themeMenuOpen = true;

      h.toggleLauncher();
      expect(h.launcherOpen).toBe(true);
      expect([h.avatarMenuOpen, h.themeMenuOpen]).toEqual([false, false]);

      const wallet = h.launcherAreas.find((a) => a.key === 'wallet')!;
      h.openArea(wallet);

      expect(h.launcherOpen).toBe(false);
      expect(localStorage.getItem('sidebar.area')).toBe('wallet');
      expect(router.navigateByUrl).toHaveBeenCalledWith(expect.stringMatching(/^\/wallet/));
    });

    it('the launcher arrows scroll the strip of areas smoothly by most of its width, and do nothing before it exists', () => {
      const { h } = make();
      h.scrollLauncher(1);

      const scrollBy = vi.fn();
      const strip = Object.assign(document.createElement('div'), { scrollBy });
      Object.defineProperty(strip, 'clientWidth', { value: 300 });
      (h as unknown as { launcherStrip: unknown }).launcherStrip = { nativeElement: strip };

      h.scrollLauncher(1);
      h.scrollLauncher(-1);

      expect(scrollBy).toHaveBeenNthCalledWith(1, { left: 240, behavior: 'smooth' });
      expect(scrollBy).toHaveBeenNthCalledWith(2, { left: -240, behavior: 'smooth' });
    });

    it('the app launcher only offers areas with a page the user may open', () => {
      const { h } = make({ allowed: () => false });
      expect(h.launcherAreas.map((a) => a.key)).not.toContain('wallet');
    });

    it('suggests the first six menu items the user may open when the box is empty', () => {
      const { h } = make();
      expect(h.searchSuggestions).toHaveLength(6);
    });

    it('filters by translated label or section, case-insensitively', () => {
      const { h } = make();
      h.searchQuery = ' MYPROFILE ';
      const found = h.searchSuggestions;
      expect(found.map((s) => s.path)).toContain('/profile');
      expect(found.every((s) => s.label.toLowerCase().includes('myprofile') || s.section.toLowerCase().includes('myprofile'))).toBe(true);
      h.searchQuery = 'zzzzzzz';
      expect(h.searchSuggestions).toEqual([]);
    });

    it('leaves out menu items the user may not open, but keeps the extra shortcuts', () => {
      const { h } = make({ allowed: () => false });
      h.searchQuery = 'nav.items';
      const paths = h.searchSuggestions.map((s) => s.path);
      expect(paths).toContain('/change-password');
      expect(paths).not.toContain('/admin/change-requests');
    });

    it('opens on focus, closing the other menus, and closes shortly after blur', () => {
      const { h } = make();
      h.dropdownOpen = h.avatarMenuOpen = h.languageMenuOpen = h.themeMenuOpen = true;
      h.onSearchFocus();
      expect(h.searchOpen).toBe(true);
      expect([h.dropdownOpen, h.avatarMenuOpen, h.languageMenuOpen, h.themeMenuOpen]).toEqual([false, false, false, false]);
      h.onSearchBlur();
      expect(h.searchOpen).toBe(true);
      vi.advanceTimersByTime(150);
      expect(h.searchOpen).toBe(false);
    });

    it('moves the highlight with the arrow keys within bounds and opens the highlighted entry on Enter', () => {
      const { h, router } = make();
      const key = (k: string) => ({ key: k, preventDefault: vi.fn() }) as unknown as KeyboardEvent;
      h.searchOpen = true;
      h.onSearchKeydown(key('ArrowDown'));
      h.onSearchKeydown(key('ArrowDown'));
      expect(h.searchFocusIndex).toBe(1);
      for (let i = 0; i < 10; i++) h.onSearchKeydown(key('ArrowDown'));
      expect(h.searchFocusIndex).toBe(5);
      h.onSearchKeydown(key('ArrowUp'));
      expect(h.searchFocusIndex).toBe(4);
      for (let i = 0; i < 10; i++) h.onSearchKeydown(key('ArrowUp'));
      expect(h.searchFocusIndex).toBe(-1);
      h.onSearchKeydown(key('Enter'));
      expect(router.navigate).not.toHaveBeenCalled();
      h.onSearchKeydown(key('ArrowDown'));
      const first = h.searchSuggestions[0];
      h.onSearchKeydown(key('Enter'));
      expect(router.navigate).toHaveBeenCalledWith([first.path]);
      expect(h.searchOpen).toBe(false);
      h.searchOpen = true;
      h.searchQuery = 'x';
      h.onSearchKeydown(key('Escape'));
      expect(h.searchOpen).toBe(false);
      expect(h.searchQuery).toBe('');
    });
  });

  describe('menus', () => {
    it('opening one menu closes the others', () => {
      const { h } = make();
      h.toggleAvatarMenu();
      expect(h.avatarMenuOpen).toBe(true);
      h.toggleLanguageMenu();
      expect([h.avatarMenuOpen, h.languageMenuOpen]).toEqual([false, true]);
      h.toggleThemeMenu();
      expect([h.languageMenuOpen, h.themeMenuOpen]).toEqual([false, true]);
      h.toggleDropdown();
      expect([h.themeMenuOpen, h.dropdownOpen]).toEqual([false, true]);
      h.toggleAvatarMenu();
      expect([h.dropdownOpen, h.avatarMenuOpen]).toEqual([false, true]);
      h.toggleAvatarMenu();
      expect(h.avatarMenuOpen).toBe(false);
      h.toggleLanguageMenu();
      h.toggleLanguageMenu();
      expect(h.languageMenuOpen).toBe(false);
      h.toggleThemeMenu();
      h.toggleThemeMenu();
      h.toggleDropdown();
      h.toggleDropdown();
      expect([h.themeMenuOpen, h.dropdownOpen]).toEqual([false, false]);
    });

    it('selecting a theme or language applies it and closes the menu', () => {
      const { h, theme, lang, sessionPrefs } = make();
      h.themeMenuOpen = true;
      h.selectTheme('ocean' as never);
      expect(theme.setTheme).toHaveBeenCalledWith('ocean');
      expect(sessionPrefs.saveTheme).toHaveBeenCalledWith('ocean');
      expect(h.themeMenuOpen).toBe(false);
      h.languageMenuOpen = true;
      h.selectLanguage('ta');
      expect(lang.setLanguage).toHaveBeenCalledWith('ta');
      expect(sessionPrefs.saveLanguage).toHaveBeenCalledWith('ta');
      expect(h.languageMenuOpen).toBe(false);
    });

    it('a click outside closes the menus, a click inside leaves them', () => {
      const { h, inside } = make();
      h.dropdownOpen = h.avatarMenuOpen = h.languageMenuOpen = true;
      h.onDocumentClick({ target: inside } as unknown as Event);
      expect(h.dropdownOpen).toBe(true);
      h.onDocumentClick({ target: document.body } as unknown as Event);
      expect([h.dropdownOpen, h.avatarMenuOpen, h.languageMenuOpen]).toEqual([false, false, false]);
    });

    it('closes the avatar menu and emits menu clicks', () => {
      const { h } = make();
      const clicked = vi.fn();
      h.menuClick.subscribe(clicked);
      h.avatarMenuOpen = true;
      h.closeAvatarMenu();
      expect(h.avatarMenuOpen).toBe(false);
      h.onMenuClick();
      expect(clicked).toHaveBeenCalled();
    });
  });

  describe('organization switcher', () => {
    const a = { tenantId: 't1', name: 'One', code: 'ONE' } as never;
    const b = { tenantId: 't2', name: 'Two', code: 'TWO' } as never;
    it('switches only to a different organization', () => {
      const { h, orgCtx } = make();
      h.organizationMenuOpen = true;
      h.chooseOrganization(a, a);
      expect(h.organizationMenuOpen).toBe(false);
      expect(orgCtx.switchTo).not.toHaveBeenCalled();
      h.chooseOrganization(b, a);
      expect(orgCtx.switchTo).toHaveBeenCalledWith(b);
      h.chooseOrganization(a, null);
      expect(orgCtx.switchTo).toHaveBeenCalledTimes(2);
      expect(h.trackOrganization(0, b)).toBe('t2');
    });
  });

  describe('notifications', () => {
    it('marks an unread one read, closes the list and follows a booking reference', () => {
      const { h, inApp, router } = make();
      h.dropdownOpen = true;
      h.onNotificationClick(note({ referenceId: 'b9', referenceType: 'booking' } as never));
      expect(inApp.markAsRead).toHaveBeenCalledWith('n1');
      expect(h.dropdownOpen).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/my-bookings', 'b9']);
    });

    it('does not re-mark a read one and goes nowhere without a known reference', () => {
      const { h, inApp, router } = make();
      h.onNotificationClick(note({ read: true }));
      h.onNotificationClick(note({ read: true, referenceId: 'x', referenceType: 'OTHER' } as never));
      expect(inApp.markAsRead).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('marks all read and opens the notifications page', () => {
      const { h, inApp, router } = make();
      h.onMarkAllRead();
      expect(inApp.markAllAsRead).toHaveBeenCalled();
      h.dropdownOpen = true;
      h.viewAll();
      expect(h.dropdownOpen).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/notifications']);
    });

    it('picks an icon and colour by type', () => {
      const { h } = make();
      expect(h.notificationIcon(undefined as never)).toBe('bi-bell-fill');
      expect(h.notificationIcon('BOOKING_CONFIRMED' as never)).toBe('bi-ticket-perforated-fill');
      expect(h.notificationIcon('DRIVER_X' as never)).toBe('bi-person-fill');
      expect(h.notificationIcon('RIDE_X' as never)).toBe('bi-car-front-fill');
      expect(h.notificationIcon('REFUND_REQUESTED' as never)).toBe('bi-arrow-return-left');
      expect(h.notificationIcon('SYSTEM_ALERT' as never)).toBe('bi-bell-fill');
      expect(h.notificationIconColor(undefined as never)).toBe('icon-blue');
      expect(h.notificationIconColor('BOOKING_CONFIRMED' as never)).toBe('icon-green');
      expect(h.notificationIconColor('RIDE_COMPLETED' as never)).toBe('icon-green');
      expect(h.notificationIconColor('BOOKING_CANCELLED' as never)).toBe('icon-red');
      expect(h.notificationIconColor('SYSTEM_ALERT' as never)).toBe('icon-amber');
      expect(h.notificationIconColor('REFUND_REQUESTED' as never)).toBe('icon-amber');
      expect(h.notificationIconColor('OTHER' as never)).toBe('icon-blue');
    });

    it('words the age of a notification', () => {
      const { h } = make();
      const ago = (ms: number) => h.timeAgo(new Date(Date.now() - ms).toISOString());
      expect(ago(10_000)).toBe('header.timeAgo.justNow');
      expect(ago(5 * 60_000)).toBe('header.timeAgo.minutes|{"count":5}');
      expect(ago(3 * 3_600_000)).toBe('header.timeAgo.hours|{"count":3}');
      expect(ago(2 * 86_400_000)).toBe('header.timeAgo.days|{"count":2}');
    });
  });

  describe('logout', () => {
    it('signs out only after confirmation and closes the avatar menu', () => {
      const { h, auth, dialog } = make();
      h.avatarMenuOpen = true;
      dialog.confirm.mockReturnValue(of(false));
      h.logout();
      expect(h.avatarMenuOpen).toBe(false);
      expect(auth.logout).not.toHaveBeenCalled();
      dialog.confirm.mockReturnValue(of(true));
      h.logout();
      expect(auth.logout).toHaveBeenCalled();
    });
  });

  describe('applications the organization is onboarded to', () => {
    const launcherKeys = (h: Header) => h.launcherAreas.map((a) => a.key);

    it('shows only subscribed applications and core areas in the launcher, never Train', () => {
      const { h } = make({ codes: [APP_CODE_BY_SUITE_KEY['matrimony']] });

      // Wallet is a core area (CORE_SUITE_APP_KEYS): it shows without a subscription.
      expect(launcherKeys(h)).toEqual(['access', 'owner', 'agent', 'wallet', 'accounting', 'matrimony', 'reports', 'developer', 'qa']);
    });

    it('keeps hidden applications out of quick navigate', () => {
      const { h } = make({ codes: [] });
      h.searchQuery = '';
      const first = h.searchSuggestions.map((s) => s.path);
      h.searchQuery = 'nav.';
      const all = h.searchSuggestions.map((s) => s.path);

      expect([...first, ...all].some((p) => p.startsWith('/matrimony') || p.includes('train-admin'))).toBe(false);
      expect(all.length).toBeGreaterThan(0);
    });

    it('shows only core areas while loading and when the lookup failed', () => {
      expect(launcherKeys(make({ status: 'loading' }).h)).toEqual(['access', 'agent', 'wallet', 'accounting', 'reports', 'developer', 'qa']);
      expect(launcherKeys(make({ status: 'failed' }).h)).toEqual(['access', 'agent', 'wallet', 'accounting', 'reports', 'developer', 'qa']);
    });

    it('asks for the subscriptions when it starts', () => {
      const { h, subscriptions } = make();
      h.ngOnInit();

      expect(subscriptions.ensureLoaded).toHaveBeenCalled();
    });
  });
});
