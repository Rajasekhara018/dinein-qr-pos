import { clampPage } from '../qa-dashboard.helpers';
import {
  QA_ASSERTION_DEFAULT_STATUS,
  QA_ASSERTION_INPUTS,
  QA_STUDIO_BODY_METHODS,
  QA_STUDIO_EMPTY_FORM,
  QA_STUDIO_EMPTY_QUERY,
  QA_STUDIO_FORBIDDEN_HEADERS,
  QA_STUDIO_FORBIDDEN_HEADER_PREFIXES,
  QA_STUDIO_HEADER_NAME_PATTERN,
  QA_STUDIO_ISSUE_KEYS,
  QA_STUDIO_LIMITS,
  QA_STUDIO_PATH_PREFIX,
  QA_STUDIO_QUERY_NAME_PATTERN,
  QA_STUDIO_SECRET_NAME_PATTERN,
  QA_STUDIO_SECRET_VALUE_PATTERNS,
} from './qa-studio.constants';
import {
  QaAssertionRow,
  QaKeyValueRow,
  QaStudioAssertion,
  QaStudioCase,
  QaStudioCaseItem,
  QaStudioFormValue,
  QaStudioInput,
  QaStudioIssue,
  QaStudioMethod,
  QaStudioPage,
  QaStudioQuery,
} from './qa-studio.models';

const L = QA_STUDIO_LIMITS;
const K = QA_STUDIO_ISSUE_KEYS;

const issue = (key: string, params?: Record<string, string | number>): QaStudioIssue => (params ? { key, params } : { key });

// ---- request: path, headers, query, body ----

/** The first rule a request path breaks, or null: it must stay on the platform's own API. */
export function pathIssue(path: string): QaStudioIssue | null {
  const text = path.trim();
  if (!text) return issue(K.required);
  if (text.length > L.pathMax) return issue(K.pathLength, { max: L.pathMax });
  if (/^[A-Za-z][A-Za-z0-9+.-]*:/.test(text) || text.startsWith('//')) return issue(K.pathScheme);
  if (!text.startsWith(QA_STUDIO_PATH_PREFIX)) return issue(K.pathPrefix);
  if (text.split('/').includes('..') || text.includes('%2e%2e') || text.includes('%2E%2E')) return issue(K.pathDotDot);
  if (text.includes('?') || text.includes('#')) return issue(K.pathQuery);
  return null;
}

/** True when a header is one a case may not set. */
export function isForbiddenHeader(name: string): boolean {
  const lower = name.trim().toLowerCase();
  return QA_STUDIO_FORBIDDEN_HEADERS.includes(lower) || QA_STUDIO_FORBIDDEN_HEADER_PREFIXES.some((p) => lower.startsWith(p));
}

/** True when a text looks like a credential: a bearer token, a JWT, a key. */
export function looksLikeSecret(value: string): boolean {
  return QA_STUDIO_SECRET_VALUE_PATTERNS.some((pattern) => pattern.test(value));
}

/** The problem with a header or query value, or null: not too long, and no credential in it. */
export function valueIssue(name: string, value: string): QaStudioIssue | null {
  if (value.length > L.valueMax) return issue(K.valueLength, { max: L.valueMax });
  if (looksLikeSecret(value)) return issue(K.secret);
  if (value.trim() && QA_STUDIO_SECRET_NAME_PATTERN.test(name)) return issue(K.secret);
  return null;
}

/** The problem with one row of the headers or query table, or null. A row with nothing in it is not a problem: it is dropped. */
export function keyValueRowIssue(row: QaKeyValueRow, kind: 'query' | 'headers'): QaStudioIssue | null {
  const name = row.name.trim();
  if (!name && !row.value.trim()) return null;
  if (!name) return issue(K.required);
  if (kind === 'headers') {
    if (name.length > L.headerNameMax || !QA_STUDIO_HEADER_NAME_PATTERN.test(name)) return issue(K.headerInvalid);
    if (isForbiddenHeader(name)) return issue(K.headerForbidden, { name });
  } else if (name.length > L.headerNameMax || !QA_STUDIO_QUERY_NAME_PATTERN.test(name)) {
    return issue(K.queryInvalid);
  }
  return valueIssue(name, row.value);
}

