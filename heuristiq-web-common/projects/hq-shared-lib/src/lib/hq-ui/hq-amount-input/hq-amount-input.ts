import { hqFlagInput, hqNumberInput, hqTextInput } from '../hq-field/hq-field.coercion';
import { ChangeDetectorRef, Component, Input, LOCALE_ID, forwardRef, inject } from '@angular/core';
import { AbstractControl, ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { HQ_AMOUNT_DEFAULT_CURRENCY, HQ_AMOUNT_FALLBACK_CURRENCY, HQ_AMOUNT_MAX_DECIMALS } from './hq-amount-input.constants';
import { hqReadSetting } from '../hq-app-setting';
import { HQ_AMOUNT_INPUT_DEFAULT_TEXTS, HQ_AMOUNT_INPUT_TEXTS, HqAmountInputTexts } from './hq-amount-input.texts';
import {
  HqAmount,
  hqCurrencyDecimals,
  hqCurrencySymbol,
  hqEditableAmount,
  hqFormatAmount,
  hqNormalizeCurrency,
  hqParseAmount,
  hqRoundAmount,
  hqSanitizeAmountTyping,
} from './hq-amount.util';

/**
 * Amount form control (ControlValueAccessor + Validator). Its value is `{ code, value }`: the ISO 4217 currency code and the
 * amount (null while empty). While typing only digits and one decimal mark are accepted, cut to the currency's decimal places
 * (2 for INR, 0 for JPY, 3 for KWD; 2 when unknown); when the field is left the amount is shown with grouping and exactly that
 * many decimals (₹ 1,00,000.00 in en-IN), and focusing it again shows the plain number to edit. The value is always rounded to
 * those decimals, so every screen and API sees the same amount.
 *
 * <p>The currency comes from the value, else the `currency` input, else HQ_AMOUNT_DEFAULT_CURRENCY, else INR. With more than one
 * entry in `currencies` a selector is shown; otherwise the currency is fixed and shown as its symbol. The library has no
 * translation dependency: pass `label` / `placeholder` per usage or provide HQ_AMOUNT_INPUT_TEXTS once.
 */
@Component({
  selector: 'hq-amount-input',
  standalone: false,
  templateUrl: './hq-amount-input.html',
  styleUrl: './hq-amount-input.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqAmountInput), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => HqAmountInput), multi: true },
  ],
})
export class HqAmountInput implements ControlValueAccessor, Validator {
  private readonly texts: HqAmountInputTexts = inject(HQ_AMOUNT_INPUT_TEXTS, { optional: true }) ?? HQ_AMOUNT_INPUT_DEFAULT_TEXTS;
  private readonly appCurrencySetting = inject(HQ_AMOUNT_DEFAULT_CURRENCY, { optional: true });
  readonly locale: string = inject(LOCALE_ID);
  private readonly cdr = inject(ChangeDetectorRef);

  /** Caller-supplied label; empty falls back to HQ_AMOUNT_INPUT_TEXTS.label(). */
  @Input({ transform: hqTextInput }) label = '';
  /** Set false when a wrapping form already renders its own label for this field. */
  @Input() showLabel = true;
  /** Caller-supplied placeholder; empty falls back to HQ_AMOUNT_INPUT_TEXTS.placeholder(). */
  @Input({ transform: hqTextInput }) placeholder = '';
  @Input() disabled = false;
  @Input() isRequired = false;
  /** Marks the amount invalid (red border) from outside, for a screen that judges the amount itself. */
  @Input({ transform: hqFlagInput }) invalid = false;
  /** Smallest allowed value (inclusive); null for none. */
  @Input({ transform: hqNumberInput }) min: number | null = 0;
  /** Largest allowed value (inclusive); null for none. */
  @Input({ transform: hqNumberInput }) max: number | null = null;
  /** Decimal places to use instead of the currency's own (capped at HQ_AMOUNT_MAX_DECIMALS); null uses the currency's. */
  @Input() decimals: number | null = null;
  /** Currencies the person may choose between (ISO codes); with fewer than two the currency is fixed. */
  @Input() currencies: string[] | null = null;
  /** Id of the amount box, for a `<label for>` outside the component. */
  @Input() inputId = '';
  /**
   * 'amount' (default): the control's value is `{ code, value }`. 'number': the value is the bare number and the currency comes
   * from the `currency` input, for a form that keeps the currency in a field of its own. 'text': as 'number', but the value is
   * decimal text with the currency's decimal places ("1500.00", '' when empty), for forms and APIs that carry amounts as text.
   */
  @Input() valueMode: 'amount' | 'number' | 'text' = 'amount';

