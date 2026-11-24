import { TableColumnSpec } from '../shared/table-columns.helpers';
import { RegistryInstance } from './service-registry.models';

/** Constants for the Service Registry screen. */

/** How often the screen re-reads the registry while auto-refresh is on. */
export const REGISTRY_REFRESH_MS = 30_000;

/** Eureka instance statuses the screen can filter by, in display order (values are never translated). */
export const REGISTRY_STATUSES: readonly string[] = ['UP', 'DOWN', 'STARTING', 'OUT_OF_SERVICE', 'UNKNOWN'];

export type StatusTone = 'success' | 'danger' | 'warning' | 'neutral';

/** Status -> pill colour. Anything not listed is neutral. */
export const REGISTRY_STATUS_TONE: Record<string, StatusTone> = {
  UP: 'success',
  DOWN: 'danger',
  STARTING: 'warning',
  OUT_OF_SERVICE: 'warning',
  UNKNOWN: 'neutral',
};

/** Translation key for a status label: serviceRegistry.statuses.<status>. */
export const REGISTRY_STATUS_KEY_PREFIX = 'serviceRegistry.statuses.';

/** Columns of one service's instance table: translation keys for the headings; the width classes keep every service's table aligned. */
export const REGISTRY_INSTANCE_COLUMNS: readonly TableColumnSpec<RegistryInstance>[] = [
  { key: 'instance', labelKey: 'serviceRegistry.columns.instance', className: 'reg-col-instance' },
  { key: 'address', labelKey: 'serviceRegistry.columns.address', className: 'reg-col-address' },
  { key: 'status', labelKey: 'serviceRegistry.columns.status', className: 'reg-col-status' },
  { key: 'registered', labelKey: 'serviceRegistry.columns.registered', className: 'reg-col-registered' },
  { key: 'lastRenewed', labelKey: 'serviceRegistry.columns.lastRenewed', className: 'reg-col-renewed' },
  { key: 'links', labelKey: '', className: 'reg-col-links' },
];
