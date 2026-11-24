import { NavArea, WORKSPACE_SECTION_APPS } from '../layout/nav-areas.constants';
import type { NavItem } from '../layout/nav-items.constants';
import { AppFeature } from './home.models';
import {
  APP_CODE_BY_SUITE_KEY,
  APP_FEATURES,
  APP_LANDING_PATHS,
  APP_PREVIEW_COUNT,
  AppFilter,
  AppKind,
  CORE_SUITE_APP_KEYS,
  HUB_ONLY_APP_KEYS,
  HubExtraApp,
  PlatformCapability,
  PRODUCT_APP_KEYS,
} from './home.constants';

/** One application of the suite as the hub shows it. */
export interface SuiteApp {
  key: string;
  icon: string;
  /** The application's one-colour logo (assets/apps), shown in the theme colour instead of `icon`; absent: the icon. */
  logo?: string;
  /** i18n key of the application's name (nav.areas.*). */
  labelKey: string;
  /** i18n key of the one-line description (home.apps.tagline.*). */
  taglineKey: string;
  /** The screens of this application that the user may open. */
  pages: NavItem[];
  /** How many screens the application has in all (including those the user may not open). */
  totalPages: number;
  /** Whether the user may open at least one screen. */
  enabled: boolean;
  /** Where "Open" goes; null when the user may open nothing here. */
  openPath: string | null;
  /** The first few screens, shown as chips. */
  preview: NavItem[];
  /** How many more screens there are than the preview shows. */
  more: number;
}

export interface SuiteStats {
  apps: number;
  available: number;
  locked: number;
  screens: number;
}

export interface CapabilityTile extends PlatformCapability {
  /** Whether the user may open the screen the tile points to. */
  open: boolean;
}

type CanOpen = (permission: string | null) => boolean;

/** Menu entries the user can see for real: not hidden, and allowed. */
function shownItems(items: ReadonlyArray<NavItem>): NavItem[] {
  return items.filter((item) => !item.hidden);
}

const startsWithAny = (path: string, prefixes: ReadonlyArray<string>): boolean => prefixes.some((prefix) => path.startsWith(prefix));

/**
 * The applications of the suite (one per sidebar area, in menu order, then the `extras`) with what the user may open in each.
 * A menu entry that an extra application claims by its path is listed under that application, not under its sidebar area.
 */
export function buildSuiteApps(
  items: ReadonlyArray<NavItem>,
  areas: ReadonlyArray<NavArea>,
  canOpen: CanOpen,
  extras: ReadonlyArray<HubExtraApp> = [],
): SuiteApp[] {
  const menu = shownItems(items);
  const claimed = (item: NavItem): boolean => extras.some((extra) => startsWithAny(item.path, extra.pathPrefixes));
  const describe = (key: string, icon: string, labelKey: string, own: NavItem[], logo?: string): SuiteApp => {
    const pages = own.filter((item) => canOpen(item.permission));
    const landing = (APP_LANDING_PATHS[key] ?? []).find((path) => pages.some((page) => page.path === path));
    const preview = pages.slice(0, APP_PREVIEW_COUNT);
    return {
      key,
      icon,
      ...(logo ? { logo } : {}),
      labelKey,
      taglineKey: `home.apps.tagline.${key}`,
      pages,
      totalPages: own.length,
      enabled: pages.length > 0,
      openPath: landing ?? pages[0]?.path ?? null,
      preview,
      more: Math.max(0, pages.length - preview.length),
    };
  };
  const fromAreas = areas.map((area) =>
    // An application's card also lists the persona-workspace pages taken from it (a matrimony agent's desk under Pasupu Kumkuma).
    describe(area.key, area.icon, area.label, menu.filter((item) => (area.sections.includes(item.section)
      || WORKSPACE_SECTION_APPS[item.section] === area.key) && !claimed(item)), area.logo),
  );
  const fromExtras = extras.map((extra) =>
    describe(extra.key, extra.icon, `home.apps.name.${extra.key}`, menu.filter((item) => startsWithAny(item.path, extra.pathPrefixes))),
  );
  return [...fromAreas, ...fromExtras];
}

/**
 * Whether the organization's subscriptions let an application (an area key) show at all: core tooling areas always do, the
 * others only when the catalogue code they belong to is subscribed. An area with no catalogue code (Train) never does.
 * The one subscription rule, shared by the hub, the sidebar, the header and the quick navigate.
 */
export function isAppSubscribed(areaKey: string, subscribedCodes: ReadonlyArray<string>): boolean {
  if (CORE_SUITE_APP_KEYS.includes(areaKey)) return true;
  const code = APP_CODE_BY_SUITE_KEY[areaKey];
  return !!code && subscribedCodes.some((subscribed) => subscribed.toUpperCase() === code);
}

