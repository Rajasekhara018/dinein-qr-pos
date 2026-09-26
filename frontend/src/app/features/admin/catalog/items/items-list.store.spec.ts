import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../../core/api/api-error';
import { AdminMenuApi } from '../../../../core/api/admin.api';
import { ItemResponse, PageResponse, PriceRequest } from '../../../../core/api/models';
import { ToastOptions, ToastService } from '../../../../core/ui/toast.service';
import { ItemsListStore } from './items-list.store';
import { applyPriceChange, diffPriceChange, parsePrice, undoChangeFor } from '../data/inline-price';

const dosa: ItemResponse = {
  id: 1,
  categoryId: 10,
  categoryName: 'Starters',
  name: 'Masala Dosa',
  basePrice: 120,
  displayPrice: 120,
  foodType: 'VEG',
  gstPercent: 5,
  available: true,
  active: true,
  displayOrder: 0,
  version: 3,
  hasVariants: false,
  variants: [],
  addons: [],
};

const biryani: ItemResponse = {
  ...dosa,
  id: 2,
  name: 'Chicken Biryani',
  basePrice: undefined,
  displayPrice: 180,
  hasVariants: true,
  version: 9,
  variants: [
    { id: 21, name: 'Half', price: 180, isDefault: true },
    { id: 22, name: 'Full', price: 320, isDefault: false },
  ],
};

const page = (items: ItemResponse[]): PageResponse<ItemResponse> => ({
  content: items,
  page: 0,
  size: 50,
  totalElements: items.length,
  totalPages: 1,
});

describe('inline price helpers', () => {
  it('diffs only changed values (in paise)', () => {
    expect(diffPriceChange(dosa, { basePrice: 120.0 })).toBeNull();
    expect(diffPriceChange(dosa, { basePrice: 130 })).toEqual({ basePrice: 130 });
    expect(
      diffPriceChange(biryani, {
        variants: [
          { id: 21, price: 180 },
          { id: 22, price: 340 },
        ],
      }),
    ).toEqual({ variants: [{ id: 22, price: 340 }] });
  });

  it('builds the undo change and applies optimistic prices', () => {
    const change = { variants: [{ id: 21, price: 200 }] };
    expect(undoChangeFor(biryani, change)).toEqual({ variants: [{ id: 21, price: 180 }] });
    const applied = applyPriceChange(biryani, change);
    expect(applied.variants[0].price).toBe(200);
    expect(applied.displayPrice).toBe(200);
  });

  it('parses user input', () => {
    expect(parsePrice('₹ 1,250.50')).toBe(1250.5);
    expect(parsePrice('0')).toBeNull();
    expect(parsePrice('12.345')).toBeNull();
    expect(parsePrice('abc')).toBeNull();
  });
});

