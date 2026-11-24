import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { Observable, Subject, of, throwError } from 'rxjs';
import type { Mock } from 'vitest';
import { HqSharedLibModule } from '../hq-shared-lib.module';
import { HqCommonSearchService } from '../hq-common-search.service';
import { HqLookupDialogData, HqLookupPage, HqLookupTable } from '../hq-common.models';
import { HqCommonModel } from './hq-common-model';

interface Row { id: number | null; name: { first: string } | null; city: string; joined: string }

const ROWS: Row[] = [
  { id: 1, name: { first: 'Asha' }, city: 'Pune', joined: '2026-03-04T12:00:00Z' },
  { id: 2, name: { first: 'Ravi' }, city: 'Goa', joined: '2026-05-06T12:00:00Z' },
];

function table(): HqLookupTable {
  return {
    id: 'people',
    title: 'People',
    columns: [
      { key: 'name.first', label: 'Name' },
      { key: 'city', label: 'City' },
      { key: 'joined', label: 'Joined', type: 'date', searchable: false },
    ],
    defaultSearchField: 'name.first',
    keyField: 'id',
    displayField: 'name.first',
    fetch: () => of({ rows: [], total: 0 }),
  };
}

interface Setup {
  fixture: ComponentFixture<HqCommonModel>;
  comp: HqCommonModel;
  query: Mock;
  dialogRef: { close: Mock };
  data: HqLookupDialogData;
}

function setup(
  data: Partial<HqLookupDialogData> = {},
  response: () => Observable<HqLookupPage> = () => of({ rows: ROWS, total: 25 }),
): Setup {
  const query = vi.fn(() => response());
  const search = {
    getTable: vi.fn(() => table()),
    query,
    read: (row: unknown, path: string) =>
      path.split('.').reduce<unknown>((acc, part) => (acc == null ? acc : (acc as Record<string, unknown>)[part]), row),
  };
  const dialogRef = { close: vi.fn() };
  const fullData: HqLookupDialogData = { lookupTable: 'people', ...data };
  TestBed.configureTestingModule({
    imports: [HqSharedLibModule],
    providers: [
      { provide: HqCommonSearchService, useValue: search },
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: fullData },
    ],
  });
  const fixture = TestBed.createComponent(HqCommonModel);
  return { fixture, comp: fixture.componentInstance, query, dialogRef, data: fullData };
}

function render(s: Setup): void {
  s.fixture.detectChanges();
}

function el(fixture: ComponentFixture<unknown>, selector: string): HTMLElement | null {
  return fixture.nativeElement.querySelector(selector);
}

function buttons(fixture: ComponentFixture<unknown>): HTMLButtonElement[] {
  return Array.from(fixture.nativeElement.querySelectorAll('button'));
}

function button(fixture: ComponentFixture<unknown>, text: string): HTMLButtonElement | undefined {
  return buttons(fixture).find((b) => b.textContent?.includes(text));
}

