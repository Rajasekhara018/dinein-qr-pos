import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, finalize, merge, Observable, startWith, Subject, switchMap, tap } from 'rxjs';
import { AdminReportsApi } from '../../../core/api/admin.api';
import { Dashboard, OrderStatus } from '../../../core/api/models';
import { AuthStore } from '../../../core/auth/auth.store';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { orderStatusLabel } from '../../../shared/components/order-status-badge';
import { orderRefreshSignals } from '../data/live-refresh';

const STATUS_ORDER: OrderStatus[] = [
  'PENDING_PAYMENT',
  'CONFIRMED',
  'PREPARING',
  'READY',
  'COMPLETED',
  'CANCELLED',
  'PAYMENT_FAILED',
  'EXPIRED',
];

/** Today at a glance, refreshed live from kitchen events and on every reconnect. */
@Component({
  selector: 'app-admin-dashboard-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-page.html',
})
export class DashboardPage {
  private readonly api = inject(AdminReportsApi);
  private readonly realtime = inject(RealtimeService);
  protected readonly auth = inject(AuthStore);

  protected readonly data = signal<Dashboard | null>(null);
  protected readonly error = signal<unknown>(null);
  protected readonly loading = signal(true);
  protected readonly refreshing = signal(false);
  protected readonly updatedAt = signal<Date | null>(null);

  protected readonly statuses = computed(() => {
    const byStatus = this.data()?.ordersByStatus ?? {};
    return STATUS_ORDER.map((status) => ({
      status,
      label: orderStatusLabel(status),
      count: byStatus[status] ?? 0,
    }));
  });

  protected readonly greeting = computed(() => {
    const name = this.auth.user()?.displayName || this.auth.user()?.username || '';
    return name ? `Hello, ${name}` : 'Dashboard';
  });

  private readonly manual$ = new Subject<void>();

  constructor() {
    merge(this.manual$, orderRefreshSignals(this.realtime, this.auth.user()?.restaurantId))
      .pipe(
        startWith(undefined),
        switchMap(() => this.fetch()),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  protected refresh(): void {
    this.manual$.next();
  }

  private fetch(): Observable<unknown> {
    this.refreshing.set(true);
    return this.api.dashboard().pipe(
      tap((dashboard) => {
        this.data.set(dashboard);
        this.error.set(null);
        this.updatedAt.set(new Date());
      }),
      catchError((error: unknown) => {
        if (!this.data()) this.error.set(error);
        return EMPTY;
      }),
      finalize(() => {
        this.loading.set(false);
        this.refreshing.set(false);
      }),
    );
  }
}
