import { ActivatedRoute, convertToParamMap, ParamMap } from '@angular/router';
import { BehaviorSubject, EMPTY } from 'rxjs';
import { vi } from 'vitest';

import { QaDonutChart } from './qa-charts/qa-donut-chart';
import { QaLineChart } from './qa-charts/qa-line-chart';
import { QaReleaseChart } from './qa-charts/qa-release-chart';
import { QaFilterBar } from './qa-filter-bar/qa-filter-bar';
import { QaHeader } from './qa-header/qa-header';
import { QaStudioCase, QaStudioCaseItem, QaStudioPage } from './qa-test-case-studio/qa-studio.models';
import {
  QaEndpointCoverage,
  QaEndpointDetail,
  QaEndpointFilterOptions,
  QaEndpointItem,
  QaEndpointPage,
} from './qa-endpoint-inventory/qa-endpoint-inventory.models';
import {
  QaFacets,
  QaFailures,
  QaOverview,
  QaReleasePoint,
  QaTestCasePage,
  QaTestCaseRow,
  QaTestSet,
  QaTestSetExecution,
  QaTotals,
} from './qa-dashboard.models';

/** The shared pieces every QA screen is built from, to declare next to the screen under test. */
export const QA_SHARED_DECLARATIONS = [QaHeader, QaFilterBar, QaReleaseChart, QaDonutChart, QaLineChart];

export const totals = (over: Partial<QaTotals> = {}): QaTotals => ({
  total: 10, passed: 7, failed: 2, skipped: 1, successPct: 70, failurePct: 20, skippedPct: 10, avgDurationMs: 90_000, ...over,
});

export const release = (name: string, over: Partial<QaReleasePoint> = {}): QaReleasePoint => ({
  release: name, workspace: 'UI', suite: 'REGRESSION', environment: 'qa', startedAt: '2026-10-01T10:00:00Z', passed: 7, failed: 2, skipped: 1, total: 10, ...over,
});

export const overview = (over: Partial<QaOverview> = {}): QaOverview => ({
  mode: 'unique',
  totals: totals(),
  releases: [release('QA_REG_OCT25_R1', { passed: 5, failed: 4, skipped: 1 }), release('QA_REG_OCT26_R2')],
  trend: [
    { date: '2026-10-25', passed: 5, failed: 4, skipped: 1, runs: 1 },
    { date: '2026-10-26', passed: 7, failed: 2, skipped: 1, runs: 1 },
  ],
  volume: [
    { date: '2026-10-25', cumulativeScenarios: 10 },
    { date: '2026-10-26', cumulativeScenarios: 20 },
  ],
  ...over,
});

export const facets = (): QaFacets => ({
  modules: [{ module: 'wallet', subModules: ['wallet.home', 'wallet.agent'] }, { module: 'uam', subModules: ['login'] }],
  environments: ['qa', 'uat'],
  releases: ['QA_REG_OCT26_R2', 'QA_REG_OCT25_R1'],
  suites: ['REGRESSION', 'SMOKE'],
});

export const row = (id: string, over: Partial<QaTestCaseRow> = {}): QaTestCaseRow => ({
  id,
  tcId: 'HQ_RW_036',
  testKey: 'riderweb:wallet.agent_load',
  name: 'Screen wallet.agent_load opens',
  app: 'riderweb',
  module: 'wallet',
  subModule: 'wallet.agent_load',
  environment: 'qa',
  suites: ['REGRESSION'],
  status: 'FAILED',
  iteration: 4,
  passCount: 3,
  failCount: 1,
  skipCount: 0,
  durationMs: 61_000,
  lastRunAt: '2026-10-26T10:00:00Z',
  lastRunId: 'run-1',
  release: 'QA_REG_OCT26_R2',
  failureCategory: 'TIMEOUT',
  failureMessage: 'Timed out after 20 seconds\nstack line',
  reportUrl: 'https://reports.example.com/qa/run-1/index.html',
  ...over,
});

export const testCasePage = (rows: QaTestCaseRow[], over: Partial<QaTestCasePage> = {}): QaTestCasePage => ({
  totals: { total: rows.length, passed: 0, failed: rows.length, skipped: 0 },
  items: rows,
  page: 0,
  size: 20,
  totalElements: rows.length,
  totalPages: 1,
  ...over,
});

export const failures = (over: Partial<QaFailures> = {}): QaFailures => ({
  totalFailed: 3,
  byCategory: [{ category: 'TIMEOUT', count: 2 }, { category: 'MYSTERY', count: 1 }],
  byModule: [{ module: 'wallet', failed: 2, total: 5 }],
  topFailing: [{ id: 'a', tcId: 'HQ_RW_036', testKey: 'riderweb:wallet.agent_load', name: 'Wallet agent load', module: 'wallet', failCount: 3, runCount: 4, failureCategory: 'TIMEOUT', failureMessage: 'Timed out\nmore', lastRunAt: '2026-10-26T10:00:00Z' }],
  flaky: [{ id: 'b', tcId: null, testKey: 'uam:login:otp', name: 'OTP login', module: 'uam', passCount: 2, failCount: 2 }],
  ...over,
});

export const testSet = (id: string, over: Partial<QaTestSet> = {}): QaTestSet => ({
  id, name: 'Nightly UI', workspace: 'UI', suite: 'REGRESSION', tagExpression: '@regression and not @slow', description: 'Every night',
  createdBy: 'qa.lead', createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-10-20T10:00:00Z', version: 2, ...over,
});

