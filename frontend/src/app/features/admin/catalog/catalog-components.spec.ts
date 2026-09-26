import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/api/api-error';
import { AdminMenuApi } from '../../../core/api/admin.api';
import { CategoryResponse, ItemResponse } from '../../../core/api/models';
import { ToastService } from '../../../core/ui/toast.service';
import { CatalogModule } from './catalog-module';
import { CategoriesPage } from './categories/categories-page';
import { PriceEditor } from './items/price-editor';

// AOT test build: import the declaring module instead of re-declaring components.

const category = (id: number, name: string, displayOrder: number): CategoryResponse => ({
  id,
  name,
  displayOrder,
  active: true,
  itemCount: 2,
});

describe('CategoriesPage (reorder)', () => {
  let fixture: ComponentFixture<CategoriesPage>;
  const api = {
    categories: vi.fn(),
    reorderCategories: vi.fn(),
    setCategoryActive: vi.fn(),
  };
  const toasts = { success: vi.fn(), error: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.categories.mockReturnValue(
      of([category(1, 'Starters', 0), category(2, 'Mains', 1), category(3, 'Drinks', 2)]),
    );
    TestBed.configureTestingModule({
      imports: [CatalogModule],
      providers: [
        provideRouter([]),
        { provide: AdminMenuApi, useValue: api },
        { provide: ToastService, useValue: toasts },
      ],
    });
    fixture = TestBed.createComponent(CategoriesPage);
    await fixture.whenStable();
    fixture.detectChanges();
  });

  const names = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid^="category-"] p.truncate.font-semibold')).map(
      (el) => el.textContent?.trim(),
    );

  it('moves a category down with the button and saves the new order', async () => {
    api.reorderCategories.mockImplementation((ids: number[]) =>
      of(ids.map((id, i) => ({ ...category(id, ['', 'Starters', 'Mains', 'Drinks'][id], i) }))),
    );
    const down = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '[aria-label="Move Starters down"]',
    )!;
    down.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(api.reorderCategories).toHaveBeenCalledWith([2, 1, 3]);
    expect(names()).toEqual(['Mains', 'Starters', 'Drinks']);
  });

  it('reverts the order when saving fails', async () => {
    api.reorderCategories.mockReturnValue(throwError(() => new ApiError(500, 'INTERNAL_ERROR', 'boom')));
    await (fixture.componentInstance as unknown as { reorder(a: number, b: number): Promise<void> }).reorder(2, 0);
    fixture.detectChanges();
    expect(names()).toEqual(['Starters', 'Mains', 'Drinks']);
    expect(toasts.error).toHaveBeenCalled();
  });

  it('disables "up" on the first and "down" on the last row', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector<HTMLButtonElement>('[aria-label="Move Starters up"]')!.disabled).toBe(true);
    expect(el.querySelector<HTMLButtonElement>('[aria-label="Move Drinks down"]')!.disabled).toBe(true);
  });
});

describe('PriceEditor', () => {
  const item: ItemResponse = {
    id: 5,
    categoryId: 1,
    categoryName: 'Biryani',
    name: 'Veg Biryani',
    foodType: 'VEG',
    gstPercent: 5,
    available: true,
    active: true,
    displayOrder: 0,
    version: 1,
    hasVariants: true,
    displayPrice: 150,
    variants: [
      { id: 51, name: 'Half', price: 150, isDefault: true },
      { id: 52, name: 'Full', price: 260, isDefault: false },
    ],
    addons: [],
  };

  it('edits size prices and emits only on valid input', () => {
    TestBed.configureTestingModule({ imports: [CatalogModule], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(PriceEditor);
    fixture.componentRef.setInput('item', item);
    const saved = vi.fn();
    fixture.componentInstance.save.subscribe(saved);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    el.querySelector<HTMLButtonElement>('[data-testid="price-edit-5"]')!.click();
    fixture.detectChanges();
    const full = el.querySelector<HTMLInputElement>('[data-testid="price-input-5-v52"]')!;
    full.value = '0';
    full.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('[data-testid="price-save-5"]')!.click();
    fixture.detectChanges();
    expect(saved).not.toHaveBeenCalled();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('greater than ₹0');

    full.value = '275.50';
    full.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('[data-testid="price-save-5"]')!.click();
    expect(saved).toHaveBeenCalledWith({
      variants: [
        { id: 51, price: 150 },
        { id: 52, price: 275.5 },
      ],
    });
  });
});
