import { AdminFormConfig, AdminFormOption } from '../admin-form.types';
import { APPLICATION_CRUD_CONFIG, CLIENT_CRUD_CONFIG, NOTIFICATION_TEMPLATE_CRUD_CONFIG, ORGANIZATION_CRUD_CONFIG, SECURITY_POLICY_CRUD_CONFIG } from '../access-management-configs';
import { USER_FORM_CONFIG } from '../users-list/users-list.constants';

/** The choices of a field that has none (one shared empty list, so the searchable select sees the same array each time). */
export const ADMIN_FORM_NO_OPTIONS: readonly AdminFormOption[] = [];

/** One create / edit page: the route segment it lives under, its form, and who may save it. */
export interface AdminFormPageConfig {
  /** First route segment under /access-management, e.g. "users" gives users/new and users/:id/edit. */
  path: string;
  config: AdminFormConfig;
  /** Translation key of the plural name shown in the breadcrumb. */
  listLabel: string;
  /** Privilege codes (any one is enough) behind the save button when creating / editing. */
  createPrivileges: readonly string[];
  updatePrivileges: readonly string[];
}

export const ADMIN_FORM_PAGES: readonly AdminFormPageConfig[] = [
  { path: 'users', config: USER_FORM_CONFIG, listLabel: 'nav.items.users', createPrivileges: ['USER_CREATE', 'UAM_USERS_MANAGE'], updatePrivileges: ['USER_UPDATE', 'UAM_USERS_MANAGE'] },
  { path: 'organizations', config: ORGANIZATION_CRUD_CONFIG, listLabel: 'nav.items.organizations', createPrivileges: ['ORGANIZATION_CREATE'], updatePrivileges: ['ORGANIZATION_UPDATE'] },
  { path: 'applications', config: APPLICATION_CRUD_CONFIG, listLabel: 'nav.items.applications', createPrivileges: ['APPLICATION_CREATE'], updatePrivileges: ['APPLICATION_UPDATE'] },
  { path: 'clients', config: CLIENT_CRUD_CONFIG, listLabel: 'nav.items.apiClients', createPrivileges: ['CLIENT_CREATE'], updatePrivileges: ['CLIENT_UPDATE'] },
  { path: 'security-policies', config: SECURITY_POLICY_CRUD_CONFIG, listLabel: 'nav.items.securityPolicies', createPrivileges: ['SECURITY_POLICY_CREATE'], updatePrivileges: ['SECURITY_POLICY_UPDATE'] },
  { path: 'notification-templates', config: NOTIFICATION_TEMPLATE_CRUD_CONFIG, listLabel: 'nav.items.notificationTemplates', createPrivileges: ['NOTIFICATION_TEMPLATE_CREATE'], updatePrivileges: ['NOTIFICATION_TEMPLATE_UPDATE'] },
];

/** Route data key that tells the page which entry of {@link ADMIN_FORM_PAGES} it renders. */
export const ADMIN_FORM_PAGE_DATA_KEY = 'adminFormPath';

/** UAM resource path of an organization's administrator. */
export const organizationAdminPath = (tenantId: string): string => `/organizations/${tenantId}/admin`;

/** What UAM returns after setting up an organization's administrator. */
export interface OrganizationAdminResult {
  userId: string;
  email: string;
  roleCode: string;
  accountCreated: boolean;
  /** One-time password; only when the account was created by this call. */
  temporaryPassword: string | null;
}

export const ACCESS_MANAGEMENT_BASE_PATH = '/access-management';
export const ADMIN_FORM_NEW_SEGMENT = 'new';
export const ADMIN_FORM_EDIT_SEGMENT = 'edit';
