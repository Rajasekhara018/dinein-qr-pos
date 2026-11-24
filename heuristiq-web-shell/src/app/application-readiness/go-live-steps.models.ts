import { ReadinessFixLink } from './application-readiness.models';

/** How a step stands, in the three words the page uses. */
export type GoLiveStepMark = 'done' | 'notDone' | 'unknown';

/** One step of the go-live list, ready to render. */
export interface GoLiveStepView {
  id: string;
  mark: GoLiveStepMark;
  /** Translation key of the step's sentence. */
  textKey: string;
  /** True when a failure of this step stops go-live. */
  required: boolean;
  /** True while a step that must pass first has not passed (no Fix then). */
  waiting: boolean;
  /** Where the step is fixed; null when it has no fix screen or the user may not open it. */
  fix: ReadinessFixLink | null;
}
