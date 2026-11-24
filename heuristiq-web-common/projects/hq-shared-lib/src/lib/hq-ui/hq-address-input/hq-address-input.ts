import { Component, Input, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { HqAddress } from './hq-address.model';
import { HQ_ADDRESS_DEFAULT_COUNTRY, HQ_INDIAN_PINCODE_PATTERN, hqEmptyAddress } from './hq-address-input.constants';
import { HQ_ADDRESS_INPUT_DEFAULT_TEXTS, HQ_ADDRESS_INPUT_TEXTS, HqAddressInputTexts } from './hq-address-input.texts';

export type HqAddressErrorField = 'lines' | 'city' | 'state' | 'pincode';

/**
 * Structured address form control (ControlValueAccessor + Validator) for an HqAddress value.
 * The library has no translation dependency: pass `label` per usage, or provide
 * HQ_ADDRESS_INPUT_TEXTS once for the app-wide field labels, placeholders and messages.
 */
@Component({
  selector: 'hq-address-input',
  standalone: false,
  templateUrl: './hq-address-input.html',
  styleUrl: './hq-address-input.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqAddressInput), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => HqAddressInput), multi: true },
  ],
})
export class HqAddressInput implements ControlValueAccessor, Validator {
  readonly texts: HqAddressInputTexts =
    inject(HQ_ADDRESS_INPUT_TEXTS, { optional: true }) ?? HQ_ADDRESS_INPUT_DEFAULT_TEXTS;

  /** Caller-supplied label; empty falls back to HQ_ADDRESS_INPUT_TEXTS.label(). */
  @Input() label = '';
  @Input() variant: 'default' | 'compact' = 'default';
  @Input() disabled = false;
  @Input() isRequired = false;
  @Input() showLandmark = true;
  /** India-only today; kept as an input rather than hardcoded in case that changes. */
  @Input() country = HQ_ADDRESS_DEFAULT_COUNTRY;

  private _value: HqAddress = hqEmptyAddress();
  @Input()
  set value(value: HqAddress | null | undefined) {
    this._value = { ...hqEmptyAddress(), ...(value ?? {}) };
  }
  get value(): HqAddress {
    return this._value;
  }

  touched = false;

  private onChange: (value: HqAddress) => void = () => {};
  private onTouched: () => void = () => {};

  get displayLabel(): string {
    return this.label || this.texts.label();
  }

  writeValue(value: HqAddress | null): void {
    this._value = { ...hqEmptyAddress(), ...(value ?? {}) };
  }

  registerOnChange(fn: (value: HqAddress) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  validate(): ValidationErrors | null {
    const errors: ValidationErrors = {};

    if (this.isRequired) {
      if (!this._value.lines?.trim()) errors['linesRequired'] = true;
      if (!this._value.city?.trim()) errors['cityRequired'] = true;
      if (!this._value.state?.trim()) errors['stateRequired'] = true;
      if (!this._value.pincode?.trim()) errors['pincodeRequired'] = true;
    }

    if (this._value.pincode && !HQ_INDIAN_PINCODE_PATTERN.test(this._value.pincode)) {
      errors['pincodePattern'] = true;
    }

    return Object.keys(errors).length ? errors : null;
  }

  updateField(field: keyof HqAddress, rawValue: string): void {
    if (field === 'pincode') {
      this._value = { ...this._value, pincode: rawValue.replace(/\D/g, '').slice(0, 6) };
    } else {
      this._value = { ...this._value, [field]: rawValue };
    }
    this.onChange(this._value);
  }

  onBlur(): void {
    this.touched = true;
    this.onTouched();
  }

  get hasError(): boolean {
    return this.touched && this.validate() !== null;
  }

  fieldError(field: HqAddressErrorField): string {
    if (!this.touched) return '';
    const errors = this.validate();
    if (!errors) return '';
    if (field === 'lines' && errors['linesRequired']) return this.texts.linesRequired();
    if (field === 'city' && errors['cityRequired']) return this.texts.cityRequired();
    if (field === 'state' && errors['stateRequired']) return this.texts.stateRequired();
    if (field === 'pincode' && errors['pincodeRequired']) return this.texts.pincodeRequired();
    if (field === 'pincode' && errors['pincodePattern']) return this.texts.pincodeInvalid();
    return '';
  }
}
