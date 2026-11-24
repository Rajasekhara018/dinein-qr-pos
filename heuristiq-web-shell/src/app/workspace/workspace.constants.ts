import { DueGroup, QuickDue, TaskWho, WorkspaceKind, WorkspacePriority, WorkspaceScope, WorkspaceShift } from './workspace.models';

/** Stable screen key for the audit trail (never rename or translate once released). */
export const WORKSPACE_SCREEN_KEY = 'workspace.board';

/** Screen permission key (mapped to privilege codes in hq-shared-lib's hq-privilege-map.ts). */
export const WORKSPACE_PERMISSION = 'view_workspace';

/** Route of the screen (shown in the Main group of the sidebar). */
export const WORKSPACE_ROUTE = '/workspace';

/** Privilege codes (exact names from privilege.json). */
export const WORKSPACE_PRIVILEGES = {
  list: 'WORKSPACE_ITEM_LIST',
  read: 'WORKSPACE_ITEM_READ',
  create: 'WORKSPACE_ITEM_CREATE',
  update: 'WORKSPACE_ITEM_UPDATE',
  status: 'WORKSPACE_ITEM_STATUS_UPDATE',
  remove: 'WORKSPACE_ITEM_DELETE',
  summary: 'WORKSPACE_SUMMARY_READ',
} as const;

/** Gateway paths (below environment.apiUrl) of the workspace API in Rider_ConfigService. */
export const WORKSPACE_ITEMS_PATH = '/v1/config/workspace/items';
export const WORKSPACE_SUMMARY_PATH = '/v1/config/workspace/summary';

/** The API caps a page at 50 items. */
export const WORKSPACE_PAGE_SIZE = 20;
export const WORKSPACE_MAX_PAGE_SIZE = 50;

/** Limits the API enforces (the form stops the user before the server does). */
export const WORKSPACE_MAX_TITLE = 200;
export const WORKSPACE_MAX_BODY = 4000;

/** One tab of the screen: what it lists and which fields its form offers. */
export interface WorkspaceTab {
  key: 'desk' | 'notes' | 'todos' | 'tasks' | 'handover';
  /** i18n key of the tab's name. */
  labelKey: string;
  icon: string;
  kind: WorkspaceKind;
  /** Which items the tab lists. */
  scope: WorkspaceScope;
  titled: boolean;
  due: boolean;
  priority: boolean;
  assignee: boolean;
  reminder: boolean;
  /** Items can be ticked off. */
  status: boolean;
}

export const WORKSPACE_TABS: readonly WorkspaceTab[] = [
  // The service desk has its own screen inside the tab (queues, board, ticket drawer); the flags below are not used by it.
  { key: 'desk', labelKey: 'workspace.tabs.desk', icon: 'bi-kanban', kind: 'TICKET', scope: 'VISIBLE', titled: true, due: true, priority: true, assignee: true, reminder: false, status: true },
  { key: 'notes', labelKey: 'workspace.tabs.notes', icon: 'bi-sticky', kind: 'NOTE', scope: 'MINE', titled: true, due: false, priority: false, assignee: false, reminder: true, status: false },
  { key: 'todos', labelKey: 'workspace.tabs.todos', icon: 'bi-check2-square', kind: 'TODO', scope: 'MINE', titled: true, due: true, priority: true, assignee: false, reminder: true, status: true },
  { key: 'tasks', labelKey: 'workspace.tabs.tasks', icon: 'bi-people', kind: 'TASK', scope: 'VISIBLE', titled: true, due: true, priority: true, assignee: true, reminder: true, status: true },
  { key: 'handover', labelKey: 'workspace.tabs.handover', icon: 'bi-arrow-left-right', kind: 'HANDOVER', scope: 'VISIBLE', titled: false, due: false, priority: false, assignee: false, reminder: false, status: false },
];

export const WORKSPACE_PRIORITIES: readonly WorkspacePriority[] = ['URGENT', 'HIGH', 'NORMAL', 'LOW'];
/** Tickets also have URGENT, handled first. */
export const TICKET_PRIORITIES: readonly WorkspacePriority[] = ['URGENT', 'HIGH', 'NORMAL', 'LOW'];

