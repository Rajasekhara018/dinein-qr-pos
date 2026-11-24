import { ChangeDetectorRef } from '@angular/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ChartPerson, DraftNode } from '../org-draft-tree.helpers';
import { ORG_PRIVILEGES } from '../org-structure.constants';
import { OrgChange, OrgPerson, OrgUnit } from '../org-structure.models';
import { OrgDeptTree } from './org-dept-tree';

const unit = (id: string, over: Partial<OrgUnit> = {}): OrgUnit =>
  ({ id, parentId: null, code: id.toUpperCase(), name: id, ladderId: null, headEmployeeId: null, headName: null, headcount: 0, childCount: 0, ...over }) as OrgUnit;
const change = (id: string, type: OrgChange['type'], payload: OrgChange['payload'], seq = 1): OrgChange =>
  ({ id, changeSetId: 'd1', seq, type, payload }) as OrgChange;
const livePerson = (employeeId: string, unitId: string, over: Partial<OrgPerson> = {}): OrgPerson =>
  ({ employeeId, unitId, fullName: employeeId, positionName: 'Lead', positionId: 'p1', userId: null, ...over }) as OrgPerson;
const chartPerson = (over: Partial<ChartPerson> = {}): ChartPerson =>
  ({ employeeId: 'e1', name: 'Asha', position: 'Lead', positionId: 'p1', pending: false, isHead: false, ...over });

/** Top level: Ops (2 live children, not fetched) and Sales (leaf). */
const TOP = [unit('ops', { childCount: 2, headEmployeeId: 'boss' }), unit('sales')];

function setup(top: OrgUnit[] = TOP, confirmed = true) {
  const api = {
    children: vi.fn((parentId?: string): Observable<OrgUnit[]> => of(parentId === 'ops' ? [unit('ops-a', { parentId: 'ops' }), unit('ops-b', { parentId: 'ops' })] : top)),
    people: vi.fn((unitId: string): Observable<OrgPerson[]> => of(unitId === 'ops' ? [livePerson('boss', 'ops'), livePerson('e1', 'ops')] : [])),
    positions: vi.fn((): Observable<{ id: string; name: string; rank: number }[]> => of([{ id: 'p9', name: 'Lead', rank: 1 }])),
    addChange: vi.fn((_d: string, type: OrgChange['type'], payload: OrgChange['payload']): Observable<OrgChange> =>
      of(change('new-' + type, type, payload, 50))),
    removeChange: vi.fn((): Observable<void> => of(undefined)),
  };
  const cdr = { detectChanges: vi.fn() };
  const notify = { error: vi.fn(), success: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(confirmed)) };
  const translate = { instant: vi.fn((key: string, params?: object) => key + (params ? JSON.stringify(params) : '')) };
  const closed = new Subject<boolean>();
  const matDialog = { open: vi.fn(() => ({ afterClosed: () => closed })) };
  const tree = new OrgDeptTree(api as never, cdr as unknown as ChangeDetectorRef, notify as never, dialogs as never, translate as never, matDialog as never);
  tree.draftId = 'd1';
  const emitted: OrgChange[][] = [];
  tree.changesChange.subscribe((c: OrgChange[]) => emitted.push(c));
  return { tree, api, cdr, notify, dialogs, emitted, matDialog, closed };
}

function loaded(top: OrgUnit[] = TOP, confirmed = true) {
  const ctx = setup(top, confirmed);
  ctx.tree.ngOnInit();
  return ctx;
}

const node = (tree: OrgDeptTree, id: string): DraftNode => tree.rows.map((r) => r.node).find((n) => n.id === id)!;

