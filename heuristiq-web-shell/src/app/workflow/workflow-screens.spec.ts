import { convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { csvCell, csvRow, downloadCsv } from './reports/csv';
import { WorkflowDefinitions } from './definitions/workflow-definitions';
import { WorkflowInstanceView } from './instance/workflow-instance';
import { WorkflowReports } from './reports/workflow-reports';
import { WorkflowShell } from './workflow-shell/workflow-shell';
import { TestBed } from '@angular/core/testing';
import { HQ_PERMISSION_PRIVILEGES } from 'hq-shared-lib';
import { AuthService } from '../shared/services/auth-service';
import { WORKFLOW_DESIGNER_PERMISSION, WORKFLOW_TABS, WORKFLOW_TASKS_PERMISSION } from './workflow.constants';
import { WorkflowStart } from './start/workflow-start';
import { WORKFLOW_TEMPLATES } from './definitions/workflow-templates';

const translate = { instant: (k: string, p?: object) => (p ? `${k}|${JSON.stringify(p)}` : k), onLangChange: of() };
const cdr = { detectChanges: vi.fn() };

describe('csv helpers', () => {
  it('quotes cells that need it and doubles embedded quotes', () => {
    expect(csvCell('plain')).toBe('plain');
    expect(csvCell('a,b')).toBe('"a,b"');
    expect(csvCell('say "hi"')).toBe('"say ""hi"""');
    expect(csvCell('line\nbreak')).toBe('"line\nbreak"');
  });

  it('renders null, undefined and numbers', () => {
    expect(csvCell(null)).toBe('');
    expect(csvCell(undefined)).toBe('');
    expect(csvCell(0)).toBe('0');
    expect(csvCell(-5)).toBe('-5');
  });

  it('neutralises spreadsheet formulas in text but not in numbers', () => {
    for (const bad of ['=SUM(A1)', '+1', '-1', '@x', '\tcmd']) expect(csvCell(bad)).toBe(`'${bad}`);
    expect(csvCell('=HYPERLINK("x")')).toBe(`"'=HYPERLINK(""x"")"`);
  });

  it('joins a row with commas', () => {
    expect(csvRow(['a', 1, null, 'b,c'])).toBe('a,1,,"b,c"');
  });

  describe('downloadCsv', () => {
    let blobs: Blob[];
    let anchor: HTMLAnchorElement | undefined;
    beforeEach(() => {
      blobs = [];
      (URL as unknown as { createObjectURL: unknown }).createObjectURL = vi.fn((b: Blob) => { blobs.push(b); return 'blob:csv'; });
      (URL as unknown as { revokeObjectURL: unknown }).revokeObjectURL = vi.fn();
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { anchor = this; });
    });
    afterEach(() => vi.restoreAllMocks());

    it('saves UTF-8 text with a BOM and CRLF line breaks under the given name', async () => {
      downloadCsv('r.csv', ['a,b', 'c,d']);
      expect(anchor?.download).toBe('r.csv');
      expect(blobs[0].type).toBe('text/csv;charset=utf-8');
      const text = await blobs[0].text();
      expect(text).toBe('a,b\r\nc,d');
      expect(new Uint8Array(await blobs[0].arrayBuffer()).slice(0, 3)).toEqual(new Uint8Array([0xef, 0xbb, 0xbf]));
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:csv');
    });
  });
});

describe('WorkflowShell', () => {
  const shellFor = (allowed: (permission: string) => boolean): WorkflowShell => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: { canAccess: allowed } }] });
    return TestBed.runInInjectionContext(() => new WorkflowShell());
  };

  it('offers the four tabs and hides Start process', () => {
    const tabs = shellFor(() => true).tabs;
    expect(tabs.map((t) => t.path)).toEqual(['/workflow/tasks', '/workflow/definitions', '/workflow/instances', '/workflow/reports']);
    expect(tabs.every((t) => t.labelKey.startsWith('workflow.tabs.'))).toBe(true);
  });

  it('shows someone who only works tasks the task inbox and instances, not the designer or reports', () => {
    const tabs = shellFor((permission) => permission === WORKFLOW_TASKS_PERMISSION).tabs;
    expect(tabs.map((t) => t.path)).toEqual(['/workflow/tasks', '/workflow/instances']);
  });

  it('guards every workflow screen: the inbox for whoever works tasks, the designer and reports for whoever reads definitions', () => {
    expect(WORKFLOW_TABS.map((t) => [t.path, t.permission])).toEqual([
      ['/workflow/tasks', WORKFLOW_TASKS_PERMISSION], ['/workflow/definitions', WORKFLOW_DESIGNER_PERMISSION],
      ['/workflow/instances', WORKFLOW_TASKS_PERMISSION], ['/workflow/reports', WORKFLOW_DESIGNER_PERMISSION],
    ]);
    expect([...HQ_PERMISSION_PRIVILEGES[WORKFLOW_TASKS_PERMISSION]]).toEqual(['WORKFLOW_TASK_LIST']);
    expect([...HQ_PERMISSION_PRIVILEGES[WORKFLOW_DESIGNER_PERMISSION]]).toEqual(['WORKFLOW_DEFINITION_LIST']);
  });
});

