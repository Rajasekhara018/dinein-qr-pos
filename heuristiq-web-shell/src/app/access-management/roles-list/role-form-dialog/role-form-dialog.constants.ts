import { Permission } from '../../../shared/models/uam.models';
import { TableColumnSpec } from '../../../shared/table-columns.helpers';

/** Constants for the role create/edit form. */

export const MODULE_OPTIONS: readonly string[] = ['ADMINISTRATION', 'SECURITY', 'BUSINESS', 'REPORTING', 'CONFIGURATION', 'INTEGRATION', 'MASTER_DATA', 'WORKFLOW', 'DASHBOARD', 'SETTINGS'];
export const RESOURCE_OPTIONS: readonly string[] = ['MASTER', 'TRANSACTION', 'CONFIGURATION', 'REPORT', 'DASHBOARD', 'WORKFLOW', 'API', 'DOCUMENT', 'FILE', 'SERVICE'];

/** Rows-per-page choices for the permissions table, and the one it starts on. */
export const PERMISSION_PAGE_SIZES: readonly number[] = [10, 25, 50, 100];
export const DEFAULT_PERMISSION_PAGE_SIZE = 10;

/** Page size used when fetching the permission catalogue from UAM (it is paged server-side). */
export const PERMISSION_FETCH_SIZE = 200;
/** Safety stop for the fetch loop, in case a backend ever reports a runaway page count. */
export const PERMISSION_FETCH_MAX_PAGES = 50;

/** How many numbered page buttons to show around the current page. */
export const PERMISSION_PAGER_WINDOW = 2;

/** Privileges (privilege.json names) that allow saving a new / an existing role; any one is enough. */
export const ROLE_CREATE_PRIVILEGES: readonly string[] = ['ROLE_CREATE', 'UAM_ROLES_MANAGE'];
export const ROLE_UPDATE_PRIVILEGES: readonly string[] = ['ROLE_UPDATE', 'UAM_ROLES_MANAGE'];

/** Columns of the permission picker: translation keys of the headings; the select column is drawn by the form. */
export const PERMISSION_COLUMNS: readonly TableColumnSpec<Permission>[] = [
  { key: 'select', labelKey: '', className: 'perm-col-select' },
  { key: 'code', labelKey: 'accessManagement.roles.fields.code' },
  { key: 'module', labelKey: 'accessManagement.roles.form.module' },
  { key: 'resource', labelKey: 'accessManagement.roles.form.resource' },
  { key: 'action', labelKey: 'accessManagement.roles.form.action' },
  { key: 'description', labelKey: 'accessManagement.roles.fields.description', render: (p) => p.displayName || p.description || '—' },
];
