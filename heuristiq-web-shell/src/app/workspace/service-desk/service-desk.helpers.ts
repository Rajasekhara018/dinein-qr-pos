import { WorkspaceItem, WorkspaceStatus } from '../workspace.models';
import {
  SLA_AT_RISK_MINUTES,
  SLA_AT_RISK_SHARE,
  SlaState,
  TICKET_ACTIVE_STATUSES,
  TICKET_KEY_PREFIX,
  TICKET_PRIORITY_RANK,
  TICKET_TRANSITIONS,
} from './service-desk.constants';

const MS_PER_MINUTE = 60_000;
const MINUTES_PER_HOUR = 60;
const HOURS_PER_DAY = 24;

/** "OPS-42" for ticket 42; '' for an item without a number, or for none. */
export function ticketKey(item: Pick<WorkspaceItem, 'ticketNumber'> | null | undefined): string {
  return item?.ticketNumber ? `${TICKET_KEY_PREFIX}-${item.ticketNumber}` : '';
}

/** True while the ticket still needs work. */
export function isActive(status: WorkspaceStatus): boolean {
  return TICKET_ACTIVE_STATUSES.includes(status);
}

/** The statuses a ticket can be moved to from where it is. */
export function nextStatuses(status: WorkspaceStatus): readonly WorkspaceStatus[] {
  return TICKET_TRANSITIONS[status] ?? [];
}

/**
 * How a ticket stands against its due time: still needing work, it is on track, at risk (less than a quarter of its time or an
 * hour left) or breached; finished, the target was met or missed; without a due time there is nothing to say.
 */
export function slaState(item: Pick<WorkspaceItem, 'status' | 'dueAt' | 'createdAt' | 'completedAt'>, now: number = Date.now()): SlaState {
  if (!item.dueAt) return 'none';
  const due = Date.parse(item.dueAt);
  if (Number.isNaN(due)) return 'none';
  if (!isActive(item.status)) {
    const finished = item.completedAt ? Date.parse(item.completedAt) : now;
    return finished <= due ? 'met' : 'missed';
  }
  if (now > due) return 'breached';
  const created = Date.parse(item.createdAt);
  const total = Number.isNaN(created) ? 0 : due - created;
  const left = due - now;
  if (left < SLA_AT_RISK_MINUTES * MS_PER_MINUTE || (total > 0 && left / total < SLA_AT_RISK_SHARE)) return 'atRisk';
  return 'onTrack';
}

/** A span as a short text and its translation key: { key: 'minutes'|'hours'|'days', value }. */
export function spanOf(ms: number): { unit: 'minutes' | 'hours' | 'days'; value: number } {
  const minutes = Math.max(1, Math.round(Math.abs(ms) / MS_PER_MINUTE));
  if (minutes < MINUTES_PER_HOUR) return { unit: 'minutes', value: minutes };
  const hours = Math.round(minutes / MINUTES_PER_HOUR);
  if (hours < HOURS_PER_DAY) return { unit: 'hours', value: hours };
  return { unit: 'days', value: Math.round(hours / HOURS_PER_DAY) };
}

/** A queue's order: higher priority first, then the soonest due, then the oldest. */
export function queueOrder(a: WorkspaceItem, b: WorkspaceItem): number {
  const rank = TICKET_PRIORITY_RANK[b.priority] - TICKET_PRIORITY_RANK[a.priority];
  if (rank !== 0) return rank;
  const dueA = a.dueAt ? Date.parse(a.dueAt) : Number.MAX_SAFE_INTEGER;
  const dueB = b.dueAt ? Date.parse(b.dueAt) : Number.MAX_SAFE_INTEGER;
  if (dueA !== dueB) return dueA - dueB;
  return Date.parse(a.createdAt) - Date.parse(b.createdAt);
}

/** Labels typed as "billing, refund  vip" into a clean list (lower case, no blanks or repeats). */
export function parseLabels(text: string): string[] {
  const labels = text.split(/[,\s]+/).map((label) => label.trim().toLowerCase()).filter((label) => label.length > 0);
  return [...new Set(labels)];
}
