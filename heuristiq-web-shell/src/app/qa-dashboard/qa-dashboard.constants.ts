import { QaFailureCategory, QaFilters, QaOverviewMode, QaStatus, QaSuite, QaTableKind, QaWorkspace } from './qa-dashboard.models';

/** Gateway path (below environment.apiUrl) of the QA API in Rider_ConfigService. */
export const QA_BASE_PATH = '/v1/config/qa';
export const QA_OVERVIEW_PATH = `${QA_BASE_PATH}/overview`;
export const QA_FILTERS_PATH = `${QA_BASE_PATH}/filters`;
export const QA_TEST_CASES_PATH = `${QA_BASE_PATH}/test-cases`;
export const QA_TEST_CASES_EXPORT_PATH = `${QA_BASE_PATH}/test-cases/export`;
export const QA_FAILURES_PATH = `${QA_BASE_PATH}/failures`;
export const QA_EXECUTIONS_TEST_SETS_PATH = `${QA_BASE_PATH}/executions/test-sets`;
export const QA_EXECUTIVE_SUMMARY_PATH = `${QA_BASE_PATH}/executive-summary`;
export const QA_TEST_SETS_PATH = `${QA_BASE_PATH}/test-sets`;
export const QA_TRIGGER_PATH = `${QA_BASE_PATH}/runs/trigger`;

/** The mount path of the section in the router and the paths of its screens. */
export const QA_ROUTE_PREFIX = 'qa';
export const QA_ROUTES = {
  overview: '/qa/overview',
  regression: '/qa/regression',
  smoke: '/qa/smoke',
  failures: '/qa/failures',
  executionsTestCases: '/qa/executions/test-cases',
  executionsTestSets: '/qa/executions/test-sets',
  executiveSummary: '/qa/executive-summary',
  testCases: '/qa/test-cases',
  testSets: '/qa/test-sets',
  endpointInventory: '/qa/endpoint-inventory',
  testCaseStudio: '/qa/test-case-studio',
  apiDocuments: '/qa/api-documents',
  runs: '/system/qa',
} as const;

/** Stable screen keys for the audit trail (never rename or translate once released). */
export const QA_SCREEN_KEYS = {
  overview: 'qa.overview',
  regression: 'qa.suite_regression',
  smoke: 'qa.suite_smoke',
  failures: 'qa.failures',
  executionsTestCases: 'qa.executions_test_cases',
  executionsTestSets: 'qa.executions_test_sets',
  executiveSummary: 'qa.executive_summary',
  testCases: 'qa.test_cases',
  testSets: 'qa.test_sets',
  endpointInventory: 'qa.endpoint_inventory',
  testCaseStudio: 'qa.test_case_studio',
  apiDocuments: 'qa.api_documents',
} as const;

/** Screen permission keys (mapped to privilege codes in hq-shared-lib's hq-privilege-map.ts). */
export const QA_PERMISSIONS = {
  overview: 'view_qa_overview',
  suites: 'view_qa_suites',
  failures: 'view_qa_failures',
  executions: 'view_qa_executions',
  testSetExecutions: 'view_qa_test_set_executions',
  executiveSummary: 'view_qa_executive_summary',
  testCases: 'view_qa_test_cases',
  testSets: 'view_qa_test_sets',
  endpointInventory: 'view_qa_endpoint_inventory',
  testCaseStudio: 'view_qa_test_case_studio',
  apiDocuments: 'view_qa_api_documents',
} as const;

