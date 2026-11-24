import { ChangeDetectorRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OrgStructureService } from '../../../organization-structure/org-structure.service';
import { WORKFLOW_TEMPLATES } from '../workflow-templates';
import { readSteps } from './bpmn-sla';
import { WorkflowDefinition } from './workflow-definition';

const XML = `<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn">
  <process id="p"><startEvent id="s"/>
    <userTask id="t1" name="One" flowable:dueDate="PT8H" flowable:assignee="u1"/>
    <userTask id="t2" name="Two" flowable:candidateUsers="a,b" flowable:candidateGroups="R1,dept:ORG:SALES"/>
    <sequenceFlow id="f1" sourceRef="s" targetRef="t1"/><sequenceFlow id="f2" sourceRef="t1" targetRef="t2"/>
  </process></definitions>`;

const detail = (over: Record<string, unknown> = {}) => ({
  definition: { key: 'p', name: 'P', version: 2, deploymentId: 'd', suspended: false },
  xml: XML,
  versions: [{ key: 'p', version: 2, deployedBy: 'u1' }, { key: 'p', version: 1, deployedBy: null }],
  ...over,
});

interface Setup {
  c: WorkflowDefinition;
  workflow: Record<string, ReturnType<typeof vi.fn>>;
  notify: Record<string, ReturnType<typeof vi.fn>>;
  appDialog: { confirm: ReturnType<typeof vi.fn>; prompt: ReturnType<typeof vi.fn> };
  router: { navigate: ReturnType<typeof vi.fn> };
  auth: { currentUser: { id?: string; role?: string } | null };
  uam: Record<string, ReturnType<typeof vi.fn>>;
  org: { searchUnits: ReturnType<typeof vi.fn> };
  params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  query: Record<string, string>;
}

function setup(opts: { role?: string; key?: string; query?: Record<string, string> } = {}): Setup {
  const workflow = {
    definitionSettings: vi.fn().mockReturnValue(of({ requireApproval: false })),
    definition: vi.fn().mockReturnValue(of(detail())),
    definitionGraph: vi.fn().mockReturnValue(of({ nodes: [], edges: [] })),
    previewGraph: vi.fn().mockReturnValue(of({ nodes: [{ id: 'n', name: 'n', type: 'userTask' }], edges: [] })),
    validateDefinition: vi.fn().mockReturnValue(of({ valid: true, errors: [] })),
    deployDefinition: vi.fn().mockReturnValue(of({ key: 'p', version: 3 })),
    propose: vi.fn().mockReturnValue(of({ id: 'pr1' })),
    proposals: vi.fn().mockReturnValue(of([])),
    proposal: vi.fn().mockReturnValue(of({ id: 'pr1', xml: '<x/>' })),
    approveProposal: vi.fn().mockReturnValue(of({ key: 'p', version: 3 })),
    rejectProposal: vi.fn().mockReturnValue(of(undefined)),
    withdrawProposal: vi.fn().mockReturnValue(of(undefined)),
    migrateRunning: vi.fn(),
  };
  const notify = { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() };
  const appDialog = { confirm: vi.fn().mockReturnValue(of(true)), prompt: vi.fn().mockReturnValue(of('my note')) };
  const router = { navigate: vi.fn().mockResolvedValue(true) };
  const auth = { currentUser: { id: 'me', role: opts.role ?? 'SUPER_ADMIN' } };
  const uam = {
    getUser: vi.fn().mockReturnValue(of({ displayName: 'Asha' })),
    list: vi.fn().mockReturnValue(of({ content: [{ code: 'R1', name: 'Role One' }, { code: 'R2' }] })),
    listUsers: vi.fn().mockReturnValue(of({ content: [{ id: 'u1', displayName: 'Asha' }, { id: 'u2', userName: 'bob', email: 'b@x' }, { id: 'u3', userName: 'cy' }] })),
  };
  const org = { searchUnits: vi.fn().mockReturnValue(of([{ code: 'SALES', name: 'Sales' }])) };
  const params = new BehaviorSubject(convertToParamMap({ key: opts.key ?? 'p' }));
  const query = opts.query ?? {};
  const route = { paramMap: params.asObservable(), snapshot: { queryParamMap: convertToParamMap(query) } };
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [{ provide: OrgStructureService, useValue: org }] });
  const cdr = { detectChanges: vi.fn() } as unknown as ChangeDetectorRef;
  const c = TestBed.runInInjectionContext(() => new WorkflowDefinition(
    route as never, router as never, workflow as never, auth as never, notify as never, appDialog as never, uam as never,
    { instant: (k: string, p?: object) => (p ? `${k}|${JSON.stringify(p)}` : k) } as never, cdr));
  return { c, workflow, notify, appDialog, router, auth, uam, org, params, query };
}