/** The problem with the whole table: more rows than allowed, or a name twice (header names ignore case). */
export function keyValueTableIssue(rows: readonly QaKeyValueRow[], kind: 'query' | 'headers'): QaStudioIssue | null {
  const used = rows.filter((r) => r.name.trim() || r.value.trim());
  const max = kind === 'query' ? L.queryMax : L.headersMax;
  if (used.length > max) return issue(K.tooMany, { max });
  const seen = new Set<string>();
  for (const row of used) {
    const key = kind === 'headers' ? row.name.trim().toLowerCase() : row.name.trim();
    if (!key) continue;
    if (seen.has(key)) return issue(K.duplicate, { name: row.name.trim() });
    seen.add(key);
  }
  return null;
}

/** A row list as the record the API stores: empty rows are dropped, names and values trimmed at the edges. */
export function toKeyValueRecord(rows: readonly QaKeyValueRow[]): Record<string, string> {
  const record: Record<string, string> = {};
  rows.forEach((row) => {
    const name = row.name.trim();
    if (name) record[name] = row.value;
  });
  return record;
}

export function toKeyValueRows(record: Record<string, string> | null | undefined): QaKeyValueRow[] {
  return Object.entries(record ?? {}).map(([name, value]) => ({ name, value: String(value ?? '') }));
}

/** True when the method carries a body. */
export function bodyAllowed(method: QaStudioMethod | string): boolean {
  return (QA_STUDIO_BODY_METHODS as readonly string[]).includes(method);
}

/** The size of a text in bytes (the limit is in bytes, not characters). */
export function utf8Length(text: string): number {
  return new TextEncoder().encode(text).length;
}

function jsonHoldsSecret(node: unknown): boolean {
  if (typeof node === 'string') return looksLikeSecret(node);
  if (Array.isArray(node)) return node.some(jsonHoldsSecret);
  if (node && typeof node === 'object') {
    return Object.entries(node as Record<string, unknown>).some(
      ([key, value]) => (typeof value === 'string' && value.trim() !== '' && QA_STUDIO_SECRET_NAME_PATTERN.test(key)) || jsonHoldsSecret(value),
    );
  }
  return false;
}

/** The problem with the body, or null: only for methods that carry one, valid JSON, at most 16 KB, no secret inside. */
export function bodyIssue(text: string, method: QaStudioMethod | string): QaStudioIssue | null {
  if (!text.trim()) return null;
  if (!bodyAllowed(method)) return issue(K.bodyNotAllowed);
  if (utf8Length(text) > L.bodyMaxBytes) return issue(K.bodySize, { max: L.bodyMaxBytes / 1024 });
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return issue(K.bodyJson);
  }
  return jsonHoldsSecret(parsed) ? issue(K.secret) : null;
}

/** The body laid out with two-space indentation, or null when it is not valid JSON. */
export function formatJson(text: string): string | null {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return null;
  }
}

// ---- basics ----

export function nameIssue(name: string): QaStudioIssue | null {
  const length = name.trim().length;
  if (length === 0) return issue(K.required);
  return length < L.nameMin || length > L.nameMax ? issue(K.nameLength, { min: L.nameMin, max: L.nameMax }) : null;
}

export function descriptionIssue(text: string): QaStudioIssue | null {
  return text.length > L.descriptionMax ? issue(K.descriptionLength, { max: L.descriptionMax }) : null;
}

export function subModuleIssue(text: string): QaStudioIssue | null {
  return text.length > L.subModuleMax ? issue(K.subModuleLength, { max: L.subModuleMax }) : null;
}

// ---- assertions ----

/** "200, 201" as codes: 1 to 5 distinct codes, each 100 to 599; null when the text is not that. */
export function parseStatusCodes(text: string): number[] | null {
  const parts = text.split(/[\s,;]+/).filter(Boolean);
  if (parts.length === 0) return null;
  const codes: number[] = [];
  for (const part of parts) {
    if (!/^\d{3}$/.test(part)) return null;
    const code = Number(part);
    if (code < L.statusMin || code > L.statusMax) return null;
    if (!codes.includes(code)) codes.push(code);
  }
  return codes.length <= L.statusCodesMax ? codes : null;
}

