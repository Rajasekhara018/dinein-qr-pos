/**
 * The sidebar groups its many sections into a few areas, and shows one area at a time (chosen with the area switcher), so
 * the menu stays short. Sections that no area lists, and the sections of NAV_ALWAYS_VISIBLE_SECTIONS, show in every area.
 * `label` is an i18n key (nav.areas.*).
 */

import type { NavSection } from './nav-items.constants';

export interface NavArea {
  key: string;
  /** i18n key of the area's name. */
  label: string;
  icon: string;
  /**
   * The application's logo (a one-colour SVG glyph under assets/apps), shown instead of `icon` in the header launcher and on the
   * Applications hub. It is painted in the current text colour through a CSS mask (AppAreaLogo), so it follows the selected
   * theme and dark mode. Absent: the Bootstrap icon is shown.
   */
  logo?: string;
  sections: ReadonlyArray<NavSection>;
}

/** The choice that lists every area's sections at once. */
export const NAV_AREA_ALL = 'all';

/**
 * In the order of the area switcher (and of the header launcher and the Applications hub): Access & configuration, Franchise,
 * the My franchise and Agent workspaces, Wallet, Accounting, then the products by their catalogue names (Pasupu Kumkuma
 * Matrimony, BestRide, Royal Food, Best Purchase), then Reports, Train, Developer and QA automation.
 * Inside an application the sections run Setup (folded by default) → Operations (the daily work) → Insights, for HQ staff. A
 * franchise owner's and an agent's own pages are not in the applications but in their workspace, grouped by application.
 */
