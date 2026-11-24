import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, booleanAttribute, forwardRef, inject } from '@angular/core';
import { AbstractControl, ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { HQ_FIELD_ID_PREFIX, HqFieldType } from './hq-field.constants';
import { hqFlagInput, hqNumberInput, hqTextInput } from './hq-field.coercion';
import {
  HqFieldValue,
  hqFieldAutocomplete,
  hqFieldCounterShown,
  hqFieldDateLimit,
  hqFieldIsNewPastDate,
  hqFieldLaterDate,
  hqFieldNumberLimit,
  hqFieldToday,
  hqFieldDateMode,
  hqFieldDescribedBy,
  hqFieldInputMode,
  hqFieldParse,
  hqFieldResolveType,
  hqFieldRows,
  hqFieldText,
} from './hq-field.helpers';
import { HQ_FIELD_DEFAULT_TEXTS, HQ_FIELD_TEXTS, HqFieldTexts } from './hq-field.texts';

let nextInstance = 0;

/**
 * The common input field (a form control: ControlValueAccessor) for every kind of typed-in value: text, e-mail, password,
 * number, telephone, URL, search, date, time, date and time, and a multi-line textarea. Every single-line field has the same
 * height (`--hq-control-height`, shared with `hq-searchable-select`, the phone and the amount inputs), so a form lines up
 * whatever mix of controls it uses.
 *
 * <p>It draws the label (with the required star), the control, the error, the hint and the length counter. With
 * `[showLabel]="false"` it draws only the control, for a screen that lays out its own labels. The value is a number for type
 * "number" and text for the others. The library has no translation dependency: pass labels, hints and errors already
 * translated, and provide HQ_FIELD_TEXTS for the words the field adds itself (show / hide password, the counter).
 *
 * <p>Extras for screens that need the inner control: type "month" (the browser's month picker, value "YYYY-MM");
 * `autoGrow` makes a textarea follow its content (up to `maxRows`); `focusControl()`, `selectControl()` and the `control`
 * getter reach the inner input or textarea (e.g. `@ViewChild(HqField)` to focus it or read the caret); and `role`,
 * `ariaExpanded`, `ariaControls`, `ariaActiveDescendant` and `ariaAutocomplete` are set on the inner control so the
 * field can be the input of a hand-built combobox.
 */
@Component({
  selector: 'hq-field',
  standalone: false,
  templateUrl: './hq-field.html',
  styleUrl: './hq-field.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqField), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => HqField), multi: true },
  ],
})
export class HqField implements ControlValueAccessor, Validator, OnChanges, AfterViewInit {
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  private readonly texts: HqFieldTexts = inject(HQ_FIELD_TEXTS, { optional: true }) ?? HQ_FIELD_DEFAULT_TEXTS;

  private readonly uid = `${HQ_FIELD_ID_PREFIX}-${nextInstance++}`;

