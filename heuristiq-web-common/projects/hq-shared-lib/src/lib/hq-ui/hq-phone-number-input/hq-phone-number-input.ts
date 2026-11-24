import { ConnectedPosition } from '@angular/cdk/overlay';
import { Component, ElementRef, Input, LOCALE_ID, ViewChild, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { hqReadSetting } from '../hq-app-setting';
import { HQ_PHONE_DEFAULT_COUNTRY, HQ_PHONE_FALLBACK_COUNTRY, HQ_PHONE_MAX_E164_DIGITS } from './hq-phone-number-input.constants';
import { HQ_PHONE_NUMBER_INPUT_DEFAULT_TEXTS, HQ_PHONE_NUMBER_INPUT_TEXTS, HqPhoneNumberInputTexts } from './hq-phone-number-input.texts';
import {
  HqPhoneCountry,
  hqIsValidPhone,
  hqParsePhone,
  hqPhoneCallingCode,
  hqPhoneCountries,
  hqPhoneFlag,
  hqToInternational,
} from './hq-phone.util';

/** Where the country list opens: under the field, or above it when there is no room below. */
const COUNTRY_PANEL_POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -6 },
];

/** Narrowest the country list is drawn, so names and codes stay readable under a narrow field. */
const COUNTRY_PANEL_MIN_WIDTH_PX = 320;

/**
 * Phone number form control (ControlValueAccessor + Validator) for a number of any country: a country-code dropdown (flag,
 * searchable list) and the number. The value of the control is the international form with a plus and no spaces
 * (+919876543210), or '' when empty, so every screen, API and database sees one format. A value saved earlier without a
 * country code (a bare 10-digit Indian mobile) is read in the default country and comes back in the field correctly; it is
 * emitted in the new form on the next edit.
 *
 * <p>The library has no translation dependency: pass `label` / `placeholder` per usage, or provide
 * HQ_PHONE_NUMBER_INPUT_TEXTS once for app-wide defaults. The country the field starts on comes from the `defaultCountry`
 * input, else HQ_PHONE_DEFAULT_COUNTRY, else India.
 */
@Component({
  selector: 'hq-phone-number-input',
  standalone: false,
  templateUrl: './hq-phone-number-input.html',
  styleUrl: './hq-phone-number-input.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqPhoneNumberInput), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => HqPhoneNumberInput), multi: true },
  ],
})
export class HqPhoneNumberInput implements ControlValueAccessor, Validator {
  private readonly texts: HqPhoneNumberInputTexts =
    inject(HQ_PHONE_NUMBER_INPUT_TEXTS, { optional: true }) ?? HQ_PHONE_NUMBER_INPUT_DEFAULT_TEXTS;
  private readonly appDefaultCountrySetting = inject(HQ_PHONE_DEFAULT_COUNTRY, { optional: true });
  /** The app's default country now (it may follow the current organization). */
  private get appDefaultCountry(): string {
    return hqReadSetting(this.appDefaultCountrySetting, HQ_PHONE_FALLBACK_COUNTRY);
  }
  private readonly locale: string = inject(LOCALE_ID);

  @ViewChild('searchBox') private searchBox?: ElementRef<HTMLInputElement>;
  @ViewChild('countryList') private countryList?: ElementRef<HTMLElement>;
  @ViewChild('countryTrigger') private countryTrigger?: ElementRef<HTMLButtonElement>;

  /** Caller-supplied label; empty falls back to HQ_PHONE_NUMBER_INPUT_TEXTS.label(). */
  @Input() label = '';
  /** Set false when a wrapping form already renders its own label for this field. */
  @Input() showLabel = true;
  @Input() variant: 'default' | 'compact' = 'default';
  @Input() disabled = false;
  @Input() isRequired = false;
  /** Caller-supplied placeholder; empty falls back to HQ_PHONE_NUMBER_INPUT_TEXTS.placeholder(). */
  @Input() placeholder = '';
  /** Limits the country list (ISO 3166 alpha-2 codes); empty or unset offers every country. */
  @Input() allowedCountries: string[] | null = null;

