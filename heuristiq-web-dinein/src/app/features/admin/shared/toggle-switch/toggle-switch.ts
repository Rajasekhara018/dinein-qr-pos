import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  model,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Accessible on/off switch (`role="switch"`, 44px target). Works as a form control (`formControlName`) or
 * standalone with `[checked]` / `(checkedChange)`. Without `showLabel` the `label` is its accessible name only.
 */
@Component({
  selector: 'app-toggle-switch',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex min-w-0' },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ToggleSwitch), multi: true },
  ],
  templateUrl: './toggle-switch.html',
})
export class ToggleSwitch implements ControlValueAccessor {
  readonly checked = model(false);
  readonly label = input.required<string>();
  readonly showLabel = input(false, { transform: booleanAttribute });
  readonly hint = input('');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly busy = input(false, { transform: booleanAttribute });
  /** Stable test id / hook. */
  readonly testId = input<string | null>(null);

  private readonly formDisabled = signal(false);
  protected readonly isDisabled = computed(
    () => this.disabled() || this.formDisabled() || this.busy(),
  );

  private onChange: (value: boolean) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  protected toggle(): void {
    if (this.isDisabled()) return;
    const next = !this.checked();
    this.checked.set(next);
    this.onChange(next);
    this.onTouched();
  }

  writeValue(value: unknown): void {
    this.checked.set(!!value);
  }

  registerOnChange(fn: (value: boolean) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.formDisabled.set(isDisabled);
  }
}
