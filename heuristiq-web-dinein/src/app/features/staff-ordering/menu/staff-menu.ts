import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MenuCategory, MenuItem } from '../../../core/api/models';
import { isCustomizable, startingPrice } from '../../guest/data/cart.models';

export interface MenuSection {
  id: number;
  name: string;
  items: MenuItem[];
}

/** Case-insensitive match on the item's name and description. */
export function matchesQuery(item: MenuItem, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return (
    item.name.toLowerCase().includes(q) || (item.description ?? '').toLowerCase().includes(q)
  );
}

/** Categories filtered by the chosen category and the search text (empty sections dropped). */
export function filterMenu(
  categories: readonly MenuCategory[],
  categoryId: number | null,
  query: string,
): MenuSection[] {
  return categories
    .filter((c) => categoryId == null || c.id === categoryId)
    .map((c) => ({ id: c.id, name: c.name, items: c.items.filter((i) => matchesQuery(i, query)) }))
    .filter((c) => c.items.length > 0);
}

/**
 * The waiter menu: search, category chips and compact item rows. Unavailable items stay visible (greyed, "Not
 * available") so the waiter can tell the guest, but cannot be added.
 */
@Component({
  selector: 'app-staff-menu',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-w-0' },
  templateUrl: './staff-menu.html',
})
export class StaffMenu {
  readonly categories = input.required<readonly MenuCategory[]>();
  /** itemId → quantity already in the cart. */
  readonly quantities = input<ReadonlyMap<number, number>>(new Map());

  readonly add = output<MenuItem>();

  protected readonly query = signal('');
  protected readonly categoryId = signal<number | null>(null);

  protected readonly sections = computed(() =>
    filterMenu(this.categories(), this.categoryId(), this.query()),
  );

  protected readonly customizable = isCustomizable;
  protected readonly startingPrice = startingPrice;

  protected onSearch(value: string): void {
    this.query.set(value);
  }

  protected selectCategory(id: number | null): void {
    this.categoryId.set(id);
  }
}