/** i18n key of a priority's label (the codes themselves are not translated). */
export const WORKSPACE_PRIORITY_LABEL_KEYS: Readonly<Record<WorkspacePriority, string>> = {
  LOW: 'workspace.priority.low',
  NORMAL: 'workspace.priority.normal',
  HIGH: 'workspace.priority.high',
  URGENT: 'workspace.priority.urgent',
};

/** i18n keys of the toasts and dialogs. */
export const WORKSPACE_MESSAGE_KEYS = {
  saved: 'workspace.messages.saved',
  deleted: 'workspace.messages.deleted',
  loadFailed: 'workspace.messages.loadFailed',
  saveFailed: 'workspace.messages.saveFailed',
  deleteTitle: 'workspace.delete.title',
  deleteMessage: 'workspace.delete.message',
  deleteConfirm: 'workspace.delete.confirm',
} as const;

/** The record a note is attached to (record notes of a screen use their own type, for example ONBOARDED_USER). */
export const RECORD_TYPE_ONBOARDED_USER = 'ONBOARDED_USER';

/** The groups of a to-do or task list, in the order they are shown. */
export const DUE_GROUPS: readonly DueGroup[] = ['overdue', 'today', 'thisWeek', 'later', 'noDate', 'done'];

/** The quick due times under the form, and the hour each one is set to (local time). */
export const QUICK_DUES: readonly QuickDue[] = ['today', 'tomorrow', 'nextWeek'];
export const QUICK_DUE_HOURS: Readonly<Record<QuickDue, number>> = { today: 18, tomorrow: 9, nextWeek: 9 };
/** Days in a week, and Monday's day number (Date#getDay), for "next week". */
export const DAYS_PER_WEEK = 7;
export const MONDAY = 1;

/** Whose team tasks a list shows, and how each choice narrows the request. */
export const TASK_WHO: readonly TaskWho[] = ['all', 'assignedToMe', 'createdByMe', 'unassigned'];
export const TASK_WHO_FILTERS: Readonly<Record<TaskWho, { scope: WorkspaceScope; unassigned?: boolean }>> = {
  all: { scope: 'VISIBLE' },
  assignedToMe: { scope: 'ASSIGNED' },
  createdByMe: { scope: 'MINE' },
  unassigned: { scope: 'VISIBLE', unassigned: true },
};

/** Higher first when a group is sorted. */
export const PRIORITY_RANK: Readonly<Record<WorkspacePriority, number>> = { URGENT: 3, HIGH: 2, NORMAL: 1, LOW: 0 };

/** A list loads this many items at once when it is grouped (the API's page cap). */
export const WORKSPACE_GROUPED_PAGE_SIZE = 50;

/** Waiting this long after typing before the search runs. */
export const WORKSPACE_SEARCH_DEBOUNCE_MS = 300;

/** The search index fields of a user: tenant, sort, and the ones the people picker searches (user name, email, full name). */
export const PEOPLE_TENANT_FIELD = 'text5';
export const PEOPLE_SORT_FIELD = 'text1';
export const PEOPLE_SEARCH_FIELDS: readonly string[] = ['text1', 'text2', 'text7'];

/** The shifts of a handover note, and the hour each one starts (local time): morning 06:00, evening 14:00, night 22:00. */
export const WORKSPACE_SHIFTS: readonly WorkspaceShift[] = ['MORNING', 'EVENING', 'NIGHT'];
export const SHIFT_START_HOURS: Readonly<Record<WorkspaceShift, number>> = { MORNING: 6, EVENING: 14, NIGHT: 22 };

/** How a ticket a handover note hands over is kept among its labels: "ops-42". */
export const HANDOVER_TICKET_LABEL_PREFIX = 'ops-';
/** The open tickets offered to a handover note: at most this many, urgent and overdue first. */
export const HANDOVER_TICKET_LIMIT = 50;

/** The record type of the comments and history of a team task or ticket (the record id is the item's id). */
export const WORKSPACE_ITEM_REF_TYPE = 'WORKSPACE_ITEM';
/** Between the code and the values of a stored history line ("STATUS|OPEN|IN_PROGRESS"). */
export const ACTIVITY_SEPARATOR = '|';
/** The longest comment. */
export const WORKSPACE_MAX_COMMENT = 4000;
