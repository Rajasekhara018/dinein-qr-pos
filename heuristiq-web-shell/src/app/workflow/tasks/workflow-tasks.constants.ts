import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { WorkflowTask } from '../workflow.models';

/** Columns of the open tasks list: translation keys for the headings, cells are drawn by the screen's templates. */
export const WORKFLOW_TASK_COLUMNS: readonly TableColumnSpec<WorkflowTask>[] = [
  { key: 'step', labelKey: 'workflow.tasks.columns.step' },
  { key: 'assignee', labelKey: 'workflow.tasks.columns.assignee' },
  { key: 'process', labelKey: 'workflow.tasks.columns.process' },
  { key: 'subject', labelKey: 'workflow.tasks.columns.subject' },
  { key: 'instance', labelKey: 'workflow.tasks.columns.instance' },
  { key: 'created', labelKey: 'workflow.tasks.columns.created' },
  { key: 'due', labelKey: 'workflow.tasks.columns.due' },
];
