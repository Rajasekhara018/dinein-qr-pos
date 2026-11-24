import { describe, expect, it } from 'vitest';

import { canEditItem, canTickItem, dueState, fromLocalInput, openFirst, toLocalInput } from './workspace.helpers';
import { WorkspaceItem } from './workspace.models';

const NOW = new Date(2026, 9, 24, 12, 0, 0);

const item = (over: Partial<WorkspaceItem> = {}): WorkspaceItem => ({
  id: 'i1', kind: 'TODO', title: 't', body: null, ownerId: 'alice', assigneeId: null, visibility: 'PRIVATE', status: 'OPEN',
  priority: 'NORMAL', dueAt: null, remindAt: null, refType: null, refId: null, createdAt: '2026-10-24T10:00:00Z',
  updatedAt: '2026-10-24T10:00:00Z', completedAt: null, ...over,
});

describe('workspace helpers', () => {
  describe('dueState', () => {
    it('has no state without a due date, for a finished item or for an unreadable date', () => {
      expect(dueState(item(), NOW)).toBe('none');
      expect(dueState(item({ dueAt: new Date(2026, 9, 1).toISOString(), status: 'DONE' }), NOW)).toBe('none');
      expect(dueState(item({ dueAt: 'not a date' }), NOW)).toBe('none');
    });

    it('is overdue once the time has passed, today for later today, upcoming after that', () => {
      expect(dueState(item({ dueAt: new Date(2026, 9, 24, 9, 0).toISOString() }), NOW)).toBe('overdue');
      expect(dueState(item({ dueAt: new Date(2026, 9, 24, 18, 0).toISOString() }), NOW)).toBe('today');
      expect(dueState(item({ dueAt: new Date(2026, 9, 25, 9, 0).toISOString() }), NOW)).toBe('upcoming');
    });
  });

  describe('who may do what', () => {
    it('only the owner edits', () => {
      expect(canEditItem(item(), 'alice')).toBe(true);
      expect(canEditItem(item(), 'bob')).toBe(false);
      expect(canEditItem(item(), null)).toBe(false);
      expect(canEditItem(item(), undefined)).toBe(false);
    });

    it('the owner and the assignee tick off a to-do or task, nobody else, and nothing else has a status', () => {
      const task = item({ kind: 'TASK', assigneeId: 'bob' });

      expect(canTickItem(task, 'alice')).toBe(true);
      expect(canTickItem(task, 'bob')).toBe(true);
      expect(canTickItem(task, 'carol')).toBe(false);
      expect(canTickItem(task, null)).toBe(false);
      expect(canTickItem(item({ kind: 'NOTE' }), 'alice')).toBe(false);
      expect(canTickItem(item({ kind: 'HANDOVER' }), 'alice')).toBe(false);
    });
  });

  it('lists open items before finished ones and keeps the order within each group', () => {
    const sorted = openFirst([
      item({ id: 'a', status: 'DONE' }), item({ id: 'b' }), item({ id: 'c', status: 'DONE' }), item({ id: 'd' }),
    ]);

    expect(sorted.map((i) => i.id)).toEqual(['b', 'd', 'a', 'c']);
  });

  describe('datetime-local conversion', () => {
    it('round-trips an instant through the local input value', () => {
      const iso = new Date(2026, 9, 24, 7, 5).toISOString();

      expect(toLocalInput(iso)).toBe('2026-10-24T07:05');
      expect(fromLocalInput('2026-10-24T07:05')).toBe(iso);
    });

    it('treats empty and invalid values as none', () => {
      expect(toLocalInput(null)).toBe('');
      expect(toLocalInput('')).toBe('');
      expect(toLocalInput('garbage')).toBe('');
      expect(fromLocalInput('')).toBeNull();
      expect(fromLocalInput(null)).toBeNull();
      expect(fromLocalInput('garbage')).toBeNull();
    });
  });
});
