import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { HqStepRailState, HqStepRailStep, HqStepRailTexts } from './hq-step-rail.model';

/**
 * A vertical step rail beside the step's own content (the projected content): on wide screens a sticky list of numbered steps
 * with a progress bar and connectors that fill as steps are done; on narrow screens a compact bar with the current step and a
 * "go to step" picker. The same look as Rider_Web's organization setup. Words come from the app ({@link HqStepRailTexts}).
 *
 * A step can be opened when it is neither the current one nor locked; the page decides what that means through
 * {@link stepSelect} (it may refuse, e.g. while the current step has errors).
 */
@Component({
  selector: 'hq-step-rail',
  standalone: false,
  templateUrl: './hq-step-rail.html',
  styleUrl: './hq-step-rail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqStepRail {
  @Input() steps: readonly HqStepRailStep[] = [];
  /** Id of the step on screen. */
  @Input() current = '';
  /** Progress 0-100 (the bar's fill). */
  @Input() percent = 0;
  @Input() texts: HqStepRailTexts = {
    stepOf: '', progress: '', ariaLabel: 'Steps', jumpTo: 'Go to step', done: 'Done', todo: 'Not started', locked: 'Locked',
  };
  /** Emits the id of a step the user asked to open. */
  @Output() stepSelect = new EventEmitter<string>();

  get currentStep(): HqStepRailStep | undefined {
    return this.steps.find((step) => step.id === this.current);
  }

  get boundedPercent(): number {
    return Math.max(0, Math.min(100, Math.round(this.percent || 0)));
  }

  stateOf(step: HqStepRailStep): HqStepRailState {
    return step.state ?? 'todo';
  }

  numberOf(step: HqStepRailStep): number {
    return this.steps.indexOf(step) + 1;
  }

  canOpen(step: HqStepRailStep): boolean {
    return step.id !== this.current && this.stateOf(step) !== 'locked';
  }

  stateLabel(step: HqStepRailStep): string {
    const state = this.stateOf(step);
    return state === 'done' ? this.texts.done : state === 'locked' ? this.texts.locked : this.texts.todo;
  }

  open(step: HqStepRailStep): void {
    if (this.canOpen(step)) this.stepSelect.emit(step.id);
  }

  /** The compact picker: open the chosen step (the current or a locked one is ignored). */
  jump(id: unknown): void {
    const step = this.steps.find((s) => s.id === id);
    if (step) this.open(step);
  }

  trackById(_: number, step: HqStepRailStep): string {
    return step.id;
  }
}
