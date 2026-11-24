import { ChangeType, OrgImportMode, OrgImportStepState } from './org-structure.models';

/** Path appended to environment.uamApiUrl (the gateway rewrites it to UAM's /api/v1/uam/org-structure). */
export const ORG_API_BASE = '/org-structure';

/** Screen keys for the audit trail: queryable history, never renamed or translated once released. */
export const ORG_SCREEN_KEYS = {
  chart: 'org_structure.chart',
  builder: 'org_structure.builder',
  import: 'org_structure.import',
  logins: 'org_structure.logins',
} as const;

/** Screen permission keys, mapped to privilege codes in hq-shared-lib's privilege map. */
export const ORG_PERMISSIONS = {
  view: 'view_org_chart',
  manage: 'manage_org_structure',
  import: 'import_org_employees',
  logins: 'manage_org_logins',
} as const;

/** Privilege codes (names in privilege.json) that guard each action on the builder screen. */
export const ORG_PRIVILEGES = {
  draftOpen: 'ORG_DRAFT_OPEN',
  changeAdd: 'ORG_DRAFT_CHANGE_ADD',
  changeRemove: 'ORG_DRAFT_CHANGE_REMOVE',
  preview: 'ORG_DRAFT_PREVIEW',
  publish: 'ORG_DRAFT_PUBLISH',
  discard: 'ORG_DRAFT_DISCARD',
  rollback: 'ORG_DRAFT_ROLLBACK',
  employeeCreate: 'ORG_EMPLOYEE_CREATE',
} as const;

/** Privilege codes (names in privilege.json) that guard each action on the import screen. */
export const ORG_IMPORT_PRIVILEGES = {
  create: 'ORG_IMPORT_CREATE',
  confirm: 'ORG_IMPORT_CONFIRM',
  cancel: 'ORG_IMPORT_CANCEL',
} as const;

/** Privilege codes that guard each action of the ladders and positions import (the Ladders & positions tab of the import screen). */
export const ORG_LADDER_IMPORT_PRIVILEGES = {
  create: 'ORG_LADDER_IMPORT_CREATE',
  list: 'ORG_LADDER_IMPORT_LIST',
  read: 'ORG_LADDER_IMPORT_READ',
  errorList: 'ORG_LADDER_IMPORT_ERROR_LIST',
  confirm: 'ORG_LADDER_IMPORT_CONFIRM',
  cancel: 'ORG_LADDER_IMPORT_CANCEL',
} as const;

/** Privilege codes (names in privilege.json) that guard each action of the department import (the Departments tab of the import screen). */
export const ORG_DEPARTMENT_IMPORT_PRIVILEGES = {
  create: 'ORG_DEPARTMENT_IMPORT_CREATE',
  list: 'ORG_DEPARTMENT_IMPORT_LIST',
  read: 'ORG_DEPARTMENT_IMPORT_READ',
  errorList: 'ORG_DEPARTMENT_IMPORT_ERROR_LIST',
  confirm: 'ORG_DEPARTMENT_IMPORT_CONFIRM',
  cancel: 'ORG_DEPARTMENT_IMPORT_CANCEL',
} as const;

export const ORG_PAGE_SIZE = 50;
export const PICKER_SEARCH_DEBOUNCE_MS = 250;
export const PICKER_MAX_RESULTS = 25;
/** Key under which the UI keeps display names inside a change payload (ignored by the backend). */
export const PAYLOAD_LABELS_KEY = '__labels';

export type FieldKind = 'text' | 'number' | 'boolean' | 'unit' | 'ladder' | 'position' | 'employee';

export interface ChangeField {
  key: string;
  kind: FieldKind;
  required: boolean;
  /** i18n key overriding the default `orgStructure.fields.<key>` label (the payload key `id` means different things per form). */
  labelKey?: string;
  /** For a position field: the sibling field whose unit decides which ladder's positions are offered. */
  unitKey?: string;
  /** For a position field: the sibling field holding the ladder directly. */
  ladderKey?: string;
}

