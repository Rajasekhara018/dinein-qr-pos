import { ChangeDetectorRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { HqLookupService } from 'hq-shared-lib';
import { UamUser } from '../../shared/models/uam.models';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { SearchApiService } from '../../shared/services/search-api-service';
import { DraftNode } from '../org-draft-tree.helpers';
import { NEW_EMPLOYEE_STATUS } from '../org-structure.constants';
import { OrgChange, OrgEmployee } from '../org-structure.models';
import { OrgPersonEditor } from './org-person-editor';

const change = (id: string, type: OrgChange['type'], payload: OrgChange['payload'], seq = 1): OrgChange =>
  ({ id, changeSetId: 'd1', seq, type, payload }) as OrgChange;
const user = (id: string, over: Partial<UamUser> = {}): UamUser =>
  ({ id, userName: 'user-' + id, email: id + '@x.com', employeeCode: null, displayName: null, fullName: null, ...over }) as UamUser;
const employee = (id: string, userId: string | null): OrgEmployee =>
  ({ id, employeeCode: 'c', fullName: 'Emp ' + id, email: null, phone: null, status: 'ACTIVE', userId, joinedOn: null });
const unitNode = (over: Partial<DraftNode> = {}): DraftNode =>
  ({ id: 'u1', parentId: null, code: 'U1', name: 'Ops', state: 'live', ladderId: 'l1', headEmployeeId: null, headcount: 0, liveChildCount: 0, children: [], needsLoad: false, ...over });

function setup(nodeOver: Partial<DraftNode> = {}, tenantId: string | null = 't1') {
  const api = {
    ladders: vi.fn((): Observable<{ id: string; name: string }[]> => of([{ id: 'l1', name: 'Eng' }, { id: 'l2', name: 'Ops' }])),
    positions: vi.fn((): Observable<{ id: string; name: string; rank: number }[]> => of([{ id: 'p2', name: 'Senior', rank: 2 }, { id: 'p1', name: 'Junior', rank: 1 }])),
    addChange: vi.fn((_d: string, type: OrgChange['type'], payload: OrgChange['payload']): Observable<OrgChange> =>
      of(change('new-' + type, type, { id: 'gen-' + type, ...payload }, 99))),
    searchEmployees: vi.fn((): Observable<OrgEmployee[]> => of([])),
    createEmployee: vi.fn((): Observable<OrgEmployee> => of(employee('e-new', 'u-a'))),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  const translate = { instant: vi.fn((key: string, params?: object) => key + (params ? JSON.stringify(params) : '')) };
  const cdr = { detectChanges: vi.fn() };
  const searchApi = { getAll: vi.fn() };
  const lookup = { open: vi.fn() };
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: OrganizationContextService, useValue: { currentTenantId: () => tenantId } },
      { provide: SearchApiService, useValue: searchApi },
      { provide: HqLookupService, useValue: lookup },
    ],
  });
  const editor = TestBed.runInInjectionContext(
    () => new OrgPersonEditor(api as never, notify as never, translate as never, cdr as unknown as ChangeDetectorRef),
  );
  editor.draftId = 'd1';
  editor.node = unitNode(nodeOver);
  const emitted: OrgChange[][] = [];
  editor.changesChange.subscribe((c: OrgChange[]) => emitted.push(c));
  return { editor, api, notify, cdr, searchApi, lookup, emitted };
}

function ready(nodeOver: Partial<DraftNode> = {}) {
  const ctx = setup(nodeOver);
  ctx.editor.ngOnInit();
  return ctx;
}

