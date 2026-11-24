/** Screen permission key (mapped to privileges in hq-shared-lib's privilege map) and audit screen key of the members screen. */
export const FRANCHISE_MEMBERS_PERMISSION = 'view_franchise_members';

/** Stable audit key: queryable history, never renamed. */
export const FRANCHISE_MEMBERS_SCREEN_KEY = 'franchise.members';

/** Privilege codes (exact names in privilege.json) behind the members screen. */
export const FRANCHISE_MEMBERS_PRIVILEGES = {
  listMembers: 'FRANCHISE_MEMBER_LIST',
  listOwnFranchises: 'FRANCHISE_MINE_LIST',
  addAgent: 'FRANCHISE_AGENT_CREATE',
  removeAgent: 'FRANCHISE_AGENT_DELETE',
  listAdminMembers: 'FRANCHISE_ADMIN_MEMBER_LIST',
  readFranchise: 'FRANCHISE_READ',
  adminAddAgent: 'FRANCHISE_ADMIN_AGENT_CREATE',
  adminRemoveAgent: 'FRANCHISE_ADMIN_AGENT_DELETE',
  listDrivers: 'FRANCHISE_DRIVER_LIST',
  listAdminDrivers: 'FRANCHISE_ADMIN_DRIVER_LIST',
} as const;

/** Width of the administrator's "add an agent" dialog, and the id of its title (for aria-labelledby). */
export const ADD_AGENT_DIALOG_WIDTH = '560px';
export const ADD_AGENT_DIALOG_TITLE_ID = 'franchise-add-agent-title';

/** Rows per page of the member list. */
export const FRANCHISE_MEMBERS_PAGE_SIZE = 20;

/** Translation keys of the member types and statuses; a code with no key is shown as it is. */
export const MEMBER_TYPE_LABEL_KEYS: Readonly<Record<string, string>> = {
  AGENT: 'accessManagement.franchiseMembers.types.AGENT',
  OWNER: 'accessManagement.franchiseMembers.types.OWNER',
  DRIVER: 'accessManagement.franchiseMembers.types.DRIVER',
};

export const MEMBER_STATUS_LABEL_KEYS: Readonly<Record<string, string>> = {
  ACTIVE: 'accessManagement.franchiseMembers.statuses.ACTIVE',
  SUSPENDED: 'accessManagement.franchiseMembers.statuses.SUSPENDED',
  REMOVED: 'accessManagement.franchiseMembers.statuses.REMOVED',
};

/** API error status to the translation key of the message shown on this screen. */
export const MEMBER_ERROR_KEYS: Readonly<Record<number, string>> = {
  403: 'accessManagement.franchiseMembers.errors.forbidden',
  404: 'accessManagement.franchiseMembers.errors.notFound',
  503: 'accessManagement.franchiseMembers.errors.unavailable',
};

export const MEMBER_FALLBACK_ERROR_KEY = 'accessManagement.franchiseMembers.errors.generic';
