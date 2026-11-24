import { ChangeDetectorRef, Component, Inject, OnDestroy, OnInit, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable, Subject, Subscription, catchError, debounceTime, distinctUntilChanged, of, switchMap, tap } from 'rxjs';
import { HqCommonTableColumn } from '../hq-common-table/hq-common-table';
import {
  LOOKUP_ALL_OPERATORS, LOOKUP_EXACT_OPERATORS, LOOKUP_PICK_COLUMN, LOOKUP_SELECT_COLUMN, LOOKUP_SKELETON_ROWS,
} from './hq-lookup-dialog.constants';
import { HQ_LOOKUP_DEFAULT_TEXTS, HQ_LOOKUP_TEXTS, HqLookupTexts } from '../hq-lookup.texts';
import { HqAdvancedQuery, HqLookupOperator } from '../../hq-common.models';

/** One page of results from the server. `total` is the number of matches across all pages (omit if unknown). */
export interface HqPickerPage<T> {
  items: T[];
  total?: number | null;
}

/**
 * A column of the lookup table; `searchable: false` keeps it out of the search-scope chips (default: offered).
 * `type` picks the value control and the operators of an advanced condition on it (default 'text'): text offers
 * equals / not equals / contains, number / date / boolean only equals / not equals.
 */
export type HqLookupDialogColumn<T extends object> = HqCommonTableColumn<T> & {
  searchable?: boolean;
  type?: HqLookupColumnType;
};

/** The kind of value a lookup column holds, for the value control of an advanced condition. */
export type HqLookupColumnType = 'text' | 'number' | 'date' | 'boolean';

/**
 * One advanced-search row, the same shape as hq-common-model's: the column key (`fieldName`), the operator, the value,
 * and how it joins the rows before it (the first row's join is always 'AND'). Values are strings: a date is
 * 'YYYY-MM-DD', a yes/no value 'true' or 'false'.
 */
export type HqLookupCondition = HqAdvancedQuery;

export interface HqLookupConfig<T extends object> {
  title: string;
  /** One line under the title, e.g. "Search and tick the people to add". */
  subtitle?: string;
  /** The first column is each record's headline; the other columns are shown beside it as a quieter detail line. */
  columns: HqLookupDialogColumn<T>[];
  /**
   * Server-side search: the text typed, the 0-indexed page and the page size. Never load the whole table here.
   * The fourth argument is the key of the column chosen in the search-scope chips; it is left out for "All fields".
   * The fifth argument is only passed in advanced mode (see `advanced`): the conditions to apply, with `query` '' and
   * `field` undefined. A callback written for three or four arguments keeps working; it simply never gets conditions.
   */
  fetch: (query: string, page: number, size: number, field?: string, conditions?: readonly HqLookupCondition[]) => Observable<HqPickerPage<T>>;
  /**
   * Offers an "Add conditions" mode next to the search-scope chips: column / operator / value rows joined by AND or OR,
   * sent to `fetch` as its fifth argument. Only turn it on when `fetch` applies them on the server. Default false.
   */
  advanced?: boolean;
  /** Column keys offered as the field of a condition, in that order; default every searchable column. */
  advancedColumns?: readonly string[];
  /** Operators the server supports in conditions; default all (equals, not equals, contains). */
  operators?: readonly HqLookupOperator[];
  /**
   * Set true when `fetch` honours its fourth argument (searches that one column on the server). When it does not,
   * the dialog narrows the page it got to the chosen column itself, so a chip never silently does nothing.
   */
  scopedSearch?: boolean;
  /** Stable id of a record; selections are kept by it across pages and searches. */
  idOf: (item: T) => string;
  multiple?: boolean;
  /** Already-chosen records, shown ticked and as chips. */
  selected?: T[];
  pageSize?: number;
  /** Search only starts once this many characters are typed (use 2-3 for very large sources). Default 0. */
  minQueryLength?: number;
  searchPlaceholder?: string;
  confirmText?: string;
  /** Text for a record in the chips of the selection; defaults to the first column's value. */
  chipLabel?: (item: T) => string;
}

