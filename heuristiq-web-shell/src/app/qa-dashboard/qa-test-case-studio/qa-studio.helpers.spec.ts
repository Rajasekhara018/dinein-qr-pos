import { studioCase } from '../qa-dashboard.fixtures';
import { QA_STUDIO_ISSUE_KEYS as K, QA_STUDIO_LIMITS } from './qa-studio.constants';
import {
  assertionRowIssue,
  assertionsIssue,
  bodyAllowed,
  bodyIssue,
  caseToForm,
  descriptionIssue,
  duplicateInput,
  emptyAssertionRow,
  emptyStudioForm,
  emptyStudioQuery,
  formToInput,
  formatJson,
  fromApiAssertion,
  hasActiveStudioQuery,
  isForbiddenHeader,
  keyValueRowIssue,
  keyValueTableIssue,
  looksLikeSecret,
  nameIssue,
  normalizeStudioCase,
  normalizeStudioItem,
  normalizeStudioPage,
  parseStatusCodes,
  pathIssue,
  studioFormIssues,
  studioParams,
  subModuleIssue,
  toApiAssertion,
  toKeyValueRecord,
  toKeyValueRows,
  utf8Length,
  valueIssue,
} from './qa-studio.helpers';
import { QaAssertionRow, QaStudioFormValue } from './qa-studio.models';

const row = (over: Partial<QaAssertionRow>): QaAssertionRow => ({ type: 'STATUS', path: '', name: '', expected: '200', ...over });
const validForm = (over: Partial<QaStudioFormValue> = {}): QaStudioFormValue => ({ ...emptyStudioForm(), name: 'Block user', path: '/api/v1/uam/users/1', ...over });