export interface ChangeFormConfig {
  type: ChangeType;
  /** i18n key of the change type's label. */
  labelKey: string;
  /** i18n key of the group heading it is listed under. */
  groupKey: string;
  fields: ChangeField[];
}

const GROUP_UNITS = 'orgStructure.builder.groups.units';
const GROUP_LADDERS = 'orgStructure.builder.groups.ladders';
const GROUP_PEOPLE = 'orgStructure.builder.groups.people';

/** The draft operations a user can add, with the fields each one needs. Field labels are orgStructure.fields.<key>. */
export const CHANGE_FORMS: readonly ChangeFormConfig[] = [
  {
    type: 'UNIT_CREATE', labelKey: 'orgStructure.changeTypes.UNIT_CREATE', groupKey: GROUP_UNITS,
    fields: [
      { key: 'code', kind: 'text', required: true },
      { key: 'name', kind: 'text', required: true },
      { key: 'parentId', kind: 'unit', required: false },
      { key: 'ladderId', kind: 'ladder', required: false },
      { key: 'unitType', kind: 'text', required: false },
    ],
  },
  {
    type: 'UNIT_RENAME', labelKey: 'orgStructure.changeTypes.UNIT_RENAME', groupKey: GROUP_UNITS,
    fields: [
      { key: 'id', kind: 'unit', required: true, labelKey: 'orgStructure.fields.unit' },
      { key: 'name', kind: 'text', required: true },
    ],
  },
  {
    type: 'UNIT_MOVE', labelKey: 'orgStructure.changeTypes.UNIT_MOVE', groupKey: GROUP_UNITS,
    fields: [
      { key: 'id', kind: 'unit', required: true, labelKey: 'orgStructure.fields.unit' },
      { key: 'newParentId', kind: 'unit', required: false },
    ],
  },
  {
    type: 'UNIT_ARCHIVE', labelKey: 'orgStructure.changeTypes.UNIT_ARCHIVE', groupKey: GROUP_UNITS,
    fields: [{ key: 'id', kind: 'unit', required: true, labelKey: 'orgStructure.fields.unit' }],
  },
  {
    type: 'UNIT_SET_LADDER', labelKey: 'orgStructure.changeTypes.UNIT_SET_LADDER', groupKey: GROUP_UNITS,
    fields: [
      { key: 'unitId', kind: 'unit', required: true },
      { key: 'ladderId', kind: 'ladder', required: true },
    ],
  },
  {
    type: 'UNIT_SET_HEAD', labelKey: 'orgStructure.changeTypes.UNIT_SET_HEAD', groupKey: GROUP_UNITS,
    fields: [
      { key: 'unitId', kind: 'unit', required: true },
      { key: 'employeeId', kind: 'employee', required: true },
    ],
  },
  {
    type: 'UNIT_CLEAR_HEAD', labelKey: 'orgStructure.changeTypes.UNIT_CLEAR_HEAD', groupKey: GROUP_UNITS,
    fields: [{ key: 'unitId', kind: 'unit', required: true }],
  },
  {
    type: 'LADDER_CREATE', labelKey: 'orgStructure.changeTypes.LADDER_CREATE', groupKey: GROUP_LADDERS,
    fields: [{ key: 'name', kind: 'text', required: true }],
  },
  {
    type: 'LADDER_COPY', labelKey: 'orgStructure.changeTypes.LADDER_COPY', groupKey: GROUP_LADDERS,
    fields: [
      { key: 'sourceLadderId', kind: 'ladder', required: true },
      { key: 'name', kind: 'text', required: true },
    ],
  },
  {
    type: 'POSITION_UPSERT', labelKey: 'orgStructure.changeTypes.POSITION_UPSERT', groupKey: GROUP_LADDERS,
    fields: [
      { key: 'ladderId', kind: 'ladder', required: true },
      { key: 'name', kind: 'text', required: true },
      { key: 'rank', kind: 'number', required: true },
      { key: 'managerial', kind: 'boolean', required: false },
    ],
  },
  {
    type: 'POSITION_REMOVE', labelKey: 'orgStructure.changeTypes.POSITION_REMOVE', groupKey: GROUP_LADDERS,
    fields: [
      { key: 'ladderId', kind: 'ladder', required: true },
      { key: 'id', kind: 'position', required: true, ladderKey: 'ladderId', labelKey: 'orgStructure.fields.position' },
    ],
  },
  {
    type: 'ASSIGN', labelKey: 'orgStructure.changeTypes.ASSIGN', groupKey: GROUP_PEOPLE,
    fields: [
      { key: 'employeeId', kind: 'employee', required: true },
      { key: 'unitId', kind: 'unit', required: true },
      { key: 'positionId', kind: 'position', required: true, unitKey: 'unitId' },
      { key: 'primary', kind: 'boolean', required: false },
    ],
  },
  {
    type: 'UNASSIGN', labelKey: 'orgStructure.changeTypes.UNASSIGN', groupKey: GROUP_PEOPLE,
    fields: [
      { key: 'employeeId', kind: 'employee', required: true },
      { key: 'unitId', kind: 'unit', required: true },
    ],
  },
  {
    type: 'SET_REPORTS_TO', labelKey: 'orgStructure.changeTypes.SET_REPORTS_TO', groupKey: GROUP_PEOPLE,
    fields: [
      { key: 'employeeId', kind: 'employee', required: true },
      { key: 'managerEmployeeId', kind: 'employee', required: false },
    ],
  },
];

