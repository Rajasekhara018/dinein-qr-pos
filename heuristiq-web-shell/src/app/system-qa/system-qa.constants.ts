import { TableColumnSpec } from '../shared/table-columns.helpers';
import { QaEnvironment, QaRunSummary, QaScenario, QaScenarioStatus } from './system-qa.models';

/** Stable screen key for the audit trail (never rename or translate once released). */
export const SYSTEM_QA_SCREEN_KEY = 'system.qa';

/** Screen permission key (mapped to privilege codes in hq-shared-lib's hq-privilege-map.ts). */
export const SYSTEM_QA_PERMISSION = 'view_qa_runs';

/** Route of the screen (the System group of the sidebar). */
export const SYSTEM_QA_ROUTE = '/system/qa';

/** Privilege codes (exact names from privilege.json). */
export const QA_PRIVILEGES = {
  runList: 'CONFIG_QA_RUN_LIST',
  runRead: 'CONFIG_QA_RUN_READ',
  runCreate: 'CONFIG_QA_RUN_CREATE',
  documentList: 'CONFIG_QA_DOCUMENT_LIST',
  documentRead: 'CONFIG_QA_DOCUMENT_READ',
  documentCreate: 'CONFIG_QA_DOCUMENT_CREATE',
  /** Opening a run's PDF goes through DMS: its metadata, then a download URL. */
  dmsMetadataRead: 'DMS_DOCUMENT_METADATA_READ',
  dmsDownloadUrl: 'DMS_DOCUMENT_DOWNLOAD_URL',
} as const;

/** Gateway paths (below environment.apiUrl) of the QA API in Rider_ConfigService. */
export const QA_RUNS_PATH = '/v1/config/qa/runs';
export const QA_DOCUMENTS_PATH = '/v1/config/qa/documents';

/** Runs per page of the list (the API caps a page at 50). */
export const QA_RUNS_PAGE_SIZE = 10;
export const QA_MAX_PAGE_SIZE = 50;

/** How many of the newest runs of one environment feed the pass-rate trend. */
export const QA_TREND_RUN_COUNT = 30;

/** The trend chart's viewBox and padding. */
export const QA_TREND_VIEW = { width: 100, height: 40, padding: 4 } as const;

/** Height of the pass-rate trend chart (px), the categorical slot of its single line, and how its points are labelled. */
export const QA_TREND_HEIGHT = 200;
export const QA_TREND_SLOT = 1;
export const QA_TREND_DAY_FORMAT = 'd MMM, HH:mm';

export const QA_ENVIRONMENTS: readonly QaEnvironment[] = ['dev', 'qa', 'uat', 'local', 'pilot', 'live'];
export const QA_SCENARIO_STATUSES: readonly QaScenarioStatus[] = ['FAILED', 'SKIPPED', 'PASSED'];

export type QaTone = 'danger' | 'warning' | 'success' | 'neutral';

/** Status -> chip colour (runs and scenarios). */
export const QA_STATUS_TONE: Readonly<Record<string, QaTone>> = {
  PASSED: 'success',
  FAILED: 'danger',
  UNSTABLE: 'warning',
  SKIPPED: 'neutral',
};

/** Translation keys of the status labels; a status with no key is shown as its raw code. */
export const QA_STATUS_KEYS: Readonly<Record<string, string>> = {
  PASSED: 'systemQa.status.PASSED',
  FAILED: 'systemQa.status.FAILED',
  UNSTABLE: 'systemQa.status.UNSTABLE',
  SKIPPED: 'systemQa.status.SKIPPED',
};

/** Translation keys of the environment labels; an environment with no key is shown as its raw code. */
export const QA_ENVIRONMENT_KEYS: Readonly<Record<string, string>> = {
  dev: 'systemQa.environments.dev',
  qa: 'systemQa.environments.qa',
  uat: 'systemQa.environments.uat',
  local: 'systemQa.environments.local',
  pilot: 'systemQa.environments.pilot',
  live: 'systemQa.environments.live',
};

/** Translation keys of the duration units ({{value}} is the number). */
export const QA_DURATION_KEYS: Readonly<Record<string, string>> = {
  h: 'systemQa.duration.h',
  m: 'systemQa.duration.m',
  s: 'systemQa.duration.s',
  ms: 'systemQa.duration.ms',
};

/** Translation keys of the file-size units ({{value}} is the number). */
export const QA_SIZE_KEYS: Readonly<Record<string, string>> = {
  B: 'systemQa.size.B',
  KB: 'systemQa.size.KB',
  MB: 'systemQa.size.MB',
};

/** Where the page's own messages live. */
export const QA_MESSAGE_KEYS = {
  pdfFailed: 'systemQa.messages.pdfFailed',
} as const;

/** Viewer dialog options. */
export const QA_PDF_DIALOG = { maxWidth: '95vw', autoFocus: false } as const;

/** Link attributes for anything that opens another site. */
export const QA_LINK_REL = 'noopener noreferrer';

/** Columns of the run history: translation keys for the headings, cells are drawn by the screen's templates. */
export const QA_RUN_COLUMNS: readonly TableColumnSpec<QaRunSummary>[] = [
  { key: 'environment', labelKey: 'systemQa.columns.environment' },
  { key: 'status', labelKey: 'systemQa.columns.status' },
  { key: 'tags', labelKey: 'systemQa.columns.tags' },
  { key: 'passed', labelKey: 'systemQa.columns.passed', className: 'qa-num' },
  { key: 'failed', labelKey: 'systemQa.columns.failed', className: 'qa-num' },
  { key: 'skipped', labelKey: 'systemQa.columns.skipped', className: 'qa-num' },
  { key: 'duration', labelKey: 'systemQa.columns.duration' },
  { key: 'started', labelKey: 'systemQa.columns.started' },
];

/** Columns of one run's scenario table. */
export const QA_SCENARIO_COLUMNS: readonly TableColumnSpec<QaScenario>[] = [
  { key: 'status', labelKey: 'systemQa.columns.status' },
  { key: 'pilotId', labelKey: 'systemQa.columns.pilotId' },
  { key: 'feature', labelKey: 'systemQa.columns.feature' },
  { key: 'scenario', labelKey: 'systemQa.columns.scenario' },
  { key: 'duration', labelKey: 'systemQa.columns.duration' },
];
