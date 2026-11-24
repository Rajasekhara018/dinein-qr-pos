import { ChangeDetectorRef, Component, ElementRef, Input, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import {
  HQ_DATE_TIME_DEFAULT_WEEK_START,
  HQ_DATE_TIME_ID_PREFIX,
  HQ_DATE_TIME_MAX_TYPED,
  HQ_DATE_TIME_POSITIONS,
  HqDateTimeMode,
} from './hq-date-time.constants';
import {
  HqDateParts,
  HqGridDay,
  HqTimeParts,
  hqAddDays,
  hqAddMonths,
  hqClampDate,
  hqDayLabel,
  hqFormatDisplayDate,
  hqFormatIsoDate,
  hqFormatIsoDateTime,
  hqFormatIsoTime,
  hqIsDayDisabled,
  hqMinuteOptions,
  hqModeOfTag,
  hqMonthGrid,
  hqMonthLabel,
  hqNormalStep,
  hqNowTime,
  hqParseIsoDate,
  hqParseIsoDateTime,
  hqParseIsoTime,
  hqParseTypedDate,
  hqParseTypedTime,
  hqToday,
  hqWeekdayLabels,
} from './hq-date-time.helpers';
import { HQ_DATE_TIME_DEFAULT_TEXTS, HQ_DATE_TIME_TEXTS, HqDateTimeTexts } from './hq-date-time.texts';

let nextInstance = 0;

const HOURS: readonly number[] = Array.from({ length: 24 }, (_, hour) => hour);

/**
 * The common date, time and date-and-time field (a form control: ControlValueAccessor), one component with three tags:
 * `hq-date-input` holds a day, `hq-time-input` a time of day and `hq-datetime-input` both. The value is the same text a native
 * input holds (`yyyy-MM-dd`, `HH:mm`, `yyyy-MM-ddTHH:mm`) and null when empty or not yet complete, so it replaces a native
 * `type="date"`, `"time"` or `"datetime-local"` input without changing what the screen sends. A day is a calendar day and a time
 * is a wall-clock time: converting to an instant (with the user's time zone) stays the screen's job.
 *
 * <p>The user can type (`dd-mm-yyyy`, `hh:mm`) or pick from the calendar and clock, with the keyboard or the mouse. The box has the
 * shared control height, so it lines up with the other fields. The library has no translation dependency: provide
 * HQ_DATE_TIME_TEXTS for the field's own words; month and weekday names come from the browser for `locale`.
 */
@Component({
  selector: 'hq-date-input, hq-time-input, hq-datetime-input',
  standalone: false,
  templateUrl: './hq-date-time-input.html',
  styleUrl: './hq-date-time-input.scss',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqDateTimeInput), multi: true }],
})
export class HqDateTimeInput implements ControlValueAccessor {
  private readonly texts: HqDateTimeTexts = inject(HQ_DATE_TIME_TEXTS, { optional: true }) ?? HQ_DATE_TIME_DEFAULT_TEXTS;
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly cdr = inject(ChangeDetectorRef);

  private readonly uid = `${HQ_DATE_TIME_ID_PREFIX}-${nextInstance++}`;
  readonly positions = HQ_DATE_TIME_POSITIONS;
  readonly hours = HOURS;
  readonly maxTyped = HQ_DATE_TIME_MAX_TYPED;

  /** What the field holds, from the tag it is used as; set it to override (for example when the tag is chosen at run time). */
  @Input() set mode(mode: HqDateTimeMode | null | undefined) {
    this.chosenMode = mode ?? null;
  }
  get mode(): HqDateTimeMode {
    return this.chosenMode ?? hqModeOfTag(this.host.nativeElement.tagName);
  }
  /** Earliest day allowed, as `yyyy-MM-dd` (a `yyyy-MM-ddTHH:mm` value uses its day). */
  @Input() min: string | null = null;
  /** Latest day allowed, as `yyyy-MM-dd` (a `yyyy-MM-ddTHH:mm` value uses its day). */
  @Input() max: string | null = null;
  /** Minutes between the choices of the clock: a divisor of 60 (5, 10, 15, 30 ...); any other value is 1. */
  @Input() minuteStep = 1;
  /** First day of the week: 0 = Sunday ... 6 = Saturday. */
  @Input() weekStart = HQ_DATE_TIME_DEFAULT_WEEK_START;
  /** Language of the month and weekday names; the page's language when empty. */
  @Input() locale = '';
  @Input() placeholder = '';
  /** Id of the text box (so a `label for` can point at it); generated when empty. */
  @Input() inputId = '';
  @Input() name = '';
  @Input() ariaLabel = '';
  @Input() readonly = false;
  /** Marks the field as invalid (red border). */
  @Input() hasError = false;
  /** Show the clear button inside the panel. */
  @Input() clearable = true;