describe('HqCommonModel', () => {
  describe('initial load', () => {
    it('reads the table, builds columns and loads the first page', () => {
      const s = setup({ selected: [{ key: '1', value: 'Asha', element: ROWS[0] }] });
      const original = s.data.selected;
      s.comp.ngOnInit();
      expect(s.comp.table.id).toBe('people');
      expect(s.comp.searchColumns.map((c) => c.key)).toEqual(['name.first', 'city']);
      expect(s.comp.displayedColumns).toEqual(['name.first', 'city', 'joined', 'actions']);
      expect(s.comp.searchField).toBe('name.first');
      expect(s.data.selected).toEqual(original);
      expect(s.data.selected).not.toBe(original);
      expect(s.query).toHaveBeenCalledWith('people', { page: 0, size: 10, search: '', searchField: 'name.first' }, [], []);
      expect(s.comp.rows).toEqual(ROWS);
      expect(s.comp.total).toBe(25);
      expect(s.comp.loading).toBe(false);
    });

    it('starts with an empty selection and passes pre-filters to every query', () => {
      const filters = [{ fieldName: 'city', operator: 'EQ' as const, value: 'Pune', condition: 'AND' as const }];
      const s = setup({ filters });
      s.comp.ngOnInit();
      expect(s.data.selected).toEqual([]);
      expect(s.comp.hasPreFilters).toBe(true);
      expect(s.query).toHaveBeenCalledWith('people', expect.anything(), [], filters);
    });

    it('hasPreFilters is false without filters', () => {
      const s = setup({ filters: [] });
      expect(s.comp.hasPreFilters).toBe(false);
    });

    it('shows the server error message, or a generic one', () => {
      const s = setup({}, () => throwError(() => ({ error: { message: 'Search down' } })));
      s.comp.ngOnInit();
      expect(s.comp.errorMessage).toBe('Search down');
      expect(s.comp.rows).toEqual([]);
      expect(s.comp.total).toBe(0);
      expect(s.comp.loading).toBe(false);

      s.query.mockReturnValueOnce(throwError(() => null));
      s.comp.load();
      expect(s.comp.errorMessage).toBe('Unable to load records.');
    });

    it('a new load cancels the one in flight, and destroy cancels the last', () => {
      const first = new Subject<HqLookupPage>();
      const second = new Subject<HqLookupPage>();
      const s = setup();
      s.query.mockReturnValueOnce(first).mockReturnValueOnce(second);
      s.comp.ngOnInit();
      expect(s.comp.loading).toBe(true);
      s.comp.load(2);
      expect(first.observed).toBe(false);
      expect(second.observed).toBe(true);
      expect(s.comp.pageIndex).toBe(2);
      s.comp.ngOnDestroy();
      expect(second.observed).toBe(false);
    });

    it('destroy before init is safe', () => {
      const s = setup();
      expect(() => s.comp.ngOnDestroy()).not.toThrow();
    });
  });

  describe('paging and quick search', () => {
    it('onPage applies the size and loads the requested page', () => {
      const s = setup();
      s.comp.ngOnInit();
      s.comp.onPage({ pageIndex: 3, pageSize: 50 });
      expect(s.comp.pageSize).toBe(50);
      expect(s.query).toHaveBeenLastCalledWith('people', { page: 3, size: 50, search: '', searchField: 'name.first' }, [], []);
    });

    it('runSearch trims the text, remembers it and loads page 0 on the chosen field', () => {
      const s = setup();
      s.comp.ngOnInit();
      s.comp.pageIndex = 4;
      s.comp.selectSearchField('city');
      s.comp.search = '  Pune ';
      s.comp.runSearch();
      expect(s.comp.lastSearch).toBe('Pune');
      expect(s.query).toHaveBeenLastCalledWith('people', { page: 0, size: 10, search: '  Pune ', searchField: 'city' }, [], []);
    });

    it('clearResult resets the search and reloads', () => {
      const s = setup();
      s.comp.ngOnInit();
      s.comp.search = 'x';
      s.comp.lastSearch = 'x';
      s.comp.clearResult();
      expect(s.comp.search).toBe('');
      expect(s.comp.lastSearch).toBe('');
      expect(s.comp.advancedFilters).toEqual([]);
      expect(s.query).toHaveBeenLastCalledWith('people', expect.objectContaining({ page: 0, search: '' }), [], []);
    });
  });

  describe('advanced search', () => {
    it('openAdvanced starts with one AND/LIKE row on the default field and clears quick search', () => {
      const s = setup();
      s.comp.ngOnInit();
      s.comp.search = 'abc';
      s.comp.lastSearch = 'abc';
      s.comp.openAdvanced();
      expect(s.comp.isAdvanced).toBe(true);
      expect(s.comp.search).toBe('');
      expect(s.comp.lastSearch).toBe('');
      expect(s.comp.advancedFilters).toEqual([{ fieldName: 'name.first', operator: 'LIKE', value: '', condition: 'AND' }]);
      s.comp.advancedFilters[0].value = 'keep';
      s.comp.openAdvanced();
      expect(s.comp.advancedFilters).toHaveLength(1);
      expect(s.comp.advancedFilters[0].value).toBe('keep');
    });

    it('addCondition appends an OR row; removeCondition keeps at least one row', () => {
      const s = setup();
      s.comp.ngOnInit();
      s.comp.openAdvanced();
      s.comp.addCondition();
      expect(s.comp.advancedFilters.map((f) => f.condition)).toEqual(['AND', 'OR']);
      s.comp.removeCondition(0);
      expect(s.comp.advancedFilters).toHaveLength(1);
      expect(s.comp.advancedFilters[0].condition).toBe('OR');
      s.comp.removeCondition(0);
      expect(s.comp.advancedFilters).toEqual([{ fieldName: 'name.first', operator: 'LIKE', value: '', condition: 'AND' }]);
    });

    it('runAdvanced sends the filters without the quick search text', () => {
      const s = setup();
      s.comp.ngOnInit();
      s.comp.search = 'ignored';
      s.comp.isAdvanced = true;
      s.comp.advancedFilters = [{ fieldName: 'city', operator: 'EQ', value: 'Goa', condition: 'AND' }];
      s.comp.runAdvanced();
      expect(s.query).toHaveBeenLastCalledWith('people', expect.objectContaining({ page: 0, search: '' }),
        [{ fieldName: 'city', operator: 'EQ', value: 'Goa', condition: 'AND' }], []);
    });

    it('clearResult in advanced mode resets to one empty row', () => {
      const s = setup();
      s.comp.ngOnInit();
      s.comp.openAdvanced();
      s.comp.addCondition();
      s.comp.clearResult();
      expect(s.comp.advancedFilters).toEqual([{ fieldName: 'name.first', operator: 'LIKE', value: '', condition: 'AND' }]);
    });

    it('normalSearch leaves advanced mode and drops the filters', () => {
      const s = setup();
      s.comp.ngOnInit();
      s.comp.openAdvanced();
      s.comp.normalSearch();
      expect(s.comp.isAdvanced).toBe(false);
      expect(s.comp.advancedFilters).toEqual([]);
      expect(s.query).toHaveBeenLastCalledWith('people', expect.anything(), [], []);
    });
  });

  describe('selection', () => {
    it('single select: pick sets key/value/element and closes with the data', () => {
      const s = setup();
      s.comp.ngOnInit();
      expect(s.comp.isSelected(ROWS[0])).toBe(false);
      s.comp.pick(ROWS[0]);
      expect(s.data.modelKey).toBe('1');
      expect(s.data.displayValue).toBe('Asha');
      expect(s.data.element).toBe(ROWS[0]);
      expect(s.dialogRef.close).toHaveBeenCalledWith(s.data);
      expect(s.comp.isSelected(ROWS[0])).toBe(true);
    });

    it('falls back to the key, or empty, when display or key are missing', () => {
      const s = setup();
      s.comp.ngOnInit();
      s.comp.pick({ id: 7, name: null, city: '', joined: '' });
      expect(s.data.displayValue).toBe('7');
      s.comp.pick({ id: null, name: null, city: '', joined: '' });
      expect(s.data.modelKey).toBe('');
      expect(s.data.displayValue).toBe('');
    });

    it('multi select: pick adds once, keeps the dialog open and counts values', () => {
      const s = setup({ multiSelect: true });
      s.comp.ngOnInit();
      s.comp.pick(ROWS[0]);
      s.comp.pick(ROWS[1]);
      s.comp.pick(ROWS[0]);
      expect(s.data.selected).toEqual([
        { key: '1', value: 'Asha', element: ROWS[0] },
        { key: '2', value: 'Ravi', element: ROWS[1] },
      ]);
      expect(s.data.displayValue).toBe('2 values');
      expect(s.comp.isSelected(ROWS[1])).toBe(true);
      expect(s.dialogRef.close).not.toHaveBeenCalled();
    });

    it('isSelected in multi mode is false when nothing is selected', () => {
      const s = setup({ multiSelect: true });
      s.comp.table = table();
      expect(s.comp.isSelected(ROWS[0])).toBe(false);
    });

    it('unpick removes a value and updates the count, empty when none left', () => {
      const s = setup({ multiSelect: true });
      s.comp.ngOnInit();
      s.comp.pick(ROWS[0]);
      s.comp.pick(ROWS[1]);
      s.comp.unpick('1');
      expect(s.data.selected?.map((x) => x.key)).toEqual(['2']);
      expect(s.data.displayValue).toBe('1 values');
      s.comp.unpick('2');
      expect(s.data.displayValue).toBe('');
    });

    it('unpick copes with no selection list', () => {
      const s = setup({ multiSelect: true });
      s.comp.unpick('1');
      expect(s.data.selected).toEqual([]);
      expect(s.data.displayValue).toBe('');
    });

    it('clearSelection wipes single and multi results; close returns the data', () => {
      const s = setup({ modelKey: '1', displayValue: 'Asha', element: ROWS[0], selected: [{ key: '1', value: 'A', element: {} }] });
      s.comp.clearSelection();
      expect(s.data).toEqual(expect.objectContaining({ modelKey: '', displayValue: '', element: undefined, selected: [] }));
      s.comp.close();
      expect(s.dialogRef.close).toHaveBeenCalledWith(s.data);
    });
  });

  describe('template', () => {
    it('renders the title, results table with formatted dates, and paginator', () => {
      const s = setup();
      render(s);
      expect(el(s.fixture, '.hq-lookup__title')?.textContent).toContain('People');
      expect(button(s.fixture, 'Close')).toBeDefined();
      const headers = Array.from(s.fixture.nativeElement.querySelectorAll('th')).map((h) => (h as HTMLElement).textContent?.trim());
      expect(headers).toEqual(['Name', 'City', 'Joined', '']);
      const rows = s.fixture.nativeElement.querySelectorAll('tr.mat-mdc-row');
      expect(rows.length).toBe(2);
      expect(rows[0].textContent).toContain('Asha');
      expect(rows[0].textContent).toContain('Pune');
      expect(rows[0].textContent).toContain('Mar 4, 2026');
      expect(el(s.fixture, 'mat-paginator')).not.toBeNull();
      expect(el(s.fixture, '.hq-lookup__fields')).not.toBeNull();
    });

    it('Close button closes the dialog; the select button picks a row', () => {
      const s = setup();
      render(s);
      button(s.fixture, 'Close')!.click();
      expect(s.dialogRef.close).toHaveBeenCalledTimes(1);
      const selectButtons = s.fixture.nativeElement.querySelectorAll('button[aria-label="Select"]');
      (selectButtons[1] as HTMLButtonElement).click();
      expect(s.data.modelKey).toBe('2');
      expect(s.dialogRef.close).toHaveBeenCalledTimes(2);
    });

    it('marks the already selected row and disables its button', () => {
      const s = setup({ modelKey: '1', displayValue: 'Asha' });
      render(s);
      const selectButtons = s.fixture.nativeElement.querySelectorAll('button[aria-label="Select"]');
      expect((selectButtons[0] as HTMLButtonElement).disabled).toBe(true);
      expect(selectButtons[0].textContent).toContain('check');
      expect((selectButtons[1] as HTMLButtonElement).disabled).toBe(false);
      expect(selectButtons[1].textContent).toContain('near_me');
    });

    it('single select shows the current value as a chip that can be cleared', () => {
      const s = setup({ modelKey: '1', displayValue: 'Asha' });
      render(s);
      expect(el(s.fixture, '.hq-lookup__selection')?.textContent).toContain('Asha');
      (el(s.fixture, 'button[aria-label="Clear selection"]') as HTMLButtonElement).click();
      expect(s.data.displayValue).toBe('');
      s.fixture.detectChanges();
      expect(el(s.fixture, '.hq-lookup__selection')).toBeNull();
    });

    it('multi select shows Done, chips per value with remove, and Remove all', () => {
      const s = setup({
        multiSelect: true,
        selected: [{ key: '1', value: 'Asha', element: ROWS[0] }, { key: '2', value: 'Ravi', element: ROWS[1] }],
      });
      render(s);
      expect(button(s.fixture, 'Done')).toBeDefined();
      (el(s.fixture, 'button[aria-label="Remove Asha"]') as HTMLButtonElement).click();
      expect(s.data.selected?.map((x) => x.key)).toEqual(['2']);
      button(s.fixture, 'Remove all')!.click();
      expect(s.data.selected).toEqual([]);
      s.fixture.detectChanges();
      expect(el(s.fixture, '.hq-lookup__selection')).toBeNull();
    });

    it('selecting a column chip changes the search field; deselecting does not', () => {
      const s = setup();
      render(s);
      const options = s.fixture.nativeElement.querySelectorAll('mat-chip-option');
      expect(options.length).toBe(2);
      (options[1].querySelector('button') ?? options[1]).click();
      expect(s.comp.searchField).toBe('city');
      s.fixture.detectChanges();
      const again = s.fixture.nativeElement.querySelectorAll('mat-chip-option');
      (again[1].querySelector('button') ?? again[1]).click();
      expect(s.comp.searchField).toBe('city');
    });

    it('typing and pressing Enter searches; the clear button resets', async () => {
      const s = setup();
      render(s);
      const input = el(s.fixture, '.hq-lookup__search input') as HTMLInputElement;
      input.value = 'Goa';
      input.dispatchEvent(new Event('input'));
      input.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter' }));
      expect(s.comp.lastSearch).toBe('Goa');
      expect(s.query).toHaveBeenLastCalledWith('people', expect.objectContaining({ search: 'Goa' }), [], []);
      s.fixture.detectChanges();
      expect(el(s.fixture, '.hq-lookup__muted b')?.textContent).toBe('Goa');
      (el(s.fixture, 'button[aria-label="Clear search"]') as HTMLButtonElement).click();
      expect(s.comp.search).toBe('');
      s.fixture.detectChanges();
      expect(el(s.fixture, 'button[aria-label="Clear search"]')).toBeNull();
      button(s.fixture, 'SEARCH')!.click();
      expect(s.query).toHaveBeenLastCalledWith('people', expect.objectContaining({ page: 0, search: '' }), [], []);
    });

    it('shows a spinner while loading', () => {
      const pending = new Subject<HqLookupPage>();
      const s = setup({}, () => pending);
      render(s);
      expect(el(s.fixture, 'mat-spinner')).not.toBeNull();
      expect(el(s.fixture, '.hq-lookup__empty')).toBeNull();
    });

    it('shows "No record found for X" with a Clear button', () => {
      const s = setup({}, () => of({ rows: [], total: 0 }));
      render(s);
      expect(el(s.fixture, '.hq-lookup__empty')?.textContent).toContain('No record found');
      s.comp.search = 'zzz';
      s.comp.runSearch();
      s.fixture.detectChanges();
      expect(el(s.fixture, '.hq-lookup__empty b')?.textContent).toBe('zzz');
      button(s.fixture, '< Clear')!.click();
      expect(s.comp.lastSearch).toBe('');
    });

    it('shows the error message instead of results', () => {
      const s = setup({}, () => throwError(() => ({ error: { message: 'Search down' } })));
      render(s);
      expect(el(s.fixture, '.hq-lookup__error')?.textContent).toContain('Search down');
      expect(el(s.fixture, 'table')).toBeNull();
    });

    it('paginator events reload the requested page; Clear result resets', () => {
      const s = setup();
      render(s);
      const paginator = s.fixture.debugElement.query(By.directive(MatPaginator)).componentInstance as MatPaginator;
      paginator.nextPage();
      expect(s.query).toHaveBeenLastCalledWith('people', expect.objectContaining({ page: 1, size: 10 }), [], []);
      button(s.fixture, 'Clear result')!.click();
      expect(s.query).toHaveBeenLastCalledWith('people', expect.objectContaining({ page: 0 }), [], []);
    });

    it('hides "+ Add conditions" when pre-filters apply', () => {
      const s = setup({ filters: [{ fieldName: 'city', operator: 'EQ', value: 'Pune', condition: 'AND' }] });
      render(s);
      expect(button(s.fixture, '+ Add conditions')).toBeUndefined();
    });

    it('advanced mode renders condition rows and drives the advanced actions', () => {
      const s = setup();
      render(s);
      button(s.fixture, '+ Add conditions')!.click();
      s.fixture.detectChanges();
      expect(s.comp.isAdvanced).toBe(true);
      expect(s.fixture.nativeElement.querySelectorAll('.hq-lookup__condition').length).toBe(1);
      expect(el(s.fixture, '.hq-lookup__fields')).toBeNull();

      button(s.fixture, '+ Add condition')!.click();
      s.fixture.detectChanges();
      const rows = s.fixture.nativeElement.querySelectorAll('.hq-lookup__condition');
      expect(rows.length).toBe(2);
      expect(rows[0].textContent).not.toContain('Join');
      expect(rows[1].textContent).toContain('Join');

      const valueInput = rows[1].querySelectorAll('input')[0] as HTMLInputElement;
      valueInput.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter' }));
      expect(s.query).toHaveBeenLastCalledWith('people', expect.objectContaining({ search: '' }), s.comp.advancedFilters, []);

      button(s.fixture, 'Advanced search')!.click();
      expect(s.query).toHaveBeenLastCalledWith('people', expect.objectContaining({ page: 0 }), s.comp.advancedFilters, []);

      (rows[1].querySelector('button[aria-label="Remove condition"]') as HTMLButtonElement).click();
      expect(s.comp.advancedFilters).toHaveLength(1);

      s.comp.lastSearch = 'hidden';
      s.fixture.detectChanges();
      expect(s.fixture.nativeElement.textContent).not.toContain('Showing results for');

      button(s.fixture, '< Normal search')!.click();
      expect(s.comp.isAdvanced).toBe(false);
    });

    it('advanced mode shows a spinner while loading and hides the search term on empty results', async () => {
      const pending = new Subject<HqLookupPage>();
      const s = setup({}, () => of({ rows: [], total: 0 }));
      render(s);
      button(s.fixture, '+ Add conditions')!.click();
      s.fixture.detectChanges();
      await s.fixture.whenStable();
      s.comp.lastSearch = 'x';
      s.fixture.changeDetectorRef.markForCheck();
      s.fixture.detectChanges();
      expect(el(s.fixture, '.hq-lookup__empty')?.textContent).toContain('No record found');
      expect(el(s.fixture, '.hq-lookup__empty b')).toBeNull();
      s.query.mockReturnValueOnce(pending);
      button(s.fixture, 'Advanced search')!.click();
      s.fixture.detectChanges();
      await s.fixture.whenStable();
      expect(el(s.fixture, 'mat-spinner')).not.toBeNull();
    });
  });
});
