import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { HqPrivilegeAccessService, HqSharedLibModule } from 'hq-shared-lib';
import { EMPTY, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AppDialogService } from '../shared/services/app-dialog-service';
import { AuthService } from '../shared/services/auth-service';
import { NotificationService } from '../shared/services/notification-service';
import { WORKSPACE_GROUPED_PAGE_SIZE, WORKSPACE_PAGE_SIZE, WORKSPACE_PRIVILEGES, WORKSPACE_TABS } from './workspace.constants';
import { WorkspaceItem, WorkspacePage } from './workspace.models';
import { WorkspaceService } from './workspace.service';
import { Workspace } from './workspace';
import { WorkspaceHelp } from './workspace-help/workspace-help';

const item = (over: Partial<WorkspaceItem> = {}): WorkspaceItem => ({
  id: 'i1', kind: 'TODO', title: 'call vendor', body: null, ownerId: 'alice', assigneeId: null, visibility: 'PRIVATE', status: 'OPEN',
  priority: 'NORMAL', dueAt: null, remindAt: null, refType: null, refId: null, createdAt: '2026-10-24T10:00:00Z',
  updatedAt: '2026-10-24T10:00:00Z', completedAt: null, ...over,
});

const page = (content: WorkspaceItem[], total = content.length): WorkspacePage => ({ content, totalElements: total, totalPages: 1, size: 20, number: 0 });