  private _defaultCountry: string | null = null;
  /** The country the field starts on and reads a number without a country code in; falls back to the app default. */
  @Input()
  set defaultCountry(country: string | null | undefined) {
    this._defaultCountry = country ? country.toUpperCase() : null;
    if (!this.nationalDigits) {
      this.selectedCountry = this.effectiveDefaultCountry;
    }
  }
  get defaultCountry(): string {
    return this.effectiveDefaultCountry;
  }

  /** Optional initial value outside a form (the control value takes over once bound). */
  @Input()
  set value(value: string | null | undefined) {
    this.apply(value);
  }
  get value(): string {
    return hqToInternational(this.selectedCountry, this.nationalDigits);
  }

  readonly panelPositions = COUNTRY_PANEL_POSITIONS;
  selectedCountry: string = this.appDefaultCountry.toUpperCase();
  nationalDigits = '';
  touched = false;
  /** True while the country list is open. */
  listOpen = false;
  query = '';
  /** Index (in the filtered list) of the row the keyboard is on. */
  activeIndex = 0;
  panelWidth = COUNTRY_PANEL_MIN_WIDTH_PX;

  private countriesCache: { key: string; list: HqPhoneCountry[] } | null = null;
  private filterCache: { key: string; list: HqPhoneCountry[] } | null = null;
  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  private get effectiveDefaultCountry(): string {
    return (this._defaultCountry ?? this.appDefaultCountry).toUpperCase();
  }

  /** The country list for the current locale and `allowedCountries`; rebuilt only when either changes. */
  get countries(): HqPhoneCountry[] {
    const key = `${this.locale}|${(this.allowedCountries ?? []).join(',')}`;
    if (this.countriesCache?.key !== key) {
      this.countriesCache = { key, list: hqPhoneCountries(this.locale, this.allowedCountries) };
    }
    return this.countriesCache.list;
  }

  /** The countries matching the search text (name, ISO code or calling code); all of them when it is empty. */
  get filteredCountries(): HqPhoneCountry[] {
    const term = this.query.trim().toLowerCase().replace(/^\+/, '');
    const key = `${this.countriesCache?.key ?? ''}|${term}`;
    if (this.filterCache?.key !== key) {
      const all = this.countries;
      this.filterCache = {
        key,
        list: term
          ? all.filter((c) => c.name.toLowerCase().includes(term) || c.iso.toLowerCase() === term || c.callingCode.startsWith(term))
          : all,
      };
    }
    return this.filterCache.list;
  }

  get callingCode(): string {
    return hqPhoneCallingCode(this.selectedCountry);
  }

  get selectedFlag(): string {
    return hqPhoneFlag(this.selectedCountry);
  }

  get selectedCountryName(): string {
    return this.countries.find((country) => country.iso === this.selectedCountry)?.name ?? this.selectedCountry;
  }

