import { ChangeDetectorRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { OrganizationContextService } from '../../shared/services/organization-context-service';
import { OrgChange, OrgDraftResult } from '../org-structure.models';
import { OrgBuilder } from './org-builder';

const change = (id: string, over: Partial<OrgChange> = {}): OrgChange =>
  ({ id, changeSetId: 'd1', seq: 1, type: 'UNIT_RENAME', payload: { id: 'u1', name: 'Ops' }, ...over }) as OrgChange;
const clean: OrgDraftResult = { applied: true, issues: [], rootUnits: [] } as never;
const withError: OrgDraftResult = { applied: false, issues: [{ severity: 'ERROR', code: 'X', message: 'Bad' }], rootUnits: [] } as never;
const withWarning: OrgDraftResult = { applied: false, issues: [{ severity: 'WARNING', code: 'X', message: 'Hm' }], rootUnits: [] } as never;

type Api = Record<string, ReturnType<typeof vi.fn>>;

function setup(confirmed = true) {
  const api: Api = {
    currentDraft: vi.fn(() => of({ id: 'd1' })),
    changes: vi.fn(() => of([change('c1'), change('c2')])),
    openDraft: vi.fn(() => of({ id: 'd2' })),
    removeChange: vi.fn(() => of(undefined)),
    preview: vi.fn(() => of(clean)),
    publish: vi.fn(() => of(clean)),
    discard: vi.fn(() => of(undefined)),
    rollbackLast: vi.fn(() => of(undefined)),
  };
  const notify = { error: vi.fn(), success: vi.fn() };
  const dialogs = { confirm: vi.fn((_: unknown): Observable<boolean> => of(confirmed)) };
  const cdr = { detectChanges: vi.fn() };
  const translate = { instant: vi.fn((key: string, params?: object) => key + (params ? JSON.stringify(params) : '')) };
  TestBed.configureTestingModule({ providers: [{ provide: OrganizationContextService, useValue: {} }] });
  const builder = TestBed.runInInjectionContext(
    () => new OrgBuilder(api as never, cdr as unknown as ChangeDetectorRef, notify as never, dialogs as never, translate as never),
  );
  return { builder, api, notify, dialogs, cdr };
}

describe('OrgBuilder', () => {
  it('builds translated table columns and loads the open draft with its changes on init', () => {
    const { builder, api } = setup();
    builder.ngOnInit();
    expect(builder.draft).toEqual({ id: 'd1' });
    expect(api['changes']).toHaveBeenCalledWith('d1');
    expect(builder.changes.map((c) => c.id)).toEqual(['c1', 'c2']);
    expect(builder.isLoading).toBe(false);

    const [seq, type, details] = builder.changeColumns;
    expect(seq.label).toBe('#');
    expect(type.label).toBe('orgStructure.builder.changeType');
    expect(type.render?.(change('c1'))).toBe('orgStructure.changeTypes.UNIT_RENAME');
    expect(details.render?.(change('c1', { payload: { id: 'u1', name: 'Ops', __labels: { id: 'Sales' } } }))).toBe('id: Sales, name: Ops');
    expect(builder.issueColumns[0].render?.({ severity: 'ERROR' } as never)).toBe('orgStructure.builder.severity.ERROR');
    expect(builder.unitColumns.map((c) => c.key)).toEqual(['name', 'code', 'headcount']);
  });

  it('treats a missing draft as a normal empty state rather than an error', () => {
    const { builder, api, notify } = setup();
    api['currentDraft'].mockReturnValue(throwError(() => ({ status: 404 })));
    builder.ngOnInit();
    expect(builder.draft).toBeNull();
    expect(builder.changes).toEqual([]);
    expect(builder.isLoading).toBe(false);
    expect(notify.error).not.toHaveBeenCalled();
  });

  it('reports a failure to load the changes with the server detail', () => {
    const { builder, api, notify } = setup();
    api['changes'].mockReturnValue(throwError(() => ({ error: { detail: 'Boom' } })));
    builder.ngOnInit();
    expect(builder.isLoading).toBe(false);
    expect(notify.error).toHaveBeenCalledWith('Boom');
  });

  it('falls back to a translated message when the error has no detail', () => {
    const { builder, api, notify } = setup();
    api['changes'].mockReturnValue(throwError(() => ({})));
    builder.ngOnInit();
    expect(notify.error).toHaveBeenCalledWith('orgStructure.builder.messages.loadFailed');
  });

  it('does not load changes when there is no draft', () => {
    const { builder, api } = setup();
    builder.draft = null;
    (builder as unknown as { loadChanges(): void }).loadChanges();
    expect(api['changes']).not.toHaveBeenCalled();
  });

  describe('startDraft', () => {
    it('opens a draft with no changes', () => {
      const { builder, api } = setup();
      builder.changes = [change('x')];
      builder.startDraft();
      expect(api['openDraft']).toHaveBeenCalled();
      expect(builder.draft).toEqual({ id: 'd2' });
      expect(builder.changes).toEqual([]);
      expect(builder.busy).toBe(false);
    });

    it('reports the failure and clears busy', () => {
      const { builder, api, notify } = setup();
      api['openDraft'].mockReturnValue(throwError(() => ({ error: { detail: 'Not allowed' } })));
      builder.startDraft();
      expect(builder.busy).toBe(false);
      expect(builder.draft).toBeNull();
      expect(notify.error).toHaveBeenCalledWith('Not allowed');
    });

    it('uses the translated fallback when the server gives no detail', () => {
      const { builder, api, notify } = setup();
      api['openDraft'].mockReturnValue(throwError(() => undefined));
      builder.startDraft();
      expect(notify.error).toHaveBeenCalledWith('orgStructure.builder.messages.startFailed');
    });
  });

  describe('tree and change list', () => {
    it('takes the tree\'s changes and invalidates the preview', () => {
      const { builder } = setup();
      builder.result = clean;
      builder.onTreeChanged([change('n')]);
      expect(builder.changes.map((c) => c.id)).toEqual(['n']);
      expect(builder.result).toBeNull();
    });

    it('removes one change and invalidates the preview', () => {
      const { builder, api } = setup();
      builder.draft = { id: 'd1' } as never;
      builder.changes = [change('c1'), change('c2')];
      builder.result = clean;
      builder.remove(change('c1'));
      expect(api['removeChange']).toHaveBeenCalledWith('d1', 'c1');
      expect(builder.changes.map((c) => c.id)).toEqual(['c2']);
      expect(builder.result).toBeNull();
    });

    it('keeps the list and reports when removal fails', () => {
      const { builder, api, notify } = setup();
      builder.draft = { id: 'd1' } as never;
      builder.changes = [change('c1')];
      api['removeChange'].mockReturnValue(throwError(() => ({})));
      builder.remove(change('c1'));
      expect(builder.changes).toHaveLength(1);
      expect(notify.error).toHaveBeenCalledWith('orgStructure.builder.messages.removeFailed');
    });

    it('ignores remove without a draft', () => {
      const { builder, api } = setup();
      builder.remove(change('c1'));
      expect(api['removeChange']).not.toHaveBeenCalled();
    });
  });

  describe('hasErrorIssues', () => {
    it('is false without a result, with only warnings, and true with an error', () => {
      const { builder } = setup();
      expect(builder.hasErrorIssues).toBe(false);
      builder.result = withWarning;
      expect(builder.hasErrorIssues).toBe(false);
      builder.result = withError;
      expect(builder.hasErrorIssues).toBe(true);
    });
  });

  describe('preview', () => {
    it('stores the result', () => {
      const { builder, api } = setup();
      builder.draft = { id: 'd1' } as never;
      builder.preview();
      expect(api['preview']).toHaveBeenCalledWith('d1');
      expect(builder.result).toBe(clean);
      expect(builder.busy).toBe(false);
    });

    it('reports a failed preview', () => {
      const { builder, api, notify } = setup();
      builder.draft = { id: 'd1' } as never;
      api['preview'].mockReturnValue(throwError(() => ({ error: { detail: 'Preview broke' } })));
      builder.preview();
      expect(notify.error).toHaveBeenCalledWith('Preview broke');
      expect(builder.busy).toBe(false);
    });

    it('does nothing without a draft', () => {
      const { builder, api } = setup();
      builder.preview();
      expect(api['preview']).not.toHaveBeenCalled();
    });
  });

  describe('publish', () => {
    it('does nothing without a draft', () => {
      const { builder, dialogs } = setup();
      builder.publish();
      expect(dialogs.confirm).not.toHaveBeenCalled();
    });

    it('asks for confirmation and does not publish when declined', () => {
      const { builder, api, dialogs } = setup(false);
      builder.draft = { id: 'd1' } as never;
      builder.publish();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'orgStructure.builder.publishDialog.title' }));
      expect(api['publish']).not.toHaveBeenCalled();
    });

    it('reports a transport failure of the publish call', () => {
      const { builder, api, notify } = setup();
      builder.draft = { id: 'd1' } as never;
      api['publish'].mockReturnValue(throwError(() => ({})));
      builder.publish();
      expect(notify.error).toHaveBeenCalledWith('orgStructure.builder.messages.publishFailed');
      expect(builder.busy).toBe(false);
    });

    it('counts all issues when none is an ERROR and tolerates an empty issue list', () => {
      const { builder, api, notify } = setup();
      builder.draft = { id: 'd1' } as never;
      api['publish'].mockReturnValue(of(withWarning));
      builder.publish();
      expect(notify.error).toHaveBeenLastCalledWith('orgStructure.builder.messages.publishBlocked{"count":1,"message":"Hm"}');
      api['publish'].mockReturnValue(of({ applied: false } as OrgDraftResult));
      builder.publish();
      expect(notify.error).toHaveBeenLastCalledWith('orgStructure.builder.messages.publishBlocked{"count":0,"message":""}');
    });
  });

  describe('discard', () => {
    it('does nothing without a draft', () => {
      const { builder, dialogs } = setup();
      builder.discard();
      expect(dialogs.confirm).not.toHaveBeenCalled();
    });

    it('uses a danger confirmation and clears the screen state on success', () => {
      const { builder, api, notify, dialogs } = setup();
      builder.draft = { id: 'd1' } as never;
      builder.changes = [change('c1')];
      builder.result = clean;
      builder.discard();
      expect(dialogs.confirm).toHaveBeenCalledWith(expect.objectContaining({ variant: 'danger' }));
      expect(api['discard']).toHaveBeenCalledWith('d1');
      expect(builder.draft).toBeNull();
      expect(builder.changes).toEqual([]);
      expect(builder.result).toBeNull();
      expect(notify.success).toHaveBeenCalledWith('orgStructure.builder.messages.discarded');
    });

    it('keeps the draft when declined', () => {
      const { builder, api } = setup(false);
      builder.draft = { id: 'd1' } as never;
      builder.discard();
      expect(api['discard']).not.toHaveBeenCalled();
      expect(builder.draft).not.toBeNull();
    });

    it('keeps the draft and reports when discarding fails', () => {
      const { builder, api, notify } = setup();
      builder.draft = { id: 'd1' } as never;
      api['discard'].mockReturnValue(throwError(() => ({ error: { detail: 'No' } })));
      builder.discard();
      expect(builder.draft).not.toBeNull();
      expect(notify.error).toHaveBeenCalledWith('No');
    });
  });

  describe('rollbackLast', () => {
    it('rolls back after confirmation and clears the preview', () => {
      const { builder, api, notify } = setup();
      builder.result = clean;
      builder.rollbackLast();
      expect(api['rollbackLast']).toHaveBeenCalled();
      expect(builder.result).toBeNull();
      expect(builder.busy).toBe(false);
      expect(notify.success).toHaveBeenCalledWith('orgStructure.builder.messages.rolledBack');
    });

    it('does not roll back when declined', () => {
      const { builder, api } = setup(false);
      builder.rollbackLast();
      expect(api['rollbackLast']).not.toHaveBeenCalled();
    });

    it('reports a refused rollback (something newer exists)', () => {
      const { builder, api, notify } = setup();
      api['rollbackLast'].mockReturnValue(throwError(() => ({ error: { detail: 'Newer exists' } })));
      builder.rollbackLast();
      expect(notify.error).toHaveBeenCalledWith('Newer exists');
      expect(builder.busy).toBe(false);
    });
  });
});