describe('WorkflowDefinition loading', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('loads settings, people and the definition on init', () => {
    const s = setup();
    s.c.ngOnInit();
    expect(s.workflow['definition']).toHaveBeenCalledWith('p', undefined);
    expect(s.c.key).toBe('p');
    expect(s.c.version).toBe(2);
    expect(s.c.xml).toBe(XML);
    expect(s.c.isNew).toBe(false);
    expect(s.c.roleOptions).toEqual([{ value: 'R1', label: 'Role One (R1)' }, { value: 'R2', label: 'R2' }]);
    expect(s.c.departmentOptions).toEqual([{ value: 'dept:SALES', label: 'Sales (SALES)' }]);
    expect(s.c.userOptions.map((o) => o.label)).toEqual(['Asha', 'bob (b@x)', 'cy']);
    expect(s.c.deployerNames['u1']).toBe('Asha');
    expect(s.workflow['definitionGraph']).toHaveBeenCalled();
    expect(s.c.graph).toEqual({ nodes: [], edges: [] });
  });

  it('keeps empty option lists when people cannot be loaded and shows an error when the definition cannot', () => {
    const s = setup();
    s.uam['list'].mockReturnValue(throwError(() => new Error('x')));
    s.uam['listUsers'].mockReturnValue(throwError(() => new Error('x')));
    s.org.searchUnits.mockReturnValue(throwError(() => new Error('x')));
    s.workflow['definitionSettings'].mockReturnValue(throwError(() => new Error('x')));
    s.workflow['definition'].mockReturnValue(throwError(() => new Error('x')));
    s.c.ngOnInit();
    expect(s.c.roleOptions).toEqual([]);
    expect(s.c.userOptions).toEqual([]);
    expect(s.c.departmentOptions).toEqual([]);
    expect(s.c.requireApproval).toBe(false);
    expect(s.c.loadError).toBe(true);
    expect(s.c.detail).toBeNull();
  });

  it('drops the graph when it cannot be loaded', () => {
    const s = setup();
    s.workflow['definitionGraph'].mockReturnValue(throwError(() => new Error('x')));
    s.c.ngOnInit();
    expect(s.c.graph).toBeNull();
  });

  it('reads the approval requirement from the service settings', () => {
    const s = setup();
    s.workflow['definitionSettings'].mockReturnValue(of({ requireApproval: true }));
    s.c.ngOnInit();
    expect(s.c.requireApproval).toBe(true);
  });

  it('selecting a version loads that version', () => {
    const s = setup();
    s.c.ngOnInit();
    s.c.selectVersion(1);
    expect(s.workflow['definition']).toHaveBeenLastCalledWith('p', 1);
  });

  it('reloads when the route key changes', () => {
    const s = setup();
    s.c.ngOnInit();
    s.params.next(convertToParamMap({ key: 'q' }));
    expect(s.workflow['definition']).toHaveBeenLastCalledWith('q', undefined);
  });

  it('is latest only at the newest version, and has changes only when the XML differs', () => {
    const s = setup();
    s.c.ngOnInit();
    expect(s.c.isLatest).toBe(true);
    expect(s.c.hasChanges).toBe(false);
    s.c.xml = XML + ' ';
    expect(s.c.hasChanges).toBe(true);
    s.c.version = 1;
    expect(s.c.isLatest).toBe(false);
  });

  it('knows the signed-in user id and who may edit (by role, as the code stands)', () => {
    const s = setup();
    expect(s.c.myId).toBe('me');
    expect(s.c.canEdit).toBe(true);
    s.auth.currentUser = { id: 'x', role: 'STAFF' };
    expect(s.c.canEdit).toBe(false);
    s.auth.currentUser = null;
    expect(s.c.myId).toBeNull();
    expect(s.c.canEdit).toBe(false);
  });

  it('shows deployers by name, or the bundled label for the shipped version', () => {
    const s = setup();
    s.c.ngOnInit();
    expect(s.c.deployer({ deployedBy: 'u1' } as never)).toBe('Asha');
    expect(s.c.deployer({ deployedBy: 'zz' } as never)).toBe('zz');
    expect(s.c.deployer({ deployedBy: null } as never)).toBe('workflow.definitions.changeLog.bundled');
    expect(s.c.personName(null)).toBe('');
    expect(s.c.personName('u1')).toBe('Asha');
  });

  it('falls back to the id when a deployer name cannot be loaded', () => {
    const s = setup();
    s.uam['getUser'].mockReturnValue(throwError(() => new Error('x')));
    s.c.ngOnInit();
    expect(s.c.deployerNames['u1']).toBe('u1');
  });
});

