/** Narrowing of the task list: part of a reference or request id, part of a step name, and when the task was created. */
export interface TaskSearch {
  q?: string;
  step?: string;
  /** yyyy-MM-dd (India time) */
  createdFrom?: string;
  createdTo?: string;
}

/** Which tasks the list returns: everything, the caller's own, or those nobody has taken. */
export type TaskView = 'ALL' | 'MINE' | 'UNASSIGNED' | 'OVERDUE';

/** Shapes returned by Rider_WorkflowService (the Flowable wrapper), see WorkflowController. */

export interface WorkflowTask {
  taskId: string;
  processInstanceId: string;
  processKey: string;
  /** The step's name in the process diagram, e.g. "Verify documents". */
  taskName?: string;
  taskKey?: string;
  createdAt?: string;
  /** UAM user id the task is assigned to; absent while it is unassigned. */
  assignee?: string | null;
  /** UAM user ids the task is offered to. */
  candidateUsers?: string[];
  /** UAM role codes whose holders the task is offered to. */
  candidateGroups?: string[];
  /** The step's SLA deadline; absent when the step has none. */
  dueDate?: string | null;
  /** True when the deadline has passed and the task is still open. */
  overdue?: boolean;
  /** Process variables; subjectType / subjectId / callbackUrl are always present. */
  variables: Record<string, unknown>;
}

export interface WorkflowInstance {
  instanceId: string;
  /** True while the instance is still running; false once it has ended (variables come from history). */
  active: boolean;
  variables: Record<string, unknown>;
}

export interface StartProcessRequest {
  subjectType: string;
  subjectId: string;
  /** Where the engine POSTs the outcome when the instance ends (best effort). */
  callbackUrl: string;
  variables?: Record<string, unknown>;
}

/** One version of a deployed process definition. */
export interface ProcessDefinitionSummary {
  key: string;
  name?: string;
  version: number;
  deploymentId: string;
  deployedAt?: string;
  suspended: boolean;
  resourceName?: string;
  /** UAM user id of who deployed this version from the console; absent for a version bundled with the service. */
  deployedBy?: string | null;
  /** What changed and why, as written when it was deployed. */
  note?: string | null;
  /** UAM user id of who approved the change, when the flow needs a second person. */
  approvedBy?: string | null;
}

export interface ProcessDefinitionDetail {
  definition: ProcessDefinitionSummary;
  xml: string;
  /** Every deployed version of the key, newest first. */
  versions: ProcessDefinitionSummary[];
}

/** Result of checking BPMN XML without deploying it. */
export interface DefinitionValidation {
  valid: boolean;
  key?: string;
  name?: string;
  errors: string[];
}

/** A process as nodes and edges, for drawing. */
export interface BpmnGraph {
  nodes: { id: string; name: string; type: string }[];
  edges: { id: string; source: string; target: string; name?: string; condition?: string }[];
}

/** One line of a request's history (see Rider_WorkflowService TimelineEntry). */
export type TimelineEntryType = 'STARTED' | 'TASK_CREATED' | 'CLAIMED' | 'RELEASED' | 'RETURNED' | 'ATTACHMENT' | 'REASSIGNED' | 'COMMENT' | 'COMPLETED' | 'ENDED';

export interface TimelineEntry {
  at: string;
  type: TimelineEntryType;
  taskId?: string | null;
  taskName?: string | null;
  /** UAM user id of who did it (for TASK_CREATED: who the step is assigned to). */
  actor?: string | null;
  decision?: string | null;
  /** The comment or reason; for REASSIGNED, the new assignee's user id. */
  text?: string | null;
  dueDate?: string | null;
  slaBreached?: boolean | null;
  durationMillis?: number | null;
  /** The document id of an ATTACHMENT entry (its text is the file name). */
  attachmentId?: string | null;
}

/** The end-to-end history of one process instance. */
export interface InstanceTimeline {
  instanceId: string;
  processKey?: string;
  processName?: string;
  active: boolean;
  startedAt?: string;
  endedAt?: string | null;
  subjectType?: string | null;
  subjectId?: string | null;
  /** The short reference the request was started with (e.g. DRV-2026-000042). */
  reference?: string | null;
  /** The final decision once the instance has ended. */
  outcome?: string | null;
  entries: TimelineEntry[];
}

/** How the approval workflows are doing over a period (see Rider_WorkflowService WorkflowReport). */
export interface WorkflowReport {
  from: string;
  to: string;
  /** More finished tasks than one report reads: the figures cover the newest ones. */
  truncated: boolean;
  totals: {
    started: number; finished: number; approved: number; rejected: number; returned: number;
    openTasks: number; overdueOpenTasks: number;
  };
  steps: {
    processKey: string; taskKey: string; taskName: string; completed: number;
    avgDurationMillis: number; maxDurationMillis: number; withSla: number; breached: number;
  }[];
  reviewers: {
    userId: string | null; completed: number; approved: number; rejected: number;
    avgDurationMillis: number; breached: number;
  }[];
  open: {
    processKey: string; taskKey: string; taskName: string; open: number; overdue: number; oldestAgeMillis: number;
  }[];
}

/** How changes to a flow are made: whether they need a second person's approval and a change note. */
export interface DefinitionSettings {
  requireApproval: boolean;
  requireNote: boolean;
}

/** Result of moving (or checking the move of) running requests to the newest version of a flow. */
export interface MigrationResult {
  key: string;
  latestVersion: number;
  dryRun: boolean;
  /** Running requests on an older version. */
  candidates: number;
  /** Of those, the ones that can move (dry run) or did move. */
  movable: number;
  problems: { instanceId: string; fromVersion: number; reasons: string[] }[];
}

export type ProposalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

/** A change to a flow waiting for (or past) a second person's decision. */
export interface DefinitionProposal {
  id: string;
  processKey: string;
  processName?: string | null;
  /** The proposed BPMN; only on a single proposal, not in lists. */
  xml?: string | null;
  note?: string | null;
  proposedBy: string;
  proposedAt: string;
  status: ProposalStatus;
  decidedBy?: string | null;
  decidedAt?: string | null;
  decisionNote?: string | null;
  deployedVersion?: number | null;
}

/** Rider_WorkflowService's envelope. */
export interface WorkflowResponse<T> {
  status: boolean;
  object?: T;
  errorMsg?: string;
}

/** One changed field of a proposed change (Rider_WorkflowService JsonDiff entry). */
export type ChangeKind = 'ADDED' | 'REMOVED' | 'CHANGED';

export interface ChangeDiffEntry {
  path: string;
  change: ChangeKind;
  before: unknown;
  after: unknown;
}

/** A maker-checker change request with its before/after snapshots and the field-level difference. */
export interface ChangeRequestDetail {
  request: {
    id: string;
    title: string;
    summary?: string | null;
    status: string;
    maker: string;
    makerComment?: string | null;
    subjectType: string;
    subjectId: string;
  };
  before: unknown;
  after: unknown;
  diff: { entries: ChangeDiffEntry[]; added: number; removed: number; changed: number };
}

/** One variable of a process instance, as shown in the instance screen's variables table. */
export interface WorkflowVariableRow {
  key: string;
  value: string;
}