export const testSetExecution = (id: string, over: Partial<QaTestSetExecution> = {}): QaTestSetExecution => ({
  testSetId: id, name: 'Nightly UI', suite: 'REGRESSION', workspace: 'UI',
  lastRun: { id: 'r1', environment: 'qa', status: 'FAILED', total: 10, passed: 8, failed: 2, skipped: 0, durationMs: 120_000, startedAt: '2026-10-26T10:00:00Z', release: 'QA_REG_OCT26_R2', reportUrl: 'https://reports.example.com/qa/r1/index.html' },
  totals: { total: 10, passed: 8, failed: 2, skipped: 0 },
  ...over,
});

/** What the privilege service says: every code, or only the listed ones. */
export function accessMock(granted: Set<string> | null) {
  return {
    changes$: EMPTY,
    can: (codes: string | readonly string[]) => granted === null || [codes].flat().some((c) => granted.has(c)),
  };
}

/** A stand-in for the current route: the query parameters can be changed and the route data is fixed. */
export function routeStub(params: Record<string, string> = {}, data: Record<string, unknown> = {}, routeParams: Record<string, string> = {}) {
  const queryParamMap$ = new BehaviorSubject<ParamMap>(convertToParamMap(params));
  const route = { queryParamMap: queryParamMap$.asObservable(), snapshot: { data, queryParamMap: convertToParamMap(params), paramMap: convertToParamMap(routeParams) } } as unknown as ActivatedRoute;
  return { route, setParams: (next: Record<string, string>) => queryParamMap$.next(convertToParamMap(next)) };
}

export const routerStub = () => ({ navigate: vi.fn().mockResolvedValue(true) });

export const endpoint = (id: string, over: Partial<QaEndpointItem> = {}): QaEndpointItem => ({
  id,
  method: 'POST',
  path: '/api/v1/uam/users/{userId}/block',
  privilegeName: 'UAM_USER_BLOCK_CREATE',
  module: 'uam',
  subModule: 'users',
  documented: true,
  hitCount: 5,
  callCount: 9,
  lastHitAt: '2026-10-26T10:00:00Z',
  mappedTestKeys: ['uam:users:block', 'uam:users:unblock', 'uam:login:otp', 'uam:session:expiry', 'uam:roles:grant'],
  mappedCount: 5,
  ...over,
});

export const coverage = (over: Partial<QaEndpointCoverage> = {}): QaEndpointCoverage => ({
  documented: 200,
  tested: 150,
  untested: 50,
  coveragePct: 75,
  undocumentedTested: 3,
  totalEndpoints: 203,
  modulesCovered: 12,
  subModulesCovered: 40,
  mappedScenarios: 88,
  lastSyncedAt: '2026-10-26T09:00:00Z',
  ...over,
});

export const endpointPage = (items: QaEndpointItem[], over: Partial<QaEndpointPage> = {}): QaEndpointPage => ({
  coverage: coverage(),
  items,
  page: 0,
  size: 20,
  totalElements: items.length,
  totalPages: 1,
  ...over,
});

export const endpointOptions = (): QaEndpointFilterOptions => ({
  modules: [{ module: 'uam', subModules: ['users', 'roles'] }, { module: 'wallet', subModules: ['agent'] }],
  methods: ['GET', 'POST', 'PUT', 'DELETE'],
});

export const endpointDetail = (id: string, over: Partial<QaEndpointDetail> = {}): QaEndpointDetail => ({
  ...endpoint(id),
  description: 'Allows blocking a user',
  operation: 'CREATE',
  tests: [
    { id: 't1', tcId: null, testKey: 'uam:users:block', name: 'Block a user', status: 'PASSED', lastRunAt: '2026-10-26T10:00:00Z' },
    { id: 't2', tcId: null, testKey: 'uam:users:unblock', name: 'Unblock a user', status: 'FAILED', lastRunAt: null },
  ],
  ...over,
});

export const studioItem = (id: string, over: Partial<QaStudioCaseItem> = {}): QaStudioCaseItem => ({
  id,
  tcId: 'HQ_ST_001',
  name: 'Block user rejects a missing token',
  description: '',
  module: 'uam',
  subModule: 'users',
  suite: 'REGRESSION',
  enabled: true,
  auth: 'QA_ADMIN',
  method: 'POST',
  path: '/api/v1/uam/users/42/block',
  assertionCount: 2,
  version: 3,
  updatedBy: 'qa.lead',
  updatedAt: '2026-10-26T10:00:00Z',
  ...over,
});

export const studioPage = (items: QaStudioCaseItem[], over: Partial<QaStudioPage> = {}): QaStudioPage => ({
  items, page: 0, size: 20, totalElements: items.length, totalPages: 1, ...over,
});

export const studioCase = (id: string, over: Partial<QaStudioCase> = {}): QaStudioCase => ({
  id,
  tcId: 'HQ_ST_001',
  name: 'Block user rejects a missing token',
  description: 'Needs a sign-in',
  module: 'uam',
  subModule: 'users',
  suite: 'REGRESSION',
  enabled: true,
  auth: 'QA_ADMIN',
  method: 'POST',
  path: '/api/v1/uam/users/42/block',
  query: { dryRun: 'true' },
  headers: { 'X-Trace': 'abc' },
  body: '{"reason":"test"}',
  assertions: [
    { type: 'STATUS', expected: [200, 201] },
    { type: 'JSON_PATH_EQUALS', path: '$.responseObject.id', expected: '42' },
    { type: 'MAX_TIME_MS', expected: 2000 },
  ],
  version: 3,
  createdBy: 'qa.lead',
  createdAt: '2026-10-01T10:00:00Z',
  updatedBy: 'qa.lead',
  updatedAt: '2026-10-26T10:00:00Z',
  ...over,
});
