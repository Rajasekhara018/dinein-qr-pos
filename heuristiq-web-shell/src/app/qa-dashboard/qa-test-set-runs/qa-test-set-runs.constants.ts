import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { QaTestSetExecution } from '../qa-dashboard.models';

/** Columns of the test set execution summary; the cells are drawn by the screen's templates (from the set's latest run). */
export const QA_TEST_SET_RUN_COLUMNS: readonly TableColumnSpec<QaTestSetExecution>[] = [
  { key: 'name', labelKey: 'qaDashboard.columns.testSet' },
  { key: 'suite', labelKey: 'qaDashboard.columns.suite' },
  { key: 'release', labelKey: 'qaDashboard.columns.release' },
  { key: 'environment', labelKey: 'qaDashboard.columns.environment' },
  { key: 'status', labelKey: 'qaDashboard.columns.status' },
  { key: 'passed', labelKey: 'qaDashboard.status.PASSED', className: 'qa-num' },
  { key: 'failed', labelKey: 'qaDashboard.status.FAILED', className: 'qa-num' },
  { key: 'skipped', labelKey: 'qaDashboard.status.SKIPPED', className: 'qa-num' },
  { key: 'duration', labelKey: 'qaDashboard.columns.duration' },
  { key: 'date', labelKey: 'qaDashboard.columns.date' },
];
