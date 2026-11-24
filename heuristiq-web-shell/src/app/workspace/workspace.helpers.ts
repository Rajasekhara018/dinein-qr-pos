import { UamUser } from '../shared/models/uam.models';
import { ACTIVITY_SEPARATOR, DAYS_PER_WEEK, HANDOVER_TICKET_LABEL_PREFIX, MONDAY, PRIORITY_RANK, QUICK_DUE_HOURS, SHIFT_START_HOURS } from './workspace.constants';
import { DueGroup, DueState, QuickDue, WorkspaceItem, WorkspaceShift } from './workspace.models';

/** How soon an open item is due: a finished item or one without a due date has no state. */
export function dueState(item: Pick<WorkspaceItem, 'dueAt' | 'status'>, now: Date): DueState {
  if (item.status === 'DONE' || !item.dueAt) return 'none';
  const due = new Date(item.dueAt);
  if (Number.isNaN(due.getTime())) return 'none';
  if (due.getTime() < now.getTime()) return 'overdue';
  const sameDay = due.getFullYear() === now.getFullYear() && due.getMonth() === now.getMonth() && due.getDate() === now.getDate();
  return sameDay ? 'today' : 'upcoming';
}

/** The owner edits and deletes an item. */
export function canEditItem(item: Pick<WorkspaceItem, 'ownerId'>, userId: string | null | undefined): boolean {
  return !!userId && item.ownerId === userId;
}

/** The owner or the assignee ticks off a to-do or task. */
export function canTickItem(item: Pick<WorkspaceItem, 'kind' | 'ownerId' | 'assigneeId'>, userId: string | null | undefined): boolean {
  if (!userId || (item.kind !== 'TODO' && item.kind !== 'TASK')) return false;
  return item.ownerId === userId || item.assigneeId === userId;
}

/** Open items first, finished ones after, each group keeping the order it came in. */
export function openFirst<T extends Pick<WorkspaceItem, 'status'>>(items: readonly T[]): T[] {
  return [...items.filter((i) => i.status !== 'DONE'), ...items.filter((i) => i.status === 'DONE')];
}

/** The value of an `<input type="datetime-local">` (local time, no zone) for an ISO instant; empty for none or invalid. */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** The ISO instant of a datetime-local value (read as local time); null for empty or invalid. */
export function fromLocalInput(value: string | null | undefined): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** True when the item's work is finished (a done to-do or task, a resolved or closed ticket). */
export function isFinished(status: WorkspaceItem['status']): boolean {
  return status === 'DONE' || status === 'RESOLVED' || status === 'CLOSED';
}

/** The group of a grouped to-do or task list: done, overdue, due today, later this week, later, or without a due date. */
export function dueGroup(item: Pick<WorkspaceItem, 'dueAt' | 'status'>, now: Date): DueGroup {
  if (isFinished(item.status)) return 'done';
  if (!item.dueAt) return 'noDate';
  const due = new Date(item.dueAt);
  if (Number.isNaN(due.getTime())) return 'noDate';
  if (due.getTime() < now.getTime()) return 'overdue';
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const days = Math.floor((due.getTime() - startOfToday.getTime()) / 86_400_000);
  if (days === 0) return 'today';
  return days < DAYS_PER_WEEK ? 'thisWeek' : 'later';
}

/** Order inside a group: higher priority first, then the soonest due, then the newest. */
export function groupOrder(a: WorkspaceItem, b: WorkspaceItem): number {
  const rank = PRIORITY_RANK[b.priority] - PRIORITY_RANK[a.priority];
  if (rank !== 0) return rank;
  const dueA = a.dueAt ? Date.parse(a.dueAt) : Number.MAX_SAFE_INTEGER;
  const dueB = b.dueAt ? Date.parse(b.dueAt) : Number.MAX_SAFE_INTEGER;
  if (dueA !== dueB) return dueA - dueB;
  return Date.parse(b.createdAt) - Date.parse(a.createdAt);
}

/** The datetime-local value of a quick due time: today 18:00, tomorrow 09:00, or next Monday 09:00. */
export function quickDueValue(choice: QuickDue, now: Date): string {
  const date = new Date(now.getFullYear(), now.getMonth(), now.getDate(), QUICK_DUE_HOURS[choice], 0, 0, 0);
  if (choice === 'tomorrow') date.setDate(date.getDate() + 1);
  if (choice === 'nextWeek') {
    const ahead = (MONDAY - date.getDay() + DAYS_PER_WEEK) % DAYS_PER_WEEK || DAYS_PER_WEEK;
    date.setDate(date.getDate() + ahead);
  }
  return toLocalInput(date.toISOString());
}

/** A person's name as lists show it: display name, else full name, else user name. */
export function personName(user: Pick<UamUser, 'displayName' | 'fullName' | 'userName'>): string {
  return user.displayName?.trim() || user.fullName?.firstName?.trim() || user.userName;
}

/** The shift running at this time: morning from 06:00, evening from 14:00, night from 22:00 (and until 06:00). */
export function currentShift(now: Date): WorkspaceShift {
  const hour = now.getHours();
  if (hour >= SHIFT_START_HOURS.MORNING && hour < SHIFT_START_HOURS.EVENING) return 'MORNING';
  if (hour >= SHIFT_START_HOURS.EVENING && hour < SHIFT_START_HOURS.NIGHT) return 'EVENING';
  return 'NIGHT';
}

/** Pinned notes first, each group keeping the order it came in (newest first). */
export function pinnedFirst<T extends Pick<WorkspaceItem, 'pinned'>>(items: readonly T[]): T[] {
  return [...items.filter((i) => i.pinned), ...items.filter((i) => !i.pinned)];
}

/** The label a handover note keeps for a ticket it hands over ("ops-42"). */
export function ticketLabel(ticketNumber: number): string {
  return `${HANDOVER_TICKET_LABEL_PREFIX}${ticketNumber}`;
}

/** The ticket numbers among a handover note's labels; its other labels are plain tags. */
export function ticketNumbersOf(labels: readonly string[] | null | undefined): number[] {
  return (labels ?? [])
    .filter((label) => label.startsWith(HANDOVER_TICKET_LABEL_PREFIX))
    .map((label) => Number(label.slice(HANDOVER_TICKET_LABEL_PREFIX.length)))
    .filter((n) => Number.isInteger(n) && n > 0);
}

/** A note's or handover note's labels that are not ticket references. */
export function plainLabels(labels: readonly string[] | null | undefined): string[] {
  return (labels ?? []).filter((label) => !label.startsWith(HANDOVER_TICKET_LABEL_PREFIX));
}

/** A stored history line ("STATUS|OPEN|IN_PROGRESS") as its code and values. */
export function parseActivity(body: string | null | undefined): { code: string; values: string[] } {
  const [code, ...values] = (body ?? '').split(ACTIVITY_SEPARATOR);
  return { code: code || 'EDITED', values };
}