/**
 * The applications the user should see: the organization must be subscribed to the application AND the user must be allowed in
 * (granted the application, or able to open at least one of its screens). Core tooling areas need only a screen. An application
 * with no catalogue mapping that is not a core area is hidden. A display rule only; the gateway still enforces access.
 */
export function visibleSuiteApps(
  apps: ReadonlyArray<SuiteApp>,
  subscribedCodes: ReadonlyArray<string>,
  grantedCodes: ReadonlyArray<string>,
): SuiteApp[] {
  const granted = new Set(grantedCodes.map((code) => code.toUpperCase()));
  return apps.filter((app) => {
    if (!isAppSubscribed(app.key, subscribedCodes)) return false;
    if (CORE_SUITE_APP_KEYS.includes(app.key)) return app.enabled;
    return app.enabled || granted.has(APP_CODE_BY_SUITE_KEY[app.key]);
  });
}

/**
 * The hub's own list: the applications visibleSuiteApps allows, plus the hub-only ones (Train, Franchise, IMS ...) the user can
 * open a screen of or holds a feature privilege of (`hasFeatures`), all in menu order. A hub-only application shown only because
 * of its features counts as enabled. Only the hub uses this; the sidebar, header and quick navigate keep using visibleSuiteApps.
 */
export function hubSuiteApps(
  apps: ReadonlyArray<SuiteApp>,
  subscribedCodes: ReadonlyArray<string>,
  grantedCodes: ReadonlyArray<string>,
  hasFeatures: (appKey: string) => boolean = () => false,
): SuiteApp[] {
  const allowed = new Set(visibleSuiteApps(apps, subscribedCodes, grantedCodes).map((app) => app.key));
  const shown: SuiteApp[] = [];
  for (const app of apps) {
    if (allowed.has(app.key)) {
      shown.push(app);
    } else if (HUB_ONLY_APP_KEYS.includes(app.key) && (app.enabled || hasFeatures(app.key))) {
      shown.push(app.enabled ? app : { ...app, enabled: true });
    }
  }
  return shown;
}

/** The counts shown in the hero strip. */
export function suiteStats(apps: ReadonlyArray<SuiteApp>): SuiteStats {
  const available = apps.filter((app) => app.enabled).length;
  return {
    apps: apps.length,
    available,
    locked: apps.length - available,
    screens: apps.reduce((sum, app) => sum + app.pages.length, 0),
  };
}

/**
 * The applications that match the chosen filter chip and the search text. The text is matched, ignoring case, against the
 * application's name, its description and the names of its screens (`translate` turns an i18n key into the shown text).
 */
export function filterSuiteApps(
  apps: ReadonlyArray<SuiteApp>,
  filter: AppFilter,
  query: string,
  translate: (key: string) => string,
): SuiteApp[] {
  const needle = query.trim().toLowerCase();
  return apps.filter((app) => {
    if (filter === 'available' && !app.enabled) return false;
    if (filter === 'locked' && app.enabled) return false;
    if (!needle) return true;
    const haystack = [translate(app.labelKey), translate(app.taglineKey), ...app.pages.map((page) => translate(page.label))];
    return haystack.some((text) => text.toLowerCase().includes(needle));
  });
}

/** Whether an application is a product or shared tooling. */
export function appKind(appKey: string): AppKind {
  return PRODUCT_APP_KEYS.includes(appKey) ? 'product' : 'shared';
}

/** The applications of one kind, in menu order. */
export function appsOfKind(apps: ReadonlyArray<SuiteApp>, kind: AppKind): SuiteApp[] {
  return apps.filter((app) => appKind(app.key) === kind);
}

/**
 * The features of an application the user may use: those where the user holds at least one of the feature's privileges
 * (`can` is HqPrivilegeAccessService.can). A display rule only; the gateway still enforces every call.
 */
export function visibleFeatures(appKey: string, can: (codes: ReadonlyArray<string>) => boolean): AppFeature[] {
  return (APP_FEATURES[appKey] ?? []).filter((feature) => can(feature.privileges));
}

/** The platform capability tiles, each marked open when the screen it points to is in the menu and the user may open it. */
export function resolveCapabilities(
  capabilities: ReadonlyArray<PlatformCapability>,
  items: ReadonlyArray<NavItem>,
  canOpen: CanOpen,
): CapabilityTile[] {
  const menu = shownItems(items);
  return capabilities.map((capability) => {
    const target = menu.find((item) => item.path === capability.path);
    return { ...capability, open: !!target && canOpen(target.permission) };
  });
}
