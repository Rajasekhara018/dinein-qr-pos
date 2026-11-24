import { Component, ViewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqCommonTable, HqCommonTableColumn, HqCommonTableSort } from './hq-common-table';
import { HQ_COMMON_TABLE_TEXTS, HqCommonTableTexts } from './hq-common-table.texts';

type Row = { name: string; qty: number; active?: boolean; tags?: string[]; address?: { city?: string | null } | null; note?: string | null };

const COLUMNS: HqCommonTableColumn<Row>[] = [
  { key: 'name', label: 'Name', sortable: true, tooltip: true },
  { key: 'qty', label: 'Qty', sortable: true },
  { key: 'address.city', label: 'City' },
];

const ROWS: Row[] = [
  { name: 'b', qty: 10, address: { city: 'Pune' } },
  { name: 'a', qty: 2, address: { city: 'Goa' } },
];

@Component({
  imports: [HqSharedLibModule],
  template: `
    <hq-common-table
      [columns]="columns" [rows]="rows" [isLoading]="isLoading" [hasError]="hasError"
      [emptyMessage]="emptyMessage" [errorMessage]="errorMessage" [actionsLabel]="actionsLabel"
      [showActionsColumn]="showActions" [clickableRows]="clickable" [highlightIndex]="highlight"
      (rowClick)="clicked.push($event)" (sortChange)="sorts.push($event)">
      <ng-template #rowActions let-row>
        <button class="edit" type="button" (click)="edited.push(row)">Edit {{ row.name }}</button>
      </ng-template>
    </hq-common-table>
  `,
})
class HostWithActions {
  @ViewChild(HqCommonTable) table!: HqCommonTable<Row>;
  columns = COLUMNS;
  rows: Row[] = ROWS;
  isLoading = false;
  hasError = false;
  emptyMessage = '';
  errorMessage = '';
  actionsLabel = '';
  showActions = true;
  clickable = false;
  highlight: number | null = null;
  clicked: Row[] = [];
  sorts: (HqCommonTableSort | null)[] = [];
  edited: Row[] = [];
}

@Component({
  imports: [HqSharedLibModule],
  template: `<hq-common-table [columns]="columns" [rows]="rows"></hq-common-table>`,
})
class HostWithoutActions {
  columns = COLUMNS;
  rows: Row[] = ROWS;
}

function configure(texts?: HqCommonTableTexts): void {
  TestBed.configureTestingModule({
    imports: [CommonModule, HostWithActions, HostWithoutActions],
    providers: texts ? [{ provide: HQ_COMMON_TABLE_TEXTS, useValue: texts }] : [],
  });
}