  /** One of the field types; an unknown one is drawn as plain text. */
  @Input() set type(type: HqFieldType | string | null | undefined) {
    this.kind = hqFieldResolveType(type);
  }
  get type(): HqFieldType {
    return this.kind;
  }
  /** Already translated; drawn above the control unless `showLabel` is off. */
  @Input({ transform: hqTextInput }) label = '';
  /** Draw the label (and the required star). Off draws only the control. */
  @Input() showLabel = true;
  @Input({ transform: hqFlagInput }) required = false;
  @Input({ transform: hqTextInput }) placeholder = '';
  /** Helper text under the field. */
  @Input({ transform: hqTextInput }) hint = '';
  /** Error text under the field (also turns the border red); empty or null means no error. */
  @Input() error: string | null | undefined = null;
  /** Longest text the field accepts. */
  @Input({ transform: hqNumberInput }) maxLength: number | null = null;
  /** Show the "length / limit" counter: null shows it for a text or textarea field that has a limit. */
  @Input() showCounter: boolean | null | undefined = null;
  @Input({ transform: hqFlagInput }) readonly = false;
  /** The browser's autofill hint (for example "new-password"); empty leaves the browser's own behaviour. */
  @Input({ transform: hqTextInput }) autocomplete = '';
  /** Id of the control (so a `label for` elsewhere can point at it); generated when empty. */
  @Input({ transform: hqTextInput }) inputId = '';
  @Input({ transform: hqTextInput }) name = '';
  /** Accessible name when the label is not drawn. */
  @Input({ transform: hqTextInput }) ariaLabel = '';
  /** Visible rows of a textarea. */
  @Input({ transform: hqNumberInput }) rows: number | null = null;
  /** Limits and step of a number or date field. */
  @Input() min: number | string | null | undefined = null;
  /**
   * For a date or date-time field holding a date that starts or ends something (an "effective from / to", a "valid until"):
   * days before today are greyed out in the picker, and a past date typed in makes the field invalid with its own message.
   * The value the field was loaded with stays valid, so a record that already started can still be edited and saved. Leave
   * it off for dates that are naturally in the past (a date of birth, a filter, a report period).
   */
  @Input({ transform: booleanAttribute }) noPastDates = false;
  @Input() max: number | string | null | undefined = null;
  @Input() step: number | string | null | undefined = null;
  /** Icon classes (for example "bi bi-search") drawn at the start of the field. */
  @Input({ transform: hqTextInput }) icon = '';
  /** Mark the field invalid (red border) without a message; an `error` text does the same and also shows the text. */
  @Input({ transform: hqFlagInput }) invalid = false;
  /** Overrides the on-screen keyboard the type would bring up (for example "numeric"). */
  @Input({ transform: hqTextInput }) inputmode = '';
  /** The browser's spell checker for this field; null leaves the browser's own behaviour. */
  @Input() spellcheck: boolean | string | null | undefined = null;
  /** Ids (space separated) of text elsewhere on the screen that describe the field; added to the field's own hint and error. */
  @Input({ transform: hqTextInput }) ariaDescribedBy = '';
  /** CSS text-transform of what is shown in the control (for example "uppercase" for a tax or bank code). */
  @Input({ transform: hqTextInput }) textTransform = '';
  /** Id of a `<datalist>` that offers suggestions while typing. */
  @Input({ transform: hqTextInput }) list = '';
  /** Draw the show / hide button of a password field. Off for a screen that has its own. */
  @Input() passwordToggle = true;
  /** Put the cursor in the field when it appears. */
  @Input({ transform: booleanAttribute }) autofocus = false;
  /** Type "textarea": the height follows the content as it is typed or set, up to `maxRows` (then it scrolls). */
  @Input({ transform: booleanAttribute }) autoGrow = false;
  /** With `autoGrow`: the most rows the textarea grows to; null for no limit. */
  @Input({ transform: hqNumberInput }) maxRows: number | null = null;
  /** ARIA for a field used as the input of a combobox: put on the inner control; empty / null leaves the attribute out. */
  @Input({ transform: hqTextInput }) role = '';
  @Input() ariaExpanded: boolean | null = null;
  @Input({ transform: hqTextInput }) ariaControls = '';
  @Input() ariaActiveDescendant: string | null = null;
  @Input({ transform: hqTextInput }) ariaAutocomplete = '';
  /** The control took the focus. */
  @Output() focus = new EventEmitter<FocusEvent>();
  /** The control lost the focus. */
  @Output() blur = new EventEmitter<FocusEvent>();

  /**
   * The value to show when the field is not bound to a form control (a read-only display): the same as `writeValue`.
   * With `ngModel` or a form control the form owns the value.
   */
  @Input() set value(value: HqFieldValue | undefined) {
    this.text = hqFieldText(value);
  }

  /** Disables the field. A form control's own disabled state sets it too, so a field without a form can use it as an input. */
  @Input({ transform: booleanAttribute }) disabled = false;
  /** The password is shown as text. */
  revealed = false;

  /** What the control shows: what the user typed, kept as typed (so "1." in a number field is not rewritten to "1"). */
  text = '';

  private kind: HqFieldType = 'text';
  private onChange: (value: HqFieldValue) => void = () => {};
  /** The text the form last gave the field (see `noPastDates`); null when it gave none. */
  private loadedText: string | null = null;
  private onTouched: () => void = () => {};

  get t(): HqFieldTexts {
    return this.texts;
  }

  /** For the types date, time and datetime-local: which kind of date-time field draws it (the common picker, not the browser's). */
  get dateMode(): 'date' | 'time' | 'datetime' | null {
    return hqFieldDateMode(this.kind);
  }

  get dateMin(): string | null {
    const own = hqFieldDateLimit(this.min);
    return this.noPastDates && this.dateMode ? hqFieldLaterDate(own, hqFieldToday()) : own;
  }

  /** A date before today chosen in a `noPastDates` field (the date it was loaded with excepted). */
  get pastDateRejected(): boolean {
    return this.noPastDates && !!this.dateMode && hqFieldIsNewPastDate(this.text, this.loadedText, hqFieldToday());
  }

  /** The screen's own error first, else the field's past-date message. */
  get shownError(): string | null {
    return this.error || (this.pastDateRejected ? this.texts.pastDate() : null);
  }

  get dateMax(): string | null {
    return hqFieldDateLimit(this.max);
  }

  get isTextarea(): boolean {
    return this.kind === 'textarea';
  }

  /** The `type` attribute of the input: a revealed password is plain text. */
  get inputType(): string {
    return this.kind === 'password' && this.revealed ? 'text' : this.kind;
  }

  get controlId(): string {
    return this.inputId || `${this.uid}-input`;
  }

  get hintId(): string {
    return `${this.controlId}-hint`;
  }

  get errorId(): string {
    return `${this.controlId}-error`;
  }

  get hasError(): boolean {
    return !!this.shownError || this.invalid;
  }

