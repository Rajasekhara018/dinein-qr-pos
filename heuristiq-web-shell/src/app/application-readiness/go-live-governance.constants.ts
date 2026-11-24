/** Privilege codes (exact `name`s in privilege.json) of the go-live governance calls. */
export const GOVERNANCE_PRIVILEGES = {
  override: 'APPLICATION_GO_LIVE_OVERRIDE_CREATE',
  read: 'APPLICATION_READINESS_GOVERNANCE_READ',
  steps: 'APPLICATION_READINESS_READ',
} as const;

/** Error code Rider_UAM answers a refused go-live with (HTTP 409). */
export const GO_LIVE_BLOCKED_ERROR_CODE = 'GO_LIVE_BLOCKED';
export const GO_LIVE_BLOCKED_STATUS = 409;
export const GOVERNANCE_FORBIDDEN_STATUS = 403;
export const GOVERNANCE_BAD_REQUEST_STATUS = 400;

/** Paths below the UAM base URL. */
export const governancePath = (code: string): string => `/applications/${encodeURIComponent(code)}/readiness-governance`;
export const goLiveStepsPath = (code: string): string => `/applications/${encodeURIComponent(code)}/readiness`;
export const goLiveOverridePath = (code: string): string => `/applications/${encodeURIComponent(code)}/go-live-override`;

/** Override reason limits (Rider_UAM default `override-reason-min` / `override-reason-max`; the server has the last word). */
export const OVERRIDE_REASON_MIN = 10;
export const OVERRIDE_REASON_MAX = 500;

/**
 * Parts of the English failure messages of Rider_UAM that tell the failures of one call apart (the failure envelope carries no
 * key). Used only to choose a translated text; the English message itself is never shown.
 */
export const GOVERNANCE_MESSAGE_PATTERNS = {
  reasonInvalid: /between\s+(\d+)\s+and\s+(\d+)\s+characters/i,
  nothingToOverride: /no required step is blocking/i,
} as const;

/** Translation keys (readiness.governance.*). */
export const GOVERNANCE_TEXT_KEYS = {
  goLiveRefused: 'readiness.governance.goLiveRefused',
  overrideForbidden: 'readiness.governance.overrideForbidden',
  overrideReasonInvalid: 'readiness.governance.overrideReasonInvalid',
  nothingToOverride: 'readiness.governance.nothingToOverride',
  requestFailed: 'readiness.governance.requestFailed',
  blockedTitle: 'readiness.governance.blocked.title',
  blockedIntro: 'readiness.governance.blocked.intro',
  blockedFix: 'readiness.governance.blocked.fix',
  blockedClose: 'readiness.governance.blocked.close',
  blockedNoFix: 'readiness.governance.blocked.noFix',
  overrideAction: 'readiness.governance.override.action',
  overrideWarning: 'readiness.governance.override.warning',
  overrideReasonLabel: 'readiness.governance.override.reasonLabel',
  overrideReasonHint: 'readiness.governance.override.reasonHint',
  overrideCounter: 'readiness.governance.override.counter',
  overrideConfirm: 'readiness.governance.override.confirm',
  overrideConfirmTitle: 'readiness.governance.override.confirmTitle',
  overrideConfirmMessage: 'readiness.governance.override.confirmMessage',
  overrideBack: 'readiness.governance.override.back',
  overrideDone: 'readiness.governance.override.done',
  overriding: 'readiness.governance.override.working',
  panelTitle: 'readiness.governance.panel.title',
  panelLoading: 'readiness.governance.panel.loading',
  panelUnavailable: 'readiness.governance.panel.unavailable',
  panelRetry: 'readiness.governance.panel.retry',
  guardOff: 'readiness.governance.panel.guardOff',
  pageOpen: 'readiness.governance.page.open',
  pageBack: 'readiness.governance.page.back',
  pageSubtitle: 'readiness.governance.page.subtitle',
  stepsTitle: 'readiness.governance.steps.title',
  stepsHint: 'readiness.governance.steps.hint',
  stepsLoading: 'readiness.governance.steps.loading',
  stepsUnavailable: 'readiness.governance.steps.unavailable',
  stepsRetry: 'readiness.governance.steps.retry',
  stepsEmpty: 'readiness.governance.steps.empty',
  stepDone: 'readiness.governance.steps.done',
  stepNotDone: 'readiness.governance.steps.notDone',
  stepUnknown: 'readiness.governance.steps.unknown',
  stepRequired: 'readiness.governance.steps.required',
  stepOptional: 'readiness.governance.steps.optional',
  stepFix: 'readiness.governance.steps.fix',
  stepWaiting: 'readiness.governance.steps.waiting',
  lastOverride: 'readiness.governance.panel.lastOverride',
  noOverride: 'readiness.governance.panel.noOverride',
  overrideBy: 'readiness.governance.panel.overrideBy',
  overrideAt: 'readiness.governance.panel.overrideAt',
  overrideReason: 'readiness.governance.panel.overrideReason',
  overrideSteps: 'readiness.governance.panel.overrideSteps',
  alerts: 'readiness.governance.panel.alerts',
  lastBlockedAlert: 'readiness.governance.panel.lastBlockedAlert',
  lastRefusedAlert: 'readiness.governance.panel.lastRefusedAlert',
  never: 'readiness.governance.panel.never',
} as const;

/** Rider_Web routes: the applications list and the go-live governance page of one application. */
export const APPLICATIONS_LIST_ROUTE = '/access-management/applications';
export const goLiveGovernanceRoute = (code: string): string => `${APPLICATIONS_LIST_ROUTE}/${encodeURIComponent(code)}/access`;

/** Screen key of the go-live governance page (an audit key: never renamed once released). */
export const GO_LIVE_GOVERNANCE_SCREEN_KEY = 'access_management.application_access';
