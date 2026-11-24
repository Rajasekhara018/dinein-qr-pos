import { TableColumnSpec } from '../../shared/table-columns.helpers';
import { QA_BASE_PATH } from '../qa-dashboard.constants';
import {
  QaAssertionType,
  QaStudioAuth,
  QaStudioCaseItem,
  QaStudioFormValue,
  QaStudioMethod,
  QaStudioQuery,
  QaStudioSuite,
} from './qa-studio.models';

/** Columns of the studio's list of test cases; module and the check count are plain text, the rest are drawn by templates. */
export const QA_STUDIO_LIST_COLUMNS: readonly TableColumnSpec<QaStudioCaseItem>[] = [
  { key: 'tcId', labelKey: 'qaDashboard.columns.tcId' },
  { key: 'name', labelKey: 'qaDashboard.columns.testCase' },
  { key: 'module', labelKey: 'qaDashboard.columns.module' },
  { key: 'subModule', labelKey: 'qaDashboard.columns.subModule' },
  { key: 'suite', labelKey: 'qaDashboard.columns.suite' },
  { key: 'request', labelKey: 'qaDashboard.studio.list.request' },
  { key: 'auth', labelKey: 'qaDashboard.studio.list.auth' },
  { key: 'assertionCount', labelKey: 'qaDashboard.studio.list.checks', className: 'qa-num' },
  { key: 'enabled', labelKey: 'qaDashboard.studio.list.enabled' },
  { key: 'updated', labelKey: 'qaDashboard.columns.updated' },
];

/** Gateway path (below environment.apiUrl) of the studio API. */
export const QA_STUDIO_CASES_PATH = `${QA_BASE_PATH}/studio/cases`;

/** The list and the editor in the router. */
export const QA_STUDIO_ROUTES = {
  list: '/qa/test-case-studio',
  new: '/qa/test-case-studio/new',
} as const;
export const QA_STUDIO_NEW_SEGMENT = 'new';
export const QA_STUDIO_ID_PARAM = 'id';

export const QA_STUDIO_PRIVILEGES = {
  list: 'CONFIG_QA_STUDIO_CASE_LIST',
  read: 'CONFIG_QA_STUDIO_CASE_READ',
  create: 'CONFIG_QA_STUDIO_CASE_CREATE',
  update: 'CONFIG_QA_STUDIO_CASE_UPDATE',
  enable: 'CONFIG_QA_STUDIO_CASE_ENABLE_UPDATE',
  delete: 'CONFIG_QA_STUDIO_CASE_DELETE',
} as const;

// ---- limits (the server enforces the same ones) ----
export const QA_STUDIO_LIMITS = {
  nameMin: 3,
  nameMax: 150,
  descriptionMax: 500,
  subModuleMax: 100,
  pathMax: 300,
  queryMax: 20,
  headersMax: 10,
  bodyMaxBytes: 16 * 1024,
  assertionsMax: 20,
  jsonPathMax: 200,
  expectedTextMax: 500,
  statusCodesMax: 5,
  statusMin: 100,
  statusMax: 599,
  timeMin: 1,
  timeMax: 60_000,
  headerNameMax: 100,
  valueMax: 500,
} as const;

/** Every path starts here, so a case can only reach the platform's own API. */
export const QA_STUDIO_PATH_PREFIX = '/api/';

/** Headers a case may not set: the sign-in is only by the account profile, the rest is set by the transport. */
export const QA_STUDIO_FORBIDDEN_HEADERS: readonly string[] = ['authorization', 'cookie', 'host', 'content-length'];
export const QA_STUDIO_FORBIDDEN_HEADER_PREFIXES: readonly string[] = ['x-forwarded-'];
/** A header name: the token characters of HTTP. */
export const QA_STUDIO_HEADER_NAME_PATTERN = /^[A-Za-z0-9!#$%&'*+.^_`|~-]+$/;
/** A query parameter name: letters, digits and a few separators. */
export const QA_STUDIO_QUERY_NAME_PATTERN = /^[A-Za-z0-9_.\-[\]]+$/;

/** What a secret looks like: a bearer token, a JWT, a private key header. */
export const QA_STUDIO_SECRET_VALUE_PATTERNS: readonly RegExp[] = [
  /\bbearer\s+[A-Za-z0-9._~+/=-]{8,}/i,
  /\beyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]*/,
  /-----BEGIN [A-Z ]*PRIVATE KEY-----/,
  /\bbasic\s+[A-Za-z0-9+/=]{12,}/i,
];
/** A name that holds a secret: a value under it is refused (an empty value is fine). */
export const QA_STUDIO_SECRET_NAME_PATTERN = /(passw(or)?d|secret|token|otp|cvv|card.?number|api.?key|credential)|^pin$/i;

