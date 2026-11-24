import { OrgImportBatch, OrgPosition, OrgUnit } from '../org-structure.models';
import {
  flattenLevel, hasUnreadUnits, loadedUnits, newLevel, pickLatestImport, sortPositionsByRank, summarizeDepartments, summarizeEmployees,
} from './org-import-loaded.helpers';

const unit = (code: string, over: Partial<OrgUnit> = {}): OrgUnit =>
  ({ id: code, parentId: null, code, name: code + ' name', unitType: null, ladderId: 'l1', headEmployeeId: null, status: 'ACTIVE', childCount: 0, headcount: 0, ...over });
const position = (name: string, rank: number): OrgPosition => ({ id: name, ladderId: 'l1', name, rank, managerial: false });
const batch = (status: string, createdAt: string): OrgImportBatch =>
  ({ id: status + createdAt, status, createdAt } as unknown as OrgImportBatch);

describe('org import loaded helpers', () => {
  it('lists job titles most senior first, then by name', () => {
    expect(sortPositionsByRank([position('Member', 1), position('Head', 3), position('Lead', 2), position('Alpha', 2)]).map((p) => p.name))
      .toEqual(['Head', 'Alpha', 'Lead', 'Member']);
    const original = [position('B', 1), position('A', 2)];
    sortPositionsByRank(original);
    expect(original.map((p) => p.name)).toEqual(['B', 'A']);
  });

  it('flattens a tree so that open departments are followed by their sub-departments', () => {
    const root = newLevel([unit('A', { childCount: 1 }), unit('B')], 0, 10);
    expect(flattenLevel(root).map((r) => (r.kind === 'unit' ? r.node.unit.code : r.kind))).toEqual(['A', 'B']);
    const a = root.nodes[0];
    a.open = true;
    a.loading = true;
    expect(flattenLevel(root).map((r) => r.kind)).toEqual(['unit', 'loading', 'unit']);
    a.loading = false;
    a.failed = true;
    expect(flattenLevel(root).map((r) => r.kind)).toEqual(['unit', 'failed', 'unit']);
    a.failed = false;
    a.level = newLevel([unit('A1')], 1, 10);
    const rows = flattenLevel(root);
    expect(rows.map((r) => (r.kind === 'unit' ? r.node.unit.code : r.kind))).toEqual(['A', 'A1', 'B']);
    a.open = false;
    expect(flattenLevel(root)).toHaveLength(2);
  });

  it('ends a level cut at its page size with the button for the rest', () => {
    const root = newLevel([unit('A'), unit('B'), unit('C')], 0, 2);
    const rows = flattenLevel(root);
    expect(rows.map((r) => r.kind)).toEqual(['unit', 'unit', 'more']);
    expect(rows[2]).toMatchObject({ kind: 'more', hidden: 1, depth: 0 });
    root.shown = 3;
    expect(flattenLevel(root).map((r) => r.kind)).toEqual(['unit', 'unit', 'unit']);
  });

  it('lists every department read so far, parents first', () => {
    const root = newLevel([unit('A', { childCount: 1 }), unit('B')], 0, 10);
    root.nodes[0].level = newLevel([unit('A1')], 1, 10);
    expect(loadedUnits(root).map((u) => u.code)).toEqual(['A', 'A1', 'B']);
  });

  it('knows when departments exist that have not been read', () => {
    const complete = newLevel([unit('A'), unit('B')], 0, 10);
    expect(hasUnreadUnits(complete)).toBe(false);
    expect(hasUnreadUnits(newLevel([unit('A', { childCount: 2 })], 0, 10))).toBe(true);
    expect(hasUnreadUnits(newLevel([unit('A'), unit('B')], 0, 1))).toBe(true);
    const nested = newLevel([unit('A', { childCount: 1 })], 0, 10);
    nested.nodes[0].level = newLevel([unit('A1', { childCount: 1 })], 1, 10);
    expect(hasUnreadUnits(nested)).toBe(true);
    nested.nodes[0].level.nodes[0].level = newLevel([], 2, 10);
    expect(hasUnreadUnits(nested)).toBe(false);
    const failed = newLevel([unit('A')], 0, 10);
    failed.nodes[0].failed = true;
    expect(hasUnreadUnits(failed)).toBe(true);
  });

  it('counts departments and those without a ladder, saying "at least" when more exist', () => {
    const root = newLevel([unit('A', { ladderId: null }), unit('B', { ladderId: undefined as unknown as null }), unit('C')], 0, 10);
    expect(summarizeDepartments(root)).toEqual({ departments: 3, withoutLadder: 2, atLeast: false });
    expect(summarizeDepartments(newLevel([unit('A', { childCount: 3 })], 0, 10))).toMatchObject({ departments: 1, atLeast: true });
  });

  it('sums the people of the top-level departments and counts departments with people and with a head', () => {
    const root = newLevel([unit('A', { headcount: 10, headEmployeeId: 'e1', childCount: 1 }), unit('B', { headcount: 5 })], 0, 10);
    root.nodes[0].level = newLevel([unit('A1', { headcount: 4, headEmployeeId: 'e2' }), unit('A2', { headcount: 0 })], 1, 10);
    expect(summarizeEmployees(root)).toEqual({ employees: 15, departments: 3, heads: 2, atLeast: false });
    expect(summarizeEmployees(newLevel([unit('A', { headcount: undefined as unknown as number })], 0, 10)).employees).toBe(0);
  });

  it('picks the most recent import that says something about what is loaded', () => {
    expect(pickLatestImport([])).toBeNull();
    expect(pickLatestImport([batch('FAILED', '2026-03-01'), batch('CANCELLED', '2026-03-02')])).toBeNull();
    expect(pickLatestImport([batch('APPLIED', '2026-01-01'), batch('VALIDATED', '2026-02-01'), batch('FAILED', '2026-03-01')])?.status).toBe('VALIDATED');
    expect(pickLatestImport([batch('VALIDATED', '2026-01-01'), batch('APPLYING', '2026-02-01'), batch('APPLIED', '2025-12-01')])?.status).toBe('APPLYING');
    expect(pickLatestImport([{ id: 'x', status: 'APPLIED' } as unknown as OrgImportBatch, batch('APPLIED', '2026-01-01')])?.createdAt).toBe('2026-01-01');
  });
});
