/** Constants for the Workflow screens (tasks, start process, instance viewer). */
import { TaskView } from './workflow.models';

/** The process definition bundled with Rider_WorkflowService; pre-filled on the Start form. */
/** Screen permission keys of the workflow screens (mapped in hq-shared-lib's HQ_PERMISSION_PRIVILEGES). */
export const WORKFLOW_TASKS_PERMISSION = 'view_workflow_tasks';
export const WORKFLOW_DESIGNER_PERMISSION = 'view_workflow_designer';

/** The tabs of the workflow screens; each shows only to someone who may open its screen. The Start process tab is hidden (its route still works). */
export const WORKFLOW_TABS: readonly { path: string; labelKey: string; icon: string; permission: string }[] = [
  { path: '/workflow/tasks', labelKey: 'workflow.tabs.tasks', icon: 'bi-list-check', permission: WORKFLOW_TASKS_PERMISSION },
  { path: '/workflow/definitions', labelKey: 'workflow.tabs.definitions', icon: 'bi-diagram-3', permission: WORKFLOW_DESIGNER_PERMISSION },
  { path: '/workflow/instances', labelKey: 'workflow.tabs.instance', icon: 'bi-search', permission: WORKFLOW_TASKS_PERMISSION },
  { path: '/workflow/reports', labelKey: 'workflow.tabs.reports', icon: 'bi-bar-chart-line', permission: WORKFLOW_DESIGNER_PERMISSION },
];

export const DEFAULT_PROCESS_KEY = 'generic-review';

/** Outcomes the bundled generic-review process branches on (variable name: `decision`). */
export const DECISION_VARIABLE = 'decision';
export const DECISIONS: readonly string[] = ['APPROVED', 'REJECTED'];

/** Translation key for a decision label: workflow.decisions.<DECISION>. */
export const DECISION_KEY_PREFIX = 'workflow.decisions.';

/** Process variables every instance carries; shown first and not editable when completing a task. */
export const CORE_VARIABLES: readonly string[] = ['subjectType', 'subjectId', 'callbackUrl'];

/** The task list views, in display order; labels are workflow.tasks.views.<VIEW>. */
export const TASK_VIEWS: readonly TaskView[] = ['ALL', 'MINE', 'UNASSIGNED', 'OVERDUE'];
export const DEFAULT_TASK_VIEW: TaskView = 'ALL';
export const TASK_VIEW_KEY_PREFIX = 'workflow.tasks.views.';

/**
 * How a step names who may act. One kind per step: a person, several people, roles, or groups.
 * Labels are workflow.definitions.who.mode.<MODE>.
 */
export type AssignMode = 'NONE' | 'PERSON' | 'USERS' | 'ROLES' | 'DEPARTMENTS';
export const ASSIGN_MODES: readonly AssignMode[] = ['NONE', 'PERSON', 'USERS', 'ROLES', 'DEPARTMENTS'];
/** A step offered to a department of the organization is written {@code dept:<code>} in candidateGroups. */
export const DEPARTMENT_PREFIX = 'dept:';
export const ASSIGN_MODE_KEY_PREFIX = 'workflow.definitions.who.mode.';

/**
 * The NMS channels a step can notify on (Rider_NMS NotificationChannel), in display order; labels are
 * workflow.definitions.notify.channels.<CHANNEL>. A step that names none uses the service's default.
 */
export type NotifyChannel = 'IN_APP' | 'FCM_PUSH' | 'EMAIL' | 'WHATSAPP' | 'SMS';
export const NOTIFY_CHANNELS: readonly NotifyChannel[] = ['IN_APP', 'FCM_PUSH', 'EMAIL', 'WHATSAPP', 'SMS'];
export const NOTIFY_CHANNEL_KEY_PREFIX = 'workflow.definitions.notify.channels.';
/** What Rider_WorkflowService uses for a step with no notifyChannels (workflow.notifications.default-channels). */
export const DEFAULT_NOTIFY_CHANNELS: readonly NotifyChannel[] = ['IN_APP', 'FCM_PUSH'];

/** Largest file that can be attached to a task (the document store's own limit). */
export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

/** Tasks per page on the Tasks screen. */
export const TASKS_PAGE_SIZE = 20;

/** Units a step's SLA (deadline) can be set in; labels are workflow.definitions.sla.units.<UNIT>. */
export type SlaUnit = 'MINUTES' | 'HOURS' | 'DAYS' | 'WEEKS';
export const SLA_UNITS: readonly SlaUnit[] = ['MINUTES', 'HOURS', 'DAYS', 'WEEKS'];
export const SLA_DEFAULT_UNIT: SlaUnit = 'HOURS';
export const SLA_UNIT_KEY_PREFIX = 'workflow.definitions.sla.units.';
/** Smallest and largest whole number allowed per unit (the service accepts 1 minute to 365 days). */
export const SLA_LIMITS: Record<SlaUnit, { min: number; max: number }> = {
  MINUTES: { min: 1, max: 1440 },
  HOURS: { min: 1, max: 720 },
  DAYS: { min: 1, max: 365 },
  WEEKS: { min: 1, max: 52 },
};
export const SLA_MINUTES_PER_UNIT: Record<SlaUnit, number> = { MINUTES: 1, HOURS: 60, DAYS: 1440, WEEKS: 10080 };

/** Sidebar-independent route of the three screens. */
export const WORKFLOW_ROUTE = '/workflow';

/** Roles that see the editor, check and deploy controls (the Workflow service enforces its own list). */
export const DEFINITION_EDITOR_ROLES: readonly string[] = ['SUPER_ADMIN'];

/** Process variable holding the id of the maker-checker change request a review task is about. */
export const CHANGE_REQUEST_VARIABLE = 'changeRequestId';

/** Subject type of an onboarding submission awaiting review (set by Rider_OnboardingService). */
export const ONBOARDING_SUBJECT_TYPE = 'ONBOARDING_SUBMISSION';
