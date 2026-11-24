import { TenantApplicationStatus, TenantApplicationView } from '../../shared/models/uam.models';
import { TableColumnSpec } from '../../shared/table-columns.helpers';

/** Privilege codes (names in privilege.json) behind each action of an organization's Applications section. */
export const ORGANIZATION_APPLICATION_PRIVILEGES = {
  list: 'ORGANIZATION_APPLICATION_LIST',
  subscribe: 'ORGANIZATION_APPLICATION_SUBSCRIBE',
  update: 'ORGANIZATION_APPLICATION_UPDATE',
  cancel: 'ORGANIZATION_APPLICATION_CANCEL',
} as const;

/** UAM resource path of an organization's applications. */
export const organizationApplicationsPath = (tenantId: string): string => `/organizations/${tenantId}/applications`;

/** Applications fetched to choose from; far more than a platform registers. */
export const APPLICATION_CHOICES_LIMIT = 200;

export const SUSPENDED: TenantApplicationStatus = 'SUSPENDED';
export const ACTIVE: TenantApplicationStatus = 'ACTIVE';
export const CANCELLED: TenantApplicationStatus = 'CANCELLED';

/** Columns of the organization's applications list: translation keys of the headings; cells are drawn by the screen. */
export const ORGANIZATION_APPLICATION_COLUMNS: readonly TableColumnSpec<TenantApplicationView>[] = [
  { key: 'application', labelKey: 'accessManagement.organizations.applications.application' },
  { key: 'status', labelKey: 'accessManagement.organizations.applications.status' },
  { key: 'maxUsers', labelKey: 'accessManagement.organizations.applications.maxUsers' },
  { key: 'validUntil', labelKey: 'accessManagement.organizations.applications.validUntil' },
];
