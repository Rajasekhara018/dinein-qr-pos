/** Screen permission key (mapped to privileges in hq-shared-lib's privilege map) of the demand planning screen. */
export const FRANCHISE_DEMAND_PERMISSION = 'view_franchise_demand';

/** Stable audit key: queryable history, never renamed. */
export const FRANCHISE_DEMAND_SCREEN_KEY = 'franchise.demand';

/** The route segment under access management. */
export const FRANCHISE_DEMAND_ROUTE = 'franchise-demand';

/** Privilege codes (exact names in privilege.json) behind the screen. */
export const FRANCHISE_DEMAND_PRIVILEGES = {
  summary: 'FRANCHISE_DEMAND_SUMMARY_LIST',
  /** Drilling into a place shows the places under it (the same call with a parent), so it needs no other privilege. */
} as const;

/** Place types with no places inside them (the lowest level): they offer no drill-down. */
export const FRANCHISE_DEMAND_LEAF_TYPES: readonly string[] = ['WARD', 'VILLAGE'];

/** Rows per page. */
export const FRANCHISE_DEMAND_PAGE_SIZE = 20;