/** Privilege codes (exact names from privilege.json). */
export const QA_DASHBOARD_PRIVILEGES = {
  runList: 'CONFIG_QA_RUN_LIST',
  overviewRead: 'CONFIG_QA_OVERVIEW_READ',
  filterRead: 'CONFIG_QA_FILTER_READ',
  testCaseList: 'CONFIG_QA_TEST_CASE_LIST',
  testCaseRead: 'CONFIG_QA_TEST_CASE_READ',
  testCaseExport: 'CONFIG_QA_TEST_CASE_EXPORT_READ',
  testCaseDelete: 'CONFIG_QA_TEST_CASE_DELETE',
  failureRead: 'CONFIG_QA_FAILURE_READ',
  executionList: 'CONFIG_QA_EXECUTION_LIST',
  executiveSummaryRead: 'CONFIG_QA_EXECUTIVE_SUMMARY_READ',
  testSetList: 'CONFIG_QA_TEST_SET_LIST',
  testSetRead: 'CONFIG_QA_TEST_SET_READ',
  testSetCreate: 'CONFIG_QA_TEST_SET_CREATE',
  testSetUpdate: 'CONFIG_QA_TEST_SET_UPDATE',
  testSetDelete: 'CONFIG_QA_TEST_SET_DELETE',
  /** POST /test-sets/{id}/run: the Run button of a test set. */
  testSetRun: 'CONFIG_QA_TEST_SET_RUN_CREATE',
  /** POST /runs/trigger: starts a run of a bare tag expression (no screen offers it yet). */
  runTrigger: 'CONFIG_QA_RUN_TRIGGER_CREATE',
  endpointList: 'CONFIG_QA_ENDPOINT_LIST',
  endpointRead: 'CONFIG_QA_ENDPOINT_READ',
  endpointExport: 'CONFIG_QA_ENDPOINT_EXPORT_READ',
  endpointFilterRead: 'CONFIG_QA_ENDPOINT_FILTER_READ',
} as const;

/** The query parameter that holds the workspace and its default. */
export const QA_WORKSPACE_PARAM = 'workspace';
export const QA_DEFAULT_WORKSPACE: QaWorkspace = 'UI';
export const QA_WORKSPACES: readonly QaWorkspace[] = ['UI', 'API'];
export const QA_WORKSPACE_KEYS: Readonly<Record<string, string>> = {
  UI: 'qaDashboard.workspace.UI',
  API: 'qaDashboard.workspace.API',
};

export const QA_OVERVIEW_MODES: readonly QaOverviewMode[] = ['overall', 'unique'];
export const QA_DEFAULT_OVERVIEW_MODE: QaOverviewMode = 'unique';
export const QA_OVERVIEW_MODE_KEYS: Readonly<Record<string, string>> = {
  overall: 'qaDashboard.mode.overall',
  unique: 'qaDashboard.mode.unique',
};

export const QA_STATUSES: readonly QaStatus[] = ['PASSED', 'FAILED', 'SKIPPED'];
/** Suites a test set can have (and the suite filter offers). */
export const QA_SUITES: readonly QaSuite[] = ['REGRESSION', 'SMOKE', 'OTHER'];

/** Environments a run may be started in (never live or production). */
export const QA_RUNNABLE_ENVIRONMENTS: readonly string[] = ['dev', 'qa', 'uat', 'local', 'pilot'];

export type QaTone = 'danger' | 'warning' | 'success' | 'neutral';

export const QA_STATUS_TONE: Readonly<Record<string, QaTone>> = {
  PASSED: 'success',
  FAILED: 'danger',
  UNSTABLE: 'warning',
  SKIPPED: 'neutral',
};

/** Translation keys of the labels below; a code with no key is shown as its raw code. */
export const QA_STATUS_KEYS: Readonly<Record<string, string>> = {
  PASSED: 'qaDashboard.status.PASSED',
  FAILED: 'qaDashboard.status.FAILED',
  SKIPPED: 'qaDashboard.status.SKIPPED',
  UNSTABLE: 'qaDashboard.status.UNSTABLE',
};

export const QA_SUITE_KEYS: Readonly<Record<string, string>> = {
  REGRESSION: 'qaDashboard.suite.REGRESSION',
  SMOKE: 'qaDashboard.suite.SMOKE',
  OTHER: 'qaDashboard.suite.OTHER',
};

export const QA_FAILURE_CATEGORY_KEYS: Readonly<Record<QaFailureCategory, string>> = {
  ASSERTION: 'qaDashboard.category.ASSERTION',
  TIMEOUT: 'qaDashboard.category.TIMEOUT',
  ELEMENT_NOT_FOUND: 'qaDashboard.category.ELEMENT_NOT_FOUND',
  ENVIRONMENT: 'qaDashboard.category.ENVIRONMENT',
  SCRIPT_ERROR: 'qaDashboard.category.SCRIPT_ERROR',
  OTHER: 'qaDashboard.category.OTHER',
};

