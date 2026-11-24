import { ReadinessStepStatus } from './application-readiness.models';

/** messageKey shape: readiness.<service>.<camelCaseCheck>.<status>. */
export const READINESS_MESSAGE_KEY_PATTERN = /^readiness\.([A-Za-z][A-Za-z0-9]*)\.([A-Za-z][A-Za-z0-9]*)\.([A-Za-z_]+)$/;

/** Step status -> the status segment of the i18n keys. */
export const READINESS_STATUS_SUFFIX: Readonly<Record<ReadinessStepStatus, string>> = {
  PASS: 'pass',
  FAIL: 'fail',
  WARN: 'warn',
  UNKNOWN: 'unknown',
  NOT_APPLICABLE: 'notApplicable',
};

/** Generic sentence per step status (the fallback when a check has no specific text). */
export const READINESS_STATUS_TEXT_PREFIX = 'readiness.status';

/** Short label per step status ("Done", "Not done"). */
export const READINESS_STEP_STATUS_KEYS: Readonly<Record<ReadinessStepStatus, string>> = {
  PASS: 'readiness.stepStatus.pass',
  FAIL: 'readiness.stepStatus.fail',
  WARN: 'readiness.stepStatus.warn',
  UNKNOWN: 'readiness.stepStatus.unknown',
  NOT_APPLICABLE: 'readiness.stepStatus.notApplicable',
};
