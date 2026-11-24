import { Component, EventEmitter, Input, OnDestroy, Output, QueryList, ViewChildren, inject } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { HqPhoneNumberInput } from '../hq-phone-number-input/hq-phone-number-input';
import {
  HQ_SEARCHABLE_SELECT_DEFAULT_TEXTS,
  HQ_SEARCHABLE_SELECT_TEXTS,
  HqSearchableSelectTexts,
} from '../hq-searchable-select/hq-searchable-select.texts';
import { HQ_PHONE_DEFAULT_COUNTRY, HQ_PHONE_FALLBACK_COUNTRY } from '../hq-phone-number-input/hq-phone-number-input.constants';
import { hqReadSetting } from '../hq-app-setting';
import { HQ_ONBOARDING_EMAIL_PATTERN } from './hq-onboarding-form.constants';
import { hqOnboardingCleanAnswers, hqOnboardingDrawnFields, hqOnboardingFieldKind } from './hq-onboarding-form.helpers';
import {
  HqOnboardingAnswers,
  HqOnboardingDateLimit,
  HqOnboardingField,
  HqOnboardingFieldKind,
  HqOnboardingStep,
} from './hq-onboarding-form.model';
import { HQ_ONBOARDING_FORM_DEFAULT_TEXTS, HQ_ONBOARDING_FORM_TEXTS, HqOnboardingFormTexts } from './hq-onboarding-form.texts';

let nextInstance = 0;

/**
 * Draws an onboarding schema (steps of fields, the shape Rider_OnboardingService serves from `active-schema`) as a
 * reactive form. Presentation only: it does not fetch the schema or submit the answers. The parent passes the parsed
 * `steps`, reads the answers (`answers`, `answersChange`), the validity (`valid`, `validityChange`) and calls
 * `markAllTouched()` before it decides whether to submit.
 *
 * <p>Text, pattern, email, number, date, select and checkbox fields are drawn with the schema's label, required flag and
 * min / max / length / regex rules (the schema's own pattern message is shown when the regex fails); phone fields use
 * `hq-phone-number-input` (any country, value in E.164). Document fields are skipped. Per-field `optionOverrides` turn a
 * field into a select of the given choices (for example options configured elsewhere); `optionLabel` translates the
 * visible text of a choice while the value stays the code.
 *
 * <p>The library has no translation dependency: provide HQ_ONBOARDING_FORM_TEXTS for the messages (English otherwise).
 */
@Component({
  selector: 'hq-onboarding-form',
  standalone: false,
  templateUrl: './hq-onboarding-form.html',
  styleUrl: './hq-onboarding-form.scss',
})
export class HqOnboardingForm implements OnDestroy {
  private readonly texts: HqOnboardingFormTexts = inject(HQ_ONBOARDING_FORM_TEXTS, { optional: true }) ?? HQ_ONBOARDING_FORM_DEFAULT_TEXTS;
  private readonly selectTexts: HqSearchableSelectTexts =
    inject(HQ_SEARCHABLE_SELECT_TEXTS, { optional: true }) ?? HQ_SEARCHABLE_SELECT_DEFAULT_TEXTS;
  private readonly appDefaultCountrySetting = inject(HQ_PHONE_DEFAULT_COUNTRY, { optional: true });
  /** The app's default country now (it may follow the current organization). */
  private get appDefaultCountry(): string {
    return hqReadSetting(this.appDefaultCountrySetting, HQ_PHONE_FALLBACK_COUNTRY);
  }

  @ViewChildren(HqPhoneNumberInput) private phoneInputs?: QueryList<HqPhoneNumberInput>;

  /** Prefix that keeps element ids unique when several forms share a page. */
  readonly uid = `hq-onb-${nextInstance++}`;

  /** The form: one control per drawn field, keyed by the field key. */
  form = new FormGroup<Record<string, FormControl>>({});

  /** Choices for a field by key; a non-empty list draws that field as a select whatever its schema type. */
  @Input() optionOverrides: Record<string, readonly string[]> = {};
  /**
   * Type-ahead suggestions by field key. Unlike `optionOverrides` the field stays a free text input (drawn with a datalist),
   * so any value can still be typed ("Other", a city that is not listed) and nothing blocks the form.
   */
  @Input() suggestions: Record<string, readonly string[]> = {};

