import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HQ_CONFIG, HqDocumentService, HqPrivilegeAccessService, HqSharedLibModule, HqTimezoneService } from 'hq-shared-lib';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AppDialogService } from '../../shared/services/app-dialog-service';
import { NotificationService } from '../../shared/services/notification-service';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import {
  IMPORT_FACT_MAX_LADDERS, IMPORT_FACT_PRIVILEGES, IMPORT_POLL_INTERVAL_MS, IMPORT_STEP_SESSION_KEY, ORG_DEPARTMENT_IMPORT_PRIVILEGES, ORG_IMPORT_PRIVILEGES, ORG_LADDER_IMPORT_PRIVILEGES,
} from '../org-structure.constants';
import { OrgImportBatch, OrgLadder, OrgPosition, OrgUnit } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';
import { OrgImport } from './org-import';

const batch = (over: Partial<OrgImportBatch> = {}): OrgImportBatch =>
  ({ id: 'b1', status: 'APPLIED', fileName: 'f.csv', totalRows: 3, validRows: 3, errorRows: 0, createdCount: 3, updatedCount: 0, message: null, createdBy: 'u', createdAt: '2026-01-01T00:00:00Z', appliedAt: null, ...over });
const ladder = (id: string): OrgLadder => ({ id, name: 'Ladder ' + id });
const position = (id: string): OrgPosition => ({ id, ladderId: 'x', name: 'P' + id, rank: 1, managerial: false });
const unit = (id: string, headcount: number): OrgUnit =>
  ({ id, parentId: null, code: id, name: id, unitType: null, ladderId: null, headEmployeeId: null, status: 'ACTIVE', childCount: 0, headcount });

