import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { QaFailingCase } from '../qa-dashboard.models';

/** Columns of the "fails most" table; module and the counts are plain text, the rest are drawn by the screen's templates. */
export const QA_TOP_FAILING_COLUMNS: readonly TableColumnSpec<QaFailingCase>[] = [
  { key: 'tcId', labelKey: 'qaDashboard.columns.tcId' },
  { key: 'name', labelKey: 'qaDashboard.columns.testCase' },
  { key: 'module', labelKey: 'qaDashboard.columns.module' },
  { key: 'failCount', labelKey: 'qaDashboard.columns.failCount', className: 'qa-num' },
  { key: 'runCount', labelKey: 'qaDashboard.columns.iteration', className: 'qa-num' },
  { key: 'category', labelKey: 'qaDashboard.columns.category' },
  { key: 'message', labelKey: 'qaDashboard.failures.lastMessage' },
  { key: 'date', labelKey: 'qaDashboard.columns.date' },
];
