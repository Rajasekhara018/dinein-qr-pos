/** Where a step of a {@link HqStepRail} stands. */
export type HqStepRailState = 'done' | 'todo' | 'locked';

/** One step of a {@link HqStepRail}: its id, the words shown (already translated by the app) and where it stands. */
export interface HqStepRailStep {
  id: string;
  label: string;
  /** Optional one-line description under the label (clamped to two lines). */
  summary?: string;
  /** Bootstrap icon class of the step (e.g. 'bi-person'), shown in the compact bar on narrow screens. */
  icon?: string;
  /** done: completed (check mark, filled connector); todo: not yet; locked: cannot be opened yet. Default todo. */
  state?: HqStepRailState;
}

/** The words of a {@link HqStepRail}; the library has no translation dependency, so the app passes them in. */
export interface HqStepRailTexts {
  /** e.g. "Step 1 of 4". */
  stepOf: string;
  /** Accessible name of the progress bar, e.g. "25% complete" (also shown under the count). */
  progress: string;
  /** Accessible name of the step list. */
  ariaLabel: string;
  /** Label of the "go to step" picker on narrow screens. */
  jumpTo: string;
  /** Screen-reader words for a step's state. */
  done: string;
  todo: string;
  locked: string;
  /** Optional note under the list (e.g. "Each step is saved on its own."). */
  note?: string;
}
