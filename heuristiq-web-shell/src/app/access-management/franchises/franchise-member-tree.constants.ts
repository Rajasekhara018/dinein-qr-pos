import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { FranchiseMember } from './franchise.models';

/** Agents (or drivers) read per franchise and application each time a branch is opened or "show more" is pressed. */
export const MEMBER_TREE_PAGE_SIZE = 25;

/** Privileges that decide which tree a person sees: every franchise of the organization, or only their own. */
export const MEMBER_TREE_PRIVILEGES = {
  listAllFranchises: 'FRANCHISE_LIST',
  listAdminMembers: 'FRANCHISE_ADMIN_MEMBER_LIST',
  listOwnFranchises: 'FRANCHISE_MINE_LIST',
  listOwnMembers: 'FRANCHISE_MEMBER_LIST',
  /** The drivers of the driver application's franchises: the owner's own, and any franchise's for an administrator. */
  listOwnDrivers: 'FRANCHISE_DRIVER_LIST',
  listAdminDrivers: 'FRANCHISE_ADMIN_DRIVER_LIST',
} as const;

/** Columns of a franchise branch's agents; type, status and onboarded cells are drawn by the screen's templates. */
export const MEMBER_TREE_COLUMNS: readonly TableColumnSpec<FranchiseMember>[] = [
  { key: 'fullName', labelKey: 'accessManagement.franchiseMembers.name' },
  { key: 'email', labelKey: 'accessManagement.franchiseMembers.email' },
  { key: 'phone', labelKey: 'accessManagement.franchiseMembers.phone' },
  { key: 'memberType', labelKey: 'accessManagement.franchiseMembers.type' },
  { key: 'status', labelKey: 'accessManagement.franchiseMembers.status' },
  { key: 'onboardedAt', labelKey: 'accessManagement.franchiseMembers.onboarded' },
];