  get describedBy(): string | null {
    const own = hqFieldDescribedBy(this.controlId, !!this.hint, !!this.shownError);
    return [own, this.ariaDescribedBy.trim()].filter(Boolean).join(' ') || null;
  }

  get inputMode(): string | null {
    return this.inputmode || hqFieldInputMode(this.kind);
  }

  get autofill(): string | null {
    return hqFieldAutocomplete(this.autocomplete);
  }

  get textareaRows(): number {
    return hqFieldRows(this.rows);
  }

  /** The counter text ("12 / 254"), or null when it is not shown. */
  get counter(): string | null {
    if (!hqFieldCounterShown(this.kind, this.maxLength, this.showCounter ?? null) || this.maxLength === null) return null;
    return this.texts.counter(this.text.length, this.maxLength);
  }

  get showPasswordToggle(): boolean {
    return this.passwordToggle && this.kind === 'password' && !this.disabled && !this.readonly;
  }

  writeValue(value: HqFieldValue | undefined): void {
    this.text = hqFieldText(value);
    // The value given by the form (a loaded record) stays valid under noPastDates even when it lies in the past.
    this.loadedText = this.text || null;
    this.scheduleGrow();
  }

  /** The inner input or textarea (null for the date-time types, which draw their own picker, and before the view exists). */
  get control(): HTMLInputElement | HTMLTextAreaElement | null {
    return this.host.nativeElement.querySelector<HTMLInputElement | HTMLTextAreaElement>('.hq-field__control');
  }

  /** Puts the cursor in the field (the date-time types focus their own input). */
  focusControl(): void {
    this.host.nativeElement.querySelector<HTMLElement>('.hq-field__control, input.hq-dt__control')?.focus();
  }

  /** Selects the field's text. */
  selectControl(): void {
    this.control?.select();
  }

  /** Sets the textarea's height to its content (`autoGrow`), capped at `maxRows`. */
  grow(): void {
    const box = this.control;
    if (!this.autoGrow || !this.isTextarea || !(box instanceof HTMLTextAreaElement)) return;
    box.style.height = 'auto';
    let height = box.scrollHeight;
    if (this.maxRows) {
      const style = getComputedStyle(box);
      const line = parseFloat(style.lineHeight) || parseFloat(style.fontSize) * 1.4 || 20;
      const extra = parseFloat(style.paddingTop) + parseFloat(style.paddingBottom) + parseFloat(style.borderTopWidth) + parseFloat(style.borderBottomWidth);
      const cap = Math.ceil(line * this.maxRows + (Number.isFinite(extra) ? extra : 0));
      box.style.overflowY = height > cap ? 'auto' : 'hidden';
      height = Math.min(height, cap);
    } else {
      box.style.overflowY = 'hidden';
    }
    box.style.height = `${height}px`;
  }

  private scheduleGrow(): void {
    if (this.autoGrow) setTimeout(() => this.grow());
  }

  registerOnChange(fn: (value: HqFieldValue) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled = disabled;
  }

  onInput(raw: string): void {
    this.text = raw;
    this.onChange(hqFieldParse(this.kind, raw));
    if (this.autoGrow) this.grow();
  }

  /** A value picked or typed in the date-time field (null while it is empty or incomplete). */
  onDateValue(value: string | null): void {
    this.text = value ?? '';
    this.onChange(value ?? '');
  }

  onBlur(event?: FocusEvent): void {
    this.onTouched();
    if (event) this.blur.emit(event);
  }

  onFocus(event: FocusEvent): void {
    this.focus.emit(event);
  }

  ngAfterViewInit(): void {
    if (this.autofocus) this.focusControl();
    this.scheduleGrow();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['min'] || changes['max'] || changes['noPastDates']) this.onValidatorChange();
    if (changes['value'] || changes['autoGrow'] || changes['maxRows']) this.scheduleGrow();
  }

  /**
   * The number limits of a number field, as a native number input's `min` and `max` would apply them; and, with
   * `noPastDates`, a date before today that is not the loaded one (`pastDate`), so the form cannot be saved with it.
   */
  validate(control: AbstractControl): ValidationErrors | null {
    if (this.noPastDates && this.dateMode) {
      const value = typeof control.value === 'string' ? control.value : '';
      const today = hqFieldToday();
      return hqFieldIsNewPastDate(value, this.loadedText, today) ? { pastDate: { today, actual: value } } : null;
    }
    if (this.kind !== 'number') return null;
    const value = control.value;
    if (typeof value !== 'number' || Number.isNaN(value)) return null;
    const min = hqFieldNumberLimit(this.min);
    const max = hqFieldNumberLimit(this.max);
    if (min !== null && value < min) return { min: { min, actual: value } };
    if (max !== null && value > max) return { max: { max, actual: value } };
    return null;
  }

  registerOnValidatorChange(fn: () => void): void {
    this.onValidatorChange = fn;
  }

  private onValidatorChange: () => void = () => {};

  toggleReveal(): void {
    this.revealed = !this.revealed;
  }
}
