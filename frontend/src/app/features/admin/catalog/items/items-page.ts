import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';
import { AdminMenuApi } from '../../../../core/api/admin.api';
import { CategoryResponse, ItemResponse } from '../../../../core/api/models';
import { ToastService } from '../../../../core/ui/toast.service';
import { ConfirmService } from '../../shared/confirm.service';
import { errorMessage } from '../../shared/form-errors';
import { PriceChange } from '../data/inline-price';
import { sortByDisplayOrder } from '../data/reorder';
import { ItemsListStore } from './items-list.store';

type AvailabilityFilter = 'all' | 'available' | 'unavailable';

/** Menu items: category filter, search, availability toggle, inline rate editing, soft delete. */
@Component({
  selector: 'app-admin-items-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ItemsListStore],
  templateUrl: './items-page.html',
})
export class ItemsPage implements OnInit {
  protected readonly store = inject(ItemsListStore);
  private readonly api = inject(AdminMenuApi);
  private readonly router = inject(Router);
  private readonly confirmService = inject(ConfirmService);
  private readonly toasts = inject(ToastService);

  /** `?categoryId=` (router-bound). */
  readonly categoryId = input<string | undefined>();

  protected readonly categories = signal<CategoryResponse[]>([]);
  protected readonly categoriesError = signal(false);
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly availability = new FormControl<AvailabilityFilter>('all', { nonNullable: true });
  protected readonly categoryControl = new FormControl<number | null>(null);

  protected readonly selectedCategory = computed(() => {
    const id = this.store.filters().categoryId;
    return id === null ? null : (this.categories().find((c) => c.id === id) ?? null);
  });
  protected readonly totalItemCount = computed(() =>
    this.categories().reduce((sum, c) => sum + c.itemCount, 0),
  );
  private readonly categoryById = computed(
    () => new Map(this.categories().map((c) => [c.id, c] as const)),
  );

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => this.store.setFilters({ q }));
    this.availability.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) =>
      this.store.setFilters({ available: value === 'all' ? null : value === 'available' }),
    );
    this.categoryControl.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => this.selectCategory(id === null ? null : Number(id)));
    void this.loadCategories();
  }

  ngOnInit(): void {
    const initial = this.categoryId() ? Number(this.categoryId()) : null;
    this.categoryControl.setValue(Number.isFinite(initial) ? initial : null, { emitEvent: false });
    this.store.filters.update((f) => ({ ...f, categoryId: Number.isFinite(initial) ? initial : null }));
    void this.store.load();
  }

  protected async loadCategories(): Promise<void> {
    try {
      this.categories.set(sortByDisplayOrder(await firstValueFrom(this.api.categories())));
      this.categoriesError.set(false);
    } catch {
      this.categoriesError.set(true);
    }
  }

  protected selectCategory(id: number | null): void {
    if (this.store.filters().categoryId === id) return;
    this.categoryControl.setValue(id, { emitEvent: false });
    this.store.setFilters({ categoryId: id });
    void this.router.navigate([], {
      queryParams: { categoryId: id ?? null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected clearFilters(): void {
    this.search.setValue('', { emitEvent: false });
    this.availability.setValue('all', { emitEvent: false });
    this.categoryControl.setValue(null, { emitEvent: false });
    this.store.setFilters({ categoryId: null, q: '', available: null });
    void this.router.navigate([], { queryParams: { categoryId: null }, replaceUrl: true });
  }

  protected categoryInactive(item: ItemResponse): boolean {
    return this.categoryById().get(item.categoryId)?.active === false;
  }

  protected onPrice(item: ItemResponse, change: PriceChange): void {
    void this.store.updatePrice(item.id, change);
  }

  protected onAvailability(item: ItemResponse, available: boolean): void {
    void this.store.setAvailability(item, available);
  }

  protected async remove(item: ItemResponse): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: `Delete ${item.name}?`,
      message: 'The item disappears from the menu and from this list. Past orders and bills are not affected.',
      confirmLabel: 'Delete item',
    });
    if (!confirmed) return;
    try {
      await firstValueFrom(this.api.deleteItem(item.id));
      this.store.remove(item.id);
      this.toasts.success(`${item.name} deleted.`);
      void this.loadCategories();
    } catch (error) {
      this.toasts.error(errorMessage(error, 'Could not delete the item.'));
    }
  }

  protected newItemLink(): Record<string, number> {
    const id = this.store.filters().categoryId;
    return id === null ? {} : { categoryId: id };
  }
}
