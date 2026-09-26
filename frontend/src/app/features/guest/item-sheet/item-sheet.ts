import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MenuItem } from '../../../core/api/models';
import { formatInr, toPaise } from '../../../core/util/money';
import {
  CartLine,
  defaultVariantId,
  ItemSelection,
  MAX_LINE_NOTES,
  MAX_LINE_QUANTITY,
} from '../data/cart.models';

export interface ItemSheetData {
  item: MenuItem;
  /** Editing an existing cart line (pre-fills the selection). */
  line?: CartLine;
}

/** Variant (radio) / add-ons (checkboxes) / notes / quantity picker with a live price. Closes with an ItemSelection. */
@Component({
  selector: 'app-item-sheet',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './item-sheet.html',
})
export class ItemSheet {
  private readonly ref = inject<DialogRef<ItemSelection, ItemSheet>>(DialogRef);
  private readonly data = inject<ItemSheetData>(DIALOG_DATA);

  protected readonly item = this.data.item;
  protected readonly editing = !!this.data.line;
  protected readonly maxNotes = MAX_LINE_NOTES;
  protected readonly maxQuantity = MAX_LINE_QUANTITY;

  protected readonly variantId = signal<number | null>(
    this.data.line?.variantId ?? defaultVariantId(this.item),
  );
  protected readonly addonIds = signal<ReadonlySet<number>>(
    new Set(this.data.line?.addonIds ?? []),
  );
  protected readonly notes = signal(this.data.line?.notes ?? '');
  protected readonly quantity = signal(this.data.line?.quantity ?? 1);

  protected readonly needsVariant = computed(
    () => this.item.variants.length > 0 && this.variantId() == null,
  );

  /** Unit price in paise: variant (or base) + selected addons. */
  protected readonly unitPaise = computed(() => {
    const variant = this.item.variants.find((v) => v.id === this.variantId());
    const base = toPaise(variant?.price ?? this.item.basePrice ?? this.item.displayPrice ?? 0);
    const addons = this.item.addons
      .filter((a) => this.addonIds().has(a.id))
      .reduce((sum, a) => sum + toPaise(a.price), 0);
    return base + addons;
  });

  protected readonly totalPaise = computed(() => this.unitPaise() * this.quantity());
  protected readonly totalLabel = computed(() => formatInr(this.totalPaise() / 100));

  protected readonly titleId = `item-sheet-${this.item.id}`;

  protected toggleAddon(id: number, checked: boolean): void {
    this.addonIds.update((current) => {
      const next = new Set(current);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  protected onNotes(value: string): void {
    this.notes.set(value.slice(0, MAX_LINE_NOTES));
  }

  protected close(): void {
    this.ref.close();
  }

  protected confirm(): void {
    if (this.needsVariant() || !this.item.available) return;
    this.ref.close({
      variantId: this.variantId(),
      addonIds: [...this.addonIds()],
      notes: this.notes().trim(),
      quantity: this.quantity(),
    });
  }
}
