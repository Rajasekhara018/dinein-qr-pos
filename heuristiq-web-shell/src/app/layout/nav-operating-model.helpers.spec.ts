import { describe, expect, it } from 'vitest';
import { FRANCHISE_OPERATING_MODELS } from './nav-operating-model.constants';
import { filterByOperatingModel } from './nav-operating-model.helpers';
import { NAV_ITEMS, NavItem } from './nav-items.constants';

const item = (path: string, operatingModels?: NavItem['operatingModels']): NavItem =>
  ({ path, icon: 'bi-x', label: 'l', section: 'nav.sections.agentMatrimony', permission: null, operatingModels });
const sample = [item('/always'), item('/franchise-only', FRANCHISE_OPERATING_MODELS), item('/empty-list', [])];
const paths = (items: NavItem[]) => items.map((i) => i.path);

// In menu order: the owner's matrimony pages (Franchise owner workspace), then the agent desk's proposals (Agent workspace).
const FRANCHISE_ONLY_MATRIMONY = [
  '/matrimony/agent/ratings',
  '/matrimony/agent/funnel',
  '/matrimony/agent/reassign',
  '/matrimony/agent/proposals',
];

describe('nav-operating-model.helpers', () => {
  it('leaves franchise-only items out for a company-run (DIRECT) organization', () => {
    expect(paths(filterByOperatingModel(sample, 'ready', 'DIRECT'))).toEqual(['/always', '/empty-list']);
  });

  it('keeps franchise-only items for FRANCHISE and HYBRID organizations', () => {
    expect(paths(filterByOperatingModel(sample, 'ready', 'FRANCHISE'))).toEqual(['/always', '/franchise-only', '/empty-list']);
    expect(paths(filterByOperatingModel(sample, 'ready', 'HYBRID'))).toEqual(['/always', '/franchise-only', '/empty-list']);
  });

  it('fails closed while loading, after a failure, or with no model', () => {
    expect(paths(filterByOperatingModel(sample, 'loading', null))).toEqual(['/always', '/empty-list']);
    expect(paths(filterByOperatingModel(sample, 'failed', null))).toEqual(['/always', '/empty-list']);
    expect(paths(filterByOperatingModel(sample, 'ready', null))).toEqual(['/always', '/empty-list']);
  });

  it('tags exactly the franchise features of the matrimony Agent section, and keeps the customer-care pages for every model', () => {
    const tagged = NAV_ITEMS.filter((i) => i.operatingModels?.length).map((i) => i.path);
    expect(tagged).toEqual(FRANCHISE_ONLY_MATRIMONY);

    const directAgentMenu = filterByOperatingModel(NAV_ITEMS, 'ready', 'DIRECT')
      .filter((i) => i.section === 'nav.sections.agentMatrimony').map((i) => i.path);
    expect(directAgentMenu).toEqual([
      '/matrimony/agent',
      '/matrimony/agent/add-customer',
      '/matrimony/agent/consent',
      '/matrimony/agent/biodata',
      '/matrimony/agent/profile-media',
      '/matrimony/agent/complaints',
    ]);
  });
});
