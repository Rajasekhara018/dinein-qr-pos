import { TenantMemberView } from '../../shared/models/uam.models';
import { TableColumnSpec } from '../../shared/table-columns.helpers';

/** Privilege codes (names in privilege.json) behind each action of an organization's Members section. */
export const ORGANIZATION_MEMBER_PRIVILEGES = {
  list: 'ORGANIZATION_MEMBER_LIST',
  add: 'ORGANIZATION_MEMBER_ADD',
  update: 'ORGANIZATION_MEMBER_UPDATE',
} as const;

/** UAM resource path of an organization's members. */
export const organizationMembersPath = (tenantId: string): string => `/organizations/${tenantId}/members`;

export const MEMBERS_PAGE_SIZE = 10;
/** Page sizes offered in the table footer. */
export const MEMBERS_PAGE_SIZE_OPTIONS: readonly number[] = [10, 25, 50];

/** Translation key of each user type code UAM sends; an unknown code is shown as it is. */
export const MEMBER_USER_TYPE_KEYS: Readonly<Record<string, string>> = {
  SUPER_ADMIN: 'accessManagement.organizations.members.userTypes.SUPER_ADMIN',
  ADMIN: 'accessManagement.organizations.members.userTypes.ADMIN',
  OPS: 'accessManagement.organizations.members.userTypes.OPS',
  BUSINESS: 'accessManagement.organizations.members.userTypes.BUSINESS',
  SECURITY: 'accessManagement.organizations.members.userTypes.SECURITY',
  CUSTOMER: 'accessManagement.organizations.members.userTypes.CUSTOMER',
  DRIVER: 'accessManagement.organizations.members.userTypes.DRIVER',
  VENDOR: 'accessManagement.organizations.members.userTypes.VENDOR',
};

/** Columns of the organization's members list: translation keys of the headings; cells are drawn by the screen. */
export const ORGANIZATION_MEMBER_COLUMNS: readonly TableColumnSpec<TenantMemberView>[] = [
  { key: 'member', labelKey: 'accessManagement.organizations.members.member' },
  { key: 'userType', labelKey: 'accessManagement.organizations.members.userType' },
  { key: 'status', labelKey: 'accessManagement.organizations.members.status' },
  { key: 'joined', labelKey: 'accessManagement.organizations.members.joined' },
];

/**
 * The account columns of the "Add people" picker and the search-index slot each one is stored in (Rider_UAM's
 * UamUser.getSearchIndexFields(): text1 = userName, text6 = displayName, text2 = email). The picker searches and filters
 * on these columns only.
 */
export const MEMBER_PICKER_SLOTS = { userName: 'text1', displayName: 'text6', email: 'text2' } as const;

/** A column key of the "Add people" picker. */
export type MemberPickerField = keyof typeof MEMBER_PICKER_SLOTS;

/** The picker's columns, in the order of its search chips and condition choices. */
export const MEMBER_PICKER_FIELDS: readonly MemberPickerField[] = ['userName', 'displayName', 'email'];

/**
 * Condition operators the search index supports on text slots (Rider_SearchAPI: EQ, NE and LIKE, a case-sensitive
 * "contains" on the slot's keyword).
 */
export const MEMBER_PICKER_OPERATORS = ['EQ', 'NE', 'LIKE'] as const;