export const QA_ENVIRONMENT_KEYS: Readonly<Record<string, string>> = {
  dev: 'qaDashboard.environments.dev',
  qa: 'qaDashboard.environments.qa',
  uat: 'qaDashboard.environments.uat',
  local: 'qaDashboard.environments.local',
  pilot: 'qaDashboard.environments.pilot',
  live: 'qaDashboard.environments.live',
};

/** Codes of the executive summary's headline, translated here (the server never returns user-facing text). */
export const QA_HEADLINE_KEYS: Readonly<Record<string, string>> = {
  ALL_GREEN: 'qaDashboard.headline.ALL_GREEN',
  FAILURES_UP: 'qaDashboard.headline.FAILURES_UP',
  FAILURES_DOWN: 'qaDashboard.headline.FAILURES_DOWN',
  NO_RUNS: 'qaDashboard.headline.NO_RUNS',
};

export const QA_DURATION_KEYS: Readonly<Record<string, string>> = {
  h: 'qaDashboard.duration.h',
  m: 'qaDashboard.duration.m',
  s: 'qaDashboard.duration.s',
  ms: 'qaDashboard.duration.ms',
};

/** Where the screens' own messages and dialog texts live. */
export const QA_MESSAGE_KEYS = {
  deleteTitle: 'qaDashboard.testCases.deleteTitle',
  deleteMessage: 'qaDashboard.testCases.deleteMessage',
  deleteConfirm: 'qaDashboard.testCases.deleteConfirm',
  deleted: 'qaDashboard.testCases.deleted',
  deleteFailed: 'qaDashboard.testCases.deleteFailed',
  exportFailed: 'qaDashboard.testCases.exportFailed',
  detailFailed: 'qaDashboard.testCases.detailFailed',
  setSaved: 'qaDashboard.testSets.saved',
  setSaveFailed: 'qaDashboard.testSets.saveFailed',
  setDeleteTitle: 'qaDashboard.testSets.deleteTitle',
  setDeleteMessage: 'qaDashboard.testSets.deleteMessage',
  setDeleted: 'qaDashboard.testSets.deleted',
  setDeleteFailed: 'qaDashboard.testSets.deleteFailed',
  runTitle: 'qaDashboard.testSets.runTitle',
  runMessage: 'qaDashboard.testSets.runMessage',
  runConfirm: 'qaDashboard.testSets.runConfirm',
  runStarted: 'qaDashboard.testSets.runStarted',
  runFailed: 'qaDashboard.testSets.runFailed',
  formCreateTitle: 'qaDashboard.testSets.createTitle',
  formEditTitle: 'qaDashboard.testSets.editTitle',
  formSave: 'qaDashboard.testSets.save',
  fieldName: 'qaDashboard.testSets.fields.name',
  fieldSuite: 'qaDashboard.testSets.fields.suite',
  fieldTagExpression: 'qaDashboard.testSets.fields.tagExpression',
  fieldTagExpressionHint: 'qaDashboard.testSets.fields.tagExpressionHint',
  fieldDescription: 'qaDashboard.testSets.fields.description',
  fieldEnvironment: 'qaDashboard.testSets.fields.environment',
  fieldError: 'qaDashboard.testSets.fields.error',
} as const;

/** Rows per page of the test-case table (the API caps a page at 50). */
export const QA_PAGE_SIZE = 20;
export const QA_MAX_PAGE_SIZE = 50;

/** A Cucumber tag expression: tags, and/or/not, brackets and spaces (the server checks the same shape). */
export const QA_TAG_EXPRESSION_PATTERN = /^[@a-z0-9_.:\-() ]+$/;
export const QA_TAG_EXPRESSION_MAX = 300;
export const QA_TEST_SET_NAME_MIN = 3;
export const QA_TEST_SET_NAME_MAX = 100;
export const QA_TEST_SET_DESCRIPTION_MAX = 500;

