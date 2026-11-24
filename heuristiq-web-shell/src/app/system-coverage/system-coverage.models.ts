/** Shapes of the code-coverage API (Rider_ConfigService, CoverageDtos) and of the collector document. */

export type CoverageKind = 'backend' | 'web';
export type CoverageProjectStatus = 'ok' | 'missing';

/** Classification of a project against the floor and target thresholds (codes are never translated). */
export type CoverageLevel = 'BELOW_FLOOR' | 'OK' | 'AT_TARGET' | 'MISSING';

export interface CoverageMetric {
  covered: number;
  total: number;
  pct: number;
}

/** Web projects carry statements and functions; backend projects carry methods, instructions and classes. */
export interface CoverageMetrics {
  lines: CoverageMetric | null;
  branches: CoverageMetric | null;
  statements?: CoverageMetric | null;
  functions?: CoverageMetric | null;
  methods?: CoverageMetric | null;
  instructions?: CoverageMetric | null;
  classes?: CoverageMetric | null;
}

export interface CoverageGroup {
  name: string;
  lines: CoverageMetric;
}

export interface CoverageProject {
  key: string;
  kind: CoverageKind;
  status: CoverageProjectStatus;
  measuredAt: string | null;
  metrics: CoverageMetrics | null;
  groups: CoverageGroup[];
}

export interface CoverageThresholds {
  floor: number;
  target: number;
}

/** The document `scripts/collect-coverage.mjs` writes (schemaVersion 1). */
export interface CoverageDocument {
  schemaVersion: number;
  generatedAt: string;
  source: { gitBranch: string | null; gitCommit: string | null };
  thresholds: CoverageThresholds;
  projects: CoverageProject[];
}

export interface CoverageSummaryCounts {
  projectCount: number;
  okCount: number;
  missingCount: number;
  belowFloorCount: number;
  atTargetCount: number;
}

export interface CoverageRunSummary {
  runId: string;
  createdAt: string;
  uploadedBy: string | null;
  gitBranch: string | null;
  gitCommit: string | null;
  summary: CoverageSummaryCounts;
}

export interface CoverageRunDetail extends CoverageRunSummary {
  document: CoverageDocument;
}

export interface CoverageRunPage {
  content: CoverageRunSummary[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface CoverageUploadResult {
  runId: string;
  createdAt: string;
  summary: CoverageSummaryCounts;
}

/** One point of the trend chart: a run and the average line coverage of its measured projects. */
export interface CoverageTrendValue {
  runId: string;
  createdAt: string;
  value: number;
}

/** A trend value placed in the SVG viewBox. */
export interface CoverageTrendPoint extends CoverageTrendValue {
  x: number;
  y: number;
}

export type CoverageSortMode = 'worst' | 'best' | 'name';

export interface CoverageFilters {
  /** '' = every kind. */
  kind: '' | CoverageKind;
  /** '' = every level. */
  level: '' | CoverageLevel;
  query: string;
}

/** One table row: a project with its classification and the numbers the table shows. */
export interface CoverageRow {
  key: string;
  kind: CoverageKind;
  level: CoverageLevel;
  stale: boolean;
  measuredAt: string | null;
  lines: number | null;
  branches: number | null;
  /** Methods (backend) or functions (web). */
  methodsOrFunctions: number | null;
  /** The lower of lines and branches; what the thresholds are applied to. */
  score: number | null;
  groups: CoverageGroup[];
}

/** Result of reading a collector file in the browser: a document or the translation key of the problem. */
export interface CoverageParseResult {
  document: CoverageDocument | null;
  errorKey: string | null;
}

/** A translated age: the key to use and its {{count}}. */
export interface CoverageAge {
  key: string;
  count: number;
}