/** Change types that create a record whose id the server fills in and later changes can refer to. */
export const ID_GENERATING_CHANGES: ReadonlySet<ChangeType> = new Set<ChangeType>([
  'UNIT_CREATE', 'LADDER_CREATE', 'LADDER_COPY', 'POSITION_UPSERT', 'ASSIGN',
]);

/** How often the import screen re-reads a batch while it is being applied in the background. */
export const IMPORT_POLL_INTERVAL_MS = 3000;
export const IMPORT_FILE_ACCEPT = '.csv';
/** DMS category the employee files are filed under, and the size cap (DMS refuses files of 10 MB or more). */
export const IMPORT_DMS_CATEGORY = 'org-import';
export const IMPORT_MAX_SIZE_MB = 9.9;
export const IMPORT_UPLOAD_INPUT_ID = 'org-import-file';

/** Statuses after which a batch no longer changes by itself. */
export const IMPORT_SETTLED_STATUSES: ReadonlySet<string> = new Set(['VALIDATED', 'APPLIED', 'FAILED', 'CANCELLED']);

/** Columns of the CSV the importer accepts (the first four are required) and an example row for the template. */
export const IMPORT_TEMPLATE_HEADER = [
  'employee_code', 'full_name', 'department_code', 'position_name', 'email', 'phone', 'manager_employee_code', 'joined_on', 'is_head',
] as const;
export const IMPORT_TEMPLATE_FILE_NAME = 'employees-template.csv';

