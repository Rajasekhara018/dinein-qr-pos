import { IMPORT_LOADED_LATEST_STATUSES } from '../org-structure.constants';
import { OrgImportBatch, OrgPosition, OrgUnit } from '../org-structure.models';

/** One page of departments below one parent; `shown` of its `nodes` are on the screen, the rest wait behind "Show more". */
export interface OrgUnitLevel {
  nodes: OrgUnitNode[];
  shown: number;
}

/** A department in the loaded tree. `level` is null until its sub-departments have been read. */
export interface OrgUnitNode {
  unit: OrgUnit;
  depth: number;
  open: boolean;
  loading: boolean;
  failed: boolean;
  level: OrgUnitLevel | null;
}

/** One line of the tree as shown: a department, the button for the next page of a level, or the state of a level being read. */
export type OrgUnitRow =
  | { kind: 'unit'; node: OrgUnitNode }
  | { kind: 'more'; level: OrgUnitLevel; depth: number; hidden: number }
  | { kind: 'loading'; depth: number }
  | { kind: 'failed'; node: OrgUnitNode; depth: number };

export function newLevel(units: OrgUnit[], depth: number, shown: number): OrgUnitLevel {
  return {
    nodes: units.map((unit) => ({ unit, depth, open: false, loading: false, failed: false, level: null })),
    shown,
  };
}

/** The lines to show for a tree: open departments are followed by their sub-departments, and a level with more departments than are shown ends with "show more". */
export function flattenLevel(level: OrgUnitLevel, depth = 0): OrgUnitRow[] {
  const rows: OrgUnitRow[] = [];
  level.nodes.slice(0, level.shown).forEach((node) => {
    rows.push({ kind: 'unit', node });
    if (!node.open) return;
    if (node.loading) rows.push({ kind: 'loading', depth: depth + 1 });
    else if (node.failed) rows.push({ kind: 'failed', node, depth: depth + 1 });
    else if (node.level) rows.push(...flattenLevel(node.level, depth + 1));
  });
  if (level.nodes.length > level.shown) rows.push({ kind: 'more', level, depth, hidden: level.nodes.length - level.shown });
  return rows;
}

/** Every department read so far (open or not), parents before their sub-departments. */
export function loadedUnits(level: OrgUnitLevel): OrgUnit[] {
  return level.nodes.flatMap((node) => [node.unit, ...(node.level ? loadedUnits(node.level) : [])]);
}

/** True when some departments are known to exist but have not been read: a level cut at its page size, or sub-departments not read yet. */
export function hasUnreadUnits(level: OrgUnitLevel): boolean {
  return level.nodes.length > level.shown || level.nodes.some((node) => (node.level ? hasUnreadUnits(node.level) : node.unit.childCount > 0 || node.failed));
}

export interface OrgDepartmentSummary {
  departments: number;
  withoutLadder: number;
  atLeast: boolean;
}

/** How many departments are loaded and how many of them have no ladder; "at least" when more exist than were read. */
export function summarizeDepartments(root: OrgUnitLevel): OrgDepartmentSummary {
  const units = loadedUnits(root);
  return { departments: units.length, withoutLadder: units.filter((unit) => !unit.ladderId).length, atLeast: hasUnreadUnits(root) };
}

export interface OrgEmployeeSummary {
  employees: number;
  departments: number;
  heads: number;
  atLeast: boolean;
}

/**
 * People placed in the organization (the headcount of a top-level department counts its sub-departments too), the departments that hold people,
 * and the departments with a head.
 */
export function summarizeEmployees(root: OrgUnitLevel): OrgEmployeeSummary {
  const units = loadedUnits(root);
  return {
    employees: root.nodes.reduce((sum, node) => sum + (node.unit.headcount ?? 0), 0),
    departments: units.filter((unit) => (unit.headcount ?? 0) > 0).length,
    heads: units.filter((unit) => !!unit.headEmployeeId).length,
    atLeast: hasUnreadUnits(root),
  };
}

/** The job titles of a ladder, the most senior first (then by name, so the order never jumps). */
export function sortPositionsByRank(positions: readonly OrgPosition[]): OrgPosition[] {
  return [...positions].sort((a, b) => b.rank - a.rank || a.name.localeCompare(b.name));
}

/** The most recent import that was applied, is being applied or waits for a decision (failed and cancelled ones say nothing about what is loaded). */
export function pickLatestImport(batches: readonly OrgImportBatch[]): OrgImportBatch | null {
  const candidates = batches.filter((batch) => IMPORT_LOADED_LATEST_STATUSES.has(batch.status));
  return candidates.reduce<OrgImportBatch | null>((best, batch) => (!best || (batch.createdAt ?? '') > (best.createdAt ?? '') ? batch : best), null);
}