/** The area tags a case can belong to (the tag vocabulary of the whole suite). */
export const QA_STUDIO_MODULES: readonly string[] = [
  'uam', 'platform', 'config', 'matrimony', 'wallet', 'payment', 'customer-service', 'ride', 'dispatch', 'pricing',
  'audit', 'workflow', 'admin', 'reports', 'onboarding', 'location', 'train', 'driver-earnings', 'nms', 'search',
];

export const QA_STUDIO_SUITES: readonly QaStudioSuite[] = ['REGRESSION', 'SMOKE'];
export const QA_STUDIO_METHODS: readonly QaStudioMethod[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];
/** Only these methods carry a body. */
export const QA_STUDIO_BODY_METHODS: readonly QaStudioMethod[] = ['POST', 'PUT', 'PATCH'];

export const QA_STUDIO_AUTHS: readonly QaStudioAuth[] = ['NONE', 'QA_ADMIN', 'CUSTOMER_A', 'CUSTOMER_B'];
export const QA_STUDIO_AUTH_KEYS: Readonly<Record<string, string>> = {
  NONE: 'qaDashboard.studio.auth.NONE',
  QA_ADMIN: 'qaDashboard.studio.auth.QA_ADMIN',
  CUSTOMER_A: 'qaDashboard.studio.auth.CUSTOMER_A',
  CUSTOMER_B: 'qaDashboard.studio.auth.CUSTOMER_B',
};

export const QA_ASSERTION_TYPES: readonly QaAssertionType[] = [
  'STATUS', 'JSON_PATH_EXISTS', 'JSON_PATH_EQUALS', 'JSON_PATH_CONTAINS', 'HEADER_CONTAINS', 'MAX_TIME_MS',
];
export const QA_ASSERTION_TYPE_KEYS: Readonly<Record<QaAssertionType, string>> = {
  STATUS: 'qaDashboard.studio.assertionTypes.STATUS',
  JSON_PATH_EXISTS: 'qaDashboard.studio.assertionTypes.JSON_PATH_EXISTS',
  JSON_PATH_EQUALS: 'qaDashboard.studio.assertionTypes.JSON_PATH_EQUALS',
  JSON_PATH_CONTAINS: 'qaDashboard.studio.assertionTypes.JSON_PATH_CONTAINS',
  HEADER_CONTAINS: 'qaDashboard.studio.assertionTypes.HEADER_CONTAINS',
  MAX_TIME_MS: 'qaDashboard.studio.assertionTypes.MAX_TIME_MS',
};
/** Which inputs each assertion type needs. */
export const QA_ASSERTION_INPUTS: Readonly<Record<QaAssertionType, { path: boolean; name: boolean; expected: boolean }>> = {
  STATUS: { path: false, name: false, expected: true },
  JSON_PATH_EXISTS: { path: true, name: false, expected: false },
  JSON_PATH_EQUALS: { path: true, name: false, expected: true },
  JSON_PATH_CONTAINS: { path: true, name: false, expected: true },
  HEADER_CONTAINS: { path: false, name: true, expected: true },
  MAX_TIME_MS: { path: false, name: false, expected: true },
};
export const QA_ASSERTION_DEFAULT_STATUS = '200';

/** The list: page size and the empty query. */
export const QA_STUDIO_PAGE_SIZE = 20;
export const QA_STUDIO_EMPTY_QUERY: Readonly<QaStudioQuery> = { q: '', module: '', subModule: '', suite: '', enabled: '' };

