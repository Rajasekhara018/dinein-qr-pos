import { describe, expect, it } from 'vitest';
import { DueState, WorkspaceItem } from '../workspace/workspace.models';
import { AttentionItem } from './today.models';
import { briefingOf, decisionItemsOf, timelineOf } from './today-home.helpers';

const label = (key: string): string => `t:${key}`;

const attention = (key: string, tone: 'pending' | 'bad', text = '3'): AttentionItem => ({
  sectionId: 'waiting', sectionTitleKey: 's.waiting', link: `/${key}`,
  metric: { key, icon: `bi-${key}`, labelKey: `m.${key}`, text, value: Number(text), tone, highlighted: true },
});

const item = (id: string, over: Partial<WorkspaceItem> = {}): WorkspaceItem => ({
  id, kind: 'TASK', title: `item ${id}`, body: null, ownerId: 'o', assigneeId: null, visibility: 'PRIVATE', status: 'OPEN', priority: 'HIGH',
  dueAt: null, remindAt: null, refType: null, refId: null, createdAt: '', updatedAt: '', completedAt: null, ...over,
});

describe('decisionItemsOf', () => {
  it('makes a row per waiting queue: its label, its card, the figure as the tag, the tone and where it leads', () => {
    const rows = decisionItemsOf([attention('mine', 'pending', '4'), attention('overdue', 'bad', '2')], label, 'Review');

    expect(rows).toEqual([
      { id: 'waiting.mine', icon: 'bi-mine', title: 't:m.mine', meta: 't:s.waiting', tag: '4', tagTone: 'pending', actionLabel: 'Review', link: '/mine' },
      { id: 'waiting.overdue', icon: 'bi-overdue', title: 't:m.overdue', meta: 't:s.waiting', tag: '2', tagTone: 'bad', actionLabel: 'Review', link: '/overdue' },
    ]);
  });

  it('makes no rows when nothing is waiting', () => {
    expect(decisionItemsOf([], label, 'Review')).toEqual([]);
  });
});

describe('briefingOf', () => {
  const text = { headline: 'Here is where things stand.', clear: 'All under control.', need: '3 need you.' };

  it('says everything is under control, with no notes, when nothing is waiting', () => {
    expect(briefingOf([], label, text)).toEqual({ headline: 'All under control.', emphasis: '', notes: [] });
  });

  it('puts how many need the user in the emphasised part and the first two as linked notes', () => {
    const briefing = briefingOf([attention('a', 'pending', '4'), attention('b', 'bad', '2'), attention('c', 'pending', '9')], label, text);

    expect(briefing.headline).toBe('Here is where things stand.');
    expect(briefing.emphasis).toBe('3 need you.');
    expect(briefing.notes).toEqual([
      { icon: 'bi-a', text: '4 t:m.a', link: '/a' },
      { icon: 'bi-b', text: '2 t:m.b', link: '/b' },
    ]);
  });
});

describe('timelineOf', () => {
  const due = (map: Record<string, DueState>) => (i: WorkspaceItem): DueState => map[i.id] ?? 'none';
  const when = (_i: WorkspaceItem, d: DueState): string => `when:${d}`;
  const kind = (k: string): string => `kind:${k}`;

  it('lists the overdue first, then today, then the rest by date, colouring the overdue and today entries', () => {
    const entries = timelineOf(
      [item('none'), item('soon', { dueAt: '2026-11-10T00:00:00Z' }), item('today', { dueAt: '2026-11-05T00:00:00Z' }), item('late', { dueAt: '2026-10-01T00:00:00Z' })],
      due({ late: 'overdue', today: 'today', soon: 'upcoming' }), when, kind,
    );

    expect(entries.map((e) => e.id)).toEqual(['late', 'today', 'soon', 'none']);
    expect(entries.map((e) => e.tone)).toEqual(['bad', 'pending', 'plain', 'plain']);
    expect(entries[0]).toMatchObject({ when: 'when:overdue', title: 'item late', meta: 'kind:TASK', link: '/workspace' });
  });

  it('makes no entries when there are no items', () => {
    expect(timelineOf([], due({}), when, kind)).toEqual([]);
  });
});
