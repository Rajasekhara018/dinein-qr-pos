import { booleanAttribute, ChangeDetectorRef, Component, EventEmitter, forwardRef, inject, Input, Output } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/** Theme-aware boolean switch. Supports ngModel, reactive forms, or checked/checkedChange. */
@Component({
  selector: 'hq-slide-toggle',
  standalone: false,
  templateUrl: './hq-slide-toggle.html',
  styleUrl: './hq-slide-toggle.scss',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqSlideToggle), multi: true }],
})
export class HqSlideToggle implements ControlValueAccessor {
  private readonly cdr = inject(ChangeDetectorRef);
  @Input({ transform: booleanAttribute }) checked = false;
  @Input({ transform: booleanAttribute }) disabled = false;
  @Input({ transform: booleanAttribute }) loading = false;
  @Input({ transform: booleanAttribute }) hideIcon = true;
  @Input() labelPosition: 'before' | 'after' = 'after';
  @Input() size: 'compact' | 'regular' = 'regular';
  @Input() ariaLabel: string | null = null;
  @Input() ariaLabelledby: string | null = null;
  @Input() ariaDescribedby: string | null = null;
  @Output() readonly checkedChange = new EventEmitter<boolean>();

  private formDisabled = false;
  private onChange: (value: boolean) => void = () => {};
  private onTouched: () => void = () => {};

  get isDisabled(): boolean {
    return this.disabled || this.formDisabled || this.loading;
  }

  writeValue(value: boolean | null | undefined): void {
    this.checked = value === true;
    this.cdr.markForCheck();
  }

  registerOnChange(fn: (value: boolean) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }

  setDisabledState(disabled: boolean): void {
    this.formDisabled = disabled;
    this.cdr.markForCheck();
  }

  setChecked(checked: boolean): void {
    if (this.isDisabled || checked === this.checked) return;
    this.checked = checked;
    this.onChange(checked);
    this.checkedChange.emit(checked);
  }

  markTouched(): void { this.onTouched(); }
}
