import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MenuItem } from '../../../core/api/models';
import { SheetService } from '../../../core/ui/sheet.service';
import { CartLine, ItemSelection } from '../data/cart.models';
import { CartStore } from '../data/cart.store';
import { ItemSheet, ItemSheetData } from './item-sheet';

/** Opens the item sheet and applies the result to the cart (add, or replace when editing a line). */
@Injectable({ providedIn: 'root' })
export class ItemSheetLauncher {
  private readonly sheets = inject(SheetService);
  private readonly cart = inject(CartStore);

  async open(item: MenuItem, line?: CartLine): Promise<ItemSelection | undefined> {
    const ref = this.sheets.open<ItemSelection, ItemSheetData, ItemSheet>(ItemSheet, {
      data: { item, line },
      ariaLabel: item.name,
    });
    const selection = await firstValueFrom(ref.closed);
    if (!selection) return undefined;
    if (line) this.cart.replace(line.key, item, selection);
    else this.cart.add(item, selection);
    return selection;
  }
}