describe('OrgDeptTree', () => {
  describe('the Roles action', () => {
    it('is offered for departments that exist live, not for new or archived ones', () => {
      const { tree } = loaded();
      const base = node(tree, 'ops');
      expect(tree.canManageRoles({ ...base, state: 'live' })).toBe(true);
      expect(tree.canManageRoles({ ...base, state: 'renamed' })).toBe(true);
      expect(tree.canManageRoles({ ...base, state: 'moved' })).toBe(true);
      expect(tree.canManageRoles({ ...base, state: 'new' })).toBe(false);
      expect(tree.canManageRoles({ ...base, state: 'archived' })).toBe(false);
    });

    it('opens the dialog for the department with its name and headcount, labelled by its title, and puts focus back on the button after', () => {
      const { tree, matDialog, closed } = loaded();
      const button = document.createElement('button');
      document.body.appendChild(button);
      const focus = vi.spyOn(button, 'focus');
      tree.openRoles({ ...node(tree, 'ops'), headcount: 7 }, { currentTarget: button } as unknown as Event);
      expect(matDialog.open).toHaveBeenCalledTimes(1);
      const config = (matDialog.open.mock.calls[0] as unknown[])[1] as { data: unknown; ariaLabelledBy: string; restoreFocus: boolean };
      expect(config.data).toEqual({ unitId: 'ops', unitName: 'ops', headcount: 7 });
      expect(config.ariaLabelledBy).toBe('org-dept-roles-title');
      expect(config.restoreFocus).toBe(true);
      expect(focus).not.toHaveBeenCalled();
      closed.next(false);
      expect(focus).toHaveBeenCalledTimes(1);
      button.remove();
    });

    it('does not fail when the opener is not an element', () => {
      const { tree, closed } = loaded();
      tree.openRoles(node(tree, 'ops'), { currentTarget: null } as unknown as Event);
      expect(() => closed.next(true)).not.toThrow();
    });
  });

  describe('loading', () => {
    it('loads the top-level departments sorted, collapsed', () => {
      const { tree } = loaded();
      expect(tree.isLoading).toBe(false);
      expect(tree.rows.map((r) => r.node.id)).toEqual(['ops', 'sales']);
      expect(tree.rows.every((r) => r.depth === 0)).toBe(true);
      expect(tree.chartNodes.length).toBeGreaterThan(0);
    });

    it('shows an error state when the departments cannot be loaded', () => {
      const { tree, api, cdr } = setup();
      api.children.mockReturnValue(throwError(() => new Error('down')));
      tree.ngOnInit();
      expect(tree.hasError).toBe(true);
      expect(tree.isLoading).toBe(false);
      expect(cdr.detectChanges).toHaveBeenCalled();
    });

    it('lays the draft over the live tree when the changes input changes', () => {
      const { tree } = loaded();
      tree.changes = [change('c1', 'UNIT_RENAME', { id: 'sales', name: 'Revenue' })];
      tree.ngOnChanges();
      expect(node(tree, 'sales').name).toBe('Revenue');
      expect(node(tree, 'sales').state).toBe('renamed');
    });

    it('exposes the privileges the template gates its actions on', () => {
      const { tree } = setup();
      expect(tree.privileges).toBe(ORG_PRIVILEGES);
    });
  });

  describe('expanding', () => {
    it('fetches sub-departments the first time, then toggles without fetching again', () => {
      const { tree, api } = loaded();
      const ops = node(tree, 'ops');
      expect(tree.canExpand(ops)).toBe(true);
      expect(tree.canExpand(node(tree, 'sales'))).toBe(false);
      tree.toggle(ops);
      expect(api.children).toHaveBeenCalledWith('ops');
      expect(tree.isExpanded(ops)).toBe(true);
      expect(tree.rows.map((r) => r.node.id)).toEqual(['ops', 'ops-a', 'ops-b', 'sales']);
      expect(tree.rows[1].depth).toBe(1);
      tree.toggle(node(tree, 'ops'));
      expect(tree.isExpanded(ops)).toBe(false);
      tree.toggle(node(tree, 'ops'));
      expect(api.children).toHaveBeenCalledTimes(2); // top level + ops, no third call
    });

    it('collapses back and leaves the node closed when the fetch fails', () => {
      const { tree, api, notify } = loaded();
      api.children.mockReturnValue(throwError(() => ({ error: { detail: 'Slow' } })));
      tree.toggle(node(tree, 'ops'));
      expect(tree.isExpanded(node(tree, 'ops'))).toBe(false);
      expect(notify.error).toHaveBeenCalledWith('Slow');
    });
  });

  describe('chart view', () => {
    it('switching to list needs no loading', () => {
      const { tree, api } = loaded();
      tree.showView('list');
      expect(tree.view).toBe('list');
      expect(api.people).not.toHaveBeenCalled();
    });

    it('loads every remaining level and each department\'s people before showing the chart', () => {
      const { tree, api } = loaded();
      tree.showView('chart');
      expect(api.children).toHaveBeenCalledWith('ops');
      expect(api.people).toHaveBeenCalledWith('ops', null, expect.any(Number));
      expect(tree.view).toBe('chart');
      expect(tree.isLoading).toBe(false);
      const ops = tree.chartNodes.find((n) => n.id === 'ops');
      expect(ops?.people[0].isHead).toBe(true);
      expect(ops?.people.map((p) => p.name)).toEqual(['boss', 'e1']);
    });

    it('goes straight to people when there is nothing left to fetch, skipping departments created in the draft', () => {
      const { tree, api } = loaded([unit('sales')]);
      tree.changes = [change('c1', 'UNIT_CREATE', { id: 'new1', code: 'N', name: 'New' })];
      tree.ngOnChanges();
      tree.showView('chart');
      expect(api.people.mock.calls.map((c) => c[0])).toEqual(['sales']);
      expect(tree.view).toBe('chart');
    });

    it('shows the chart without lookups when no department needs people fetched', () => {
      const { tree, api } = loaded([]);
      tree.showView('chart');
      expect(api.people).not.toHaveBeenCalled();
      expect(tree.view).toBe('chart');
    });

    it('treats a failed people lookup as an empty department', () => {
      const { tree, api } = loaded([unit('sales')]);
      api.people.mockReturnValue(throwError(() => new Error('x')));
      tree.showView('chart');
      expect(tree.view).toBe('chart');
      expect(tree.chartNodes.find((n) => n.id === 'sales')?.people).toEqual([]);
    });

    it('stays out of the chart and reports when a level cannot be loaded', () => {
      const { tree, api, notify } = loaded();
      api.children.mockReturnValue(throwError(() => ({ error: { detail: 'No levels' } })));
      tree.showView('chart');
      expect(tree.view).toBe('list');
      expect(tree.isLoading).toBe(false);
      expect(notify.error).toHaveBeenCalledWith('No levels');
    });

    it('stops descending after the maximum number of levels', () => {
      // every unit claims a child that never ends: the loader must give up rather than loop forever
      const { tree, api } = loaded([unit('a', { childCount: 1 })]);
      let n = 0;
      api.children.mockImplementation((parentId?: string) => of(parentId ? [unit('x' + n++, { parentId, childCount: 1 })] : []));
      tree.showView('chart');
      expect(tree.view).toBe('chart');
      expect(api.children.mock.calls.length).toBeLessThanOrEqual(25);
    });
  });

  describe('people panel', () => {
    it('fetches people the first time a panel opens, and reuses them afterwards', () => {
      const { tree, api } = loaded();
      const ops = node(tree, 'ops');
      expect(tree.isPeopleOpen('ops')).toBe(false);
      tree.togglePeople(ops);
      expect(tree.isPeopleOpen('ops')).toBe(true);
      expect(api.people).toHaveBeenCalledTimes(1);
      expect(tree.peopleOf(ops).map((p) => p.name)).toEqual(['boss', 'e1']);
      tree.togglePeople(ops);
      expect(tree.isPeopleOpen('ops')).toBe(false);
      tree.togglePeople(ops);
      expect(api.people).toHaveBeenCalledTimes(1);
    });

    it('does not ask the server about a department that only exists in the draft', () => {
      const { tree, api } = loaded();
      tree.changes = [change('c1', 'UNIT_CREATE', { id: 'new1', code: 'N', name: 'New' })];
      tree.ngOnChanges();
      tree.togglePeople(node(tree, 'new1'));
      expect(api.people).not.toHaveBeenCalled();
      expect(tree.isPeopleOpen('new1')).toBe(true);
    });

    it('shows an empty list when the lookup fails, and nothing for an unknown department', () => {
      const { tree, api } = loaded();
      api.people.mockReturnValue(throwError(() => new Error('x')));
      tree.togglePeople(node(tree, 'sales'));
      expect(tree.peopleOf(node(tree, 'sales'))).toEqual([]);
      expect(tree.peopleOf({ id: 'ghost' } as DraftNode)).toEqual([]);
    });
  });

  describe('inline editor', () => {
    it('tracks which editor is open where', () => {
      const { tree } = loaded();
      tree.startAdd('ops');
      expect(tree.isEditing('add', 'ops')).toBe(true);
      expect(tree.isEditing('rename', 'ops')).toBe(false);
      expect(tree.isEditing('add', 'sales')).toBe(false);
      tree.startRename(node(tree, 'sales'));
      expect(tree.nameDraft).toBe('sales');
      expect(tree.isEditing('rename', 'sales')).toBe(true);
      tree.startPerson(node(tree, 'sales'));
      expect(tree.isEditing('person', 'sales')).toBe(true);
      tree.moveTargetId = 'ops';
      tree.startMove(node(tree, 'sales'));
      expect(tree.moveTargetId).toBe('');
      tree.cancelEdit();
      expect(tree.editing).toBeNull();
      expect(tree.isEditing('move', 'sales')).toBe(false);
    });

    it('forwards the person editor\'s changes to the parent', () => {
      const { tree, emitted } = loaded();
      const c = change('c1', 'ASSIGN', {});
      tree.onChangesFromEditor([c]);
      expect(emitted).toEqual([[c]]);
    });

    it('previews the code a new department would get, unique among taken codes', () => {
      const { tree } = loaded();
      expect(tree.codePreview).toBe('');
      tree.nameDraft = '  ';
      expect(tree.codePreview).toBe('');
      tree.nameDraft = 'Sales';
      expect(tree.codePreview).toBe('SALES_2');
      tree.nameDraft = 'Field Support';
      expect(tree.codePreview).toBe('FS');
    });

    it('tracks options and rows by id', () => {
      const { tree } = loaded();
      expect(tree.trackOption(0, { id: 'q' })).toBe('q');
      expect(tree.trackRow(0, tree.rows[0])).toBe('ops');
    });
  });

  describe('move options', () => {
    it('lists the departments indented, skipping itself, its subtree and archived ones', () => {
      const { tree } = loaded();
      tree.changes = [
        change('c1', 'UNIT_CREATE', { id: 'n1', code: 'N1', name: 'Alpha', parentId: 'sales' }, 1),
        change('c2', 'UNIT_ARCHIVE', { id: 'sales' }, 2),
      ];
      tree.ngOnChanges();
      expect(tree.moveOptions(node(tree, 'ops')).map((o) => o.label)).toEqual([]);
      tree.changes = [change('c1', 'UNIT_CREATE', { id: 'n1', code: 'N1', name: 'Alpha', parentId: 'sales' })];
      tree.ngOnChanges();
      const labels = tree.moveOptions(node(tree, 'ops')).map((o) => o.label);
      expect(labels).toHaveLength(2);
      expect(labels[0]).toBe('sales');
      expect(labels[1]).toMatch(/^\s+\S+\s+Alpha$/);
      expect(tree.moveOptions(node(tree, 'sales')).map((o) => o.id)).toEqual(['ops']);
    });
  });

  describe('adding a department', () => {
    it('creates a top-level department with a code and expands nothing', () => {
      const { tree, api, emitted } = loaded();
      tree.startAdd('');
      tree.nameDraft = ' Field Support ';
      tree.confirmAdd();
      expect(api.addChange).toHaveBeenCalledWith('d1', 'UNIT_CREATE', { code: 'FS', name: 'Field Support', __labels: {} });
      expect(emitted).toHaveLength(1);
      expect(tree.editing).toBeNull();
      expect(tree.busy).toBe(false);
    });

    it('creates a sub-department with its parent labelled, and opens the parent', () => {
      const { tree, api } = loaded();
      tree.startAdd('sales');
      tree.nameDraft = 'Inside';
      tree.confirmAdd();
      expect(api.addChange).toHaveBeenCalledWith('d1', 'UNIT_CREATE', { code: 'INSIDE', name: 'Inside', parentId: 'sales', __labels: { parentId: 'sales' } });
      expect(tree.isExpanded(node(tree, 'sales'))).toBe(true);
    });

    it('does nothing for a blank name or when not adding', () => {
      const { tree, api } = loaded();
      tree.confirmAdd();
      tree.startAdd('');
      tree.nameDraft = '   ';
      tree.confirmAdd();
      tree.startRename(node(tree, 'sales'));
      tree.confirmAdd();
      expect(api.addChange).not.toHaveBeenCalled();
    });

    it('reports a failure and keeps the editor open', () => {
      const { tree, api, notify, emitted } = loaded();
      api.addChange.mockReturnValue(throwError(() => ({ error: { detail: 'Duplicate' } })));
      tree.startAdd('');
      tree.nameDraft = 'Dup';
      tree.confirmAdd();
      expect(notify.error).toHaveBeenCalledWith('Duplicate');
      expect(tree.editing).not.toBeNull();
      expect(tree.busy).toBe(false);
      expect(emitted).toHaveLength(0);
    });

    it('uses the translated fallback when the failure has no detail', () => {
      const { tree, api, notify } = loaded();
      api.addChange.mockReturnValue(throwError(() => undefined));
      tree.startAdd('');
      tree.nameDraft = 'X';
      tree.confirmAdd();
      expect(notify.error).toHaveBeenCalledWith('orgStructure.builder.messages.addFailed');
    });
  });

  describe('renaming', () => {
    it('saves a rename with the old name as label', () => {
      const { tree, api } = loaded();
      tree.startRename(node(tree, 'sales'));
      tree.nameDraft = 'Revenue';
      tree.confirmRename();
      expect(api.addChange).toHaveBeenCalledWith('d1', 'UNIT_RENAME', { id: 'sales', name: 'Revenue', __labels: { id: 'sales' } });
    });

    it('closes without a change when the name is unchanged', () => {
      const { tree, api } = loaded();
      tree.startRename(node(tree, 'sales'));
      tree.confirmRename();
      expect(api.addChange).not.toHaveBeenCalled();
      expect(tree.editing).toBeNull();
    });

    it('ignores a blank name, a missing editor or an unknown department', () => {
      const { tree, api } = loaded();
      tree.confirmRename();
      tree.startRename(node(tree, 'sales'));
      tree.nameDraft = ' ';
      tree.confirmRename();
      tree.editing = { mode: 'rename', unitId: 'ghost' };
      tree.nameDraft = 'Anything';
      tree.confirmRename();
      expect(api.addChange).not.toHaveBeenCalled();
      expect(tree.editing).toBeNull(); // the unknown department just closes the editor
    });
  });

  describe('moving a department', () => {
    it('moves under the chosen department', () => {
      const { tree, api } = loaded();
      tree.startMove(node(tree, 'sales'));
      tree.moveTargetId = 'ops';
      tree.confirmMove();
      expect(api.addChange).toHaveBeenCalledWith('d1', 'UNIT_MOVE', { id: 'sales', newParentId: 'ops', __labels: { id: 'sales', newParentId: 'ops' } });
    });

    it('moves to the top level without a target', () => {
      const { tree, api } = loaded();
      tree.startMove(node(tree, 'sales'));
      tree.confirmMove(true);
      expect(api.addChange).toHaveBeenCalledWith('d1', 'UNIT_MOVE', { id: 'sales', __labels: { id: 'sales' } });
    });

    it('does nothing without a chosen target, a move editor or a known department', () => {
      const { tree, api } = loaded();
      tree.confirmMove();
      tree.startMove(node(tree, 'sales'));
      tree.confirmMove();
      tree.editing = { mode: 'move', unitId: 'ghost' };
      tree.confirmMove(true);
      expect(api.addChange).not.toHaveBeenCalled();
    });
  });

  describe('archive and undo', () => {
    it('archives only after a danger confirmation naming the department', () => {
      const { tree, api, dialogs } = loaded();
      tree.archive(node(tree, 'sales'));
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({
        variant: 'danger', message: 'orgStructure.tree.archiveDialog.message{"name":"sales"}',
      }));
      expect(api.addChange).toHaveBeenCalledWith('d1', 'UNIT_ARCHIVE', { id: 'sales', __labels: { id: 'sales' } });
    });

    it('does not archive when declined', () => {
      const { tree, api } = loaded(TOP, false);
      tree.archive(node(tree, 'sales'));
      expect(api.addChange).not.toHaveBeenCalled();
    });

    it('undoes every pending change on a live department and emits the remainder', () => {
      const { tree, api, emitted } = loaded();
      tree.changes = [
        change('c1', 'UNIT_RENAME', { id: 'sales', name: 'R' }, 1),
        change('c2', 'UNIT_RENAME', { id: 'ops', name: 'O' }, 2),
      ];
      tree.ngOnChanges();
      tree.undo(node(tree, 'sales'));
      expect(api.removeChange).toHaveBeenCalledWith('d1', 'c1');
      expect(api.removeChange).toHaveBeenCalledTimes(1);
      expect(emitted[0].map((c) => c.id)).toEqual(['c2']);
      expect(tree.busy).toBe(false);
    });

    it('does nothing when the department has nothing pending', () => {
      const { tree, api } = loaded();
      tree.undo(node(tree, 'sales'));
      expect(api.removeChange).not.toHaveBeenCalled();
    });

    it('reports when taking a change back fails', () => {
      const { tree, api, notify } = loaded();
      tree.changes = [change('c1', 'UNIT_RENAME', { id: 'sales', name: 'R' })];
      tree.ngOnChanges();
      api.removeChange.mockReturnValue(throwError(() => ({})));
      tree.undo(node(tree, 'sales'));
      expect(notify.error).toHaveBeenCalledWith('orgStructure.builder.messages.removeFailed');
      expect(tree.busy).toBe(false);
    });
  });

  describe('head', () => {
    it('clears the head', () => {
      const { tree, api } = loaded();
      tree.removeHead(node(tree, 'ops'));
      expect(api.addChange).toHaveBeenCalledWith('d1', 'UNIT_CLEAR_HEAD', { unitId: 'ops', __labels: { unitId: 'ops' } });
    });

    it('sets the head directly when there is none', () => {
      const { tree, api } = loaded();
      tree.makeHead(node(tree, 'sales'), chartPerson());
      expect(api.addChange).toHaveBeenCalledTimes(1);
      expect(api.addChange).toHaveBeenCalledWith('d1', 'UNIT_SET_HEAD', { unitId: 'sales', employeeId: 'e1', __labels: { unitId: 'sales', employeeId: 'Asha' } });
    });

    it('clears the current head first, emitting after each step', () => {
      const { tree, api, emitted } = loaded();
      tree.makeHead(node(tree, 'ops'), chartPerson());
      expect(api.addChange.mock.calls.map((c) => c[1])).toEqual(['UNIT_CLEAR_HEAD', 'UNIT_SET_HEAD']);
      expect(emitted.map((e) => e.map((c) => c.id))).toEqual([['new-UNIT_CLEAR_HEAD'], ['new-UNIT_CLEAR_HEAD', 'new-UNIT_SET_HEAD']]);
    });

    it('reports a failure of either step', () => {
      const { tree, api, notify } = loaded();
      api.addChange.mockReturnValueOnce(throwError(() => ({ error: { detail: 'first' } })));
      tree.makeHead(node(tree, 'ops'), chartPerson());
      expect(notify.error).toHaveBeenLastCalledWith('first');
      api.addChange
        .mockReturnValueOnce(of(change('k', 'UNIT_CLEAR_HEAD', {})))
        .mockReturnValueOnce(throwError(() => ({ error: { detail: 'second' } })));
      tree.makeHead(node(tree, 'ops'), chartPerson());
      expect(notify.error).toHaveBeenLastCalledWith('second');
      expect(tree.busy).toBe(false);
    });
  });

  describe('removing a person', () => {
    it('unassigns an ordinary member', () => {
      const { tree, api } = loaded();
      tree.removePerson(node(tree, 'ops'), chartPerson());
      expect(api.addChange).toHaveBeenCalledWith('d1', 'UNASSIGN', { employeeId: 'e1', unitId: 'ops', __labels: { unitId: 'ops', employeeId: 'Asha' } });
    });

    it('clears the head before unassigning the head', () => {
      const { tree, api, emitted } = loaded();
      tree.removePerson(node(tree, 'ops'), chartPerson({ isHead: true }));
      expect(api.addChange.mock.calls.map((c) => c[1])).toEqual(['UNIT_CLEAR_HEAD', 'UNASSIGN']);
      expect(emitted).toHaveLength(2);
      expect(tree.busy).toBe(false);
    });

    it('reports a failure of either step', () => {
      const { tree, api, notify } = loaded();
      api.addChange.mockReturnValueOnce(throwError(() => ({})));
      tree.removePerson(node(tree, 'ops'), chartPerson({ isHead: true }));
      expect(notify.error).toHaveBeenLastCalledWith('orgStructure.builder.messages.addFailed');
      api.addChange.mockReturnValueOnce(of(change('k', 'UNIT_CLEAR_HEAD', {}))).mockReturnValueOnce(throwError(() => ({ error: { detail: 'late' } })));
      tree.removePerson(node(tree, 'ops'), chartPerson({ isHead: true }));
      expect(notify.error).toHaveBeenLastCalledWith('late');
    });
  });

  describe('moving a person to another department', () => {
    function withTargets() {
      const ctx = loaded([unit('ops', { ladderId: 'l1' }), unit('sales', { ladderId: 'l2' }), unit('bare')]);
      return ctx;
    }

    it('lists every other non-archived department as a target and tracks who is being moved', () => {
      const { tree } = withTargets();
      tree.changes = [change('c1', 'UNIT_ARCHIVE', { id: 'bare' })];
      tree.ngOnChanges();
      expect(tree.moveTargets(node(tree, 'ops')).map((n) => n.id)).toEqual(['sales']);
      const person = chartPerson();
      tree.startMovePerson(node(tree, 'ops'), person);
      expect(tree.isMoving(node(tree, 'ops'), person)).toBe(true);
      expect(tree.isMoving(node(tree, 'sales'), person)).toBe(false);
      expect(tree.isMoving(node(tree, 'ops'), chartPerson({ employeeId: 'other' }))).toBe(false);
      tree.cancelMove();
      expect(tree.moving).toBeNull();
      expect(tree.isMoving(node(tree, 'ops'), person)).toBe(false);
    });

    it('matches the person\'s job in the target ladder', () => {
      const { tree, api } = withTargets();
      api.positions.mockReturnValue(of([{ id: 'p1', name: 'Lead', rank: 1 }, { id: 'p2', name: 'Other', rank: 2 }]));
      tree.startMovePerson(node(tree, 'ops'), chartPerson());
      tree.moveToUnitId = 'sales';
      tree.onMoveTargetChange(chartPerson());
      expect(api.positions).toHaveBeenCalledWith('l2');
      expect(tree.movePositionId).toBe('p1');
      expect(tree.moveChecking).toBe(false);
      expect(tree.canConfirmMove).toBe(true);
    });

    it('asks the person to pick a job when none matches, and waits during the lookup', () => {
      const { tree, api } = withTargets();
      const pending = new Subject<{ id: string; name: string; rank: number }[]>();
      api.positions.mockReturnValue(pending);
      tree.moveToUnitId = 'sales';
      tree.onMoveTargetChange(chartPerson({ positionId: 'zzz', position: 'Nothing' }));
      expect(tree.moveChecking).toBe(true);
      expect(tree.canConfirmMove).toBe(false);
      pending.next([{ id: 'p5', name: 'Manager', rank: 1 }]);
      expect(tree.moveChecking).toBe(false);
      expect(tree.movePositionId).toBe('');
      expect(tree.moveTargetPositions).toHaveLength(1);
      expect(tree.canConfirmMove).toBe(false);
    });

    it('flags a target department without a ladder', () => {
      const { tree, api } = withTargets();
      tree.moveToUnitId = 'bare';
      tree.onMoveTargetChange(chartPerson());
      expect(tree.moveLadderMissing).toBe(true);
      expect(api.positions).not.toHaveBeenCalled();
      expect(tree.canConfirmMove).toBe(false);
    });

    it('resets when no target is chosen, and tolerates a failing ladder lookup', () => {
      const { tree, api } = withTargets();
      tree.moveToUnitId = '';
      tree.onMoveTargetChange(chartPerson());
      expect(tree.moveLadderMissing).toBe(false);
      api.positions.mockReturnValue(throwError(() => new Error('x')));
      tree.moveToUnitId = 'sales';
      tree.onMoveTargetChange(chartPerson());
      expect(tree.moveTargetPositions).toEqual([]);
      expect(tree.moveChecking).toBe(false);
    });

    it('moves a member: unassign here, assign there with the matched job', () => {
      const { tree, api, emitted } = withTargets();
      api.positions.mockReturnValue(of([{ id: 'p1', name: 'Lead', rank: 1 }]));
      const person = chartPerson();
      tree.startMovePerson(node(tree, 'ops'), person);
      tree.moveToUnitId = 'sales';
      tree.onMoveTargetChange(person);
      tree.confirmMovePerson(node(tree, 'ops'), person);
      expect(api.addChange.mock.calls.map((c) => c[1])).toEqual(['UNASSIGN', 'ASSIGN']);
      expect(api.addChange.mock.calls[1][2]).toMatchObject({ employeeId: 'e1', unitId: 'sales', positionId: 'p1', primary: true });
      expect(emitted.at(-1)!.map((c) => c.id)).toEqual(['new-UNASSIGN', 'new-ASSIGN']);
      expect(tree.moving).toBeNull();
      expect(tree.busy).toBe(false);
    });

    it('also clears the head when the moved person is the head', () => {
      const { tree, api } = withTargets();
      api.positions.mockReturnValue(of([{ id: 'p1', name: 'Lead', rank: 1 }]));
      const person = chartPerson({ isHead: true });
      tree.moveToUnitId = 'sales';
      tree.onMoveTargetChange(person);
      tree.confirmMovePerson(node(tree, 'ops'), person);
      expect(api.addChange.mock.calls.map((c) => c[1])).toEqual(['UNIT_CLEAR_HEAD', 'UNASSIGN', 'ASSIGN']);
    });

    it('stops and reports when a step fails', () => {
      const { tree, api, notify } = withTargets();
      api.positions.mockReturnValue(of([{ id: 'p1', name: 'Lead', rank: 1 }]));
      const person = chartPerson();
      tree.moveToUnitId = 'sales';
      tree.onMoveTargetChange(person);
      api.addChange.mockReturnValueOnce(of(change('u', 'UNASSIGN', {}))).mockReturnValueOnce(throwError(() => ({ error: { detail: 'Not in ladder' } })));
      tree.confirmMovePerson(node(tree, 'ops'), person);
      expect(notify.error).toHaveBeenCalledWith('Not in ladder');
      expect(tree.busy).toBe(false);
    });

    it('refuses to move without a confirmed target and job', () => {
      const { tree, api } = withTargets();
      tree.confirmMovePerson(node(tree, 'ops'), chartPerson());
      tree.moveToUnitId = 'sales';
      tree.confirmMovePerson(node(tree, 'ops'), chartPerson()); // no job chosen yet
      expect(api.addChange).not.toHaveBeenCalled();
    });
  });
});