  private _currency = '';
  /** The currency when the value names none (falls back to the app default). */
  @Input()
  set currency(code: string | null | undefined) {
    this._currency = hqNormalizeCurrency(code);
    if (this.valueMode !== 'amount' && this._currency && this._currency !== this.code) {
      // A bare number takes its currency from here: show it in the new currency's decimals.
      this.code = this._currency;
      if (this.amount !== null) {
        this.amount = hqRoundAmount(this.amount, this.places);
      }
      this.refreshDisplay();
    } else if (!this.code) {
      this.code = this.startCurrency;
    }
  }

  code = hqNormalizeCurrency(this.appCurrency) || HQ_AMOUNT_FALLBACK_CURRENCY;
  amount: number | null = null;
  /** What the box shows: the formatted amount, or the plain number while it has focus. */
  display = '';
  focused = false;
  touched = false;
  /** True when the text typed could not be read as an amount. */
  unreadable = false;

  private onChange: (value: HqAmount | number | string | null) => void = () => {};
  private onTouched: () => void = () => {};

  /** The app's default currency now (it may follow the current organization). */
  private get appCurrency(): string {
    return hqReadSetting(this.appCurrencySetting, HQ_AMOUNT_FALLBACK_CURRENCY);
  }

  private get startCurrency(): string {
    return this._currency || hqNormalizeCurrency(this.appCurrency) || HQ_AMOUNT_FALLBACK_CURRENCY;
  }

  /** Decimal places of the current currency (or the `decimals` override). */
  get places(): number {
    const override = this.decimals;
    return override === null || override === undefined ? hqCurrencyDecimals(this.code) : Math.max(0, Math.min(Math.trunc(override), HQ_AMOUNT_MAX_DECIMALS));
  }

  get symbol(): string {
    return hqCurrencySymbol(this.code, this.locale);
  }

  /** A minus may be typed only when the smallest allowed value is below zero. */
  get allowsNegative(): boolean {
    return this.min !== null && this.min !== undefined && this.min < 0;
  }

  get canChooseCurrency(): boolean {
    return (this.currencies?.length ?? 0) > 1;
  }

  get currencyOptions(): string[] {
    const list = (this.currencies ?? []).map(hqNormalizeCurrency).filter((c) => !!c);
    return list.includes(this.code) ? list : [this.code, ...list];
  }

  get displayLabel(): string {
    return this.label || this.texts.label();
  }

  get displayPlaceholder(): string {
    return this.placeholder || (this.places > 0 ? '0.' + '0'.repeat(this.places) : '0');
  }

  get currencyLabel(): string {
    return this.texts.currencyLabel();
  }

  get value(): HqAmount {
    return { code: this.code, value: this.amount };
  }

  // ---- ControlValueAccessor ------------------------------------------------------------------------

  /**
   * Takes a value from the form. While the user is typing, a screen that hands back the amount just typed (a new object each
   * keystroke, as `[ngModel]` + `(ngModelChange)` does) must not rewrite the box: showing it as "5.00" after the first "5"
   * moved the caret past the decimals and every further digit was dropped. So a value equal to what is being typed keeps the
   * typed text; a different value (the screen changed it) still replaces it.
   */
  writeValue(value: HqAmount | number | string | null | undefined): void {
    const asAmount = value !== null && typeof value === 'object' ? value : null;
    const code = hqNormalizeCurrency(asAmount?.code) || this.startCurrency;
    const given = asAmount ? asAmount.value : (value as number | string | null | undefined);
    const raw = typeof given === 'string' ? (given.trim() === '' ? null : Number(given)) : given;
    const sameAsTyped = this.focused && !this.unreadable && code === this.code;
    this.code = code;
    const next = raw === null || raw === undefined || !Number.isFinite(raw) ? null : hqRoundAmount(raw, this.places);
    if (sameAsTyped && next === this.amount) {
      return;
    }
    this.amount = next;
    this.unreadable = false;
    this.refreshDisplay();
    this.cdr.markForCheck();
  }

