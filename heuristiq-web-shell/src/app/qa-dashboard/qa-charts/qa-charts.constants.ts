import { HqStatusTone } from 'hq-shared-lib';

import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { QA_STATUSES, QA_STATUS_KEYS } from '../qa-dashboard.constants';
import { QaStatus } from '../qa-dashboard.models';
import { QaDonutTableRow, QaLineTableRow, QaReleaseTableRow } from './qa-charts.models';

/** Columns of the status split's table view. */
export const QA_DONUT_TABLE_COLUMNS: readonly TableColumnSpec<QaDonutTableRow>[] = [
  { key: 'status', labelKey: 'qaDashboard.columns.status' },
  { key: 'count', labelKey: 'qaDashboard.columns.count', className: 'qa-num' },
];

/** Columns of the release chart's table view: the release, then one count column per status. */
export const QA_RELEASE_TABLE_COLUMNS: readonly TableColumnSpec<QaReleaseTableRow>[] = [
  { key: 'release', labelKey: 'qaDashboard.columns.release' },
  ...QA_STATUSES.map((status) => ({ key: status, labelKey: QA_STATUS_KEYS[status], className: 'qa-num' })),
];

/** First column of the line chart's table view; one column per series follows it. */
export const QA_LINE_TABLE_LABEL_COLUMN: TableColumnSpec<QaLineTableRow> = { key: 'label', labelKey: 'qaDashboard.columns.date' };

/** Status tone of each test status in the status split (a state, so it wears the reserved status colours with its name). */
export const QA_STATUS_TONES: Readonly<Record<QaStatus, HqStatusTone>> = {
  PASSED: 'success',
  FAILED: 'danger',
  SKIPPED: 'neutral',
};

/**
 * Categorical slot (1-based, fixed order of the shared chart palette) of each status when it is a series of a stacked bar or a
 * line. The first three slots are the ones validated for every pair, so the three statuses stay apart for colour-blind readers;
 * the legend names each one, so colour never carries the meaning alone. The same status keeps its slot in every chart.
 */
export const QA_STATUS_SLOTS: Readonly<Record<QaStatus, number>> = {
  PASSED: 3,
  FAILED: 2,
  SKIPPED: 1,
};

/** Slot of the single growth-of-volume line (a single series: the card title names it, no legend). */
export const QA_VOLUME_SLOT = 1;

/** Height of the QA charts in the overview cards (px). */
export const QA_CHART_HEIGHT = 220;
