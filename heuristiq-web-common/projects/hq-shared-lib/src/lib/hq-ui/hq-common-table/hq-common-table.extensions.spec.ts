import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqCommonTableColumn } from './hq-common-table';

type Row = { id: string; name: string };

const COLUMNS: HqCommonTableColumn<Row>[] = [
  { key: 'pick', label: '', className: 'col-pick' },
  { key: 'name', label: 'Name', className: 'col-name wide' },
];

const ROWS: Row[] = [{ id: '1', name: 'Ana' }, { id: '2', name: 'Bob' }];

/** The backward-compatible extras: header templates, row classes, per-column classes and keyboard rows. */
@Component({
  imports: [HqSharedLibModule],
  template: `
    <hq-common-table
      [columns]="columns" [rows]="rows" [showActionsColumn]="false" [clickableRows]="clickable" [keyboardRows]="keyboard"
      [rowClass]="rowClass" (rowClick)="clicked.push($event)">
      <ng-template hqHead="pick" let-col><button class="head-check" type="button">all {{ col.key }}</button></ng-template>
      <ng-template hqCell="pick" let-row><span class="cell-check">{{ row.id }}</span></ng-template>
    </hq-common-table>
  `,
})
class Host {
  columns = COLUMNS;
  rows = ROWS;
  clickable = true;
  keyboard = true;
  rowClass: ((row: Row) => string | string[] | null) | null = (row) => (row.id === '2' ? ['row-on', 'extra'] : null);
  clicked: Row[] = [];
}

describe('HqCommonTable extensions', () => {
  let fixture: ComponentFixture<Host>;
  let host: Host;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Host] });
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  const rows = () => Array.from(el.querySelectorAll<HTMLElement>('tbody tr'));

  it('renders a projected header template for its column and the plain label for the others', () => {
    const heads = Array.from(el.querySelectorAll('thead th'));
    expect(heads[0].querySelector('.head-check')?.textContent).toBe('all pick');
    expect(heads[1].textContent?.trim()).toBe('Name');
  });

  it('gives each column its own classes on the header and the cells', () => {
    const heads = Array.from(el.querySelectorAll('thead th'));
    expect(heads[0].classList).toContain('col-pick');
    expect(heads[1].classList).toContain('col-name');
    expect(heads[1].classList).toContain('wide');
    expect(rows()[0].querySelectorAll('td')[1].classList).toContain('col-name');
  });

  it('adds the classes returned by rowClass to a row, and none when it returns null or is not set', () => {
    expect(rows()[0].classList).not.toContain('row-on');
    expect(rows()[1].classList).toContain('row-on');
    expect(rows()[1].classList).toContain('extra');
    host.rowClass = null;
    fixture.detectChanges();
    expect(rows()[1].classList).not.toContain('row-on');
  });

  it('makes clickable rows focusable and lets Enter act like a click when keyboardRows is on', () => {
    expect(rows()[0].getAttribute('tabindex')).toBe('0');
    rows()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(host.clicked).toEqual([ROWS[0]]);
  });

  it('leaves rows out of the tab order and ignores Enter by default, or when the rows are not clickable', () => {
    host.keyboard = false;
    fixture.detectChanges();
    expect(rows()[0].getAttribute('tabindex')).toBeNull();
    rows()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(host.clicked).toEqual([]);

    host.keyboard = true;
    host.clickable = false;
    fixture.detectChanges();
    expect(rows()[0].getAttribute('tabindex')).toBeNull();
    rows()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(host.clicked).toEqual([]);
  });
});
