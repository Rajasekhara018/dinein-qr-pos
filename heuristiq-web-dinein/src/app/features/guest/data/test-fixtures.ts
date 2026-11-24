import { MenuItem, MenuResponse } from '../../../core/api/models';

/** Test fixtures shared by guest specs. */
export function menuItem(overrides: Partial<MenuItem> = {}): MenuItem {
  return {
    id: 1,
    name: 'Masala Dosa',
    description: 'Crispy rice crepe',
    foodType: 'VEG',
    basePrice: 120,
    displayPrice: 120,
    gstPercent: 5,
    available: true,
    variants: [],
    addons: [],
    ...overrides,
  };
}

export const biryani: MenuItem = menuItem({
  id: 2,
  name: 'Chicken Biryani',
  foodType: 'NON_VEG',
  basePrice: undefined,
  displayPrice: 180,
  variants: [
    { id: 21, name: 'Half', price: 180, isDefault: true },
    { id: 22, name: 'Full', price: 320, isDefault: false },
  ],
  addons: [
    { id: 31, name: 'Extra raita', price: 30 },
    { id: 32, name: 'Boiled egg', price: 25.5 },
  ],
});

export const dosa: MenuItem = menuItem();

export function menu(items: MenuItem[] = [dosa, biryani], pricesIncludeGst = false): MenuResponse {
  return {
    version: 'v1',
    pricesIncludeGst,
    categories: [{ id: 10, name: 'Mains', items }],
  };
}