describe('studio helpers', () => {
  describe('path', () => {
    it.each([
      ['/api/v1/uam/users', null],
      ['  /api/v1/x  ', null],
      ['', K.required],
      ['   ', K.required],
      ['/v1/uam/users', K.pathPrefix],
      ['api/v1/x', K.pathPrefix],
      ['https://host/api/v1/x', K.pathScheme],
      ['http://localhost/api/', K.pathScheme],
      ['//evil.example/api/x', K.pathScheme],
      ['javascript:alert(1)', K.pathScheme],
      ['/api/../admin', K.pathDotDot],
      ['/api/v1/%2e%2e/x', K.pathDotDot],
      ['/api/v1/x?x=1', K.pathQuery],
      ['/api/v1/x#top', K.pathQuery],
      [`/api/${'a'.repeat(300)}`, K.pathLength],
    ])('checks the path %j', (path, key) => {
      expect(pathIssue(path)?.key ?? null).toBe(key);
    });

    it('keeps names like "..foo" that are not a parent segment', () => {
      expect(pathIssue('/api/v1/..foo/x')).toBeNull();
    });
  });

  describe('headers, query and secrets', () => {
    it.each(['Authorization', 'authorization', ' COOKIE ', 'Host', 'content-length', 'X-Forwarded-For', 'x-forwarded-proto'])('refuses the header %s', (name) => {
      expect(isForbiddenHeader(name)).toBe(true);
    });

    it('allows an ordinary header', () => {
      expect(isForbiddenHeader('X-Trace-Id')).toBe(false);
      expect(isForbiddenHeader('Accept')).toBe(false);
    });

    it.each([
      ['Bearer abcdefghijklmnop', true],
      ['bearer  AbCdEf123456', true],
      ['eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.c2lnbmF0dXJl', true],
      ['-----BEGIN RSA PRIVATE KEY-----', true],
      ['Basic dXNlcjpwYXNzd29yZA==', true],
      ['hello', false],
      ['Bearer', false],
      ['a1b2c3', false],
    ])('recognises a secret in %j', (value, secret) => {
      expect(looksLikeSecret(value)).toBe(secret);
    });

    it('refuses a value that is too long, a credential, or any value under a secret-looking name', () => {
      expect(valueIssue('x', 'a'.repeat(501))?.key).toBe(K.valueLength);
      expect(valueIssue('x', 'Bearer abcdefghijklmnop')?.key).toBe(K.secret);
      expect(valueIssue('password', 'hunter2')?.key).toBe(K.secret);
      expect(valueIssue('X-Api-Key', 'abc')?.key).toBe(K.secret);
      expect(valueIssue('accessToken', 'abc')?.key).toBe(K.secret);
      expect(valueIssue('password', '  ')).toBeNull();
      expect(valueIssue('passenger', 'abc')).toBeNull();
      expect(valueIssue('pin', '1234')?.key).toBe(K.secret);
      expect(valueIssue('shipping', '1234')).toBeNull();
    });

    it('checks a header row: needs a name, a valid one, not a forbidden one, no secret', () => {
      expect(keyValueRowIssue({ name: '', value: '' }, 'headers')).toBeNull();
      expect(keyValueRowIssue({ name: '', value: 'x' }, 'headers')?.key).toBe(K.required);
      expect(keyValueRowIssue({ name: 'Bad Name', value: 'x' }, 'headers')?.key).toBe(K.headerInvalid);
      expect(keyValueRowIssue({ name: 'a'.repeat(101), value: 'x' }, 'headers')?.key).toBe(K.headerInvalid);
      expect(keyValueRowIssue({ name: 'Authorization', value: 'x' }, 'headers')).toEqual({ key: K.headerForbidden, params: { name: 'Authorization' } });
      expect(keyValueRowIssue({ name: 'X-Trace', value: 'Bearer abcdefghijklmnop' }, 'headers')?.key).toBe(K.secret);
      expect(keyValueRowIssue({ name: 'X-Trace', value: 'abc' }, 'headers')).toBeNull();
    });

    it('checks a query row: a plain parameter name', () => {
      expect(keyValueRowIssue({ name: 'page[size]', value: '10' }, 'query')).toBeNull();
      expect(keyValueRowIssue({ name: 'a b', value: '1' }, 'query')?.key).toBe(K.queryInvalid);
      expect(keyValueRowIssue({ name: 'token', value: 'abc' }, 'query')?.key).toBe(K.secret);
    });

    it('limits the rows of a table and refuses a name twice (header names ignore case)', () => {
      const many = Array.from({ length: 21 }, (_, i) => ({ name: `p${i}`, value: '1' }));
      expect(keyValueTableIssue(many, 'query')).toEqual({ key: K.tooMany, params: { max: 20 } });
      expect(keyValueTableIssue(many.slice(0, 11), 'headers')).toEqual({ key: K.tooMany, params: { max: 10 } });
      expect(keyValueTableIssue(many.slice(0, 20), 'query')).toBeNull();
      expect(keyValueTableIssue([{ name: 'A', value: '1' }, { name: 'a', value: '2' }], 'headers')?.key).toBe(K.duplicate);
      expect(keyValueTableIssue([{ name: 'A', value: '1' }, { name: 'a', value: '2' }], 'query')).toBeNull();
      expect(keyValueTableIssue([{ name: 'a', value: '1' }, { name: 'a', value: '2' }], 'query')?.key).toBe(K.duplicate);
      expect(keyValueTableIssue([{ name: '', value: '' }, { name: '', value: '' }], 'headers')).toBeNull();
      expect(keyValueTableIssue([{ name: '', value: 'x' }, { name: '', value: 'y' }], 'headers')).toBeNull();
    });

    it('turns rows into a record and back, dropping empty rows', () => {
      expect(toKeyValueRecord([{ name: ' a ', value: '1' }, { name: '', value: '' }, { name: 'b', value: '' }])).toEqual({ a: '1', b: '' });
      expect(toKeyValueRows({ a: '1', b: '2' })).toEqual([{ name: 'a', value: '1' }, { name: 'b', value: '2' }]);
      expect(toKeyValueRows(null)).toEqual([]);
    });
  });

  describe('body', () => {
    it('carries a body only with POST, PUT and PATCH', () => {
      expect(bodyAllowed('POST')).toBe(true);
      expect(bodyAllowed('PUT')).toBe(true);
      expect(bodyAllowed('PATCH')).toBe(true);
      expect(bodyAllowed('GET')).toBe(false);
      expect(bodyAllowed('DELETE')).toBe(false);
    });

    it('accepts an empty body, valid JSON and refuses the rest', () => {
      expect(bodyIssue('', 'GET')).toBeNull();
      expect(bodyIssue('   ', 'POST')).toBeNull();
      expect(bodyIssue('{"a":1}', 'POST')).toBeNull();
      expect(bodyIssue('[1,2]', 'PUT')).toBeNull();
      expect(bodyIssue('{"a":1}', 'GET')?.key).toBe(K.bodyNotAllowed);
      expect(bodyIssue('{a:1}', 'POST')?.key).toBe(K.bodyJson);
      expect(bodyIssue('not json', 'PATCH')?.key).toBe(K.bodyJson);
    });

    it('counts the size in bytes, not characters', () => {
      expect(utf8Length('abc')).toBe(3);
      expect(utf8Length('€')).toBe(3);
      const big = JSON.stringify({ a: 'x'.repeat(16 * 1024) });
      expect(bodyIssue(big, 'POST')).toEqual({ key: K.bodySize, params: { max: 16 } });
      // 6000 euro signs are 6000 characters but 18000 bytes.
      expect(bodyIssue(JSON.stringify({ a: '€'.repeat(6000) }), 'POST')?.key).toBe(K.bodySize);
    });

    it('refuses a secret anywhere in the body', () => {
      expect(bodyIssue('{"password":"hunter2"}', 'POST')?.key).toBe(K.secret);
      expect(bodyIssue('{"user":{"accessToken":"abc"}}', 'POST')?.key).toBe(K.secret);
      expect(bodyIssue('{"list":[{"note":"Bearer abcdefghijklmnop"}]}', 'POST')?.key).toBe(K.secret);
      expect(bodyIssue('{"password":""}', 'POST')).toBeNull();
      expect(bodyIssue('{"password":123}', 'POST')).toBeNull();
      expect(bodyIssue('{"name":"Asha"}', 'POST')).toBeNull();
      expect(bodyIssue('"Bearer abcdefghijklmnop"', 'POST')?.key).toBe(K.secret);
      expect(bodyIssue('null', 'POST')).toBeNull();
    });

    it('formats valid JSON and gives null for the rest', () => {
      expect(formatJson('{"a":[1,2]}')).toBe('{\n  "a": [\n    1,\n    2\n  ]\n}');
      expect(formatJson('{a')).toBeNull();
    });
  });

  describe('basics', () => {
    it('checks the name, description and sub-module lengths', () => {
      expect(nameIssue('')?.key).toBe(K.required);
      expect(nameIssue('ab')?.key).toBe(K.nameLength);
      expect(nameIssue('x'.repeat(151))?.key).toBe(K.nameLength);
      expect(nameIssue('  abc  ')).toBeNull();
      expect(descriptionIssue('x'.repeat(501))).toEqual({ key: K.descriptionLength, params: { max: 500 } });
      expect(descriptionIssue('x'.repeat(500))).toBeNull();
      expect(subModuleIssue('x'.repeat(101))?.key).toBe(K.subModuleLength);
      expect(subModuleIssue('users')).toBeNull();
    });
  });

  describe('assertions', () => {
    it.each([
      ['200', [200]],
      ['200, 201', [200, 201]],
      ['200;201 404', [200, 201, 404]],
      ['200, 200', [200]],
      ['100,599', [100, 599]],
      ['200,201,202,203,204', [200, 201, 202, 203, 204]],
    ])('reads the status codes %j', (text, codes) => {
      expect(parseStatusCodes(text)).toEqual(codes);
    });

    it.each(['', '  ', '99', '600', '20', '2000', 'abc', '200, x', '200,201,202,203,204,205', '2.5'])('refuses the status codes %j', (text) => {
      expect(parseStatusCodes(text)).toBeNull();
    });

    it('checks each type of assertion row', () => {
      expect(assertionRowIssue(row({}))).toBeNull();
      expect(assertionRowIssue(row({ expected: '99' }))?.key).toBe(K.statusCodes);
      expect(assertionRowIssue(row({ type: 'MAX_TIME_MS', expected: '2000' }))).toBeNull();
      expect(assertionRowIssue(row({ type: 'MAX_TIME_MS', expected: '0' }))?.key).toBe(K.timeRange);
      expect(assertionRowIssue(row({ type: 'MAX_TIME_MS', expected: '60001' }))?.key).toBe(K.timeRange);
      expect(assertionRowIssue(row({ type: 'MAX_TIME_MS', expected: '1.5' }))?.key).toBe(K.timeRange);
      expect(assertionRowIssue(row({ type: 'MAX_TIME_MS', expected: 'x' }))?.key).toBe(K.timeRange);
      expect(assertionRowIssue(row({ type: 'JSON_PATH_EXISTS', path: '$.id' }))).toBeNull();
      expect(assertionRowIssue(row({ type: 'JSON_PATH_EXISTS', path: 'id' }))?.key).toBe(K.jsonPath);
      expect(assertionRowIssue(row({ type: 'JSON_PATH_EXISTS', path: `$${'a'.repeat(200)}` }))?.key).toBe(K.jsonPath);
      expect(assertionRowIssue(row({ type: 'JSON_PATH_EQUALS', path: '$.id', expected: '42' }))).toBeNull();
      expect(assertionRowIssue(row({ type: 'JSON_PATH_EQUALS', path: '$.id', expected: '' }))?.key).toBe(K.required);
      expect(assertionRowIssue(row({ type: 'JSON_PATH_CONTAINS', path: '$.id', expected: 'x'.repeat(501) }))?.key).toBe(K.expectedLength);
      expect(assertionRowIssue(row({ type: 'JSON_PATH_CONTAINS', path: '$.id', expected: 'Bearer abcdefghijklmnop' }))?.key).toBe(K.secret);
      expect(assertionRowIssue(row({ type: 'HEADER_CONTAINS', name: 'Content-Type', expected: 'json' }))).toBeNull();
      expect(assertionRowIssue(row({ type: 'HEADER_CONTAINS', name: '', expected: 'json' }))?.key).toBe(K.headerInvalid);
      expect(assertionRowIssue(row({ type: 'HEADER_CONTAINS', name: 'Bad Name', expected: 'json' }))?.key).toBe(K.headerInvalid);
      expect(assertionRowIssue(row({ type: 'HEADER_CONTAINS', name: 'Content-Type', expected: '' }))?.key).toBe(K.required);
      expect(assertionRowIssue(row({ type: 'NOPE' as never }))?.key).toBe(K.required);
    });

    it('needs a status check and at most 20 checks', () => {
      expect(assertionsIssue([row({})])).toBeNull();
      expect(assertionsIssue([])?.key).toBe(K.statusRequired);
      expect(assertionsIssue([row({ type: 'MAX_TIME_MS', expected: '10' })])?.key).toBe(K.statusRequired);
      expect(assertionsIssue(Array.from({ length: 21 }, () => row({})))).toEqual({ key: K.tooMany, params: { max: 20 } });
      expect(assertionsIssue(Array.from({ length: 20 }, () => row({})))).toBeNull();
    });

    it('turns every type of row into the shape the API stores, and back', () => {
      expect(toApiAssertion(row({ expected: '200, 201' }))).toEqual({ type: 'STATUS', expected: [200, 201] });
      expect(toApiAssertion(row({ expected: 'x' }))).toEqual({ type: 'STATUS', expected: [] });
      expect(toApiAssertion(row({ type: 'MAX_TIME_MS', expected: '2000' }))).toEqual({ type: 'MAX_TIME_MS', expected: 2000 });
      expect(toApiAssertion(row({ type: 'JSON_PATH_EXISTS', path: ' $.id ', expected: 'ignored' }))).toEqual({ type: 'JSON_PATH_EXISTS', path: '$.id' });
      expect(toApiAssertion(row({ type: 'HEADER_CONTAINS', name: ' Content-Type ', expected: 'json' }))).toEqual({ type: 'HEADER_CONTAINS', name: 'Content-Type', expected: 'json' });
      expect(toApiAssertion(row({ type: 'JSON_PATH_EQUALS', path: '$.id', expected: '42' }))).toEqual({ type: 'JSON_PATH_EQUALS', path: '$.id', expected: '42' });
      expect(toApiAssertion(row({ type: 'JSON_PATH_CONTAINS', path: '$.id', expected: 'a' }))).toEqual({ type: 'JSON_PATH_CONTAINS', path: '$.id', expected: 'a' });

      expect(fromApiAssertion({ type: 'STATUS', expected: [200, 201] })).toEqual({ type: 'STATUS', path: '', name: '', expected: '200, 201' });
      expect(fromApiAssertion({ type: 'MAX_TIME_MS', expected: 2000 }).expected).toBe('2000');
      expect(fromApiAssertion({ type: 'JSON_PATH_EXISTS', path: '$.id' })).toEqual({ type: 'JSON_PATH_EXISTS', path: '$.id', name: '', expected: '' });
      expect(fromApiAssertion({ type: 'HEADER_CONTAINS', name: 'X', expected: null }).expected).toBe('');
    });

    it('starts a new assertion row as a 200 status check', () => {
      expect(emptyAssertionRow()).toEqual({ type: 'STATUS', path: '', name: '', expected: '200' });
    });
  });

  describe('form and case', () => {
    it('gives a fresh form each time, with one status check', () => {
      const a = emptyStudioForm();
      a.assertions[0].expected = '500';
      a.query.push({ name: 'x', value: 'y' });
      const b = emptyStudioForm();
      expect(b.assertions[0].expected).toBe('200');
      expect(b.query).toEqual([]);
      expect(studioFormIssues(validForm())).toEqual([]);
    });

    it('turns a case into the form and the form into the request', () => {
      const form = caseToForm(studioCase('a'));
      expect(form.query).toEqual([{ name: 'dryRun', value: 'true' }]);
      expect(form.assertions.map((r) => r.type)).toEqual(['STATUS', 'JSON_PATH_EQUALS', 'MAX_TIME_MS']);
      expect(form.assertions[0].expected).toBe('200, 201');
      const input = formToInput(form, 3);
      expect(input).toMatchObject({ name: 'Block user rejects a missing token', method: 'POST', body: '{"reason":"test"}', version: 3, query: { dryRun: 'true' } });
      expect(input.assertions[0]).toEqual({ type: 'STATUS', expected: [200, 201] });
      expect('version' in formToInput(form)).toBe(false);
    });

    it('sends no body for a method that carries none, and trims the text', () => {
      const input = formToInput(validForm({ method: 'GET', body: '{"a":1}', name: '  Block  ', path: ' /api/x ', subModule: ' users ', description: ' d ' }));
      expect(input.body).toBeNull();
      expect(input).toMatchObject({ name: 'Block', path: '/api/x', subModule: 'users', description: 'd' });
      expect(formToInput(validForm({ method: 'POST', body: '   ' })).body).toBeNull();
    });

    it('fills what an older case lacks', () => {
      const form = caseToForm({ ...studioCase('a'), description: undefined as never, subModule: undefined as never, body: null, assertions: undefined as never });
      expect(form.description).toBe('');
      expect(form.subModule).toBe('');
      expect(form.body).toBe('');
      expect(form.assertions).toEqual([]);
    });

    it('lists every rule the form breaks, in the order of the page', () => {
      const bad = validForm({
        name: 'x', path: 'no', method: 'GET', body: '{"a":1}',
        headers: [{ name: 'Host', value: 'x' }], query: [{ name: 'a b', value: '1' }],
        assertions: [row({ type: 'MAX_TIME_MS', expected: '0' })],
      });
      expect(studioFormIssues(bad).map((i) => i.key)).toEqual([
        K.nameLength, K.pathPrefix, K.queryInvalid, K.headerForbidden, K.bodyNotAllowed, K.statusRequired, K.timeRange,
      ]);
    });

    it('copies a case switched off under a name that says it is a copy, within the name limit', () => {
      const copy = duplicateInput(studioCase('a'), '(copy)');
      expect(copy.name).toBe('Block user rejects a missing token (copy)');
      expect(copy.enabled).toBe(false);
      expect(copy.assertions).toHaveLength(3);
      expect('version' in copy).toBe(false);
      const long = duplicateInput(studioCase('a', { name: 'x'.repeat(150) }), '(copy)');
      expect(long.name.length).toBeLessThanOrEqual(QA_STUDIO_LIMITS.nameMax);
      expect(long.name.endsWith(' (copy)')).toBe(true);
    });
  });

  describe('list', () => {
    it('gives a fresh query and knows when it narrows the list', () => {
      const a = emptyStudioQuery({ suite: 'SMOKE' });
      expect(a.suite).toBe('SMOKE');
      expect(emptyStudioQuery().suite).toBe('');
      expect(hasActiveStudioQuery(emptyStudioQuery())).toBe(false);
      expect(hasActiveStudioQuery(emptyStudioQuery({ q: '  ' }))).toBe(false);
      expect(hasActiveStudioQuery(emptyStudioQuery({ enabled: 'false' }))).toBe(true);
    });

    it('sends only what is set and clamps the page request', () => {
      expect(studioParams(emptyStudioQuery({ q: ' x ', module: 'uam', enabled: 'true' }), 1, 500)).toEqual({ q: 'x', module: 'uam', enabled: 'true', page: 1, size: 50 });
      expect(studioParams(emptyStudioQuery(), -1, 20)).toEqual({ page: 0, size: 20 });
    });

    it('fills what the API leaves out', () => {
      expect(normalizeStudioItem({ id: '1' })).toMatchObject({ id: '1', tcId: '', enabled: false, assertionCount: 0, version: 0 });
      const page = normalizeStudioPage({ items: [{ id: '1' }] as never }, 2, 20);
      expect(page).toMatchObject({ page: 2, size: 20, totalElements: 1, totalPages: 1 });
      expect(normalizeStudioPage(null, 0, 20)).toMatchObject({ items: [], totalPages: 0 });
      expect(normalizeStudioCase(null)).toMatchObject({ id: '', method: 'GET', auth: 'NONE', suite: 'REGRESSION', query: {}, headers: {}, body: null, assertions: [] });
      expect(normalizeStudioCase({ id: '1', name: 'n' }).name).toBe('n');
    });
  });
});
