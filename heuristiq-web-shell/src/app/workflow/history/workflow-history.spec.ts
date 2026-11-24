import { ChangeDetectorRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UamService } from '../../shared/services/uam-service';
import { WorkflowService } from '../workflow-service';
import { TimelineEntry } from '../workflow.models';
import { WORKFLOW_HISTORY_DIALOG_WIDTH } from './workflow-history.constants';
import { WorkflowHistoryDialog } from './workflow-history-dialog';
import { summaryOf, userIdsOf, variableText } from './workflow-history.helpers';
import { WorkflowHistoryService } from './workflow-history.service';
import { WorkflowPeopleService } from './workflow-people.service';
import { WorkflowTimeline } from './workflow-timeline';

const entry = (over: Partial<TimelineEntry>): TimelineEntry => ({ at: '2026-01-01T00:00:00Z', type: 'COMMENT', ...over });
const labels: Record<string, string> = { 'workflow.decisions.APPROVED': 'Approved', 'onboardingSubmissions.types.DRIVER': 'Driver' };
const translate = { instant: (k: string) => labels[k] ?? k };

describe('workflow history helpers', () => {
  it('finds the people a workflow names', () => {
    expect(userIdsOf({ decidedBy: 'u1', startedBy: '', subjectId: 's1', reviewer: 7 })).toEqual(['u1']);
  });

  it('summarises a request in plain words: reference, type, decision and who decided, by name', () => {
    const vars = { reference: 'DRV-2026-000019', typeCode: 'DRIVER', decision: 'APPROVED', decidedBy: 'u1', callbackUrl: 'http://x', subjectId: 's1' };
    expect(summaryOf(vars, { u1: 'RealBoom Super Admin' }, translate.instant)).toEqual([
      { labelKey: 'workflow.history.fields.reference', value: 'DRV-2026-000019' },
      { labelKey: 'workflow.history.fields.type', value: 'Driver' },
      { labelKey: 'workflow.history.fields.decision', value: 'Approved' },
      { labelKey: 'workflow.history.fields.decidedBy', value: 'RealBoom Super Admin' },
    ]);
    expect(summaryOf({ typeCode: 'VENDOR', decision: 'ODD', decidedBy: 'u9', reference: '' }, {}, translate.instant)).toEqual([
      { labelKey: 'workflow.history.fields.type', value: 'VENDOR' },
      { labelKey: 'workflow.history.fields.decision', value: 'ODD' },
      { labelKey: 'workflow.history.fields.decidedBy', value: 'u9' },
    ]);
  });

  it('shows a variable as text: a person by name with the id, an object as JSON', () => {
    expect(variableText('decidedBy', 'u1', { u1: 'Asha' })).toBe('Asha (u1)');
    expect(variableText('decidedBy', 'u2', {})).toBe('u2');
    expect(variableText('subjectId', 'u1', { u1: 'Asha' })).toBe('u1');
    expect(variableText('x', { n: 1 }, {})).toBe('{"n":1}');
    expect(variableText('x', null, {})).toBe('null');
  });
});

describe('WorkflowPeopleService', () => {
  it('reads each person once, remembers the name and falls back to the id', () => {
    const uam = { getUser: vi.fn((id: string) => (id === 'u2' ? throwError(() => new Error('x')) : of({ displayName: id === 'u1' ? 'Asha' : '', userName: 'kiran' }))) };
    TestBed.configureTestingModule({ providers: [{ provide: UamService, useValue: uam }] });
    const people = TestBed.inject(WorkflowPeopleService);
    let names: Record<string, string> = {};
    people.names(['u1', 'u2', 'u3', null, 'u1']).subscribe((n) => (names = n));
    expect(names).toEqual({ u1: 'Asha', u2: 'u2', u3: 'kiran' });
    people.names(['u1']).subscribe((n) => (names = n));
    expect(uam.getUser).toHaveBeenCalledTimes(3);
    people.names([]).subscribe((n) => (names = n));
    expect(names).toEqual({});
  });
});

