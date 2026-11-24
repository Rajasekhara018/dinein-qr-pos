import { ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Subscription } from 'rxjs';
import { HqApplication } from '../hq-application.model';
import { HqApplicationsService } from '../hq-applications.service';

let nextId = 0;

/**
 * Picks one application of the session organization (form control). Shows each as "Name (CODE)", in display order; the
 * value is the application code, or '' when nothing is picked. The list comes from {@link HqApplicationsService}.
 *
 * <p>The library has no translation dependency: every word (label, placeholder, empty and error texts) is an input.
 * `onlyCodes` limits the choices to those codes; `excludeCodes` hides codes. A value that is not among the choices
 * (for example an application switched off since) stays selected and is shown by its code.
 */
@Component({
  selector: 'hq-application-select',
  standalone: false,
  templateUrl: './hq-application-select.html',
  styleUrl: './hq-application-select.scss',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqApplicationSelect), multi: true }],
})
export class HqApplicationSelect implements ControlValueAccessor, OnInit, OnDestroy {
  private readonly service = inject(HqApplicationsService);
  private readonly cdr = inject(ChangeDetectorRef);
  private subscription: Subscription | null = null;

  @Input() label = '';
  /** Text of the empty first choice. */
  @Input() placeholder = '';
  @Input() required = false;
  @Input() disabled = false;
  /** When set, only these codes are offered. */
  @Input() onlyCodes: readonly string[] | null = null;
  /** Codes never offered. */
  @Input() excludeCodes: readonly string[] | null = null;
  /** Shown when the organization has no application to offer. */
  @Input() emptyText = '';
  /** Shown when the list could not be loaded. */
  @Input() errorText = '';
  /** Id of the native select (for an outside label); generated when not given. */
  @Input() inputId = `hq-application-select-${nextId++}`;

  /** The picked application, or null when the choice is cleared. */
  @Output() readonly applicationChange = new EventEmitter<HqApplication | null>();

  value = '';
  all: HqApplication[] = [];
  loaded = false;
  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  ngOnInit(): void {
    this.subscription = this.service.applications$.subscribe((list) => {
      this.all = list;
      this.loaded = true;
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  /** The applications offered, after `onlyCodes` and `excludeCodes`. */
  get options(): HqApplication[] {
    return this.all.filter((app) => (!this.onlyCodes || this.onlyCodes.includes(app.code)) && !this.excludeCodes?.includes(app.code));
  }

  /** True when the current value is not one of the offered applications (it is still shown so it is not lost). */
  get valueOutsideOptions(): boolean {
    return this.value !== '' && !this.options.some((app) => app.code === this.value);
  }

  get failed(): boolean {
    return this.service.loadFailed();
  }

  /** The message under the field: the error text after a failed load, else the empty text when nothing can be offered. */
  get message(): string {
    if (!this.loaded) return '';
    if (this.failed && this.all.length === 0) return this.errorText;
    return this.options.length === 0 ? this.emptyText : '';
  }

  labelOf(app: HqApplication): string {
    return `${app.name} (${app.code})`;
  }

  select(code: string): void {
    this.value = code;
    this.onChange(code);
    this.applicationChange.emit(this.options.find((app) => app.code === code) ?? null);
  }

  touch(): void {
    this.onTouched();
  }

  writeValue(value: string | null | undefined): void {
    this.value = value ?? '';
    this.cdr.markForCheck();
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    this.cdr.markForCheck();
  }
}
