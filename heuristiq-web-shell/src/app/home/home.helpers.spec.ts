import { describe, expect, it } from 'vitest';
import { NAV_AREAS } from '../layout/nav-areas.constants';
import { NAV_ITEMS, NavItem } from '../layout/nav-items.constants';
import { APP_CODE_BY_SUITE_KEY, APP_FEATURES, HUB_EXTRA_APPS, APP_LANDING_PATHS, CORE_SUITE_APP_KEYS, APP_PREVIEW_COUNT, PLATFORM_CAPABILITIES } from './home.constants';
import { hubSuiteApps, appKind, appsOfKind, buildSuiteApps, visibleFeatures, filterSuiteApps, resolveCapabilities, suiteStats, visibleSuiteApps, isAppSubscribed } from './home.helpers';

const everything = () => true;
const nothing = (permission: string | null) => !permission;
const only = (...allowed: string[]) => (permission: string | null) => !permission || allowed.includes(permission);

const findApp = (apps: ReturnType<typeof buildSuiteApps>, key: string) => apps.find((app) => app.key === key)!;

describe('home.helpers', () => {
  describe('buildSuiteApps', () => {
    it('makes one application per sidebar area, in menu order', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything);

      expect(apps.map((app) => app.key)).toEqual(NAV_AREAS.map((area) => area.key));
      expect(apps.every((app) => app.taglineKey === `home.apps.tagline.${app.key}`)).toBe(true);
    });

    it('counts only the screens in the menu: hidden ones never appear', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything);

      const listed = new Set(apps.flatMap((app) => app.pages.map((page) => page.path)));
      for (const hidden of NAV_ITEMS.filter((item) => item.hidden)) {
        expect(listed.has(hidden.path), hidden.path).toBe(false);
      }
      expect(apps.every((app) => app.pages.length === app.totalPages)).toBe(true);
    });

    it('lists only the screens the user may open, and keeps the full count for "x of y"', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, only('view_wallet_journals'));

      const wallet = findApp(apps, 'wallet');
      expect(wallet.pages.map((page) => page.path)).toEqual(['/wallet/admin/journals']);
      expect(wallet.totalPages).toBeGreaterThan(1);
      expect(wallet.enabled).toBe(true);
    });

    it('marks an application with no allowed screen as not enabled, with nowhere to open', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, nothing);

      const matrimony = findApp(apps, 'matrimony');
      expect(matrimony.enabled).toBe(false);
      expect(matrimony.openPath).toBeNull();
      expect(matrimony.preview).toEqual([]);
      expect(matrimony.totalPages).toBeGreaterThan(0);
    });

    it('opens the preferred landing screen when the user may open it', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything);

      expect(findApp(apps, 'matrimony').openPath).toBe('/matrimony/dashboard');
      expect(findApp(apps, 'wallet').openPath).toBe('/wallet/home');
      expect(findApp(apps, 'admin').openPath).toBe('/admin/dashboard');
    });

    it('falls back to the first allowed screen when the preferred landing screen is not allowed', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, only('view_matrimony_payments'));

      expect(findApp(apps, 'matrimony').openPath).toBe('/matrimony/payments');
    });

    it('previews the first few screens and says how many more there are', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything);

      const admin = findApp(apps, 'admin');
      expect(admin.preview).toHaveLength(APP_PREVIEW_COUNT);
      expect(admin.more).toBe(admin.pages.length - APP_PREVIEW_COUNT);
    });

    it('only names landing screens that are real menu entries of the same application', () => {
      for (const area of NAV_AREAS) {
        for (const path of APP_LANDING_PATHS[area.key] ?? []) {
          const item = NAV_ITEMS.find((candidate) => candidate.path === path);
          expect(item, `${area.key}: ${path}`).toBeDefined();
          expect(area.sections, `${area.key}: ${path}`).toContain(item!.section);
        }
      }
      expect(Object.keys(APP_LANDING_PATHS).sort()).toEqual(NAV_AREAS.map((area) => area.key).sort());
    });
  });

  describe('suiteStats', () => {
    it('counts applications, available and locked ones, and openable screens', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, nothing);
      const stats = suiteStats(apps);

      expect(stats.apps).toBe(NAV_AREAS.length);
      expect(stats.available + stats.locked).toBe(stats.apps);
      expect(stats.screens).toBe(apps.reduce((sum, app) => sum + app.pages.length, 0));
    });

    it('says everything is available when the user may open everything', () => {
      const stats = suiteStats(buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything));

      expect(stats.locked).toBe(0);
      expect(stats.available).toBe(NAV_AREAS.length);
    });
  });

  describe('filterSuiteApps', () => {
    const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, only('view_wallet_journals'));
    const say = (key: string) => key;

    it('keeps everything for the "all" chip and an empty search', () => {
      expect(filterSuiteApps(apps, 'all', '  ', say)).toHaveLength(apps.length);
    });

    it('splits the list into available and not enabled applications', () => {
      const available = filterSuiteApps(apps, 'available', '', say);
      const locked = filterSuiteApps(apps, 'locked', '', say);

      expect(available.every((app) => app.enabled)).toBe(true);
      expect(locked.every((app) => !app.enabled)).toBe(true);
      expect(available.length + locked.length).toBe(apps.length);
    });

    it('matches the search against the name, the description and the screen names, ignoring case', () => {
      const text = (key: string) => ({ 'nav.areas.wallet': 'Wallet', 'home.apps.tagline.admin': 'Railway bookings' } as Record<string, string>)[key] ?? key;

      expect(filterSuiteApps(apps, 'all', 'WALLET', text).map((app) => app.key)).toContain('wallet');
      expect(filterSuiteApps(apps, 'all', 'railway', text).map((app) => app.key)).toEqual(['admin']);
      expect(filterSuiteApps(apps, 'all', 'nav.items.walletJournals', text).map((app) => app.key)).toContain('wallet');
      expect(filterSuiteApps(apps, 'all', 'zzz-nothing', text)).toEqual([]);
    });

    it('combines the chip and the search', () => {
      const result = filterSuiteApps(apps, 'locked', 'wallet', say);

      expect(result.every((app) => !app.enabled)).toBe(true);
      expect(result.map((app) => app.key)).not.toContain('wallet');
    });
  });

  describe('resolveCapabilities', () => {
    it('points every platform capability at a screen that is in the menu', () => {
      for (const capability of PLATFORM_CAPABILITIES) {
        const item: NavItem | undefined = NAV_ITEMS.find((candidate) => candidate.path === capability.path);
        expect(item, capability.path).toBeDefined();
        expect(item!.hidden, capability.path).toBeFalsy();
      }
    });

    it('opens a tile only when the user may open the screen behind it', () => {
      const tiles = resolveCapabilities(PLATFORM_CAPABILITIES, NAV_ITEMS, only('view_roles'));

      expect(tiles.find((tile) => tile.key === 'access')?.open).toBe(true);
      expect(tiles.find((tile) => tile.key === 'audit')?.open).toBe(false);
      // Workflow and the service registry are staff tools now: closed without their permission, open with it.
      expect(tiles.find((tile) => tile.key === 'workflow')?.open).toBe(false);
      expect(tiles.find((tile) => tile.key === 'integration')?.open).toBe(false);
      const staff = resolveCapabilities(PLATFORM_CAPABILITIES, NAV_ITEMS, (permission) => permission === null
        || permission === 'view_workflow_tasks' || permission === 'view_service_registry');
      expect(staff.find((tile) => tile.key === 'workflow')?.open).toBe(true);
      expect(staff.find((tile) => tile.key === 'integration')?.open).toBe(true);
    });

    it('keeps the tile, closed, for a user who may open nothing that needs a permission', () => {
      const tiles = resolveCapabilities(PLATFORM_CAPABILITIES, NAV_ITEMS, nothing);

      expect(tiles).toHaveLength(PLATFORM_CAPABILITIES.length);
      expect(tiles.find((tile) => tile.key === 'approvals')?.open).toBe(false);
    });
  });
});

