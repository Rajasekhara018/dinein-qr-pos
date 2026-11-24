/** Shapes of the Test Case Studio API (Rider_ConfigService, /api/v1/config/qa/studio), section 8 of docs/QA_DASHBOARD_DESIGN.md. */

export type QaStudioAuth = 'NONE' | 'QA_ADMIN' | 'CUSTOMER_A' | 'CUSTOMER_B';
export type QaStudioMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
export type QaStudioSuite = 'REGRESSION' | 'SMOKE';
export type QaAssertionType = 'STATUS' | 'JSON_PATH_EXISTS' | 'JSON_PATH_EQUALS' | 'JSON_PATH_CONTAINS' | 'HEADER_CONTAINS' | 'MAX_TIME_MS';

/** One check on the response, as the API stores it. */
export interface QaStudioAssertion {
  type: QaAssertionType;
  /** JSON_PATH_*: where in the response body, for example `$.responseObject.id`. */
  path?: string | null;
  /** HEADER_CONTAINS: the response header. */
  name?: string | null;
  /** STATUS: 1 to 5 codes; MAX_TIME_MS: milliseconds; the others: text. */
  expected?: number[] | number | string | null;
}

/** What the editor sends to create or replace a case. */
export interface QaStudioInput {
  name: string;
  description: string;
  module: string;
  subModule: string;
  suite: QaStudioSuite;
  enabled: boolean;
  auth: QaStudioAuth;
  method: QaStudioMethod;
  path: string;
  query: Record<string, string>;
  headers: Record<string, string>;
  body: string | null;
  assertions: QaStudioAssertion[];
  /** The version the edit started from (optimistic lock); absent on create. */
  version?: number;
}

export interface QaStudioCase extends QaStudioInput {
  id: string;
  /** `HQ_ST_001`: assigned by the server, never changes. */
  tcId: string;
  version: number;
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

/** A line of the list: the case without its body and assertions, with how many checks it has. */
export interface QaStudioCaseItem {
  id: string;
  tcId: string;
  name: string;
  description: string;
  module: string;
  subModule: string;
  suite: QaStudioSuite | string;
  enabled: boolean;
  auth: QaStudioAuth | string;
  method: QaStudioMethod | string;
  path: string;
  assertionCount: number;
  version: number;
  updatedBy: string;
  updatedAt: string;
}

export interface QaStudioPage {
  items: QaStudioCaseItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** What the list toolbar sends ('' = not filtered). */
export interface QaStudioQuery {
  q: string;
  module: string;
  subModule: string;
  suite: string;
  /** '' = both, 'true' = enabled only, 'false' = disabled only. */
  enabled: '' | 'true' | 'false';
}

/** A key and value row of the query and header tables. */
export interface QaKeyValueRow {
  name: string;
  value: string;
}

/** An assertion row of the editor: every value as the text the user typed. */
export interface QaAssertionRow {
  type: QaAssertionType;
  path: string;
  name: string;
  expected: string;
}

/** The editor's form values. */
export interface QaStudioFormValue {
  name: string;
  description: string;
  module: string;
  subModule: string;
  suite: QaStudioSuite;
  enabled: boolean;
  auth: QaStudioAuth;
  method: QaStudioMethod;
  path: string;
  query: QaKeyValueRow[];
  headers: QaKeyValueRow[];
  body: string;
  assertions: QaAssertionRow[];
}

/** A rule the form broke: the translation key of the message and the values it needs. */
export interface QaStudioIssue {
  key: string;
  params?: Record<string, string | number>;
}
