import { HqOption } from '../hq-option.model';
import { ChangeDetectorRef, Component, booleanAttribute, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild, forwardRef, inject, ContentChildren, QueryList } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import {
  HQ_SEARCHABLE_SELECT_MIN_PANEL_PX,
  HQ_SEARCHABLE_SELECT_PHONE_MAX_PX,
  HQ_SEARCHABLE_SELECT_PHONE_MARGIN_PX,
  HQ_SEARCHABLE_SELECT_POSITIONS,
} from './hq-searchable-select.constants';
import { HqSearchableSegment, hqSearchableFilter, hqSearchableMatch, hqSearchableSegments, hqSearchableStep } from './hq-searchable-select.helpers';
import {
  HQ_SEARCHABLE_SELECT_DEFAULT_TEXTS,
  HQ_SEARCHABLE_SELECT_TEXTS,
  HqSearchableSelectTexts,
} from './hq-searchable-select.texts';

let nextInstance = 0;

/** One row of the open list: a listed value, or the closing "Other / not listed" entry. */
export interface HqSearchableRow {
  kind: 'option' | 'other';
  label: string;
  /** A listed choice that is shown but cannot be picked. */
  disabled?: boolean;
}

/** A choice with a value apart from its text (`items` or `<hq-ss-option>`). */
interface HqSearchableChoice {
  value: unknown;
  label: string;
  disabled?: boolean;
}

/**
 * A searchable drop-down for a value picked from a list (a form control: ControlValueAccessor). Click or focus opens the
 * whole list; typing narrows it; the arrow keys move, Enter picks, Escape closes. The last entry, "Other / not listed",
 * turns the control into a free-text box so a value that is not in the list never blocks the form, and a value that is
 * already stored but not in the list is shown the same way. When the list is empty (it could not be loaded) the control is
 * simply that free-text box. The value of the control is the text itself, so existing data keeps working.
 *
 * <p>`blockedHint` makes the control non-interactive and shows the hint under it (for example "Choose the country first");
 * the form control stays enabled, so a required child still reports as missing. The library has no translation
 * dependency: provide HQ_SEARCHABLE_SELECT_TEXTS (English otherwise).
 *
 * <p>Disabled choices: `<hq-ss-option [disabled]="true">` (or `disabled` on a HqOption item) is listed greyed out and
 * cannot be picked by click or Enter; the arrow keys skip it. When it is the stored value it is still shown as the value.
 *
 * <p>Several values: with `multiple` the control value is an array of the chosen values (null/undefined is an empty
 * array). Click or Enter toggles a choice and keeps the list open, every chosen row shows a check, the closed box shows
 * the chosen labels separated by commas, and clear empties the array. "Other" is not offered. `valuesChange` emits the
 * array (`valueChange` is for the single mode only).
 */
/**
 * One choice of a `hq-searchable-select`, written like a native `<option>`:
 * `<hq-ss-option [value]="code" [label]="name"></hq-ss-option>` (works inside `*ngFor`). Draws nothing itself.
 */
@Component({ selector: 'hq-ss-option', standalone: false, template: '' })
export class HqSearchableOption {
  /** What the form control receives when this choice is picked (compared by identity, like a native select's `ngValue`). */
  @Input() value: unknown = '';
  /** The text shown for this choice (already translated); a number (a page size, say) is shown as its text. */
  @Input({ transform: (text: unknown): string => (text === null || text === undefined ? '' : String(text)) }) label = '';
  /** Listed greyed out; cannot be picked. */
  @Input({ transform: booleanAttribute }) disabled = false;
}

@Component({
  selector: 'hq-searchable-select',
  standalone: false,
  templateUrl: './hq-searchable-select.html',
  styleUrl: './hq-searchable-select.scss',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqSearchableSelect), multi: true }],
})
export class HqSearchableSelect implements ControlValueAccessor, OnChanges {
  private readonly texts: HqSearchableSelectTexts =
    inject(HQ_SEARCHABLE_SELECT_TEXTS, { optional: true }) ?? HQ_SEARCHABLE_SELECT_DEFAULT_TEXTS;
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild('freeText') private freeText?: ElementRef<HTMLInputElement>;