describe('visibleSuiteApps', () => {
  const all = () => buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything);
  const keys = (apps: { key: string }[]) => apps.map((app) => app.key);
  const MATRI = APP_CODE_BY_SUITE_KEY['matrimony'];
  const WALLET = APP_CODE_BY_SUITE_KEY['wallet'];
  const CORE = NAV_AREAS.map((a) => a.key).filter((key) => CORE_SUITE_APP_KEYS.includes(key));

  it('shows an application that is subscribed and whose screens the user may open', () => {
    expect(keys(visibleSuiteApps(all(), [MATRI], []))).toContain('matrimony');
  });

  it('hides an application the organization is not subscribed to, even when the user may open its screens or is granted it', () => {
    const visible = keys(visibleSuiteApps(all(), [WALLET], [MATRI]));

    expect(visible).toContain('wallet');
    expect(visible).not.toContain('matrimony');
  });

  it('hides a subscribed application the user can neither open nor is granted', () => {
    const none = buildSuiteApps(NAV_ITEMS, NAV_AREAS, nothing);

    expect(keys(visibleSuiteApps(none, [MATRI], []))).not.toContain('matrimony');
  });

  it('keeps a subscribed application the user is granted although no screen opens (shown as locked)', () => {
    const none = buildSuiteApps(NAV_ITEMS, NAV_AREAS, nothing);

    const visible = visibleSuiteApps(none, [MATRI], [MATRI.toLowerCase()]);
    expect(keys(visible)).toContain('matrimony');
    expect(visible.find((app) => app.key === 'matrimony')!.enabled).toBe(false);
  });

  it('matches codes whatever their case', () => {
    expect(keys(visibleSuiteApps(all(), [MATRI.toLowerCase()], []))).toContain('matrimony');
  });

  it('shows every subscribed application to a user who can open them all (a super administrator holds every privilege)', () => {
    const visible = keys(visibleSuiteApps(all(), Object.values(APP_CODE_BY_SUITE_KEY), []));

    for (const key of Object.keys(APP_CODE_BY_SUITE_KEY)) expect(visible).toContain(key);
  });

  it('shows platform tooling areas without a subscription, but only when a screen opens', () => {
    expect(keys(visibleSuiteApps(all(), [], []))).toEqual(CORE);
    const open = buildSuiteApps(NAV_ITEMS, NAV_AREAS, nothing).filter((app) => CORE.includes(app.key) && app.enabled);
    expect(keys(visibleSuiteApps(buildSuiteApps(NAV_ITEMS, NAV_AREAS, nothing), [], []))).toEqual(keys(open));
    expect(visibleSuiteApps([{ ...all().find((app) => app.key === 'reports')!, enabled: false }], [], [])).toEqual([]);
  });

  it('shows nothing from the catalogue when there are no subscriptions at all (empty or failed lookup)', () => {
    const visible = keys(visibleSuiteApps(all(), [], Object.values(APP_CODE_BY_SUITE_KEY)));

    for (const key of Object.keys(APP_CODE_BY_SUITE_KEY).filter((k) => !CORE_SUITE_APP_KEYS.includes(k))) expect(visible).not.toContain(key);
  });

  it('hides an application that has no catalogue mapping and is not listed as core', () => {
    const unknown = { ...all()[0], key: 'mystery' };

    expect(visibleSuiteApps([unknown], Object.values(APP_CODE_BY_SUITE_KEY), Object.values(APP_CODE_BY_SUITE_KEY))).toEqual([]);
  });

  it('counts only the visible applications in the stats', () => {
    const visible = visibleSuiteApps(all(), [MATRI], []);

    expect(suiteStats(visible).apps).toBe(visible.length);
    expect(visible.length).toBeLessThan(NAV_AREAS.length);
  });

  it('maps every hub area to a catalogue code or lists it as core; Wallet is deliberately both (Train is the one deliberately hidden area)', () => {
    for (const area of NAV_AREAS.filter((a) => a.key !== 'train' && a.key !== 'wallet')) {
      const mapped = !!APP_CODE_BY_SUITE_KEY[area.key];
      const core = CORE_SUITE_APP_KEYS.includes(area.key);
      expect(mapped !== core, area.key).toBe(true);
    }
    expect(CORE_SUITE_APP_KEYS).toContain('wallet');
    expect(APP_CODE_BY_SUITE_KEY['wallet']).toBeDefined();
  });
});

