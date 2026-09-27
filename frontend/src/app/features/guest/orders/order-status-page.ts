import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  numberAttribute,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { EMPTY, filter, firstValueFrom, switchMap } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import {
  GuestOrderView,
  OrderStatus,
  PAID_STATUSES,
  RealtimeEvent,
  TOPICS,
} from '../../../core/api/models';
import { PublicApi } from '../../../core/api/public.api';
import { CheckoutService } from '../../../core/payments/checkout.service';
import { PaymentNotCompletedError } from '../../../core/payments/checkout.types';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { playChime, vibrate } from '../../../core/util/alerts';
import { BrowserNavigator } from '../../../core/util/browser-navigator';
import { CartStore } from '../data/cart.store';
import { GuestSessionStore } from '../data/guest-session.store';

/** Backoff for polling a PENDING_PAYMENT order right after returning from a payment provider. */
const RETURN_POLL_DELAYS_MS = [1000, 2000, 3000, 5000, 8000, 13000];

/**
 * Live order status (`/menu/orders/:id`), also the landing page for redirect providers (`?payment=return|failed`).
 * REST is the source of truth: refetched on every `/topic/orders/{id}` event and on every (re)connect.
 */
@Component({
  selector: 'app-order-status-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-status-page.html',
  styleUrl: './order-status-page.css',
})
export class OrderStatusPage {
  /** Route param `:id` (component input binding). */
  readonly id = input.required({ transform: numberAttribute });
  /** Query param set by the backend's provider callback redirect. */
  readonly payment = input<'return' | 'failed' | undefined>();

  private readonly api = inject(PublicApi);
  private readonly realtime = inject(RealtimeService);
  private readonly checkout = inject(CheckoutService);
  private readonly cart = inject(CartStore);
  private readonly browser = inject(BrowserNavigator);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly session = inject(GuestSessionStore);

  protected readonly order = signal<GuestOrderView | null>(null);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly loading = signal(true);
  protected readonly retrying = signal(false);
  protected readonly paymentMessage = signal<string | null>(null);

  protected readonly status = computed(() => this.order()?.status ?? null);
  protected readonly isPaid = computed(() => {
    const s = this.status();
    return !!s && PAID_STATUSES.includes(s);
  });
  protected readonly showPaymentFailed = computed(
    () =>
      this.status() === 'PENDING_PAYMENT' &&
      (this.payment() === 'failed' || this.paymentMessage() !== null),
  );
  protected readonly restaurant = this.session.restaurant;

  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private previousStatus: OrderStatus | null = null;
  private readonly originalTitle = typeof document !== 'undefined' ? document.title : '';

  constructor() {
    // Load whenever the id changes; subscribe to that order's topic.
    toObservable(this.id)
      .pipe(
        switchMap((id) => {
          this.order.set(null);
          this.previousStatus = null;
          this.loading.set(true);
          void this.refetch(id).then(() => this.maybeStartReturnPolling());
          return Number.isFinite(id) ? this.realtime.watch<RealtimeEvent>(TOPICS.order(id)) : EMPTY;
        }),
        filter((event) => event.orderId == null || event.orderId === this.id()),
        takeUntilDestroyed(),
      )
      .subscribe(() => void this.refetch(this.id()));

    this.realtime.reconnected$
      .pipe(takeUntilDestroyed())
      .subscribe(() => void this.refetch(this.id()));

    // Clear the cart once the order that came from it is paid (covers redirect/form-post providers too).
    effect(() => {
      const order = this.order();
      if (order && PAID_STATUSES.includes(order.status)) {
        untracked(() => {
          if (this.cart.pendingOrderId() === order.id) this.cart.clear();
        });
      }
    });

    this.destroyRef.onDestroy(() => {
      this.stopPolling();
      this.restoreTitle();
    });
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

  protected async retryPayment(): Promise<void> {
    const order = this.order();
    if (!order || this.retrying()) return;
    this.retrying.set(true);
    this.paymentMessage.set(null);
    try {
      const response = await firstValueFrom(this.api.retryPayment(order.id));
      const outcome = await this.checkout.pay(response);
      if (outcome.kind === 'paid') this.onOrder(outcome.order);
    } catch (e) {
      if (e instanceof PaymentNotCompletedError) {
        this.paymentMessage.set(
          e.reason === 'dismissed' ? 'You closed the payment window before paying.' : e.message,
        );
      } else {
        const error = ApiError.from(e);
        this.paymentMessage.set(error.message);
        if (error.code === 'ORDER_NOT_PAYABLE') void this.refetch();
      }
    } finally {
      this.retrying.set(false);
    }
  }

  protected editCart(): void {
    void this.router.navigate(['/menu', 'cart']);
  }

  protected print(): void {
    this.browser.print();
  }

  private onOrder(order: GuestOrderView): void {
    const previous = this.previousStatus;
    this.previousStatus = order.status;
    this.order.set(order);
    if (previous && previous !== 'READY' && order.status === 'READY') {
      void playChime({ tones: [784, 1047, 1319] });
      vibrate([250, 120, 250, 120, 400]);
    }
    if (order.status === 'READY') {
      this.setTitle(`🔔 Order ready · Token ${order.displayToken}`);
    } else {
      this.restoreTitle();
    }
    if (order.status !== 'PENDING_PAYMENT') this.stopPolling();
  }

  private setTitle(title: string): void {
    if (typeof document !== 'undefined') document.title = title;
  }

  private restoreTitle(): void {
    this.setTitle(this.originalTitle);
  }

  /** After returning from a provider the webhook/callback may lag a little: poll a few times with backoff. */
  private maybeStartReturnPolling(): void {
    if (this.payment() !== 'return' || this.status() !== 'PENDING_PAYMENT') return;
    let attempt = 0;
    const tick = () => {
      if (attempt >= RETURN_POLL_DELAYS_MS.length || this.status() !== 'PENDING_PAYMENT') return;
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
  }
}
