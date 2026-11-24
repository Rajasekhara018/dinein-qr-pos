import { HQ_PERMISSION_PRIVILEGES } from 'hq-shared-lib';
import { describe, expect, it } from 'vitest';
import { NAV_AREAS, WORKSPACE_SECTION_APPS } from './nav-areas.constants';
import { EXTRA_QUICK_NAV, NAV_ITEMS, SIDEBAR_DEFAULT_COLLAPSED_SECTIONS, findNavItemForUrl } from './nav-items.constants';

/** The wallet portal's screens: the agent's desk (Agent workspace) and the Wallet application's setup and operations. */
const WALLET_SECTIONS: string[] = [
  'nav.sections.agentWallet',
  'nav.sections.walletSetup',
  'nav.sections.walletOperations',
];

describe('nav-items.constants', () => {
  it('keeps each section contiguous so the sidebar shows one heading per section', () => {
    const seen = new Set<string>();
    let previous = '';
    for (const item of NAV_ITEMS.filter(n => !n.hidden)) {
      if (item.section !== previous) {
        expect(seen.has(item.section), item.section).toBe(false);
        seen.add(item.section);
        previous = item.section;
      }
    }
  });

  it('hides Make payment and Roles & logins from the menu but keeps them routable, and lists the Applications hub', () => {
    expect(NAV_ITEMS.find(n => n.path === '/home')?.hidden).toBeFalsy();
    for (const path of ['/payments/make-payment', '/org-structure/logins']) {
      expect(NAV_ITEMS.find(n => n.path === path)?.hidden, path).toBe(true);
    }
  });

  it('only folds sections that exist', () => {
    const sections = new Set(NAV_ITEMS.map(n => n.section));
    for (const section of SIDEBAR_DEFAULT_COLLAPSED_SECTIONS) expect(sections.has(section), section).toBe(true);
  });

  it('resolves an exact menu path to its item', () => {
    expect(findNavItemForUrl('/admin/train-admin/bookings')?.label).toBe('nav.items.trainBookings');
  });

  it('prefers the longest matching path (stuck payments beats refunds)', () => {
    expect(findNavItemForUrl('/admin/train-admin/refunds/stuck')?.label).toBe('nav.items.trainStuckPayments');
    expect(findNavItemForUrl('/admin/train-admin/refunds')?.label).toBe('nav.items.trainRefunds');
  });

  it('resolves a sub-page to its parent screen (setting detail -> Settings)', () => {
    expect(findNavItemForUrl('/admin/config-settings/train.irctcOtpMax')?.label).toBe('nav.items.configSettings');
    expect(findNavItemForUrl('/admin/change-requests/abc-123')?.label).toBe('nav.items.changeRequests');
  });

  it('ignores the query string, fragment and a trailing slash', () => {
    expect(findNavItemForUrl('/admin/config-settings/cities?page=2#top')?.label).toBe('nav.items.cities');
    expect(findNavItemForUrl('/admin/config-settings/serviceability')?.permission).toBe('view_serviceability');
    expect(findNavItemForUrl('/admin/audit/')?.label).toBe('nav.items.auditLogs');
  });

  it('does not match a path that only shares a prefix of characters', () => {
    expect(findNavItemForUrl('/admin/auditing')).toBeUndefined();
  });

  it('returns undefined for an unknown route', () => {
    expect(findNavItemForUrl('/somewhere-else')).toBeUndefined();
  });

  it('lists every newly added admin screen in the menu (so quick-navigate and the title can find them)', () => {
    const paths = NAV_ITEMS.map(n => n.path);
    for (const expected of [
      '/admin/audit',
      '/admin/train-admin/dashboard',
      '/admin/train-admin/bookings',
      '/admin/train-admin/refunds',
      '/admin/train-admin/refunds/stuck',
      '/admin/train-admin/tdr',
      '/admin/train-admin/alerts',
      '/admin/train-admin/vikalp',
      '/admin/train-admin/pnr',
      '/admin/train-admin/reconciliation',
      '/admin/train-admin/class-commissions',
      '/admin/train-admin/reports',
      '/admin/train-admin/report-jobs',
      '/admin/train-admin/travel-users',
      '/admin/train-admin/irctc-accounts',
      '/admin/change-requests',
      '/admin/config-settings',
      '/admin/config-settings/cities',
      '/admin/config-settings/serviceability',
      '/admin/config-settings/banners',
      '/admin/config-settings/faqs',
      '/admin/config-settings/app-versions',
    ]) {
      expect(paths, expected).toContain(expected);
    }
  });

  it('has unique menu paths and keeps the legacy quick-nav shortcuts out of the menu', () => {
    const paths = NAV_ITEMS.map(n => n.path);
    expect(new Set(paths).size).toBe(paths.length);
    for (const extra of EXTRA_QUICK_NAV) {
      expect(paths).not.toContain(extra.path);
    }
  });

  it('lists the wallet portal screens (federation remote railcab-wallet) under /wallet with keys the shared lib knows', () => {
    const wallet = NAV_ITEMS.filter(n => WALLET_SECTIONS.includes(n.section));
    expect(wallet.length).toBeGreaterThan(0);
    // Floats and account statements are wallet screens hosted by Rider_Web itself, under /access-management.
    const hostedHere = ['/access-management/partner-floats', '/access-management/account-statement', '/access-management/my-float'];
    for (const item of wallet) {
      expect(item.path.startsWith('/wallet/') || hostedHere.includes(item.path), item.path).toBe(true);
      expect(HQ_PERMISSION_PRIVILEGES[item.permission as string]?.length, item.path).toBeGreaterThan(0);
    }
    expect(findNavItemForUrl('/wallet/admin/journals/abc')?.label).toBe('nav.items.walletJournals');
  });

  it('lists the wallet statements and floats outside Access: the statements in Wallet, the agent\'s float in the Agent workspace', () => {
    const inSection = (section: string) => NAV_ITEMS.filter(n => n.section === section).map(n => n.path);
    const wallet = NAV_AREAS.find(a => a.key === 'wallet')!;
    const agent = NAV_AREAS.find(a => a.key === 'agent')!;
    const access = NAV_AREAS.find(a => a.key === 'access')!;

    expect(inSection('nav.sections.wallet')).toEqual(['/wallet/home', '/access-management/my-statement']);
    expect(inSection('nav.sections.walletOperations').slice(-2)).toEqual(['/access-management/partner-floats', '/access-management/account-statement']);
    for (const path of ['/access-management/my-statement', '/access-management/partner-floats', '/access-management/account-statement']) {
      const item = NAV_ITEMS.find(n => n.path === path)!;
      expect(wallet.sections, path).toContain(item.section);
      expect(access.sections, path).not.toContain(item.section);
    }
    expect(agent.sections).toContain(NAV_ITEMS.find(n => n.path === '/access-management/my-float')!.section);
  });

  it('orders the Wallet application: own wallet, setup, then operations (including control and close); the agent desk is in the Agent workspace', () => {
    const wallet = NAV_ITEMS.filter(n => NAV_AREAS.find(a => a.key === 'wallet')!.sections.includes(n.section));
    expect([...new Set(wallet.map(n => n.section))]).toEqual(['nav.sections.wallet', 'nav.sections.walletSetup', 'nav.sections.walletOperations']);

    const paths = (section: string) => NAV_ITEMS.filter(n => n.section === section).map(n => n.path);
    expect(paths('nav.sections.agentWallet')).toEqual([
      '/wallet/agent',
      '/wallet/agent/load',
      '/wallet/agent/customers',
      '/wallet/agent/cash',
      // Team sharing and team movement are hidden from the menu for now (commented out in nav-items.constants.ts).
      '/wallet/agent/earnings',
      '/access-management/my-float',
    ]);
    expect(paths('nav.sections.walletSetup')).toEqual(['/wallet/admin/system-accounts']);
    expect(paths('nav.sections.walletOperations')[0]).toBe('/wallet/admin');
    // Period close ends the wallet portal's own screens; the floats and statements hosted by Rider_Web follow it.
    expect(paths('nav.sections.walletOperations').slice(-3)).toEqual([
      '/wallet/admin/period-close', '/access-management/partner-floats', '/access-management/account-statement',
    ]);
  });

  it('lists the accounting screens in the Accounting area as setup, operations and insights, with keys the shared lib knows', () => {
    const inSection = (section: string) => NAV_ITEMS.filter(n => n.section === section).map(n => [n.path, n.permission]);
    expect(inSection('nav.sections.accountingSetup')).toEqual([['/accounting/admin/chart', 'view_accounting_chart']]);
    expect(inSection('nav.sections.accountingOperations')).toEqual([
      ['/wallet/admin/accounting/transactions', 'view_wallet_accounting_transactions'],
      ['/wallet/admin/accounting/statements', 'view_wallet_accounting_party_statement'],
    ]);
    expect(inSection('nav.sections.accountingInsights')).toEqual([
      ['/wallet/admin/accounting/summary', 'view_wallet_accounting_summary'],
      ['/wallet/admin/accounting/commissions', 'view_wallet_accounting_commissions'],
      ['/wallet/admin/accounting/franchises', 'view_wallet_accounting_franchise'],
      ['/accounting/admin/trial-balance', 'view_accounting_trial_balance'],
    ]);
    expect([...HQ_PERMISSION_PRIVILEGES['view_accounting_chart']]).toEqual(['WALLET_ACCOUNTING_CHART_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES['view_accounting_trial_balance']]).toEqual(['WALLET_ACCOUNTING_TRIAL_BALANCE_READ']);
    // Accounting is its own area: the accounting portal and the wallet accounting views, split out of Wallet.
    expect(NAV_AREAS.find(a => a.key === 'accounting')?.sections).toEqual([
      'nav.sections.accountingSetup', 'nav.sections.accountingOperations', 'nav.sections.accountingInsights',
    ]);
    expect(NAV_AREAS.find(a => a.key === 'wallet')?.sections.some(sec => sec.startsWith('nav.sections.accounting'))).toBe(false);
    expect(findNavItemForUrl('/accounting/admin/trial-balance?asOf=2026-03-31')?.label).toBe('nav.items.accountingTrialBalance');
  });

  it('lists the inventory portal screens (federation remote railcab-ims) under /inventory, as setup then operations, with keys the shared lib knows', () => {
    const inventory = NAV_ITEMS.filter(n => n.path.startsWith('/inventory/'));
    expect(inventory.map(n => [n.path, n.permission, n.section])).toEqual([
      ['/inventory/items', 'view_inventory_items', 'nav.sections.imsSetup'],
      ['/inventory/locations', 'view_inventory_locations', 'nav.sections.imsSetup'],
      ['/inventory/dashboard', 'view_inventory_dashboard', 'nav.sections.imsOperations'],
      ['/inventory/receiving', 'view_inventory_receiving', 'nav.sections.imsOperations'],
      ['/inventory/transfers', 'view_inventory_transfers', 'nav.sections.imsOperations'],
      ['/inventory/ledger', 'view_inventory_ledger', 'nav.sections.imsOperations'],
    ]);
    for (const item of inventory) {
      expect(item.label.startsWith('nav.items.inventory'), item.label).toBe(true);
      expect(item.icon.startsWith('bi-'), item.icon).toBe(true);
      expect(HQ_PERMISSION_PRIVILEGES[item.permission!]?.length, item.permission!).toBeGreaterThan(0);
    }
    expect(NAV_AREAS.find(a => a.key === 'ims')?.sections).toEqual(['nav.sections.imsSetup', 'nav.sections.imsOperations']);
    expect(findNavItemForUrl('/inventory/receiving/6650f1c2a9e4b1d2c3f4a5b6')?.label).toBe('nav.items.inventoryReceiving');
    expect(findNavItemForUrl('/inventory/transfers/42')?.label).toBe('nav.items.inventoryTransfers');
  });

  it('orders the reports menu as the setup has to be done: application, custom fields, template, configuration, then output', () => {
    const reports = NAV_ITEMS.filter(n => n.path.startsWith('/reports/') && !n.hidden);
    expect(reports.map(n => n.path)).toEqual([
      '/reports/home',
      '/reports/csearch/SYSTEM_CONFIGURATION',
      '/reports/csearch/REPORTS_CUSTOM_FIELDS',
      '/reports/csearch/REPORTS_TEMPLATE',
      '/reports/csearch/REPORTS_CONFIGURATION',
      '/reports/csearch/REPORTS',
      '/reports/csearch/AUDITLOG',
    ]);
    expect([...new Set(reports.map(n => n.section))]).toEqual([
      'nav.sections.reports',
      'nav.sections.reportsSetup',
      'nav.sections.reportsRun',
    ]);
    // The create / edit screens are shown under the setup group.
    for (const item of NAV_ITEMS.filter(n => n.path.startsWith('/reports/') && n.hidden)) {
      expect(item.section, item.path).toBe('nav.sections.reportsSetup');
    }
  });

  it('lists the matrimony menu as setup, daily queues and insights, with the agent desk and the owner\'s pages in the workspaces', () => {
    const matrimony = NAV_ITEMS.filter(n => n.path.startsWith('/matrimony/'));
    expect(NAV_AREAS.find(a => a.key === 'matrimony')?.sections).toEqual([
      'nav.sections.matrimonySetup',
      'nav.sections.matrimonyOperations',
      'nav.sections.matrimonyInsights',
    ]);
    expect([...new Set(matrimony.map(n => n.section))]).toEqual([
      'nav.sections.matrimonySetup',
      'nav.sections.matrimonyOperations',
      'nav.sections.matrimonyInsights',
      'nav.sections.ownerMatrimony',
      'nav.sections.agentMatrimony',
    ]);
    const paths = (section: string) => matrimony.filter(n => n.section === section).map(n => n.path);
    expect(paths('nav.sections.matrimonySetup')).toEqual([
      '/matrimony/platform-config',
      '/matrimony/subscription-plans',
      '/matrimony/operational-settings',
    ]);
    expect(paths('nav.sections.matrimonyOperations')[0]).toBe('/matrimony/dashboard');
    expect(paths('nav.sections.agentMatrimony')[0]).toBe('/matrimony/agent');
    // Running the franchise is its own section, apart from the agent's desk: an agent's role holds none of these privileges.
    expect(paths('nav.sections.ownerMatrimony')).toEqual([
      '/matrimony/agent/complaints',
      '/matrimony/agent/ratings',
      '/matrimony/agent/funnel',
      '/matrimony/agent/reassign',
    ]);
    expect(paths('nav.sections.agentMatrimony')).not.toContain('/matrimony/agent/reassign');
    expect(paths('nav.sections.matrimonyInsights')).toContain('/matrimony/insights');
  });

  it('lists the BestRide menu as three setup sections, operations, people and fleet, then finance; train keeps setup, operations, reconciliation', () => {
    const admin = NAV_ITEMS.filter(n => n.path.startsWith('/admin/') && !n.path.startsWith('/admin/franchise-admin/'));
    const visible = admin.filter(n => !n.hidden && n.path !== '/admin/change-requests');
    const sections = [...new Set(visible.map(n => n.section))];
    expect(sections).toEqual([
      'nav.sections.adminSetup',
      'nav.sections.adminStep2VehiclesFares',
      'nav.sections.adminStep3AppContent',
      'nav.sections.adminOperations',
      'nav.sections.adminPeopleFleet',
      'nav.sections.adminFinance',
      'nav.sections.trainSetup',
      'nav.sections.trainOperations',
      'nav.sections.trainReconciliation',
    ]);
    const paths = (section: string) => visible.filter(n => n.section === section).map(n => n.path);
    expect(paths('nav.sections.adminSetup')).toEqual(['/admin/config-settings', '/admin/config-settings/cities', '/admin/config-settings/serviceability']);
    expect(paths('nav.sections.adminStep2VehiclesFares')).toEqual(['/admin/config-settings/vehicle-types', '/admin/fare-engine']);
    expect(paths('nav.sections.adminStep3AppContent')).toEqual([
      '/admin/config-settings/banners', '/admin/config-settings/faqs', '/admin/config-settings/app-versions',
    ]);
    // Operations: the ride work, the live map and the cash handling.
    expect(paths('nav.sections.adminOperations')).toEqual(['/admin/dashboard', '/admin/sos', '/admin/rides', '/admin/ride-disputes', '/admin/support',
      '/admin/fleet-map', '/admin/cash-ops/driver-payouts', '/admin/cash-ops/offline-handovers', '/admin/cash-ops/ledger-check']);
    expect(paths('nav.sections.adminPeopleFleet')).toEqual(['/admin/drivers', '/admin/vendors', '/admin/customers', '/admin/fleet']);
    expect(paths('nav.sections.adminFinance')).toEqual(['/admin/finance', '/admin/analytics', '/admin/audit']);
    expect(paths('nav.sections.trainSetup')).toEqual([
      '/admin/train-admin/irctc-accounts',
      '/admin/train-admin/class-commissions',
      '/admin/train-admin/travel-users',
    ]);
    expect(paths('nav.sections.trainReconciliation')[0]).toBe('/admin/train-admin/reconciliation');
  });

  it('keeps every developer tool behind a permission that maps to existing privileges, so only staff see the Developer workspace', () => {
    const developer = NAV_ITEMS.filter((n) => n.section === 'nav.sections.developerTools');
    expect(developer.length).toBeGreaterThan(0);
    for (const item of developer) {
      expect(item.permission, item.path).toBeTruthy();
      expect(HQ_PERMISSION_PRIVILEGES[item.permission as string]?.length, item.path).toBeGreaterThan(0);
    }
    expect(NAV_ITEMS.find((n) => n.path === '/service-registry')?.permission).toBe('view_service_registry');
    expect([...HQ_PERMISSION_PRIVILEGES['view_service_registry']]).toEqual(['ADMIN_READ']);
    expect(NAV_ITEMS.find((n) => n.path === '/api-docs')?.permission).toBe('view_developer_docs');
    expect(NAV_ITEMS.find((n) => n.path === '/workflow')?.permission).toBe('view_workflow_tasks');
  });

  it('keeps Code coverage with the developer tools, not in the Rider app finance group', () => {
    const coverage = NAV_ITEMS.find(n => n.path === '/system/coverage')!;

    expect(coverage.section).toBe('nav.sections.developerTools');
    expect(coverage.permission).toBe('view_code_coverage');
    expect(NAV_ITEMS.filter(n => n.section === 'nav.sections.adminFinance').map(n => n.path)).not.toContain('/system/coverage');
    const developer = NAV_AREAS.find(a => a.key === 'developer')!;
    expect(developer.sections).toContain(coverage.section);
  });

  it('lists the access set-up as numbered steps, in the order it has to be done', () => {
    const inSection = (section: string) => NAV_ITEMS.filter(n => n.section === section).map(n => n.path);
    // The sidebar prints a heading each time the section changes, so the sections must be contiguous and in this order.
    const order = [...new Set(NAV_ITEMS.filter(n => n.section.startsWith('nav.sections.access')).map(n => n.section))];
    expect(order).toEqual([
      'nav.sections.accessStep1Organization',
      'nav.sections.accessStep3People',
      'nav.sections.accessStep2Organization',
      'nav.sections.accessStep2System',
      'nav.sections.accessOperations',
    ]);
    const firstRow = (section: string) => NAV_ITEMS.findIndex(n => n.section === section);
    const lastRow = (section: string) => NAV_ITEMS.map(n => n.section).lastIndexOf(section as never);
    for (let i = 1; i < order.length; i++) {
      expect(lastRow(order[i - 1]), order[i]).toBeLessThan(firstRow(order[i]));
    }
    expect(inSection('nav.sections.accessStep1Organization')).toEqual([
      '/org-structure/builder',
      '/org-structure/chart',
      '/org-structure/import',
    ]);
    expect(inSection('nav.sections.accessStep2Organization')).toEqual([
      '/access-management/organizations',
      '/access-management/applications',
      '/access-management/clients',
      '/access-management/security-policies',
      '/access-management/setup-checklist',
    ]);
    expect(inSection('nav.sections.accessStep2System')).toEqual([
      '/onboarding-forms',
      '/nms-templates',
      '/nms-templates/events',
      '/document-management/document-types',
      '/document-management/lifecycle',
    ]);
    // Roles come before users: a user is created with a role that has to exist already.
    expect(inSection('nav.sections.accessStep3People')).toEqual([
      '/access-management/roles',
      '/access-management/users',
      '/org-structure/logins',
    ]);
    expect(inSection('nav.sections.accessOperations')).toEqual([
      '/onboarding-reviews',
      '/onboarded-users',
    ]);
  });

  it('gives every application one Operations section, after its setup, with the setup sections folded by default', () => {
    const operations: Record<string, string> = {
      access: 'nav.sections.accessOperations',
      franchise: 'nav.sections.franchiseOperations',
      wallet: 'nav.sections.walletOperations',
      accounting: 'nav.sections.accountingOperations',
      matrimony: 'nav.sections.matrimonyOperations',
      admin: 'nav.sections.adminOperations',
      food: 'nav.sections.foodOperations',
      ims: 'nav.sections.imsOperations',
      qa: 'nav.sections.qaOperations',
    };
    const setup: Record<string, string[]> = {
      franchise: ['nav.sections.franchiseSetup', 'nav.sections.franchiseMoney'],
      wallet: ['nav.sections.walletSetup'],
      accounting: ['nav.sections.accountingSetup'],
      matrimony: ['nav.sections.matrimonySetup'],
      admin: ['nav.sections.adminSetup', 'nav.sections.adminStep2VehiclesFares', 'nav.sections.adminStep3AppContent'],
      food: ['nav.sections.foodSetup'],
      ims: ['nav.sections.imsSetup'],
      qa: ['nav.sections.qaSetup'],
    };
    const firstRow = (section: string) => NAV_ITEMS.findIndex(n => n.section === section);
    for (const [key, section] of Object.entries(operations)) {
      const area = NAV_AREAS.find(a => a.key === key)!;
      expect(area.sections.filter(sec => sec.endsWith('Operations')), key).toEqual([section]);
      expect(NAV_ITEMS.filter(n => n.section === section).length, section).toBeGreaterThan(0);
      for (const sec of setup[key] ?? []) {
        expect(area.sections, key).toContain(sec);
        expect(firstRow(sec), sec).toBeLessThan(firstRow(section));
        expect(SIDEBAR_DEFAULT_COLLAPSED_SECTIONS, sec).toContain(sec);
      }
      expect(SIDEBAR_DEFAULT_COLLAPSED_SECTIONS, section).not.toContain(section);
    }
  });

  it('splits Royal Food into setup, operations and insights, keeping every console screen', () => {
    const inSection = (section: string) => NAV_ITEMS.filter(n => n.section === section).map(n => n.path);
    expect(NAV_AREAS.find(a => a.key === 'food')?.sections).toEqual(['nav.sections.foodSetup', 'nav.sections.foodOperations', 'nav.sections.foodInsights']);
    expect(inSection('nav.sections.foodSetup')).toEqual(['/food/restaurants', '/food/categories', '/food/foods', '/food/coupons', '/food/offers',
      '/food/delivery-partners', '/food/admin-users', '/food/notifications', '/food/settings']);
    expect(inSection('nav.sections.foodOperations')).toEqual(['/food/dashboard', '/food/orders', '/food/payments', '/food/refunds', '/food/customers',
      '/food/reviews', '/food/support-tickets', '/food/live-board', '/food/refund-requests', '/food/restaurant-approvals',
      '/food/menu-reviews', '/food/settlements', '/food-partner']);
    expect(inSection('nav.sections.foodInsights')).toEqual(['/food/reports', '/food/analytics']);
    expect(NAV_ITEMS.filter(n => n.path.startsWith('/food/')).length).toBe(23);
  });

  it('splits QA automation out of Developer into setup, operations and insights', () => {
    const inSection = (section: string) => NAV_ITEMS.filter(n => n.section === section).map(n => n.path);
    expect(NAV_AREAS.find(a => a.key === 'developer')?.sections).toEqual(['nav.sections.developerTools']);
    expect(NAV_AREAS.find(a => a.key === 'qa')?.sections).toEqual(['nav.sections.qaSetup', 'nav.sections.qaOperations', 'nav.sections.qaInsights']);
    expect(inSection('nav.sections.qaSetup')).toEqual(['/qa/test-cases', '/qa/test-sets', '/qa/test-case-studio', '/qa/endpoint-inventory', '/qa/api-documents']);
    expect(inSection('nav.sections.qaOperations')).toEqual(['/qa/regression', '/qa/smoke', '/qa/executions/test-cases', '/qa/executions/test-sets',
      '/qa/failures', '/system/qa']);
    expect(inSection('nav.sections.qaInsights')).toEqual(['/qa/overview', '/qa/executive-summary']);
  });

  it('orders the area switcher: Access, Franchise, the owner and agent workspaces, Wallet, Accounting, then the products', () => {
    expect(NAV_AREAS.map(a => a.key)).toEqual([
      'access', 'franchise', 'owner', 'agent', 'wallet', 'accounting', 'matrimony', 'admin', 'food', 'ims', 'reports', 'train', 'developer', 'qa',
    ]);
  });

  it('gives the franchise owner and the agent a workspace each, grouped by application, apart from the HQ applications', () => {
    const inSection = (section: string) => NAV_ITEMS.filter(n => n.section === section).map(n => n.path);
    const owner = NAV_AREAS.find(a => a.key === 'owner')!;
    const agent = NAV_AREAS.find(a => a.key === 'agent')!;

    expect(owner.label).toBe('nav.areas.owner');
    expect(agent.label).toBe('nav.areas.agent');
    expect(owner.sections).toEqual(['nav.sections.ownerFranchise', 'nav.sections.ownerMatrimony', 'nav.sections.ownerBestRide']);
    expect(agent.sections).toEqual(['nav.sections.agentMatrimony', 'nav.sections.agentWallet']);
    expect(inSection('nav.sections.ownerFranchise')).toEqual([
      '/access-management/my-franchises',
      '/access-management/my-earnings',
      '/access-management/my-payouts',
    ]);
    expect(inSection('nav.sections.ownerMatrimony')).toEqual([
      '/matrimony/agent/complaints', '/matrimony/agent/ratings', '/matrimony/agent/funnel', '/matrimony/agent/reassign',
    ]);
    // My drivers has no franchise in its address: it redirects to the only franchise's drivers, or to My franchises.
    expect(NAV_ITEMS.filter(n => n.section === 'nav.sections.ownerBestRide').map(n => [n.path, n.permission])).toEqual([
      ['/access-management/my-drivers', 'view_franchise_drivers'],
    ]);
    expect(inSection('nav.sections.agentMatrimony')).toEqual([
      '/matrimony/agent', '/matrimony/agent/add-customer', '/matrimony/agent/consent', '/matrimony/agent/biodata',
      '/matrimony/agent/profile-media', '/matrimony/agent/proposals',
    ]);
    // Every workspace section names the application whose subscription it follows, and no HQ application lists it.
    for (const section of [...owner.sections, ...agent.sections]) {
      expect(WORKSPACE_SECTION_APPS[section], section).toBeTruthy();
      expect(NAV_AREAS.filter(a => a.key !== 'owner' && a.key !== 'agent').some(a => a.sections.includes(section)), section).toBe(false);
    }
    // The owner's pages and the agent's pages never share a section: an agent's role holds none of the owner's privileges.
    const ownerPaths = owner.sections.flatMap(inSection);
    expect(agent.sections.flatMap(inSection).some(p => ownerPaths.includes(p))).toBe(false);
  });

  it('lists Franchise as its own application for HQ: setup (places and plans, money rules), then operations', () => {
    const inSection = (section: string) => NAV_ITEMS.filter(n => n.section === section).map(n => n.path);
    const area = NAV_AREAS.find(a => a.key === 'franchise')!;
    const order = [
      'nav.sections.franchiseSetup',
      'nav.sections.franchiseMoney',
      'nav.sections.franchiseOperations',
    ];

    expect(area.label).toBe('nav.areas.franchise');
    expect(area.sections).toEqual(order);
    // No other area lists a franchise section, so the screens show only under Franchise.
    expect(NAV_AREAS.filter(a => a.key !== 'franchise').flatMap(a => a.sections).some(s => s.startsWith('nav.sections.franchise'))).toBe(false);
    // The sidebar prints a heading each time the section changes, so the sections must be contiguous and in this order.
    expect([...new Set(NAV_ITEMS.filter(n => n.section.startsWith('nav.sections.franchise')).map(n => n.section))]).toEqual(order);
    const firstRow = (section: string) => NAV_ITEMS.findIndex(n => n.section === section);
    const lastRow = (section: string) => NAV_ITEMS.map(n => n.section).lastIndexOf(section as never);
    for (let i = 1; i < order.length; i++) {
      expect(lastRow(order[i - 1]), order[i]).toBeLessThan(firstRow(order[i]));
    }
    expect(inSection('nav.sections.franchiseSetup')).toEqual([
      '/access-management/franchise-locations',
      '/access-management/franchise-demand',
      '/access-management/franchise-plans',
      '/access-management/franchise-packages',
    ]);
    expect(inSection('nav.sections.franchiseMoney')).toEqual([
      '/admin/franchise-admin/commission-plans',
      '/admin/franchise-admin/referral-plans',
      '/admin/franchise-admin/payout-rules',
      '/admin/franchise-admin/rule-resolver',
    ]);
    expect(inSection('nav.sections.franchiseOperations')).toEqual([
      '/access-management/franchises',
      '/access-management/franchise-members',
      '/admin/franchise-admin/payout-approvals',
      '/admin/franchise-admin/ledger',
      '/admin/franchise-admin/driver-subscriptions',
    ]);
  });

  it('lists the matrimony portal screens (federation remote railcab-matrimony) behind per-screen view_matrimony_* permissions', () => {
    const matrimony = NAV_ITEMS.filter(n => n.path.startsWith('/matrimony/'));
    expect(matrimony.length).toBeGreaterThan(0);
    for (const item of matrimony) {
      expect(item.path.startsWith('/matrimony/'), item.path).toBe(true);
      // One key per screen (view_matrimony_<screen>), each mapped to that screen's read/list privilege in the shared lib.
      const screen = item.path.replace('/matrimony/', '').replace(/\//g, '_').replace(/-/g, '_');
      expect(item.permission, item.path).toBe(`view_matrimony_${screen}`);
      expect(HQ_PERMISSION_PRIVILEGES[item.permission as string]?.length, item.path).toBeGreaterThan(0);
    }
    expect(findNavItemForUrl('/matrimony/payments?page=2')?.label).toBe('nav.items.matrimonyPayments');
  });

  it('lists the reports portal screens (federation remote railcab-reports) under /reports with keys the shared lib knows', () => {
    const reports = NAV_ITEMS.filter(n => n.section === 'nav.sections.reports');
    expect(reports.length).toBeGreaterThan(0);
    for (const item of reports) {
      expect(item.path.startsWith('/reports/'), item.path).toBe(true);
      expect(item.permission, item.path).toMatch(/^view_reports_/);
      expect(HQ_PERMISSION_PRIVILEGES[item.permission as string]?.length, item.path).toBeGreaterThan(0);
    }
    expect(findNavItemForUrl('/reports/system-config/cre')?.label).toBe('nav.items.reportsSystemConfig');
    expect(findNavItemForUrl('/reports/csearch/REPORTS')?.label).toBe('nav.items.reportsGenerated');
  });

  it('resolves the reports create / edit screens (query string, /cre and /:id suffixes) to their setup menu item', () => {
    const cases: Array<[string, string]> = [
      ['/reports/reports-config/cre?productName=MATRIMONY', 'nav.items.reportsConfig'],
      ['/reports/reports-config/6650f1c2a9e4b1', 'nav.items.reportsConfig'],
      ['/reports/reports-config?productName=MATRIMONY', 'nav.items.reportsConfig'],
      ['/reports/report-template/cre', 'nav.items.reportsTemplates'],
      ['/reports/reports-custom-fields/cre', 'nav.items.reportsCustomFields'],
      ['/reports/system-config/cre', 'nav.items.reportsSystemConfig'],
    ];
    for (const [url, label] of cases) {
      const item = findNavItemForUrl(url);
      expect(item?.label, url).toBe(label);
      expect(item?.section, url).toBe('nav.sections.reportsSetup');
    }
  });
});