  /** The listed values, in the order to show them (the value of the control is the text itself). */
  @Input() options: readonly string[] = [];
  /**
   * Choices that have a value apart from their text, like a native select's options: the control's value is the chosen
   * item's `value` and the box shows its `label`. When set, `options` is ignored. Typed free text (when `allowOther` is on)
   * is the control's value as it is.
   */
  @Input() items: readonly HqOption[] | null = null;
  /** Choices written as `<hq-ss-option>` children (used when `items` is not set). */
  @ContentChildren(HqSearchableOption, { descendants: true }) private projected?: QueryList<HqSearchableOption>;
  /** Id of the text box (so a `label for` points at it). */
  @Input() inputId = '';
  @Input() placeholder = '';
  @Input() ariaLabel = '';
  /** Offer "Other / not listed" (free text). */
  @Input() allowOther = true;
  /** Marks the control as invalid (red border). */
  @Input() hasError = false;
  /** Non-interactive until something else is chosen; the text is shown under the control. */
  @Input() blockedHint: string | null = null;
  /** Show the "list could not be loaded" notice when there are no options. Off for a child list that is simply empty. */
  @Input() showUnavailableNotice = true;
  /** The list is being loaded: the control is disabled and shows a spinner instead of looking like an empty box. */
  @Input() loading = false;
  /** The list could not be loaded: the control is a free-text box with a notice and a retry action. */
  @Input() loadFailed = false;
  /** The retry action of a failed list was used. */
  @Output() retry = new EventEmitter<void>();
  /** Emits the new value whenever the user changes it. */
  @Output() valueChange = new EventEmitter<string>();
  /** Several values can be chosen: the control value is an array (see the class comment). */
  @Input({ transform: booleanAttribute }) multiple = false;
  /** The chosen values, emitted on every change in `multiple` mode. */
  @Output() valuesChange = new EventEmitter<unknown[]>();

  /** Used when the app's texts do not provide `optionsEmpty`. */
  readonly defaultEmptyText = HQ_SEARCHABLE_SELECT_DEFAULT_TEXTS.optionsEmpty?.() ?? '';

  readonly uid = `hq-ss-${nextInstance++}`;
  readonly positions = HQ_SEARCHABLE_SELECT_POSITIONS;

  /** What the form control holds: the chosen item's value when items are used, else the text itself. */
  private model: unknown = '';
  /** What the form control holds in `multiple` mode: the chosen values. */
  private models: unknown[] = [];

  /** What the box shows: the text of the chosen choice (or the free text typed). */
  get value(): string {
    if (this.multiple) return this.models.map((model) => this.labelFor(model)).join(', ');
    return this.labelFor(this.model);
  }
  disabled = false;
  open = false;
  term = '';
  activeIndex = -1;
  panelWidth = HQ_SEARCHABLE_SELECT_MIN_PANEL_PX;
  /** The user chose "Other", or the stored value is not in the list: the control is the free-text box. */
  private otherChosen = false;

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  /** The texts offered in the list: the items' labels, or `options`. */
  get listed(): readonly string[] {
    const items = this.choices;
    return items ? items.map((item) => item.label) : this.options;
  }

  /** The choices that have a value apart from their text: `items`, else the `<hq-ss-option>` children, else none. */
  private get choices(): ReadonlyArray<HqSearchableChoice> | null {
    if (this.items) return this.items;
    const children = this.projected?.toArray() ?? [];
    return children.length ? children : null;
  }

  get t(): HqSearchableSelectTexts {
    return this.texts;
  }

  get controlId(): string {
    return this.inputId || `${this.uid}-input`;
  }

  get listId(): string {
    return `${this.uid}-list`;
  }

  get blocked(): boolean {
    return !!this.blockedHint;
  }

  /** The list is on its way: shown as a disabled control with a spinner. */
  get isLoading(): boolean {
    return this.loading && !this.blocked;
  }

  /** The free-text box is shown: by choice, for an unlisted stored value, or because there is no list. */
  get otherMode(): boolean {
    if (this.multiple) return false;
    return !this.blocked && !this.isLoading && (this.listed.length === 0 || this.otherChosen);
  }

  /** The notice under the free-text box that stands in for a list that is not there. */
  get unavailable(): boolean {
    return !this.blocked && !this.isLoading && this.listed.length === 0 && (this.loadFailed || this.showUnavailableNotice);
  }

