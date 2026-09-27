import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../../core/api/api-error';
import { CheckoutResponse, OrderType } from '../../../core/api/models';
import { PublicApi } from '../../../core/api/public.api';
import { CheckoutService, orderPagePath } from '../../../core/payments/checkout.service';
import {
  PaymentNotCompletedError,
  PaymentNotCompletedReason,
} from '../../../core/payments/checkout.types';
import { ToastService } from '../../../core/ui/toast.service';
import { IdempotencyKeyHolder } from '../../../core/util/idempotency';
import { CartLine, INDIAN_MOBILE, MAX_CUSTOMER_NAME, MAX_ORDER_NOTES } from '../data/cart.models';
import { CartStore } from '../data/cart.store';
import { GuestSessionStore } from '../data/guest-session.store';
import { MenuStore } from '../data/menu.store';
import { ItemSheetLauncher } from '../item-sheet/item-sheet-launcher';
import { LineQuantityChange } from './cart-lines';

export interface PaymentFailure {
  orderId: number;
  reason: PaymentNotCompletedReason;
  message: string;
}

type CheckoutForm = FormGroup<{
  notes: FormControl<string>;
  customerName: FormControl<string>;
  customerPhone: FormControl<string>;
}>;

@Component({
  selector: 'app-cart-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cart-page.html',
})
export class CartPage {
  protected readonly cart = inject(CartStore);
  protected readonly session = inject(GuestSessionStore);
  private readonly menu = inject(MenuStore);
  private readonly api = inject(PublicApi);
  private readonly checkout = inject(CheckoutService);
  private readonly sheet = inject(ItemSheetLauncher);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly maxNotes = MAX_ORDER_NOTES;
  protected readonly maxName = MAX_CUSTOMER_NAME;

  protected readonly form: CheckoutForm = new FormGroup({
    notes: new FormControl(this.cart.notes(), {
      nonNullable: true,
      validators: [Validators.maxLength(MAX_ORDER_NOTES)],
    }),
    customerName: new FormControl(this.cart.customerName(), {
      nonNullable: true,
      validators: [Validators.maxLength(MAX_CUSTOMER_NAME)],
    }),
    customerPhone: new FormControl(this.cart.customerPhone(), {
      nonNullable: true,
      validators: [Validators.pattern(INDIAN_MOBILE)],
    }),
  });

  /** Phase of the current checkout attempt. */
  protected readonly phase = signal<'idle' | 'placing' | 'paying' | 'retrying'>('idle');
  protected readonly busy = computed(() => this.phase() !== 'idle');
  protected readonly failure = signal<PaymentFailure | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  /** Server-confirmed amount (paise) of the last placed order, when it differs from our estimate. */
  protected readonly serverTotal = signal<number | null>(null);

  protected readonly payTotal = computed(() => this.serverTotal() ?? this.cart.total());
  /** Dine-in / Takeaway is offered only when the restaurant enabled takeaway. */
  protected readonly takeawayEnabled = computed(
    () => this.session.restaurant()?.takeawayEnabled === true,
  );
  protected readonly orderType = signal<OrderType>('DINE_IN');
  protected readonly canPay = computed(
    () =>
      this.session.canOrder() &&
      !this.cart.isEmpty() &&
      !this.cart.hasIssues() &&
      this.cart.orderableLines().length > 0 &&
      !this.busy(),
  );

