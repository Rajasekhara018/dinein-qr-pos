/** Shapes of the QA dashboard API (Rider_ConfigService, /api/v1/config/qa) as agreed in docs/QA_DASHBOARD_DESIGN.md. */

import { QaEvidence } from '../system-qa/system-qa.models';

/** Browser tests (qa-ui) or API tests (qa-api). */
export type QaWorkspace = 'UI' | 'API';

export type QaStatus = 'PASSED' | 'FAILED' | 'SKIPPED';

export type QaSuite = 'REGRESSION' | 'SMOKE' | 'OTHER';

export type QaFailureCategory = 'ASSERTION' | 'TIMEOUT' | 'ELEMENT_NOT_FOUND' | 'ENVIRONMENT' | 'SCRIPT_ERROR' | 'OTHER';

/** `unique` counts each distinct test case by its latest status; `overall` counts every executed scenario in the range. */
export type QaOverviewMode = 'overall' | 'unique';

/** Which table a test-case screen shows. */
export type QaTableKind = 'regression' | 'smoke' | 'inventory' | 'executions';

export interface QaTotals {
  total: number;
  passed: number;
  failed: number;
  skipped: number;
  successPct: number;
  failurePct: number;
  skippedPct: number;
  avgDurationMs: number;
}

export interface QaReleasePoint {
  release: string;
  workspace: string;
  suite: string;
  environment: string;
  startedAt: string;
  passed: number;
  failed: number;
  skipped: number;
  total: number;
}

export interface QaTrendDay {
  date: string;
  passed: number;
  failed: number;
  skipped: number;
  runs: number;
}

export interface QaVolumePoint {
  date: string;
  cumulativeScenarios: number;
}

export interface QaOverview {
  mode: QaOverviewMode | string;
  totals: QaTotals;
  releases: QaReleasePoint[];
  trend: QaTrendDay[];
  volume: QaVolumePoint[];
}

export interface QaModuleFacet {
  module: string;
  subModules: string[];
}

/** What the filter bars offer. */
export interface QaFacets {
  modules: QaModuleFacet[];
  environments: string[];
  releases: string[];
  suites: string[];
}

/** The filters every list and chart screen sends ('' = not filtered). */
export interface QaFilters {
  environment: string;
  release: string;
  suite: string;
  module: string;
  subModule: string;
  status: string;
  q: string;
  from: string;
  to: string;
}

/** One line of the test-case table: a test case in one environment. */
export interface QaTestCaseRow {
  id: string;
  /** `HQ_RW_036`-style number; null for API scenarios. */
  tcId: string | null;
  testKey: string;
  name: string;
  app: string;
  module: string;
  subModule: string;
  environment: string;
  suites: string[];
  status: QaStatus | string;
  /** How many times the test case has run. */
  iteration: number;
  passCount: number;
  failCount: number;
  skipCount: number;
  durationMs: number;
  lastRunAt: string | null;
  lastRunId: string | null;
  release: string | null;
  failureCategory: QaFailureCategory | string | null;
  /** Already secret-masked by the backend; shown as plain text only. */
  failureMessage: string | null;
  /** The generated Allure report of the run it last ran in; only opened when it is a plain http(s) address. */
  reportUrl?: string | null;
}

export interface QaTestCaseTotals {
  total: number;
  passed: number;
  failed: number;
  skipped: number;
}

export interface QaTestCasePage {
  totals: QaTestCaseTotals;
  items: QaTestCaseRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** One past result of a test case. */
export interface QaTestCaseResult {
  runId: string;
  release: string | null;
  status: QaStatus | string;
  durationMs: number;
  startedAt: string;
  failureMessage: string | null;
  /** The generated report of that run. */
  reportUrl?: string | null;
  /** Screenshots of that run's scenario (kept in the DMS). */
  evidence?: QaEvidence[];
}

export interface QaTestCaseDetail {
  testCase: QaTestCaseRow;
  /** The test case's newest results, newest first (the API calls this `history`). */
  history: QaTestCaseResult[];
}

export interface QaCategoryCount {
  category: QaFailureCategory | string;
  count: number;
}

export interface QaModuleFailure {
  module: string;
  failed: number;
  total: number;
}

/** A module's results in the executive summary. */
export interface QaModuleTotals {
  module: string;
  passed: number;
  failed: number;
  total: number;
}

export interface QaFailingCase {
  id: string;
  tcId: string | null;
  testKey: string;
  name: string;
  module: string;
  failCount: number;
  runCount: number;
  failureCategory: QaFailureCategory | string | null;
  failureMessage: string | null;
  lastRunAt: string | null;
}

export interface QaFlakyCase {
  id: string;
  tcId: string | null;
  testKey: string;
  name: string;
  module: string;
  passCount: number;
  failCount: number;
}

export interface QaFailures {
  totalFailed: number;
  byCategory: QaCategoryCount[];
  byModule: QaModuleFailure[];
  topFailing: QaFailingCase[];
  flaky: QaFlakyCase[];
}

export interface QaRunSummaryLite {
  id: string;
  environment: string;
  status: string;
  total: number;
  passed: number;
  failed: number;
  skipped: number;
  durationMs: number;
  startedAt: string;
  release: string | null;
  /** The generated report of the run. */
  reportUrl?: string | null;
}

export interface QaTestSet {
  id: string;
  name: string;
  workspace: QaWorkspace | string;
  suite: QaSuite | string;
  tagExpression: string;
  description: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** What the create and edit calls send. */
export interface QaTestSetInput {
  name: string;
  workspace: QaWorkspace;
  suite: QaSuite;
  tagExpression: string;
  description: string;
  version?: number;
}

export interface QaTestSetExecution {
  testSetId: string;
  name: string;
  suite: string;
  workspace: string;
  lastRun: QaRunSummaryLite | null;
  totals: QaTestCaseTotals;
}

export interface QaPreviousRelease {
  release: string;
  totals: QaTotals;
}

export interface QaExecutiveSummary {
  generatedAt: string;
  workspace: string;
  release: string | null;
  environment: string | null;
  totals: QaTotals;
  previousRelease: QaPreviousRelease | null;
  /** Codes (ALL_GREEN, FAILURES_UP, ...), translated by the screen. */
  headline: string[];
  topRisks: QaTestCaseRow[];
  slowest: QaTestCaseRow[];
  modules: QaModuleTotals[];
}

export interface QaTriggerRequest {
  workspace: QaWorkspace;
  suite: QaSuite;
  environment: string;
  tagExpression: string;
}

export interface QaTriggerResult {
  release: string;
  queued: boolean;
  queueUrl: string | null;
}

/** A segment of a stacked bar, placed in the SVG viewBox. */
export interface QaBarSegment {
  status: QaStatus;
  value: number;
  y: number;
  height: number;
}

/** A bar of the stacked release chart, placed in the SVG viewBox. */
export interface QaBar {
  release: string;
  x: number;
  width: number;
  segments: QaBarSegment[];
  total: number;
}

export interface QaDonutSlice {
  status: QaStatus;
  value: number;
  /** SVG path of the arc. */
  path: string;
}

export interface QaChartPoint {
  label: string;
  x: number;
  y: number;
  value: number;
}