/** A filled example (12 people in 6 departments with reporting lines and a head each; same content as docs/samples/org-import-sample.csv); the ladders, positions and departments must exist first. */
export const IMPORT_SAMPLE_ROWS: readonly (readonly string[])[] = [
  ['E1001', 'Rajesh Kumar', 'EXEC', 'Head', 'rajesh.kumar@example.com', '', '', '2022-04-01', 'yes'],
  ['E1002', 'Priya Sharma', 'TECH', 'Head', 'priya.sharma@example.com', '', 'E1001', '2022-06-15', 'yes'],
  ['E1003', 'Arjun Reddy', 'BACKEND', 'Lead', 'arjun.reddy@example.com', '', 'E1002', '2023-01-09', 'yes'],
  ['E1004', 'Sneha Iyer', 'BACKEND', 'Member', 'sneha.iyer@example.com', '', 'E1003', '2023-07-03', ''],
  ['E1005', 'Karthik Nair', 'BACKEND', 'Member', 'karthik.nair@example.com', '', 'E1003', '2024-02-12', ''],
  ['E1006', 'Divya Menon', 'FRONTEND', 'Lead', 'divya.menon@example.com', '', 'E1002', '2023-03-20', 'yes'],
  ['E1007', 'Rohit Verma', 'FRONTEND', 'Member', 'rohit.verma@example.com', '', 'E1006', '2024-05-06', ''],
  ['E1008', 'Anita Desai', 'SALES', 'Head', 'anita.desai@example.com', '', 'E1001', '2022-09-01', 'yes'],
  ['E1009', 'Vikram Singh', 'SALES', 'Member', 'vikram.singh@example.com', '', 'E1008', '2023-11-13', ''],
  ['E1010', 'Meera Pillai', 'SALES', 'Member', 'meera.pillai@example.com', '', 'E1008', '2024-08-26', ''],
  ['E1011', 'Suresh Babu', 'HR', 'Head', 'suresh.babu@example.com', '', 'E1001', '2022-10-17', 'yes'],
  ['E1012', 'Lakshmi Rao', 'HR', 'Member', 'lakshmi.rao@example.com', '', 'E1011', '2025-01-08', ''],
];
export const IMPORT_SAMPLE_FILE_NAME = 'org-import-sample.csv';

/** Error codes the server can return for a row; labels are orgStructure.import.errors.<CODE>. */
export const IMPORT_ERROR_CODES: readonly string[] = [
  'EMPLOYEE_CODE_REQUIRED', 'EMPLOYEE_CODE_TOO_LONG', 'EMPLOYEE_CODE_DUPLICATE', 'FULL_NAME_REQUIRED', 'FULL_NAME_TOO_LONG',
  'EMAIL_INVALID', 'JOINED_ON_INVALID', 'DEPARTMENT_REQUIRED', 'DEPARTMENT_UNKNOWN', 'DEPARTMENT_HAS_NO_LADDER',
  'POSITION_REQUIRED', 'POSITION_NOT_IN_LADDER', 'MANAGER_UNKNOWN', 'MANAGER_IS_SELF', 'MANAGER_CYCLE', 'IS_HEAD_INVALID', 'HEAD_DUPLICATE',
];

/** The files the import screen can take, in the order of its setup steps. */
export const IMPORT_MODES: readonly OrgImportMode[] = ['LADDERS', 'DEPARTMENTS', 'EMPLOYEES'];

/** What one tab of the import screen needs from the privilege catalogue (the employee tab has no list, read or error-list privilege of its own). */
export interface OrgImportPrivileges {
  readonly create: string;
  readonly confirm: string;
  readonly cancel: string;
  readonly list?: string;
  readonly read?: string;
  readonly errorList?: string;
}

export const IMPORT_PRIVILEGES_BY_MODE: Readonly<Record<OrgImportMode, OrgImportPrivileges>> = {
  LADDERS: ORG_LADDER_IMPORT_PRIVILEGES,
  DEPARTMENTS: ORG_DEPARTMENT_IMPORT_PRIVILEGES,
  EMPLOYEES: ORG_IMPORT_PRIVILEGES,
};

/** Where the texts of each tab live: its own texts under <prefix>, the labels of its row problems under <prefix>errors.<CODE>. */
export const IMPORT_TEXT_PREFIX: Readonly<Record<OrgImportMode, string>> = {
  LADDERS: 'orgStructure.import.ladders.',
  DEPARTMENTS: 'orgStructure.import.departments.',
  EMPLOYEES: 'orgStructure.import.',
};

export const IMPORT_TAB_ID_PREFIX = 'org-import-step-';
export const IMPORT_PANEL_ID = 'org-import-panel';

