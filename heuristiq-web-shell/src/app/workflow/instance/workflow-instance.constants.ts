import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { WorkflowVariableRow } from '../workflow.models';

/** Columns of the instance's variables table. */
export const WORKFLOW_VARIABLE_COLUMNS: readonly TableColumnSpec<WorkflowVariableRow>[] = [
  { key: 'key', labelKey: 'workflow.instance.columns.name', className: 'wf-key' },
  { key: 'value', labelKey: 'workflow.instance.columns.value', className: 'wf-val' },
];