  writeValue(value: string | null): void {
    this.apply(value);
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.closeList();
    }
  }

  validate(): ValidationErrors | null {
    if (!this.nationalDigits) {
      return this.isRequired ? { required: true } : null;
    }
    return hqIsValidPhone(this.value, this.selectedCountry) ? null : { pattern: true };
  }

  // ---- the country list ------------------------------------------------------------------------

  toggleList(origin: HTMLElement): void {
    if (this.disabled) return;
    if (this.listOpen) {
      this.closeList();
      return;
    }
    this.query = '';
    this.panelWidth = Math.max(origin.getBoundingClientRect().width, COUNTRY_PANEL_MIN_WIDTH_PX);
    this.activeIndex = Math.max(0, this.filteredCountries.findIndex((c) => c.iso === this.selectedCountry));
    this.listOpen = true;
  }

  closeList(): void {
    if (!this.listOpen) return;
    this.listOpen = false;
    this.onBlur();
  }

  /** Called when the list is drawn: focuses the search box and scrolls the chosen country into view. */
  onListAttached(): void {
    setTimeout(() => {
      this.searchBox?.nativeElement.focus();
      this.scrollActiveIntoView('center');
    });
  }

  onQuery(value: string): void {
    this.query = value;
    this.activeIndex = 0;
    this.countryList?.nativeElement.scrollTo({ top: 0 });
  }

  chooseCountry(country: HqPhoneCountry): void {
    this.selectedCountry = country.iso;
    this.listOpen = false;
    this.emit();
    this.countryTrigger?.nativeElement.focus();
  }

  onListKey(event: KeyboardEvent): void {
    const list = this.filteredCountries;
    switch (event.key) {
      case 'ArrowDown':
        this.activeIndex = Math.min(list.length - 1, this.activeIndex + 1);
        break;
      case 'ArrowUp':
        this.activeIndex = Math.max(0, this.activeIndex - 1);
        break;
      case 'PageDown':
        this.activeIndex = Math.min(list.length - 1, this.activeIndex + 8);
        break;
      case 'PageUp':
        this.activeIndex = Math.max(0, this.activeIndex - 8);
        break;
      case 'Home':
        this.activeIndex = 0;
        break;
      case 'End':
        this.activeIndex = Math.max(0, list.length - 1);
        break;
      case 'Enter':
        event.preventDefault();
        if (list[this.activeIndex]) this.chooseCountry(list[this.activeIndex]);
        return;
      case 'Escape':
        event.preventDefault();
        this.closeList();
        this.countryTrigger?.nativeElement.focus();
        return;
      case 'Tab':
        this.closeList();
        return;
      default:
        return;
    }
    event.preventDefault();
    this.scrollActiveIntoView('nearest');
  }

  /** Opens the list from the keyboard (arrow down on the trigger). */
  onTriggerKey(event: KeyboardEvent, origin: HTMLElement): void {
    if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && !this.listOpen) {
      event.preventDefault();
      this.toggleList(origin);
    }
  }

  // ---- the number ------------------------------------------------------------------------------

  onInput(rawInput: string): void {
    // Digits only. A pasted full number ("+91 98765 43210", "0091...") is split into country and digits instead of
    // ending up in the national part.
    const pasted = rawInput.trim();
    if (pasted.startsWith('+')) {
      const parts = hqParsePhone(pasted, this.selectedCountry);
      this.selectedCountry = parts.country;
      this.nationalDigits = parts.national.slice(0, HQ_PHONE_MAX_E164_DIGITS);
    } else {
      this.nationalDigits = pasted.replace(/\D/g, '').slice(0, HQ_PHONE_MAX_E164_DIGITS - this.callingCode.length);
    }
    this.emit();
  }

  onBlur(): void {
    this.touched = true;
    this.onTouched();
  }

  trackCountry(_: number, country: HqPhoneCountry): string {
    return country.iso;
  }

  get displayLabel(): string {
    return this.label || this.texts.label();
  }

  get displayPlaceholder(): string {
    return this.placeholder || this.texts.placeholder();
  }

  get countryLabel(): string {
    return this.text('countryLabel');
  }

  get searchPlaceholder(): string {
    return this.text('searchPlaceholder');
  }

  get noCountryFound(): string {
    return this.text('noCountryFound');
  }

  get hasError(): boolean {
    return this.touched && this.validate() !== null;
  }

  get errorMessage(): string {
    if (!this.touched) return '';
    const errors = this.validate();
    if (!errors) return '';
    if (errors['required']) return this.texts.required(this.displayLabel);
    if (errors['pattern']) return this.texts.invalid();
    return '';
  }

  private text(key: 'countryLabel' | 'searchPlaceholder' | 'noCountryFound'): string {
    return this.texts[key]?.() ?? HQ_PHONE_NUMBER_INPUT_DEFAULT_TEXTS[key]();
  }

  private scrollActiveIntoView(block: ScrollLogicalPosition): void {
    const row = this.countryList?.nativeElement.querySelector<HTMLElement>(`[data-index="${this.activeIndex}"]`);
    row?.scrollIntoView({ block });
  }

  private apply(value: string | null | undefined): void {
    const parts = hqParsePhone(value, this.effectiveDefaultCountry);
    this.selectedCountry = parts.country;
    this.nationalDigits = parts.national;
  }

  private emit(): void {
    this.onChange(this.value);
  }
}
