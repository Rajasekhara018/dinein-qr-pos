import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  signal,
} from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { describeError, ErrorMessages } from '../form-errors';

/**
 * Shows the first validation message of a control once it was touched or edited. Give it an `id` and reference it
 * from the input's `aria-describedby`.
 */
@Component({
  selector: 'app-field-error',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './field-error.html',
})
export class FieldError {
  readonly control = input.required<AbstractControl>();
  readonly messages = input<ErrorMessages>({});
  readonly id = input<string | null>(null);
  /** Show even if untouched (e.g. group-level rules after a submit attempt). */
  readonly force = input(false);

  private readonly version = signal(0);

  constructor() {
    // Reactive-forms state is not signal-based: re-evaluate on every control event (value, status, touched…).
    effect((onCleanup) => {
      const sub = this.control().events.subscribe(() => this.version.update((v) => v + 1));
      onCleanup(() => sub.unsubscribe());
    });
  }

  protected readonly message = computed(() => {
    this.version();
    const control = this.control();
    if (!control.errors || control.disabled) return '';
    if (!this.force() && !control.touched && !control.dirty) return '';
    return describeError(control.errors, this.messages());
  });
}