/** The words of the optional lookup texts, resolved with their English defaults. */
type HqLookupWord = 'close' | 'searchLabel' | 'searchButton' | 'showingResultsFor' | 'clearResult' | 'allFields' | 'searchFields'
  | 'selectAllOnPage' | 'addConditions' | 'addCondition' | 'advancedSearch' | 'advancedHint' | 'normalSearch' | 'join' | 'field'
  | 'condition' | 'value' | 'removeCondition' | 'equals' | 'notEquals' | 'contains' | 'yes' | 'no';

const DEFAULT_PAGE_SIZE = 10;
const SEARCH_DEBOUNCE_MS = 300;

/** Search-and-pick dialog. Open it through HqLookupService. */
@Component({
  selector: 'hq-lookup-dialog',
  standalone: false,
  templateUrl: './hq-lookup-dialog.html',
  styleUrl: './hq-lookup-dialog.scss',
})
export class HqLookupDialog<T extends object> implements OnInit, OnDestroy {
  readonly texts: HqLookupTexts = inject(HQ_LOOKUP_TEXTS, { optional: true }) ?? HQ_LOOKUP_DEFAULT_TEXTS;

  query = '';
  page = 0;
  size: number;
  rows: T[] = [];
  total: number | null = null;
  isLoading = false;
  hasError = false;
  /** Why the last load failed (the server's own message when it sent one). */
  errorMessage = '';
  /** Key of the column the search is scoped to; '' searches all searchable columns together. */
  searchField = '';
  /** True while the shown rows were narrowed to the chosen column by the dialog (the server total no longer applies). */
  narrowed = false;
  /** Scrolled body: the header casts a soft shadow. */
  scrolled = false;
  /** How many records the server sent for the current page, before any narrowing. */
  private fetchedCount = 0;
  /** Placeholder rows shown while a page loads. */
  readonly skeleton = LOOKUP_SKELETON_ROWS;
  /** The columns handed to hq-common-table: a leading checkbox (multiple) or a trailing pick button (single) around the config's. */
  readonly tableColumns: HqCommonTableColumn<T>[];
  readonly selectKey = LOOKUP_SELECT_COLUMN;
  readonly pickKey = LOOKUP_PICK_COLUMN;
  /** Everyone ticked so far, by id, kept while the search and page change. */
  readonly chosen = new Map<string, T>();

  /** Advanced mode: the condition rows are shown instead of the quick search. */
  isAdvanced = false;
  /** The condition rows being edited. */
  conditions: HqLookupCondition[] = [];
  /** The conditions of the last "Advanced search" run; paging keeps using them until the next run. */
  private appliedConditions: HqLookupCondition[] = [];

  private readonly requests$ = new Subject<{ query: string; page: number; field: string; conditions: readonly HqLookupCondition[] }>();
  private readonly search$ = new Subject<string>();
  private readonly subscriptions = new Subscription();

  constructor(
    private ref: MatDialogRef<HqLookupDialog<T>, T[] | null>,
    @Inject(MAT_DIALOG_DATA) public config: HqLookupConfig<T>,
    private cdr: ChangeDetectorRef,
  ) {
    this.size = config.pageSize ?? DEFAULT_PAGE_SIZE;
    (config.selected ?? []).forEach((item) => this.chosen.set(config.idOf(item), item));
    this.tableColumns = this.buildTableColumns();
  }

  /** The key of the first column: its cells are the record's headline (avatar and name). */
  get leadKey(): string { return this.config.columns[0]?.key ?? ''; }

  /** The classes of a table row: always a lookup row, plus the chosen tint for a ticked record. */
  readonly rowClass = (row: T): string[] => (this.isChosen(row) ? ['lookup__row', 'lookup__row--chosen'] : ['lookup__row']);

  private buildTableColumns(): HqCommonTableColumn<T>[] {
    const own = this.config.columns.map((column, index): HqCommonTableColumn<T> => ({
      key: column.key,
      label: column.label,
      // A missing value is an empty cell (the common table would show a dash), and a long one is cut short with its full text as a tooltip.
      render: (row) => this.cell(row, column),
      tooltip: true,
      className: index === 0 ? 'lookup__col-lead' : undefined,
    }));
    return this.config.multiple
      ? [{ key: LOOKUP_SELECT_COLUMN, label: '', className: 'lookup__col-select' }, ...own]
      : [...own, { key: LOOKUP_PICK_COLUMN, label: '', className: 'lookup__col-pick' }];
  }

