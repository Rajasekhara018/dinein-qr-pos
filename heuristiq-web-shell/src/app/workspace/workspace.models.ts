/** What a workspace item is (the API's enum, never translated). */
export type WorkspaceKind = 'NOTE' | 'TODO' | 'TASK' | 'RECORD_NOTE' | 'HANDOVER' | 'TICKET' | 'ACTIVITY';

/** To-dos and tasks are OPEN or DONE; service-desk tickets move OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED. */
export type WorkspaceStatus = 'OPEN' | 'DONE' | 'IN_PROGRESS' | 'WAITING' | 'RESOLVED' | 'CLOSED';

export type WorkspacePriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

/** What kind of work a ticket is (the API's enum, never translated). */
export type WorkspaceCategory = 'INCIDENT' | 'REQUEST' | 'PROBLEM' | 'CHANGE' | 'QUESTION';

export type WorkspaceVisibility = 'PRIVATE' | 'SHARED';

/** Which of the items the caller may see a list narrows to. */
export type WorkspaceScope = 'VISIBLE' | 'MINE' | 'ASSIGNED' | 'INVOLVED' | 'SHARED';

/** One item as the API returns it. */
export interface WorkspaceItem {
  id: string;
  kind: WorkspaceKind;
  title: string | null;
  body: string | null;
  ownerId: string;
  assigneeId: string | null;
  visibility: WorkspaceVisibility;
  status: WorkspaceStatus;
  priority: WorkspacePriority;
  dueAt: string | null;
  remindAt: string | null;
  refType: string | null;
  refId: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  /** A ticket's number in the organization (shown as OPS-42); null for other kinds. */
  ticketNumber?: number | null;
  category?: WorkspaceCategory | null;
  /** The team that works a ticket, as a code (OPERATIONS, SUPPORT ...). */
  queue?: string | null;
  labels?: string[];
  /** What was done to resolve a ticket. */
  resolution?: string | null;
  /** How the assignee is shown (their name when they were assigned). */
  assigneeName?: string | null;
  /** A note kept at the top of its owner's notes. */
  pinned?: boolean;
  /** The shift a handover note closes. */
  shift?: WorkspaceShift | null;
  /** Who of the next shift has read a handover note, oldest first. */
  acknowledgements?: WorkspaceAcknowledgement[];
  /** How the writer is shown (their name when they wrote it). */
  ownerName?: string | null;
}

/** What the screen sends to create or edit an item (the kind is ignored by an edit). */
export interface WorkspaceDraft {
  kind?: WorkspaceKind;
  title?: string | null;
  body?: string | null;
  assigneeId?: string | null;
  priority?: WorkspacePriority | null;
  dueAt?: string | null;
  remindAt?: string | null;
  refType?: string | null;
  refId?: string | null;
  category?: WorkspaceCategory | null;
  queue?: string | null;
  labels?: string[];
  resolution?: string | null;
  assigneeName?: string | null;
  pinned?: boolean;
  shift?: WorkspaceShift | null;
  /** How the signed-in user is shown on what they write (display only). */
  actorName?: string | null;
}

/** One page of items. */
export interface WorkspacePage {
  content: WorkspaceItem[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/** The counters of the Today page. */
export interface WorkspaceSummary {
  openTodos: number;
  openAssigned: number;
  overdue: number;
  remindersDue: number;
  /** Tickets assigned to the user that still need work. */
  myTickets?: number;
  unassignedTickets?: number;
  overdueTickets?: number;
  urgentTickets?: number;
}

/** What a list request narrows by; every field is optional. */
export interface WorkspaceListFilter {
  kinds?: readonly WorkspaceKind[];
  status?: WorkspaceStatus;
  priority?: WorkspacePriority;
  scope?: WorkspaceScope;
  refType?: string;
  refId?: string;
  /** One ticket queue. */
  queue?: string;
  /** Only items nobody is assigned to. */
  unassigned?: boolean;
  /** Only items that still need work (OPEN, IN_PROGRESS or WAITING). */
  activeOnly?: boolean;
  /** Text the title contains. */
  q?: string;
}

/** How soon an open item is due. */
export type DueState = 'none' | 'overdue' | 'today' | 'upcoming';

/** A colleague chosen to work on a task or ticket. */
export interface WorkspacePerson {
  id: string;
  name: string;
}

/** Where an item with a status sits in a grouped list. */
export type DueGroup = 'overdue' | 'today' | 'thisWeek' | 'later' | 'noDate' | 'done';

/** A quick due time offered under the form: today 18:00, tomorrow 09:00, next Monday 09:00. */
export type QuickDue = 'today' | 'tomorrow' | 'nextWeek';

/** Whose team tasks a list shows. */
export type TaskWho = 'all' | 'assignedToMe' | 'createdByMe' | 'unassigned';

/** The shift a handover note closes (the API's enum, never translated). */
export type WorkspaceShift = 'MORNING' | 'EVENING' | 'NIGHT';

/** One read receipt of a handover note. */
export interface WorkspaceAcknowledgement {
  userId: string;
  userName: string | null;
  acknowledgedAt: string;
}

/** One entry of a task's or ticket's timeline: a comment, or a line of its history. */
export interface TimelineEntry {
  id: string;
  kind: 'comment' | 'activity';
  /** Who wrote it ('' when unknown). */
  author: string;
  mine: boolean;
  at: string;
  /** A comment's text. */
  text: string;
  /** A history line's translation key and values. */
  key: string;
  params: Record<string, string>;
}
