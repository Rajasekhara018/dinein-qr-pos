import { TestBed } from '@angular/core/testing';
import { HqCommonTable, HqCommonTableColumn } from './hq-common-table';

type Row = { name: string; qty: number };

describe('HqCommonTable sorting and highlight', () => {
  const columns: HqCommonTableColumn<Row>[] = [
    { key: 'name', label: 'Name', sortable: true, tooltip: true },
    { key: 'qty', label: 'Qty', sortable: true },
    { key: 'other', label: 'Other' },
  ];
  const rows: Row[] = [
    { name: 'b', qty: 10 },
    { name: 'a', qty: 2 },
    { name: 'c', qty: 1 },
  ];

  function create(): HqCommonTable<Row> {
    const table = TestBed.runInInjectionContext(() => new HqCommonTable<Row>());
    table.columns = columns;
    table.rows = rows;
    return table;
  }

  it('keeps the given order until a header is clicked', () => {
    expect(create().displayRows.map(r => r.name)).toEqual(['b', 'a', 'c']);
  });

  it('cycles ascending, descending, none and emits each change', () => {
    const table = create();
    const emitted: unknown[] = [];
    table.sortChange.subscribe(v => emitted.push(v));

    table.toggleSort(columns[0]);
    expect(table.displayRows.map(r => r.name)).toEqual(['a', 'b', 'c']);
    table.toggleSort(columns[0]);
    expect(table.displayRows.map(r => r.name)).toEqual(['c', 'b', 'a']);
    table.toggleSort(columns[0]);
    expect(table.displayRows.map(r => r.name)).toEqual(['b', 'a', 'c']);
    expect(emitted).toEqual([{ key: 'name', direction: 'asc' }, { key: 'name', direction: 'desc' }, null]);
  });

  it('sorts numbers naturally, not as text', () => {
    const table = create();
    table.toggleSort(columns[1]);
    expect(table.displayRows.map(r => r.qty)).toEqual([1, 2, 10]);
  });

  it('ignores clicks on a column that is not sortable', () => {
    const table = create();
    table.toggleSort(columns[2]);
    expect(table.sort).toBeNull();
    expect(table.ariaSort(columns[2])).toBeNull();
  });

  it('reports aria-sort per header', () => {
    const table = create();
    table.toggleSort(columns[0]);
    expect(table.ariaSort(columns[0])).toBe('ascending');
    expect(table.ariaSort(columns[1])).toBe('none');
    table.toggleSort(columns[0]);
    expect(table.ariaSort(columns[0])).toBe('descending');
  });

  it('highlights only the requested row index', () => {
    const table = create();
    expect(table.isHighlighted(0)).toBe(false);
    table.highlightIndex = 1;
    expect(table.isHighlighted(1)).toBe(true);
    expect(table.isHighlighted(0)).toBe(false);
  });
});