  disabled = false;
  open = false;
  /** What the text box shows: what the user typed, or the value written out. */
  text = '';
  /** The month the calendar shows (month 1-12). */
  viewYear = hqToday().year;
  viewMonth = hqToday().month;
  /** The day that has the keyboard focus in the calendar. */
  focusDay: HqDateParts = hqToday();

  private chosenMode: HqDateTimeMode | null = null;
  private date: HqDateParts | null = null;
  private time: HqTimeParts | null = null;
  /** The value last reported to the form, so an unchanged value is not reported again. */
  private reported: string | null = null;

  private onChange: (value: string | null) => void = () => {};
  private onTouched: () => void = () => {};

  get t(): HqDateTimeTexts {
    return this.texts;
  }

  get hasDate(): boolean {
    return this.mode !== 'time';
  }

  get hasTime(): boolean {
    return this.mode !== 'date';
  }

  get controlId(): string {
    return this.inputId || `${this.uid}-input`;
  }

  get panelId(): string {
    return `${this.uid}-panel`;
  }

  get shownPlaceholder(): string {
    return this.placeholder || this.texts.placeholder(this.mode);
  }

  get activeLocale(): string {
    return this.locale || (typeof document !== 'undefined' && document.documentElement.lang) || 'en';
  }

  get monthTitle(): string {
    return hqMonthLabel(this.activeLocale, this.viewYear, this.viewMonth);
  }

  get weekdays(): string[] {
    return hqWeekdayLabels(this.activeLocale, this.weekStartNormal);
  }

  get grid(): HqGridDay[] {
    return hqMonthGrid(this.viewYear, this.viewMonth, this.weekStartNormal);
  }

  /** The grid cut into its weeks. */
  get weeks(): HqGridDay[][] {
    const cells = this.grid;
    return Array.from({ length: cells.length / 7 }, (_, row) => cells.slice(row * 7, row * 7 + 7));
  }

  get minutes(): number[] {
    return hqMinuteOptions(this.minuteStep);
  }

  /** The value the form holds right now: null unless it is complete for the mode. */
  get currentValue(): string | null {
    if (this.mode === 'date') return this.date ? hqFormatIsoDate(this.date) : null;
    if (this.mode === 'time') return this.time ? hqFormatIsoTime(this.time) : null;
    return this.date && this.time ? hqFormatIsoDateTime(this.date, this.time) : null;
  }

  /** The text box holds something the field cannot read as a complete value. */
  get isIncomplete(): boolean {
    return this.text.trim() !== '' && this.currentValue === null;
  }

  get selectedIso(): string | null {
    return this.date ? hqFormatIsoDate(this.date) : null;
  }

  get todayIso(): string {
    return hqFormatIsoDate(hqToday());
  }

  get selectedHour(): number | null {
    return this.time ? this.time.hour : null;
  }

  get selectedMinute(): number | null {
    return this.time ? this.time.minute : null;
  }

  get focusIso(): string {
    return hqFormatIsoDate(this.focusDay);
  }

  private get minDate(): HqDateParts | null {
    return this.limit(this.min);
  }

  private get maxDate(): HqDateParts | null {
    return this.limit(this.max);
  }

  private get weekStartNormal(): number {
    return Number.isInteger(this.weekStart) && this.weekStart >= 0 && this.weekStart <= 6 ? this.weekStart : 0;
  }

  private limit(value: string | null): HqDateParts | null {
    return value ? (hqParseIsoDate(value) ?? hqParseIsoDateTime(value)?.date ?? null) : null;
  }