  private readonly idempotency = new IdempotencyKeyHolder();

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe((value) => {
      this.cart.setNotes(value.notes ?? '');
      this.cart.setCustomer(value.customerName ?? '', value.customerPhone ?? '');
      this.serverTotal.set(null);
    });
  }

  protected get phone(): FormControl<string> {
    return this.form.controls.customerPhone;
  }

  protected get name(): FormControl<string> {
    return this.form.controls.customerName;
  }

  protected get notes(): FormControl<string> {
    return this.form.controls.notes;
  }

  protected onPhoneInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 10);
    if (digits !== input.value) {
      input.value = digits;
      this.phone.setValue(digits);
    }
  }

  protected edit(line: CartLine): void {
    const item = this.menu.itemsById().get(line.itemId);
    if (item) void this.sheet.open(item, line);
  }

  protected setQuantity(change: LineQuantityChange): void {
    this.cart.setQuantity(change.line.key, change.quantity);
    this.serverTotal.set(null);
  }

  protected remove(line: CartLine): void {
    this.cart.remove(line.key);
    this.serverTotal.set(null);
  }

  protected setOrderType(type: OrderType): void {
    this.orderType.set(type);
    this.errorMessage.set(null);
  }

  protected dismissFailure(): void {
    this.failure.set(null);
  }

  /** Places the order (idempotently) and starts the payment. */
  async pay(): Promise<void> {
    if (this.busy()) return; // double-tap guard
    this.errorMessage.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focusFirstInvalid();
      return;
    }
    if (!this.canPay()) return;

    const { request: base, keys } = this.cart.toOrderRequest();
    // `orderType` is sent only when the guest could choose (older backends and "takeaway off" get the old body).
    const request = this.takeawayEnabled() ? { ...base, orderType: this.orderType() } : base;
    // Same payload → same key (a retried/double-submitted attempt returns the same order).
    const key = this.idempotency.keyFor(JSON.stringify(request));
    this.failure.set(null);
    this.phase.set('placing');
    try {
      const response = await firstValueFrom(this.api.placeOrder(request, key));
      this.cart.markCheckout(response.orderId);
      if (response.amountPaise != null && response.amountPaise !== this.cart.total()) {
        this.serverTotal.set(response.amountPaise);
      }
      await this.startPayment(response);
    } catch (error) {
      this.handlePlaceError(ApiError.from(error), keys);
    } finally {
      this.phase.set('idle');
    }
  }

  /** "Retry payment" for the order of the failed attempt. */
  async retryPayment(): Promise<void> {
    const failure = this.failure();
    if (!failure || this.busy()) return;
    this.phase.set('retrying');
    try {
      const response = await firstValueFrom(this.api.retryPayment(failure.orderId));
      this.failure.set(null);
      await this.startPayment(response);
    } catch (error) {
      const apiError = ApiError.from(error);
      if (apiError.code === 'ORDER_NOT_PAYABLE' || apiError.status === 404) {
        this.failure.set(null);
        await this.router.navigate(orderPagePath(failure.orderId));
        return;
      }
      this.failure.set({ ...failure, message: apiError.message });
    } finally {
      this.phase.set('idle');
    }
  }

  /** "Edit cart": close the failure panel and let the guest change things (a new attempt gets a new key). */
  protected editCart(): void {
    this.failure.set(null);
    this.host.nativeElement.querySelector<HTMLElement>('#cart-title')?.focus();
  }

  private async startPayment(response: CheckoutResponse): Promise<void> {
    this.phase.set('paying');
    try {
      const outcome = await this.checkout.pay(response);
      if (outcome.kind === 'paid') {
        this.cart.clear();
        this.idempotency.reset();
        await this.router.navigate(orderPagePath(outcome.order.id));
      }
      // 'navigating': leaving for the provider; 'not-payable': CheckoutService already navigated.
    } catch (error) {
      if (error instanceof PaymentNotCompletedError) {
        this.failure.set({ orderId: error.orderId, reason: error.reason, message: error.message });
        return;
      }
      throw error;
    }
  }

  private handlePlaceError(error: ApiError, keys: string[]): void {
    switch (error.code) {
      case 'ITEM_UNAVAILABLE':
        this.cart.markProblems(error.cartProblems, keys);
        void this.menu.load();
        this.errorMessage.set('Some items are no longer available. Remove them to continue.');
        break;
      case 'ORDERING_CLOSED':
      case 'OUTSIDE_OPENING_HOURS':
        this.errorMessage.set(error.message || 'Ordering is closed right now.');
        void this.session.refresh();
        break;
      case 'RATE_LIMITED':
        this.errorMessage.set('Too many attempts. Please wait a minute and try again.');
        break;
      case 'TAKEAWAY_DISABLED':
        this.orderType.set('DINE_IN');
        this.errorMessage.set(
          'Takeaway is not available right now, so we switched your order to dine-in. Tap Pay to continue.',
        );
        void this.session.refresh();
        break;
      case 'VALIDATION_FAILED': {
        const fields = error.fieldErrors.map((f) => f.message).filter(Boolean);
        this.errorMessage.set(fields.length ? fields.join('. ') : error.message);
        break;
      }
      case 'GUEST_SESSION_REQUIRED':
        void this.router.navigate(['/menu', 'scan']);
        break;
      case 'IDEMPOTENCY_KEY_REUSED':
        this.idempotency.reset();
        this.errorMessage.set('Please tap Pay again.');
        break;
      default:
        this.errorMessage.set(error.message);
        if (error.isNetworkError || error.isServerError) {
          this.toasts.error(error.message, { key: 'place-order' });
        }
    }
  }

  private focusFirstInvalid(): void {
    queueMicrotask(() =>
      this.host.nativeElement
        .querySelector<HTMLElement>('form .ng-invalid[formControlName]')
        ?.focus(),
    );
  }
}