  /** The text typed into the open list. */
  get typed(): string {
    return this.term.trim();
  }

  get shownPlaceholder(): string {
    return this.isLoading ? this.texts.loading() : this.placeholder || this.texts.placeholder();
  }

  /** The rows of the open list: the options that match the typed text, then "Other". */
  get rows(): HqSearchableRow[] {
    if (this.isLoading) return [];
    const rows: HqSearchableRow[] = hqSearchableFilter(this.listed, this.term).map((label) => ({
      kind: 'option',
      label,
      disabled: this.isDisabledLabel(label),
    }));
    if (this.allowOther && !this.multiple) rows.push({ kind: 'other', label: this.texts.other() });
    return rows;
  }

  /** Something is typed and nothing in the list contains it: Enter uses the typed text. */
  get noMatch(): boolean {
    return !this.multiple && !!this.typed && this.rows.every((row) => row.kind === 'other');
  }

  /** The row's text split around the typed text, so the match can be highlighted. */
  segments(label: string): HqSearchableSegment[] {
    return hqSearchableSegments(label, this.term);
  }

  get activeId(): string | null {
    return this.open && this.activeIndex >= 0 ? this.optionId(this.activeIndex) : null;
  }

  /** Whether a row is a chosen value (its check is shown). */
  isChosen(row: HqSearchableRow): boolean {
    if (row.kind !== 'option') return false;
    if (!this.multiple) return row.label === this.value;
    return this.models.includes(this.modelFor(row.label));
  }

