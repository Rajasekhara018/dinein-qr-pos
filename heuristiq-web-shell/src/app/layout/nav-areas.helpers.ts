import { NAV_ALWAYS_VISIBLE_SECTIONS, NAV_AREA_ALL, NAV_AREAS, NavArea, WORKSPACE_SECTION_APPS } from './nav-areas.constants';
import type { NavItem, NavSection } from './nav-items.constants';

/** The area a section belongs to, if any. */
export function areaOfSection(section: NavSection): NavArea | undefined {
  return NAV_AREAS.find((area) => area.sections.includes(section));
}

/**
 * The application (area key) whose subscription a section follows: for a persona-workspace section the application it was taken
 * from, otherwise the area that lists it; undefined for a section outside every area.
 */
export function appOfSection(section: NavSection): string | undefined {
  return WORKSPACE_SECTION_APPS[section] ?? areaOfSection(section)?.key;
}

/**
 * The areas that have at least one of the given (already permitted) items, in menu order. An application also counts as
 * present when the user has a persona-workspace page taken from it (WORKSPACE_SECTION_APPS): a matrimony franchise agent sees
 * Pasupu Kumkuma Matrimony in the switcher, with their agent desk in it, as well as the Agent workspace.
 */
export function availableAreas(items: ReadonlyArray<Pick<NavItem, 'section'>>): NavArea[] {
  const present = new Set(items.map((item) => item.section));
  const viaWorkspaces = new Set([...present].map((section) => WORKSPACE_SECTION_APPS[section]).filter((key): key is string => !!key));
  return NAV_AREAS.filter((area) => area.sections.some((section) => present.has(section)) || viaWorkspaces.has(area.key));
}

/** The area to use: the wanted one when the user has it, otherwise everything. */
export function effectiveArea(wanted: string, available: readonly NavArea[]): string {
  return available.some((area) => area.key === wanted) ? wanted : NAV_AREA_ALL;
}

/** Whether an item shows when the given area is chosen. */
export function inArea(item: NavItem, area: string): boolean {
  if (area === NAV_AREA_ALL) return true;
  const own = areaOfSection(item.section);
  // A persona-workspace page also shows under the application it was taken from.
  return !own || own.key === area || WORKSPACE_SECTION_APPS[item.section] === area || NAV_ALWAYS_VISIBLE_SECTIONS.includes(item.section);
}

/** The stored list of paths; anything that is not a list of text (or is unreadable) is an empty list. */
export function parseStoredPaths(raw: string | null): string[] {
  if (!raw) return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string') : [];
  } catch {
    return [];
  }
}

/** The path moved to the front of the recents, once, and the list cut to `max`. */
export function pushRecent(recents: readonly string[], path: string, max: number): string[] {
  return [path, ...recents.filter((p) => p !== path)].slice(0, max);
}

/** The path added to the favourites, or removed when it is there; adding stops at `max` (the list is unchanged then). */
export function toggleFavourite(favourites: readonly string[], path: string, max: number): string[] {
  if (favourites.includes(path)) return favourites.filter((p) => p !== path);
  return favourites.length >= max ? [...favourites] : [...favourites, path];
}
