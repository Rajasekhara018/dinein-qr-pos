import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  Output,
  booleanAttribute,
  forwardRef,
  inject,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { HQ_RADIO_GROUP_NAME_PREFIX, HqRadioDirection } from './hq-radio-group.constants';

let nextGroupId = 0;

/**
 * A group of radio buttons (a form control: ControlValueAccessor holding the chosen option's value). The options are
 * `<hq-radio>` children; a value is compared by identity, like a native select's `ngValue`, so objects work too.
 *
 * Each option holds a real native `<input type="radio">` (visually hidden) sharing the group's `name`, so arrow keys,
 * focus and screen readers behave as usual.
 *
 *   <hq-radio-group formControlName="channel" [ariaLabel]="'x.channel' | translate" direction="row">
 *     <hq-radio *ngFor="let c of channels" [value]="c" [label]="channelLabel(c)"></hq-radio>
 *   </hq-radio-group>
 */
@Component({
  selector: 'hq-radio-group',
  standalone: false,
  templateUrl: './hq-radio-group.html',
  styleUrl: './hq-radio-group.scss',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqRadioGroup), multi: true }],
})
export class HqRadioGroup implements ControlValueAccessor {
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly uid = `${HQ_RADIO_GROUP_NAME_PREFIX}${nextGroupId++}`;

  @Input() name = '';
  @Input() ariaLabel = '';
  @Input({ transform: booleanAttribute }) disabled = false;
  @Input({ transform: booleanAttribute }) invalid = false;
  /** Message shown under the group; any text also marks it invalid. */
  @Input() error: string | null = null;
  @Input() direction: HqRadioDirection = 'column';

  /** Emitted when the person picks an option (after the form control has been updated). */
  @Output() readonly valueChange = new EventEmitter<unknown>();

  /** The chosen option's value. */
  value: unknown = null;

  private formDisabled = false;
  private onChange: (value: unknown) => void = () => {};
  private onTouched: () => void = () => {};

  get groupName(): string {
    return this.name || this.uid;
  }

  get errorId(): string {
    return `${this.groupName}-error`;
  }

  get isDisabled(): boolean {
    return this.disabled || this.formDisabled;
  }

  get isInvalid(): boolean {
    return this.invalid || !!this.error;
  }

  isChosen(value: unknown): boolean {
    return this.value === value;
  }

  /** Called by an option when the person picks it. */
  choose(value: unknown): void {
    if (this.isDisabled || this.value === value) return;
    this.value = value;
    this.onChange(value);
    this.valueChange.emit(value);
    this.cdr.markForCheck();
  }

  markTouched(): void {
    this.onTouched();
  }

  // ---- ControlValueAccessor ----------------------------------------------------------------

  writeValue(value: unknown): void {
    this.value = value === undefined ? null : value;
    this.cdr.markForCheck();
  }

  registerOnChange(fn: (value: unknown) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.formDisabled = disabled;
    this.cdr.markForCheck();
  }
}

/**
 * One option of a `<hq-radio-group>`; its label is `label`, or the projected content when `label` is empty.
 *
 * The host carries `hq-radio--checked` while the option is chosen and `hq-radio--disabled` while it cannot be picked, so a
 * screen can style its own markup from them. With `hideControl` the drawn dot is not shown and the projected content fills the
 * whole clickable option, for options drawn as cards or chips; the hidden native radio still handles arrow keys, focus and
 * screen readers, and the option shows a focus ring while it has keyboard focus.
 *
 *   <hq-radio-group formControlName="format" direction="row" class="export-cards">
 *     <hq-radio value="csv" hideControl><div class="export-card">...</div></hq-radio>
 *   </hq-radio-group>
 */
@Component({
  selector: 'hq-radio',
  standalone: false,
  templateUrl: './hq-radio.html',
  styleUrl: './hq-radio-group.scss',
  host: {
    '[class.hq-radio--checked]': 'isChosen',
    '[class.hq-radio--disabled]': 'isDisabled',
    '[class.hq-radio--plain]': 'hideControl',
  },
})
export class HqRadio {
  readonly group = inject(HqRadioGroup);

  @Input() value: unknown = null;
  /** Already-translated label; when empty, the projected content is the label. */
  @Input() label = '';
  @Input({ transform: booleanAttribute }) disabled = false;
  /** Hides the drawn dot so the projected content (a card, a chip) is the whole option. */
  @Input({ transform: booleanAttribute }) hideControl = false;

  get isDisabled(): boolean {
    return this.disabled || this.group.isDisabled;
  }

  get isChosen(): boolean {
    return this.group.isChosen(this.value);
  }

  onPick(): void {
    if (!this.isDisabled) this.group.choose(this.value);
  }
}