describe('OrgImport: the setup stepper', () => {
  const api = {
    recentLadderImports: vi.fn((): Observable<OrgImportBatch[]> => of([])),
    recentDepartmentImports: vi.fn((): Observable<OrgImportBatch[]> => of([])),
    recentImports: vi.fn((): Observable<OrgImportBatch[]> => of([])),
    createLadderImport: vi.fn(), ladderImportBatch: vi.fn((): Observable<OrgImportBatch> => of(batch({ status: 'APPLIED' }))),
    ladderImportErrors: vi.fn(() => of([])), confirmLadderImport: vi.fn((): Observable<OrgImportBatch> => of(batch({ status: 'APPLYING' }))), cancelLadderImport: vi.fn(() => of(undefined)),
    importBatch: vi.fn(), importErrors: vi.fn(() => of([])), confirmImport: vi.fn((): Observable<OrgImportBatch> => of(batch({ status: 'APPLYING' }))), cancelImport: vi.fn(() => of(undefined)),
    ladders: vi.fn((): Observable<OrgLadder[]> => of([])),
    positions: vi.fn((): Observable<OrgPosition[]> => of([])),
    children: vi.fn((): Observable<OrgUnit[]> => of([])),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(true)) };
  const router = { navigate: vi.fn(() => Promise.resolve(true)) };
  let granted: Set<string> | null;
  let routeStep: string | null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };

  function create(): ComponentFixture<OrgImport> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [OrgImport],
      imports: [TranslateModule.forRoot(), HqSharedLibModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: OrgStructureService, useValue: api },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialogs },
        { provide: OrganizationContextService, useValue: { currentName$: of('Acme') } },
        { provide: HqTimezoneService, useValue: { formatLocale: (v: string) => 'local:' + v } },
        { provide: HqPrivilegeAccessService, useValue: access },
        { provide: HqDocumentService, useValue: {} },
        { provide: HQ_CONFIG, useValue: {} },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => routeStep } } } },
        { provide: Router, useValue: router },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {});
    translate.use('en');
    const fixture = TestBed.createComponent(OrgImport);
    fixture.detectChanges();
    return fixture;
  }

  const el = (fixture: ComponentFixture<OrgImport>) => fixture.nativeElement as HTMLElement;
  const refresh = (fixture: ComponentFixture<OrgImport>) => { fixture.changeDetectorRef.markForCheck(); fixture.detectChanges(); };
  const testId = (fixture: ComponentFixture<OrgImport>, id: string) => el(fixture).querySelector(`[data-testid="${id}"]`);
  const stateOf = (fixture: ComponentFixture<OrgImport>, key: string) => fixture.componentInstance.steps.find((s) => s.mode === key)?.state;

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    routeStep = null;
    sessionStorage.clear();
    dialogs.confirm.mockImplementation(() => of(true));
    api.recentLadderImports.mockImplementation(() => of([]));
    api.recentDepartmentImports.mockImplementation(() => of([]));
    api.recentImports.mockImplementation(() => of([]));
    api.ladders.mockImplementation(() => of([]));
    api.positions.mockImplementation(() => of([]));
    api.children.mockImplementation(() => of([]));
  });

  describe('which step opens', () => {
    it('is the first step when nothing has been loaded', () => {
      const fixture = create();
      expect(fixture.componentInstance.mode).toBe('LADDERS');
      expect(fixture.componentInstance.steps.map((s) => s.state)).toEqual(['NOT_STARTED', 'NOT_STARTED', 'NOT_STARTED']);
    });

    it('is the first step that is not done: ladders there, so the departments', () => {
      api.ladders.mockImplementation(() => of([ladder('a')]));
      expect(create().componentInstance.mode).toBe('DEPARTMENTS');
    });

    it('moves on to the employees once ladders and departments are there', () => {
      api.ladders.mockImplementation(() => of([ladder('a')]));
      api.children.mockImplementation(() => of([unit('HQ', 0)]));
      const fixture = create();
      expect(fixture.componentInstance.mode).toBe('EMPLOYEES');
      expect(stateOf(fixture, 'DEPARTMENTS')).toBe('DONE');
      expect(stateOf(fixture, 'EMPLOYEES')).toBe('NOT_STARTED');
    });

    it('stays on the last step when every step is done', () => {
      api.ladders.mockImplementation(() => of([ladder('a')]));
      api.children.mockImplementation(() => of([unit('HQ', 5)]));
      const fixture = create();
      expect(fixture.componentInstance.steps.map((s) => s.state)).toEqual(['DONE', 'DONE', 'DONE']);
      expect(fixture.componentInstance.mode).toBe('EMPLOYEES');
    });

    it('counts a step as in progress while a file of its kind is being applied, and as a preview not confirmed while one only waits', () => {
      api.recentLadderImports.mockImplementation(() => of([batch({ status: 'VALIDATED' })]));
      api.recentDepartmentImports.mockImplementation(() => of([batch({ status: 'APPLYING' })]));
      const fixture = create();
      expect(fixture.componentInstance.steps.map((s) => s.state)).toEqual(['PREVIEW', 'IN_PROGRESS', 'NOT_STARTED']);
      expect(fixture.componentInstance.mode).toBe('LADDERS');
    });

    it('is Done, not in progress, when an old preview was never confirmed but the data is there', () => {
      api.ladders.mockImplementation(() => of([ladder('a')]));
      api.positions.mockImplementation(() => of([position('1')]));
      api.children.mockImplementation(() => of([unit('HQ', 1002)]));
      api.recentLadderImports.mockImplementation(() => of([batch({ status: 'VALIDATED' })]));
      api.recentDepartmentImports.mockImplementation(() => of([batch({ status: 'VALIDATED' })]));
      api.recentImports.mockImplementation(() => of([batch({ status: 'VALIDATED', totalRows: 1000 })]));
      const fixture = create();
      expect(fixture.componentInstance.steps.map((s) => s.state)).toEqual(['DONE', 'DONE', 'DONE']);
    });

    it('lets an applying file win over data that is already there', () => {
      api.children.mockImplementation(() => of([unit('HQ', 5)]));
      api.recentImports.mockImplementation(() => of([batch({ status: 'APPLYING' })]));
      expect(stateOf(create(), 'EMPLOYEES')).toBe('IN_PROGRESS');
    });

    it('is Done when people are placed even though no import can be read, and Preview not confirmed with no data', () => {
      api.children.mockImplementation(() => of([unit('HQ', 40)]));
      expect(stateOf(create(), 'EMPLOYEES')).toBe('DONE');
      api.children.mockImplementation(() => of([]));
      api.recentImports.mockImplementation(() => of([batch({ status: 'VALIDATED' })]));
      expect(stateOf(create(), 'EMPLOYEES')).toBe('PREVIEW');
    });

    it('ignores a failed or cancelled file', () => {
      api.recentImports.mockImplementation(() => of([batch({ status: 'FAILED' }), batch({ status: 'CANCELLED' })]));
      expect(stateOf(create(), 'EMPLOYEES')).toBe('NOT_STARTED');
    });

    it('counts a step as done when a file of its kind was applied, even if its data cannot be read', () => {
      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create, ORG_LADDER_IMPORT_PRIVILEGES.list, ORG_IMPORT_PRIVILEGES.create]);
      api.recentLadderImports.mockImplementation(() => of([batch({ status: 'APPLIED' })]));
      const fixture = create();
      expect(stateOf(fixture, 'LADDERS')).toBe('DONE');
      expect(fixture.componentInstance.mode).toBe('EMPLOYEES');
    });

    it('does not count a cancelled or failed file as progress', () => {
      api.recentLadderImports.mockImplementation(() => of([batch({ status: 'CANCELLED' }), batch({ status: 'FAILED' })]));
      expect(stateOf(create(), 'LADDERS')).toBe('NOT_STARTED');
    });

    it('is the step asked for in the address, whatever its state', () => {
      routeStep = 'departments';
      expect(create().componentInstance.mode).toBe('DEPARTMENTS');
      routeStep = 'employees';
      expect(create().componentInstance.mode).toBe('EMPLOYEES');
      routeStep = 'ladders';
      expect(create().componentInstance.mode).toBe('LADDERS');
    });

    it('ignores an address step that does not exist or that this person may not open', () => {
      routeStep = 'nonsense';
      expect(create().componentInstance.mode).toBe('LADDERS');
      granted = new Set([ORG_IMPORT_PRIVILEGES.create]);
      routeStep = 'ladders';
      expect(create().componentInstance.mode).toBe('EMPLOYEES');
    });

    it('is the step the person chose earlier in this session, and the address wins over it', () => {
      sessionStorage.setItem(IMPORT_STEP_SESSION_KEY, 'employees');
      expect(create().componentInstance.mode).toBe('EMPLOYEES');
      routeStep = 'departments';
      expect(create().componentInstance.mode).toBe('DEPARTMENTS');
    });

    it('remembers a chosen step for the session and puts it in the address without adding a history entry', () => {
      const fixture = create();
      fixture.componentInstance.setMode('DEPARTMENTS');
      expect(sessionStorage.getItem(IMPORT_STEP_SESSION_KEY)).toBe('departments');
      expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { step: 'departments' }, queryParamsHandling: 'merge', replaceUrl: true }));
    });

    it('still works when the browser will not store anything', () => {
      const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
      const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
      const fixture = create();
      expect(fixture.componentInstance.mode).toBe('LADDERS');
      expect(() => fixture.componentInstance.setMode('EMPLOYEES')).not.toThrow();
      getItem.mockRestore();
      setItem.mockRestore();
    });

    it('reports a failed first read of the open step, and shows no list', () => {
      routeStep = 'employees';
      api.recentImports.mockImplementation(() => throwError(() => ({ error: { detail: 'Nope' } })));
      const fixture = create();
      expect(notify.error).toHaveBeenCalledWith('Nope');
      expect(fixture.componentInstance.recent).toEqual([]);
      expect(stateOf(fixture, 'EMPLOYEES')).toBe('NOT_STARTED');
    });
  });

  describe('the facts of a step', () => {
    it('names the ladders and job titles, the top-level departments and the people placed', () => {
      api.ladders.mockImplementation(() => of([ladder('a'), ladder('b')]));
      api.positions.mockImplementation(() => of([position('1'), position('2'), position('3')]));
      api.children.mockImplementation(() => of([unit('HQ', 600), unit('OPS', 400)]));
      const fixture = create();
      const facts = (mode: string) => fixture.componentInstance.steps.find((s) => s.mode === mode)?.facts;
      expect(facts('LADDERS')).toEqual([{ key: 'ladders', count: 2 }, { key: 'positions', count: 6 }]);
      expect(facts('DEPARTMENTS')).toEqual([{ key: 'departments', count: 2 }]);
      expect(facts('EMPLOYEES')).toEqual([{ key: 'employees', count: 1000 }]);
      refresh(fixture);
      expect(testId(fixture, 'import-step-ladders')?.textContent).toContain('orgStructure.import.steps.facts.ladders');
      expect(testId(fixture, 'import-step-ladders')?.textContent).toContain('orgStructure.import.steps.facts.positions');
      expect(testId(fixture, 'import-step-employees')?.textContent).toContain('orgStructure.import.steps.facts.employees');
    });

    it('shows no fact for a step with no data, and treats a department with no people as a department only', () => {
      api.children.mockImplementation(() => of([unit('HQ', 0)]));
      const fixture = create();
      expect(fixture.componentInstance.steps.find((s) => s.mode === 'LADDERS')?.facts).toEqual([]);
      expect(fixture.componentInstance.steps.find((s) => s.mode === 'EMPLOYEES')?.facts).toEqual([]);
      expect(fixture.componentInstance.steps.find((s) => s.mode === 'DEPARTMENTS')?.facts).toHaveLength(1);
    });

    it('leaves out what this person may not read, without an error and without a call', () => {
      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create, ORG_DEPARTMENT_IMPORT_PRIVILEGES.create, ORG_IMPORT_PRIVILEGES.create]);
      const fixture = create();
      expect(api.ladders).not.toHaveBeenCalled();
      expect(api.children).not.toHaveBeenCalled();
      expect(fixture.componentInstance.steps.every((s) => s.facts.length === 0)).toBe(true);
      expect(notify.error).not.toHaveBeenCalled();
    });

    it('shows the ladders without their job titles when the job titles may not be read', () => {
      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create, IMPORT_FACT_PRIVILEGES.ladders]);
      api.ladders.mockImplementation(() => of([ladder('a')]));
      const fixture = create();
      expect(api.positions).not.toHaveBeenCalled();
      expect(fixture.componentInstance.steps[0].facts).toEqual([{ key: 'ladders', count: 1 }]);
    });

    it('leaves the job titles out when there are too many ladders to count them cheaply', () => {
      api.ladders.mockImplementation(() => of(Array.from({ length: IMPORT_FACT_MAX_LADDERS + 1 }, (_, i) => ladder(String(i)))));
      const fixture = create();
      expect(api.positions).not.toHaveBeenCalled();
      expect(fixture.componentInstance.steps[0].facts).toEqual([{ key: 'ladders', count: IMPORT_FACT_MAX_LADDERS + 1 }]);
    });

    it('leaves a fact out, quietly, when reading it fails', () => {
      api.ladders.mockImplementation(() => of([ladder('a')]));
      api.positions.mockImplementation(() => throwError(() => new Error('down')));
      api.children.mockImplementation(() => throwError(() => new Error('down')));
      const fixture = create();
      expect(fixture.componentInstance.steps[0].facts).toEqual([{ key: 'ladders', count: 1 }]);
      expect(fixture.componentInstance.steps[1].facts).toEqual([]);
      api.ladders.mockImplementation(() => throwError(() => new Error('down')));
      expect(create().componentInstance.steps[0].facts).toEqual([]);
      expect(notify.error).not.toHaveBeenCalled();
    });

    it('survives a service that throws instead of returning an observable', () => {
      api.recentLadderImports.mockImplementation(() => { throw new Error('boom'); });
      const fixture = create();
      expect(stateOf(fixture, 'LADDERS')).toBe('NOT_STARTED');
    });

    it('refreshes after a confirm, so the numbers follow what was loaded', () => {
      routeStep = 'employees';
      const fixture = create();
      expect(fixture.componentInstance.steps[2].facts).toEqual([]);
      api.children.mockImplementation(() => of([unit('HQ', 12)]));
      fixture.componentInstance.open(batch({ status: 'VALIDATED', errorRows: 0 }));
      fixture.componentInstance.confirm();
      expect(fixture.componentInstance.steps[2].facts).toEqual([{ key: 'employees', count: 12 }]);
    });
  });

  describe('keeping the stepper up to date without a reload', () => {
    const states = (fixture: ComponentFixture<OrgImport>) => fixture.componentInstance.steps.map((s) => s.state);

    it('moves from Preview not confirmed to In progress on confirm and to Done when a poll sees the file applied', () => {
      vi.useFakeTimers();
      routeStep = 'employees';
      const preview = batch({ id: 'e1', status: 'VALIDATED', errorRows: 0 });
      api.recentImports.mockImplementation(() => of([preview]));
      const fixture = create();
      expect(stateOf(fixture, 'EMPLOYEES')).toBe('PREVIEW');
      fixture.componentInstance.open(preview);
      // confirming: the server now reports the file as applying, and nothing is placed yet
      api.recentImports.mockImplementation(() => of([batch({ id: 'e1', status: 'APPLYING' })]));
      fixture.componentInstance.confirm();
      expect(stateOf(fixture, 'EMPLOYEES')).toBe('IN_PROGRESS');
      // the poll sees it applied; the people are now placed
      api.importBatch.mockImplementation(() => of(batch({ id: 'e1', status: 'APPLIED' })));
      api.recentImports.mockImplementation(() => of([batch({ id: 'e1', status: 'APPLIED' })]));
      api.children.mockImplementation(() => of([unit('HQ', 1000)]));
      vi.advanceTimersByTime(IMPORT_POLL_INTERVAL_MS);
      expect(stateOf(fixture, 'EMPLOYEES')).toBe('DONE');
      expect(fixture.componentInstance.steps[2].facts).toEqual([{ key: 'employees', count: 1000 }]);
      vi.useRealTimers();
    });

    it('leaves a preview when it is cancelled', () => {
      routeStep = 'employees';
      const preview = batch({ id: 'e1', status: 'VALIDATED', errorRows: 0 });
      api.recentImports.mockImplementation(() => of([preview]));
      const fixture = create();
      fixture.componentInstance.open(preview);
      api.recentImports.mockImplementation(() => of([batch({ id: 'e1', status: 'CANCELLED' })]));
      api.importBatch.mockImplementation(() => of(batch({ id: 'e1', status: 'CANCELLED' })));
      fixture.componentInstance.cancel();
      expect(stateOf(fixture, 'EMPLOYEES')).toBe('NOT_STARTED');
    });

    it('re-reads the list and the facts when another step is opened', () => {
      routeStep = 'ladders';
      const fixture = create();
      expect(states(fixture)).toEqual(['NOT_STARTED', 'NOT_STARTED', 'NOT_STARTED']);
      api.ladders.mockImplementation(() => of([ladder('a')]));
      api.recentDepartmentImports.mockImplementation(() => of([batch({ status: 'APPLIED' })]));
      fixture.componentInstance.setMode('DEPARTMENTS');
      expect(states(fixture)).toEqual(['DONE', 'DONE', 'NOT_STARTED']);
    });

    it('asks only for what this person may read when it refreshes', () => {
      granted = new Set([ORG_IMPORT_PRIVILEGES.create, ORG_DEPARTMENT_IMPORT_PRIVILEGES.create]);
      routeStep = 'employees';
      const fixture = create();
      vi.clearAllMocks();
      fixture.componentInstance.setMode('DEPARTMENTS');
      expect(api.recentLadderImports).not.toHaveBeenCalled();
      expect(api.ladders).not.toHaveBeenCalled();
      expect(api.children).not.toHaveBeenCalled();
      expect(api.recentDepartmentImports).not.toHaveBeenCalled();
    });
  });

  describe('the stepper on the page', () => {
    it('is a labelled navigation with an ordered list, the open step marked as the current one', () => {
      const fixture = create();
      refresh(fixture);
      const nav = el(fixture).querySelector('nav.import-stepper');
      expect(nav?.getAttribute('aria-label')).toBe('orgStructure.import.steps.label');
      expect(nav?.querySelector('ol')).not.toBeNull();
      expect(Array.from(nav?.querySelectorAll('ol > li') ?? [])).toHaveLength(3);
      const buttons = Array.from(nav?.querySelectorAll('button') ?? []);
      expect(buttons.map((b) => b.getAttribute('aria-current'))).toEqual(['step', null, null]);
      expect(nav?.querySelector('[role="tablist"], [role="tab"]')).toBeNull();
      expect(el(fixture).querySelector('[role="region"]')?.getAttribute('aria-labelledby')).toBe('org-import-step-LADDERS');
    });

    it('says the state of each step in words and the step number, not only by colour', () => {
      api.ladders.mockImplementation(() => of([ladder('a')]));
      api.recentDepartmentImports.mockImplementation(() => of([batch({ status: 'VALIDATED' })]));
      api.recentImports.mockImplementation(() => of([batch({ status: 'APPLYING' })]));
      const fixture = create();
      refresh(fixture);
      expect(testId(fixture, 'import-step-state-ladders')?.textContent).toContain('orgStructure.import.steps.state.DONE');
      expect(testId(fixture, 'import-step-state-departments')?.textContent).toContain('orgStructure.import.steps.state.PREVIEW');
      expect(testId(fixture, 'import-step-hint-departments')?.textContent).toContain('orgStructure.import.steps.previewHint');
      expect(testId(fixture, 'import-step-hint-ladders')).toBeNull();
      expect(testId(fixture, 'import-step-state-employees')?.textContent).toContain('orgStructure.import.steps.state.IN_PROGRESS');
      expect(testId(fixture, 'import-step-employees')?.textContent).toContain('orgStructure.import.steps.stepOf');
      expect(el(fixture).querySelectorAll('li.import-step').length).toBe(3);
      expect(Array.from(el(fixture).querySelectorAll('li.import-step')).map((li) => li.getAttribute('data-state'))).toEqual(['DONE', 'PREVIEW', 'IN_PROGRESS']);
      expect(el(fixture).querySelector('.import-step__state i')?.getAttribute('aria-hidden')).toBe('true');
    });

    it('opens a step on a click and the keys move between the steps', () => {
      const fixture = create();
      testId(fixture, 'import-step-departments')?.dispatchEvent(new Event('click'));
      refresh(fixture);
      expect(fixture.componentInstance.mode).toBe('DEPARTMENTS');
      expect(testId(fixture, 'import-step-departments')?.getAttribute('aria-current')).toBe('step');
      const key = new KeyboardEvent('keydown', { key: 'ArrowRight', cancelable: true });
      fixture.componentInstance.onTabKeydown(key);
      expect(fixture.componentInstance.mode).toBe('EMPLOYEES');
      expect(key.defaultPrevented).toBe(true);
    });

    it('shows only the steps this person may open, numbered as in the full setup', () => {
      granted = new Set([ORG_DEPARTMENT_IMPORT_PRIVILEGES.create, ORG_IMPORT_PRIVILEGES.create]);
      const fixture = create();
      refresh(fixture);
      expect(fixture.componentInstance.visibleSteps.map((s) => s.number)).toEqual([2, 3]);
      expect(testId(fixture, 'import-step-ladders')).toBeNull();
    });

    it('shows no stepper, no purpose line and no notes to someone who may import only employees', () => {
      granted = new Set([ORG_IMPORT_PRIVILEGES.create]);
      const fixture = create();
      refresh(fixture);
      expect(el(fixture).querySelector('nav.import-stepper')).toBeNull();
      expect(testId(fixture, 'step-purpose')).toBeNull();
      expect(fixture.componentInstance.mode).toBe('EMPLOYEES');
    });
  });

  describe('the start of each step', () => {
    it('opens with its purpose line, and a note about the step it builds on from step 2 on', () => {
      routeStep = 'ladders';
      const first = create();
      refresh(first);
      expect(testId(first, 'step-purpose')?.textContent).toContain('orgStructure.import.steps.purpose.ladders');
      expect(testId(first, 'step-depends')).toBeNull();
      expect(testId(first, 'download-sample')).not.toBeNull();
      first.componentInstance.setMode('DEPARTMENTS');
      refresh(first);
      expect(testId(first, 'step-purpose')?.textContent).toContain('orgStructure.import.steps.purpose.departments');
      expect(testId(first, 'step-depends')?.textContent).toContain('orgStructure.import.steps.depends.departments');
      expect(testId(first, 'download-template')).not.toBeNull();
      first.componentInstance.setMode('EMPLOYEES');
      refresh(first);
      expect(testId(first, 'step-depends')?.textContent).toContain('orgStructure.import.steps.depends.employees');
    });

    it('warns gently, as a status, when the step before has not been started, without locking anything', () => {
      routeStep = 'departments';
      const fixture = create();
      refresh(fixture);
      const warning = testId(fixture, 'step-warning');
      expect(warning?.getAttribute('role')).toBe('status');
      expect(warning?.textContent).toContain('orgStructure.import.steps.previousNotStarted');
      expect(fixture.componentInstance.previousNotStarted).toBe(true);
      expect(fixture.componentInstance.visibleSteps.length).toBe(3);
      expect(el(fixture).querySelector('hq-doc-upload')).not.toBeNull();
    });

    it('does not warn on the first step, when the step before is done or in progress, or when nothing is known about it', () => {
      routeStep = 'ladders';
      expect(create().componentInstance.previousNotStarted).toBe(false);
      routeStep = 'departments';
      api.ladders.mockImplementation(() => of([ladder('a')]));
      expect(create().componentInstance.previousNotStarted).toBe(false);
      api.ladders.mockImplementation(() => of([]));
      api.recentLadderImports.mockImplementation(() => of([batch({ status: 'VALIDATED' })]));
      expect(create().componentInstance.previousNotStarted).toBe(false);
      api.recentLadderImports.mockImplementation(() => of([]));
      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create, ORG_DEPARTMENT_IMPORT_PRIVILEGES.create, ORG_IMPORT_PRIVILEGES.create]);
      const unknown = create();
      expect(unknown.componentInstance.steps[0].known).toBe(false);
      expect(unknown.componentInstance.previousNotStarted).toBe(false);
    });
  });

  describe('the next step', () => {
    it('is offered once a file is confirmed and takes the person to the following step', () => {
      routeStep = 'ladders';
      const fixture = create();
      fixture.componentInstance.open(batch({ status: 'VALIDATED', errorRows: 0 }));
      refresh(fixture);
      expect(testId(fixture, 'next-step')).toBeNull();
      fixture.componentInstance.confirm();
      refresh(fixture);
      expect(fixture.componentInstance.nextStep).toBe('DEPARTMENTS');
      testId(fixture, 'next-step')?.dispatchEvent(new Event('click'));
      refresh(fixture);
      expect(fixture.componentInstance.mode).toBe('DEPARTMENTS');
      expect(testId(fixture, 'next-step')).toBeNull();
    });

    it('is offered for a file that is already applied, skips a step the person may not open, and is absent on the last step', () => {
      granted = new Set([ORG_LADDER_IMPORT_PRIVILEGES.create, ORG_IMPORT_PRIVILEGES.create]);
      routeStep = 'ladders';
      const fixture = create();
      fixture.componentInstance.selected = batch({ status: 'APPLIED' });
      expect(fixture.componentInstance.nextStep).toBe('EMPLOYEES');
      fixture.componentInstance.setMode('EMPLOYEES');
      fixture.componentInstance.selected = batch({ status: 'APPLIED' });
      expect(fixture.componentInstance.nextStep).toBeNull();
    });

    it('is not offered for a file that failed, was cancelled or waits to be confirmed', () => {
      routeStep = 'ladders';
      const fixture = create();
      (['VALIDATED', 'FAILED', 'CANCELLED'] as const).forEach((status) => {
        fixture.componentInstance.selected = batch({ status });
        expect(fixture.componentInstance.nextStep).toBeNull();
      });
      fixture.componentInstance.selected = null;
      expect(fixture.componentInstance.nextStep).toBeNull();
    });
  });
});