describe('OrgPersonEditor', () => {
  describe('init and options', () => {
    it('starts on the department\'s ladder and loads its ladders and job titles', () => {
      const { editor, api } = ready();
      expect(editor.ladderId).toBe('l1');
      expect(api.positions).toHaveBeenCalledWith('l1');
      expect(editor.ladderOptions.map((l) => l.label)).toEqual(['Eng', 'Ops']);
      expect(editor.positionOptions.map((p) => p.label)).toEqual(['Junior', 'Senior']); // lowest rank first
    });

    it('starts with no ladder when the department has none, and loads no titles', () => {
      const { editor, api } = ready({ ladderId: null });
      expect(editor.ladderId).toBe('');
      expect(api.positions).not.toHaveBeenCalled();
      expect(editor.positionOptions).toEqual([]);
    });

    it('survives a failing ladder lookup', () => {
      const { editor, api } = setup();
      api.ladders.mockReturnValue(throwError(() => new Error('x')));
      editor.ngOnInit();
      expect(editor.ladderOptions).toEqual([]);
    });

    it('adds ladders created or copied in the draft once, and ignores other change types', () => {
      const { editor } = ready();
      editor.changes = [
        change('c1', 'LADDER_CREATE', { id: 'l9', name: 'New' }),
        change('c2', 'LADDER_COPY', { id: 'l1', name: 'Eng (copy)' }),
        change('c3', 'UNIT_RENAME', { id: 'x', name: 'y' }),
      ];
      expect(editor.ladderOptions.map((l) => l.id)).toEqual(['l1', 'l2', 'l9']);
    });

    it('adds the titles created in the draft for the chosen ladder only, ordered by rank', () => {
      const { editor } = ready();
      editor.changes = [
        change('c1', 'POSITION_UPSERT', { id: 'p3', ladderId: 'l1', name: 'Lead', rank: 3 }),
        change('c2', 'POSITION_UPSERT', { id: 'p7', ladderId: 'other', name: 'Elsewhere', rank: 1 }),
        change('c3', 'POSITION_UPSERT', { id: 'p1', ladderId: 'l1', name: 'Dup', rank: 9 }),
      ];
      expect(editor.positionOptions.map((p) => p.id)).toEqual(['p1', 'p2', 'p3']);
    });

    it('does not ask the server for titles of a ladder that only exists in the draft', () => {
      const { editor, api } = ready();
      editor.changes = [change('c1', 'LADDER_CREATE', { id: 'l9', name: 'New' })];
      api.positions.mockClear();
      editor.ladderId = 'l9';
      editor.positionId = 'p1';
      editor.onLadderChosen();
      expect(api.positions).not.toHaveBeenCalled();
      expect(editor.positionId).toBe('');
      expect(editor.positionOptions).toEqual([]);
    });

    it('reloads titles when another ladder is chosen, and tolerates a failure', () => {
      const { editor, api } = ready();
      api.positions.mockReturnValue(throwError(() => new Error('x')));
      editor.ladderId = 'l2';
      editor.onLadderChosen();
      expect(api.positions).toHaveBeenLastCalledWith('l2');
      expect(editor.positionOptions).toEqual([]);
    });

    it('tracks options and users by id', () => {
      const { editor } = ready();
      expect(editor.trackOption(0, { id: 'a', label: 'A' })).toBe('a');
      expect(editor.trackUser(0, user('u9'))).toBe('u9');
    });
  });

  describe('what is missing', () => {
    it('names the first missing step, in order', () => {
      const { editor } = ready({ ladderId: null });
      expect(editor.missingKey).toBe('orgStructure.tree.person.needLadder');
      editor.ladderId = 'l1';
      expect(editor.missingKey).toBe('orgStructure.tree.person.needPosition');
      editor.positionId = 'p1';
      expect(editor.missingKey).toBe('orgStructure.tree.person.needPerson');
      editor.selectedUsers = [user('a')];
      expect(editor.missingKey).toBeNull();
    });

    it('allows adding only with ladder, title and a person, and not while busy', () => {
      const { editor } = ready();
      expect(editor.canAdd).toBe(false);
      editor.positionId = 'p1';
      editor.selectedUsers = [user('a')];
      expect(editor.canAdd).toBe(true);
      editor.busy = true;
      expect(editor.canAdd).toBe(false);
    });

    it('offers "make head" only while the department has no head', () => {
      expect(ready().editor.headAvailable).toBe(true);
      expect(ready({ headEmployeeId: 'boss' }).editor.headAvailable).toBe(false);
    });
  });

  describe('chosen people', () => {
    it('removes a person from the selection', () => {
      const { editor } = ready();
      editor.selectedUsers = [user('a'), user('b')];
      editor.removeUser(user('a'));
      expect(editor.selectedUsers.map((u) => u.id)).toEqual(['b']);
    });

    it('shows the display name, else the full name, else the login name', () => {
      const { editor } = ready();
      expect(editor.displayName(user('a', { displayName: 'Dee' }))).toBe('Dee');
      expect(editor.displayName(user('a', { fullName: { firstName: 'A', middleName: null, lastName: 'B' } as never }))).toBe('A B');
      expect(editor.displayName(user('a'))).toBe('user-a');
    });
  });

  describe('creating a ladder', () => {
    it('saves it, selects it and clears the box', () => {
      const { editor, api, emitted } = ready();
      editor.newLadderName = '  Support ';
      editor.createLadder();
      expect(api.addChange).toHaveBeenCalledWith('d1', 'LADDER_CREATE', { name: 'Support' });
      expect(editor.newLadderName).toBe('');
      expect(editor.ladderId).toBe('gen-LADDER_CREATE');
      expect(emitted[0].map((c) => c.id)).toEqual(['new-LADDER_CREATE']);
      expect(editor.busy).toBe(false);
    });

    it('ignores a blank name or a busy editor', () => {
      const { editor, api } = ready();
      editor.newLadderName = '   ';
      editor.createLadder();
      editor.newLadderName = 'X';
      editor.busy = true;
      editor.createLadder();
      expect(api.addChange).not.toHaveBeenCalled();
    });

    it('reports a failure with the server detail and clears busy', () => {
      const { editor, api, notify } = ready();
      api.addChange.mockReturnValue(throwError(() => ({ error: { detail: 'Name taken' } })));
      editor.newLadderName = 'X';
      editor.createLadder();
      expect(notify.error).toHaveBeenCalledWith('Name taken');
      expect(editor.busy).toBe(false);
    });
  });

  describe('creating a job title', () => {
    it('ranks it after the highest existing one and selects it', () => {
      const { editor, api } = ready();
      editor.newPositionName = ' Principal ';
      editor.newPositionManagerial = true;
      editor.createPosition();
      expect(api.addChange).toHaveBeenCalledWith('d1', 'POSITION_UPSERT', { ladderId: 'l1', name: 'Principal', rank: 3, managerial: true });
      expect(editor.positionId).toBe('gen-POSITION_UPSERT');
      expect(editor.newPositionName).toBe('');
      expect(editor.newPositionRank).toBeNull();
      expect(editor.newPositionManagerial).toBe(false);
    });

    it('uses an explicit rank', () => {
      const { editor, api } = ready();
      editor.newPositionName = 'Intern';
      editor.newPositionRank = 0;
      editor.createPosition();
      expect(api.addChange.mock.calls[0][2]).toMatchObject({ rank: 0 });
    });

    it('needs a name and a ladder', () => {
      const { editor, api } = ready({ ladderId: null });
      editor.newPositionName = 'X';
      editor.createPosition();
      editor.ladderId = 'l1';
      editor.newPositionName = '';
      editor.createPosition();
      expect(api.addChange).not.toHaveBeenCalled();
    });

    it('reports a failure', () => {
      const { editor, api, notify } = ready();
      api.addChange.mockReturnValue(throwError(() => ({})));
      editor.newPositionName = 'X';
      editor.createPosition();
      expect(notify.error).toHaveBeenCalledWith('orgStructure.tree.person.failed');
    });
  });

  describe('adding people', () => {
    function withSelection(users: UamUser[], nodeOver: Partial<DraftNode> = {}) {
      const ctx = ready(nodeOver);
      ctx.editor.positionId = 'p1';
      ctx.editor.selectedUsers = users;
      return ctx;
    }

    it('does nothing until everything is chosen', () => {
      const { editor, api } = ready();
      editor.add();
      expect(api.addChange).not.toHaveBeenCalled();
    });

    it('creates the employee for a user that is not one yet, then assigns them with display labels', () => {
      const { editor, api, notify, emitted } = withSelection([user('a', { employeeCode: 'EC1' })]);
      api.createEmployee.mockReturnValue(of(employee('e1', 'a')));
      editor.add();
      expect(api.searchEmployees).toHaveBeenCalledWith('EC1', expect.any(Number));
      expect(api.createEmployee).toHaveBeenCalledWith(expect.objectContaining({
        employeeCode: 'EC1', fullName: 'user-a', email: 'a@x.com', status: NEW_EMPLOYEE_STATUS, userId: 'a',
      }));
      const assign = api.addChange.mock.calls.find((c) => c[1] === 'ASSIGN')!;
      expect(assign[2]).toMatchObject({
        employeeId: 'e1', unitId: 'u1', positionId: 'p1', primary: true,
        __labels: { employeeId: 'Emp e1', unitId: 'Ops', positionId: 'Junior' },
      });
      expect(notify.success).toHaveBeenCalledWith('orgStructure.tree.person.addedMany{"count":1}');
      expect(emitted.at(-1)!.map((c) => c.type)).toEqual(['ASSIGN']);
      expect(editor.selectedUsers).toEqual([]);
      expect(editor.busy).toBe(false);
    });

    it('reuses an employee already linked to the user and falls back to the login name for the code', () => {
      const { editor, api } = withSelection([user('a')]);
      api.searchEmployees.mockReturnValue(of([employee('other', 'zzz'), employee('e7', 'a')]));
      editor.add();
      expect(api.searchEmployees).toHaveBeenCalledWith('user-a', expect.any(Number));
      expect(api.createEmployee).not.toHaveBeenCalled();
      expect(api.addChange.mock.calls[0][2]).toMatchObject({ employeeId: 'e7' });
    });

    it('saves a ladder change first when the department uses another ladder', () => {
      const { editor, api, emitted } = withSelection([user('a')]);
      editor.ladderId = 'l2';
      editor.add();
      expect(api.addChange.mock.calls.map((c) => c[1])).toEqual(['UNIT_SET_LADDER', 'ASSIGN']);
      expect(api.addChange.mock.calls[0][2]).toMatchObject({ unitId: 'u1', ladderId: 'l2', __labels: { unitId: 'Ops', ladderId: 'Ops' } });
      expect(emitted.at(-1)!.map((c) => c.type)).toEqual(['UNIT_SET_LADDER', 'ASSIGN']);
    });

    it('makes a single person the head when asked and the department has none', () => {
      const { editor, api } = withSelection([user('a')]);
      editor.makeHead = true;
      editor.add();
      const head = api.addChange.mock.calls.find((c) => c[1] === 'UNIT_SET_HEAD')!;
      expect(head[2]).toMatchObject({ unitId: 'u1', employeeId: 'e-new', __labels: { unitId: 'Ops', employeeId: 'user-a' } });
    });

    it('does not make anyone head when the department already has one, or several people are added', () => {
      const withHead = withSelection([user('a')], { headEmployeeId: 'boss' });
      withHead.editor.makeHead = true;
      withHead.editor.add();
      expect(withHead.api.addChange.mock.calls.map((c) => c[1])).toEqual(['ASSIGN']);

      const several = withSelection([user('a'), user('b')]);
      several.editor.makeHead = true;
      several.editor.add();
      expect(several.api.addChange.mock.calls.map((c) => c[1])).toEqual(['ASSIGN', 'ASSIGN']);
    });

    it('adds several people one after another', () => {
      const { editor, notify } = withSelection([user('a'), user('b'), user('c')]);
      editor.add();
      expect(notify.success).toHaveBeenCalledWith('orgStructure.tree.person.addedMany{"count":3}');
    });

    it('reports a person who fails, skips them, and keeps only them selected', () => {
      const { editor, api, notify, emitted } = withSelection([user('a'), user('b')]);
      api.addChange.mockImplementation((_d, type, payload) => {
        if (type === 'ASSIGN' && payload['employeeId'] === 'e-bad') return throwError(() => ({ error: { detail: 'Already in Sales' } }));
        return of(change('ok-' + String(payload['employeeId']), type, payload, 5));
      });
      api.createEmployee.mockImplementation(((req: { userId: string }) => of(employee(req.userId === 'a' ? 'e-bad' : 'e-good', req.userId))) as never);
      editor.add();
      expect(notify.error).toHaveBeenCalledWith('user-a: Already in Sales');
      expect(notify.success).toHaveBeenCalledWith('orgStructure.tree.person.addedMany{"count":1}');
      expect(editor.selectedUsers.map((u) => u.id)).toEqual(['a']);
      expect(emitted.at(-1)!.map((c) => c.id)).toEqual(['ok-e-good']);
    });

    it('uses the translated fallback for a failure without detail, and does not claim success', () => {
      const { editor, api, notify } = withSelection([user('a')]);
      api.addChange.mockReturnValue(throwError(() => ({})));
      editor.add();
      expect(notify.error).toHaveBeenCalledWith('user-a: orgStructure.tree.person.failed');
      expect(notify.success).not.toHaveBeenCalled();
    });

    it('refuses a person already assigned to a different department in the draft', () => {
      const { editor, api, notify } = withSelection([user('a')]);
      api.createEmployee.mockReturnValue(of(employee('e1', 'a')));
      editor.changes = [
        change('c1', 'ASSIGN', { employeeId: 'e1', unitId: 'sales', __labels: { unitId: 'Sales' } }, 1),
      ];
      editor.add();
      expect(api.addChange).not.toHaveBeenCalled();
      expect(notify.error).toHaveBeenCalledWith('user-a: orgStructure.tree.person.alreadyAssigned{"person":"Emp e1","department":"Sales"}');
    });

    it('falls back to the department id when the earlier assignment carried no label', () => {
      const { editor, notify, api } = withSelection([user('a')]);
      api.createEmployee.mockReturnValue(of(employee('e1', 'a')));
      editor.changes = [change('c1', 'ASSIGN', { employeeId: 'e1', unitId: 'sales' }, 1)];
      editor.add();
      expect(notify.error.mock.calls[0][0]).toContain('"department":"sales"');
    });

    it('allows a person who was unassigned from the other department, or is already in this one', () => {
      const unassigned = withSelection([user('a')]);
      unassigned.api.createEmployee.mockReturnValue(of(employee('e1', 'a')));
      unassigned.editor.changes = [
        change('c1', 'ASSIGN', { employeeId: 'e1', unitId: 'sales' }, 1),
        change('c2', 'UNASSIGN', { employeeId: 'e1', unitId: 'sales' }, 2),
        change('c3', 'UNIT_RENAME', { id: 'x' }, 3),
      ];
      unassigned.editor.add();
      expect(unassigned.api.addChange.mock.calls.map((c) => c[1])).toEqual(['ASSIGN']);

      const here = withSelection([user('a')]);
      here.api.createEmployee.mockReturnValue(of(employee('e1', 'a')));
      here.editor.changes = [change('c1', 'ASSIGN', { employeeId: 'e1', unitId: 'u1' }, 1)];
      here.editor.add();
      expect(here.api.addChange).toHaveBeenCalledTimes(1);
    });

    it('stops with the error when the ladder change itself fails, keeping what was saved', () => {
      const { editor, api, notify, emitted } = withSelection([user('a')]);
      editor.ladderId = 'l2';
      api.addChange.mockReturnValue(throwError(() => ({ error: { detail: 'Ladder refused' } })));
      editor.add();
      expect(notify.error).toHaveBeenCalledWith('Ladder refused');
      expect(emitted).toHaveLength(0);
      expect(editor.busy).toBe(false);
      expect(editor.selectedUsers).toHaveLength(1);
    });
  });

  describe('browsing users', () => {
    it('opens the lookup with the current selection and applies the ticked users', () => {
      const { editor, lookup, cdr } = ready();
      editor.selectedUsers = [user('a')];
      lookup.open.mockReturnValue(of([user('a'), user('b')]));
      editor.browseUsers();
      const config = lookup.open.mock.calls[0][0];
      expect(config.multiple).toBe(true);
      expect(config.selected).toEqual([user('a')]);
      expect(config.idOf(user('b'))).toBe('b');
      expect(config.chipLabel(user('b', { displayName: 'Bee' }))).toBe('Bee');
      expect(config.columns.map((c: { key: string }) => c.key)).toEqual(['userName', 'displayName', 'email']);
      expect(config.columns[1].render(user('b', { displayName: 'Bee' }))).toBe('Bee');
      expect(editor.selectedUsers.map((u) => u.id)).toEqual(['a', 'b']);
      expect(cdr.detectChanges).toHaveBeenCalled();
    });

    it('keeps the selection when the dialog is dismissed', () => {
      const { editor, lookup } = ready();
      editor.selectedUsers = [user('a')];
      lookup.open.mockReturnValue(of(null));
      editor.browseUsers();
      expect(editor.selectedUsers.map((u) => u.id)).toEqual(['a']);
    });

    it('searches only the session organization\'s users, paged, narrowed by the typed text', () => {
      const { editor, lookup, searchApi } = ready();
      searchApi.getAll.mockReturnValue(of({ content: [{ modelKey: 'u1', text1: 'asha' }], totalElements: 41 }));
      lookup.open.mockReturnValue(of(null));
      editor.browseUsers();
      const fetch = lookup.open.mock.calls[0][0].fetch as (q: string, page: number, size: number) => Observable<{ items: UamUser[]; total: number }>;
      let page: { items: UamUser[]; total: number } | undefined;
      fetch(' asha ', 2, 10).subscribe((p) => (page = p));
      expect(searchApi.getAll).toHaveBeenCalledWith(
        'user',
        [{ field: 'text5', operator: 'EQ', value: 't1' }],
        [
          { field: 'text1', operator: 'LIKE', value: 'asha' },
          { field: 'text2', operator: 'LIKE', value: 'asha' },
          { field: 'text7', operator: 'LIKE', value: 'asha' },
        ],
        2, 10, 'text1',
      );
      expect(page?.total).toBe(41);
      expect(page?.items[0].id).toBe('u1');
    });

    it('sends no text conditions for an empty query', () => {
      const { editor, lookup, searchApi } = ready();
      searchApi.getAll.mockReturnValue(of({ content: [], totalElements: 0 }));
      lookup.open.mockReturnValue(of(null));
      editor.browseUsers();
      lookup.open.mock.calls[0][0].fetch('  ', 0, 10).subscribe();
      expect(searchApi.getAll.mock.calls[0][2]).toEqual([]);
    });

    it('returns nothing, without calling the search, when the session has no organization', () => {
      const { editor, lookup, searchApi } = setup({}, null);
      lookup.open.mockReturnValue(of(null));
      editor.browseUsers();
      let page: unknown;
      lookup.open.mock.calls[0][0].fetch('a', 0, 10).subscribe((p: unknown) => (page = p));
      expect(page).toEqual({ items: [], total: 0 });
      expect(searchApi.getAll).not.toHaveBeenCalled();
    });
  });

  it('closing the editor emits closed', () => {
    const { editor } = ready();
    const closed = vi.fn();
    editor.closed.subscribe(closed);
    editor.cancel();
    expect(closed).toHaveBeenCalledTimes(1);
  });
});
