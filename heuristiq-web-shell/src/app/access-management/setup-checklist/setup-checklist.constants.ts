import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { SetupApplication, SetupApplicationItem, SetupPlacelessApplication } from './setup-checklist.models';

/** Screen permission key (mapped to privileges in hq-shared-lib's privilege map) of the organization set-up checklist. */
export const SETUP_CHECKLIST_PERMISSION = 'view_setup_checklist';

/** Stable audit key: queryable history, never renamed. */
export const SETUP_CHECKLIST_SCREEN_KEY = 'access_management.setup_checklist';

/** The route segment under access management. */
export const SETUP_CHECKLIST_ROUTE = 'setup-checklist';

/** The franchise service path of the checklist (after environment.franchiseApiUrl). */
export const SETUP_CHECKLIST_PATH = '/setup-checklist';

/** Privilege codes (exact names in privilege.json) behind the screen. */
export const SETUP_CHECKLIST_PRIVILEGES = {
  read: 'FRANCHISE_SETUP_CHECKLIST_READ',
  /** The "set place" action opens the franchise's page, where the place of an application is changed. */
  setPlace: 'FRANCHISE_APPLICATION_UPDATE',
  /** The cities screen, where a city is linked to its place. */
  cities: 'CONFIG_CITY_UPDATE',
} as const;

/** The franchise page a "set place" action opens: `/access-management/franchises/{id}/edit`. */
export const SETUP_CHECKLIST_FRANCHISE_EDIT_PATH = ['/access-management', 'franchises'] as const;
export const SETUP_CHECKLIST_FRANCHISE_EDIT_SEGMENT = 'edit';

/** Labels of the operating models (the keys the organization screen already uses); an unknown model shows its code. */
export const SETUP_CHECKLIST_MODEL_LABEL_KEYS: Readonly<Record<string, string>> = {
  DIRECT: 'accessManagement.organizations.operatingModel.models.DIRECT',
  FRANCHISE: 'accessManagement.organizations.operatingModel.models.FRANCHISE',
  HYBRID: 'accessManagement.organizations.operatingModel.models.HYBRID',
};

/** Where each missing item is set up. */
export const SETUP_CHECKLIST_LINKS = {
  operatingModel: '/access-management/organizations',
  places: '/access-management/franchise-locations',
  commissionPlan: '/admin/franchise-admin/commission-plans',
  packageTemplate: '/access-management/franchise-packages',
  franchisePlan: '/access-management/franchise-plans',
  cities: '/admin/config-settings/cities',
} as const;

/** The state labels of one item. */
export const SETUP_CHECKLIST_STATE_LABEL_KEYS = {
  done: 'accessManagement.setupChecklist.state.done',
  missing: 'accessManagement.setupChecklist.state.missing',
  notNeeded: 'accessManagement.setupChecklist.state.notNeeded',
} as const;

/** The application items the checklist shows, in order (one column each). */
export const SETUP_CHECKLIST_ITEMS: readonly SetupApplicationItem[] = ['commissionPlan', 'packageTemplate', 'franchisePlan'];

/** Columns of the per-application checklist: translation keys of the headings; cells are drawn by the screen. */
export const SETUP_CHECKLIST_APPLICATION_COLUMNS: readonly TableColumnSpec<SetupApplication>[] = [
  { key: 'applicationCode', labelKey: 'accessManagement.setupChecklist.application' },
  ...SETUP_CHECKLIST_ITEMS.map((item) => ({ key: item, labelKey: `accessManagement.setupChecklist.${item}` })),
];

/** Columns of the franchise applications that cover no place. */
export const SETUP_CHECKLIST_PLACELESS_COLUMNS: readonly TableColumnSpec<SetupPlacelessApplication>[] = [
  { key: 'franchiseCode', labelKey: 'accessManagement.setupChecklist.placeless.franchise' },
  { key: 'applicationCode', labelKey: 'accessManagement.setupChecklist.application' },
  { key: 'effect', labelKey: 'accessManagement.setupChecklist.placeless.effect' },
  { key: 'action', labelKey: 'accessManagement.setupChecklist.placeless.action' },
];
