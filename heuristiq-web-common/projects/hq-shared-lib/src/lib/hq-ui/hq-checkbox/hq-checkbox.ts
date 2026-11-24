import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  ViewChild,
  booleanAttribute,
  forwardRef,
  inject,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { HQ_CHECKBOX_ID_PREFIX, HqCheckboxSize } from './hq-checkbox.constants';

let nextCheckboxId = 0;

/**
 * The common checkbox (a form control: ControlValueAccessor holding a boolean). Works with ngModel, reactive forms, or
 * `checked` / `(checkedChange)`.
 *
 * A real native `<input type="checkbox">` sits inside (visually hidden) so Space, focus and screen readers behave as usual;
 * the visible box and tick are drawn on top. `indeterminate` shows a dash (a "select all" with some rows chosen) and is
 * cleared by the next click. The label is `label`, or the projected content when `label` is empty. Clicks are not swallowed,
 * so `(click)="$event.stopPropagation()"` on the host keeps a checkbox inside a clickable table row from opening the row.
 *
 *   <hq-checkbox formControlName="active" [label]="'x.active' | translate"></hq-checkbox>
 *   <hq-checkbox [checked]="allChosen" [indeterminate]="someChosen" (checkedChange)="chooseAll($event)" ariaLabel="..."></hq-checkbox>
 *
 * The host carries `hq-checkbox--checked`, `hq-checkbox--indeterminate` and `hq-checkbox--disabled`, so a screen can style
 * its own markup from them. With `hideControl` the drawn box is not shown and the projected content fills the whole
 * clickable label, for choices drawn as chips or cards; the hidden native checkbox still handles Space, focus and screen
 * readers, and the label shows a focus ring while it has keyboard focus.
 *
 *   <hq-checkbox hideControl [checked]="isPicked(d)" (checkedChange)="toggle(d, $event)"><span class="chip">{{ d }}</span></hq-checkbox>
 */
@Component({
  selector: 'hq-checkbox',
  standalone: false,
  templateUrl: './hq-checkbox.html',
  styleUrl: './hq-checkbox.scss',
  host: {
    '[class.hq-checkbox--checked]': 'checked && !indeterminate',
    '[class.hq-checkbox--indeterminate]': 'indeterminate',
    '[class.hq-checkbox--disabled]': 'isDisabled',
    '[class.hq-checkbox--plain]': 'hideControl',
  },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqCheckbox), multi: true }],
})
export class HqCheckbox implements ControlValueAccessor {
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly uid = `${HQ_CHECKBOX_ID_PREFIX}${nextCheckboxId++}`;

  @ViewChild('box') private box?: ElementRef<HTMLInputElement>;

  @Input({ transform: booleanAttribute }) checked = false;
  @Input({ transform: booleanAttribute }) disabled = false;
  /** Shown as a dash instead of a tick; the next click clears it and sets a definite value. */
  @Input({ transform: booleanAttribute }) indeterminate = false;
  /** Already-translated label; when empty, the projected content is the label. */
  @Input() label = '';
  @Input() ariaLabel = '';
  @Input() inputId = '';
  @Input() name = '';
  @Input({ transform: booleanAttribute }) required = false;
  @Input({ transform: booleanAttribute }) invalid = false;
  /** Message shown under the checkbox; any text also marks it invalid. */
  @Input() error: string | null = null;
  @Input() size: HqCheckboxSize = 'regular';
  /** Hides the drawn box so the projected content (a chip, a card) is the whole control. */
  @Input({ transform: booleanAttribute }) hideControl = false;

  /** Emitted when the person toggles it (after the form control has been updated). */
  @Output() readonly checkedChange = new EventEmitter<boolean>();

  private formDisabled = false;
  private onChange: (value: boolean) => void = () => {};
  private onTouched: () => void = () => {};

  get controlId(): string {
    return this.inputId || this.uid;
  }

  get errorId(): string {
    return `${this.controlId}-error`;
  }

  get isDisabled(): boolean {
    return this.disabled || this.formDisabled;
  }

  get isInvalid(): boolean {
    return this.invalid || !!this.error;
  }

  /** The native checkbox, e.g. to focus it from a screen. */
  get control(): HTMLInputElement | null {
    return this.box?.nativeElement ?? null;
  }

  focusControl(): void {
    this.control?.focus();
  }

  // ---- ControlValueAccessor ----------------------------------------------------------------

  writeValue(value: unknown): void {
    this.checked = value === true;
    this.cdr.markForCheck();
  }

  registerOnChange(fn: (value: boolean) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.formDisabled = disabled;
    this.cdr.markForCheck();
  }

  // ---- the native input's events ----------------------------------------------------------

  onToggle(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (this.isDisabled) {
      input.checked = this.checked;
      return;
    }
    this.indeterminate = false;
    this.checked = input.checked;
    this.onChange(this.checked);
    this.checkedChange.emit(this.checked);
  }

  onBlur(): void {
    this.onTouched();
  }
}
