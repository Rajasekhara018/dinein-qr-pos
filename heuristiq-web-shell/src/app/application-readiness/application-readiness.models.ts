/**
 * Shared shapes of the go-live explanation (the blocked dialog and the governance panel): the status of a step that blocked
 * a go-live, the result of a read that never throws into the view, and a link to a Rider_Web screen.
 */

/** Result of one check. */
export type ReadinessStepStatus = 'PASS' | 'FAIL' | 'WARN' | 'UNKNOWN' | 'NOT_APPLICABLE';

/** Stage of the guided path (the steps come in this order). */
export type ReadinessPhase = 'ACCESS' | 'SUPPORT_APPS' | 'BUSINESS' | 'CONNECTIVITY' | 'GO_LIVE';

/** BLOCK = a failure stops go-live (required); WARN = recommended only. */
export type ReadinessSeverity = 'BLOCK' | 'WARN';

/** One check of GET /applications/{code}/readiness (only what the go-live page reads). */
export interface ReadinessStep {
  id: string;
  phase: ReadinessPhase;
  order: number;
  /** Owning service slug (uam, wallet, reports, onboarding, dms, nms, ...). */
  service: string;
  severity: ReadinessSeverity;
  status: ReadinessStepStatus;
  messageKey: string;
  fixPath: string | null;
  /** True while a step that must pass first has not passed. */
  locked: boolean;
}

/** GET /applications/{code}/readiness (only what the go-live page reads). */
export interface ApplicationReadinessDetail {
  applicationCode: string;
  applicationName: string;
  steps: readonly ReadinessStep[];
}

/** What a read ended in: never an exception into the view. */
export type ReadinessResultState = 'ready' | 'unavailable' | 'forbidden';

export interface ReadinessResult<T> {
  state: ReadinessResultState;
  data: T | null;
}

/** A Rider_Web route a fix button opens. */
export interface ReadinessFixLink {
  path: string;
  queryParams: Record<string, string>;
}
