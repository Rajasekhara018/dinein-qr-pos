import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { UamService } from '../../shared/services/uam-service';
import { LOGIN_RUN_PAGE_SIZE, ORG_LOGIN_PRIVILEGES, ORG_PAGE_SIZE, ROLE_OPTIONS_LIMIT } from '../org-structure.constants';
import { OrgLoginCandidate, OrgLoginRun, OrgRoleRule } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { OrgLogins } from './org-logins';

const rule = (id: string, over: Partial<OrgRoleRule> = {}): OrgRoleRule =>
  ({ id, unitId: 'u1', unitName: 'Ops', positionId: null, positionName: null, roleCode: 'AGENT', active: true, ...over });
const candidate = (id: string, over: Partial<OrgLoginCandidate> = {}): OrgLoginCandidate =>
  ({ employeeId: id, employeeCode: 'C' + id, fullName: 'Name ' + id, email: id + '@x.io', unitId: 'u1', positionId: 'p1', roles: ['AGENT'], issues: [], ready: true, ...over });
const run = (over: Partial<OrgLoginRun> = {}): OrgLoginRun => ({ created: 1, skipped: 0, failed: 0, results: [], nextAfterEmployeeId: null, ...over });

describe('OrgLogins', () => {
  const api = {
    roleRules: vi.fn((): Observable<OrgRoleRule[]> => of([rule('r1'), rule('r2', { unitName: null, positionName: 'Lead' })])),
    createRoleRule: vi.fn((): Observable<unknown> => of({})),
    deleteRoleRule: vi.fn((): Observable<void> => of(undefined)),
    loginCandidates: vi.fn((): Observable<OrgLoginCandidate[]> => of([candidate('1'), candidate('2', { ready: false, issues: ['NO_EMAIL', 'EMAIL_IN_USE:a@b.c', 'WEIRD'] })])),
    createLogins: vi.fn((_request?: unknown): Observable<OrgLoginRun> => of(run())),
  };
  const uam = {
    list: vi.fn((): Observable<unknown> => of({ content: [{ code: 'AGENT', name: 'Agent', active: true }, { code: 'OLD', name: null, active: false }] })),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  let granted: Set<string> | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(): ComponentFixture<OrgLogins> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrgLogins],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: OrgStructureService, useValue: api },
        { provide: UamService, useValue: uam },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      orgStructure: { logins: { issues: { NO_EMAIL: 'No email' }, outcome: { SKIPPED: 'Skipped', FAILED: 'Failed' }, ready: 'Ready', anyDepartment: 'Any department', anyPosition: 'Any position' } },
    });
    translate.use('en');
    const fixture = TestBed.createComponent(OrgLogins);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    dialogs.confirm.mockImplementation(() => of(true));
  });

  describe('loading', () => {
    it('loads the rules and only the active roles for the dropdown', () => {
      const c = create().componentInstance;
      expect(c.rules.map((r) => r.id)).toEqual(['r1', 'r2']);
      expect(uam.list).toHaveBeenCalledWith('/roles', 0, ROLE_OPTIONS_LIMIT);
      expect(c.roles.map((r) => r.code)).toEqual(['AGENT']);
    });

    it('renders any-department / any-position when a rule leaves one open', () => {
      const c = create().componentInstance;
      const [dept, pos] = c.ruleColumns;
      expect(dept.render?.(c.rules[0])).toBe('Ops');
      expect(dept.render?.(c.rules[1])).toBe('Any department');
      expect(pos.render?.(c.rules[0])).toBe('Any position');
      expect(pos.render?.(c.rules[1])).toBe('Lead');
    });

    it('reports a failed rule load, and survives a failed role load', () => {
      api.roleRules.mockReturnValueOnce(throwError(() => ({ error: { detail: 'No rules' } })));
      uam.list.mockReturnValueOnce(throwError(() => new Error('x')));
      const c = create().componentInstance;
      expect(notify.error).toHaveBeenCalledWith('No rules');
      expect(c.roles).toEqual([]);
    });

    it('treats a role page without content as empty', () => {
      uam.list.mockReturnValueOnce(of({}));
      expect(create().componentInstance.roles).toEqual([]);
    });
  });

  describe('rules', () => {
    it('needs a role and a department or a position before a rule can be added', () => {
      const c = create().componentInstance;
      expect(c.canAddRule).toBe(false);
      c.newRuleRole = 'AGENT';
      expect(c.canAddRule).toBe(false);
      c.newRulePosition = { id: 'p1', label: 'Lead' };
      expect(c.canAddRule).toBe(true);
      c.addRule();
      expect(api.createRoleRule).toHaveBeenCalledWith(null, 'p1', 'AGENT');
    });

    it('adds a rule, clears the form and reloads the rules', () => {
      const c = create().componentInstance;
      c.newRuleUnit = { id: 'u1', label: 'Ops', ladderId: 'l1' };
      c.newRuleRole = 'AGENT';
      expect(c.positionLadderId).toBe('l1');
      c.addRule();
      expect(api.createRoleRule).toHaveBeenCalledWith('u1', null, 'AGENT');
      expect(c.newRuleUnit).toBeNull();
      expect(c.newRuleRole).toBe('');
      expect(notify.success).toHaveBeenCalledWith('orgStructure.logins.messages.ruleAdded');
      expect(api.roleRules).toHaveBeenCalledTimes(2);
    });

    it('does nothing when the form is incomplete, and reports a failure', () => {
      const c = create().componentInstance;
      c.addRule();
      expect(api.createRoleRule).not.toHaveBeenCalled();
      expect(c.positionLadderId).toBeNull();
      c.newRuleUnit = { id: 'u1', label: 'Ops' };
      c.newRuleRole = 'AGENT';
      api.createRoleRule.mockReturnValueOnce(throwError(() => ({})));
      c.addRule();
      expect(notify.error).toHaveBeenCalledWith('orgStructure.logins.messages.ruleFailed');
      expect(c.busy).toBe(false);
    });

    it('deletes a rule only after a danger confirmation', () => {
      const c = create().componentInstance;
      c.deleteRule(c.rules[0]);
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger' }));
      expect(api.deleteRoleRule).toHaveBeenCalledWith('r1');
      expect(notify.success).toHaveBeenCalledWith('orgStructure.logins.messages.ruleDeleted');
      dialogs.confirm.mockImplementation(() => of(false));
      c.deleteRule(c.rules[1]);
      expect(api.deleteRoleRule).toHaveBeenCalledTimes(1);
    });

    it('reports a failed delete', () => {
      const c = create().componentInstance;
      api.deleteRoleRule.mockReturnValueOnce(throwError(() => ({ error: { detail: 'In use' } })));
      c.deleteRule(c.rules[0]);
      expect(notify.error).toHaveBeenCalledWith('In use');
    });
  });

  describe('preview', () => {
    it('lists candidates with translated issues and counts the ready ones', () => {
      const c = create().componentInstance;
      expect(c.previewed).toBe(false);
      c.preview();
      expect(api.loginCandidates).toHaveBeenCalledWith(null, null, ORG_PAGE_SIZE);
      expect(c.previewed).toBe(true);
      expect(c.readyCount).toBe(1);
      expect(c.candidateRows[0].status).toBe('Ready');
      // known code translated, unknown translation falls back to the raw code
      expect(c.candidateRows[1].status).toBe('No email; EMAIL_IN_USE:a@b.c; WEIRD');
      expect(c.candidateRows[1].roles).toBe('AGENT');
    });

    it('filters by department and appends the next page after the last candidate', () => {
      const c = create().componentInstance;
      c.filterUnit = { id: 'u9', label: 'X' };
      api.loginCandidates.mockReturnValueOnce(of(Array.from({ length: ORG_PAGE_SIZE }, (_, i) => candidate('c' + i))));
      c.preview();
      expect(api.loginCandidates).toHaveBeenLastCalledWith('u9', null, ORG_PAGE_SIZE);
      expect(c.hasMoreCandidates).toBe(true);
      api.loginCandidates.mockReturnValueOnce(of([candidate('z')]));
      c.preview(true);
      expect(api.loginCandidates).toHaveBeenLastCalledWith('u9', 'c' + (ORG_PAGE_SIZE - 1), ORG_PAGE_SIZE);
      expect(c.candidates).toHaveLength(ORG_PAGE_SIZE + 1);
      expect(c.hasMoreCandidates).toBe(false);
    });

    it('reports a failed preview', () => {
      const c = create().componentInstance;
      api.loginCandidates.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Bad' } })));
      c.preview();
      expect(notify.error).toHaveBeenCalledWith('Bad');
      expect(c.loadingCandidates).toBe(false);
    });
  });

  describe('creating logins', () => {
    it('creates only for the ready people listed, then refreshes the preview', () => {
      const c = create().componentInstance;
      c.preview();
      api.createLogins.mockReturnValueOnce(of(run({ created: 1, skipped: 1, results: [
        { employeeId: '2', employeeCode: 'C2', outcome: 'SKIPPED', userId: null, code: 'NO_EMAIL', roles: [] },
        { employeeId: '1', employeeCode: 'C1', outcome: 'CREATED', userId: 'u', code: null, roles: [] },
      ] })));
      c.createListed();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ message: 'orgStructure.logins.confirmDialog.message' }));
      expect(api.createLogins).toHaveBeenCalledWith({ employeeIds: ['1'] });
      expect(c.totals).toEqual({ created: 1, skipped: 1, failed: 0 });
      expect(c.problems).toEqual([{ employeeCode: 'C2', outcome: 'Skipped', reason: 'No email' }]);
      expect(c.running).toBe(false);
      expect(api.loginCandidates).toHaveBeenCalledTimes(2);
    });

    it('does nothing when nobody is ready or the confirmation is declined', () => {
      const c = create().componentInstance;
      c.createListed();
      expect(dialogs.confirm).not.toHaveBeenCalled();
      c.preview();
      dialogs.confirm.mockImplementation(() => of(false));
      c.createListed();
      c.createAll();
      expect(api.createLogins).not.toHaveBeenCalled();
    });

    it('reports a failed batch and stops running', () => {
      const c = create().componentInstance;
      c.preview();
      api.createLogins.mockReturnValueOnce(throwError(() => ({ error: { detail: 'Boom' } })));
      c.createListed();
      expect(notify.error).toHaveBeenCalledWith('Boom');
      expect(c.running).toBe(false);
    });

    it('runs through a whole department page after page, adding up the totals', () => {
      const c = create().componentInstance;
      c.filterUnit = { id: 'u1', label: 'Ops' };
      api.createLogins
        .mockReturnValueOnce(of(run({ created: 2, nextAfterEmployeeId: 'e2', results: [{ employeeId: 'a', employeeCode: 'A', outcome: 'FAILED', userId: null, code: 'CREATE_FAILED', roles: [] }] })))
        .mockReturnValueOnce(of(run({ created: 3, failed: 1 })));
      c.createAll();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ message: 'orgStructure.logins.confirmDialog.messageAll' }));
      expect(api.createLogins.mock.calls.map((x) => x[0])).toEqual([
        { unitId: 'u1', afterId: undefined, limit: LOGIN_RUN_PAGE_SIZE },
        { unitId: 'u1', afterId: 'e2', limit: LOGIN_RUN_PAGE_SIZE },
      ]);
      expect(c.totals).toEqual({ created: 5, skipped: 0, failed: 1 });
      expect(c.problems[0]).toEqual({ employeeCode: 'A', outcome: 'Failed', reason: 'CREATE_FAILED' });
      expect(c.running).toBe(false);
    });

    it('stops after the current page when the run is cancelled', () => {
      const c = create().componentInstance;
      const first = new Subject<OrgLoginRun>();
      api.createLogins.mockReturnValueOnce(first);
      c.createAll();
      expect(c.running).toBe(true);
      c.cancelRun();
      first.next(run({ nextAfterEmployeeId: 'more' }));
      expect(api.createLogins).toHaveBeenCalledTimes(1);
      expect(c.running).toBe(false);
    });

    it('reports a failure in the middle of a run', () => {
      const c = create().componentInstance;
      api.createLogins.mockReturnValueOnce(of(run({ nextAfterEmployeeId: 'n' }))).mockReturnValueOnce(throwError(() => ({})));
      c.createAll();
      expect(notify.error).toHaveBeenCalledWith('orgStructure.logins.messages.createFailed');
      expect(c.running).toBe(false);
    });

    it('ignores results arriving when no run is active, and cancels a running request when destroyed', () => {
      const c = create().componentInstance;
      const pending = new Subject<OrgLoginRun>();
      api.createLogins.mockReturnValueOnce(pending);
      c.createAll();
      c.ngOnDestroy();
      pending.next(run({ nextAfterEmployeeId: 'x' }));
      expect(api.createLogins).toHaveBeenCalledTimes(1);
    });
  });

  describe('privileges', () => {
    const buttons = (el: HTMLElement) => Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim());

    it('a full operator sees the rule form, delete buttons and both create buttons', () => {
      const el: HTMLElement = create().nativeElement;
      const texts = buttons(el);
      expect(texts).toContain('orgStructure.logins.addRule');
      expect(texts.some((t) => t?.startsWith('orgStructure.logins.createAll'))).toBe(true);
      expect(texts.some((t) => t?.startsWith('orgStructure.logins.createListed'))).toBe(true);
      expect(el.querySelectorAll('.row-action-btn').length).toBe(2);
    });

    it('someone who can only list rules sees them but no change actions', () => {
      granted = new Set([ORG_LOGIN_PRIVILEGES.ruleList]);
      const el: HTMLElement = create().nativeElement;
      const texts = buttons(el);
      expect(texts).not.toContain('orgStructure.logins.addRule');
      expect(texts.some((t) => t?.startsWith('orgStructure.logins.createAll'))).toBe(false);
      expect(el.querySelectorAll('.row-action-btn').length).toBe(0);
      expect(texts.some((t) => t?.includes('orgStructure.logins.loadPreview'))).toBe(true);
    });

    it('hides the whole rules section without the list privilege', () => {
      granted = new Set();
      const el: HTMLElement = create().nativeElement;
      expect(el.textContent).not.toContain('orgStructure.logins.rulesTitle');
      expect(el.textContent).toContain('orgStructure.logins.previewTitle');
    });

    it('lets the rule creator and deleter act when granted, and shows run progress with a stop button', () => {
      granted = new Set([ORG_LOGIN_PRIVILEGES.ruleList, ORG_LOGIN_PRIVILEGES.ruleCreate, ORG_LOGIN_PRIVILEGES.ruleDelete, ORG_LOGIN_PRIVILEGES.loginCreate]);
      const fixture = create();
      const c = fixture.componentInstance;
      c.totals = { created: 1, skipped: 2, failed: 3 };
      c.running = true;
      c.problems = [{ employeeCode: 'A', outcome: 'Failed', reason: 'x' }];
      fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('.result')?.textContent).toContain('orgStructure.logins.progress');
      expect(buttons(el).some((t) => t?.includes('orgStructure.logins.stop'))).toBe(true);
      (el.querySelector('.row-action-btn') as HTMLElement).click();
      expect(api.deleteRoleRule).toHaveBeenCalledWith('r1');
    });
  });
});
