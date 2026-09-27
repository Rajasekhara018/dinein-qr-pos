import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';
import { AdminOrdersApi } from '../../../core/api/admin.api';
import { AdminOrderSummary, ORDER_TYPES, OrderStatus, OrderType } from '../../../core/api/models';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { orderStatusLabel } from '../../../shared/components/order-status-badge';
import { orderRefreshSignals } from '../data/live-refresh';
import { addDays, istDate } from '../shared/ist-date';
import { FILTERABLE_STATUSES } from './order-actions';

interface OrderFilterForm {
  date: FormControl<string>;
  q: FormControl<string>;
}

const PAGE_SIZE = 25;

/**
 * Orders: filter by IST date, status (multi), order type and search; table/cards; live refresh. Filters live in the
 * URL (`date`, `status`, `type`, `q`, `page`).
 */
@Component({
  selector: 'app-admin-orders-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './orders-page.html',
})
export class OrdersPage implements OnInit {
  private readonly api = inject(AdminOrdersApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly realtime = inject(RealtimeService);

  protected readonly statusOptions = FILTERABLE_STATUSES.map((status) => ({
    status,
    label: orderStatusLabel(status),
  }));

  protected readonly filters = new FormGroup<OrderFilterForm>({
    date: new FormControl(istDate(), { nonNullable: true }),
    q: new FormControl('', { nonNullable: true }),
  });
  protected readonly statuses = signal<ReadonlySet<OrderStatus>>(new Set());
  protected readonly orderType = signal<OrderType | null>(null);
  protected readonly typeOptions: { value: OrderType | null; label: string }[] = [
    { value: null, label: 'All types' },
    { value: 'DINE_IN', label: 'Dine-in' },
    { value: 'TAKEAWAY', label: 'Takeaway' },
  ];
  protected readonly page = signal(0);

  protected readonly orders = signal<AdminOrderSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly loaded = signal(false);
  protected readonly error = signal<unknown>(null);
  protected readonly today = istDate();

  protected readonly hasFilters = computed(() => this.statuses().size > 0);
  private seq = 0;

  constructor() {
    this.filters.controls.q.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.apply());
    this.filters.controls.date.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.apply());
    orderRefreshSignals(this.realtime)
      .pipe(takeUntilDestroyed())
      .subscribe(() => void this.load(true));
  }

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const date = params.get('date');
    const status = params.get('status');
    const type = params.get('type');
    this.orderType.set(
      type && (ORDER_TYPES as readonly string[]).includes(type) ? (type as OrderType) : null,
    );
    this.filters.setValue(
      { date: date && /^\d{4}-\d{2}-\d{2}$/.test(date) ? date : istDate(), q: params.get('q') ?? '' },
      { emitEvent: false },
    );
    if (status) {
      const allowed = new Set<string>(FILTERABLE_STATUSES);
      this.statuses.set(new Set(status.split(',').filter((s) => allowed.has(s)) as OrderStatus[]));
    }
    this.page.set(Math.max(0, Number(params.get('page') ?? 0) || 0));
    void this.load();
  }

  protected toggleStatus(status: OrderStatus): void {
    this.statuses.update((set) => {
      const next = new Set(set);
      if (next.has(status)) next.delete(status);
      else next.add(status);
      return next;
    });
    this.apply();
  }

  protected setOrderType(type: OrderType | null): void {
    if (this.orderType() === type) return;
    this.orderType.set(type);
    this.apply();
  }

  protected clearStatuses(): void {
    this.statuses.set(new Set());
    this.apply();
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    this.syncUrl();
    void this.load();
  }

  protected shiftDate(days: number): void {
    this.filters.controls.date.setValue(addDays(this.filters.controls.date.value || istDate(), days));
  }

  private apply(): void {
    this.page.set(0);
    this.syncUrl();
    void this.load();
  }

  private syncUrl(): void {
    const { date, q } = this.filters.getRawValue();
    void this.router.navigate([], {
      relativeTo: this.route,
      replaceUrl: true,
      queryParams: {
        date: date === istDate() ? null : date,
        q: q.trim() || null,
        status: this.statuses().size ? [...this.statuses()].join(',') : null,
        type: this.orderType(),
        page: this.page() || null,
      },
    });
  }

  protected async load(silent = false): Promise<void> {
    const seq = ++this.seq;
    if (!silent) this.loading.set(true);
    const { date, q } = this.filters.getRawValue();
    try {
      const result = await firstValueFrom(
        this.api.list({
          date: date || undefined,
          q: q.trim() || undefined,
          status: this.statuses().size ? [...this.statuses()] : undefined,
          orderType: this.orderType() ?? undefined,
          page: this.page(),
          size: PAGE_SIZE,
        }),
      );
      if (seq !== this.seq) return;
      this.orders.set(result.content);
      this.total.set(result.totalElements);
      this.totalPages.set(result.totalPages);
      this.error.set(null);
      this.loaded.set(true);
    } catch (error) {
      if (seq === this.seq && !silent) this.error.set(error);
    } finally {
      if (seq === this.seq) this.loading.set(false);
    }
  }
}
