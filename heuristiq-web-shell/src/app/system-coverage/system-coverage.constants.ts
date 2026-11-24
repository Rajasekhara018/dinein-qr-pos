import { TableColumnSpec } from '../shared/table-columns.helpers';
import { CoverageGroup, CoverageKind, CoverageLevel, CoverageRow, CoverageSortMode, CoverageThresholds } from './system-coverage.models';

/** Stable screen key for the audit trail (never rename or translate once released). */
export const SYSTEM_COVERAGE_SCREEN_KEY = 'system.coverage';

/** Screen permission key (mapped to privilege codes in hq-shared-lib's hq-privilege-map.ts). */
export const SYSTEM_COVERAGE_PERMISSION = 'view_code_coverage';

/** Route of the screen (the System group of the sidebar). */
export const SYSTEM_COVERAGE_ROUTE = '/system/coverage';

/** Privilege codes (exact names from privilege.json). */
export const COVERAGE_PRIVILEGES = {
  list: 'CONFIG_COVERAGE_RUN_LIST',
  read: 'CONFIG_COVERAGE_RUN_READ',
  create: 'CONFIG_COVERAGE_RUN_CREATE',
} as const;

/** Gateway path (below environment.apiUrl) of the coverage API in Rider_ConfigService. */
export const COVERAGE_RUNS_PATH = '/v1/config/coverage/runs';

/** The only collector document version the screen and the server accept. */
export const COVERAGE_SCHEMA_VERSION = 1;

/** Used when a document carries no thresholds of its own. */
export const COVERAGE_DEFAULT_THRESHOLDS: CoverageThresholds = { floor: 70, target: 90 };

/** A project measured longer ago than this is marked stale. */
export const COVERAGE_STALE_AFTER_DAYS = 7;

/** Runs per page of the history list (the API caps a page at 50). */
export const COVERAGE_HISTORY_PAGE_SIZE = 10;

/** The screen asks the server for a newer run this often (milliseconds), so a finished Jenkins run shows up by itself. */
export const COVERAGE_AUTO_REFRESH_MS = 30_000;

/** How many of the newest runs feed the trend chart. */
export const COVERAGE_TREND_RUN_COUNT = 10;

/** Upload limit, the same 2 MB the server enforces. */
export const COVERAGE_MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

/** The viewBox of the former SVG trend, still used by buildTrendPoints in the helpers. */
export const COVERAGE_TREND_VIEW = { width: 100, height: 40, padding: 4 } as const;

/** Height of the coverage trend chart (px), the categorical slot of its single line, and how its points are labelled. */
export const COVERAGE_TREND_HEIGHT = 200;
export const COVERAGE_TREND_SLOT = 1;
export const COVERAGE_TREND_DAY_FORMAT = 'd MMM';

export const COVERAGE_KINDS: readonly CoverageKind[] = ['backend', 'web'];
export const COVERAGE_LEVELS: readonly CoverageLevel[] = ['BELOW_FLOOR', 'OK', 'AT_TARGET', 'MISSING'];
export const COVERAGE_SORT_MODES: readonly CoverageSortMode[] = ['worst', 'best', 'name'];

/** Translation keys of the kind labels; a kind with no key is shown as its raw code. */
export const COVERAGE_KIND_KEYS: Readonly<Record<string, string>> = {
  backend: 'systemCoverage.kinds.backend',
  web: 'systemCoverage.kinds.web',
};

/** Translation keys of the level (status chip) labels; a level with no key is shown as its raw code. */
export const COVERAGE_LEVEL_KEYS: Readonly<Record<string, string>> = {
  BELOW_FLOOR: 'systemCoverage.levels.BELOW_FLOOR',
  OK: 'systemCoverage.levels.OK',
  AT_TARGET: 'systemCoverage.levels.AT_TARGET',
  MISSING: 'systemCoverage.levels.MISSING',
};

/** Translation keys of the sort choices. */
export const COVERAGE_SORT_KEYS: Readonly<Record<CoverageSortMode, string>> = {
  worst: 'systemCoverage.sort.worst',
  best: 'systemCoverage.sort.best',
  name: 'systemCoverage.sort.name',
};

export type CoverageTone = 'danger' | 'warning' | 'success' | 'neutral';

/** Level -> chip colour. */
export const COVERAGE_LEVEL_TONE: Readonly<Record<CoverageLevel, CoverageTone>> = {
  BELOW_FLOOR: 'danger',
  OK: 'warning',
  AT_TARGET: 'success',
  MISSING: 'neutral',
};

/** Translation keys of an age ("just now", "5 minutes ago" ...). */
export const COVERAGE_AGE_KEYS = {
  now: 'systemCoverage.age.now',
  minutes: 'systemCoverage.age.minutes',
  hours: 'systemCoverage.age.hours',
  days: 'systemCoverage.age.days',
} as const;

/** Translation keys of the problems found while reading a collector file or calling the API. */
export const COVERAGE_ERROR_KEYS = {
  notJson: 'systemCoverage.upload.errors.notJson',
  notObject: 'systemCoverage.upload.errors.notObject',
  schemaVersion: 'systemCoverage.upload.errors.schemaVersion',
  generatedAt: 'systemCoverage.upload.errors.generatedAt',
  thresholds: 'systemCoverage.upload.errors.thresholds',
  noProjects: 'systemCoverage.upload.errors.noProjects',
  tooLarge: 'systemCoverage.upload.errors.tooLarge',
  unreadable: 'systemCoverage.upload.errors.unreadable',
  rejected: 'systemCoverage.upload.errors.rejected',
  forbidden: 'systemCoverage.upload.errors.forbidden',
  failed: 'systemCoverage.upload.errors.failed',
} as const;

/** Where the page's own messages live. */
export const COVERAGE_MESSAGE_KEYS = {
  uploaded: 'systemCoverage.upload.success',
  runLoadFailed: 'systemCoverage.messages.runLoadFailed',
} as const;

/** Columns of the per-project coverage table: translation keys for the headings, cells are drawn by the screen's templates. */
export const COVERAGE_ROW_COLUMNS: readonly TableColumnSpec<CoverageRow>[] = [
  { key: 'project', labelKey: 'systemCoverage.columns.project' },
  { key: 'kind', labelKey: 'systemCoverage.columns.kind' },
  { key: 'lines', labelKey: 'systemCoverage.columns.lines', className: 'cov-num' },
  { key: 'branches', labelKey: 'systemCoverage.columns.branches', className: 'cov-num' },
  { key: 'methods', labelKey: 'systemCoverage.columns.methodsFunctions', className: 'cov-num' },
  { key: 'status', labelKey: 'systemCoverage.columns.status' },
  { key: 'measured', labelKey: 'systemCoverage.columns.measured' },
];

/** Columns of a project's areas, shown in the expanded detail row; the bar column has no heading. */
export const COVERAGE_GROUP_COLUMNS: readonly TableColumnSpec<CoverageGroup>[] = [
  { key: 'name', labelKey: 'systemCoverage.columns.area' },
  { key: 'pct', labelKey: 'systemCoverage.columns.lines', className: 'cov-num' },
  { key: 'coveredOfTotal', labelKey: 'systemCoverage.columns.coveredOfTotal', className: 'cov-num' },
  { key: 'bar', labelKey: '', className: 'cov-bar-col' },
];