describe('WorkflowDefinition new from template', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('opens the editor with the template filled in and nothing deployed', () => {
    const t = WORKFLOW_TEMPLATES[0];
    const s = setup({ key: 'new', query: { template: t.id, key: 'k1', name: 'Name 1' } });
    s.c.ngOnInit();
    expect(s.c.isNew).toBe(true);
    expect(s.c.xml).toBe(t.build('k1', 'Name 1'));
    expect(s.c.detail?.definition).toMatchObject({ key: 'k1', name: 'Name 1', version: 0 });
    expect(s.workflow['previewGraph']).toHaveBeenCalledWith(s.c.xml);
    expect(s.c.previewing).toBe(true);
    expect(s.c.isLatest).toBe(true);
    expect(s.c.hasChanges).toBe(true);
    expect(s.workflow['definition']).not.toHaveBeenCalled();
    s.c.loadProposals();
    expect(s.workflow['proposals']).not.toHaveBeenCalled();
  });

  it('uses the key as the name when none is given', () => {
    const s = setup({ key: 'new', query: { template: WORKFLOW_TEMPLATES[0].id, key: 'k1' } });
    s.c.ngOnInit();
    expect(s.c.detail?.definition.name).toBe('k1');
  });

  it('goes back to the list when the template or key is missing', () => {
    const s = setup({ key: 'new', query: { template: 'nope', key: 'k' } });
    s.c.ngOnInit();
    expect(s.router.navigate).toHaveBeenCalledWith(['/workflow', 'definitions']);
    const s2 = setup({ key: 'new', query: { template: WORKFLOW_TEMPLATES[0].id } });
    s2.c.ngOnInit();
    expect(s2.router.navigate).toHaveBeenCalledWith(['/workflow', 'definitions']);
  });
});