  /** The words of the optional members of the texts, with the English default where an app did not provide them. */
  get words(): Record<HqLookupWord, string> {
    const own = this.texts;
    const fallback = HQ_LOOKUP_DEFAULT_TEXTS;
    return {
      close: (own.close ?? fallback.close)(),
      searchLabel: (own.searchLabel ?? fallback.searchLabel)(),
      searchButton: (own.searchButton ?? fallback.searchButton)(),
      showingResultsFor: (own.showingResultsFor ?? fallback.showingResultsFor)(),
      clearResult: (own.clearResult ?? fallback.clearResult)(),
      allFields: (own.allFields ?? fallback.allFields)(),
      searchFields: (own.searchFields ?? fallback.searchFields)(),
      selectAllOnPage: (own.selectAllOnPage ?? fallback.selectAllOnPage)(),
      addConditions: (own.addConditions ?? fallback.addConditions)(),
      addCondition: (own.addCondition ?? fallback.addCondition)(),
      advancedSearch: (own.advancedSearch ?? fallback.advancedSearch)(),
      advancedHint: (own.advancedHint ?? fallback.advancedHint)(),
      normalSearch: (own.normalSearch ?? fallback.normalSearch)(),
      join: (own.join ?? fallback.join)(),
      field: (own.field ?? fallback.field)(),
      condition: (own.condition ?? fallback.condition)(),
      value: (own.value ?? fallback.value)(),
      removeCondition: (own.removeCondition ?? fallback.removeCondition)(),
      equals: (own.equals ?? fallback.equals)(),
      notEquals: (own.notEquals ?? fallback.notEquals)(),
      contains: (own.contains ?? fallback.contains)(),
      yes: (own.yes ?? fallback.yes)(),
      no: (own.no ?? fallback.no)(),
    };
  }

  /** The columns offered as search-scope chips. */
  get searchColumns(): HqLookupDialogColumn<T>[] { return this.config.columns.filter((column) => column.searchable !== false); }

  /** The search box placeholder: names the chosen column, else the configured or default one. */
  get placeholder(): string {
    const column = this.searchColumns.find((c) => c.key === this.searchField);
    if (column) return (this.texts.searchIn ?? HQ_LOOKUP_DEFAULT_TEXTS.searchIn)(column.label);
    return this.config.searchPlaceholder || this.texts.search();
  }

  get multiple(): boolean { return !!this.config.multiple; }
  get minLength(): number { return this.config.minQueryLength ?? 0; }
  get tooShort(): boolean { return !this.isAdvanced && this.query.trim().length < this.minLength; }
  get chosenItems(): T[] { return [...this.chosen.values()]; }
  get hasMore(): boolean {
    return this.total != null ? (this.page + 1) * this.size < this.total : this.fetchedCount === this.size;
  }
  /** Every record on the page is ticked (a header checkbox of a multi-select). */
  get pageAllChosen(): boolean { return this.rows.length > 0 && this.rows.every((row) => this.isChosen(row)); }
  get pageSomeChosen(): boolean { return !this.pageAllChosen && this.rows.some((row) => this.isChosen(row)); }

  ngOnInit(): void {
    // switchMap drops the answer of an older request when a newer one starts, so fast typing never shows stale rows.
    this.subscriptions.add(this.requests$.pipe(
      tap(() => { this.isLoading = true; this.hasError = false; this.cdr.detectChanges(); }),
      switchMap(({ query, page, field, conditions }) =>
        // The column argument is only passed when a chip is chosen and the conditions only in advanced mode, so a callback
        // written for three or four arguments is unaffected.
        this.fetchPage(query, page, field, conditions).pipe(
          catchError((err) => {
            this.hasError = true;
            this.errorMessage = err?.error?.detail ?? err?.error?.message ?? '';
            return of({ items: [] as T[], total: 0 } as HqPickerPage<T>);
          }),
        )),
    ).subscribe((result) => {
      const items = result.items ?? [];
      this.fetchedCount = items.length;
      this.rows = this.narrowToField(items);
      this.narrowed = !this.isAdvanced
        && (this.rows.length !== items.length || (this.searchField !== '' && !this.config.scopedSearch && this.query.trim() !== ''));
      this.total = result.total ?? null;
      this.isLoading = false;
      this.cdr.detectChanges();
    }));
    this.subscriptions.add(
      this.search$.pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged()).subscribe((q) => this.load(q, 0)),
    );
    this.load('', 0);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  onQuery(value: string): void {
    this.query = value;
    this.search$.next(value.trim());
  }