/** The first problem of an assertion row, or null: what is needed depends on its type. */
export function assertionRowIssue(row: QaAssertionRow): QaStudioIssue | null {
  const needs = QA_ASSERTION_INPUTS[row.type];
  if (!needs) return issue(K.required);
  if (row.type === 'STATUS') return parseStatusCodes(row.expected) ? null : issue(K.statusCodes, { max: L.statusCodesMax });
  if (row.type === 'MAX_TIME_MS') {
    const ms = Number(row.expected);
    return /^\d+$/.test(row.expected.trim()) && ms >= L.timeMin && ms <= L.timeMax ? null : issue(K.timeRange, { min: L.timeMin, max: L.timeMax });
  }
  if (needs.path) {
    const path = row.path.trim();
    if (!path.startsWith('$') || path.length > L.jsonPathMax) return issue(K.jsonPath, { max: L.jsonPathMax });
  }
  if (needs.name) {
    const name = row.name.trim();
    if (!name || !QA_STUDIO_HEADER_NAME_PATTERN.test(name)) return issue(K.headerInvalid);
  }
  if (needs.expected) {
    if (!row.expected.trim()) return issue(K.required);
    if (row.expected.length > L.expectedTextMax) return issue(K.expectedLength, { max: L.expectedTextMax });
    if (looksLikeSecret(row.expected)) return issue(K.secret);
  }
  return null;
}

/** The problem with the list of assertions as a whole: not too many, and a status check among them. */
export function assertionsIssue(rows: readonly QaAssertionRow[]): QaStudioIssue | null {
  if (rows.length > L.assertionsMax) return issue(K.tooMany, { max: L.assertionsMax });
  return rows.some((r) => r.type === 'STATUS') ? null : issue(K.statusRequired);
}

/** An assertion row in the shape the API stores. */
export function toApiAssertion(row: QaAssertionRow): QaStudioAssertion {
  switch (row.type) {
    case 'STATUS':
      return { type: 'STATUS', expected: parseStatusCodes(row.expected) ?? [] };
    case 'MAX_TIME_MS':
      return { type: 'MAX_TIME_MS', expected: Number(row.expected) };
    case 'JSON_PATH_EXISTS':
      return { type: 'JSON_PATH_EXISTS', path: row.path.trim() };
    case 'HEADER_CONTAINS':
      return { type: 'HEADER_CONTAINS', name: row.name.trim(), expected: row.expected };
    default:
      return { type: row.type, path: row.path.trim(), expected: row.expected };
  }
}

/** An assertion from the API as the text the editor shows. */
export function fromApiAssertion(assertion: QaStudioAssertion): QaAssertionRow {
  const expected = assertion.expected;
  return {
    type: assertion.type,
    path: assertion.path ?? '',
    name: assertion.name ?? '',
    expected: Array.isArray(expected) ? expected.join(', ') : expected === null || expected === undefined ? '' : String(expected),
  };
}

export function emptyAssertionRow(): QaAssertionRow {
  return { type: 'STATUS', path: '', name: '', expected: QA_ASSERTION_DEFAULT_STATUS };
}

// ---- the form and the case ----

/** A fresh form for a new case (never share the constant: the editor changes its copy). */
export function emptyStudioForm(): QaStudioFormValue {
  return {
    ...QA_STUDIO_EMPTY_FORM,
    query: [],
    headers: [],
    assertions: QA_STUDIO_EMPTY_FORM.assertions.map((a) => ({ ...a })),
  };
}

export function caseToForm(c: QaStudioCase): QaStudioFormValue {
  return {
    name: c.name,
    description: c.description ?? '',
    module: c.module,
    subModule: c.subModule ?? '',
    suite: c.suite,
    enabled: c.enabled,
    auth: c.auth,
    method: c.method,
    path: c.path,
    query: toKeyValueRows(c.query),
    headers: toKeyValueRows(c.headers),
    body: c.body ?? '',
    assertions: (c.assertions ?? []).map(fromApiAssertion),
  };
}

/** The request to send: empty rows dropped, text trimmed, and a body only for the methods that carry one. */
export function formToInput(value: QaStudioFormValue, version?: number): QaStudioInput {
  const body = bodyAllowed(value.method) && value.body.trim() ? value.body : null;
  return {
    name: value.name.trim(),
    description: value.description.trim(),
    module: value.module,
    subModule: value.subModule.trim(),
    suite: value.suite,
    enabled: value.enabled,
    auth: value.auth,
    method: value.method,
    path: value.path.trim(),
    query: toKeyValueRecord(value.query),
    headers: toKeyValueRecord(value.headers),
    body,
    assertions: value.assertions.map(toApiAssertion),
    ...(version === undefined ? {} : { version }),
  };
}