describe('WorkflowDefinition SLA editing', () => {
  beforeEach(() => TestBed.resetTestingModule());
  const ready = () => { const s = setup(); s.c.ngOnInit(); return s; };
  const step = (s: Setup, id: string) => s.c.steps.find((x) => x.id === id)!;

  it('lists the steps with the SLA read from the XML', () => {
    const s = ready();
    expect(s.c.steps.map((x) => x.id)).toEqual(['t1', 't2']);
    expect(step(s, 't1')).toMatchObject({ amount: 8, unit: 'HOURS' });
    expect(s.c.limit('DAYS')).toEqual({ min: 1, max: 365 });
  });

  it('writes a valid SLA to the XML and shows it in the unit the user picked', () => {
    const s = ready();
    s.c.setSla(step(s, 't1'), 24, 'HOURS');
    expect(readSteps(s.c.xml)[0]).toMatchObject({ amount: 1, unit: 'DAYS' });
    expect(step(s, 't1')).toMatchObject({ amount: 24, unit: 'HOURS' });
    expect(s.c.hasSlaErrors).toBe(false);
  });

  it('keeps an out-of-range entry as a draft without touching the XML', () => {
    const s = ready();
    const before = s.c.xml;
    s.c.setSla(step(s, 't1'), 9999, 'HOURS');
    expect(s.c.xml).toBe(before);
    expect(s.c.hasSlaErrors).toBe(true);
    expect(s.c.displayAmount(step(s, 't1'))).toBe('9999');
    expect(s.c.displayUnit(step(s, 't1'))).toBe('HOURS');
    s.c.setSla(step(s, 't1'), 1.5, 'HOURS');
    expect(s.c.hasSlaErrors).toBe(true);
    s.c.setSla(step(s, 't1'), 5, 'HOURS');
    expect(s.c.hasSlaErrors).toBe(false);
    expect(s.c.displayAmount(step(s, 't1'))).toBe(5);
  });

  it('removes the SLA when the field is cleared', () => {
    const s = ready();
    s.c.setSla(step(s, 't1'), 9999, 'HOURS');
    s.c.setSla(step(s, 't1'), '', 'HOURS');
    expect(s.c.hasSlaErrors).toBe(false);
    expect(readSteps(s.c.xml)[0].amount).toBeNull();
    s.c.setSla(step(s, 't2'), null, 'DAYS');
    expect(step(s, 't2').unit).toBe('DAYS');
  });

  it('blocks Check and Deploy while a draft is invalid', () => {
    const s = ready();
    s.c.setSla(step(s, 't1'), 0, 'HOURS');
    s.c.check();
    s.c.deploy();
    expect(s.workflow['validateDefinition']).not.toHaveBeenCalled();
    expect(s.appDialog.prompt).not.toHaveBeenCalled();
  });

  it('applies reminders with a 15 minute floor on repeats', () => {
    const s = ready();
    expect(s.c.reminderLimit('repeatEvery', 'MINUTES')).toEqual({ min: 15, max: 1440 });
    expect(s.c.reminderLimit('remindBefore', 'MINUTES')).toEqual({ min: 1, max: 1440 });
    s.c.setReminder(step(s, 't1'), 'repeatEvery', 5, 'MINUTES');
    expect(s.c.reminderInvalid(step(s, 't1'), 'repeatEvery')).toBe(true);
    expect(s.c.reminderAmount(step(s, 't1'), 'repeatEvery')).toBe('5');
    expect(readSteps(s.c.xml)[0].repeatEvery).toBeNull();
    s.c.setReminder(step(s, 't1'), 'repeatEvery', 30, 'MINUTES');
    expect(s.c.reminderInvalid(step(s, 't1'), 'repeatEvery')).toBe(false);
    expect(readSteps(s.c.xml)[0].repeatEvery).toEqual({ amount: 30, unit: 'MINUTES' });
    expect(s.c.reminderUnit(step(s, 't1'), 'repeatEvery')).toBe('MINUTES');
    s.c.setReminder(step(s, 't1'), 'remindBefore', 2, 'HOURS');
    expect(s.c.reminderAmount(step(s, 't1'), 'remindBefore')).toBe(2);
    s.c.setReminder(step(s, 't1'), 'remindBefore', 120, 'MINUTES');
    expect(s.c.reminderAmount(step(s, 't1'), 'remindBefore')).toBe(120);
    s.c.setReminder(step(s, 't1'), 'remindBefore', null, 'HOURS');
    expect(readSteps(s.c.xml)[0].remindBefore).toBeNull();
    expect(s.c.reminderAmount(step(s, 't1'), 'remindBefore')).toBeNull();
    expect(s.c.reminderUnit(step(s, 't2'), 'remindBefore')).toBe('HOURS');
  });

  it('sets and clears the overdue hand-over person', () => {
    const s = ready();
    s.c.setReassign(step(s, 't1'), 'u2');
    expect(readSteps(s.c.xml)[0].reassignOnOverdue).toBe('u2');
    s.c.setReassign(step(s, 't1'), '');
    expect(readSteps(s.c.xml)[0].reassignOnOverdue).toBeNull();
  });
});

