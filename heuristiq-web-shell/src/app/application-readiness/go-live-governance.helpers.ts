import {
  GOVERNANCE_BAD_REQUEST_STATUS,
  GOVERNANCE_FORBIDDEN_STATUS,
  GOVERNANCE_MESSAGE_PATTERNS,
  GOVERNANCE_TEXT_KEYS,
  GO_LIVE_BLOCKED_ERROR_CODE,
  GO_LIVE_BLOCKED_STATUS,
  OVERRIDE_REASON_MAX,
  OVERRIDE_REASON_MIN,
} from './go-live-governance.constants';
import { GoLiveBlockedInfo, GoLiveBlocker, GoLiveBlockerView, GovernanceFailure } from './go-live-governance.models';
import { HasTranslation, parseFixPath, readinessTextKey } from './application-readiness.helpers';
import { READINESS_STEP_STATUS_KEYS } from './application-readiness.constants';
import { ReadinessStepStatus } from './application-readiness.models';

const STEP_STATUSES: readonly string[] = ['PASS', 'FAIL', 'WARN', 'UNKNOWN', 'NOT_APPLICABLE'];

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function stringParams(value: unknown): Record<string, string> | null {
  if (!isRecord(value)) return null;
  const out: Record<string, string> = {};
  Object.entries(value).forEach(([k, v]) => {
    if (v !== null && v !== undefined) out[k] = String(v);
  });
  return out;
}

function toBlocker(raw: unknown): GoLiveBlocker | null {
  if (!isRecord(raw) || typeof raw['stepId'] !== 'string') return null;
  const status = typeof raw['status'] === 'string' && STEP_STATUSES.includes(raw['status']) ? (raw['status'] as ReadinessStepStatus) : 'UNKNOWN';
  return {
    stepId: raw['stepId'],
    status,
    messageKey: typeof raw['messageKey'] === 'string' ? raw['messageKey'] : '',
    params: stringParams(raw['params']),
    fixPath: typeof raw['fixPath'] === 'string' ? raw['fixPath'] : null,
  };
}

/** The HTTP status of a failed call (0 when it is not an HTTP failure). */
export function httpStatusOf(err: unknown): number {
  return isRecord(err) && typeof err['status'] === 'number' ? err['status'] : 0;
}

/** The failure envelope Rider_UAM sent (the body of a failed call), or null. */
function bodyOf(err: unknown): Record<string, unknown> | null {
  return isRecord(err) && isRecord(err['error']) ? err['error'] : null;
}

/** The go-live refusal inside a failed call (HTTP 409, errorCode GO_LIVE_BLOCKED), or null when it is anything else. */
export function parseGoLiveBlocked(err: unknown): GoLiveBlockedInfo | null {
  const body = bodyOf(err);
  if (!body || httpStatusOf(err) !== GO_LIVE_BLOCKED_STATUS || body['errorCode'] !== GO_LIVE_BLOCKED_ERROR_CODE) return null;
  const object = body['responseObject'];
  if (!isRecord(object)) return null;
  const blockers = (Array.isArray(object['blockers']) ? object['blockers'] : []).map(toBlocker).filter((b): b is GoLiveBlocker => b !== null);
  return {
    applicationCode: typeof object['applicationCode'] === 'string' ? object['applicationCode'] : '',
    messageKey: typeof object['messageKey'] === 'string' ? object['messageKey'] : GOVERNANCE_TEXT_KEYS.goLiveRefused,
    blockers,
  };
}

/** A blocker as the dialog renders it: its step text through the readiness text resolver, a safe in-app Fix target. */
export function toBlockerView(blocker: GoLiveBlocker, has: HasTranslation, canOpen: (path: string) => boolean): GoLiveBlockerView {
  const fix = parseFixPath(blocker.fixPath);
  return {
    stepId: blocker.stepId,
    statusKey: READINESS_STEP_STATUS_KEYS[blocker.status] ?? READINESS_STEP_STATUS_KEYS.UNKNOWN,
    textKey: readinessTextKey(blocker.messageKey, has, blocker.status),
    params: { ...(blocker.params ?? {}) },
    fix: fix && canOpen(fix.path) ? fix : null,
  };
}

/**
 * Which translated sentence explains a failed override: the status and the English message of Rider_UAM choose the key. Anything unrecognised is a generic "request failed".
 */
export function governanceFailure(err: unknown): GovernanceFailure {
  const status = httpStatusOf(err);
  const message = typeof bodyOf(err)?.['message'] === 'string' ? (bodyOf(err)?.['message'] as string) : '';
  if (status === GOVERNANCE_FORBIDDEN_STATUS) return { key: GOVERNANCE_TEXT_KEYS.overrideForbidden, params: {} };
  if (status === GOVERNANCE_BAD_REQUEST_STATUS) {
    const range = GOVERNANCE_MESSAGE_PATTERNS.reasonInvalid.exec(message);
    if (range) return { key: GOVERNANCE_TEXT_KEYS.overrideReasonInvalid, params: { min: Number(range[1]), max: Number(range[2]) } };
    if (GOVERNANCE_MESSAGE_PATTERNS.nothingToOverride.test(message)) return { key: GOVERNANCE_TEXT_KEYS.nothingToOverride, params: {} };
  }
  return { key: GOVERNANCE_TEXT_KEYS.requestFailed, params: {} };
}

/** True when the reason (trimmed, as the server counts it) is within the limits. */
export function isOverrideReasonValid(reason: string | null | undefined, min = OVERRIDE_REASON_MIN, max = OVERRIDE_REASON_MAX): boolean {
  const length = (reason ?? '').trim().length;
  return length >= min && length <= max;
}

/** Parses an API timestamp; null when missing or invalid. */
export function parseTimestamp(value: string | null | undefined): Date | null {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}