  /** The SEARCH button (and Enter): searches now, without waiting for the typing pause. */
  searchNow(): void { this.load(this.query.trim(), 0); }

  /** Empties the search and shows everything again. */
  clearQuery(): void {
    if (this.isAdvanced) {
      this.conditions = [this.newCondition()];
      this.appliedConditions = [];
      this.load('', 0);
      return;
    }
    this.query = '';
    this.search$.next('');
    this.load('', 0);
  }

  /** Chooses the column the search is scoped to ('' = all fields); starts again on the first page. */
  selectField(key: string): void {
    if (key === this.searchField) return;
    this.searchField = key;
    this.load(this.query.trim(), 0);
  }

  isField(key: string): boolean { return this.searchField === key; }

  goTo(page: number): void { this.load(this.currentQuery, page); }

  resize(size: number): void { this.size = size; this.load(this.currentQuery, 0); }

  /** The quick-search text in force ('' in advanced mode, where the conditions filter instead). */
  private get currentQuery(): string { return this.isAdvanced ? '' : this.query.trim(); }

  private load(query: string, page: number): void {
    this.page = page;
    if (!this.isAdvanced && query.length < this.minLength) {
      this.rows = []; this.fetchedCount = 0; this.narrowed = false; this.total = null; this.isLoading = false; this.cdr.detectChanges();
      return;
    }
    this.requests$.next(this.isAdvanced
      ? { query: '', page, field: '', conditions: this.appliedConditions }
      : { query, page, field: this.searchField, conditions: [] });
  }

  /** Calls the screen's fetch with only the arguments that apply, so older three- and four-argument callbacks keep working. */
  private fetchPage(query: string, page: number, field: string, conditions: readonly HqLookupCondition[]): Observable<HqPickerPage<T>> {
    if (conditions.length) return this.config.fetch(query, page, this.size, undefined, conditions);
    return field ? this.config.fetch(query, page, this.size, field) : this.config.fetch(query, page, this.size);
  }

  // ---- advanced conditions --------------------------------------------------------------------

  /** The columns a condition may be on: `advancedColumns` (in that order), else every searchable column. */
  get conditionColumns(): HqLookupDialogColumn<T>[] {
    const keys = this.config.advancedColumns;
    if (!keys?.length) return this.searchColumns;
    return keys.map((key) => this.config.columns.find((c) => c.key === key)).filter((c): c is HqLookupDialogColumn<T> => !!c);
  }

  /** The kind of value of a condition's column (default 'text'). */
  typeOf(fieldName: string): HqLookupColumnType {
    return this.config.columns.find((c) => c.key === fieldName)?.type ?? 'text';
  }

  /** The operators offered for a row: the configured ones, and only equals / not equals for a non-text column. */
  operatorsFor(fieldName: string): Array<{ value: HqLookupOperator; label: string }> {
    const allowed = this.config.operators?.length ? this.config.operators : LOOKUP_ALL_OPERATORS;
    const forType = this.typeOf(fieldName) === 'text' ? LOOKUP_ALL_OPERATORS : LOOKUP_EXACT_OPERATORS;
    const words = this.words;
    const label: Record<HqLookupOperator, string> = { EQ: words.equals, NE: words.notEquals, LIKE: words.contains };
    return forType.filter((op) => allowed.includes(op)).map((op) => ({ value: op, label: label[op] }));
  }

  /** Switches to the condition rows, starting with one empty row. */
  openAdvanced(): void {
    this.isAdvanced = true;
    if (!this.conditions.length) this.conditions = [this.newCondition()];
  }

  addCondition(): void {
    this.conditions = [...this.conditions, this.newCondition()];
  }

  removeCondition(index: number): void {
    this.conditions = this.conditions.filter((_, i) => i !== index);
    if (!this.conditions.length) this.conditions = [this.newCondition()];
  }