describe('WorkflowTimeline', () => {
  const workflow = { timeline: vi.fn(), downloadAttachment: vi.fn() };
  const people = { names: vi.fn() };

  function make(): WorkflowTimeline {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: WorkflowService, useValue: workflow },
        { provide: WorkflowPeopleService, useValue: people },
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
        { provide: ChangeDetectorRef, useValue: { markForCheck: vi.fn() } },
      ],
    });
    return TestBed.runInInjectionContext(() => new WorkflowTimeline());
  }

  beforeEach(() => {
    vi.clearAllMocks();
    workflow.timeline.mockReturnValue(of({ instanceId: 'i1', active: false, entries: [entry({ actor: 'u1' }), entry({ type: 'REASSIGNED', actor: 'u1', text: 'u2' }), entry({ actor: null })] }));
    workflow.downloadAttachment.mockReturnValue(of(undefined));
    people.names.mockReturnValue(of({ u1: 'Asha', u2: 'u2' }));
  });

  it('reads the history for its instance and names the people on it', () => {
    const t = make();
    t.instanceId = 'i1';
    expect(workflow.timeline).toHaveBeenCalledWith('i1');
    expect(people.names).toHaveBeenCalledWith(['u1', null, 'u1', 'u2', null, null]);
    expect(t.timeline?.entries).toHaveLength(3);
    expect(t.actorName(entry({ actor: 'u1' }))).toBe('Asha');
    expect(t.actorName(entry({ actor: 'zz' }))).toBe('zz');
    expect(t.actorName(entry({ actor: null }))).toBe('workflow.timeline.system');
    expect(t.targetName(entry({ text: 'u1' }))).toBe('Asha');
    expect(t.targetName(entry({ text: null }))).toBe('');
  });

  it('reads nothing without an instance, reads again on refresh, and says when the history cannot be read', () => {
    const t = make();
    t.instanceId = '';
    t.refresh = 1;
    expect(workflow.timeline).not.toHaveBeenCalled();
    t.instanceId = 'i1';
    t.refresh = 2;
    expect(workflow.timeline).toHaveBeenCalledTimes(2);
    workflow.timeline.mockReturnValue(throwError(() => new Error('x')));
    t.load();
    expect(t.failed).toBe(true);
    expect(t.timeline).toBeNull();
  });

  it('downloads an attachment and shows a download failure', () => {
    const t = make();
    t.download(entry({ type: 'ATTACHMENT' }));
    expect(workflow.downloadAttachment).not.toHaveBeenCalled();
    t.download(entry({ type: 'ATTACHMENT', attachmentId: 'd1', text: 'a.pdf' }));
    expect(workflow.downloadAttachment).toHaveBeenCalledWith('d1', 'a.pdf');
    workflow.downloadAttachment.mockReturnValue(throwError(() => ({})));
    t.download(entry({ attachmentId: 'd1' }));
    expect(t.errorText).toBe('workflow.timeline.downloadFailed');
  });

  it('formats durations and picks an icon per entry', () => {
    const t = make();
    const min = 60000;
    expect(t.duration(null)).toBe('');
    expect(t.duration(-1)).toBe('');
    expect(t.duration(40 * min)).toBe('40workflow.timeline.units.m');
    expect(t.duration(185 * min)).toBe('3workflow.timeline.units.h 5workflow.timeline.units.m');
    expect(t.duration(1560 * min)).toBe('1workflow.timeline.units.d 2workflow.timeline.units.h');
    const icon = (type: TimelineEntry['type'], decision?: string) => t.iconOf(entry({ type, decision }));
    expect(icon('STARTED')).toBe('bi-send');
    expect(icon('TASK_CREATED')).toBe('bi-hourglass-split');
    expect(icon('CLAIMED')).toBe('bi-person-check');
    expect(icon('RELEASED')).toBe('bi-box-arrow-left');
    expect(icon('RETURNED')).toBe('bi-arrow-return-left');
    expect(icon('ATTACHMENT')).toBe('bi-paperclip');
    expect(icon('REASSIGNED')).toBe('bi-person-up');
    expect(icon('COMMENT')).toBe('bi-chat-left-text');
    expect(icon('COMPLETED', 'REJECTED')).toBe('bi-x-circle');
    expect(icon('COMPLETED', 'APPROVED')).toBe('bi-check-circle');
    expect(icon('ENDED', 'REJECTED')).toBe('bi-x-octagon');
    expect(icon('ENDED', 'RETURNED')).toBe('bi-arrow-return-left');
    expect(icon('ENDED', 'APPROVED')).toBe('bi-flag');
    expect(t.trackByEntry(4)).toBe(4);
  });
});

describe('WorkflowHistoryDialog', () => {
  const workflow = { instance: vi.fn() };
  const people = { names: vi.fn() };
  const ref = { close: vi.fn() };
  const router = { navigate: vi.fn().mockResolvedValue(true) };

  function make(): WorkflowHistoryDialog {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { instanceId: 'i1', title: 'DRV-2026-000019', subtitle: 'Saidu Reddy' } },
        { provide: MatDialogRef, useValue: ref },
        { provide: WorkflowService, useValue: workflow },
        { provide: WorkflowPeopleService, useValue: people },
        { provide: TranslateService, useValue: translate },
        { provide: Router, useValue: router },
        { provide: ChangeDetectorRef, useValue: { markForCheck: vi.fn() } },
      ],
    });
    const d = TestBed.runInInjectionContext(() => new WorkflowHistoryDialog());
    d.ngOnInit();
    return d;
  }

  beforeEach(() => {
    vi.clearAllMocks();
    workflow.instance.mockReturnValue(of({ instanceId: 'i1', active: false, variables: { reference: 'DRV-2026-000019', decidedBy: 'u1', callbackUrl: 'http://x' } }));
    people.names.mockReturnValue(of({ u1: 'RealBoom Super Admin' }));
  });

  it('shows the request in plain words with the decider by name, and no internal values', () => {
    const d = make();
    expect(d.active).toBe(false);
    expect(d.summary).toEqual([
      { labelKey: 'workflow.history.fields.reference', value: 'DRV-2026-000019' },
      { labelKey: 'workflow.history.fields.decidedBy', value: 'RealBoom Super Admin' },
    ]);
  });

  it('opens the full Workflow page and closes, or just closes', () => {
    const d = make();
    d.openFull();
    expect(ref.close).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/workflow', 'instances', 'i1']);
    d.close();
    expect(ref.close).toHaveBeenCalledTimes(2);
  });

  it('still opens when the instance cannot be read (the timeline explains)', () => {
    workflow.instance.mockReturnValue(throwError(() => ({ status: 500 })));
    const d = make();
    expect(d.summary).toEqual([]);
    expect(d.active).toBeNull();
  });
});

describe('WorkflowHistoryService', () => {
  it('opens the dialog with the request, and nothing without an instance', () => {
    const dialog = { open: vi.fn() };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: MatDialog, useValue: dialog }] });
    const service = TestBed.inject(WorkflowHistoryService);
    service.open({ instanceId: '' });
    expect(dialog.open).not.toHaveBeenCalled();
    service.open({ instanceId: 'i1', title: 'DRV-1' });
    expect(dialog.open).toHaveBeenCalledWith(WorkflowHistoryDialog, expect.objectContaining({
      data: { instanceId: 'i1', title: 'DRV-1' }, width: WORKFLOW_HISTORY_DIALOG_WIDTH,
    }));
  });
});