  registerOnChange(fn: (value: HqAmount | number | string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    this.cdr.markForCheck();
  }

  // ---- Validator -----------------------------------------------------------------------------------

  validate(_control?: AbstractControl): ValidationErrors | null {
    if (this.unreadable) {
      return { amountInvalid: true };
    }
    if (this.amount === null) {
      return this.isRequired ? { required: true } : null;
    }
    if (this.min !== null && this.min !== undefined && this.amount < this.min) {
      return { amountMin: { min: this.min, actual: this.amount } };
    }
    if (this.max !== null && this.max !== undefined && this.amount > this.max) {
      return { amountMax: { max: this.max, actual: this.amount } };
    }
    return null;
  }

  get hasError(): boolean {
    return this.invalid || (this.touched && this.validate() !== null);
  }

  get errorMessage(): string {
    const errors = this.validate();
    if (!errors) {
      return '';
    }
    if (errors['required']) {
      return this.texts.required(this.displayLabel);
    }
    if (errors['amountMin']) {
      return this.texts.min(hqFormatAmount(this.min, this.code, this.locale, this.places));
    }
    if (errors['amountMax']) {
      return this.texts.max(hqFormatAmount(this.max, this.code, this.locale, this.places));
    }
    return this.texts.invalid();
  }

  // ---- events --------------------------------------------------------------------------------------

  /** Keeps only what an amount may contain, cut to the currency's decimals, and emits the number typed so far. */
  onInput(input: HTMLInputElement): void {
    const cleaned = hqSanitizeAmountTyping(input.value, this.places, this.allowsNegative);
    if (cleaned !== input.value) {
      input.value = cleaned;
    }
    this.display = cleaned;
    const parsed = hqParseAmount(cleaned === '-' ? '' : cleaned);
    this.unreadable = parsed !== null && Number.isNaN(parsed);
    this.amount = parsed === null || this.unreadable ? null : hqRoundAmount(parsed, this.places);
    this.emit();
  }

  /** Pasted text is read the same way as typed text (grouping and a comma decimal mark are understood). */
  onPaste(event: ClipboardEvent, input: HTMLInputElement): void {
    const text = event.clipboardData?.getData('text') ?? '';
    const parsed = hqParseAmount(text);
    if (parsed === null || Number.isNaN(parsed)) {
      return;
    }
    event.preventDefault();
    input.value = hqEditableAmount(this.allowsNegative ? parsed : Math.abs(parsed), this.places);
    this.onInput(input);
  }

  onFocus(): void {
    this.focused = true;
    this.display = this.unreadable ? this.display : hqEditableAmount(this.amount, this.places);
  }

  onBlur(): void {
    this.focused = false;
    this.touched = true;
    this.refreshDisplay();
    this.onTouched();
  }

  /** A new currency keeps the amount, rounded to the new currency's decimals. */
  chooseCurrency(code: string): void {
    const next = hqNormalizeCurrency(code);
    if (!next || next === this.code) {
      return;
    }
    this.code = next;
    if (this.amount !== null) {
      this.amount = hqRoundAmount(this.amount, this.places);
    }
    this.refreshDisplay();
    this.emit();
  }

  private refreshDisplay(): void {
    if (this.unreadable) {
      return;
    }
    this.display = this.focused ? hqEditableAmount(this.amount, this.places) : hqFormatAmount(this.amount, this.code, this.locale, this.places);
  }

  private emit(): void {
    if (this.valueMode === 'number') {
      this.onChange(this.amount);
    } else if (this.valueMode === 'text') {
      this.onChange(hqEditableAmount(this.amount, this.places));
    } else {
      this.onChange(this.value);
    }
  }
}
