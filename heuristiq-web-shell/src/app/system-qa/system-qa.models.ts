/** Shapes of the QA runs API (Rider_ConfigService, /api/v1/config/qa) as agreed in the shared contract. */

/** Environment codes (sent to and received from the API; never translated). */
export type QaEnvironment = 'dev' | 'qa' | 'uat' | 'local' | 'pilot' | 'live';

/** Status of a run, computed by the backend. */
export type QaRunStatus = 'PASSED' | 'FAILED' | 'UNSTABLE';

/** Status of one scenario. */
export type QaScenarioStatus = 'PASSED' | 'FAILED' | 'SKIPPED';

export interface QaRunSummary {
  id: string;
  environment: string;
  /** The Cucumber tag filter the run used. */
  tags: string;
  status: QaRunStatus | string;
  total: number;
  passed: number;
  failed: number;
  skipped: number;
  durationMs: number;
  startedAt: string;
  jenkinsJob: string;
  jenkinsBuildNumber: number;
  jenkinsUrl: string | null;
  /** DMS id of the per-run PDF, when there is one. */
  documentId: string | null;
  /** The generated Allure HTML report of the run (step-by-step screenshots, timings); opened only when it is a plain http(s) address. */
  reportUrl?: string | null;
  createdAt: string;
  /** The release the run belongs to (`QA_REG_OCT26_R2`); runs from before releases existed carry `LEGACY`. */
  release?: string;
  /** REGRESSION, SMOKE or OTHER. */
  suite?: string;
  /** UI or API. */
  workspace?: string;
  /** The test set the run was started from, when it was started from the portal. */
  testSetId?: string | null;
  triggeredBy?: string | null;
}

export interface QaScenario {
  feature: string;
  name: string;
  tags: string[];
  /** The `@A10`-style tag, when the scenario has one. */
  pilotId: string | null;
  status: QaScenarioStatus | string;
  durationMs: number;
  /** Already secret-masked by the backend; shown as plain text only. */
  failureMessage: string | null;
  /** `HQ_RW_036`-style number; null for API scenarios. */
  tcId?: string | null;
  testKey?: string;
  app?: string;
  module?: string;
  subModule?: string;
  /** Screenshots of the scenario, kept in the DMS. */
  evidence?: QaEvidence[];
}

/** One screenshot of a scenario: `SCREEN` is its final state, `FAILURE` the one taken when it failed. */
export interface QaEvidence {
  documentId: string;
  kind: 'SCREEN' | 'FAILURE';
  name: string;
}

export interface QaRunDetail extends QaRunSummary {
  scenarios: QaScenario[];
}

export interface QaRunPage {
  content: QaRunSummary[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/** A document in the list (no content). */
export interface QaDocumentSummary {
  name: string;
  title: string;
  updatedAt: string;
  sizeBytes: number;
}

export interface QaDocument {
  name: string;
  title: string;
  markdown: string;
  updatedAt: string;
}

/** The file bytes of a run's PDF and its name, ready for the viewer. */
export interface QaPdfFile {
  blob: Blob;
  fileName: string;
}

export type QaTab = 'runs' | 'detail' | 'documents';

export interface QaScenarioFilters {
  /** '' = every status. */
  status: '' | QaScenarioStatus;
  /** Text matched against feature, scenario name and pilot id. */
  query: string;
  /** '' = every tag. */
  tag: string;
  /** '' = every pilot id. */
  pilotId: string;
}

/** A pass rate placed in the SVG viewBox. */
export interface QaTrendValue {
  id: string;
  startedAt: string;
  value: number;
}

export interface QaTrendPoint extends QaTrendValue {
  x: number;
  y: number;
}

/** One unit of a duration, translated through QA_DURATION_KEYS. */
export interface QaDurationPart {
  unit: 'h' | 'm' | 's' | 'ms';
  value: number;
}
