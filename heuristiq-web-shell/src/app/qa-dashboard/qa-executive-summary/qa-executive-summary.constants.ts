import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { QaModuleTotals } from '../qa-dashboard.models';

/** Columns of the per-module results table of the executive summary (all plain text). */
export const QA_EXECUTIVE_MODULE_COLUMNS: readonly TableColumnSpec<QaModuleTotals>[] = [
  { key: 'module', labelKey: 'qaDashboard.columns.module' },
  { key: 'passed', labelKey: 'qaDashboard.status.PASSED', className: 'qa-num' },
  { key: 'failed', labelKey: 'qaDashboard.status.FAILED', className: 'qa-num' },
  { key: 'total', labelKey: 'qaDashboard.overview.totalUnique', className: 'qa-num' },
];
