import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminKioskApi, AdminMenuApi } from '../../../core/api/admin.api';
import { CategoryResponse, ItemResponse, KioskUpsellView } from '../../../core/api/models';
import { SheetService } from '../../../core/ui/sheet.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { errorMessage } from '../../../shared/util/form-errors';
import { UpsellDialog, UpsellDialogData } from './upsell-dialog';
import { describeUpsell, nameLookup } from './upsell-logic';

const ITEM_PAGE_SIZE = 100;

/** Owner/manager: rules that make kiosks suggest an extra item. */
@Component({
  selector: 'app-admin-kiosk-upsells-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './upsells-page.html',
})
export class UpsellsPage {
  private readonly kioskApi = inject(AdminKioskApi);
  private readonly menuApi = inject(AdminMenuApi);
  private readonly sheets = inject(SheetService);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  protected readonly rules = signal<KioskUpsellView[]>([]);
  protected readonly items = signal<ItemResponse[]>([]);
  protected readonly categories = signal<CategoryResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<unknown>(null);
  protected readonly deleting = signal<number | null>(null);

  private readonly names = computed(() => nameLookup(this.items(), this.categories()));
  protected readonly rows = computed(() =>
    this.rules().map((rule) => ({ rule, text: describeUpsell(rule, this.names()) })),
  );

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [rules, categories, items] = await Promise.all([
        firstValueFrom(this.kioskApi.upsells()),
        firstValueFrom(this.menuApi.categories()),
        this.loadAllItems(),
      ]);
      this.rules.set(rules);
      this.categories.set(categories);
      this.items.set(items);
      this.error.set(null);
    } catch (error) {
      this.error.set(error);
    } finally {
      this.loading.set(false);
    }
  }

  /** The items endpoint is paged (max 100 per page): read every page. */
  private async loadAllItems(): Promise<ItemResponse[]> {
    const first = await firstValueFrom(this.menuApi.items({ page: 0, size: ITEM_PAGE_SIZE }));
    const all = [...first.content];
    for (let page = 1; page < first.totalPages; page++) {
      const next = await firstValueFrom(this.menuApi.items({ page, size: ITEM_PAGE_SIZE }));
      all.push(...next.content);
    }
    return all;
  }

  protected open(rule: KioskUpsellView | null): void {
    const ref = this.sheets.open<KioskUpsellView, UpsellDialogData, UpsellDialog>(UpsellDialog, {
      data: {
        rule,
        items: this.items()
          .filter((i) => i.active || i.id === rule?.suggestedItemId || i.id === rule?.triggerItemId)
          .map((i) => ({ id: i.id, name: i.name, categoryName: i.categoryName })),
        categories: this.categories().map((c) => ({ id: c.id, name: c.name })),
      },
      maxWidth: '36rem',
    });
    ref.closed.subscribe((saved) => {
      if (!saved) return;
      this.toasts.success(rule ? 'Upsell updated.' : 'Upsell added.');
      void this.load();
    });
  }

  protected async remove(rule: KioskUpsellView, text: string): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Delete this upsell?',
      message: text,
      confirmLabel: 'Delete upsell',
    });
    if (!confirmed) return;
    this.deleting.set(rule.id);
    try {
      await firstValueFrom(this.kioskApi.deleteUpsell(rule.id));
      this.rules.update((list) => list.filter((r) => r.id !== rule.id));
      this.toasts.success('Upsell deleted.');
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not delete the upsell.'));
    } finally {
      this.deleting.set(null);
    }
  }
}