function render<H>(type: new () => H): { fixture: ComponentFixture<H>; host: H; el: HTMLElement } {
  const fixture = TestBed.createComponent(type);
  fixture.detectChanges();
  return { fixture, host: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
}

function bodyText(el: HTMLElement): string[][] {
  return Array.from(el.querySelectorAll('tbody tr')).map((tr) =>
    Array.from(tr.querySelectorAll('td')).map((td) => td.textContent?.trim() ?? ''),
  );
}

describe('HqCommonTable template', () => {
  describe('with default texts', () => {
    beforeEach(() => configure());

    it('renders headers, rows by dot-path and the default actions heading with projected actions', () => {
      const { el, host } = render(HostWithActions);
      const headers = Array.from(el.querySelectorAll('th')).map((th) => th.textContent?.trim());
      expect(headers).toEqual(['Name', 'Qty', 'City', 'Actions']);
      expect(bodyText(el)).toEqual([
        ['b', '10', 'Pune', 'Edit b'],
        ['a', '2', 'Goa', 'Edit a'],
      ]);
      (el.querySelectorAll('button.edit')[1] as HTMLButtonElement).click();
      expect(host.edited).toEqual([ROWS[1]]);
    });

    it('sets the tooltip title only on tooltip columns', () => {
      const { el } = render(HostWithActions);
      const firstRow = el.querySelectorAll('tbody tr')[0].querySelectorAll('td');
      expect(firstRow[0].getAttribute('title')).toBe('b');
      expect(firstRow[1].hasAttribute('title')).toBe(false);
    });

    it('marks sortable headers focusable with aria-sort and sorts on click and Enter', () => {
      const { el, host, fixture } = render(HostWithActions);
      const ths = el.querySelectorAll('th');
      expect(ths[0].getAttribute('tabindex')).toBe('0');
      expect(ths[0].classList.contains('sortable')).toBe(true);
      expect(ths[0].getAttribute('aria-sort')).toBe('none');
      expect(ths[0].querySelector('.sort-icon')?.classList.contains('bi-arrow-down-up')).toBe(true);
      expect(ths[2].hasAttribute('tabindex')).toBe(false);
      expect(ths[2].hasAttribute('aria-sort')).toBe(false);
      expect(ths[2].querySelector('.sort-icon')).toBeNull();

      ths[0].click();
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(ths[0].getAttribute('aria-sort')).toBe('ascending');
      expect(ths[0].querySelector('.sort-icon')?.classList.contains('bi-sort-up')).toBe(true);
      expect(bodyText(el).map((r) => r[0])).toEqual(['a', 'b']);

      ths[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(ths[0].getAttribute('aria-sort')).toBe('descending');
      expect(ths[0].querySelector('.sort-icon')?.classList.contains('bi-sort-down')).toBe(true);
      expect(bodyText(el).map((r) => r[0])).toEqual(['b', 'a']);

      ths[2].click();
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(host.sorts).toEqual([{ key: 'name', direction: 'asc' }, { key: 'name', direction: 'desc' }]);
    });

    it('emits rowClick only when rows are clickable', () => {
      const { el, host, fixture } = render(HostWithActions);
      const firstRow = el.querySelector('tbody tr') as HTMLTableRowElement;
      expect(firstRow.classList.contains('clickable-row')).toBe(false);
      firstRow.click();
      expect(host.clicked).toEqual([]);

      host.clickable = true;
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(firstRow.classList.contains('clickable-row')).toBe(true);
      firstRow.click();
      expect(host.clicked).toEqual([ROWS[0]]);
    });

    it('highlights the requested row', () => {
      const { el, host, fixture } = render(HostWithActions);
      host.highlight = 1;
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      const rows = el.querySelectorAll('tbody tr');
      expect(rows[0].classList.contains('highlighted-row')).toBe(false);
      expect(rows[1].classList.contains('highlighted-row')).toBe(true);
    });

    it('shows the default empty text spanning all columns, or the caller text', () => {
      const { el, host, fixture } = render(HostWithActions);
      host.rows = [];
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      const cell = el.querySelector('.no-data-cell') as HTMLTableCellElement;
      expect(cell.textContent?.trim()).toBe('No records found.');
      expect(cell.getAttribute('colspan')).toBe('4');

      host.emptyMessage = 'Nothing yet';
      host.showActions = false;
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(el.querySelector('.no-data-cell')?.textContent?.trim()).toBe('Nothing yet');
      expect(el.querySelector('.no-data-cell')?.getAttribute('colspan')).toBe('3');
      expect(Array.from(el.querySelectorAll('th')).map((th) => th.textContent?.trim())).toEqual(['Name', 'Qty', 'City']);
    });

    it('hides the empty row while loading', () => {
      const { el, host, fixture } = render(HostWithActions);
      host.rows = [];
      host.isLoading = true;
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(el.querySelector('.no-data-cell')).toBeNull();
    });

    it('shows the error state (default then caller text) instead of the empty row', () => {
      const { el, host, fixture } = render(HostWithActions);
      host.rows = [];
      host.hasError = true;
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(el.querySelector('.no-data-cell')).toBeNull();
      expect(el.querySelector('.app-state--error h3')?.textContent?.trim()).toBe('Failed to load data.');

      host.errorMessage = 'Server down';
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(el.querySelector('.app-state--error h3')?.textContent?.trim()).toBe('Server down');
    });

    it('uses the caller actions label', () => {
      const { el, host, fixture } = render(HostWithActions);
      host.actionsLabel = 'Options';
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(el.querySelectorAll('th')[3].textContent?.trim()).toBe('Options');
    });

    it('renders an empty actions cell when no template is projected', () => {
      const { el } = render(HostWithoutActions);
      const cells = el.querySelectorAll('tbody tr')[0].querySelectorAll('td');
      expect(cells.length).toBe(4);
      expect(cells[3].querySelector('.row-actions')?.children.length).toBe(0);
    });
  });

  describe('cellValue', () => {
    beforeEach(() => configure());

    function table(): HqCommonTable<Row> {
      return TestBed.runInInjectionContext(() => new HqCommonTable<Row>());
    }

    it('uses a render function when given', () => {
      expect(table().cellValue(ROWS[0], { key: 'name', label: 'N', render: (r) => r.name.toUpperCase() })).toBe('B');
    });

    it('joins arrays and shows a dash for empty values, missing paths and non-object parents', () => {
      const t = table();
      const dash = '—';
      expect(t.cellValue({ name: 'x', qty: 1, tags: ['a', 'b'] }, { key: 'tags', label: 'T' })).toBe('a, b');
      expect(t.cellValue({ name: 'x', qty: 1, tags: [] }, { key: 'tags', label: 'T' })).toBe(dash);
      expect(t.cellValue({ name: 'x', qty: 1, note: null }, { key: 'note', label: 'N' })).toBe(dash);
      expect(t.cellValue({ name: '', qty: 1 }, { key: 'name', label: 'N' })).toBe(dash);
      expect(t.cellValue({ name: 'x', qty: 1 }, { key: 'missing', label: 'M' })).toBe(dash);
      expect(t.cellValue({ name: 'x', qty: 1, address: null }, { key: 'address.city', label: 'C' })).toBe(dash);
      expect(t.cellValue({ name: 'x', qty: 1 }, { key: 'name.length', label: 'L' })).toBe(dash);
    });

    it('stringifies numbers, including zero', () => {
      expect(table().cellValue({ name: 'x', qty: 0 }, { key: 'qty', label: 'Q' })).toBe('0');
    });

    it('shows Yes / No for booleans', () => {
      const t = table();
      expect(t.cellValue({ name: 'x', qty: 1, active: true }, { key: 'active', label: 'A' })).toBe('Yes');
      expect(t.cellValue({ name: 'x', qty: 1, active: false }, { key: 'active', label: 'A' })).toBe('No');
    });

    it('returns the rows as given when the sorted column no longer exists', () => {
      const t = table();
      t.columns = COLUMNS;
      t.rows = ROWS;
      t.sort = { key: 'gone', direction: 'asc' };
      expect(t.displayRows).toBe(ROWS);
    });

    it('starts a fresh ascending sort when a different column is clicked', () => {
      const t = table();
      t.columns = COLUMNS;
      t.rows = ROWS;
      t.toggleSort(COLUMNS[0]);
      t.toggleSort(COLUMNS[1]);
      expect(t.sort).toEqual({ key: 'qty', direction: 'asc' });
      expect(t.displayRows.map((r) => r.qty)).toEqual([2, 10]);
    });
  });

  describe('with HQ_COMMON_TABLE_TEXTS', () => {
    const texts: HqCommonTableTexts = {
      noRecords: () => 'Aucun',
      loadFailed: () => 'Echec',
      actions: () => 'Actions FR',
      yes: () => 'Oui',
      no: () => 'Non',
    };
    beforeEach(() => configure(texts));

    it('uses the provided texts for headings, empty, error and boolean cells', () => {
      const { el, host, fixture } = render(HostWithActions);
      expect(el.querySelectorAll('th')[3].textContent?.trim()).toBe('Actions FR');
      host.rows = [];
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(el.querySelector('.no-data-cell')?.textContent?.trim()).toBe('Aucun');
      host.hasError = true;
      fixture.componentRef.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(el.querySelector('.app-state--error h3')?.textContent?.trim()).toBe('Echec');

      const t = host.table;
      expect(t.cellValue({ name: 'x', qty: 1, active: true }, { key: 'active', label: 'A' })).toBe('Oui');
      expect(t.cellValue({ name: 'x', qty: 1, active: false }, { key: 'active', label: 'A' })).toBe('Non');
    });
  });
});