/** The empty filter set. */
export const QA_EMPTY_FILTERS: Readonly<QaFilters> = {
  environment: '',
  release: '',
  suite: '',
  module: '',
  subModule: '',
  status: '',
  q: '',
  from: '',
  to: '',
};

/** How long the search box waits after the last key before it asks the server. */
export const QA_SEARCH_DEBOUNCE_MS = 350;

export type QaColumn =
  | 'tcId'
  | 'name'
  | 'module'
  | 'subModule'
  | 'iteration'
  | 'passCount'
  | 'failCount'
  | 'skipCount'
  | 'environment'
  | 'status'
  | 'duration'
  | 'date'
  | 'release'
  | 'category';

/** What each table screen shows: the suite it is fixed to, whether it lets the user export and delete, and its columns. */
export interface QaTableConfig {
  suite: QaSuite | null;
  showSuiteFilter: boolean;
  allowExport: boolean;
  allowDelete: boolean;
  columns: readonly QaColumn[];
}

const SUITE_COLUMNS: readonly QaColumn[] = [
  'tcId', 'name', 'module', 'subModule', 'iteration', 'environment', 'status', 'duration', 'date',
];

export const QA_TABLE_CONFIGS: Readonly<Record<QaTableKind, QaTableConfig>> = {
  regression: { suite: 'REGRESSION', showSuiteFilter: false, allowExport: true, allowDelete: false, columns: SUITE_COLUMNS },
  smoke: { suite: 'SMOKE', showSuiteFilter: false, allowExport: true, allowDelete: false, columns: SUITE_COLUMNS },
  inventory: {
    suite: null,
    showSuiteFilter: true,
    allowExport: true,
    allowDelete: true,
    columns: ['tcId', 'name', 'module', 'subModule', 'environment', 'iteration', 'status', 'release', 'date'],
  },
  executions: {
    suite: null,
    showSuiteFilter: true,
    allowExport: true,
    allowDelete: false,
    columns: ['tcId', 'name', 'module', 'environment', 'iteration', 'passCount', 'failCount', 'skipCount', 'status', 'category', 'date'],
  },
};

/** Translation keys of the table's column headings. */
export const QA_COLUMN_KEYS: Readonly<Record<QaColumn, string>> = {
  tcId: 'qaDashboard.columns.tcId',
  name: 'qaDashboard.columns.testCase',
  module: 'qaDashboard.columns.module',
  subModule: 'qaDashboard.columns.subModule',
  iteration: 'qaDashboard.columns.iteration',
  passCount: 'qaDashboard.columns.passCount',
  failCount: 'qaDashboard.columns.failCount',
  skipCount: 'qaDashboard.columns.skipCount',
  environment: 'qaDashboard.columns.environment',
  status: 'qaDashboard.columns.status',
  duration: 'qaDashboard.columns.duration',
  date: 'qaDashboard.columns.date',
  release: 'qaDashboard.columns.release',
  category: 'qaDashboard.columns.category',
};

/** Columns of the test case table that hold a number, so they are right-aligned. */
export const QA_NUMERIC_COLUMNS: readonly QaColumn[] = ['iteration', 'passCount', 'failCount', 'skipCount'];

/** The viewBox of the charts and their padding (SVG units). */
export const QA_BAR_VIEW = { width: 320, height: 160, paddingTop: 8, paddingBottom: 8, paddingX: 8, gap: 2, maxBars: 12 } as const;
export const QA_LINE_VIEW = { width: 320, height: 120, padding: 8 } as const;
export const QA_DONUT_VIEW = { size: 160, radius: 62, stroke: 22 } as const;

/** Link attributes for anything that opens another site or a generated report. */
export const QA_LINK_REL = 'noopener noreferrer';

/** Rows shown in the failure and executive lists. */
export const QA_LIST_LIMIT = 10;

/** Name of the CSV the export saves. */
export const QA_EXPORT_FILE_NAME = 'qa-test-cases.csv';
