import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideRouter } from '@angular/router';
import { GuestModule } from '../guest-module';
import { biryani, dosa } from '../data/test-fixtures';
import { ItemSheet, ItemSheetData } from './item-sheet';
import { MenuItem } from '../../../core/api/models';
import { CartLine } from '../data/cart.models';

describe('ItemSheet', () => {
  let fixture: ComponentFixture<ItemSheet>;
  let ref: { close: ReturnType<typeof vi.fn> };

  function create(item: MenuItem, line?: CartLine): HTMLElement {
    ref = { close: vi.fn() };
    TestBed.configureTestingModule({
      // AOT test build: the component keeps the compilation scope of the module that declares it, so import
      // that module instead of re-declaring the component (re-declaring would reset its scope).
      imports: [GuestModule],
      providers: [
        provideRouter([]),
        { provide: DialogRef, useValue: ref },
        { provide: DIALOG_DATA, useValue: { item, line } satisfies ItemSheetData },
      ],
    });
    fixture = TestBed.createComponent(ItemSheet);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const total = (el: HTMLElement) =>
    el.querySelector('[data-testid="item-sheet-total"]')!.textContent!.trim();
  const confirm = (el: HTMLElement) =>
    el.querySelector<HTMLButtonElement>('[data-testid="item-sheet-confirm"]')!;

  function click(el: Element): void {
    (el as HTMLElement).click();
    fixture.detectChanges();
  }

  it('preselects the default variant and shows its price', () => {
    const el = create(biryani);
    const radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
    expect(radios.length).toBe(2);
    expect(radios[0].checked).toBe(true);
    expect(total(el)).toBe('₹180.00');
  });

  it('updates the live price for variant, addons and quantity', () => {
    const el = create(biryani);
    click(el.querySelectorAll('input[type="radio"]')[1]); // Full 320
    expect(total(el)).toBe('₹320.00');

    const addons = el.querySelectorAll<HTMLInputElement>('input[type="checkbox"]');
    click(addons[0]); // +30
    click(addons[1]); // +25.50
    expect(total(el)).toBe('₹375.50');

    click(el.querySelector('[aria-label="Increase Chicken Biryani"]')!);
    expect(total(el)).toBe('₹751.00');

    click(addons[0]); // remove raita
    expect(total(el)).toBe('₹691.00');
  });

  it('closes with the selection', () => {
    const el = create(biryani);
    click(el.querySelectorAll('input[type="radio"]')[1]);
    click(el.querySelectorAll('input[type="checkbox"]')[1]);
    const notes = el.querySelector('textarea')!;
    notes.value = 'extra spicy';
    notes.dispatchEvent(new Event('input'));
    click(confirm(el));
    expect(ref.close).toHaveBeenCalledWith({
      variantId: 22,
      addonIds: [32],
      notes: 'extra spicy',
      quantity: 1,
    });
  });

  it('pre-fills an existing line when editing', () => {
    const line = {
      key: 'k',
      itemId: 2,
      variantId: 22,
      addonIds: [31],
      notes: 'no onion',
      quantity: 3,
    } as CartLine;
    const el = create(biryani, line);
    expect(el.querySelectorAll<HTMLInputElement>('input[type="radio"]')[1].checked).toBe(true);
    expect(el.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')[0].checked).toBe(true);
    expect(total(el)).toBe('₹1,050.00');
    expect(confirm(el).textContent).toContain('Update');
  });

  it('uses the base price for items without variants', () => {
    const el = create(dosa);
    expect(el.querySelectorAll('input[type="radio"]').length).toBe(0);
    expect(total(el)).toBe('₹120.00');
  });

  it('cannot add unavailable items', () => {
    const el = create({ ...dosa, available: false });
    expect(confirm(el).disabled).toBe(true);
    confirm(el).click();
    expect(ref.close).not.toHaveBeenCalled();
  });
});
