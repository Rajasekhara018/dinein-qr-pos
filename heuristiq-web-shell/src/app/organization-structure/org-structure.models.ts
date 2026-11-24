/** Shapes returned by / sent to Rider_UAM's /org-structure API (see OrgStructureController). */

export type ChangeType =
  | 'UNIT_CREATE'
  | 'UNIT_RENAME'
  | 'UNIT_MOVE'
  | 'UNIT_ARCHIVE'
  | 'UNIT_SET_LADDER'
  | 'UNIT_SET_HEAD'
  | 'UNIT_CLEAR_HEAD'
  | 'LADDER_CREATE'
  | 'LADDER_COPY'
  | 'POSITION_UPSERT'
  | 'POSITION_REMOVE'
  | 'ASSIGN'
  | 'UNASSIGN'
  | 'SET_REPORTS_TO';

export interface OrgUnit {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  unitType: string | null;
  ladderId: string | null;
  headEmployeeId: string | null;
  /** The head's name, for showing who leads the department. */
  headName?: string | null;
  /** The head's user account, for opening their wallet and team money movement. */
  headUserId?: string | null;
  status: string;
  childCount: number;
  headcount: number;
}

export interface OrgLadder {
  id: string;
  name: string;
}

export interface OrgPosition {
  id: string;
  ladderId: string;
  name: string;
  rank: number;
  managerial: boolean;
}

export interface OrgPerson {
  employeeId: string;
  employeeCode: string;
  fullName: string;
  email: string | null;
  unitId: string;
  positionId: string;
  positionName: string;
  managerEmployeeId: string | null;
  /** The person's user account, for opening their wallet. */
  userId?: string | null;
}

export interface OrgEmployee {
  id: string;
  employeeCode: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  status: string;
  userId: string | null;
  joinedOn: string | null;
}

/** What the server needs to add a person; `userId` ties them to an existing login. */
export interface CreateEmployeeRequest {
  employeeCode: string;
  fullName: string;
  email?: string | null;
  phone?: string | null;
  status?: string;
  userId?: string | null;
}

export interface OrgDraft {
  id: string;
  status: string;
  note: string | null;
  createdBy: string | null;
  createdAt: string;
  version: number;
}

/** Display names the UI stores next to the ids so a draft's change list stays readable. */
export type ChangeLabels = Record<string, string>;

export type ChangePayload = Record<string, string | number | boolean | ChangeLabels | null | undefined>;

export interface OrgChange {
  id: string;
  changeSetId: string;
  seq: number;
  type: ChangeType;
  payload: ChangePayload;
}

export type IssueSeverity = 'ERROR' | 'WARNING';

export interface OrgIssue {
  severity: IssueSeverity;
  code: string;
  message: string;
  subjectId: string | null;
}

export interface OrgDraftResult {
  applied: boolean;
  issues: OrgIssue[];
  rootUnits: OrgUnit[];
}

/** One selectable item of an {@link OrgPicker}. */
export interface PickerItem {
  id: string;
  label: string;
  /** Extra data a form may need later, e.g. the ladder of a picked unit. */
  ladderId?: string | null;
}

export type ImportStatus = 'VALIDATED' | 'APPLYING' | 'APPLIED' | 'FAILED' | 'CANCELLED';

/** An uploaded employee file: validated and staged, then confirmed (applied) or cancelled. */
export interface OrgImportBatch {
  id: string;
  status: ImportStatus;
  fileName: string | null;
  totalRows: number;
  validRows: number;
  errorRows: number;
  createdCount: number;
  updatedCount: number;
  /** Department imports only: how many of the departments the file created or updated have no position ladder (employees cannot be placed in them). */
  withoutLadder?: number | null;
  /** Employee imports only: how many departments got their head from the file. */
  headsSet?: number | null;
  /** Ladder imports only: how many new ladders the file created (createdCount and updatedCount count positions). */
  laddersCreated?: number | null;
  message: string | null;
  createdBy: string | null;
  createdAt: string;
  appliedAt: string | null;
}

/** A rejected row; each error is "CODE" or "CODE:detail" (for example DEPARTMENT_UNKNOWN:HR). */
export interface OrgImportErrorRow {
  rowNo: number;
  employeeCode: string | null;
  fullName: string | null;
  errors: string[];
}

/** Which file the import screen is working with. */
export type OrgImportMode = 'LADDERS' | 'DEPARTMENTS' | 'EMPLOYEES';

/**
 * A rejected row of a department import; each error is "CODE" or "CODE:detail" (for example PARENT_UNKNOWN:HQ). The batch itself is an
 * {@link OrgImportBatch}: a department import reports the same counts as an employee import.
 */
export interface OrgDepartmentImportErrorRow {
  rowNo: number;
  code: string | null;
  name: string | null;
  parentCode: string | null;
  unitType: string | null;
  /** The name of the position ladder the row asked for, when it did. */
  ladder: string | null;
  errors: string[];
}

/** How far one setup step (ladders, departments, employees) is: nothing loaded yet, a file being applied, a validated preview waiting to be confirmed or cancelled (and no data yet), or its data is there. */
export type OrgImportStepState = 'NOT_STARTED' | 'IN_PROGRESS' | 'PREVIEW' | 'DONE';

/** One short fact about a finished step: a translated label key and the number it shows ("Ladders: 1"). */
export interface OrgImportStepFact {
  key: string;
  count: number;
}

/** One step of the import stepper. `known` is false when this person may read neither its imports nor its data, so nothing can be said about it. */
export interface OrgImportStep {
  mode: OrgImportMode;
  number: number;
  state: OrgImportStepState;
  known: boolean;
  facts: OrgImportStepFact[];
}

/** What the live organization holds, as far as this person may read it (null: not readable or not known). */
export interface OrgImportFacts {
  ladders: number | null;
  positions: number | null;
  topDepartments: number | null;
  employees: number | null;
}

/** A rejected row of a ladder import (one job title of one ladder); each error is "CODE" or "CODE:detail" (for example DUPLICATE_ROW:2). */
export interface OrgLadderImportErrorRow {
  rowNo: number;
  ladder: string | null;
  position: string | null;
  rank: string | null;
  managerial: string | null;
  errors: string[];
}

/** Gives `roleCode` to employees in a department (and below), in a position, or both; at least one is always set. */
export interface OrgRoleRule {
  id: string;
  unitId: string | null;
  unitName: string | null;
  positionId: string | null;
  positionName: string | null;
  roleCode: string;
  active: boolean;
}

/** An employee without a login, with the roles the rules would give them. Each issue is "CODE" or "CODE:detail". */
export interface OrgLoginCandidate {
  employeeId: string;
  employeeCode: string;
  fullName: string;
  email: string | null;
  unitId: string;
  positionId: string;
  roles: string[];
  issues: string[];
  ready: boolean;
}

export type LoginOutcome = 'CREATED' | 'SKIPPED' | 'FAILED';

export interface OrgLoginResult {
  employeeId: string;
  employeeCode: string;
  outcome: LoginOutcome;
  userId: string | null;
  code: string | null;
  roles: string[];
}

export interface OrgLoginRun {
  created: number;
  skipped: number;
  failed: number;
  results: OrgLoginResult[];
  /** Where to continue when the department has more employees to look at; null at the end. */
  nextAfterEmployeeId: string | null;
}

export interface CreateLoginsRequest {
  unitId?: string;
  employeeIds?: string[];
  afterId?: string;
  limit?: number;
}
