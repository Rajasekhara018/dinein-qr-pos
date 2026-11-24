import { describe, expect, it } from 'vitest';
import { buildDraftTree, chartPeople, flattenTree, makeUnitCode, takenCodes, undoChangeIds } from './org-draft-tree.helpers';
import { OrgChange, OrgPerson, OrgUnit } from './org-structure.models';

const unit = (id: string, name: string, parentId: string | null = null, childCount = 0): OrgUnit => ({
  id, parentId, code: name.toUpperCase(), name, unitType: null, ladderId: null, headEmployeeId: null, status: 'ACTIVE', childCount, headcount: 0,
});
const change = (seq: number, type: OrgChange['type'], payload: OrgChange['payload']): OrgChange => ({
  id: `c${seq}`, changeSetId: 'd', seq, type, payload,
});

describe('org draft tree', () => {
  const live = new Map<string, OrgUnit[]>([['', [unit('a', 'Sales', null, 1), unit('b', 'Tech')]]]);

  it('shows live departments sorted, and flags the ones with sub-departments not fetched yet', () => {
    const roots = buildDraftTree(live, []);
    expect(roots.map((n) => n.name)).toEqual(['Sales', 'Tech']);
    expect(roots[0].needsLoad).toBe(true);
    expect(roots[1].needsLoad).toBe(false);
  });

  it('carries the live headcount of a department and none for one created in the draft', () => {
    const counted = new Map<string, OrgUnit[]>([['', [{ ...unit('a', 'Sales'), headcount: 7 }]]]);
    const roots = buildDraftTree(counted, [change(1, 'UNIT_CREATE', { id: 'n1', code: 'OPS', name: 'Ops' })]);

    expect(roots.find((n) => n.id === 'a')?.headcount).toBe(7);
    expect(roots.find((n) => n.id === 'n1')?.headcount).toBeNull();
  });

  it('lays create, rename, move and archive changes over the live tree', () => {
    const roots = buildDraftTree(live, [
      change(1, 'UNIT_CREATE', { id: 'n1', code: 'OPS', name: 'Ops' }),
      change(2, 'UNIT_CREATE', { id: 'n2', code: 'NIGHT', name: 'Night shift', parentId: 'n1' }),
      change(3, 'UNIT_RENAME', { id: 'b', name: 'Technology' }),
      change(4, 'UNIT_MOVE', { id: 'b', newParentId: 'n1' }),
      change(5, 'UNIT_ARCHIVE', { id: 'a' }),
    ]);
    const ops = roots.find((n) => n.id === 'n1')!;
    expect(ops.state).toBe('new');
    expect(ops.children.map((n) => n.name)).toEqual(['Night shift', 'Technology']);
    expect(ops.children[1].state).toBe('moved');
    expect(roots.find((n) => n.id === 'a')!.state).toBe('archived');
  });

  it('follows the ladder chosen for a department', () => {
    const roots = buildDraftTree(live, [
      change(1, 'UNIT_CREATE', { id: 'n1', code: 'OPS', name: 'Ops', ladderId: 'l1' }),
      change(2, 'UNIT_SET_LADDER', { unitId: 'b', ladderId: 'l2' }),
    ]);
    expect(roots.find((n) => n.id === 'n1')!.ladderId).toBe('l1');
    expect(roots.find((n) => n.id === 'b')!.ladderId).toBe('l2');
  });

  it('lists only the opened branches when flattening', () => {
    const roots = buildDraftTree(live, [change(1, 'UNIT_CREATE', { id: 'n1', code: 'X', name: 'X', parentId: 'b' })]);
    expect(flattenTree(roots, new Set()).map((r) => r.node.id)).toEqual(['a', 'b']);
    expect(flattenTree(roots, new Set(['b'])).map((r) => [r.node.id, r.depth])).toEqual([['a', 0], ['b', 0], ['n1', 1]]);
  });

  it('makes a short, unique code from the first letters of the name', () => {
    expect(makeUnitCode('Technical Lead', new Set())).toBe('TL');
    expect(makeUnitCode('Chief Executive Officer', new Set())).toBe('CEO');
    expect(makeUnitCode('Tech', new Set())).toBe('TECH');
    expect(makeUnitCode('Tech Team', new Set(['TT', 'TT_2']))).toBe('TT_3');
    expect(makeUnitCode('  ', new Set())).toBe('DEPT');
    expect(takenCodes(live, [change(1, 'UNIT_CREATE', { id: 'n', code: 'ops', name: 'Ops' })]).has('OPS')).toBe(true);
  });

  it('undoes a new department together with everything under it, but only its own changes otherwise', () => {
    const changes = [
      change(1, 'UNIT_CREATE', { id: 'n1', code: 'A', name: 'A' }),
      change(2, 'UNIT_CREATE', { id: 'n2', code: 'B', name: 'B', parentId: 'n1' }),
      change(3, 'UNIT_RENAME', { id: 'n2', name: 'B2' }),
      change(4, 'UNIT_RENAME', { id: 'b', name: 'T' }),
    ];
    const roots = buildDraftTree(live, changes);
    const a = roots.find((n) => n.id === 'n1')!;
    expect(undoChangeIds(changes, a)).toEqual(['c1', 'c2', 'c3']);
    const b = roots.find((n) => n.id === 'b')!;
    expect(undoChangeIds(changes, b)).toEqual(['c4']);
  });

  it('lists the people a department will have after publishing', () => {
    const person = (employeeId: string, fullName: string, unitId: string): OrgPerson => ({
      employeeId, employeeCode: employeeId, fullName, email: null, unitId, positionId: 'p', positionName: 'Engineer', managerEmployeeId: null,
    });
    const livePeople = [person('e1', 'Asha', 'u1'), person('e2', 'Ravi', 'u1'), person('e3', 'Zed', 'other')];
    const people = chartPeople('u1', livePeople, [
      change(1, 'ASSIGN', { employeeId: 'e9', unitId: 'u1', positionId: 'x', __labels: { employeeId: 'Meera', positionId: 'Lead' } }),
      change(2, 'UNASSIGN', { employeeId: 'e2', unitId: 'u1' }),
      change(3, 'ASSIGN', { employeeId: 'e1', unitId: 'u1', positionId: 'x', __labels: { employeeId: 'Asha', positionId: 'Senior' } }),
    ]);
    expect(people.map((p) => [p.name, p.position, p.pending])).toEqual([['Asha', 'Senior', true], ['Meera', 'Lead', true]]);
  });

  it('lists the department head first and marks only them', () => {
    const person = (employeeId: string, fullName: string): OrgPerson => ({
      employeeId, employeeCode: employeeId, fullName, email: null, unitId: 'u1', positionId: 'p', positionName: 'Engineer', managerEmployeeId: null,
    });
    const people = chartPeople('u1', [person('e1', 'Asha'), person('e2', 'Ravi'), person('e3', 'Zed')], [], 'e3');

    expect(people.map((p) => [p.name, p.isHead])).toEqual([['Zed', true], ['Asha', false], ['Ravi', false]]);
  });

  it('keeps the account of people who exist live so their wallet can be opened', () => {
    const live = [{ employeeId: 'e1', employeeCode: 'e1', fullName: 'Asha', email: null, unitId: 'u1', positionId: 'p', positionName: 'Eng', managerEmployeeId: null, userId: 'user-asha' }];
    const people = chartPeople('u1', live, [change(1, 'ASSIGN', { employeeId: 'e9', unitId: 'u1', positionId: 'x', __labels: { employeeId: 'Meera', positionId: 'Lead' } })]);

    expect(people.find((p) => p.name === 'Asha')?.userId).toBe('user-asha');
    expect(people.find((p) => p.name === 'Meera')?.userId).toBeUndefined();
  });

  it('follows the draft for who heads a department: set, clear, and leaving the department', () => {
    const headed = new Map<string, OrgUnit[]>([['', [{ ...unit('a', 'Sales'), headEmployeeId: 'e1' }]]]);

    expect(buildDraftTree(headed, [])[0].headEmployeeId).toBe('e1');
    expect(buildDraftTree(headed, [change(1, 'UNIT_CLEAR_HEAD', { unitId: 'a' })])[0].headEmployeeId).toBeNull();
    expect(buildDraftTree(headed, [
      change(1, 'UNIT_CLEAR_HEAD', { unitId: 'a' }), change(2, 'UNIT_SET_HEAD', { unitId: 'a', employeeId: 'e2' }),
    ])[0].headEmployeeId).toBe('e2');
    expect(buildDraftTree(headed, [change(1, 'UNASSIGN', { unitId: 'a', employeeId: 'e1' })])[0].headEmployeeId).toBeNull();
    // the name shown next to a department follows the draft too
    const named = buildDraftTree(headed, [
      change(1, 'UNIT_CLEAR_HEAD', { unitId: 'a' }),
      change(2, 'UNIT_SET_HEAD', { unitId: 'a', employeeId: 'e2', __labels: { unitId: 'Sales', employeeId: 'Ravi' } }),
    ])[0];
    expect(named.headName).toBe('Ravi');
    expect(buildDraftTree(headed, [change(1, 'UNIT_CLEAR_HEAD', { unitId: 'a' })])[0].headName).toBeNull();
    // someone else leaving does not change the head
    expect(buildDraftTree(headed, [change(1, 'UNASSIGN', { unitId: 'a', employeeId: 'e9' })])[0].headEmployeeId).toBe('e1');
  });
});