describe('WorkflowDefinition who and notifications', () => {
  beforeEach(() => TestBed.resetTestingModule());
  const ready = () => { const s = setup(); s.c.ngOnInit(); return s; };
  const step = (s: Setup, id: string) => s.c.steps.find((x) => x.id === id)!;

  it('works out the assignment mode from what the XML holds, or what was just picked', () => {
    const s = ready();
    expect(s.c.mode(step(s, 't1'))).toBe('PERSON');
    expect(s.c.mode(step(s, 't2'))).toBe('USERS');
    expect(s.c.rolesOf(step(s, 't2'))).toEqual(['R1']);
    expect(s.c.departmentsOf(step(s, 't2'))).toEqual(['dept:ORG:SALES']);
  });

  it('switching mode clears the other kinds so they never mix', () => {
    const s = ready();
    s.c.setMode(step(s, 't2'), 'USERS');
    expect(readSteps(s.c.xml)[1].candidateGroups).toEqual(['R1', 'dept:ORG:SALES']);
    s.c.setMode(step(s, 't2'), 'ROLES');
    expect(readSteps(s.c.xml)[1]).toMatchObject({ candidateUsers: [], candidateGroups: [], assignee: null });
    expect(s.c.mode(step(s, 't2'))).toBe('ROLES');
    expect(s.c.validation).toBeNull();
  });

  it('groups: departments win over roles, then roles, else none', () => {
    const s = ready();
    const base = step(s, 't2');
    expect(s.c.mode({ ...base, candidateUsers: [] })).toBe('DEPARTMENTS');
    expect(s.c.mode({ ...base, candidateUsers: [], candidateGroups: ['R1'] })).toBe('ROLES');
    expect(s.c.mode({ ...base, candidateUsers: [], candidateGroups: [] })).toBe('NONE');
  });

  it('adds, removes and assigns people and roles', () => {
    const s = ready();
    s.c.setAssignee(step(s, 't1'), 'u9');
    expect(readSteps(s.c.xml)[0].assignee).toBe('u9');
    s.c.setAssignee(step(s, 't1'), '');
    expect(readSteps(s.c.xml)[0].assignee).toBeNull();
    s.c.addWho(step(s, 't2'), 'candidateUsers', 'c');
    expect(readSteps(s.c.xml)[1].candidateUsers).toEqual(['a', 'b', 'c']);
    s.c.addWho(step(s, 't2'), 'candidateUsers', '');
    expect(readSteps(s.c.xml)[1].candidateUsers).toEqual(['a', 'b', 'c']);
    s.c.removeWho(step(s, 't2'), 'candidateUsers', 'a');
    expect(readSteps(s.c.xml)[1].candidateUsers).toEqual(['b', 'c']);
  });

  it('labels stored values by their option, stripping the department prefix as a fallback', () => {
    const s = ready();
    const options = [{ value: 'u1', label: 'Asha' }];
    expect(s.c.label(options, 'u1')).toBe('Asha');
    expect(s.c.label(options, 'zz')).toBe('zz');
    expect(s.c.label(options, 'dept:HR')).toBe('HR');
    expect(s.c.remaining([...options, { value: 'u2', label: 'B' }], ['u1'])).toEqual([{ value: 'u2', label: 'B' }]);
  });

  it('toggles notification channels from the default set, switching off when none remain', () => {
    const s = ready();
    const t1 = () => step(s, 't1');
    expect(s.c.effectiveChannels(t1())).toEqual(['IN_APP', 'FCM_PUSH']);
    expect(s.c.channelOn(t1(), 'IN_APP')).toBe(true);
    expect(s.c.channelOn(t1(), 'SMS')).toBe(false);
    s.c.toggleChannel(t1(), 'SMS');
    expect(readSteps(s.c.xml)[0].notifyChannels).toEqual(['IN_APP', 'FCM_PUSH', 'SMS']);
    s.c.toggleChannel(t1(), 'IN_APP');
    s.c.toggleChannel(t1(), 'FCM_PUSH');
    s.c.toggleChannel(t1(), 'SMS');
    expect(readSteps(s.c.xml)[0].notifyChannels).toEqual([]);
    s.c.useDefaultChannels(t1());
    expect(readSteps(s.c.xml)[0].notifyChannels).toBeNull();
  });

  it('tracks steps by id', () => {
    const s = ready();
    expect(s.c.trackByStep(0, step(s, 't2'))).toBe('t2');
  });
});