  dayDisabled(day: HqDateParts): boolean {
    return hqIsDayDisabled(day, this.minDate, this.maxDate);
  }

  dayLabel(day: HqDateParts): string {
    return hqDayLabel(this.activeLocale, day);
  }

  hourLabel(hour: number): string {
    return String(hour).padStart(2, '0');
  }

  // ---- ControlValueAccessor ----------------------------------------------------------------------------------

  writeValue(value: string | null | undefined): void {
    this.setParts(value ?? null);
    this.text = this.displayText();
    this.reported = this.currentValue;
    this.cdr.markForCheck();
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled = disabled;
    if (disabled) this.open = false;
    this.cdr.markForCheck();
  }

  // ---- typing ------------------------------------------------------------------------------------------------

  onType(raw: string): void {
    this.text = raw;
    if (raw.trim() === '') {
      this.date = null;
      this.time = null;
    } else {
      this.readTyped(raw);
    }
    this.report();
  }

  onBlur(): void {
    this.onTouched();
  }

  onInputKey(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' && event.altKey && !this.open) {
      event.preventDefault();
      this.openPanel();
    } else if (event.key === 'Escape' && this.open) {
      event.preventDefault();
      this.closePanel(false);
    }
  }

  private readTyped(raw: string): void {
    if (this.mode === 'time') {
      this.time = hqParseTypedTime(raw);
      this.date = null;
      return;
    }
    if (this.mode === 'date') {
      this.date = hqParseTypedDate(raw);
      this.time = null;
      if (this.date) this.showMonthOf(this.date);
      return;
    }
    const match = /^(.+?)\s+(\d{1,2}:\d{2}|\d{4})$/.exec(raw.trim());
    this.date = match ? hqParseTypedDate(match[1]) : null;
    this.time = match ? hqParseTypedTime(match[2]) : null;
    if (this.date) this.showMonthOf(this.date);
  }

  // ---- the panel ---------------------------------------------------------------------------------------------

  toggle(): void {
    if (this.open) this.closePanel(true);
    else this.openPanel();
  }

  openPanel(): void {
    if (this.disabled || this.readonly || this.open) return;
    const anchor = this.date ?? hqClampDate(hqToday(), this.minDate, this.maxDate);
    this.focusDay = anchor;
    this.showMonthOf(anchor);
    this.open = true;
    this.cdr.markForCheck();
  }

  closePanel(returnFocus: boolean): void {
    if (!this.open) return;
    this.open = false;
    this.cdr.markForCheck();
    if (returnFocus) this.focusInput();
  }

  onOutsideClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) this.closePanel(false);
  }

  previousMonth(): void {
    this.moveView(-1);
  }

  nextMonth(): void {
    this.moveView(1);
  }

  selectDay(day: HqDateParts): void {
    if (this.dayDisabled(day)) return;
    this.date = day;
    this.focusDay = day;
    if (this.hasTime && !this.time) this.time = { hour: 0, minute: 0 };
    this.afterPick(this.mode === 'date');
  }

  selectHour(hour: number): void {
    this.ensureDate();
    this.time = { hour, minute: this.time?.minute ?? 0 };
    this.afterPick(false);
  }

  selectMinute(minute: number): void {
    this.ensureDate();
    this.time = { hour: this.time?.hour ?? 0, minute };
    this.afterPick(false);
  }

  /** The "Today" button (or "Now" for a time field). */
  selectToday(): void {
    if (this.mode === 'time') {
      this.time = hqNowTime(new Date(), hqNormalStep(this.minuteStep));
      this.afterPick(false);
      return;
    }
    const today = hqClampDate(hqToday(), this.minDate, this.maxDate);
    this.date = today;
    this.focusDay = today;
    this.showMonthOf(today);
    if (this.hasTime && !this.time) this.time = hqNowTime(new Date(), hqNormalStep(this.minuteStep));
    this.afterPick(this.mode === 'date');
  }

  clear(): void {
    this.date = null;
    this.time = null;
    this.text = '';
    this.report();
    this.closePanel(true);
  }

  done(): void {
    this.closePanel(true);
  }

  /** The calendar's keyboard: arrows move by a day or a week, Home and End to the ends of the week, Page keys by a month or a year. */
  onGridKey(event: KeyboardEvent): void {
    const week = this.weekStartNormal;
    const weekday = new Date(Date.UTC(this.focusDay.year, this.focusDay.month - 1, this.focusDay.day)).getUTCDay();
    let target: HqDateParts | null = null;
    switch (event.key) {
      case 'ArrowLeft': target = hqAddDays(this.focusDay, -1); break;
      case 'ArrowRight': target = hqAddDays(this.focusDay, 1); break;
      case 'ArrowUp': target = hqAddDays(this.focusDay, -7); break;
      case 'ArrowDown': target = hqAddDays(this.focusDay, 7); break;
      case 'Home': target = hqAddDays(this.focusDay, -((weekday - week + 7) % 7)); break;
      case 'End': target = hqAddDays(this.focusDay, 6 - ((weekday - week + 7) % 7)); break;
      case 'PageUp': target = this.sameDayInMonth(event.shiftKey ? -12 : -1); break;
      case 'PageDown': target = this.sameDayInMonth(event.shiftKey ? 12 : 1); break;
      case 'Escape':
        event.preventDefault();
        this.closePanel(true);
        return;
      default:
        return;
    }
    event.preventDefault();
    this.focusDay = target;
    this.showMonthOf(target);
    this.cdr.markForCheck();
    this.focusDayButton();
  }

  // ---- internals ---------------------------------------------------------------------------------------------

  private setParts(value: string | null): void {
    this.date = null;
    this.time = null;
    if (!value) return;
    if (this.mode === 'date') this.date = hqParseIsoDate(value) ?? hqParseIsoDateTime(value)?.date ?? null;
    else if (this.mode === 'time') this.time = hqParseIsoTime(value) ?? hqParseIsoDateTime(value)?.time ?? null;
    else {
      const both = hqParseIsoDateTime(value);
      this.date = both?.date ?? null;
      this.time = both?.time ?? null;
    }
    if (this.date) {
      this.viewYear = this.date.year;
      this.viewMonth = this.date.month;
    }
  }

  private displayText(): string {
    const day = this.date ? hqFormatDisplayDate(this.date) : '';
    const clock = this.time ? hqFormatIsoTime(this.time) : '';
    if (this.mode === 'date') return day;
    if (this.mode === 'time') return clock;
    return this.date && this.time ? `${day} ${clock}` : '';
  }

  private ensureDate(): void {
    if (this.hasDate && !this.date) {
      this.date = hqClampDate(hqToday(), this.minDate, this.maxDate);
      this.focusDay = this.date;
      this.showMonthOf(this.date);
    }
  }

  private afterPick(close: boolean): void {
    this.text = this.displayText();
    this.report();
    if (close) this.closePanel(true);
    else this.cdr.markForCheck();
  }

  private report(): void {
    const value = this.currentValue;
    if (value === this.reported) return;
    this.reported = value;
    this.onChange(value);
    this.cdr.markForCheck();
  }

  private moveView(delta: number): void {
    const moved = hqAddMonths(this.viewYear, this.viewMonth, delta);
    this.viewYear = moved.year;
    this.viewMonth = moved.month;
    this.cdr.markForCheck();
  }

  private showMonthOf(day: HqDateParts): void {
    this.viewYear = day.year;
    this.viewMonth = day.month;
  }

  private sameDayInMonth(delta: number): HqDateParts {
    const moved = hqAddMonths(this.focusDay.year, this.focusDay.month, delta);
    const last = new Date(Date.UTC(moved.year, moved.month, 0)).getUTCDate();
    return { year: moved.year, month: moved.month, day: Math.min(this.focusDay.day, last) };
  }

  private focusInput(): void {
    this.host.nativeElement.querySelector<HTMLInputElement>('input.hq-dt__control')?.focus();
  }

  private focusDayButton(): void {
    const iso = this.focusIso;
    queueMicrotask(() =>
      document.querySelector<HTMLButtonElement>(`#${this.panelId} button[data-iso="${iso}"]`)?.focus(),
    );
  }

  /** The longest text the box accepts, as an input attribute. */
  get maxLength(): number {
    return this.maxTyped;
  }
}
