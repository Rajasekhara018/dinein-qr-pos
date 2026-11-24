import { HqStatTone, HqStatusTone } from 'hq-shared-lib';
import { WorkspaceCategory, WorkspaceListFilter, WorkspacePriority, WorkspaceStatus } from '../workspace.models';

/** How a ticket's number is shown: OPS-42 (a display prefix, not stored). */
export const TICKET_KEY_PREFIX = 'OPS';

/** Translation namespace of the service desk. */
export const DESK_I18N = 'workspace.desk';

/** A board shows at most this many tickets of a queue (the API's page cap). */
export const DESK_BOARD_SIZE = 50;
export const DESK_PAGE_SIZE = 20;
/** Waiting this long after typing before the search runs. */
export const DESK_SEARCH_DEBOUNCE_MS = 300;
/** A ticket is "at risk" when less than this share of its time is left, or less than this many minutes. */
export const SLA_AT_RISK_SHARE = 0.25;
export const SLA_AT_RISK_MINUTES = 60;

/** The statuses of a ticket, in the order work moves through them. */
export const TICKET_STATUSES: readonly WorkspaceStatus[] = ['OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED'];
/** The statuses that still need work. */
export const TICKET_ACTIVE_STATUSES: readonly WorkspaceStatus[] = ['OPEN', 'IN_PROGRESS', 'WAITING'];
/** The board's columns (closed tickets leave the board). */
export const BOARD_COLUMNS: readonly WorkspaceStatus[] = ['OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED'];

/** Where a ticket can go from each status (the buttons of the ticket drawer). */
export const TICKET_TRANSITIONS: Readonly<Record<string, readonly WorkspaceStatus[]>> = {
  OPEN: ['IN_PROGRESS', 'WAITING', 'RESOLVED'],
  IN_PROGRESS: ['WAITING', 'RESOLVED'],
  WAITING: ['IN_PROGRESS', 'RESOLVED'],
  RESOLVED: ['CLOSED', 'OPEN'],
  CLOSED: ['OPEN'],
};

export const TICKET_STATUS_LABEL_KEYS: Readonly<Record<string, string>> = {
  OPEN: `${DESK_I18N}.status.OPEN`,
  IN_PROGRESS: `${DESK_I18N}.status.IN_PROGRESS`,
  WAITING: `${DESK_I18N}.status.WAITING`,
  RESOLVED: `${DESK_I18N}.status.RESOLVED`,
  CLOSED: `${DESK_I18N}.status.CLOSED`,
};

/** The verb on the button that moves a ticket to each status. */
export const TICKET_ACTION_LABEL_KEYS: Readonly<Record<string, string>> = {
  OPEN: `${DESK_I18N}.actions.reopen`,
  IN_PROGRESS: `${DESK_I18N}.actions.start`,
  WAITING: `${DESK_I18N}.actions.wait`,
  RESOLVED: `${DESK_I18N}.actions.resolve`,
  CLOSED: `${DESK_I18N}.actions.close`,
};

export const TICKET_STATUS_TONES: Readonly<Record<string, HqStatusTone>> = {
  OPEN: 'info',
  IN_PROGRESS: 'warning',
  WAITING: 'neutral',
  RESOLVED: 'success',
  CLOSED: 'neutral',
};

export const TICKET_PRIORITY_TONES: Readonly<Record<WorkspacePriority, HqStatusTone>> = {
  URGENT: 'danger',
  HIGH: 'warning',
  NORMAL: 'info',
  LOW: 'neutral',
};

/** Higher first when a queue is sorted. */
export const TICKET_PRIORITY_RANK: Readonly<Record<WorkspacePriority, number>> = { URGENT: 3, HIGH: 2, NORMAL: 1, LOW: 0 };

export const TICKET_CATEGORIES: readonly WorkspaceCategory[] = ['INCIDENT', 'REQUEST', 'PROBLEM', 'CHANGE', 'QUESTION'];
export const TICKET_CATEGORY_ICONS: Readonly<Record<WorkspaceCategory, string>> = {
  INCIDENT: 'bi-exclamation-octagon',
  REQUEST: 'bi-inbox',
  PROBLEM: 'bi-bug',
  CHANGE: 'bi-arrow-repeat',
  QUESTION: 'bi-question-circle',
};

/** The teams that work tickets (codes sent to the API; labels are translated, an unknown code is shown as it is). */
export const TICKET_QUEUES: readonly string[] = ['OPERATIONS', 'SUPPORT', 'FINANCE', 'ONBOARDING', 'FRANCHISE', 'TECH'];

/** The kinds of record a ticket can be about (codes; an unknown one is shown as it is). */
export const TICKET_RECORD_TYPES: readonly string[] = ['FRANCHISE', 'USER', 'ONBOARDED_USER', 'ORDER', 'PAYMENT', 'RIDE', 'DRIVER'];

/** The desk's queues: which tickets each one lists. */
export type DeskView = 'mine' | 'unassigned' | 'team' | 'raised' | 'done';

export interface DeskViewDef {
  key: DeskView;
  icon: string;
  filter: WorkspaceListFilter;
}

export const DESK_VIEWS: readonly DeskViewDef[] = [
  { key: 'mine', icon: 'bi-person-check', filter: { kinds: ['TICKET'], scope: 'ASSIGNED', activeOnly: true } },
  { key: 'unassigned', icon: 'bi-inbox', filter: { kinds: ['TICKET'], scope: 'VISIBLE', activeOnly: true, unassigned: true } },
  { key: 'team', icon: 'bi-people', filter: { kinds: ['TICKET'], scope: 'VISIBLE', activeOnly: true } },
  { key: 'raised', icon: 'bi-send', filter: { kinds: ['TICKET'], scope: 'MINE' } },
  { key: 'done', icon: 'bi-check2-all', filter: { kinds: ['TICKET'], scope: 'VISIBLE', status: 'RESOLVED' } },
];

/** The headline cards above the queues: which summary counter and how it looks. */
export const DESK_STATS: ReadonlyArray<{ key: 'myTickets' | 'unassignedTickets' | 'overdueTickets' | 'urgentTickets'; icon: string; tone: HqStatTone }> = [
  { key: 'myTickets', icon: 'bi-person-check', tone: 'blue' },
  { key: 'unassignedTickets', icon: 'bi-inbox', tone: 'purple' },
  { key: 'overdueTickets', icon: 'bi-alarm', tone: 'orange' },
  { key: 'urgentTickets', icon: 'bi-lightning-charge', tone: 'green' },
];

/** How a ticket stands against its due time. */
export type SlaState = 'none' | 'onTrack' | 'atRisk' | 'breached' | 'met' | 'missed';

export const SLA_TONES: Readonly<Record<SlaState, HqStatusTone>> = {
  none: 'neutral',
  onTrack: 'success',
  atRisk: 'warning',
  breached: 'danger',
  met: 'success',
  missed: 'danger',
};
