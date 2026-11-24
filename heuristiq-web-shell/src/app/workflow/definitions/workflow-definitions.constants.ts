import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { ProcessDefinitionSummary } from '../workflow.models';

/** Columns of the process definitions list: translation keys for the headings, cells are drawn by the screen's templates. */
export const WORKFLOW_DEFINITION_COLUMNS: readonly TableColumnSpec<ProcessDefinitionSummary>[] = [
  { key: 'key', labelKey: 'workflow.definitions.columns.key' },
  { key: 'name', labelKey: 'workflow.definitions.columns.name' },
  { key: 'version', labelKey: 'workflow.definitions.columns.version' },
  { key: 'deployedAt', labelKey: 'workflow.definitions.columns.deployed' },
  { key: 'status', labelKey: 'workflow.definitions.columns.status' },
];