  /**
   * Keys of the text fields drawn as a searchable drop-down (`hq-searchable-select`) over `suggestions[key]`, with "Other / not
   * listed" for a value that is not in the list. A key listed here is drawn that way whatever its list holds (an empty list is
   * the free-text box), and a field in `dependsOn` waits, with a hint, until its parent is answered. Fields that are not listed
   * keep the plain text box with type-ahead suggestions.
   */
  @Input() searchableFields: readonly string[] = [];
  /** Searchable fields whose list is still being loaded: shown disabled with a spinner. */
  @Input() loadingFields: readonly string[] = [];
  /** Searchable fields whose list could not be loaded: a free-text box with a notice and a retry action. */
  @Input() failedFields: readonly string[] = [];
  /** The retry action of a failed list was used; carries the field key. */
  @Output() retryField = new EventEmitter<string>();
  /** Child field key -> parent field key: when the parent's answer changes by hand, the child's answer is cleared. */
  @Input()
  set dependsOn(value: Record<string, string> | null | undefined) {
    this._dependsOn = value ?? {};
    this.watchParents();
  }
  get dependsOn(): Record<string, string> {
    return this._dependsOn;
  }
  private _dependsOn: Record<string, string> = {};
  /** Visible text of a choice; the value stays the code. Falls back to the code. */
  @Input() optionLabel: ((fieldKey: string, value: string) => string) | null = null;
  /** Hide the heading of each step (for a form that has a single step). */
  @Input() hideStepTitles = false;
  /** The phone field's starting country (ISO alpha-2); defaults to HQ_PHONE_DEFAULT_COUNTRY. */
  @Input() defaultPhoneCountry: string | null = null;
  /** Fields that cannot be edited. */
  @Input()
  set disabled(value: boolean) {
    this._disabled = value;
    this.syncHidden();
  }
  get disabled(): boolean {
    return this._disabled;
  }

  private _hiddenFields: readonly string[] = [];
  /**
   * Fields not drawn right now (for example payment details until a box is ticked). A hidden field is disabled, so it
   * neither blocks validity nor appears in the answers.
   */
  @Input()
  set hiddenFields(keys: readonly string[] | null | undefined) {
    this._hiddenFields = keys ?? [];
    this.syncHidden();
    this.emitLater();
  }
  get hiddenFields(): readonly string[] {
    return this._hiddenFields;
  }

  private _dateLimits: Record<string, HqOnboardingDateLimit> = {};
  /** Allowed range of a DATE field by key (yyyy-MM-dd); enforced as a validator and as the picker's min / max. */
  @Input()
  set dateLimits(limits: Record<string, HqOnboardingDateLimit>) {
    this._dateLimits = limits ?? {};
    for (const control of Object.values(this.form.controls)) control.updateValueAndValidity({ emitEvent: false });
    this.emitLater();
  }
  get dateLimits(): Record<string, HqOnboardingDateLimit> {
    return this._dateLimits;
  }

  private _steps: HqOnboardingStep[] = [];
  /** The parsed schema. Setting it rebuilds the form (previous answers are dropped). */
  @Input()
  set steps(steps: HqOnboardingStep[] | null | undefined) {
    this._steps = steps ?? [];
    this.rebuild();
  }
  get steps(): HqOnboardingStep[] {
    return this._steps;
  }

  /** The cleaned answers whenever the form changes (empty fields left out, numbers as numbers, phone in E.164). */
  @Output() readonly answersChange = new EventEmitter<HqOnboardingAnswers>();
  /** Whether every field is valid, whenever it changes. */
  @Output() readonly validityChange = new EventEmitter<boolean>();

  private _disabled = false;
  private subscription: Subscription | null = null;
  private lastValid: boolean | null = null;
  private resetting = false;
  private dependencySubscriptions: Subscription[] = [];

  get valid(): boolean {
    return this.form.valid;
  }