  optionId(index: number): string {
    return `${this.uid}-opt-${index}`;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['options'] || changes['items'] || changes['blockedHint']) && !this.multiple) {
      // A value that is stored but not in the (newly loaded) list stays visible as free text.
      if (this.value && this.listed.length > 0 && !hqSearchableMatch(this.listed, this.value)) this.otherChosen = true;
      if (this.blocked) this.closeList();
    }
  }

  // ---- ControlValueAccessor ----------------------------------------------------------------

  writeValue(value: unknown): void {
    if (this.multiple) {
      this.models = Array.isArray(value) ? [...value] : [];
      this.otherChosen = false;
      this.closeList();
      this.cdr.markForCheck();
      return;
    }
    this.model = value ?? '';
    this.otherChosen = !!this.value && this.listed.length > 0 && !hqSearchableMatch(this.listed, this.value);
    this.closeList();
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
    if (isDisabled) this.closeList();
    this.cdr.markForCheck();
  }

  // ---- the list ----------------------------------------------------------------------------

  openList(origin?: HTMLElement): void {
    if (this.disabled || this.blocked || this.open) return;
    this.term = '';
    this.panelWidth = this.widthFor(origin);
    const current = this.multiple ? undefined : hqSearchableMatch(this.listed, this.value);
    const rows = this.rows;
    this.activeIndex = this.multiple
      ? rows.findIndex((row) => this.isChosen(row) && !row.disabled)
      : rows.findIndex((row) => row.kind === 'option' && row.label === current && !row.disabled);
    this.open = true;
    if (this.activeIndex >= 0) setTimeout(() => this.scrollActiveIntoView());
  }

  closeList(): void {
    this.open = false;
    this.term = '';
    this.activeIndex = -1;
  }

  /** A click outside both the list and the control closes the list; a click on the control itself is the control's own. */
  onOutsideClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) this.closeList();
  }

  onType(text: string, origin: HTMLElement): void {
    if (this.disabled || this.blocked || this.isLoading) return;
    if (!this.open) this.openList(origin);
    this.term = text;
    this.activeIndex = this.rows.findIndex((row) => !row.disabled);
  }

  onKey(event: KeyboardEvent, origin: HTMLElement): void {
    if (this.disabled || this.blocked || this.isLoading) return;
    switch (event.key) {
      case 'ArrowDown':
      case 'ArrowUp':
        event.preventDefault();
        if (!this.open) {
          this.openList(origin);
          return;
        }
        this.activeIndex = this.stepEnabled(this.activeIndex, event.key === 'ArrowDown' ? 1 : -1);
        this.scrollActiveIntoView();
        break;
      case 'Enter':
        if (this.open) {
          event.preventDefault();
          if (this.noMatch) {
            this.useTyped();
          } else {
            const row = this.rows[this.activeIndex];
            if (row) this.pick(row);
          }
        }
        break;
      case 'Escape':
        if (this.open) {
          event.preventDefault();
          event.stopPropagation();
          this.closeList();
        }
        break;
      case 'Tab':
        this.closeList();
        break;
    }
  }

  /** The list was drawn: measures the control again, so the list is never narrower than the control it opens under. */
  onPanelAttached(origin: HTMLElement): void {
    this.panelWidth = this.widthFor(origin);
  }

  onBlur(): void {
    this.closeList();
    this.onTouched();
  }

  pick(row: HqSearchableRow): void {
    if (row.disabled) return;
    if (this.multiple) {
      if (row.kind === 'option') this.toggle(row.label);
      return;
    }
    this.closeList();
    if (row.kind === 'other') {
      this.otherChosen = true;
      this.setValue('');
      setTimeout(() => this.freeText?.nativeElement.focus());
      return;
    }
    this.otherChosen = false;
    this.setValue(row.label);
  }

  /** Uses the typed text as the value although it is not in the list (the "Other" route without the extra click). */
  useTyped(): void {
    const text = this.typed;
    this.closeList();
    if (!text) return;
    this.otherChosen = true;
    this.setValue(text);
  }

  // ---- the free-text box and the clear / back actions ---------------------------------------

  onFreeText(text: string): void {
    this.setValue(text);
  }

  clear(): void {
    if (this.multiple) {
      this.setValues([]);
      return;
    }
    this.otherChosen = false;
    this.setValue('');
  }

  backToList(): void {
    this.otherChosen = false;
    this.setValue('');
  }

  /** On a phone the list is a near full-width sheet; elsewhere it is exactly as wide as the control (wider only for a narrow control). */
  private widthFor(origin?: HTMLElement): number {
    if (window.innerWidth <= HQ_SEARCHABLE_SELECT_PHONE_MAX_PX) {
      return window.innerWidth - 2 * HQ_SEARCHABLE_SELECT_PHONE_MARGIN_PX;
    }
    const field = origin ?? this.host.nativeElement.querySelector<HTMLElement>('.hq-ss__field') ?? this.host.nativeElement;
    return Math.max(field.getBoundingClientRect().width, HQ_SEARCHABLE_SELECT_MIN_PANEL_PX);
  }

  /** Adds or removes one choice in `multiple` mode; the list stays open so more can be picked. */
  private toggle(label: string): void {
    const value = this.modelFor(label);
    this.setValues(this.models.includes(value) ? this.models.filter((model) => model !== value) : [...this.models, value]);
    this.term = '';
  }

  private setValues(values: unknown[]): void {
    this.models = values;
    (this.onChange as (value: unknown) => void)([...values]);
    this.valuesChange.emit([...values]);
  }

  /** Whether the listed text belongs to a disabled choice. */
  private isDisabledLabel(label: string): boolean {
    return !!this.choices?.find((item) => item.label === label)?.disabled;
  }

  /** The next row in the given direction that can be picked; stays put when there is none. */
  private stepEnabled(from: number, delta: number): number {
    const rows = this.rows;
    let index = from;
    for (;;) {
      const next = hqSearchableStep(index, delta, rows.length);
      if (next === index || next < 0) return rows[from]?.disabled ? -1 : from;
      if (!rows[next].disabled) return next;
      index = next;
    }
  }

  private setValue(value: string): void {
    this.model = this.modelFor(value);
    // With `hq-ss-option` choices the value can be any object (like a native select's `ngValue`); the typings stay string for the
    // common case of text values.
    this.onChange(this.model as string);
    this.valueChange.emit(this.model as string);
  }

  /** The text to show for a control value: the matching item's label, else the value itself. */
  private labelFor(model: unknown): string {
    return this.choices?.find((item) => item.value === model)?.label ?? (model === null || model === undefined ? '' : String(model));
  }

  /** The control value for shown text: the matching item's value, else the text itself (free text). */
  private modelFor(text: string): unknown {
    return this.choices?.find((item) => item.label === text)?.value ?? text;
  }

  private scrollActiveIntoView(): void {
    const id = this.activeId;
    const element = id ? document.getElementById(id) : null;
    element?.scrollIntoView?.({ block: 'nearest' });
  }
}