describe('Workspace', () => {
  const svc = { list: vi.fn(), create: vi.fn(), update: vi.fn(), setStatus: vi.fn(), remove: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const dialog = { confirm: vi.fn() };
  let granted: Set<string> | null = null;
  const access = {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted?.has(c)),
  };
  let fixture: ComponentFixture<Workspace>;
  let w: Workspace;

  /** The component, not yet rendered (so ngOnInit has not run): tests call what they need. */
  function create(userId: string | null = 'alice'): Workspace {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [Workspace, WorkspaceHelp],
      imports: [FormsModule, TranslateModule.forRoot(), HqSharedLibModule],
      providers: [
        { provide: WorkspaceService, useValue: svc },
        { provide: AuthService, useValue: { currentUser: userId ? { id: userId } : null } },
        { provide: NotificationService, useValue: notify },
        { provide: AppDialogService, useValue: dialog },
        { provide: HqPrivilegeAccessService, useValue: access },
      ],
    });
    fixture = TestBed.createComponent(Workspace);
    return fixture.componentInstance;
  }

  const q = (selector: string): HTMLElement | null => (fixture.nativeElement as HTMLElement).querySelector(selector);
  const all = (selector: string): HTMLElement[] => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll(selector)) as HTMLElement[];

  beforeEach(() => {
    vi.clearAllMocks();
    granted = null;
    svc.list.mockReturnValue(of(page([])));
    dialog.confirm.mockReturnValue(of(true));
    w = create();
  });

  it('starts on the service desk, which loads its own queues, and loads only the kind and scope of another tab', () => {
    w.ngOnInit();

    expect(w.active.key).toBe('desk');
    expect(svc.list).not.toHaveBeenCalled();

    w.selectTab(WORKSPACE_TABS[1]);
    expect(w.active.key).toBe('notes');
    expect(svc.list).toHaveBeenCalledWith({ kinds: ['NOTE'], scope: 'MINE' }, 0, WORKSPACE_PAGE_SIZE);
  });

  it('switching tab loads that tab and drops a half-written edit', () => {
    w.ngOnInit();
    w.form.title = 'draft';
    w.editingId = 'x';

    w.selectTab(WORKSPACE_TABS[3]);

    expect(w.editingId).toBeNull();
    expect(w.form.title).toBe('');
    expect(svc.list).toHaveBeenLastCalledWith({ kinds: ['TASK'], scope: 'VISIBLE', activeOnly: true }, 0, WORKSPACE_GROUPED_PAGE_SIZE);
    svc.list.mockClear();
    w.selectTab(WORKSPACE_TABS[3]);
    expect(svc.list).not.toHaveBeenCalled();
  });

  it('lists open items before finished ones and offers more while there are more', () => {
    w.ngOnInit();
    svc.list.mockReturnValueOnce(of(page([item({ id: 'a', status: 'DONE' }), item({ id: 'b' })], 3)));

    w.selectTab(WORKSPACE_TABS[2]);

    expect(w.items.map((i) => i.id)).toEqual(['b', 'a']);
    expect(w.hasMore).toBe(true);

    svc.list.mockReturnValueOnce(of(page([item({ id: 'c' })], 3)));
    w.loadMore();

    expect(svc.list).toHaveBeenLastCalledWith({ kinds: ['TODO'], scope: 'MINE', activeOnly: true }, 1, WORKSPACE_GROUPED_PAGE_SIZE);
    expect(w.items.map((i) => i.id)).toEqual(['b', 'c', 'a']);
    expect(w.hasMore).toBe(false);
  });

  it('shows an error when the list cannot be loaded', () => {
    svc.list.mockReturnValue(throwError(() => new Error('down')));

    w.ngOnInit();
    w.selectTab(WORKSPACE_TABS[1]);

    expect(w.loadError).toBe(true);
    expect(notify.error).toHaveBeenCalledWith('workspace.messages.loadFailed');
    expect(w.loading).toBe(false);
  });

  describe('adding and editing', () => {
    it('needs a title where the tab has one and text where it has none', () => {
      expect(w.canSubmit).toBe(false);
      w.form.title = '  ';
      expect(w.canSubmit).toBe(false);
      w.form.title = 'call';
      expect(w.canSubmit).toBe(true);

      w.selectTab(WORKSPACE_TABS[4]);
      w.form.title = 'ignored';
      expect(w.canSubmit).toBe(false);
      w.form.body = 'two reports failed';
      expect(w.canSubmit).toBe(true);
    });

    it('sends only the fields of the tab: a to-do carries priority, due date and reminder but no assignee', () => {
      svc.create.mockReturnValue(of(item()));
      w.selectTab(WORKSPACE_TABS[2]);
      w.form = { ...w.form, title: 'pay', body: 'b', assignee: { id: 'bob', name: 'Bob' }, priority: 'HIGH', due: '2026-10-25T10:00', remind: '2026-10-25T09:00' };

      w.submit();

      const draft = svc.create.mock.calls[0][0];
      expect(draft).toMatchObject({ kind: 'TODO', title: 'pay', body: 'b', assigneeId: null, assigneeName: null, priority: 'HIGH' });
      expect(draft.dueAt).toBe(new Date('2026-10-25T10:00').toISOString());
      expect(draft.remindAt).toBe(new Date('2026-10-25T09:00').toISOString());
      expect(notify.success).toHaveBeenCalledWith('workspace.messages.saved');
      expect(w.form.title).toBe('');
    });

    it('a task carries its assignee, a note neither priority nor due date, a handover note no title', () => {
      svc.create.mockReturnValue(of(item()));

      w.selectTab(WORKSPACE_TABS[3]);
      w.form.title = 't';
      w.form.assignee = { id: 'bob', name: 'Bob' };
      w.submit();
      expect(svc.create.mock.calls[0][0]).toMatchObject({ kind: 'TASK', assigneeId: 'bob', assigneeName: 'Bob' });

      w.selectTab(WORKSPACE_TABS[1]);
      w.form.title = 'n';
      w.submit();
      expect(svc.create.mock.calls[1][0]).toMatchObject({ kind: 'NOTE', priority: null, dueAt: null });

      w.selectTab(WORKSPACE_TABS[4]);
      w.form.body = 'handover';
      w.submit();
      expect(svc.create.mock.calls[2][0]).toMatchObject({ kind: 'HANDOVER', title: null, body: 'handover' });
    });

    it('editing fills the form and saving updates that item', () => {
      svc.update.mockReturnValue(of(item()));
      w.edit(item({ id: 'z', title: 'old', body: 'b', priority: 'LOW', dueAt: new Date(2026, 9, 25, 10, 0).toISOString() }));

      expect(w.editingId).toBe('z');
      expect(w.form).toMatchObject({ title: 'old', body: 'b', priority: 'LOW', due: '2026-10-25T10:00' });
      w.form.title = 'new';
      w.submit();

      expect(svc.update).toHaveBeenCalledWith('z', expect.objectContaining({ title: 'new' }));
      expect(w.editingId).toBeNull();
    });

    it('shows the server message when saving is refused, else the generic text, and does nothing when the form is empty', () => {
      w.submit();
      expect(svc.create).not.toHaveBeenCalled();

      w.form.title = 't';
      svc.create.mockReturnValueOnce(throwError(() => ({ error: { responseObject: [{ message: 'is required' }] } })));
      w.submit();
      expect(notify.error).toHaveBeenLastCalledWith('is required');

      svc.create.mockReturnValueOnce(throwError(() => ({})));
      w.submit();
      expect(notify.error).toHaveBeenLastCalledWith('workspace.messages.saveFailed');
      expect(w.saving).toBe(false);
    });
  });

  describe('ticking off and deleting', () => {
    it('ticks an open item off and re-opens a finished one, then reloads', () => {
      svc.setStatus.mockReturnValue(of(item()));
      w.active = WORKSPACE_TABS[2];

      w.toggleDone(item({ id: 'a' }));
      w.toggleDone(item({ id: 'b', status: 'DONE' }));

      expect(svc.setStatus).toHaveBeenNthCalledWith(1, 'a', 'DONE');
      expect(svc.setStatus).toHaveBeenNthCalledWith(2, 'b', 'OPEN');
      expect(svc.list).toHaveBeenCalledTimes(2);
    });

    it('reports a refused tick', () => {
      svc.setStatus.mockReturnValue(throwError(() => ({ error: { message: 'Only the owner or the assignee can change the status' } })));

      w.toggleDone(item());

      expect(notify.error).toHaveBeenCalledWith('Only the owner or the assignee can change the status');
    });

    it('deletes only after the confirmation, and leaves edit mode when the edited item goes', () => {
      svc.remove.mockReturnValue(of(undefined));
      w.editingId = 'a';

      dialog.confirm.mockReturnValueOnce(of(false));
      w.remove(item({ id: 'a' }));
      expect(svc.remove).not.toHaveBeenCalled();

      w.remove(item({ id: 'a' }));
      expect(svc.remove).toHaveBeenCalledWith('a');
      expect(notify.success).toHaveBeenCalledWith('workspace.messages.deleted');
      expect(w.editingId).toBeNull();
    });

    it('reports a refused delete', () => {
      svc.remove.mockReturnValue(throwError(() => ({})));

      w.remove(item());

      expect(notify.error).toHaveBeenCalledWith('workspace.messages.saveFailed');
    });
  });

  it('follows the signed-in user for ownership, ticking and due state', () => {
    expect(w.userId).toBe('alice');
    expect(w.isMine(item())).toBe(true);
    expect(w.isMine(item({ ownerId: 'bob' }))).toBe(false);
    expect(w.canTick(item({ ownerId: 'bob', kind: 'TASK', assigneeId: 'alice' }))).toBe(true);
    expect(w.canTick(item({ ownerId: 'bob', kind: 'TASK' }))).toBe(false);
    expect(w.due(item({ dueAt: '2000-01-01T00:00:00Z' }))).toBe('overdue');
    expect(w.trackItem(0, item({ id: 'k' }))).toBe('k');
    expect(create(null).userId).toBeNull();
  });

  describe('rendering', () => {
    it('shows the five tabs, the add form and the items with their actions', () => {
      svc.list.mockReturnValue(of(page([item({ id: 'a', title: 'call vendor', priority: 'HIGH' })])));
      w.active = WORKSPACE_TABS[2];

      fixture.detectChanges();

      expect(all('.ws-tabs .hq-tab__label').map((t) => t.textContent?.trim())).toEqual(WORKSPACE_TABS.map((t) => t.labelKey));
      expect(q('.ws-form')).not.toBeNull();
      expect(all('.ws-item')).toHaveLength(1);
      expect(q('.ws-item__title')?.textContent).toContain('call vendor');
      expect(q('.ws-check')).not.toBeNull();
      expect(all('.ws-icon-btn')).toHaveLength(2);
    });

    it('shows the empty message', () => {
      w.active = WORKSPACE_TABS[1];

      fixture.detectChanges();

      expect(q('hq-empty-state')?.textContent).toContain('workspace.empty');
    });

    it('shows an error message when the list fails', () => {
      svc.list.mockReturnValue(throwError(() => new Error('down')));
      w.active = WORKSPACE_TABS[1];

      fixture.detectChanges();

      expect(q('hq-error-state')).not.toBeNull();
    });

    it('shows nothing of the screen without the list privilege', () => {
      granted = new Set();
      w.active = WORKSPACE_TABS[1];

      fixture.detectChanges();

      expect(q('.ws-form')).toBeNull();
      expect(q('.ws-list')).toBeNull();
    });

    it('hides the add form without the create privilege and the row actions without update, status and delete', () => {
      granted = new Set([WORKSPACE_PRIVILEGES.list]);
      svc.list.mockReturnValue(of(page([item()])));
      w.active = WORKSPACE_TABS[2];

      fixture.detectChanges();

      expect(q('.ws-form')).toBeNull();
      expect(q('.ws-check')).toBeNull();
      expect(all('.ws-icon-btn')).toHaveLength(0);
      expect(all('.ws-item')).toHaveLength(1);
    });

    it("does not offer edit, delete or tick-off on a colleague's item, and marks it as a colleague's", () => {
      w = create('bob');
      w.active = WORKSPACE_TABS[3];
      svc.list.mockReturnValue(of(page([item({ kind: 'TASK', visibility: 'SHARED', ownerId: 'alice' })])));

      fixture.detectChanges();

      // Only the comments and history toggle is left, which everyone who can see the task may open.
      const buttons = all('.ws-icon-btn');
      expect(buttons).toHaveLength(1);
      expect(buttons[0].getAttribute('aria-label')).toBe('workspace.timeline.toggle');
      expect(q('.ws-check')).toBeNull();
      expect(q('.ws-item__meta')?.textContent).toContain('workspace.badge.colleague');
    });

    it('marks overdue items and shows the more button while there are more', () => {
      svc.list.mockReturnValue(of(page([item({ dueAt: '2000-01-01T00:00:00Z' })], 5)));
      w.active = WORKSPACE_TABS[2];

      fixture.detectChanges();

      expect(q('.ws-item__meta hq-status-badge .hq-badge--danger')?.textContent).toContain('workspace.due.overdue');
      expect(q('.ws-more button')).not.toBeNull();
    });
  });
});
