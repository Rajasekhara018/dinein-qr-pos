import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqCommonTableColumn } from './hq-common-table';

type Place = { name: string; type: string; active: boolean };

const COLUMNS: HqCommonTableColumn<Place>[] = [
  { key: 'name', label: 'Name' },
  { key: 'type', label: 'Type' },
  { key: 'active', label: 'Status' },
];

const ROWS: Place[] = [
  { name: 'Pune', type: 'CITY', active: true },
  { name: 'Goa', type: 'STATE', active: false },
];

@Component({
  imports: [HqSharedLibModule],
  template: `
    <hq-common-table [columns]="columns" [rows]="rows" [isRowExpanded]="isOpen" [showActionsColumn]="false">
      <ng-template hqCell="type" let-row><span class="badge">{{ row.type }}!</span></ng-template>
      <ng-template hqCell="active" let-row><b class="state">{{ row.active ? 'On' : 'Off' }}</b></ng-template>
      <ng-template #rowDetail let-row><div class="detail">Details of {{ row.name }}</div></ng-template>
    </hq-common-table>
  `,
})
class HostWithCells {
  columns = COLUMNS;
  rows = ROWS;
  open = new Set<string>();
  isOpen = (row: Place): boolean => this.open.has(row.name);
}

@Component({
  imports: [HqSharedLibModule],
  template: `<hq-common-table [columns]="columns" [rows]="rows" [showActionsColumn]="false"></hq-common-table>`,
})
class HostPlain {
  columns = COLUMNS;
  rows = ROWS;
}

describe('HqCommonTable cell templates and expandable rows', () => {
  function create<T>(type: new () => T): ComponentFixture<T> {
    TestBed.configureTestingModule({ imports: [type as never] });
    const fixture = TestBed.createComponent(type);
    fixture.detectChanges();
    return fixture;
  }

  it('draws a column with a hqCell template from the template, and leaves other columns as text', () => {
    const el: HTMLElement = create(HostWithCells).nativeElement;
    const firstRow = el.querySelectorAll('tbody tr')[0];
    expect(firstRow.querySelector('td:nth-child(1)')?.textContent?.trim()).toBe('Pune');
    expect(firstRow.querySelector('td:nth-child(2) .badge')?.textContent).toBe('CITY!');
    expect(firstRow.querySelector('td:nth-child(3) .state')?.textContent).toBe('On');
    expect(el.querySelectorAll('tbody tr')[1].querySelector('.state')?.textContent).toBe('Off');
  });

  it('keeps rendering text cells when a screen supplies no templates', () => {
    const el: HTMLElement = create(HostPlain).nativeElement;
    const cells = Array.from(el.querySelectorAll('tbody tr:first-child td')).map(td => td.textContent?.trim());
    expect(cells).toEqual(['Pune', 'CITY', 'Yes']);
    expect(el.querySelector('.badge')).toBeNull();
  });

  it('shows a row detail strip only for the rows that are expanded, directly below that row', () => {
    const fixture = create(HostWithCells);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('tr.detail-row').length).toBe(0);

    fixture.componentInstance.open.add('Goa');
    fixture.componentRef.changeDetectorRef.detectChanges();

    const rows = Array.from(el.querySelectorAll('tbody tr'));
    expect(el.querySelectorAll('tr.detail-row').length).toBe(1);
    const detailIndex = rows.findIndex(r => r.classList.contains('detail-row'));
    expect(rows[detailIndex - 1].textContent).toContain('Goa');
    expect(rows[detailIndex].querySelector('.detail')?.textContent).toBe('Details of Goa');
    expect(rows[detailIndex].querySelector('td')?.getAttribute('colspan')).toBe('3');
  });

  it('never shows a detail strip when the screen supplies no rowDetail template', () => {
    const el: HTMLElement = create(HostPlain).nativeElement;
    expect(el.querySelectorAll('tr.detail-row').length).toBe(0);
  });
});