  /** A row's column changed: keeps its operator only when the new column allows it, and clears a value of another kind. */
  onConditionField(row: HqLookupCondition, fieldName: string): void {
    const kindChanged = this.typeOf(row.fieldName) !== this.typeOf(fieldName);
    row.fieldName = fieldName;
    const ops = this.operatorsFor(fieldName).map((op) => op.value);
    if (!ops.includes(row.operator)) row.operator = ops[0] ?? 'EQ';
    if (kindChanged) row.value = '';
  }

  /** At least one row has a column and a value. */
  get canRunAdvanced(): boolean { return this.conditions.some((c) => !!c.fieldName && String(c.value ?? '').trim() !== ''); }

  /** "Advanced search": the complete rows (column and value set) filter the results, from the first page. */
  runAdvanced(): void {
    if (!this.canRunAdvanced) return;
    this.appliedConditions = this.conditions
      .filter((c) => !!c.fieldName && String(c.value ?? '').trim() !== '')
      .map((c, i): HqLookupCondition => ({ ...c, value: String(c.value).trim(), condition: i === 0 ? 'AND' : c.condition }));
    this.load('', 0);
  }

  /** Back to the quick search; the conditions are dropped. */
  normalSearch(): void {
    this.isAdvanced = false;
    this.conditions = [];
    this.appliedConditions = [];
    this.load(this.query.trim(), 0);
  }

  trackCondition = (index: number): number => index;

  private newCondition(): HqLookupCondition {
    const first = this.conditionColumns[0]?.key ?? '';
    return { fieldName: first, operator: this.operatorsFor(first)[0]?.value ?? 'EQ', value: '', condition: 'AND' };
  }

  /** When the callback cannot scope by column, keep the page's records whose chosen column contains the text. */
  private narrowToField(items: T[]): T[] {
    const text = this.query.trim().toLowerCase();
    const column = this.searchColumns.find((c) => c.key === this.searchField);
    if (!column || this.config.scopedSearch || !text) return items;
    return items.filter((item) => this.cell(item, column).toLowerCase().includes(text));
  }

  // ---- selection ------------------------------------------------------------------------------

  isChosen(item: T): boolean { return this.chosen.has(this.config.idOf(item)); }

  toggle(item: T): void {
    const id = this.config.idOf(item);
    if (this.multiple) {
      if (this.chosen.has(id)) this.chosen.delete(id); else this.chosen.set(id, item);
    } else {
      this.chosen.clear();
      this.chosen.set(id, item);
    }
  }

  /** In single-select mode a click on a row confirms straight away. */
  rowClick(item: T): void {
    this.toggle(item);
    if (!this.multiple) this.confirm();
  }

  clear(): void { this.chosen.clear(); }

  /** The header checkbox: ticks every record on this page, or clears them when they are all ticked already. */
  togglePage(): void {
    const all = this.pageAllChosen;
    this.rows.forEach((row) => {
      const id = this.config.idOf(row);
      if (all) this.chosen.delete(id); else this.chosen.set(id, row);
    });
  }

  /** Remembers whether the table body has scrolled, to show the header's shadow. */
  onBodyScroll(event: Event): void { this.scrolled = (event.target as HTMLElement).scrollTop > 0; }

  cell(item: T, column: HqLookupDialogColumn<T>): string {
    if (column.render) return column.render(item);
    const value = column.key.split('.').reduce<unknown>((o, k) => (o as Record<string, unknown> | null)?.[k], item);
    return value == null ? '' : String(value);
  }

  label(item: T): string {
    return this.config.chipLabel ? this.config.chipLabel(item) : this.cell(item, this.config.columns[0]);
  }

  /** The quieter second line of a record: every column after the first, joined. */
  meta(item: T): string {
    return this.config.columns.slice(1).map((column) => this.cell(item, column)).filter(Boolean).join('  ·  ');
  }

  /** Up to two capital letters for the round avatar. */
  initials(item: T): string {
    const words = this.label(item).trim().split(/\s+/).filter(Boolean);
    return ((words[0]?.[0] ?? '') + (words.length > 1 ? words[words.length - 1][0] : '')).toUpperCase() || '?';
  }

  /** Reloads the current page after a failure. */
  retry(): void { this.load(this.currentQuery, this.page); }

  trackRow = (_: number, item: T): string => this.config.idOf(item);

  cancel(): void { this.ref.close(null); }

  confirm(): void { this.ref.close(this.chosenItems); }
}
