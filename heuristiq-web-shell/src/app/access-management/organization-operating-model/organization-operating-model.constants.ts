import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { OperatingModel, OperatingModelChange } from './organization-operating-model.types';

/** Privilege codes (names in privilege.json) behind the operating-model section of an organization. */
export const ORGANIZATION_OPERATING_MODEL_PRIVILEGES = {
  read: 'ORGANIZATION_OPERATING_MODEL_READ',
  update: 'ORGANIZATION_OPERATING_MODEL_UPDATE',
} as const;

/** UAM resource path of an organization (the operating model hangs below it). */
export const organizationPath = (tenantId: string): string => `/organizations/${tenantId}`;

/** The last segment of the operating-model path. */
export const OPERATING_MODEL_SEGMENT = 'operating-model';

export const DIRECT: OperatingModel = 'DIRECT';
export const FRANCHISE: OperatingModel = 'FRANCHISE';
export const HYBRID: OperatingModel = 'HYBRID';

/** Every model, in the order the switch picker offers them. */
export const OPERATING_MODELS: readonly OperatingModel[] = [DIRECT, FRANCHISE, HYBRID];

/** Translation key of each model's label; an unknown code is shown as it is. */
export const OPERATING_MODEL_LABEL_KEYS: Record<OperatingModel, string> = {
  DIRECT: 'accessManagement.organizations.operatingModel.models.DIRECT',
  FRANCHISE: 'accessManagement.organizations.operatingModel.models.FRANCHISE',
  HYBRID: 'accessManagement.organizations.operatingModel.models.HYBRID',
};

/** Translation key of each model's one-line explanation. */
export const OPERATING_MODEL_DESCRIPTION_KEYS: Record<OperatingModel, string> = {
  DIRECT: 'accessManagement.organizations.operatingModel.descriptions.DIRECT',
  FRANCHISE: 'accessManagement.organizations.operatingModel.descriptions.FRANCHISE',
  HYBRID: 'accessManagement.organizations.operatingModel.descriptions.HYBRID',
};

/** Columns of the scheduled switches list: translation keys of the headings; cells are drawn by the screen. */
export const OPERATING_MODEL_SCHEDULE_COLUMNS: readonly TableColumnSpec<OperatingModelChange>[] = [
  { key: 'model', labelKey: 'accessManagement.organizations.operatingModel.model' },
  { key: 'effectiveFrom', labelKey: 'accessManagement.organizations.operatingModel.effectiveFrom' },
  { key: 'state', labelKey: 'accessManagement.organizations.operatingModel.state' },
];