  /** The cleaned answers right now. */
  get answers(): HqOnboardingAnswers {
    const shown = hqOnboardingDrawnFields(this._steps).filter((field) => !this._hiddenFields.includes(field.key));
    return hqOnboardingCleanAnswers(shown, this.form.getRawValue());
  }

  /** Shows every field's error, so a submit attempt on an invalid form points at what is wrong. */
  markAllTouched(): void {
    this.form.markAllAsTouched();
    this.phoneInputs?.forEach((input) => input.onBlur());
  }

  /** Clears every answer (or sets the given ones) and hides the errors. */
  reset(values: Record<string, string | number | boolean | null> = {}): void {
    const empty: Record<string, string | number | boolean | null> = {};
    for (const field of hqOnboardingDrawnFields(this._steps)) {
      empty[field.key] = hqOnboardingFieldKind(field) === 'checkbox' ? false : null;
    }
    this.resetting = true;
    this.form.reset({ ...empty, ...values });
    this.resetting = false;
    this.phoneInputs?.forEach((input) => (input.touched = false));
    this.emit();
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.dependencySubscriptions.forEach((subscription) => subscription.unsubscribe());
  }

  kind(field: HqOnboardingField): HqOnboardingFieldKind {
    return hqOnboardingFieldKind(field, this.optionOverrides[field.key]);
  }

  choices(field: HqOnboardingField): readonly string[] {
    const override = this.optionOverrides[field.key];
    return override && override.length > 0 ? override : (field.options ?? []);
  }

  /** The listed values of a searchable drop-down (none while the list is not loaded). */
  optionsOf(field: HqOnboardingField): readonly string[] {
    return this.suggestions[field.key] ?? [];
  }

  /** True when the text field is drawn as a searchable drop-down. */
  isSearchable(field: HqOnboardingField): boolean {
    return this.kind(field) === 'text' && this.searchableFields.includes(field.key);
  }

  /** The hint of a drop-down that waits for its parent answer (state for country), or null when it can be used. */
  blockedHintOf(field: HqOnboardingField): string | null {
    const parentKey = this._dependsOn[field.key];
    if (!parentKey) return null;
    const parentValue = this.form.controls[parentKey]?.value;
    if (typeof parentValue === 'string' ? parentValue.trim() !== '' : parentValue != null && parentValue !== '') return null;
    const parentLabel = this.steps.flatMap((step) => step.fields).find((candidate) => candidate.key === parentKey)?.label ?? parentKey;
    return this.selectTexts.chooseFirst(parentLabel);
  }

  /** True when the field depends on a parent answer: an empty list under a chosen parent is then simply free text. */
  hasParent(field: HqOnboardingField): boolean {
    return !!this._dependsOn[field.key];
  }

  /** The suggestions of a text field, or an empty list. */
  suggestionsOf(field: HqOnboardingField): readonly string[] {
    return this.kind(field) === 'text' ? (this.suggestions[field.key] ?? []) : [];
  }

  listId(field: HqOnboardingField): string {
    return `${this.id(field)}-list`;
  }

  choiceText(field: HqOnboardingField, value: string): string {
    return this.optionLabel?.(field.key, value) || value;
  }

  id(field: HqOnboardingField): string {
    return `${this.uid}-${field.key}`;
  }

  get phoneCountry(): string {
    return this.defaultPhoneCountry ?? this.appDefaultCountry;
  }

  get selectPlaceholder(): string {
    return this.texts.selectPlaceholder();
  }

  trackStep(_: number, step: HqOnboardingStep): string {
    return step.key;
  }

  trackField(_: number, field: HqOnboardingField): string {
    return field.key;
  }

  /** The message under a field, or '' while it is valid or untouched. */
  errorOf(field: HqOnboardingField): string {
    const control = this.form.controls[field.key];
    if (!control || !control.touched || !control.errors) return '';
    const errors = control.errors;
    const label = field.label;
    if (errors['required']) return this.texts.required(label);
    if (errors['email']) return this.texts.invalidEmail();
    if (errors['pattern']) return field.patternErrorMessage || this.texts.invalidPattern(label);
    if (errors['min']) return this.texts.min(label, errors['min'].min);
    if (errors['max']) return this.texts.max(label, errors['max'].max);
    if (errors['minlength']) return this.texts.minLength(label, errors['minlength'].requiredLength);
    if (errors['maxlength']) return this.texts.maxLength(label, errors['maxlength'].requiredLength);
    if (errors['dateMin']) return this.texts.dateMin(label, errors['dateMin'].limit);
    if (errors['dateMax']) return this.texts.dateMax(label, errors['dateMax'].limit);
    return this.texts.invalidPattern(label);
  }

