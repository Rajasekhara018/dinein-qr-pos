import { LiveAnnouncer } from '@angular/cdk/a11y';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminMenuApi } from '../../../../core/api/admin.api';
import { CategoryResponse } from '../../../../core/api/models';
import { SheetService } from '../../../../core/ui/sheet.service';
import { ToastService } from '../../../../core/ui/toast.service';
import { errorMessage } from '../../../../shared/util/form-errors';
import { canMove, moveItem, orderChanged, sortByDisplayOrder } from '../data/reorder';
import { CategoryDialog, CategoryDialogData } from './category-dialog';

/** Categories: thumbnails, item counts, active toggle, drag-and-drop (and up/down) reordering, add/edit dialog. */
@Component({
  selector: 'app-admin-categories-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './categories-page.html',
})
export class CategoriesPage {
  private readonly api = inject(AdminMenuApi);
  private readonly sheets = inject(SheetService);
  private readonly toasts = inject(ToastService);
  private readonly announcer = inject(LiveAnnouncer);

  protected readonly categories = signal<CategoryResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<unknown>(null);
  protected readonly saving = signal(false);
  protected readonly busyIds = signal<ReadonlySet<number>>(new Set());

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.categories.set(sortByDisplayOrder(await firstValueFrom(this.api.categories())));
      this.error.set(null);
    } catch (error) {
      this.error.set(error);
    } finally {
      this.loading.set(false);
    }
  }

  protected onDrop(event: CdkDragDrop<CategoryResponse[]>): void {
    void this.reorder(event.previousIndex, event.currentIndex);
  }

  protected canMove(index: number, delta: number): boolean {
    return canMove(index, delta, this.categories().length);
  }

  protected move(index: number, delta: number): void {
    if (this.canMove(index, delta)) void this.reorder(index, index + delta);
  }

  /** Optimistic reorder; reverts if the server rejects it. */
  async reorder(from: number, to: number): Promise<void> {
    const before = this.categories();
    const after = moveItem(before, from, to);
    if (!orderChanged(before.map((c) => c.id), after.map((c) => c.id))) return;
    this.categories.set(after);
    const moved = after[to];
    void this.announcer.announce(`${moved.name} moved to position ${to + 1} of ${after.length}`);
    this.saving.set(true);
    try {
      const saved = await firstValueFrom(this.api.reorderCategories(after.map((c) => c.id)));
      this.categories.set(sortByDisplayOrder(saved));
    } catch (error) {
      this.categories.set(before);
      this.toasts.error(errorMessage(error, 'Could not save the new order.'));
    } finally {
      this.saving.set(false);
    }
  }

  protected async setActive(category: CategoryResponse, active: boolean): Promise<void> {
    this.patch({ ...category, active });
    this.markBusy(category.id, true);
    try {
      this.patch(await firstValueFrom(this.api.setCategoryActive(category.id, active)));
      this.toasts.success(
        active ? `${category.name} is visible to guests.` : `${category.name} and its items are hidden from guests.`,
        { key: `category-${category.id}` },
      );
    } catch (error) {
      this.patch(category);
      this.toasts.error(errorMessage(error, 'Could not change visibility.'));
    } finally {
      this.markBusy(category.id, false);
    }
  }

  protected open(category: CategoryResponse | null): void {
    const ref = this.sheets.open<CategoryResponse, CategoryDialogData, CategoryDialog>(CategoryDialog, {
      data: { category },
      maxWidth: '34rem',
    });
    ref.closed.subscribe((saved) => {
      if (!saved) return;
      if (category) {
        this.patch(saved);
        this.toasts.success(`${saved.name} saved.`);
      } else {
        this.categories.update((list) => [...list, saved]);
        this.toasts.success(`${saved.name} added.`);
      }
    });
  }

  private patch(category: CategoryResponse): void {
    this.categories.update((list) => list.map((c) => (c.id === category.id ? category : c)));
  }

  private markBusy(id: number, busy: boolean): void {
    this.busyIds.update((set) => {
      const next = new Set(set);
      if (busy) next.add(id);
      else next.delete(id);
      return next;
    });
  }
}
