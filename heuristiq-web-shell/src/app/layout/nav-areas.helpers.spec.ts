import { NAV_AREA_ALL, NAV_AREAS } from './nav-areas.constants';
import {
  appOfSection, areaOfSection, availableAreas, effectiveArea, inArea, parseStoredPaths, pushRecent, toggleFavourite,
} from './nav-areas.helpers';
import { NAV_ITEMS, NavItem } from './nav-items.constants';

const at = (path: string): NavItem => NAV_ITEMS.find((i) => i.path === path)!;

const item = (section: NavItem['section']): NavItem => ({ path: '/x', icon: 'bi-x', label: 'l', section, permission: null });

describe('nav areas', () => {
  it('puts every section of the menu in one area or in the always-visible main section', () => {
    const unassigned = [...new Set(NAV_ITEMS.map((i) => i.section))].filter((s) => !areaOfSection(s));

    expect(unassigned).toEqual(['nav.sections.main']);
  });

  it('maps each workspace section to the application it comes from, and every other section to its own area', () => {
    expect(appOfSection('nav.sections.ownerFranchise')).toBe('franchise');
    expect(appOfSection('nav.sections.ownerMatrimony')).toBe('matrimony');
    expect(appOfSection('nav.sections.ownerBestRide')).toBe('admin');
    expect(appOfSection('nav.sections.agentMatrimony')).toBe('matrimony');
    expect(appOfSection('nav.sections.agentWallet')).toBe('wallet');
    expect(appOfSection('nav.sections.foodOperations')).toBe('food');
    expect(appOfSection('nav.sections.main')).toBeUndefined();
    // The workspace sections are listed by the workspace areas, so the sidebar shows them there.
    expect(areaOfSection('nav.sections.ownerMatrimony')?.key).toBe('owner');
    expect(areaOfSection('nav.sections.agentWallet')?.key).toBe('agent');
  });

  it('offers the application a workspace page came from as well as the workspace, so a matrimony agent finds Pasupu Kumkuma', () => {
    const agentDesk = NAV_ITEMS.filter((i) => i.section === 'nav.sections.agentMatrimony');
    const keys = availableAreas(agentDesk).map((a) => a.key);

    expect(keys).toEqual(['agent', 'matrimony']);
    // Only the application the page came from: a wallet desk page does not bring Matrimony in.
    expect(availableAreas([item('nav.sections.agentWallet')]).map((a) => a.key)).toEqual(['agent', 'wallet']);
  });

  it('shows a workspace page under its workspace and under the application it came from, and nowhere else', () => {
    const desk = at('/matrimony/agent');
    expect(inArea(desk, 'agent')).toBe(true);
    expect(inArea(desk, 'matrimony')).toBe(true);
    expect(inArea(desk, 'wallet')).toBe(false);
    expect(inArea(at('/access-management/my-earnings'), 'franchise')).toBe(true);
    expect(inArea(at('/access-management/my-drivers'), 'admin')).toBe(true);
    // HQ pages keep showing only in their own application.
    expect(inArea(at('/matrimony/dashboard'), 'agent')).toBe(false);
  });

  it('lists only the areas the user has items in, in menu order', () => {
    const areas = availableAreas([item('nav.sections.reports'), item('nav.sections.main'), item('nav.sections.agentWallet')]);

    // The wallet desk page also brings in Wallet, the application it came from.
    expect(areas.map((a) => a.key)).toEqual(['agent', 'wallet', 'reports']);
  });

  it('falls back to everything when the wanted area is not available', () => {
    const areas = NAV_AREAS.filter((a) => a.key === 'reports');

    expect(effectiveArea('reports', areas)).toBe('reports');
    expect(effectiveArea('wallet', areas)).toBe(NAV_AREA_ALL);
    expect(effectiveArea(NAV_AREA_ALL, areas)).toBe(NAV_AREA_ALL);
  });

  it('shows the main section in every area and the other sections only in their own', () => {
    expect(inArea(item('nav.sections.main'), 'reports')).toBe(true);
    expect(inArea(item('nav.sections.reports'), 'reports')).toBe(true);
    expect(inArea(item('nav.sections.wallet'), 'reports')).toBe(false);
    expect(inArea(item('nav.sections.wallet'), NAV_AREA_ALL)).toBe(true);
  });

  it('reads stored paths defensively', () => {
    expect(parseStoredPaths(null)).toEqual([]);
    expect(parseStoredPaths('not json')).toEqual([]);
    expect(parseStoredPaths('{"a":1}')).toEqual([]);
    expect(parseStoredPaths('["/a",1,"/b"]')).toEqual(['/a', '/b']);
  });

  it('keeps the recents newest first, without repeats, within the limit', () => {
    expect(pushRecent(['/a', '/b', '/c'], '/b', 3)).toEqual(['/b', '/a', '/c']);
    expect(pushRecent(['/a', '/b', '/c'], '/d', 3)).toEqual(['/d', '/a', '/b']);
  });

  it('adds and removes favourites and stops adding at the limit', () => {
    expect(toggleFavourite(['/a'], '/b', 2)).toEqual(['/a', '/b']);
    expect(toggleFavourite(['/a', '/b'], '/a', 2)).toEqual(['/b']);
    expect(toggleFavourite(['/a', '/b'], '/c', 2)).toEqual(['/a', '/b']);
  });
});
