import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, filter, firstValueFrom, switchMap } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import {
  GuestOrderView,
  RealtimeEvent,
  TOPICS,
} from '../../../core/api/models';
import { WaiterApi } from '../../../core/api/waiter.api';
import { CheckoutService } from '../../../core/payments/checkout.service';
import { CheckoutContext, PaymentNotCompletedError } from '../../../core/payments/checkout.types';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { ToastService } from '../../../core/ui/toast.service';
import { WaiterBoardStore } from '../data/waiter-board.store';

/** Backoff for polling a PENDING_PAYMENT order right after returning from a payment provider. */
export const RETURN_POLL_DELAYS_MS = [1000, 2000, 3000, 5000, 8000, 13000];

/**
 * `/waiter/orders/:id`: bill and live status of one order. Also the landing page of redirect gateways for orders
 * paid on the waiter's device (`?payment=return|failed`): it re-checks the order (the backend verified the payment
 * with the provider before redirecting, and the webhook may still be on its way, so it polls a few times) and
 * offers "Retry payment" while the order is unpaid.
 */
@Component({
  selector: 'app-waiter-order-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './waiter-order-page.html',
})
export class WaiterOrderPage {
  /** Route param `:id`. */
  readonly id = input.required({ transform: numberAttribute });
  /** Query param set by the backend's provider callback redirect. */
  readonly payment = input<'return' | 'failed' | undefined>();

  private readonly api = inject(WaiterApi);
  private readonly realtime = inject(RealtimeService);
  private readonly checkout = inject(CheckoutService);
  private readonly toasts = inject(ToastService);
  private readonly board = inject(WaiterBoardStore);

  protected readonly order = signal<GuestOrderView | null>(null);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly loading = signal(true);
  protected readonly retrying = signal(false);
  protected readonly serving = signal(false);
  protected readonly paymentMessage = signal<string | null>(null);

  protected readonly status = computed(() => this.order()?.status ?? null);
  protected readonly showPaymentFailed = computed(
    () =>
      this.status() === 'PENDING_PAYMENT' &&
      (this.payment() === 'failed' || this.paymentMessage() !== null),
  );
  protected readonly checkingPayment = computed(
    () => this.status() === 'PENDING_PAYMENT' && this.payment() === 'return' && this.polling(),
  );
  private readonly polling = signal(false);

  private readonly context: CheckoutContext = {
    verify: (body) => this.api.verifyPayment(body),
    orderPage: (orderId) => ['/waiter/orders', orderId],
  };
  private pollTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    toObservable(this.id)
      .pipe(
        switchMap((id) => {
          this.order.set(null);
          this.loading.set(true);
          void this.refetch(id).then(() => this.maybeStartReturnPolling());
          return Number.isFinite(id) ? this.realtime.watch<RealtimeEvent>(TOPICS.order(id)) : EMPTY;
        }),
        filter((event) => event.orderId == null || event.orderId === this.id()),
        takeUntilDestroyed(),
      )
      .subscribe(() => void this.refetch());
    this.realtime.reconnected$.pipe(takeUntilDestroyed()).subscribe(() => void this.refetch());
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  protected async refetch(id: number = this.id()): Promise<void> {
    if (!Number.isFinite(id)) {
      this.error.set(new ApiError(404, 'NOT_FOUND', 'Order not found.'));
      this.loading.set(false);
      return;
    }
    try {
      const order = await firstValueFrom(this.api.order(id));
      if (id !== this.id()) return;
      this.error.set(null);
      this.onOrder(order);
    } catch (e) {
      if (id !== this.id()) return;
      if (!this.order()) this.error.set(ApiError.from(e));
    } finally {
      if (id === this.id()) this.loading.set(false);
    }
  }

  /** New checkout for the unpaid staff-assisted ONLINE order, then the usual payment strategies. */
  protected async retryPayment(): Promise<void> {
    const order = this.order();
    if (!order || this.retrying()) return;
    this.retrying.set(true);
    this.paymentMessage.set(null);
    try {
      const response = await firstValueFrom(this.api.retryPayment(order.id));
      const outcome = await this.checkout.pay(response, this.context);
      if (outcome.kind === 'paid') this.onOrder(outcome.order);
    } catch (e) {
      if (e instanceof PaymentNotCompletedError) {
        this.paymentMessage.set(
          e.reason === 'dismissed' ? 'The payment window was closed before paying.' : e.message,
        );
      } else {
        const error = ApiError.from(e);
        this.paymentMessage.set(
          error.code === 'NOT_STAFF_ORDER'
            ? 'The guest placed this order; they pay for it on their own phone.'
            : error.message,
        );
        if (error.code === 'ORDER_NOT_PAYABLE') void this.refetch();
      }
    } finally {
      this.retrying.set(false);
    }
  }

  /** READY → served, from the order page. */
  protected async serve(): Promise<void> {
    const order = this.order();
    if (!order || order.status !== 'READY' || this.serving()) return;
    this.serving.set(true);
    const card = this.board.orders().find((o) => o.id === order.id);
    try {
      if (card) {
        await this.board.serve(card);
      } else {
        await firstValueFrom(this.api.serve(order.id));
        this.toasts.success(`Token #${order.displayToken} served.`);
      }
    } catch (e) {
      this.toasts.error(ApiError.from(e).message);
    } finally {
      this.serving.set(false);
      void this.refetch();
    }
  }

  private onOrder(order: GuestOrderView): void {
    this.order.set(order);
    if (order.status !== 'PENDING_PAYMENT') this.stopPolling();
  }

  private maybeStartReturnPolling(): void {
    if (this.payment() !== 'return' || this.status() !== 'PENDING_PAYMENT') return;
    let attempt = 0;
    this.polling.set(true);
    const tick = () => {
      if (attempt >= RETURN_POLL_DELAYS_MS.length || this.status() !== 'PENDING_PAYMENT') {
        this.polling.set(false);
        return;
      }
      this.pollTimer = setTimeout(async () => {
        attempt++;
        await this.refetch();
        tick();
      }, RETURN_POLL_DELAYS_MS[attempt]);
    };
    tick();
  }

  private stopPolling(): void {
    if (this.pollTimer) clearTimeout(this.pollTimer);
    this.pollTimer = null;
    this.polling.set(false);
  }
}
