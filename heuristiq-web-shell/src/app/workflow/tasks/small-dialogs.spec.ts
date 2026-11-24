import { describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { AddCommentDialog } from './add-comment-dialog/add-comment-dialog';
import { ReassignDialog } from './reassign-dialog/reassign-dialog';
import { WorkflowTask } from '../workflow.models';

const task = { taskId: 't1', processInstanceId: 'p', processKey: 'k', assignee: 'u-self' } as WorkflowTask;

describe('AddCommentDialog', () => {
  it('closes with the trimmed text and ignores blank text', () => {
    const ref = { close: vi.fn() };
    const d = new AddCommentDialog(ref as never, { task });
    d.text = '   ';
    d.submit();
    expect(ref.close).not.toHaveBeenCalled();
    d.text = '  hello ';
    d.submit();
    expect(ref.close).toHaveBeenCalledWith('hello');
  });

  it('closes with null on cancel', () => {
    const ref = { close: vi.fn() };
    new AddCommentDialog(ref as never, { task }).cancel();
    expect(ref.close).toHaveBeenCalledWith(null);
  });
});

describe('ReassignDialog', () => {
  const page = {
    content: [
      { id: 'u-self', userName: 'self' },
      { id: 'u1', userName: 'asha', displayName: 'Asha Rao' },
      { id: 'u2', userName: 'bob', email: 'bob@x.com' },
      { id: 'u3', userName: 'cy' },
    ],
  };

  function make(listUsers: unknown) {
    const ref = { close: vi.fn() };
    const uam = { listUsers };
    const cdr = { detectChanges: vi.fn() };
    const d = new ReassignDialog(ref as never, { task }, uam as never, cdr as never);
    d.ngOnInit();
    return { d, ref, uam, cdr };
  }

  it('lists users except the current assignee with readable labels', () => {
    const { d, uam } = make(vi.fn().mockReturnValue(of(page)));
    expect(uam.listUsers).toHaveBeenCalledWith(0, 200);
    expect(d.users.map((u) => u.label)).toEqual(['Asha Rao', 'bob (bob@x.com)', 'cy']);
    expect(d.users.find((u) => u.id === 'u-self')).toBeUndefined();
  });

  it('filters case-insensitively', () => {
    const { d } = make(vi.fn().mockReturnValue(of(page)));
    d.filter = ' ASHA ';
    expect(d.visible.map((u) => u.id)).toEqual(['u1']);
    d.filter = '';
    expect(d.visible).toHaveLength(3);
  });

  it('flags a load failure', () => {
    const { d } = make(vi.fn().mockReturnValue(throwError(() => new Error('x'))));
    expect(d.loadFailed).toBe(true);
  });

  it('submits only with a selection; cancel closes with null', () => {
    const { d, ref } = make(vi.fn().mockReturnValue(of(page)));
    d.submit();
    expect(ref.close).not.toHaveBeenCalled();
    d.selected = 'u1';
    d.submit();
    expect(ref.close).toHaveBeenCalledWith('u1');
    d.cancel();
    expect(ref.close).toHaveBeenLastCalledWith(null);
  });
});
