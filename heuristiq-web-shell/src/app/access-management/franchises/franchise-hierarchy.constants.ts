/** Route segment of the hierarchy page of one franchise (`franchises/:id/hierarchy`). */
export const HIERARCHY_ROUTE = 'hierarchy';

/** Audit key of the page; keys are queryable history, so never renamed or translated. */
export const FRANCHISE_HIERARCHY_SCREEN_KEY = 'access_management.franchise_hierarchy';

/** Privileges (exact names in privilege.json) behind the page. */
export const FRANCHISE_HIERARCHY_PRIVILEGES = {
  read: 'FRANCHISE_HIERARCHY_READ',
  setParent: 'FRANCHISE_PARENT_UPDATE',
} as const;

/** Status of a franchise that may be chosen as a parent. */
export const PARENT_ELIGIBLE_STATUS = 'ACTIVE';
