import { ReadinessFixLink, ReadinessStepStatus } from './application-readiness.models';

/**
 * Go-live governance of an application in the session organization (see docs/APPLICATION_READINESS.md, "stage 3"):
 * the go-live guard refusal, the SUPER_ADMIN override and the alert times.
 */

/** One required step that stops a go-live (the guard's 409 body). */
export interface GoLiveBlocker {
  stepId: string;
  status: ReadinessStepStatus;
  messageKey: string;
  params: Readonly<Record<string, string>> | null;
  fixPath: string | null;
}

/** The body of a 409 GO_LIVE_BLOCKED answer. */
export interface GoLiveBlockedInfo {
  applicationCode: string;
  messageKey: string;
  blockers: GoLiveBlocker[];
}

/** The last override of the guard, kept on the application link. */
export interface GoLiveOverrideRecord {
  by: string | null;
  at: string | null;
  reason: string | null;
  blockingSteps: string[];
}

/** Times of the last alerts (null: never). */
export interface GovernanceAlerts {
  lastBlockedAt: string | null;
  lastRefusedAt: string | null;
}

/** GET /applications/{code}/readiness-governance (also the answer of the override call). */
export interface GovernanceState {
  applicationCode: string;
  guardEnabled: boolean;
  override: GoLiveOverrideRecord | null;
  alerts: GovernanceAlerts | null;
}

/** A blocker ready to render: translation key and params of its text, and where Fix leads (null: no safe in-app path). */
export interface GoLiveBlockerView {
  stepId: string;
  statusKey: string;
  textKey: string;
  params: Record<string, string>;
  fix: ReadinessFixLink | null;
}

/** What the blocked dialog returns when it closes. */
export type GoLiveDialogResult = 'overridden' | 'closed';

/** The translation key (and params) a failed governance call is shown with. */
export interface GovernanceFailure {
  key: string;
  params: Record<string, string | number>;
}
