import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HqSharedLibModule } from '../../hq-shared-lib.module';
import { HqOption } from '../hq-option.model';
import { HqSearchableSelect } from './hq-searchable-select';

@Component({
  standalone: true,
  imports: [HqSharedLibModule, ReactiveFormsModule],
  template: `<hq-searchable-select [formControl]="ctrl" [items]="items" [allowOther]="false" placeholder="Pick one"></hq-searchable-select>`,
})
class ItemsHost {
  ctrl = new FormControl<string | null>(null);
  items: HqOption[] = [
    { value: 'IN', label: 'India (IN)' },
    { value: 'AE', label: 'United Arab Emirates (AE)' },
  ];
}

describe('HqSearchableSelect with items (a value apart from the text)', () => {
  let fixture: ComponentFixture<ItemsHost>;
  let host: ItemsHost;
  let el: HTMLElement;

  beforeAll(() => {
    if (!Element.prototype.scrollIntoView) Element.prototype.scrollIntoView = () => {};
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ItemsHost] });
    fixture = TestBed.createComponent(ItemsHost);
    host = fixture.componentInstance;
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => document.querySelectorAll('.cdk-overlay-container').forEach((n) => (n.innerHTML = '')));

  const control = () => el.querySelector<HTMLInputElement>('.hq-ss__control')!;
  const open = () => {
    control().dispatchEvent(new Event('focus'));
    fixture.detectChanges();
  };
  const options = () => Array.from(document.querySelectorAll('.hq-ss-panel__option')).map((n) => n.textContent?.trim());

  it('shows the label of the value the form holds', () => {
    host.ctrl.setValue('AE');
    fixture.detectChanges();
    expect(control().value).toBe('United Arab Emirates (AE)');
  });

  it('lists the labels and puts the chosen item\'s value, not its text, into the form', () => {
    open();
    expect(options()).toEqual(['India (IN)', 'United Arab Emirates (AE)']);

    (document.querySelectorAll('.hq-ss-panel__option')[1] as HTMLElement).dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    (document.querySelectorAll('.hq-ss-panel__option')[1] as HTMLElement).click();
    fixture.detectChanges();

    expect(host.ctrl.value).toBe('AE');
    expect(control().value).toBe('United Arab Emirates (AE)');
  });

  it('filters the list by what is typed, matching the label', () => {
    open();
    control().value = 'emir';
    control().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(options()).toEqual(['United Arab Emirates (AE)']);
  });

  it('clears the form value to an empty string', () => {
    host.ctrl.setValue('IN');
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('.hq-ss__clear')!.click();
    fixture.detectChanges();
    expect(host.ctrl.value).toBe('');
    expect(control().value).toBe('');
  });

  it('keeps showing a value that is not among the items instead of losing it', () => {
    host.ctrl.setValue('ZZ');
    fixture.detectChanges();
    expect(control().value).toBe('ZZ');
  });

  it('shows the new label when the items are replaced', () => {
    const direct = TestBed.createComponent(HqSearchableSelect);
    direct.componentRef.setInput('items', [{ value: 'IN', label: 'India (IN)' }]);
    direct.componentInstance.writeValue('IN');
    direct.detectChanges();
    expect(direct.nativeElement.querySelector('.hq-ss__control').value).toBe('India (IN)');

    direct.componentRef.setInput('items', [{ value: 'IN', label: 'Bharat' }]);
    direct.detectChanges();
    expect(direct.nativeElement.querySelector('.hq-ss__control').value).toBe('Bharat');
  });
});

@Component({
  standalone: true,
  imports: [HqSharedLibModule, FormsModule],
  template: `
    <hq-searchable-select [(ngModel)]="picked" (ngModelChange)="changes.push($event)" [allowOther]="false" placeholder="Any status">
      @for (o of statuses; track o.value) {
        <hq-ss-option [value]="o.value" [label]="o.label"></hq-ss-option>
      }
    </hq-searchable-select>`,
})
class ProjectedHost {
  picked = '';
  changes: string[] = [];
  statuses = [{ value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }];
}

describe('HqSearchableSelect with hq-ss-option children (written like native options)', () => {
  let fixture: ComponentFixture<ProjectedHost>;
  let el: HTMLElement;

  beforeAll(() => {
    if (!Element.prototype.scrollIntoView) Element.prototype.scrollIntoView = () => {};
  });

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [ProjectedHost] });
    fixture = TestBed.createComponent(ProjectedHost);
    el = fixture.nativeElement;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  afterEach(() => document.querySelectorAll('.cdk-overlay-container').forEach((n) => (n.innerHTML = '')));

  const control = () => el.querySelector<HTMLInputElement>('.hq-ss__control')!;

  it('lists the labels of the children and gives the form the value of the picked child', async () => {
    control().dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    const rows = Array.from(document.querySelectorAll('.hq-ss-panel__option'));
    expect(rows.map((r) => r.textContent?.trim())).toEqual(['Active', 'Inactive']);

    (rows[1] as HTMLElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.picked).toBe('false');
    expect(control().value).toBe('Inactive');
  });

  it('shows the placeholder when no value is set', () => {
    expect(control().placeholder).toBe('Any status');
    expect(control().value).toBe('');
  });

  it('shows the label of a value the model starts with', async () => {
    const started = TestBed.createComponent(ProjectedHost);
    started.componentInstance.picked = 'true';
    started.detectChanges();
    await started.whenStable();
    started.detectChanges();
    expect((started.nativeElement as HTMLElement).querySelector<HTMLInputElement>('.hq-ss__control')!.value).toBe('Active');
  });
});
