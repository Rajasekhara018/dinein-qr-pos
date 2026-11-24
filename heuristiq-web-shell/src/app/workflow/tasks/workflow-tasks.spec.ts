import { describe, expect, it, vi } from 'vitest';
import { EMPTY, of, throwError } from 'rxjs';
import { WorkflowTasks } from './workflow-tasks';
import { WorkflowTask } from '../workflow.models';
import { MAX_ATTACHMENT_BYTES, TASKS_PAGE_SIZE } from '../workflow.constants';

const t = (over: Partial<WorkflowTask> = {}): WorkflowTask =>
  ({ taskId: 't1', processInstanceId: 'p1', processKey: 'k', variables: {}, ...over });

function setup(opts: { user?: { id?: string; role?: string } | null; closed?: unknown } = {}) {
  const workflow = {
    tasks: vi.fn().mockReturnValue(of({ items: [t()], total: 1 })),
    claimTask: vi.fn().mockReturnValue(of(undefined)),
    releaseTask: vi.fn().mockReturnValue(of(undefined)),
    reassignTask: vi.fn().mockReturnValue(of(undefined)),
    completeTask: vi.fn().mockReturnValue(of(undefined)),
    addComment: vi.fn().mockReturnValue(of(undefined)),
    uploadAttachment: vi.fn().mockReturnValue(of(undefined)),
  };
  const dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => of(opts.closed) }) };
  const router = { navigate: vi.fn().mockResolvedValue(true) };
  const notify = { success: vi.fn(), error: vi.fn() };
  const auth = { currentUser: opts.user === undefined ? { id: 'me', role: 'STAFF' } : opts.user };
  const translate = { instant: vi.fn((k: string) => k), onLangChange: EMPTY };
  const cdr = { detectChanges: vi.fn() };
  const c = new WorkflowTasks(workflow as never, dialog as never, router as never, notify as never, auth as never, translate as never, cdr as never);
  return { c, workflow, dialog, router, notify, auth, cdr };
}

