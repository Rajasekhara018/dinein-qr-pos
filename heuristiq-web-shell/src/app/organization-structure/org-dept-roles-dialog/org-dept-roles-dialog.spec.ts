import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Observable, of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import ar from '../../../assets/i18n/ar.json';
import en from '../../../assets/i18n/en.json';
import hi from '../../../assets/i18n/hi.json';
import kn from '../../../assets/i18n/kn.json';
import ml from '../../../assets/i18n/ml.json';
import ta from '../../../assets/i18n/ta.json';
import te from '../../../assets/i18n/te.json';
import { UamRole } from '../../shared/models/uam.models';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrgRoleRulesService } from '../org-role-rules.service';
import { ORG_LOGIN_PRIVILEGES } from '../org-structure.constants';
import { OrgRoleRule } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { OrgDeptRolesDialog } from './org-dept-roles-dialog';

const rule = (id: string, over: Partial<OrgRoleRule> = {}): OrgRoleRule =>
  ({ id, unitId: 'u1', unitName: 'Tech', positionId: null, positionName: null, roleCode: 'HR_VIEW', active: true, ...over });
const role = (code: string, over: Partial<UamRole> = {}): UamRole => ({ code, name: code + ' name', active: true, ...over } as UamRole);

describe('OrgDeptRolesDialog', () => {
  const source = {
    rules: vi.fn((): Observable<OrgRoleRule[]> => of([])),
    roles: vi.fn((): Observable<UamRole[]> => of([])),
  };
  const api = {
    createRoleRule: vi.fn((_u: string | null, _p: string | null, _r: string): Observable<OrgRoleRule> => of(rule('new'))),
    deleteRoleRule: vi.fn((_id: string): Observable<void> => of(undefined)),
  };
  const ref = { close: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  const notify = { success: vi.fn(), error: vi.fn() };
  let granted: Set<string> | null;
  const access = { changes$: EMPTY, can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)) };

  function create(headcount: number | null = 12): ComponentFixture<OrgDeptRolesDialog> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrgDeptRolesDialog],
      imports: [FormsModule, HqSharedLibModule, TranslateModule.forRoot()],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { unitId: 'u1', unitName: 'Tech', headcount } },
        { provide: MatDialogRef, useValue: ref },
        { provide: OrgRoleRulesService, useValue: source },
        { provide: OrgStructureService, useValue: api },
        { provide: AppDialogService, useValue: dialogs },
        { provide: NotificationService, useValue: notify },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const fixture = TestBed.createComponent(OrgDeptRolesDialog);
    fixture.detectChanges();
    return fixture;
  }

  const el = (f: ComponentFixture<OrgDeptRolesDialog>) => f.nativeElement as HTMLElement;
  const q = (f: ComponentFixture<OrgDeptRolesDialog>, id: string) => el(f).querySelector(`[data-testid="${id}"]`);
  const refresh = (f: ComponentFixture<OrgDeptRolesDialog>) => { f.changeDetectorRef.markForCheck(); f.detectChanges(); };
  const click = (f: ComponentFixture<OrgDeptRolesDialog>, target: Element | null) => { target?.dispatchEvent(new Event('click')); refresh(f); };
  const choose = (f: ComponentFixture<OrgDeptRolesDialog>, code: string) => {
    f.componentInstance.newRole = code;
    refresh(f);
  };

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    source.rules.mockImplementation(() => of([]));
    source.roles.mockImplementation(() => of([role('HR_VIEW'), role('SALES'), role('AUDIT')]));
    api.createRoleRule.mockImplementation(() => of(rule('new')));
    api.deleteRoleRule.mockImplementation(() => of(undefined));
    dialogs.confirm.mockImplementation(() => of(true));
  });

  describe('what it shows', () => {
    it('is titled and explained, and says how many people are in the department and below', () => {
      const f = create(12);
      expect(el(f).querySelector('h2')?.id).toBe('org-dept-roles-title');
      expect(el(f).querySelector('h2')?.textContent).toContain('orgStructure.tree.rolesDialog.title');
      expect(q(f, 'roles-explain')?.textContent).toContain('orgStructure.tree.rolesDialog.explanation');
      expect(q(f, 'roles-headcount')?.textContent).toContain('orgStructure.tree.rolesDialog.headcount');
    });

    it('leaves the headcount line out when the chart does not know it', () => {
      expect(q(create(null), 'roles-headcount')).toBeNull();
    });

    it('lists only the rules of this department, with the position when a rule names one', () => {
      source.rules.mockImplementation(() => of([
        rule('a'), rule('b', { unitId: 'u2', roleCode: 'OTHER' }), rule('c', { unitId: null, positionId: 'p9', roleCode: 'NO_UNIT' }),
        rule('d', { roleCode: 'SALES', positionId: 'p1', positionName: 'Lead' }),
      ]));
      const f = create();
      const items = Array.from(el(f).querySelectorAll('[data-testid^="roles-rule-"]')).map((li) => li.getAttribute('data-testid'));
      expect(items).toEqual(['roles-rule-a', 'roles-rule-d']);
      expect(q(f, 'roles-rule-a')?.textContent).toContain('HR_VIEW');
      expect(q(f, 'roles-rule-a')?.textContent).toContain('HR_VIEW name');
      expect(q(f, 'roles-rule-a')?.querySelector('[data-testid="roles-position"]')).toBeNull();
      expect(q(f, 'roles-rule-d')?.querySelector('[data-testid="roles-position"]')?.textContent).toContain('orgStructure.tree.rolesDialog.position');
      expect(el(f).textContent).not.toContain('OTHER');
    });

    it('says so when no role is given to this department yet', () => {
      const f = create();
      expect(q(f, 'roles-empty')?.textContent).toContain('orgStructure.tree.rolesDialog.empty');
      expect(q(f, 'roles-list')).toBeNull();
    });

    it('shows a loading status first, then the rules', () => {
      const pending = new Subject<OrgRoleRule[]>();
      source.rules.mockImplementation(() => pending);
      const f = create();
      expect(q(f, 'roles-loading')?.getAttribute('role')).toBe('status');
      pending.next([rule('a')]);
      refresh(f);
      expect(q(f, 'roles-loading')).toBeNull();
      expect(q(f, 'roles-rule-a')).not.toBeNull();
    });

    it('says inline that the rules could not be read, and reads them again on request', () => {
      source.rules.mockImplementationOnce(() => throwError(() => new Error('down')));
      const f = create();
      expect(q(f, 'roles-load-error')?.getAttribute('role')).toBe('alert');
      expect(q(f, 'roles-add')).toBeNull();
      click(f, q(f, 'roles-retry'));
      expect(f.componentInstance.state).toBe('ready');
      expect(q(f, 'roles-load-error')).toBeNull();
      expect(notify.error).not.toHaveBeenCalled();
    });
  });

  describe('adding a role', () => {
    it('offers the existing roles except those already given to this department', () => {
      source.rules.mockImplementation(() => of([rule('a', { roleCode: 'HR_VIEW' }), rule('x', { unitId: 'u2', roleCode: 'AUDIT' })]));
      const f = create();
      expect(f.componentInstance.offeredRoles.map((r) => r.code)).toEqual(['SALES', 'AUDIT']);
      const options = Array.from(el(f).querySelectorAll('#org-dept-roles-select option')).map((o) => (o as HTMLOptionElement).value);
      expect(options).toEqual(['', 'SALES', 'AUDIT']);
    });

    it('still offers a role that only has a rule for one position of the department', () => {
      source.rules.mockImplementation(() => of([rule('a', { roleCode: 'SALES', positionId: 'p1', positionName: 'Lead' })]));
      expect(create().componentInstance.offeredRoles.map((r) => r.code)).toContain('SALES');
    });

    it('adds the chosen role for this department only, tells the person, reloads the rules and offers one role fewer', () => {
      const f = create();
      choose(f, 'SALES');
      source.rules.mockImplementation(() => of([rule('new', { roleCode: 'SALES' })]));
      click(f, q(f, 'roles-add-button'));
      expect(api.createRoleRule).toHaveBeenCalledWith('u1', null, 'SALES');
      expect(notify.success).toHaveBeenCalledWith('orgStructure.logins.messages.ruleAdded');
      expect(f.componentInstance.newRole).toBe('');
      expect(q(f, 'roles-rule-new')).not.toBeNull();
      expect(f.componentInstance.offeredRoles.map((r) => r.code)).not.toContain('SALES');
      expect(f.componentInstance.changed).toBe(true);
    });

    it('does nothing until a role is chosen', () => {
      const f = create();
      expect((q(f, 'roles-add-button') as HTMLButtonElement).disabled).toBe(true);
      f.componentInstance.add();
      expect(api.createRoleRule).not.toHaveBeenCalled();
    });

    it('says inline, not in a toast, why an add failed, using the server text when there is one', () => {
      const f = create();
      choose(f, 'SALES');
      api.createRoleRule.mockImplementationOnce(() => throwError(() => ({ error: { detail: 'Role not allowed' } })));
      click(f, q(f, 'roles-add-button'));
      expect(q(f, 'roles-action-error')?.textContent).toContain('Role not allowed');
      expect(q(f, 'roles-action-error')?.getAttribute('role')).toBe('alert');
      expect(notify.error).not.toHaveBeenCalled();
      expect(f.componentInstance.busy).toBe(false);
      choose(f, 'SALES');
      api.createRoleRule.mockImplementationOnce(() => throwError(() => new Error('x')));
      click(f, q(f, 'roles-add-button'));
      expect(q(f, 'roles-action-error')?.textContent).toContain('orgStructure.logins.messages.ruleFailed');
    });

    it('says when every role is already given, and when the list of roles could not be read', () => {
      source.rules.mockImplementation(() => of([rule('a', { roleCode: 'HR_VIEW' }), rule('b', { roleCode: 'SALES' }), rule('c', { roleCode: 'AUDIT' })]));
      const none = create();
      expect(q(none, 'roles-none-left')).not.toBeNull();
      expect(none.componentInstance.offeredRoles).toEqual([]);
      source.roles.mockImplementation(() => throwError(() => new Error('down')));
      const failed = create();
      expect(q(failed, 'roles-roles-failed')?.textContent).toContain('orgStructure.tree.rolesDialog.rolesFailed');
      expect(q(failed, 'roles-none-left')).toBeNull();
      expect(failed.componentInstance.rolesFailed).toBe(true);
    });

    it('names a role in the list by its code alone when it has no name', () => {
      source.roles.mockImplementation(() => of([role('HR_VIEW', { name: null })]));
      source.rules.mockImplementation(() => of([rule('a')]));
      const f = create();
      expect(f.componentInstance.roleName('HR_VIEW')).toBeNull();
      expect(f.componentInstance.roleName('UNKNOWN')).toBeNull();
    });
  });

  describe('removing a role', () => {
    beforeEach(() => source.rules.mockImplementation(() => of([rule('a'), rule('b', { roleCode: 'SALES' })])));

    it('asks first, then removes the rule, tells the person and reloads', () => {
      const f = create();
      source.rules.mockImplementation(() => of([rule('b', { roleCode: 'SALES' })]));
      click(f, q(f, 'roles-rule-a')?.querySelector('[data-testid="roles-delete"]') ?? null);
      expect(dialogs.confirm).toHaveBeenCalledTimes(1);
      expect(api.deleteRoleRule).toHaveBeenCalledWith('a');
      expect(notify.success).toHaveBeenCalledWith('orgStructure.logins.messages.ruleDeleted');
      expect(q(f, 'roles-rule-a')).toBeNull();
      expect(f.componentInstance.offeredRoles.map((r) => r.code)).toContain('HR_VIEW');
    });

    it('gives each delete button a name that says which role it removes', () => {
      const f = create();
      expect(q(f, 'roles-rule-a')?.querySelector('button')?.getAttribute('aria-label')).toContain('orgStructure.tree.rolesDialog.removeLabel');
    });

    it('removes nothing when the question is answered no', () => {
      dialogs.confirm.mockImplementation(() => of(false));
      const f = create();
      click(f, q(f, 'roles-rule-a')?.querySelector('[data-testid="roles-delete"]') ?? null);
      expect(api.deleteRoleRule).not.toHaveBeenCalled();
      expect(f.componentInstance.changed).toBe(false);
    });

    it('says inline why a removal failed', () => {
      api.deleteRoleRule.mockImplementationOnce(() => throwError(() => ({ error: { detail: 'Rule is in use' } })));
      const f = create();
      click(f, q(f, 'roles-rule-a')?.querySelector('[data-testid="roles-delete"]') ?? null);
      expect(q(f, 'roles-action-error')?.textContent).toContain('Rule is in use');
      expect(notify.success).not.toHaveBeenCalled();
    });
  });

  describe('privileges', () => {
    beforeEach(() => source.rules.mockImplementation(() => of([rule('a')])));

    it('shows no add control and no delete button to someone who may only list the rules', () => {
      granted = new Set([ORG_LOGIN_PRIVILEGES.ruleList]);
      const f = create();
      expect(q(f, 'roles-rule-a')).not.toBeNull();
      expect(q(f, 'roles-add')).toBeNull();
      expect(q(f, 'roles-delete')).toBeNull();
      expect(q(f, 'roles-logins-link')).toBeNull();
    });

    it('shows add without delete, or delete without add, as each is granted', () => {
      granted = new Set([ORG_LOGIN_PRIVILEGES.ruleCreate]);
      const addOnly = create();
      expect(q(addOnly, 'roles-add')).not.toBeNull();
      expect(q(addOnly, 'roles-delete')).toBeNull();
      granted = new Set([ORG_LOGIN_PRIVILEGES.ruleDelete]);
      const deleteOnly = create();
      expect(q(deleteOnly, 'roles-add')).toBeNull();
      expect(q(deleteOnly, 'roles-delete')).not.toBeNull();
    });
  });

  describe('closing and the link to the logins', () => {
    it('links to the Org logins screen only for someone who may create logins, and closes the dialog on the way', () => {
      granted = new Set([ORG_LOGIN_PRIVILEGES.loginCreate]);
      const f = create();
      const link = q(f, 'roles-logins-link');
      expect(link).not.toBeNull();
      expect(f.componentInstance.loginsRoute).toBe('/org-structure/logins');
      link?.dispatchEvent(new Event('click'));
      expect(ref.close).toHaveBeenCalledWith(false);
      granted = new Set();
      expect(q(create(), 'roles-logins-link')).toBeNull();
    });

    it('tells the opener whether anything changed when it is closed', () => {
      const f = create();
      click(f, q(f, 'roles-close'));
      expect(ref.close).toHaveBeenLastCalledWith(false);
      choose(f, 'SALES');
      click(f, q(f, 'roles-add-button'));
      click(f, q(f, 'roles-close'));
      expect(ref.close).toHaveBeenLastCalledWith(true);
    });
  });

  describe('texts', () => {
    it('has the dialog texts in all 7 languages with the same words to fill in', () => {
      const keys = ['rolesOf', 'rolesDialog.title', 'rolesDialog.explanation', 'rolesDialog.headcount', 'rolesDialog.loading', 'rolesDialog.loadError',
        'rolesDialog.retry', 'rolesDialog.empty', 'rolesDialog.position', 'rolesDialog.removeLabel', 'rolesDialog.addLabel', 'rolesDialog.pick',
        'rolesDialog.add', 'rolesDialog.rolesFailed', 'rolesDialog.noneLeft', 'rolesDialog.createLogins', 'rolesDialog.close'];
      const languages: Record<string, unknown> = { en, hi, ta, te, kn, ml, ar };
      const read = (lang: string, key: string): string => key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part],
        (languages[lang] as { orgStructure: { tree: unknown } }).orgStructure.tree) as string;
      const params = (text: string) => (text.match(/{{\s*\w+\s*}}/g) ?? []).map((p) => p.replace(/\s/g, '')).sort();
      keys.forEach((key) => {
        const english = read('en', key);
        expect(english, key).toBeTruthy();
        Object.keys(languages).filter((l) => l !== 'en').forEach((lang) => {
          const text = read(lang, key);
          expect(text, lang + ' ' + key).toBeTruthy();
          expect(text, lang + ' ' + key).not.toBe(english);
          expect(params(text), lang + ' ' + key).toEqual(params(english));
        });
      });
    });
  });
});
