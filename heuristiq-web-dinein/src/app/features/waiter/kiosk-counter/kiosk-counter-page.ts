import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  signal,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { KioskCounterOrder, KioskCounterPaymentMethod } from '../../../core/api/models';
import { WaiterApi } from '../../../core/api/waiter.api';
import { ToastService } from '../../../core/ui/toast.service';
import { formatInr } from '../../../core/util/money';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { WaiterBoardStore } from '../data/waiter-board.store';
import {
  addonsLine,
  filterByToken,
  isExpired,
  itemTitle,
  paymentMethod,
  PAYMENT_METHODS,
  removeKioskOrder,
  sortOldestFirst,
  waitedMinutes,
} from './kiosk-counter-logic';

/** How often the unpaid list is re-read (the kitchen realtime channel refreshes it sooner when it is active). */
export const KIOSK_POLL_MS = 10_000;

/**
 * `/waiter/kiosk`: unpaid self-order kiosk orders by token number. The waiter takes payment at the counter with
 * one of three big buttons and a confirm step that repeats the amount.
 */
@Component({
  selector: 'app-waiter-kiosk-counter-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './kiosk-counter-page.html',
})
export class KioskCounterPage {
  private readonly api = inject(WaiterApi);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly board = inject(WaiterBoardStore);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly methods = PAYMENT_METHODS;
  protected readonly orders = signal<readonly KioskCounterOrder[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly query = signal('');
  protected readonly paying = signal<number | null>(null);
  protected readonly now = signal(Date.now());

  protected readonly visible = computed(() => filterByToken(this.orders(), this.query()));

  private inflight: Promise<void> | null = null;
  private queued = false;
  private destroyed = false;

  constructor() {
    const poll = setInterval(() => {
      this.now.set(Date.now());
      void this.refresh();
    }, KIOSK_POLL_MS);
    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      clearInterval(poll);
    });

    // The waiter shell already listens to the kitchen realtime channel; a change in its order list (e.g. a kiosk
    // order just got paid and confirmed) is our cue to re-read sooner than the next poll.
    let first = true;
    effect(() => {
      this.board.orders();
      if (first) {
        first = false;
        return;
      }
      void this.refresh();
    });

    void this.refresh();
  }

  /** Single-flight; a call during a request queues exactly one follow-up. */
  protected refresh(): Promise<void> {
    if (this.inflight) {
      this.queued = true;
      return this.inflight;
    }
    this.inflight = this.fetch().finally(() => {
      this.inflight = null;
      if (this.queued && !this.destroyed) {
        this.queued = false;
        void this.refresh();
      }
    });
    return this.inflight;
  }

  private async fetch(): Promise<void> {
    try {
      const list = await firstValueFrom(this.api.kioskOrders());
      if (this.destroyed) return;
      this.orders.set(sortOldestFirst(list));
      this.error.set(null);
    } catch (e) {
      if (this.destroyed) return;
      // A failed background poll keeps the list on screen; only an empty screen shows the error state.
      if (this.orders().length === 0) this.error.set(ApiError.from(e));
    } finally {
      if (!this.destroyed) this.loading.set(false);
    }
  }

  protected onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  protected title = itemTitle;
  protected addons = addonsLine;
  protected expired = isExpired;
  protected waited(order: KioskCounterOrder): number {
    return waitedMinutes(order.placedAt, this.now());
  }

  protected async pay(order: KioskCounterOrder, method: KioskCounterPaymentMethod): Promise<void> {
    if (this.paying() !== null) return;
    const info = paymentMethod(method);
    const amount = formatInr(order.grandTotal);
    const confirmed = await this.confirmService.confirm({
      title: `Take ${amount} ${info.phrase}?`,
      message: `Token #${order.displayToken}. Collect exactly ${amount} before confirming.`,
      confirmLabel: `Received ${amount}`,
      tone: 'primary',
    });
    if (!confirmed) return;
    this.paying.set(order.id);
    try {
      await firstValueFrom(this.api.payKioskOrder(order.id, method));
      this.orders.update((list) => removeKioskOrder(list, order.id));
      this.toasts.success(`Token #${order.displayToken} paid ${info.phrase} (${amount}).`, {
        key: `kiosk-paid-${order.id}`,
      });
    } catch (e) {
      const error = ApiError.from(e);
      if (error.status === 401) return; // the interceptor sends us to the login
      // ALREADY_PAID / ORDER_NOT_PAYABLE / PAYMENT_FLAGGED / NOT_A_KIOSK_ORDER carry readable messages.
      this.toasts.error(error.message, { key: `kiosk-pay-${order.id}` });
      void this.refresh();
    } finally {
      if (!this.destroyed) this.paying.set(null);
    }
  }
}