describe('Train and isAppSubscribed', () => {
  it('never shows Train, even when the user may open every screen and every catalogue application is subscribed', () => {
    const all = buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything);
    const visible = visibleSuiteApps(all, Object.values(APP_CODE_BY_SUITE_KEY), Object.values(APP_CODE_BY_SUITE_KEY));

    expect(visible.map((app) => app.key)).not.toContain('train');
    expect(APP_CODE_BY_SUITE_KEY['train']).toBeUndefined();
    expect(hubSuiteApps(all, Object.values(APP_CODE_BY_SUITE_KEY), []).map((app) => app.key)).toContain('train');
  });

  it('applies one rule: core areas always, others only with their catalogue code (any case), unmapped never', () => {
    expect(isAppSubscribed('access', [])).toBe(true);
    expect(isAppSubscribed('wallet', [])).toBe(true);
    expect(isAppSubscribed('matrimony', [])).toBe(false);
    expect(isAppSubscribed('matrimony', [APP_CODE_BY_SUITE_KEY['matrimony'].toLowerCase()])).toBe(true);
    expect(isAppSubscribed('train', Object.values(APP_CODE_BY_SUITE_KEY))).toBe(false);
  });

  describe('products and shared applications', () => {
    it('treats the catalogue applications as products and everything else as shared', () => {
      expect(['admin', 'matrimony', 'wallet', 'food', 'train', 'franchise', 'ims'].map(appKind)).toEqual(Array(7).fill('product'));
      expect(['reports', 'access', 'developer', 'qa', 'communications', 'documents', 'support', 'payments'].map(appKind)).toEqual(Array(8).fill('shared'));
    });

    it('splits the applications by kind, keeping menu order', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything, HUB_EXTRA_APPS);

      expect(appsOfKind(apps, 'product').map((a) => a.key)).toEqual(['franchise', 'wallet', 'matrimony', 'admin', 'food', 'ims', 'train']);
      expect(appsOfKind(apps, 'shared').map((a) => a.key)).toEqual(['access', 'owner', 'agent', 'accounting', 'reports', 'developer', 'qa', 'communications', 'documents', 'support', 'payments']);
    });

    it('shows only the features whose privileges the user holds', () => {
      const can = (codes: ReadonlyArray<string>) => codes.includes('WALLET_SELF_READ');

      expect(visibleFeatures('wallet', can).map((f) => f.key)).toEqual(['myWallet']);
      expect(visibleFeatures('admin', can)).toEqual([]);
      expect(visibleFeatures('unknown', () => true)).toEqual([]);
    });

    it('describes every application with at least three features', () => {
      for (const key of Object.keys(APP_FEATURES)) {
        expect(APP_FEATURES[key].length, key).toBeGreaterThanOrEqual(3);
      }
    });

    it('moves the screens an extra application claims out of the area that listed them', () => {
      const withExtras = buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything, HUB_EXTRA_APPS);
      const without = buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything);
      const communications = findApp(withExtras, 'communications');

      expect(communications.totalPages).toBeGreaterThan(0);
      expect(communications.pages.every((page) => page.path.startsWith('/nms-templates'))).toBe(true);
      expect(findApp(withExtras, 'access').totalPages).toBe(findApp(without, 'access').totalPages - communications.totalPages - findApp(withExtras, 'documents').totalPages);
    });

    it('lists a matrimony agent desk under Pasupu Kumkuma Matrimony as well, so an agent finds the application', () => {
      const agentOnly = (permission: string | null) => permission === null || permission.startsWith('view_matrimony_agent');
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, agentOnly, HUB_EXTRA_APPS);
      const matrimony = findApp(apps, 'matrimony');

      expect(matrimony.enabled).toBe(true);
      expect(matrimony.pages.map((p) => p.path)).toContain('/matrimony/agent');
      expect(matrimony.pages.every((p) => p.path.startsWith('/matrimony/agent'))).toBe(true);
    });

    it('carries the logo of each application that has one, and none for those that keep their icon', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything, HUB_EXTRA_APPS);

      expect(findApp(apps, 'admin').logo).toBe('assets/apps/bestride.svg');
      expect(findApp(apps, 'matrimony').logo).toBe('assets/apps/pasupu-kumkuma.svg');
      expect(findApp(apps, 'access').logo).toBeUndefined();
      expect(findApp(apps, 'communications').logo).toBeUndefined();
      expect(findApp(apps, 'access').icon).toBe('bi-people');
    });

    it('builds Franchise from its own sidebar area, not from the extra cards, and opens it on the franchise list', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, everything, HUB_EXTRA_APPS);
      const franchise = findApp(apps, 'franchise');
      const area = NAV_AREAS.find((a) => a.key === 'franchise')!;
      // The franchise application's own sections, plus the franchise member's pages taken from it (My franchises, earnings, payouts).
      const own = NAV_ITEMS.filter((item) => !item.hidden && (area.sections.includes(item.section) || item.section === 'nav.sections.ownerFranchise'));

      expect(HUB_EXTRA_APPS.map((extra) => extra.key)).not.toContain('franchise');
      expect(franchise.labelKey).toBe('nav.areas.franchise');
      expect(franchise.totalPages).toBe(own.length);
      expect(franchise.openPath).toBe('/access-management/franchises');
      expect(findApp(apps, 'access').pages.some((page) => /franchise|my-earnings|my-payouts/.test(page.path))).toBe(false);
      expect(isAppSubscribed('franchise', [])).toBe(false);
      expect(isAppSubscribed('franchise', ['FRANCHISE_MANAGEMENT_APP'])).toBe(true);
    });

    it('shows a hub-only application with no screens when the user holds one of its feature privileges', () => {
      const apps = buildSuiteApps(NAV_ITEMS, NAV_AREAS, nothing, HUB_EXTRA_APPS);
      const keys = (has: (key: string) => boolean) => hubSuiteApps(apps, [], [], has).map((app) => app.key);

      expect(keys(() => false)).not.toContain('ims');
      const shown = hubSuiteApps(apps, [], [], (key) => key === 'ims');
      expect(shown.find((app) => app.key === 'ims')?.enabled).toBe(true);
    });
  });
});
