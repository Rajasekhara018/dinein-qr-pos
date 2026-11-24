import { describe, expect, it } from 'vitest';
import { APP_CODE_BY_SUITE_KEY } from '../home/home.constants';
import { NAV_AREA_ALL, NAV_AREAS } from './nav-areas.constants';
import { availableAreas, effectiveArea } from './nav-areas.helpers';
import { NAV_ITEMS, NavItem } from './nav-items.constants';
import { awaitsSubscription, filterBySubscription, sectionNeedsSubscription } from './nav-subscription.helpers';

const item = (section: NavItem['section']): NavItem => ({ path: `/${section}`, icon: 'bi-x', label: 'l', section, permission: null });
const keysOf = (items: NavItem[]) => availableAreas(items).map((a) => a.key);
const WALLET = APP_CODE_BY_SUITE_KEY['wallet'];
// The Agent workspace shows too: its wallet desk follows Wallet, which needs no subscription.
const CORE = ['access', 'agent', 'wallet', 'accounting', 'reports', 'developer', 'qa'];
const sample = [
  item('nav.sections.main'),
  item('nav.sections.agentWallet'),
  item('nav.sections.agentMatrimony'),
  item('nav.sections.reports'),
  item('nav.sections.trainSetup'),
  item('nav.sections.adminSetup'),
];

describe('nav-subscription.helpers', () => {
  it('keeps unassigned sections and core areas, and only the subscribed applications once ready', () => {
    const shown = filterBySubscription(sample, 'ready', []);

    expect(shown.map((i) => i.section)).toEqual(['nav.sections.main', 'nav.sections.agentWallet', 'nav.sections.reports']);
  });

  it('never lists Train, whatever is subscribed', () => {
    const shown = filterBySubscription(NAV_ITEMS, 'ready', Object.values(APP_CODE_BY_SUITE_KEY));

    expect(shown.some((i) => i.section.startsWith('nav.sections.train'))).toBe(false);
    expect(keysOf(shown)).not.toContain('train');
    expect(keysOf(shown)).toContain('admin');
  });

  it('shows only the areas that need no subscription while loading (no flash of every application)', () => {
    const shown = filterBySubscription(NAV_ITEMS, 'loading', Object.values(APP_CODE_BY_SUITE_KEY));

    expect(keysOf(shown)).toEqual(CORE);
  });

  it('fails closed when the lookup failed: gated areas hidden, core areas kept', () => {
    expect(keysOf(filterBySubscription(NAV_ITEMS, 'failed', Object.values(APP_CODE_BY_SUITE_KEY)))).toEqual(CORE);
  });

  it('shows only the core areas to an organization subscribed to nothing', () => {
    expect(keysOf(filterBySubscription(NAV_ITEMS, 'ready', []))).toEqual(CORE);
  });

  it('always lists Wallet, and follows the subscriptions for the other applications', () => {
    expect(keysOf(filterBySubscription(NAV_ITEMS, 'ready', []))).toContain('wallet');
    expect(keysOf(filterBySubscription(NAV_ITEMS, 'ready', [APP_CODE_BY_SUITE_KEY['food']]))).toContain('wallet');
    expect(keysOf(filterBySubscription(NAV_ITEMS, 'ready', [APP_CODE_BY_SUITE_KEY['food']]))).toContain('food');
    expect(keysOf(filterBySubscription(NAV_ITEMS, 'ready', []))).not.toContain('food');
  });

  it('knows which sections wait on the lookup', () => {
    expect(sectionNeedsSubscription('nav.sections.agentWallet')).toBe(false);
    expect(sectionNeedsSubscription('nav.sections.agentMatrimony')).toBe(true);
    expect(sectionNeedsSubscription('nav.sections.ownerFranchise')).toBe(true);
    expect(sectionNeedsSubscription('nav.sections.ownerMatrimony')).toBe(true);
    expect(sectionNeedsSubscription('nav.sections.reports')).toBe(false);
    expect(sectionNeedsSubscription('nav.sections.main')).toBe(false);
    expect(awaitsSubscription(sample, 'loading')).toBe(true); // matrimony waits
    expect(awaitsSubscription(sample, 'ready')).toBe(false);
    expect(awaitsSubscription([item('nav.sections.reports')], 'loading')).toBe(false);
  });

  it('shows each section of the Franchise owner and Agent workspaces only when its own application is subscribed', () => {
    const sectionsOf = (codes: string[]) =>
      new Set(filterBySubscription(NAV_ITEMS, 'ready', codes).map((i) => i.section).filter((s) => /^nav\.sections\.(owner|agent)/.test(s)));

    // Nothing subscribed: only the agent's wallet desk (Wallet needs no subscription).
    expect([...sectionsOf([])]).toEqual(['nav.sections.agentWallet']);
    // Matrimony subscribed: the owner's and the agent's matrimony pages appear, the owner's franchise pages do not.
    expect(sectionsOf([APP_CODE_BY_SUITE_KEY['matrimony']])).toEqual(new Set(['nav.sections.ownerMatrimony', 'nav.sections.agentMatrimony', 'nav.sections.agentWallet']));
    // Franchise Management subscribed: the owner's own franchises, earnings and payouts.
    expect(sectionsOf([APP_CODE_BY_SUITE_KEY['franchise']])).toEqual(new Set(['nav.sections.ownerFranchise', 'nav.sections.agentWallet']));
    // BestRide subscribed: the owner's My drivers.
    expect(sectionsOf([APP_CODE_BY_SUITE_KEY['admin']])).toEqual(new Set(['nav.sections.ownerBestRide', 'nav.sections.agentWallet']));
    // The Franchise owner workspace is offered only when one of its applications is subscribed.
    expect(keysOf(filterBySubscription(NAV_ITEMS, 'ready', []))).not.toContain('owner');
    expect(keysOf(filterBySubscription(NAV_ITEMS, 'ready', [APP_CODE_BY_SUITE_KEY['franchise']]))).toContain('owner');
  });

  it('falls back to All when the remembered area (sidebar.area) is now hidden', () => {
    const areas = availableAreas(filterBySubscription(NAV_ITEMS, 'ready', [WALLET]));

    expect(effectiveArea('train', areas)).toBe(NAV_AREA_ALL);
    expect(effectiveArea('matrimony', areas)).toBe(NAV_AREA_ALL);
    expect(effectiveArea('wallet', areas)).toBe('wallet');
    expect(NAV_AREAS.some((a) => a.key === 'train')).toBe(true);
  });
});