describe('WorkflowTasks', () => {
  it('loads the first page on init with trimmed filters', () => {
    const { c, workflow } = setup();
    c.processKey = ' pk ';
    c.q = ' x ';
    c.ngOnInit();
    expect(workflow.tasks).toHaveBeenCalledWith('pk', '', 'ALL', 0, TASKS_PAGE_SIZE, { q: 'x', step: '', createdFrom: '', createdTo: '' });
    expect(c.tasks).toHaveLength(1);
    expect(c.total).toBe(1);
    expect(c.isLoading).toBe(false);
    expect(c.hasError).toBe(false);
  });

  it('shows an error state and empties the list when loading fails', () => {
    const { c, workflow } = setup();
    workflow.tasks.mockReturnValue(throwError(() => new Error('x')));
    c.tasks = [t()];
    c.load();
    expect(c.hasError).toBe(true);
    expect(c.tasks).toEqual([]);
    expect(c.total).toBe(0);
  });

  it('steps back to the new last page when the current page has vanished', () => {
    const { c, workflow } = setup();
    c.page = 3;
    workflow.tasks
      .mockReturnValueOnce(of({ items: [], total: 41 }))
      .mockReturnValueOnce(of({ items: [t()], total: 41 }));
    c.load();
    expect(workflow.tasks).toHaveBeenCalledTimes(2);
    expect(c.page).toBe(2);
    expect(c.tasks).toHaveLength(1);
  });

  it('paginates within bounds and resets on filters', () => {
    const { c, workflow } = setup();
    c.total = 45;
    expect(c.totalPages).toBe(3);
    c.goToPage(-1);
    expect(workflow.tasks).not.toHaveBeenCalled();
    c.goToPage(1);
    expect(c.page).toBe(1);
    c.page = 2;
    c.goToPage(1);
    expect(c.page).toBe(2);
    c.applyFilters();
    expect(c.page).toBe(0);
    c.total = 0;
    expect(c.totalPages).toBe(1);
  });

  it('clearFilters resets every filter and the view', () => {
    const { c, workflow } = setup();
    Object.assign(c, { processKey: 'a', subjectType: 'b', q: 'c', step: 'd', createdFrom: 'e', createdTo: 'f', view: 'MINE', page: 2 });
    c.clearFilters();
    expect([c.processKey, c.subjectType, c.q, c.step, c.createdFrom, c.createdTo, c.view, c.page]).toEqual(['', '', '', '', '', '', 'ALL', 0]);
    expect(workflow.tasks).toHaveBeenCalled();
  });

  it('describes the subject of a task', () => {
    const { c } = setup();
    expect(c.subjectOf(t({ variables: { subjectType: 'DRIVER', subjectId: '7' } }))).toBe('DRIVER · 7');
    expect(c.subjectOf(t({ variables: { subjectType: 'DRIVER', subjectId: '' } }))).toBe('DRIVER');
    expect(c.subjectOf(t({ variables: undefined as never }))).toBe('—');
  });

  it('knows which tasks are mine and what can be released or reassigned', () => {
    const { c, auth } = setup();
    const mine = t({ assignee: 'me', candidateGroups: ['R'] });
    expect(c.isMine(mine)).toBe(true);
    expect(c.isMine(t({ assignee: 'x' }))).toBe(false);
    expect(c.isMine(t())).toBe(false);
    expect(c.canRelease(mine)).toBe(true);
    expect(c.canRelease(t({ assignee: 'me' }))).toBe(false);
    expect(c.canRelease(t({ assignee: 'me', candidateUsers: ['u'] }))).toBe(true);
    expect(c.canRelease(t({ assignee: 'x', candidateUsers: ['u'] }))).toBe(false);
    expect(c.canReassign(mine)).toBe(true);
    expect(c.canReassign(t({ assignee: 'x' }))).toBe(false);
    expect(c.canReassign(t())).toBe(false);
    auth.currentUser = { id: 'admin', role: 'SUPER_ADMIN' };
    expect(c.canReassign(t({ assignee: 'x' }))).toBe(true);
    (auth as { currentUser: unknown }).currentUser = null;
    expect(c.canReassign(t({ assignee: 'x' }))).toBe(false);
    expect(c.isMine(mine)).toBe(false);
  });

  it('lists offered users, roles and department codes separately', () => {
    const { c } = setup();
    const task = t({ candidateUsers: ['a', 'b'], candidateGroups: ['ROLE_X', 'dept:org1:SALES', 'dept:HR'] });
    expect(c.offeredUsers(task)).toBe('a, b');
    expect(c.offeredRoles(task)).toBe('ROLE_X');
    expect(c.offeredDepartments(task)).toBe('SALES, HR');
    expect(c.offeredUsers(t())).toBe('');
    expect(c.offeredRoles(t())).toBe('');
    expect(c.offeredDepartments(t())).toBe('');
  });

  it('claims a task, reloads and toasts; failure toasts the backend message or fallback', () => {
    const { c, workflow, notify } = setup();
    c.claim(t());
    expect(workflow.claimTask).toHaveBeenCalledWith('t1');
    expect(notify.success).toHaveBeenCalledWith('workflow.tasks.claimed');
    expect(workflow.tasks).toHaveBeenCalled();
    expect(c.claimingId).toBeNull();
    workflow.claimTask.mockReturnValue(throwError(() => ({ error: { errorMsg: 'taken' } })));
    c.claim(t());
    expect(notify.error).toHaveBeenCalledWith('taken');
    workflow.claimTask.mockReturnValue(throwError(() => ({})));
    c.claim(t());
    expect(notify.error).toHaveBeenCalledWith('workflow.tasks.claimFailed');
  });

  it('releases a task and reports failure', () => {
    const { c, workflow, notify } = setup();
    c.release(t());
    expect(workflow.releaseTask).toHaveBeenCalledWith('t1');
    expect(notify.success).toHaveBeenCalledWith('workflow.tasks.released');
    workflow.releaseTask.mockReturnValue(throwError(() => ({})));
    c.release(t());
    expect(notify.error).toHaveBeenCalledWith('workflow.tasks.releaseFailed');
  });

  it('reassigns only when the dialog returns a user', () => {
    const none = setup({ closed: null });
    none.c.openReassign(t());
    expect(none.workflow.reassignTask).not.toHaveBeenCalled();

    const s = setup({ closed: 'u9' });
    s.c.openReassign(t());
    expect(s.workflow.reassignTask).toHaveBeenCalledWith('t1', 'u9');
    expect(s.notify.success).toHaveBeenCalledWith('workflow.tasks.reassigned');
    s.workflow.reassignTask.mockReturnValue(throwError(() => ({ error: { message: 'denied' } })));
    s.c.openReassign(t());
    expect(s.notify.error).toHaveBeenCalledWith('denied');
    s.workflow.reassignTask.mockReturnValue(throwError(() => ({})));
    s.c.openReassign(t());
    expect(s.notify.error).toHaveBeenCalledWith('workflow.tasks.reassignFailed');
  });

  it('completes with the dialog result and reports failure', () => {
    const result = { variables: { decision: 'APPROVED' }, comment: 'fine' };
    const s = setup({ closed: result });
    s.c.openComplete(t());
    expect(s.workflow.completeTask).toHaveBeenCalledWith('t1', { decision: 'APPROVED' }, 'fine');
    expect(s.notify.success).toHaveBeenCalledWith('workflow.tasks.completed');
    expect(s.c.completingId).toBeNull();
    s.workflow.completeTask.mockReturnValue(throwError(() => ({})));
    s.c.openComplete(t());
    expect(s.notify.error).toHaveBeenCalledWith('workflow.tasks.completeFailed');

    const cancelled = setup({ closed: undefined });
    cancelled.c.openComplete(t());
    expect(cancelled.workflow.completeTask).not.toHaveBeenCalled();
  });

  it('adds a comment only when text was entered', () => {
    const none = setup({ closed: '' });
    none.c.openComment(t());
    expect(none.workflow.addComment).not.toHaveBeenCalled();
    const s = setup({ closed: 'note' });
    s.c.openComment(t());
    expect(s.workflow.addComment).toHaveBeenCalledWith('t1', 'note');
    expect(s.notify.success).toHaveBeenCalledWith('workflow.tasks.commentAdded');
    s.workflow.addComment.mockReturnValue(throwError(() => ({})));
    s.c.openComment(t());
    expect(s.notify.error).toHaveBeenCalledWith('workflow.tasks.commentFailed');
  });

  describe('attachments', () => {
    const picker = () => ({ choose: vi.fn() });

    it('opens the picker for the chosen task and uploads the picked file', () => {
      const { c, workflow, notify } = setup();
      const p = picker();
      c.pickAttachment(t(), p);
      expect(p.choose).toHaveBeenCalled();
      c.onAttachmentChosen([new File(['a'], 'a.txt')]);
      expect(workflow.uploadAttachment).toHaveBeenCalledWith('t1', expect.any(File));
      expect(notify.success).toHaveBeenCalledWith('workflow.tasks.attached');
    });

    it('does nothing when no file or no target task', () => {
      const { c, workflow } = setup();
      c.onAttachmentChosen([new File(['a'], 'a')]);
      c.pickAttachment(t(), picker());
      c.onAttachmentChosen([]);
      expect(workflow.uploadAttachment).not.toHaveBeenCalled();
    });

    it('forgets the task after one pick so a later file is not attached to it', () => {
      const { c, workflow } = setup();
      c.pickAttachment(t(), picker());
      c.onAttachmentChosen([new File(['a'], 'a')]);
      c.onAttachmentChosen([new File(['b'], 'b')]);
      expect(workflow.uploadAttachment).toHaveBeenCalledTimes(1);
    });

    it('rejects a file over the size limit without uploading', () => {
      const { c, workflow, notify } = setup();
      c.pickAttachment(t(), picker());
      const big = new File(['a'], 'big');
      Object.defineProperty(big, 'size', { value: MAX_ATTACHMENT_BYTES + 1 });
      c.onAttachmentChosen([big]);
      expect(notify.error).toHaveBeenCalledWith('workflow.tasks.attachTooLarge');
      expect(workflow.uploadAttachment).not.toHaveBeenCalled();
    });

    it('reports an upload failure', () => {
      const { c, workflow, notify } = setup();
      workflow.uploadAttachment.mockReturnValue(throwError(() => ({ error: { errorMsg: 'virus' } })));
      c.pickAttachment(t(), picker());
      c.onAttachmentChosen([new File(['a'], 'a')]);
      expect(notify.error).toHaveBeenCalledWith('virus');
    });
  });

  it('navigates to the instance and tracks by task id', () => {
    const { c, router } = setup();
    c.viewInstance(t({ processInstanceId: 'p7' }));
    expect(router.navigate).toHaveBeenCalledWith(['/workflow', 'instances', 'p7']);
    expect(c.trackByTask(0, t({ taskId: 'zz' }))).toBe('zz');
  });
});