/** The path field's suggestions: how many, and how many characters before it asks. */
export const QA_STUDIO_SUGGESTION_COUNT = 8;
export const QA_STUDIO_SUGGESTION_MIN_CHARS = 2;
export const QA_STUDIO_SUGGESTION_DEBOUNCE_MS = 300;

/** A new case before anything is typed. */
export const QA_STUDIO_EMPTY_FORM: Readonly<QaStudioFormValue> = {
  name: '',
  description: '',
  module: 'platform',
  subModule: '',
  suite: 'REGRESSION',
  enabled: true,
  auth: 'QA_ADMIN',
  method: 'GET',
  path: '/api/',
  query: [],
  headers: [],
  body: '',
  assertions: [{ type: 'STATUS', path: '', name: '', expected: QA_ASSERTION_DEFAULT_STATUS }],
};

/** What the user sees for each rule: the translation key (the values come with the issue). */
export const QA_STUDIO_ISSUE_KEYS = {
  required: 'qaDashboard.studio.errors.required',
  nameLength: 'qaDashboard.studio.errors.nameLength',
  descriptionLength: 'qaDashboard.studio.errors.descriptionLength',
  subModuleLength: 'qaDashboard.studio.errors.subModuleLength',
  pathPrefix: 'qaDashboard.studio.errors.pathPrefix',
  pathDotDot: 'qaDashboard.studio.errors.pathDotDot',
  pathScheme: 'qaDashboard.studio.errors.pathScheme',
  pathQuery: 'qaDashboard.studio.errors.pathQuery',
  pathLength: 'qaDashboard.studio.errors.pathLength',
  tooMany: 'qaDashboard.studio.errors.tooMany',
  headerForbidden: 'qaDashboard.studio.errors.headerForbidden',
  headerInvalid: 'qaDashboard.studio.errors.headerInvalid',
  queryInvalid: 'qaDashboard.studio.errors.queryInvalid',
  duplicate: 'qaDashboard.studio.errors.duplicate',
  secret: 'qaDashboard.studio.errors.secret',
  valueLength: 'qaDashboard.studio.errors.valueLength',
  bodySize: 'qaDashboard.studio.errors.bodySize',
  bodyJson: 'qaDashboard.studio.errors.bodyJson',
  bodyNotAllowed: 'qaDashboard.studio.errors.bodyNotAllowed',
  statusRequired: 'qaDashboard.studio.errors.statusRequired',
  statusCodes: 'qaDashboard.studio.errors.statusCodes',
  jsonPath: 'qaDashboard.studio.errors.jsonPath',
  expectedLength: 'qaDashboard.studio.errors.expectedLength',
  timeRange: 'qaDashboard.studio.errors.timeRange',
} as const;

export const QA_STUDIO_MESSAGE_KEYS = {
  saved: 'qaDashboard.studio.editor.saved',
  saveFailed: 'qaDashboard.studio.editor.saveFailed',
  deleteTitle: 'qaDashboard.studio.list.deleteTitle',
  deleteMessage: 'qaDashboard.studio.list.deleteMessage',
  deleteConfirm: 'qaDashboard.studio.list.deleteConfirm',
  deleted: 'qaDashboard.studio.list.deleted',
  deleteFailed: 'qaDashboard.studio.list.deleteFailed',
  duplicated: 'qaDashboard.studio.list.duplicated',
  duplicateFailed: 'qaDashboard.studio.list.duplicateFailed',
  enableFailed: 'qaDashboard.studio.list.enableFailed',
  copySuffix: 'qaDashboard.studio.list.copySuffix',
  discardTitle: 'qaDashboard.studio.editor.discardTitle',
  discardMessage: 'qaDashboard.studio.editor.discardMessage',
  discardConfirm: 'qaDashboard.studio.editor.discardConfirm',
} as const;

/** HTTP status the API answers a stale edit with. */
export const QA_STUDIO_CONFLICT_STATUS = 409;
