import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { GuestOrderSummary } from '../../../core/api/models';
import { PublicApi } from '../../../core/api/public.api';

/** Orders placed in the current guest session (`/menu/orders`). */
@Component({
  selector: 'app-my-orders-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-orders-page.html',
})
export class MyOrdersPage {
  private readonly api = inject(PublicApi);

  protected readonly orders = signal<GuestOrderSummary[] | null>(null);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly loading = signal(false);

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      const orders = await firstValueFrom(this.api.myOrders());
      this.orders.set(
        [...orders].sort((a, b) => (b.placedAt ?? '').localeCompare(a.placedAt ?? '')),
      );
      this.error.set(null);
    } catch (e) {
      this.error.set(ApiError.from(e));
    } finally {
      this.loading.set(false);
    }
  }
}