describe('WorkflowDefinition check, preview and discard', () => {
  beforeEach(() => TestBed.resetTestingModule());
  const ready = () => { const s = setup(); s.c.ngOnInit(); return s; };

  it('checks the XML and reports success', () => {
    const s = ready();
    s.c.check();
    expect(s.workflow['validateDefinition']).toHaveBeenCalledWith(XML);
    expect(s.c.validation).toEqual({ valid: true, errors: [] });
    expect(s.notify['success']).toHaveBeenCalledWith('workflow.definitions.valid');
    expect(s.c.isBusy).toBe(false);
  });

  it('keeps invalid results on screen without a success toast', () => {
    const s = ready();
    s.workflow['validateDefinition'].mockReturnValue(of({ valid: false, errors: ['bad'] }));
    s.c.check();
    expect(s.c.validation?.valid).toBe(false);
    expect(s.notify['success']).not.toHaveBeenCalled();
  });

  it('shows the service message, or a generic one, when an action fails', () => {
    const s = ready();
    s.workflow['validateDefinition'].mockReturnValue(throwError(() => ({ error: { errorMsg: 'boom' } })));
    s.c.check();
    expect(s.notify['error']).toHaveBeenCalledWith('boom');
    s.workflow['validateDefinition'].mockReturnValue(throwError(() => ({})));
    s.c.check();
    expect(s.notify['error']).toHaveBeenLastCalledWith('workflow.definitions.actionFailed');
    expect(s.c.isBusy).toBe(false);
  });

  it('previews the edited XML, and discard restores the deployed XML and diagram', () => {
    const s = ready();
    s.c.xml = '<edited/>';
    s.c.preview();
    expect(s.c.previewing).toBe(true);
    expect(s.c.graph?.nodes).toHaveLength(1);
    s.workflow['definitionGraph'].mockClear();
    s.c.discard();
    expect(s.c.xml).toBe(XML);
    expect(s.c.previewing).toBe(false);
    expect(s.workflow['definitionGraph']).toHaveBeenCalled();
  });

  it('discard does nothing before anything is loaded', () => {
    const s = setup();
    s.c.xml = 'x';
    s.c.discard();
    expect(s.c.xml).toBe('x');
  });
});

