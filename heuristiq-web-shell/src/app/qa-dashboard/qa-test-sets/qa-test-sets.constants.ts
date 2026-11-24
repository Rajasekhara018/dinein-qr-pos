import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { QaTestSet } from '../qa-dashboard.models';

/** Columns of the test sets list; the cells are drawn by the screen's templates. */
export const QA_TEST_SET_COLUMNS: readonly TableColumnSpec<QaTestSet>[] = [
  { key: 'name', labelKey: 'qaDashboard.columns.testSet' },
  { key: 'suite', labelKey: 'qaDashboard.columns.suite' },
  { key: 'tagExpression', labelKey: 'qaDashboard.columns.tagExpression' },
  { key: 'description', labelKey: 'qaDashboard.columns.description' },
  { key: 'updated', labelKey: 'qaDashboard.columns.updated' },
];