  private rebuild(): void {
    this.subscription?.unsubscribe();
    const controls: Record<string, FormControl> = {};
    for (const field of hqOnboardingDrawnFields(this._steps)) {
      const kind = hqOnboardingFieldKind(field);
      controls[field.key] = new FormControl(kind === 'checkbox' ? false : null, this.validatorsFor(field, kind));
    }
    this.form = new FormGroup(controls);
    this.watchParents();
    if (this._disabled) this.form.disable({ emitEvent: false });
    this.syncHidden();
    this.lastValid = null;
    this.subscription = this.form.valueChanges.subscribe(() => this.emit());
    this.emitLater();
  }

  /** A child answer (state, city) no longer fits once its parent (country, state) is changed by hand: clear it. */
  private watchParents(): void {
    this.dependencySubscriptions.forEach((subscription) => subscription.unsubscribe());
    this.dependencySubscriptions = [];
    for (const [childKey, parentKey] of Object.entries(this._dependsOn)) {
      const parent = this.form.controls[parentKey];
      const child = this.form.controls[childKey];
      if (!parent || !child) continue;
      this.dependencySubscriptions.push(
        parent.valueChanges.subscribe(() => {
          if (!this.resetting && child.value !== null && child.value !== '') child.setValue(null);
        }),
      );
    }
  }

  /** Emits after the current change-detection pass, so a parent binding the outputs is not changed mid-check. */
  /** Disables the hidden fields' controls and enables the rest (unless the whole form is disabled). */
  private syncHidden(): void {
    for (const [key, control] of Object.entries(this.form.controls)) {
      if (this._hiddenFields.includes(key) || this._disabled) control.disable({ emitEvent: false });
      else control.enable({ emitEvent: false });
    }
  }

  private emitLater(): void {
    void Promise.resolve().then(() => this.emit());
  }

  private emit(): void {
    this.answersChange.emit(this.answers);
    const valid = this.form.valid;
    if (valid !== this.lastValid) {
      this.lastValid = valid;
      this.validityChange.emit(valid);
    }
  }

  private validatorsFor(field: HqOnboardingField, kind: HqOnboardingFieldKind): ValidatorFn[] {
    const validators: ValidatorFn[] = [];
    if (field.required) validators.push(kind === 'checkbox' ? Validators.requiredTrue : Validators.required);
    if (kind === 'number') {
      if (field.minValue != null) validators.push(Validators.min(field.minValue));
      if (field.maxValue != null) validators.push(Validators.max(field.maxValue));
    }
    if (kind === 'text' || kind === 'email') {
      if (field.minLength != null) validators.push(Validators.minLength(field.minLength));
      if (field.maxLength != null) validators.push(Validators.maxLength(field.maxLength));
    }
    // A phone field validates itself (any country); every other text-like field honours the schema's regex.
    if (field.pattern && (kind === 'text' || kind === 'email' || kind === 'select')) {
      validators.push(Validators.pattern(field.pattern));
    } else if (kind === 'email') {
      validators.push(this.emailValidator());
    }
    if (kind === 'date') validators.push(this.dateValidator(field.key));
    return validators;
  }

  private emailValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value as string | null;
      return !value || HQ_ONBOARDING_EMAIL_PATTERN.test(value.trim()) ? null : { email: true };
    };
  }

  private dateValidator(key: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value as string | null;
      const limit = this._dateLimits[key];
      if (!value || !limit) return null;
      if (limit.min && value < limit.min) return { dateMin: { limit: limit.min } };
      if (limit.max && value > limit.max) return { dateMax: { limit: limit.max } };
      return null;
    };
  }
}