/** The setup steps in the order they are done, and the name of each in the address (?step=ladders|departments|employees) and in translation keys. */
export const IMPORT_STEP_KEY: Readonly<Record<OrgImportMode, string>> = { LADDERS: 'ladders', DEPARTMENTS: 'departments', EMPLOYEES: 'employees' };
export const IMPORT_STEP_QUERY_PARAM = 'step';
export const IMPORT_STEP_SESSION_KEY = 'org-import.step';
/** The step each step builds on (the first has none). */
export const IMPORT_PREVIOUS_STEP: Readonly<Partial<Record<OrgImportMode, OrgImportMode>>> = { DEPARTMENTS: 'LADDERS', EMPLOYEES: 'DEPARTMENTS' };
/** Icon beside each state; the state's words are always shown too, so colour and icon are never the only sign. */
export const IMPORT_STEP_ICONS: Readonly<Record<OrgImportStepState, string>> = {
  NOT_STARTED: 'bi-circle', IN_PROGRESS: 'bi-hourglass-split', PREVIEW: 'bi-eye', DONE: 'bi-check-circle-fill',
};
/** Privileges that let the stepper read facts about the live organization (without them the step shows no fact, never an error). */
export const IMPORT_FACT_PRIVILEGES = { ladders: 'ORG_LADDER_LIST', positions: 'ORG_POSITION_LIST', units: 'ORG_UNIT_LIST' } as const;
/** Positions are counted ladder by ladder; past this many ladders the count is left out rather than made with many calls. */
export const IMPORT_FACT_MAX_LADDERS = 10;
/** Statuses of an import that decide a step's state; FAILED and CANCELLED imports never change it. */
export const IMPORT_APPLYING_STATUS = 'APPLYING';
export const IMPORT_APPLIED_STATUS = 'APPLIED';
export const IMPORT_PREVIEW_STATUS = 'VALIDATED';

/** Upload input of the department file. */
export const DEPARTMENT_IMPORT_UPLOAD_INPUT_ID = 'org-department-import-file';

/** Columns of the department CSV (code and name are required; unit_type and ladder, the name of an existing position ladder, are optional) and the example rows of its sample: a root, a child and a grandchild. */
export const DEPARTMENT_IMPORT_HEADER = ['code', 'name', 'parent_code', 'unit_type', 'ladder'] as const;
export const DEPARTMENT_IMPORT_SAMPLE_ROWS: readonly (readonly string[])[] = [
  ['EXEC', 'Executive Office', '', 'DIVISION', 'Standard'],
  ['TECH', 'Technology', 'EXEC', 'DIVISION', 'Standard'],
  ['HR', 'Human Resources', 'EXEC', 'DEPARTMENT', 'Standard'],
  ['SALES', 'Sales', 'EXEC', 'DEPARTMENT', 'Standard'],
  ['BACKEND', 'Backend Engineering', 'TECH', 'TEAM', 'Standard'],
  ['FRONTEND', 'Frontend Engineering', 'TECH', 'TEAM', 'Standard'],
];
export const DEPARTMENT_IMPORT_SAMPLE_FILE_NAME = 'org-departments-import-sample.csv';
export const DEPARTMENT_IMPORT_TEMPLATE_FILE_NAME = 'departments-template.csv';

/** Error codes the server can return for a department row; labels are orgStructure.import.departments.errors.<CODE>. */
export const DEPARTMENT_IMPORT_ERROR_CODES: readonly string[] = [
  'CODE_REQUIRED', 'CODE_TOO_LONG', 'CODE_INVALID', 'CODE_DUPLICATE', 'CODE_ARCHIVED',
  'NAME_REQUIRED', 'NAME_TOO_LONG', 'UNIT_TYPE_TOO_LONG',
  'PARENT_UNKNOWN', 'PARENT_REJECTED', 'PARENT_CYCLE', 'TOO_DEEP', 'LADDER_UNKNOWN',
];


/** Columns of the ladders and positions CSV (one job title of one ladder per row) and the sample the browser builds (same content as docs/samples/org-ladders-import-sample.csv). */
export const LADDER_IMPORT_HEADER = ['ladder', 'position', 'rank', 'managerial'] as const;
export const LADDER_IMPORT_SAMPLE_ROWS: readonly (readonly string[])[] = [
  ['Standard', 'Head', '3', 'yes'],
  ['Standard', 'Lead', '2', 'yes'],
  ['Standard', 'Member', '1', 'no'],
];
export const LADDER_IMPORT_SAMPLE_FILE_NAME = 'org-ladders-import-sample.csv';
export const LADDER_IMPORT_TEMPLATE_FILE_NAME = 'ladders-template.csv';
export const LADDER_IMPORT_UPLOAD_INPUT_ID = 'org-ladder-import-file';