describe('ItemsListStore – inline price edit with undo', () => {
  let api: {
    items: ReturnType<typeof vi.fn>;
    item: ReturnType<typeof vi.fn>;
    updateItemPrice: ReturnType<typeof vi.fn<(id: number, body: PriceRequest) => Observable<ItemResponse>>>;
    setItemAvailability: ReturnType<typeof vi.fn>;
  };
  let toasts: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn>; warning: ReturnType<typeof vi.fn>; info: ReturnType<typeof vi.fn> };
  let store: ItemsListStore;

  beforeEach(async () => {
    api = {
      items: vi.fn(() => of(page([dosa, biryani]))),
      item: vi.fn(),
      updateItemPrice: vi.fn(),
      setItemAvailability: vi.fn(),
    };
    toasts = { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        ItemsListStore,
        { provide: AdminMenuApi, useValue: api },
        { provide: ToastService, useValue: toasts },
      ],
    });
    store = TestBed.inject(ItemsListStore);
    await store.load();
  });

  it('updates optimistically, PATCHes with the version, and offers Undo that restores with the NEW version', async () => {
    let resolve!: (item: ItemResponse) => void;
    api.updateItemPrice.mockReturnValueOnce(
      new Observable<ItemResponse>((sub) => {
        resolve = (item) => {
          sub.next(item);
          sub.complete();
        };
      }),
    );
    const pending = store.updatePrice(1, { basePrice: 150 });
    // Optimistic value before the server answers.
    expect(store.find(1)?.basePrice).toBe(150);
    expect(store.isBusy(1)).toBe(true);
    expect(api.updateItemPrice).toHaveBeenCalledWith(1, { basePrice: 150, version: 3 });

    resolve({ ...dosa, basePrice: 150, displayPrice: 150, version: 4 });
    expect(await pending).toBe(true);
    expect(store.find(1)?.version).toBe(4);

    const [message, options] = toasts.success.mock.calls[0] as [string, ToastOptions];
    expect(message).toContain('₹150.00');
    expect(options.action?.label).toBe('Undo');

    api.updateItemPrice.mockReturnValueOnce(of({ ...dosa, basePrice: 120, version: 5 }));
    options.action!.run();
    await vi.waitFor(() => expect(store.find(1)?.version).toBe(5));
    expect(api.updateItemPrice).toHaveBeenLastCalledWith(1, { basePrice: 120, version: 4 });
    expect(store.find(1)?.basePrice).toBe(120);
  });

  it('edits size prices and undoes only the changed size', async () => {
    api.updateItemPrice.mockReturnValueOnce(
      of({ ...biryani, version: 10, variants: [biryani.variants[0], { ...biryani.variants[1], price: 350 }] }),
    );
    await store.updatePrice(2, { variants: [{ id: 21, price: 180 }, { id: 22, price: 350 }] });
    expect(api.updateItemPrice).toHaveBeenCalledWith(2, { variants: [{ id: 22, price: 350 }], version: 9 });

    const options = toasts.success.mock.calls[0][1] as ToastOptions;
    api.updateItemPrice.mockReturnValueOnce(of({ ...biryani, version: 11 }));
    options.action!.run();
    await vi.waitFor(() => expect(store.find(2)?.version).toBe(11));
    expect(api.updateItemPrice).toHaveBeenLastCalledWith(2, { variants: [{ id: 22, price: 320 }], version: 10 });
  });

  it('does not call the API when nothing changed', async () => {
    expect(await store.updatePrice(1, { basePrice: 120 })).toBe(true);
    expect(api.updateItemPrice).not.toHaveBeenCalled();
  });

  it('reloads the row on 409 CONCURRENT_MODIFICATION', async () => {
    api.updateItemPrice.mockReturnValueOnce(
      throwError(() => new ApiError(409, 'CONCURRENT_MODIFICATION', 'Changed elsewhere')),
    );
    api.item.mockReturnValueOnce(of({ ...dosa, basePrice: 135, version: 8 }));
    expect(await store.updatePrice(1, { basePrice: 150 })).toBe(false);
    expect(api.item).toHaveBeenCalledWith(1);
    expect(store.find(1)).toMatchObject({ basePrice: 135, version: 8 });
    expect(toasts.warning).toHaveBeenCalled();
  });

  it('reverts the optimistic value on other errors', async () => {
    api.updateItemPrice.mockReturnValueOnce(
      throwError(() => new ApiError(400, 'VALIDATION_FAILED', 'Invalid price')),
    );
    expect(await store.updatePrice(1, { basePrice: 150 })).toBe(false);
    expect(store.find(1)?.basePrice).toBe(120);
    expect(toasts.error).toHaveBeenCalledWith('Invalid price', expect.anything());
  });

  it('reverts availability when the toggle fails', async () => {
    api.setItemAvailability.mockReturnValueOnce(throwError(() => new ApiError(500, 'INTERNAL_ERROR', 'x')));
    await store.setAvailability(dosa, false);
    expect(store.find(1)?.available).toBe(true);
  });
});