describe('WorkflowDefinition deploy', () => {
  beforeEach(() => TestBed.resetTestingModule());
  const ready = () => { const s = setup(); s.c.ngOnInit(); return s; };

  it('warns instead of deploying when nothing changed', () => {
    const s = ready();
    s.c.deploy();
    expect(s.notify['warning']).toHaveBeenCalledWith('workflow.definitions.unchanged');
    expect(s.appDialog.prompt).not.toHaveBeenCalled();
  });

  it('asks for a change note, deploys, and reloads the same key', () => {
    const s = ready();
    s.c.xml = XML + '<!-- edit -->';
    s.c.deploy();
    expect(s.appDialog.prompt).toHaveBeenCalled();
    expect(s.workflow['deployDefinition']).toHaveBeenCalledWith(XML + '<!-- edit -->', 'my note');
    expect(s.notify['success']).toHaveBeenCalledWith('workflow.definitions.deployed|{"key":"p","version":3}');
    expect(s.workflow['definition']).toHaveBeenCalledTimes(2);
  });

  it('follows the new key when the process id was edited', () => {
    const s = ready();
    s.c.xml += ' ';
    s.workflow['deployDefinition'].mockReturnValue(of({ key: 'renamed', version: 1 }));
    s.c.deploy();
    expect(s.router.navigate).toHaveBeenCalledWith(['/workflow', 'definitions', 'renamed']);
  });

  it('does nothing when the note prompt is cancelled or blank', () => {
    const s = ready();
    s.c.xml += ' ';
    s.appDialog.prompt.mockReturnValue(of(null));
    s.c.deploy();
    s.appDialog.prompt.mockReturnValue(of('   '));
    s.c.deploy();
    expect(s.workflow['deployDefinition']).not.toHaveBeenCalled();
    expect(s.workflow['propose']).not.toHaveBeenCalled();
  });

  it('submits for approval instead of deploying when approval is required', () => {
    const s = setup();
    s.workflow['definitionSettings'].mockReturnValue(of({ requireApproval: true }));
    s.c.ngOnInit();
    s.c.xml += ' ';
    s.c.deploy();
    expect(s.workflow['propose']).toHaveBeenCalledWith(s.c.xml, 'my note');
    expect(s.workflow['deployDefinition']).not.toHaveBeenCalled();
    expect(s.notify['success']).toHaveBeenCalledWith('workflow.definitions.proposals.submitted');
  });
});

describe('WorkflowDefinition proposals', () => {
  beforeEach(() => TestBed.resetTestingModule());

  const proposal = { id: 'pr1', processKey: 'p', proposedBy: 'u1', decidedBy: 'u2' };

  it('loads at most ten proposals for an editor and the names of those involved', () => {
    const s = setup();
    s.workflow['proposals'].mockReturnValue(of(Array.from({ length: 12 }, (_, i) => ({ ...proposal, id: `p${i}` }))));
    s.c.ngOnInit();
    expect(s.c.proposals).toHaveLength(10);
    expect(s.uam['getUser']).toHaveBeenCalledWith('u1');
    expect(s.uam['getUser']).toHaveBeenCalledWith('u2');
  });

  it('does not load proposals for a user who cannot edit', () => {
    const s = setup({ role: 'STAFF' });
    s.c.ngOnInit();
    expect(s.workflow['proposals']).not.toHaveBeenCalled();
  });

  it('clears the list when proposals cannot be loaded', () => {
    const s = setup();
    s.workflow['proposals'].mockReturnValue(throwError(() => new Error('x')));
    s.c.ngOnInit();
    expect(s.c.proposals).toEqual([]);
  });

  it('views a proposal in the editor and previews its diagram', () => {
    const s = setup();
    s.c.ngOnInit();
    s.c.viewProposal(proposal as never);
    expect(s.c.xml).toBe('<x/>');
    expect(s.workflow['previewGraph']).toHaveBeenCalledWith('<x/>');
    expect(s.notify['info']).toHaveBeenCalledWith('workflow.definitions.proposals.loaded');
    s.workflow['proposal'].mockReturnValue(throwError(() => ({ error: { message: 'gone' } })));
    s.c.viewProposal(proposal as never);
    expect(s.notify['error']).toHaveBeenCalledWith('gone');
    s.workflow['proposal'].mockReturnValue(of({ id: 'pr1' }));
    s.c.viewProposal(proposal as never);
    expect(s.c.xml).toBe('');
  });

  it('approves after confirmation and then reloads', () => {
    const s = setup();
    s.c.ngOnInit();
    s.c.approveProposal(proposal as never);
    expect(s.workflow['approveProposal']).toHaveBeenCalledWith('pr1');
    expect(s.notify['success']).toHaveBeenCalledWith('workflow.definitions.deployed|{"key":"p","version":3}');
    s.workflow['approveProposal'].mockClear();
    s.appDialog.confirm.mockReturnValue(of(false));
    s.c.approveProposal(proposal as never);
    expect(s.workflow['approveProposal']).not.toHaveBeenCalled();
  });

  it('rejects only with a reason, trimmed', () => {
    const s = setup();
    s.c.ngOnInit();
    s.appDialog.prompt.mockReturnValue(of('  too risky '));
    s.c.rejectProposal(proposal as never);
    expect(s.workflow['rejectProposal']).toHaveBeenCalledWith('pr1', 'too risky');
    s.workflow['rejectProposal'].mockClear();
    s.appDialog.prompt.mockReturnValue(of('  '));
    s.c.rejectProposal(proposal as never);
    s.appDialog.prompt.mockReturnValue(of(null));
    s.c.rejectProposal(proposal as never);
    expect(s.workflow['rejectProposal']).not.toHaveBeenCalled();
  });

  it('withdraws a proposal', () => {
    const s = setup();
    s.c.ngOnInit();
    s.c.withdrawProposal(proposal as never);
    expect(s.workflow['withdrawProposal']).toHaveBeenCalledWith('pr1');
  });
});