/** Error codes the server can return for a ladder row; labels are orgStructure.import.ladders.errors.<CODE>. */
export const LADDER_IMPORT_ERROR_CODES: readonly string[] = [
  'LADDER_REQUIRED', 'LADDER_TOO_LONG', 'POSITION_REQUIRED', 'POSITION_TOO_LONG', 'RANK_INVALID', 'MANAGERIAL_INVALID', 'DUPLICATE_ROW', 'RANK_DUPLICATE', 'RANK_TAKEN',
];

/** Row errors whose detail holds two values, written CODE:<first>:<second>; they are shown with {{code}} and {{row}} (see importErrorParams). */
export const IMPORT_TWO_VALUED_ERROR_CODES: ReadonlySet<string> = new Set(['PARENT_REJECTED', 'HEAD_DUPLICATE']);

/** Rejections that mean the ladders and job titles are not loaded yet (the code the server gives, and the words of its English text): the hint to import them first is shown. */
export const IMPORT_NEEDS_LADDERS: Readonly<Partial<Record<OrgImportMode, { readonly codes: readonly string[]; readonly texts: readonly string[] }>>> = {
  EMPLOYEES: { codes: ['POSITION_REQUIRED', 'POSITION_NOT_IN_LADDER'], texts: ['unknown position', 'not in the ladder'] },
  DEPARTMENTS: { codes: ['LADDER_UNKNOWN'], texts: ['unknown position ladder'] },
};

/**
 * The row problem that means the department the employee row names has no position ladder (the server says DEPARTMENT_HAS_NO_LADDER:<code>; its
 * English text, "Department X has no position ladder", is recognised too): the departments need a ladder, not a new employee file.
 */
export const IMPORT_NO_LADDER_CODE = 'DEPARTMENT_HAS_NO_LADDER';
export const IMPORT_NO_LADDER_TEXT = 'has no position ladder';

/** The row problem that means "import the departments first" (an employee row names a department that does not exist). */
export const IMPORT_UNKNOWN_DEPARTMENT_CODE = 'DEPARTMENT_UNKNOWN';

/** Which text the file chooser gave for a refused file (see classifyUploadProblem): the keys under orgStructure.import.departments.upload. */
export const UPLOAD_PROBLEM_KEYS = { tooLarge: 'tooLarge', notCsv: 'notCsv', tooSmall: 'tooSmall', failed: 'failed' } as const;

/** Privilege codes (names in privilege.json) that guard each action on the roles-and-logins screen. */
export const ORG_LOGIN_PRIVILEGES = {
  ruleList: 'ORG_ROLE_RULE_LIST',
  ruleCreate: 'ORG_ROLE_RULE_CREATE',
  ruleDelete: 'ORG_ROLE_RULE_DELETE',
  loginCreate: 'ORG_LOGIN_CREATE',
} as const;

/** Employees looked at per request while creating logins for a whole department; the server caps this at 200. */
export const LOGIN_RUN_PAGE_SIZE = 25;
/** The screen the Roles dialog of the Org builder links to for creating logins, and the dialog's width. */
export const ORG_LOGINS_ROUTE = '/org-structure/logins';
export const ORG_ROLES_DIALOG_WIDTH = '560px';
export const ORG_ROLES_DIALOG_TITLE_ID = 'org-dept-roles-title';
/** Most roles offered in the rule form's dropdown. */
export const ROLE_OPTIONS_LIMIT = 200;
export const LOGIN_ISSUE_CODES: readonly string[] = [
  'NO_EMAIL', 'NO_ROLE_RULE', 'UNKNOWN_ROLE', 'EMAIL_IN_USE', 'CREATE_FAILED', 'LINK_FAILED',
];

