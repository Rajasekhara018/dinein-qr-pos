import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  untracked,
  viewChild,
  viewChildren,
} from '@angular/core';
import { MenuCategory, MenuItem } from '../../../core/api/models';
import { BreakpointService } from '../../../core/ui/breakpoint.service';
import { defaultVariantId, isCustomizable, lineKey } from '../data/cart.models';
import { CartStore } from '../data/cart.store';
import { MenuStore } from '../data/menu.store';
import { ItemSheetLauncher } from '../item-sheet/item-sheet-launcher';

/** Height of the sticky search + chips bar, used as the scroll-spy/scroll offset. */
const STICKY_OFFSET_PX = 132;

@Component({
  selector: 'app-menu-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './menu-page.html',
})
export class MenuPage {
  protected readonly menu = inject(MenuStore);
  protected readonly cart = inject(CartStore);
  private readonly sheet = inject(ItemSheetLauncher);
  private readonly breakpoints = inject(BreakpointService);

  protected readonly query = signal('');
  protected readonly activeCategoryId = signal<number | null>(null);

  private readonly sections = viewChildren<ElementRef<HTMLElement>>('section');
  private readonly chipScroller = viewChild<ElementRef<HTMLElement>>('chips');
  private suppressSpyUntil = 0;

  /** Categories filtered by the search query (empty categories hidden). */
  protected readonly visibleCategories = computed<MenuCategory[]>(() => {
    const q = this.query().trim().toLowerCase();
    const categories = this.menu.categories().filter((c) => c.items.length > 0);
    if (!q) return categories;
    return categories
      .map((c) => ({
        ...c,
        items: c.items.filter(
          (i) =>
            i.name.toLowerCase().includes(q) || (i.description ?? '').toLowerCase().includes(q),
        ),
      }))
      .filter((c) => c.items.length > 0);
  });

  protected readonly resultCount = computed(() =>
    this.visibleCategories().reduce((n, c) => n + c.items.length, 0),
  );

  protected readonly skeletons = Array.from({ length: 6 }, (_, i) => i);

  constructor() {
    this.setUpScrollSpy();

    // Keep the active chip visible inside the horizontally scrolling chip bar.
    effect(() => {
      const id = this.activeCategoryId();
      const scroller = this.chipScroller()?.nativeElement;
      if (id == null || !scroller) return;
      untracked(() => {
        const chip = scroller.querySelector<HTMLElement>(`[data-chip="${id}"]`);
        if (!chip) return;
        const left = chip.offsetLeft - scroller.clientWidth / 2 + chip.clientWidth / 2;
        scroller.scrollTo?.({ left: Math.max(0, left), behavior: this.scrollBehavior() });
      });
    });
  }

  protected quantityOf(item: MenuItem): number {
    if (isCustomizable(item)) return this.cart.quantityByItem().get(item.id) ?? 0;
    return this.cart.find(this.simpleKey(item))?.quantity ?? 0;
  }

  protected onAdd(item: MenuItem): void {
    if (!item.available) return;
    if (isCustomizable(item)) {
      void this.sheet.open(item);
      return;
    }
    this.cart.add(item, {
      variantId: defaultVariantId(item),
      addonIds: [],
      notes: '',
      quantity: 1,
    });
  }

  protected onQuantity(item: MenuItem, quantity: number): void {
    if (isCustomizable(item)) {
      // "+" opens the sheet (incrementOnly); only "−" lands here.
      if (quantity < this.quantityOf(item)) this.cart.decrementLatest(item.id);
      return;
    }
    const key = this.simpleKey(item);
    if (this.cart.find(key)) this.cart.setQuantity(key, quantity);
    else if (quantity > 0) this.onAdd(item);
  }

  protected scrollToCategory(id: number): void {
    const target = this.sections().find(
      (s) => s.nativeElement.dataset['categoryId'] === String(id),
    );
    if (!target) return;
    this.activeCategoryId.set(id);
    this.suppressSpyUntil = Date.now() + 900;
    target.nativeElement.scrollIntoView({ behavior: this.scrollBehavior(), block: 'start' });
    target.nativeElement.focus({ preventScroll: true });
  }

  protected onSearch(value: string): void {
    this.query.set(value);
  }

  protected clearSearch(): void {
    this.query.set('');
  }

  protected retry(): void {
    void this.menu.load();
  }

  private simpleKey(item: MenuItem): string {
    return lineKey(item.id, defaultVariantId(item), [], '');
  }

  private scrollBehavior(): ScrollBehavior {
    return this.breakpoints.prefersReducedMotion() ? 'auto' : 'smooth';
  }

  /** Scroll-spy: the first category section intersecting the band below the sticky bar is "active". */
  private setUpScrollSpy(): void {
    effect((onCleanup) => {
      const sections = this.sections();
      if (!sections.length || typeof IntersectionObserver === 'undefined') return;
      const visible = new Set<number>();
      const observer = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            const id = Number((entry.target as HTMLElement).dataset['categoryId']);
            if (entry.isIntersecting) visible.add(id);
            else visible.delete(id);
          }
          if (Date.now() < this.suppressSpyUntil) return;
          const first = untracked(this.visibleCategories).find((c) => visible.has(c.id));
          if (first) this.activeCategoryId.set(first.id);
        },
        { rootMargin: `-${STICKY_OFFSET_PX}px 0px -55% 0px`, threshold: 0 },
      );
      for (const section of sections) observer.observe(section.nativeElement);
      untracked(() => {
        if (this.activeCategoryId() == null)
          this.activeCategoryId.set(this.visibleCategories()[0]?.id ?? null);
      });
      onCleanup(() => observer.disconnect());
    });
  }
}