/** Every rule the form breaks, in the order of the page: the same rules the controls check one by one. */
export function studioFormIssues(value: QaStudioFormValue): QaStudioIssue[] {
  const found: Array<QaStudioIssue | null> = [
    nameIssue(value.name),
    descriptionIssue(value.description),
    subModuleIssue(value.subModule),
    pathIssue(value.path),
    keyValueTableIssue(value.query, 'query'),
    ...value.query.map((r) => keyValueRowIssue(r, 'query')),
    keyValueTableIssue(value.headers, 'headers'),
    ...value.headers.map((r) => keyValueRowIssue(r, 'headers')),
    bodyIssue(value.body, value.method),
    assertionsIssue(value.assertions),
    ...value.assertions.map(assertionRowIssue),
  ];
  return found.filter((i): i is QaStudioIssue => i !== null);
}

/** A copy of a case for "duplicate": the same request under a name that says it is a copy, switched off until it is checked. */
export function duplicateInput(c: QaStudioCase, suffix: string): QaStudioInput {
  const copyName = `${c.name} ${suffix}`;
  const input = formToInput(caseToForm(c));
  return { ...input, name: copyName.length > L.nameMax ? `${c.name.slice(0, L.nameMax - suffix.length - 1)} ${suffix}` : copyName, enabled: false };
}

// ---- list ----

export function emptyStudioQuery(overrides: Partial<QaStudioQuery> = {}): QaStudioQuery {
  return { ...QA_STUDIO_EMPTY_QUERY, ...overrides };
}

export function hasActiveStudioQuery(query: QaStudioQuery): boolean {
  return Object.values(query).some((v) => String(v ?? '').trim() !== '');
}

/** The query parameters of the list: empty values left out, text trimmed, the page request clamped. */
export function studioParams(query: QaStudioQuery, page: number, size: number): Record<string, string | number> {
  const params: Record<string, string | number> = {};
  (Object.keys(query) as Array<keyof QaStudioQuery>).forEach((key) => {
    const value = String(query[key] ?? '').trim();
    if (value) params[key] = value;
  });
  return { ...params, ...clampPage(page, size) };
}

export function normalizeStudioItem(raw: Partial<QaStudioCaseItem>): QaStudioCaseItem {
  return {
    id: raw.id ?? '',
    tcId: raw.tcId ?? '',
    name: raw.name ?? '',
    description: raw.description ?? '',
    module: raw.module ?? '',
    subModule: raw.subModule ?? '',
    suite: raw.suite ?? '',
    enabled: raw.enabled ?? false,
    auth: raw.auth ?? '',
    method: raw.method ?? '',
    path: raw.path ?? '',
    assertionCount: raw.assertionCount ?? 0,
    version: raw.version ?? 0,
    updatedBy: raw.updatedBy ?? '',
    updatedAt: raw.updatedAt ?? '',
  };
}

export function normalizeStudioPage(raw: Partial<QaStudioPage> | null | undefined, page: number, size: number): QaStudioPage {
  const items = (raw?.items ?? []).map(normalizeStudioItem);
  return {
    items,
    page: raw?.page ?? page,
    size: raw?.size ?? size,
    totalElements: raw?.totalElements ?? items.length,
    totalPages: raw?.totalPages ?? (items.length ? 1 : 0),
  };
}

export function normalizeStudioCase(raw: Partial<QaStudioCase> | null | undefined): QaStudioCase {
  return {
    id: raw?.id ?? '',
    tcId: raw?.tcId ?? '',
    name: raw?.name ?? '',
    description: raw?.description ?? '',
    module: raw?.module ?? '',
    subModule: raw?.subModule ?? '',
    suite: raw?.suite ?? 'REGRESSION',
    enabled: raw?.enabled ?? false,
    auth: raw?.auth ?? 'NONE',
    method: raw?.method ?? 'GET',
    path: raw?.path ?? '',
    query: raw?.query ?? {},
    headers: raw?.headers ?? {},
    body: raw?.body ?? null,
    assertions: raw?.assertions ?? [],
    version: raw?.version ?? 0,
    createdBy: raw?.createdBy ?? '',
    createdAt: raw?.createdAt ?? '',
    updatedBy: raw?.updatedBy ?? '',
    updatedAt: raw?.updatedAt ?? '',
  };
}