describe('WorkflowStart', () => {
  function make() {
    const workflow = { startInstance: vi.fn().mockReturnValue(of('inst-1')) };
    const router = { navigate: vi.fn().mockResolvedValue(true) };
    const notify = { success: vi.fn(), error: vi.fn() };
    const c = new WorkflowStart(workflow as never, router as never, notify as never, translate as never, cdr as never);
    return { c, workflow, router, notify };
  }
  const fill = (c: WorkflowStart) => Object.assign(c, { subjectType: ' DRIVER ', subjectId: ' 7 ', callbackUrl: ' http://cb ' });

  it('defaults to the generic review process and is invalid until every field is filled', () => {
    const { c } = make();
    expect(c.processKey).toBe('generic-review');
    expect(c.isValid).toBe(false);
    fill(c);
    expect(c.isValid).toBe(true);
    c.processKey = '  ';
    expect(c.isValid).toBe(false);
  });

  it('does not call the service for an incomplete form', () => {
    const { c, workflow } = make();
    c.submit();
    expect(c.submitted).toBe(true);
    expect(workflow.startInstance).not.toHaveBeenCalled();
  });

  it('flags variables that are not a JSON object', () => {
    const { c, workflow } = make();
    fill(c);
    c.variablesText = '[1]';
    c.submit();
    expect(c.invalidJson).toBe(true);
    expect(workflow.startInstance).not.toHaveBeenCalled();
  });

  it('starts the process with trimmed values, tells the user and opens the instance', () => {
    const { c, workflow, router, notify } = make();
    fill(c);
    c.variablesText = '{"a":1}';
    c.submit();
    expect(workflow.startInstance).toHaveBeenCalledWith('generic-review', { subjectType: 'DRIVER', subjectId: '7', callbackUrl: 'http://cb', variables: { a: 1 } });
    expect(notify.success).toHaveBeenCalledWith('workflow.start.started|{"id":"inst-1"}');
    expect(router.navigate).toHaveBeenCalledWith(['/workflow', 'instances', 'inst-1']);
    expect(c.isSaving).toBe(false);
  });

  it('shows the service message or a generic one on failure and allows retry', () => {
    const { c, workflow, notify } = make();
    fill(c);
    workflow.startInstance.mockReturnValue(throwError(() => ({ error: { errorMsg: 'no such process' } })));
    c.submit();
    expect(notify.error).toHaveBeenCalledWith('no such process');
    workflow.startInstance.mockReturnValue(throwError(() => ({})));
    c.submit();
    expect(notify.error).toHaveBeenLastCalledWith('workflow.start.failed');
    expect(c.isSaving).toBe(false);
  });

  it('ignores a second submit while one is running', () => {
    const { c, workflow } = make();
    fill(c);
    c.isSaving = true;
    c.submit();
    expect(workflow.startInstance).not.toHaveBeenCalled();
  });
});

