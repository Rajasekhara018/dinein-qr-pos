import { ReadinessStep } from './application-readiness.models';
import { HasTranslation, parseFixPath, readinessTextKey } from './application-readiness.helpers';
import { GO_LIVE_STEP_EXCLUDED_IDS, GO_LIVE_STEP_EXCLUDED_PREFIXES, GO_LIVE_STEP_EXCLUDED_SERVICES } from './go-live-steps.constants';
import { GoLiveStepMark, GoLiveStepView } from './go-live-steps.models';

/** True for the steps the go-live list leaves out: the summary step, user related steps and the switched-off reports steps. */
export function isExcludedStep(step: Pick<ReadinessStep, 'id' | 'service'>): boolean {
  return (
    GO_LIVE_STEP_EXCLUDED_IDS.includes(step.id) ||
    GO_LIVE_STEP_EXCLUDED_SERVICES.includes(step.service) ||
    GO_LIVE_STEP_EXCLUDED_PREFIXES.some((prefix) => step.id.startsWith(prefix))
  );
}

/** Done = passed or not applicable; could not be checked = unknown; everything else is not done. */
export function markOf(status: ReadinessStep['status']): GoLiveStepMark {
  if (status === 'PASS' || status === 'NOT_APPLICABLE') return 'done';
  return status === 'UNKNOWN' ? 'unknown' : 'notDone';
}

/** The steps to show, in the order UAM sent them (phase, then order), each with its mark, sentence and Fix. */
export function toGoLiveSteps(steps: readonly ReadinessStep[] | null | undefined, has: HasTranslation, canOpen: (path: string) => boolean): GoLiveStepView[] {
  return (steps ?? [])
    .filter((step) => !isExcludedStep(step))
    .map((step) => {
      const mark = markOf(step.status);
      const fixLink = mark !== 'done' && !step.locked ? parseFixPath(step.fixPath) : null;
      return {
        id: step.id,
        mark,
        textKey: readinessTextKey(step.messageKey, has, step.status),
        required: step.severity === 'BLOCK',
        waiting: !!step.locked && mark !== 'done',
        fix: fixLink && canOpen(fixLink.path) ? fixLink : null,
      };
    });
}