export const NAV_AREAS: ReadonlyArray<NavArea> = [
  {
    key: 'access',
    label: 'nav.areas.access',
    icon: 'bi-people',
    sections: [
      'nav.sections.account',
      'nav.sections.accessStep1Organization',
      'nav.sections.accessStep2Organization',
      'nav.sections.accessStep2System',
      'nav.sections.accessStep3People',
      'nav.sections.accessOperations',
    ],
  },
  // Franchise: its own application (catalogue code FRANCHISE_MANAGEMENT_APP, see APP_CODE_BY_SUITE_KEY), so the sidebar shows it
  // only for an organization subscribed to it. Its screens were steps 4 to 6 of Access before.
  {
    key: 'franchise',
    logo: 'assets/apps/franchise.svg',
    label: 'nav.areas.franchise',
    icon: 'bi-shop',
    sections: [
      'nav.sections.franchiseSetup',
      'nav.sections.franchiseMoney',
      'nav.sections.franchiseOperations',
    ],
  },
  // Persona workspaces: "My franchise" (every franchise member's own franchise, earnings and payouts, plus owner-only groups) and
  // "Agent" (the daily desk), each with the pages from every application, one section per
  // application. Each section follows its own application's subscription (WORKSPACE_SECTION_APPS), and a workspace shows only to
  // someone who may open one of its pages (privileges, never role names).
  {
    key: 'owner',
    label: 'nav.areas.owner',
    icon: 'bi-person-workspace',
    sections: ['nav.sections.ownerFranchise', 'nav.sections.ownerMatrimony', 'nav.sections.ownerBestRide'],
  },
  {
    key: 'agent',
    label: 'nav.areas.agent',
    icon: 'bi-person-badge',
    sections: ['nav.sections.agentMatrimony', 'nav.sections.agentWallet'],
  },
  { key: 'wallet', label: 'nav.areas.wallet', icon: 'bi-wallet2', logo: 'assets/apps/wallet.svg', sections: ['nav.sections.wallet', 'nav.sections.walletSetup', 'nav.sections.walletOperations'] },
  // Accounting: its own area (wallet accounting views and the accounting portal), split out of Wallet. Platform tooling like
  // Wallet, so no catalogue subscription applies (CORE_SUITE_APP_KEYS).
  { key: 'accounting', label: 'nav.areas.accounting', icon: 'bi-journal-bookmark', logo: 'assets/apps/accounting.svg', sections: ['nav.sections.accountingSetup', 'nav.sections.accountingOperations', 'nav.sections.accountingInsights'] },
  { key: 'matrimony', label: 'nav.areas.matrimony', icon: 'bi-heart', logo: 'assets/apps/pasupu-kumkuma.svg', sections: ['nav.sections.matrimonySetup', 'nav.sections.matrimonyOperations', 'nav.sections.matrimonyInsights'] },
  {
    key: 'admin',
    logo: 'assets/apps/bestride.svg',
    label: 'nav.areas.admin',
    icon: 'bi-shield-check',
    sections: [
      'nav.sections.adminSetup',
      'nav.sections.adminStep2VehiclesFares',
      'nav.sections.adminStep3AppContent',
      'nav.sections.adminOperations',
      'nav.sections.adminPeopleFleet',
      'nav.sections.adminFinance',
    ],
  },
  { key: 'food', label: 'nav.areas.food', icon: 'bi-basket', logo: 'assets/apps/royal-food.svg', sections: ['nav.sections.foodSetup', 'nav.sections.foodOperations', 'nav.sections.foodInsights'] },
  // Inventory (Rider_IMSWeb): its own application like Food. The key is the Applications hub's 'ims' card, so the hub and the
  // sidebar show the same screens; the organization needs the catalogue application that carries IMS (BEST_PURCHASE_APP).
  { key: 'ims', label: 'nav.areas.ims', icon: 'bi-boxes', logo: 'assets/apps/best-purchase.svg', sections: ['nav.sections.imsSetup', 'nav.sections.imsOperations'] },
  { key: 'reports', label: 'nav.areas.reports', icon: 'bi-bar-chart-line', sections: ['nav.sections.reports', 'nav.sections.reportsSetup', 'nav.sections.reportsRun'] },
  {
    key: 'train',
    label: 'nav.areas.train',
    icon: 'bi-train-front',
    sections: ['nav.sections.trainSetup', 'nav.sections.trainOperations', 'nav.sections.trainReconciliation'],
  },
  { key: 'developer', label: 'nav.areas.developer', icon: 'bi-code-slash', sections: ['nav.sections.developerTools'] },
  // QA automation: its own application (split out of Developer), platform tooling like it, so no subscription applies.
  { key: 'qa', label: 'nav.areas.qa', icon: 'bi-clipboard-check', sections: ['nav.sections.qaSetup', 'nav.sections.qaOperations', 'nav.sections.qaInsights'] },
];

/**
 * The application (area key) each persona-workspace section belongs to, so it follows that application's subscription exactly as
 * it did inside the application: the owner's matrimony pages need the matrimony subscription, the agent's wallet desk none.
 */
export const WORKSPACE_SECTION_APPS: Readonly<Partial<Record<NavSection, string>>> = {
  'nav.sections.ownerFranchise': 'franchise',
  'nav.sections.ownerMatrimony': 'matrimony',
  'nav.sections.ownerBestRide': 'admin',
  'nav.sections.agentMatrimony': 'matrimony',
  'nav.sections.agentWallet': 'wallet',
};

/** Sections shown whichever area is chosen (the daily landing page and what is waiting for the user). */
export const NAV_ALWAYS_VISIBLE_SECTIONS: ReadonlyArray<NavSection> = ['nav.sections.main'];

/** localStorage keys: the chosen area, the starred pages and the recently opened ones. */
export const NAV_AREA_STATE_KEY = 'sidebar.area';
export const NAV_FAVOURITES_STATE_KEY = 'sidebar.favourites';
export const NAV_RECENTS_STATE_KEY = 'sidebar.recents';

export const NAV_FAVOURITES_MAX = 10;
export const NAV_RECENTS_MAX = 5;
/** How many of the recents the sidebar lists (the rest are kept for when pages drop out). */
export const NAV_RECENTS_SHOWN = 3;