describe('WorkflowDefinition migrate running requests', () => {
  beforeEach(() => TestBed.resetTestingModule());
  const result = (over: Record<string, unknown>) => ({ candidates: 5, movable: 4, latestVersion: 3, ...over });

  it('says so when there is nothing to move', () => {
    const s = setup();
    s.c.ngOnInit();
    s.workflow['migrateRunning'].mockReturnValue(of(result({ candidates: 0, movable: 0 })));
    s.c.migrateRunning();
    expect(s.workflow['migrateRunning']).toHaveBeenCalledWith('p', true);
    expect(s.notify['info']).toHaveBeenCalledWith('workflow.definitions.migrate.nothing');
    expect(s.appDialog.confirm).not.toHaveBeenCalled();
  });

  it('warns when requests exist but none can move', () => {
    const s = setup();
    s.c.ngOnInit();
    s.workflow['migrateRunning'].mockReturnValue(of(result({ movable: 0 })));
    s.c.migrateRunning();
    expect(s.notify['warning']).toHaveBeenCalledWith('workflow.definitions.migrate.noneMovable|{"count":5}');
  });

  it('dry-runs first, then moves for real after the person confirms', () => {
    const s = setup();
    s.c.ngOnInit();
    s.workflow['migrateRunning'].mockImplementation((_k: string, dry: boolean) => of(result({ movable: dry ? 4 : 4 })));
    s.c.migrateRunning();
    expect(s.workflow['migrateRunning']).toHaveBeenNthCalledWith(1, 'p', true);
    expect(s.workflow['migrateRunning']).toHaveBeenNthCalledWith(2, 'p', false);
    expect(s.notify['success']).toHaveBeenCalledWith('workflow.definitions.migrate.done|{"count":4,"version":3}');
  });

  it('does not move anything when declined', () => {
    const s = setup();
    s.c.ngOnInit();
    s.workflow['migrateRunning'].mockReturnValue(of(result({})));
    s.appDialog.confirm.mockReturnValue(of(false));
    s.c.migrateRunning();
    expect(s.workflow['migrateRunning']).toHaveBeenCalledTimes(1);
  });

  it('reports a failed check', () => {
    const s = setup();
    s.c.ngOnInit();
    s.workflow['migrateRunning'].mockReturnValue(throwError(() => ({ error: { errorMsg: 'locked' } })));
    s.c.migrateRunning();
    expect(s.notify['error']).toHaveBeenCalledWith('locked');
  });
});
