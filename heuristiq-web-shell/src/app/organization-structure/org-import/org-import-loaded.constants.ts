import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { OrgPosition, OrgUnit } from '../org-structure.models';
import { OrgUnitRow } from './org-import-loaded.helpers';

/** Step 1: the job titles of one ladder, most senior first. */
export const ORG_LOADED_POSITION_COLUMNS: readonly TableColumnSpec<OrgPosition>[] = [
  { key: 'name', labelKey: 'orgStructure.import.ladders.columns.position', className: 'loaded__wide' },
  { key: 'rank', labelKey: 'orgStructure.import.ladders.columns.rank', className: 'loaded__num' },
  { key: 'managerial', labelKey: 'orgStructure.import.ladders.columns.managerial', className: 'loaded__narrow' },
];

/** Step 2: the department tree; every cell is drawn by the screen's templates (a row may also be a loading / failed / more strip). */
export const ORG_LOADED_TREE_COLUMNS: readonly TableColumnSpec<OrgUnitRow>[] = [
  { key: 'code', labelKey: 'orgStructure.chart.columns.code', className: 'loaded__code' },
  { key: 'name', labelKey: 'orgStructure.chart.columns.name', className: 'loaded__wide' },
  { key: 'type', labelKey: 'orgStructure.import.loaded.columns.type' },
  { key: 'ladder', labelKey: 'orgStructure.import.departments.columns.ladder' },
  { key: 'head', labelKey: 'orgStructure.import.loaded.columns.head' },
  { key: 'headcount', labelKey: 'orgStructure.import.loaded.columns.headcount', className: 'loaded__num' },
];

/** Step 3: the people, department by department. */
export const ORG_LOADED_EMPLOYEE_COLUMNS: readonly TableColumnSpec<OrgUnit>[] = [
  { key: 'department', labelKey: 'orgStructure.import.loaded.columns.department', className: 'loaded__wide', render: (unit) => `${unit.name} (${unit.code})` },
  { key: 'headName', labelKey: 'orgStructure.import.loaded.columns.head' },
  { key: 'headcount', labelKey: 'orgStructure.import.loaded.columns.headcount', className: 'loaded__num' },
];
