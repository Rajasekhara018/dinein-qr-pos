import { AdminFormCodeBox, AdminFormOption, AdminFormOptionPanel } from './admin-form.types';

/** UAM path (under the UAM base URL) of the fixed application catalogue, with the session organization's `created` flags. */
export const APPLICATION_CATALOGUE_PATH = '/applications/catalogue';

/** UAM's application types (backend codes, shown as-is); the catalogue decides which one an application has. */
/** Privilege codes (any one) that show and save the organization's "open for public self-signup" switch: the organization update call. */
export const ORGANIZATION_SELF_SIGNUP_PRIVILEGES: readonly string[] = ['ORGANIZATION_UPDATE'];

export const APPLICATION_TYPE_OPTIONS: AdminFormOption[] = ['WEB', 'MOBILE', 'DESKTOP', 'API', 'MICROSERVICE', 'BATCH', 'INTERNAL', 'EXTERNAL']
  .map((v) => ({ value: v, label: v }));

const APPLICATION_KEYS = 'accessManagement.applications';

/** The information panel listing every application code of the catalogue on the application form. */
export const APPLICATION_CODE_PANEL: AdminFormOptionPanel = {
  title: `${APPLICATION_KEYS}.codePanelTitle`,
  note: `${APPLICATION_KEYS}.codePanelNote`,
  categoryLabel: `${APPLICATION_KEYS}.codePanelType`,
  flaggedBadge: `${APPLICATION_KEYS}.codePanelCreated`,
  empty: `${APPLICATION_KEYS}.catalogueUnavailable`,
};

/** The read-only "Application code" box with its copy button. */
export const APPLICATION_CODE_BOX: AdminFormCodeBox = {
  label: `${APPLICATION_KEYS}.codeBoxLabel`,
  copyLabel: `${APPLICATION_KEYS}.codeBoxCopy`,
  copied: `${APPLICATION_KEYS}.codeBoxCopied`,
  copyFailed: `${APPLICATION_KEYS}.codeBoxCopyFailed`,
};
