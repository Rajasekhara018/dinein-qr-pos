import { HqBriefingNote, HqDecisionItem, HqTimelineEntry } from 'hq-shared-lib';
import { DueState, WorkspaceItem } from '../workspace/workspace.models';
import { AttentionItem } from './today.models';

/** What the briefing banner says: a headline, the part of it drawn heavier, and a couple of notes. */
export interface TodayBriefing {
  headline: string;
  emphasis: string;
  notes: HqBriefingNote[];
}

/** How many notes the briefing banner carries at most. */
export const TODAY_BRIEFING_NOTES = 2;

/** One row of the "needs your decision" list per waiting queue or failure, in card order. */
export function decisionItemsOf(items: readonly AttentionItem[], label: (key: string) => string, actionLabel: string): HqDecisionItem[] {
  return items.map((item) => ({
    id: `${item.sectionId}.${item.metric.key}`, icon: item.metric.icon, title: label(item.metric.labelKey), meta: label(item.sectionTitleKey),
    tag: item.metric.text ?? '', tagTone: item.metric.tone, actionLabel, link: item.link,
  }));
}

/** The banner: how many things need the user, with the first few as notes; "all under control" when nothing waits. */
export function briefingOf(items: readonly AttentionItem[], label: (key: string) => string, text: { headline: string; clear: string; need: string }): TodayBriefing {
  if (items.length === 0) {
    return { headline: text.clear, emphasis: '', notes: [] };
  }
  return {
    headline: text.headline,
    emphasis: text.need,
    notes: items.slice(0, TODAY_BRIEFING_NOTES).map((item) => ({
      icon: item.metric.icon, text: `${item.metric.text ?? ''} ${label(item.metric.labelKey)}`.trim(), link: item.link,
    })),
  };
}

/** The "Today" timeline: the user's high-priority items, the overdue and the soonest first, each with when it is due. */
export function timelineOf(
  items: readonly WorkspaceItem[], dueOf: (item: WorkspaceItem) => DueState, whenOf: (item: WorkspaceItem, due: DueState) => string,
  kindLabel: (kind: string) => string,
): HqTimelineEntry[] {
  const rank: Record<DueState, number> = { overdue: 0, today: 1, upcoming: 2, none: 3 };
  return [...items]
    .map((item) => ({ item, due: dueOf(item) }))
    .sort((a, b) => rank[a.due] - rank[b.due] || (a.item.dueAt ?? '').localeCompare(b.item.dueAt ?? ''))
    .map(({ item, due }) => ({
      id: item.id, when: whenOf(item, due), title: item.title ?? '', meta: kindLabel(item.kind), link: '/workspace',
      tone: due === 'overdue' ? 'bad' as const : due === 'today' ? 'pending' as const : 'plain' as const,
    }));
}
