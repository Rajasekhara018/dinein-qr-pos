import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { SharedModule } from '../shared-module';
import { DataTable, DataTableColumn } from './data-table';

interface Row extends Record<string, unknown> {
  id: number;
  name: string;
  amount: number;
}

const columns: DataTableColumn<Row>[] = [
  { key: 'name', header: 'Name', sortable: true },
  { key: 'amount', header: 'Amount', sortable: true, align: 'end' },
];

function createTable(rows: Row[]) {
  TestBed.configureTestingModule({ imports: [SharedModule] });
  const fixture = TestBed.createComponent(DataTable<Row>);
  fixture.componentRef.setInput('columns', columns);
  fixture.componentRef.setInput('rows', rows);
  fixture.componentRef.setInput('rowIdKey', 'id');
  fixture.componentRef.setInput('pageSize', 2);
  fixture.detectChanges();
  return fixture;
}

const rows: Row[] = [
  { id: 1, name: 'Charlie', amount: 30 },
  { id: 2, name: 'Alice', amount: 10 },
  { id: 3, name: 'Bob', amount: 20 },
];

describe('DataTable', () => {
  it('paginates rows client-side by pageSize', () => {
    const fixture = createTable(rows);
    const table = fixture.componentInstance;
    expect(table['pagedRows']().length).toBe(2);
    expect(table['pageCount']()).toBe(2);
  });

  it('sorts ascending, then descending, then clears on repeated header clicks', () => {
    const fixture = createTable(rows);
    const table = fixture.componentInstance;
    const nameCol = columns[0];

    table['toggleSort'](nameCol);
    expect(table.sort()).toEqual({ key: 'name', direction: 'asc' });
    expect(table['sortedRows']()[0].name).toBe('Alice');

    table['toggleSort'](nameCol);
    expect(table.sort()).toEqual({ key: 'name', direction: 'desc' });
    expect(table['sortedRows']()[0].name).toBe('Charlie');

    table['toggleSort'](nameCol);
    expect(table.sort()).toBeNull();
  });

  it('toggles row selection and select-all-on-page', () => {
    const fixture = createTable(rows);
    const table = fixture.componentInstance;

    table['toggleRow'](rows[0]);
    expect(table.selected().has(1)).toBe(true);

    table['toggleRow'](rows[0]);
    expect(table.selected().has(1)).toBe(false);

    table['toggleAllOnPage'](); // page 1 has rows 1 and 2 (pageSize 2)
    expect(table.selected().has(1)).toBe(true);
    expect(table.selected().has(2)).toBe(true);
    expect(table.selected().has(3)).toBe(false);

    table['toggleAllOnPage']();
    expect(table.selected().size).toBe(0);
  });

  it('clamps goToPage to [1, pageCount]', () => {
    const fixture = createTable(rows);
    const table = fixture.componentInstance;

    table['goToPage'](99);
    expect(table.page()).toBe(2);

    table['goToPage'](-5);
    expect(table.page()).toBe(1);
  });

  it('shows the empty state when there are no rows', () => {
    const fixture = createTable([]);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No rows to show.');
  });

  it('computes the total row count for the "Showing X–Y of Z" pager label', () => {
    const fixture = createTable(rows);
    const table = fixture.componentInstance;

    expect(table['totalCount']()).toBe(3);
  });

  it('changing the page size updates pageSize and resets to page 1', () => {
    const fixture = createTable(rows);
    const table = fixture.componentInstance;

    table['goToPage'](2);
    table['onPageSizeChange'](50);

    expect(table.pageSize()).toBe(50);
    expect(table.page()).toBe(1);
    expect(table['pageCount']()).toBe(1);
  });

  it('ignores an invalid page size', () => {
    const fixture = createTable(rows);
    const table = fixture.componentInstance;

    table['onPageSizeChange'](NaN);
    expect(table.pageSize()).toBe(2);
  });
});
