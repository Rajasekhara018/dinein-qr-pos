import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { WorkflowReport } from '../workflow.models';

export type WorkflowReportStep = WorkflowReport['steps'][number];
export type WorkflowReportReviewer = WorkflowReport['reviewers'][number];
export type WorkflowReportOpenStep = WorkflowReport['open'][number];

const NUMBER = 'text-end';

/** Columns of the three report tables: translation keys for the headings, cells are drawn by the screen's templates. */
export const WORKFLOW_REPORT_STEP_COLUMNS: readonly TableColumnSpec<WorkflowReportStep>[] = [
  { key: 'step', labelKey: 'workflow.reports.steps.step' },
  { key: 'process', labelKey: 'workflow.tasks.columns.process' },
  { key: 'completed', labelKey: 'workflow.reports.steps.completed', className: NUMBER },
  { key: 'avg', labelKey: 'workflow.reports.steps.avg', className: NUMBER },
  { key: 'max', labelKey: 'workflow.reports.steps.max', className: NUMBER },
  { key: 'sla', labelKey: 'workflow.reports.steps.sla', className: NUMBER },
  { key: 'late', labelKey: 'workflow.reports.steps.late', className: NUMBER },
];

export const WORKFLOW_REPORT_REVIEWER_COLUMNS: readonly TableColumnSpec<WorkflowReportReviewer>[] = [
  { key: 'person', labelKey: 'workflow.reports.people.person' },
  { key: 'completed', labelKey: 'workflow.reports.steps.completed', className: NUMBER },
  { key: 'approved', labelKey: 'workflow.reports.cards.approved', className: NUMBER },
  { key: 'rejected', labelKey: 'workflow.reports.cards.rejected', className: NUMBER },
  { key: 'avg', labelKey: 'workflow.reports.steps.avg', className: NUMBER },
  { key: 'late', labelKey: 'workflow.reports.people.late', className: NUMBER },
];

export const WORKFLOW_REPORT_OPEN_COLUMNS: readonly TableColumnSpec<WorkflowReportOpenStep>[] = [
  { key: 'step', labelKey: 'workflow.reports.steps.step' },
  { key: 'process', labelKey: 'workflow.tasks.columns.process' },
  { key: 'open', labelKey: 'workflow.reports.waiting.open', className: NUMBER },
  { key: 'overdue', labelKey: 'workflow.reports.waiting.overdue', className: NUMBER },
  { key: 'oldest', labelKey: 'workflow.reports.waiting.oldest', className: NUMBER },
];
