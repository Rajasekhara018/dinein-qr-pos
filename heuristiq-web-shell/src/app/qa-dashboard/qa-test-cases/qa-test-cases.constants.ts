import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { QaTestCaseResult } from '../qa-dashboard.models';

/** Columns of a test case's past results in its detail panel; the cells are drawn by the screen's templates. */
export const QA_TEST_CASE_HISTORY_COLUMNS: readonly TableColumnSpec<QaTestCaseResult>[] = [
  { key: 'release', labelKey: 'qaDashboard.columns.release' },
  { key: 'status', labelKey: 'qaDashboard.columns.status' },
  { key: 'duration', labelKey: 'qaDashboard.columns.duration' },
  { key: 'date', labelKey: 'qaDashboard.columns.date' },
];