/** Key of the top level in the department tree's cache of loaded children (a unit id is never empty). */
export const TREE_ROOT_KEY = '';
/** Longest department code the server accepts; generated codes stay well under it. */
export const UNIT_CODE_MAX_LENGTH = 100;
export const UNIT_CODE_FALLBACK = 'DEPT';
/** Room left for the "_2", "_3" suffix that keeps a generated code unique. */
export const UNIT_CODE_SUFFIX_ROOM = 6;
/** Indent per tree level, in rem. */
export const TREE_INDENT_REM = 1.5;

/** Most levels of sub-departments fetched to draw the whole chart; a safety stop, not an expected depth. */
export const CHART_MAX_LEVELS = 20;

/** Users offered per search when picking a person to add to a department. */
export const PERSON_SEARCH_LIMIT = 10;
export const EMPLOYEE_LOOKUP_LIMIT = 20;
/** Status a person added from a user starts with. */
export const NEW_EMPLOYEE_STATUS = 'ACTIVE';

/** People fetched per department for the chart (the server caps a page at 200) and how many departments get that lookup. */
export const CHART_PEOPLE_PER_UNIT = 200;
export const CHART_MAX_PEOPLE_UNITS = 100;
/** People named on a chart card before "+N more". */
export const CHART_PEOPLE_SHOWN = 4;

/** The drawn chart: card size and spacing, the box that joins several top-level departments, and image export. */
export const CHART_ROOT_ID = '__organization__';
export const CHART_NODE_WIDTH = 250;
export const CHART_NODE_BASE_HEIGHT = 96;
export const CHART_PERSON_ROW_HEIGHT = 34;
export const CHART_MORE_ROW_HEIGHT = 22;
export const CHART_CHILDREN_MARGIN = 70;
export const CHART_SIBLINGS_MARGIN = 24;
export const CHART_EXPORT_SCALE = 2;
export const CHART_EXPORT_BACKGROUND = '#ffffff';
export const CHART_HEIGHT_PX = 560;

/** What the template (header only) and the sample (with data) downloads of each step contain; both are built in the browser. */
export interface OrgImportDownload {
  readonly header: readonly string[];
  readonly sampleRows: readonly (readonly string[])[];
  readonly templateFileName: string;
  readonly sampleFileName: string;
}

export const IMPORT_DOWNLOADS: Readonly<Record<OrgImportMode, OrgImportDownload>> = {
  LADDERS: { header: LADDER_IMPORT_HEADER, sampleRows: LADDER_IMPORT_SAMPLE_ROWS, templateFileName: LADDER_IMPORT_TEMPLATE_FILE_NAME, sampleFileName: LADDER_IMPORT_SAMPLE_FILE_NAME },
  DEPARTMENTS: {
    header: DEPARTMENT_IMPORT_HEADER, sampleRows: DEPARTMENT_IMPORT_SAMPLE_ROWS,
    templateFileName: DEPARTMENT_IMPORT_TEMPLATE_FILE_NAME, sampleFileName: DEPARTMENT_IMPORT_SAMPLE_FILE_NAME,
  },
  EMPLOYEES: { header: IMPORT_TEMPLATE_HEADER, sampleRows: IMPORT_SAMPLE_ROWS, templateFileName: IMPORT_TEMPLATE_FILE_NAME, sampleFileName: IMPORT_SAMPLE_FILE_NAME },
};

/** The "Loaded now" section of each import step: departments read per level before "Show more", ladders read at most, calls made to read every department of the employee step. */
export const IMPORT_LOADED_LEVEL_PAGE = 200;
export const IMPORT_LOADED_MAX_LADDERS = 50;
export const IMPORT_LOADED_MAX_CALLS = 50;
/** Imports that say something about what is loaded now (failed and cancelled ones do not). */
export const IMPORT_LOADED_LATEST_STATUSES: ReadonlySet<string> = new Set(['APPLIED', 'APPLYING', 'VALIDATED']);
/** Where the Org chart is, for the link from the department and employee steps. */
export const ORG_CHART_ROUTE = '/org-structure/chart';