describe('WorkflowDefinitions', () => {
  function make(role = 'SUPER_ADMIN') {
    const workflow = { definitions: vi.fn().mockReturnValue(of([{ key: 'a', name: 'A' }, { key: 'b', name: 'B' }])) };
    const router = { navigate: vi.fn().mockResolvedValue(true) };
    const auth = { currentUser: { role } as { role: string } | null };
    const notify = { error: vi.fn() };
    const c = new WorkflowDefinitions(workflow as never, router as never, auth as never, notify as never, translate as never, cdr as never);
    return { c, workflow, router, auth, notify };
  }

  it('lists the definitions on init, or shows an error state', () => {
    const { c, workflow } = make();
    c.ngOnInit();
    expect(c.definitions.map((d) => d.key)).toEqual(['a', 'b']);
    expect(c.isLoading).toBe(false);
    workflow.definitions.mockReturnValue(throwError(() => new Error('x')));
    c.load();
    expect(c.hasError).toBe(true);
    expect(c.definitions).toEqual([]);
  });

  it('opens a definition and tracks rows by key', () => {
    const { c, router } = make();
    c.open({ key: 'a' } as never);
    expect(router.navigate).toHaveBeenCalledWith(['/workflow', 'definitions', 'a']);
    expect(c.trackByKey(0, { key: 'zz' } as never)).toBe('zz');
  });

  it('lets only the editor role create workflows (as the code stands)', () => {
    const { c, auth } = make();
    expect(c.canCreate).toBe(true);
    auth.currentUser = { role: 'STAFF' };
    expect(c.canCreate).toBe(false);
    auth.currentUser = null;
    expect(c.canCreate).toBe(false);
  });

  it('selects the chosen template, falling back to the first', () => {
    const { c } = make();
    expect(c.selectedTemplate).toBe(WORKFLOW_TEMPLATES[0]);
    c.templateId = WORKFLOW_TEMPLATES[1].id;
    expect(c.selectedTemplate).toBe(WORKFLOW_TEMPLATES[1]);
    c.templateId = 'unknown';
    expect(c.selectedTemplate).toBe(WORKFLOW_TEMPLATES[0]);
  });

  it('refuses a malformed or already used key', () => {
    const { c, router, notify } = make();
    c.ngOnInit();
    c.newKey = '9 bad';
    c.createFromTemplate();
    expect(notify.error).toHaveBeenLastCalledWith('workflow.definitions.templates.badKey');
    c.newKey = ' a ';
    c.createFromTemplate();
    expect(notify.error).toHaveBeenLastCalledWith('workflow.definitions.templates.keyTaken');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('opens the editor for a new workflow with the template, key and name in the URL', () => {
    const { c, router } = make();
    c.ngOnInit();
    c.newKey = 'new-flow';
    c.createFromTemplate();
    expect(router.navigate).toHaveBeenCalledWith(['/workflow', 'definitions', 'new'], { queryParams: { template: c.templateId, key: 'new-flow', name: 'new-flow' } });
    c.newName = ' My flow ';
    c.createFromTemplate();
    expect(router.navigate).toHaveBeenLastCalledWith(['/workflow', 'definitions', 'new'], { queryParams: { template: c.templateId, key: 'new-flow', name: 'My flow' } });
  });
});

describe('WorkflowInstanceView', () => {
  function make() {
    const params = new BehaviorSubject(convertToParamMap({}));
    const route = { paramMap: params.asObservable() };
    const router = { navigate: vi.fn().mockResolvedValue(true) };
    const workflow = {
      instance: vi.fn().mockReturnValue(of({ instanceId: 'i1', active: true, variables: { zeta: 1, subjectId: '9', alpha: { n: 1 }, subjectType: 'D', nul: null, decidedBy: 'u1' } })),
      instanceIdByReference: vi.fn().mockReturnValue(of('i-from-ref')),
    };
    const people = { names: vi.fn().mockReturnValue(of({ u1: 'Asha' })) };
    const c = new WorkflowInstanceView(route as never, router as never, workflow as never, translate as never, people as never, cdr as never);
    return { c, params, router, workflow, people };
  }

  it('shows nothing until an id is in the route', () => {
    const { c, workflow } = make();
    c.ngOnInit();
    expect(workflow.instance).not.toHaveBeenCalled();
    expect(c.instance).toBeNull();
  });

  it('loads the instance for the id in the route with core variables first, objects as JSON', () => {
    const { c, params, workflow } = make();
    c.ngOnInit();
    params.next(convertToParamMap({ id: 'i1' }));
    expect(c.lookupId).toBe('i1');
    expect(workflow.instance).toHaveBeenCalledWith('i1');
    expect(c.rows.map((r) => r.key)).toEqual(['subjectId', 'subjectType', 'alpha', 'decidedBy', 'nul', 'zeta']);
    expect(c.rows.find((r) => r.key === 'alpha')?.value).toBe('{"n":1}');
    expect(c.rows.find((r) => r.key === 'nul')?.value).toBe('null');
    expect(c.isLoading).toBe(false);
  });

  it('shows a person held by a variable by name, with the id after it', () => {
    const { c, params, people } = make();
    c.ngOnInit();
    params.next(convertToParamMap({ id: 'i1' }));
    expect(people.names).toHaveBeenCalledWith(['u1']);
    expect(c.rows.find((r) => r.key === 'decidedBy')?.value).toBe('Asha (u1)');
  });

  it('tells a missing instance from other failures', () => {
    const { c, params, workflow } = make();
    c.ngOnInit();
    workflow.instance.mockReturnValue(throwError(() => ({ status: 404 })));
    params.next(convertToParamMap({ id: 'gone' }));
    expect(c.errorText).toBe('workflow.instance.notFound');
    expect(c.instance).toBeNull();
    workflow.instance.mockReturnValue(throwError(() => ({ error: { errorMsg: 'engine down' } })));
    params.next(convertToParamMap({ id: 'x' }));
    expect(c.errorText).toBe('engine down');
    workflow.instance.mockReturnValue(throwError(() => ({})));
    params.next(convertToParamMap({ id: 'y' }));
    expect(c.errorText).toBe('workflow.instance.loadFailed');
  });

  it('reload re-reads the shown instance and asks the timeline to read the history again', () => {
    const { c, params, workflow } = make();
    c.reload();
    expect(workflow.instance).not.toHaveBeenCalled();
    c.ngOnInit();
    params.next(convertToParamMap({ id: 'i1' }));
    workflow.instance.mockClear();
    c.reload();
    expect(workflow.instance).toHaveBeenCalledWith('i1');
    expect(c.refreshCount).toBe(1);
  });

  describe('find', () => {
    it('does nothing for a blank lookup', () => {
      const { c, router } = make();
      c.lookupId = '  ';
      c.find();
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('opens an instance id through the route so the URL is shareable', () => {
      const { c, router, workflow } = make();
      c.lookupId = ' abc-123 ';
      c.find();
      expect(router.navigate).toHaveBeenCalledWith(['/workflow', 'instances', 'abc-123']);
      expect(workflow.instanceIdByReference).not.toHaveBeenCalled();
    });

    it('resolves a short reference such as DRV-2026-000042 to its instance first', () => {
      const { c, router, workflow } = make();
      c.lookupId = 'DRV-2026-000042';
      c.find();
      expect(workflow.instanceIdByReference).toHaveBeenCalledWith('DRV-2026-000042');
      expect(router.navigate).toHaveBeenCalledWith(['/workflow', 'instances', 'i-from-ref']);
    });

    it('reports an unknown reference, or another failure', () => {
      const { c, workflow } = make();
      c.lookupId = 'DRV-2026-1';
      workflow.instanceIdByReference.mockReturnValue(throwError(() => ({ status: 404 })));
      c.find();
      expect(c.errorText).toBe('workflow.instance.referenceNotFound');
      workflow.instanceIdByReference.mockReturnValue(throwError(() => ({ error: { message: 'bad' } })));
      c.find();
      expect(c.errorText).toBe('bad');
      workflow.instanceIdByReference.mockReturnValue(throwError(() => ({})));
      c.find();
      expect(c.errorText).toBe('workflow.instance.loadFailed');
    });
  });
});

describe('WorkflowReports', () => {
  const report = (reviewers: Array<{ userId: string | null }> = [{ userId: 'u1' }, { userId: null }, { userId: 'u1' }]) => ({
    from: '', to: '', truncated: false,
    totals: { started: 10, finished: 8, approved: 6, rejected: 2, returned: 0, openTasks: 2, overdueOpenTasks: 1 },
    steps: [{ processKey: 'p', taskKey: 't', taskName: '', completed: 3, avgDurationMillis: 120000, maxDurationMillis: 300000, withSla: 4, breached: 1 }],
    reviewers: reviewers.map((r) => ({ ...r, completed: 2, approved: 1, rejected: 1, avgDurationMillis: 60000, breached: 0 })),
    open: [{ processKey: 'p', taskKey: 'o', taskName: 'Open step', open: 2, overdue: 1, oldestAgeMillis: 600000 }],
  });

  function make() {
    const workflow = { report: vi.fn().mockReturnValue(of(report())) };
    const uam = { getUser: vi.fn((id: string) => (id === 'bad' ? throwError(() => new Error('x')) : of({ displayName: 'Asha' }))) };
    const c = new WorkflowReports(workflow as never, uam as never, translate as never, cdr as never);
    return { c, workflow, uam };
  }

  afterEach(() => vi.useRealTimers());

  it('starts with the last 30 days ending today and loads the report', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 31, 12));
    const { c, workflow } = make();
    c.ngOnInit();
    expect(c.to).toBe('2026-03-31');
    expect(c.from).toBe('2026-03-01');
    expect(workflow.report).toHaveBeenCalledWith('2026-03-01', '2026-03-31', '');
    expect(c.report?.totals.started).toBe(10);
    expect(c.isLoading).toBe(false);
  });

  it('looks up each reviewer once and shows names, with the id as fallback', () => {
    const { c, uam, workflow } = make();
    c.ngOnInit();
    expect(uam.getUser).toHaveBeenCalledTimes(1);
    expect(c.person('u1')).toBe('Asha');
    expect(c.person('other')).toBe('other');
    expect(c.person(null)).toBe('workflow.reports.unknownPerson');
    workflow.report.mockReturnValue(of(report([{ userId: 'bad' }])));
    c.load();
    expect(c.names).toEqual({ bad: 'bad' });
  });

  it('skips name lookups when there are no reviewers', () => {
    const { c, uam, workflow } = make();
    workflow.report.mockReturnValue(of(report([])));
    c.ngOnInit();
    expect(uam.getUser).not.toHaveBeenCalled();
  });

  it('shows an error state and clears the report when loading fails', () => {
    const { c, workflow } = make();
    workflow.report.mockReturnValue(throwError(() => new Error('x')));
    c.report = report() as never;
    c.load();
    expect(c.hasError).toBe(true);
    expect(c.report).toBeNull();
  });

  it('formats durations and breach rates', () => {
    const { c } = make();
    expect(c.duration(0)).toBe('—');
    expect(c.duration(30000)).toBe('<1workflow.timeline.units.m');
    expect(c.duration(40 * 60000)).toBe('40workflow.timeline.units.m');
    expect(c.duration(125 * 60000)).toBe('2workflow.timeline.units.h 5workflow.timeline.units.m');
    expect(c.duration(1500 * 60000)).toBe('1workflow.timeline.units.d 1workflow.timeline.units.h');
    expect(c.breachRate(0, 0)).toBeNull();
    expect(c.breachRate(3, 1)).toBe(33);
    expect(c.breachRate(4, 4)).toBe(100);
    expect(c.trackByIndex(3)).toBe(3);
  });

  describe('exportCsv', () => {
    let blobs: Blob[];
    let anchor: HTMLAnchorElement | undefined;
    beforeEach(() => {
      blobs = [];
      anchor = undefined;
      (URL as unknown as { createObjectURL: unknown }).createObjectURL = vi.fn((b: Blob) => { blobs.push(b); return 'blob:x'; });
      (URL as unknown as { revokeObjectURL: unknown }).revokeObjectURL = vi.fn();
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { anchor = this; });
    });
    afterEach(() => vi.restoreAllMocks());

    it('does nothing before a report is loaded', () => {
      const { c } = make();
      c.exportCsv();
      expect(blobs).toEqual([]);
    });

    it('writes totals, steps, people by name and waiting items, with durations in minutes', async () => {
      const { c } = make();
      c.from = '2026-01-01';
      c.to = '2026-01-31';
      c.processKey = ' p ';
      c.ngOnInit();
      c.from = '2026-01-01';
      c.to = '2026-01-31';
      c.exportCsv();
      expect(anchor?.download).toBe('workflow-report-2026-01-01_2026-01-31.csv');
      const lines = (await blobs[0].text()).replace(/^﻿/, '').split('\r\n');
      expect(lines[0]).toBe('workflow.reports.export.period,2026-01-01,2026-01-31,p');
      expect(lines).toContain('10,8,6,2,0,2,1');
      expect(lines).toContain('t,p,3,2,5,4,1');
      expect(lines).toContain('Asha,2,1,1,1,0');
      expect(lines).toContain('workflow.reports.unknownPerson,2,1,1,1,0');
      expect(lines).toContain('Open step,p,2,1,10');
    });

    it('falls back to start/today in the file name when no dates are set', () => {
      const { c } = make();
      c.report = report([]) as never;
      c.from = '';
      c.to = '';
      c.exportCsv();
      expect(anchor?.download).toBe('workflow-report-start_today.csv');
    });
  });
});
