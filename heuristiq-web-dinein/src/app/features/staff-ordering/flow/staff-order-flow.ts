import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom, Observable } from 'rxjs';
import { AdminOrdersApi } from '../../../core/api/admin.api';
import { ApiError } from '../../../core/api/api-error';
import {
  CheckoutResponse,
  MenuItem,
  MenuResponse,
  OrderType,
  StaffPaymentMethod,
  StaffPlaceOrderRequest,
  WaiterConfig,
  WaiterTableView,
} from '../../../core/api/models';
import { WaiterApi } from '../../../core/api/waiter.api';
import { CheckoutService } from '../../../core/payments/checkout.service';
import {
  CheckoutContext,
  PaymentNotCompletedError,
  PaymentNotCompletedReason,
} from '../../../core/payments/checkout.types';
import { SheetService } from '../../../core/ui/sheet.service';
import { IdempotencyKeyHolder } from '../../../core/util/idempotency';
import { staffPaymentMethodLabel } from '../../../core/util/order-labels';
import {
  CartLine,
  defaultVariantId,
  isCustomizable,
  ItemSelection,
  MAX_CUSTOMER_NAME,
} from '../../guest/data/cart.models';
import { ItemSheet, ItemSheetData } from '../../guest/item-sheet/item-sheet';
import { StaffLineQuantity } from '../cart/staff-cart-panel';
import { StaffCartStore } from '../data/staff-cart.store';
import {
  buildStaffOrderRequest,
  checkoutBranch,
  draftFingerprint,
  draftProblem,
  MAX_STAFF_NOTE,
  placeErrorMessage,
  StaffOrderDraft,
  StaffOrderingMode,
} from '../data/staff-order';

type Step = 'where' | 'order' | 'done';
type Phase = 'idle' | 'placing' | 'paying' | 'retrying';

interface OrderDetailsForm {
  note: FormControl<string>;
  customerName: FormControl<string>;
  customerPhone: FormControl<string>;
}

export interface OnlinePaymentFailure {
  orderId: number;
  reason: PaymentNotCompletedReason | 'error';
  message: string;
}

/** Optional 10-digit Indian mobile (same rule as the backend). */
const OPTIONAL_MOBILE = /^([6-9]\d{9})?$/;

/**
 * Staff-assisted ordering, shared by the waiter screen (`/waiter/new`) and the admin panel (`/admin/orders/new`):
 * pick a table or takeaway → browse the waiter menu (unavailable items greyed) → cart with a labelled estimate →
 * customer details and payment method → place with an idempotency key.
 *
 * - Offline methods (cash / UPI / card at the counter) are confirmed by the server at once: a success screen shows
 *   the token and the server-computed amount.
 * - Online reuses `CheckoutService` (SDK / form post / redirect); SDK payments are verified through the waiter
 *   endpoint, redirect gateways come back to `/waiter/orders/{id}`.
 *
 * The only difference between the modes is the place endpoint and where the order page is.
 */
@Component({
  selector: 'app-staff-order-flow',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [StaffCartStore],
  host: { class: 'block min-w-0' },
  templateUrl: './staff-order-flow.html',
})
export class StaffOrderFlow implements OnInit {
  readonly mode = input<StaffOrderingMode>('waiter');
  /** Pre-selected table (e.g. tapped on the Tables tab). */
  readonly initialTableId = input<number | null>(null);
  /** Pre-selected order type (`TAKEAWAY` skips the table step). */
  readonly initialType = input<OrderType | null>(null);

  protected readonly cart = inject(StaffCartStore);
  private readonly waiterApi = inject(WaiterApi);
  private readonly adminApi = inject(AdminOrdersApi);
  private readonly checkout = inject(CheckoutService);
  private readonly sheets = inject(SheetService);
  private readonly router = inject(Router);

  protected readonly config = signal<WaiterConfig | null>(null);
  protected readonly tables = signal<readonly WaiterTableView[]>([]);
  protected readonly menu = signal<MenuResponse | null>(null);
  protected readonly loadState = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly loadError = signal<unknown>(null);

  protected readonly step = signal<Step>('where');
  /** Phones: the menu and the order are two views; side by side from 1024px. */
  protected readonly view = signal<'menu' | 'cart'>('menu');
  protected readonly tableId = signal<number | null>(null);
  protected readonly orderType = signal<OrderType>('DINE_IN');
  protected readonly paymentMethod = signal<StaffPaymentMethod>('CASH');

  protected readonly phase = signal<Phase>('idle');
  protected readonly busy = computed(() => this.phase() !== 'idle');
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly placed = signal<CheckoutResponse | null>(null);
  protected readonly failure = signal<OnlinePaymentFailure | null>(null);
  /** Screen-reader confirmation of the last add. */
  protected readonly announcement = signal('');

  protected readonly maxNote = MAX_STAFF_NOTE;
  protected readonly maxName = MAX_CUSTOMER_NAME;

  protected readonly form = new FormGroup<OrderDetailsForm>({
    note: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(MAX_STAFF_NOTE)],
    }),
    customerName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(MAX_CUSTOMER_NAME)],
    }),
    customerPhone: new FormControl('', {
      nonNullable: true,
      validators: [Validators.pattern(OPTIONAL_MOBILE)],
    }),
  });

  protected readonly selectedTable = computed(
    () => this.tables().find((t) => t.id === this.tableId()) ?? null,
  );
  protected readonly whereLabel = computed(() => {
    if (this.orderType() === 'TAKEAWAY') {
      const table = this.selectedTable();
      return table ? `Takeaway · ${table.label}` : 'Takeaway';
    }
    const table = this.selectedTable();
    return table ? `Table ${table.label}` : '';
  });
  protected readonly takeawayEnabled = computed(() => this.config()?.takeawayEnabled ?? false);
  protected readonly onlineAvailable = computed(
    () => this.config()?.onlinePaymentsAvailable ?? false,
  );
  protected readonly closedMessage = computed(() => {
    const c = this.config();
    if (!c) return null;
    if (!c.acceptingOrders) return 'The restaurant is not accepting orders right now.';
    if (!c.openNow) return 'The restaurant is closed right now (outside opening hours).';
    return null;
  });
  protected readonly problem = computed(() =>
    draftProblem({
      tableId: this.tableId(),
      orderType: this.orderType(),
      lineCount: this.cart.orderableLines().length,
      hasIssues: this.cart.hasIssues(),
    }),
  );
  protected readonly canPlace = computed(
    () => !this.problem() && !this.closedMessage() && !this.busy(),
  );
  protected readonly itemsById = computed(() => {
    const map = new Map<number, MenuItem>();
    for (const category of this.menu()?.categories ?? [])
      for (const item of category.items) map.set(item.id, item);
    return map;
  });
  protected readonly methodLabel = computed(() => staffPaymentMethodLabel(this.paymentMethod()));

  private readonly idempotency = new IdempotencyKeyHolder();

  /** SDK payments are verified with the waiter endpoint; the order page depends on the app. */
  private readonly checkoutContext: CheckoutContext = {
    verify: (body) => this.waiterApi.verifyPayment(body),
    orderPage: (orderId) => this.orderLink(orderId),
  };

  ngOnInit(): void {
    const type = this.initialType();
    const tableId = this.initialTableId();
    if (type === 'TAKEAWAY') {
      this.orderType.set('TAKEAWAY');
      this.tableId.set(tableId);
      this.step.set('order');
    } else if (tableId != null) {
      this.tableId.set(tableId);
      this.step.set('order');
    }
    void this.load();
  }

  async load(): Promise<void> {
    this.loadState.set('loading');
    try {
      const [config, tables, menu] = await Promise.all([
        firstValueFrom(this.waiterApi.config()),
        firstValueFrom(this.waiterApi.tables()),
        firstValueFrom(this.waiterApi.menu()),
      ]);
      this.config.set(config);
      this.tables.set(tables);
      this.applyMenu(menu);
      this.loadError.set(null);
      this.loadState.set('ready');
      // A pre-selected table that no longer exists, or takeaway while it is off → pick again.
      if (this.orderType() === 'TAKEAWAY' && !config.takeawayEnabled) {
        this.orderType.set('DINE_IN');
        this.tableId.set(null);
        this.step.set('where');
      } else if (this.tableId() != null && !this.selectedTable()) {
        this.tableId.set(null);
        if (this.orderType() === 'DINE_IN') this.step.set('where');
      }
    } catch (error) {
      this.loadError.set(error);
      this.loadState.set('error');
    }
  }

  orderLink(orderId: number): unknown[] {
    return this.mode() === 'admin' ? ['/admin/orders', orderId] : ['/waiter/orders', orderId];
  }

  protected backLink(): unknown[] {
    return this.mode() === 'admin' ? ['/admin/orders'] : ['/waiter/tables'];
  }

  protected backLabel(): string {
    return this.mode() === 'admin' ? 'All orders' : 'Back to tables';
  }

  // ─── Where ───────────────────────────────────────────────────────────────────────────────────

  protected pickTable(table: WaiterTableView): void {
    this.tableId.set(table.id);
    this.orderType.set('DINE_IN');
    this.enterOrderStep();
  }

  protected pickTakeaway(): void {
    this.tableId.set(null);
    this.orderType.set('TAKEAWAY');
    this.enterOrderStep();
  }

  protected changeWhere(): void {
    this.step.set('where');
  }

  private enterOrderStep(): void {
    this.errorMessage.set(null);
    this.step.set('order');
    this.view.set('menu');
  }

  // ─── Cart ────────────────────────────────────────────────────────────────────────────────────

  protected addItem(item: MenuItem): void {
    if (!item.available) return;
    if (isCustomizable(item)) {
      void this.openSheet(item);
      return;
    }
    this.addSelection(item, {
      variantId: defaultVariantId(item),
      addonIds: [],
      notes: '',
      quantity: 1,
    });
  }

  protected editLine(line: CartLine): void {
    const item = this.itemsById().get(line.itemId);
    if (item) void this.openSheet(item, line);
  }

  protected setQuantity(change: StaffLineQuantity): void {
    this.cart.setQuantity(change.line.key, change.quantity);
  }

  private async openSheet(item: MenuItem, line?: CartLine): Promise<void> {
    const ref = this.sheets.open<ItemSelection, ItemSheetData, ItemSheet>(ItemSheet, {
      data: { item, line },
      ariaLabel: item.name,
    });
    const selection = await firstValueFrom(ref.closed);
    if (!selection) return;
    if (line) this.cart.replace(line.key, item, selection);
    else this.addSelection(item, selection);
  }

  private addSelection(item: MenuItem, selection: ItemSelection): void {
    if (this.cart.add(item, selection)) {
      this.announcement.set(`Added ${selection.quantity} × ${item.name}`);
      this.errorMessage.set(null);
    }
  }

  protected onPhoneInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '').slice(0, 10);
    if (digits !== input.value) {
      input.value = digits;
      this.form.controls.customerPhone.setValue(digits);
    }
  }

  // ─── Place & pay ─────────────────────────────────────────────────────────────────────────────

  /** Builds the draft from the current state (exposed for tests). */
  draft(): { draft: StaffOrderDraft; keys: string[] } {
    const { items, keys } = this.cart.toItems();
    const { note, customerName, customerPhone } = this.form.getRawValue();
    return {
      keys,
      draft: {
        tableId: this.tableId(),
        orderType: this.orderType(),
        items,
        note,
        customerName,
        customerPhone,
        paymentMethod: this.paymentMethod(),
      },
    };
  }

  async place(): Promise<void> {
    if (this.busy()) return; // double-tap guard
    this.errorMessage.set(null);
    const problem = this.problem();
    if (problem) {
      this.errorMessage.set(problem);
      if (this.orderType() === 'DINE_IN' && this.tableId() == null) this.step.set('where');
      return;
    }
    if (this.closedMessage()) {
      this.errorMessage.set(this.closedMessage());
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { draft, keys } = this.draft();
    // Same payload → same key: a retried or double-submitted attempt returns the same order.
    const key = this.idempotency.keyFor(draftFingerprint(draft));
    const request = buildStaffOrderRequest(draft, key);
    this.failure.set(null);
    this.phase.set('placing');
    try {
      const response = await firstValueFrom(this.placeRequest(request));
      switch (checkoutBranch(response)) {
        case 'confirmed':
          this.placed.set(response);
          this.step.set('done');
          this.resetOrder();
          break;
        case 'checkout':
          await this.startPayment(response);
          break;
        case 'already-paid':
          this.resetOrder();
          await this.router.navigate(this.orderLink(response.orderId));
          break;
      }
    } catch (error) {
      this.handlePlaceError(ApiError.from(error), keys);
    } finally {
      this.phase.set('idle');
    }
  }

  /** "Retry payment" after an online payment was not completed. */
  async retryPayment(): Promise<void> {
    const failure = this.failure();
    if (!failure || this.busy()) return;
    this.phase.set('retrying');
    try {
      const response = await firstValueFrom(this.waiterApi.retryPayment(failure.orderId));
      this.failure.set(null);
      await this.startPayment(response);
    } catch (error) {
      const apiError = ApiError.from(error);
      if (apiError.code === 'ORDER_NOT_PAYABLE' || apiError.status === 404) {
        this.failure.set(null);
        await this.router.navigate(this.orderLink(failure.orderId));
        return;
      }
      this.failure.set({ ...failure, reason: 'error', message: apiError.message });
    } finally {
      this.phase.set('idle');
    }
  }

  protected newOrder(): void {
    this.placed.set(null);
    this.failure.set(null);
    this.errorMessage.set(null);
    this.tableId.set(null);
    this.orderType.set('DINE_IN');
    this.step.set('where');
    this.view.set('menu');
    void this.refreshTables();
  }

  private placeRequest(request: StaffPlaceOrderRequest): Observable<CheckoutResponse> {
    return this.mode() === 'admin'
      ? this.adminApi.place(request)
      : this.waiterApi.placeOrder(request);
  }

  private async startPayment(response: CheckoutResponse): Promise<void> {
    this.phase.set('paying');
    try {
      const outcome = await this.checkout.pay(response, this.checkoutContext);
      if (outcome.kind === 'paid') {
        this.resetOrder();
        await this.router.navigate(this.orderLink(outcome.order.id));
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

  /** After a successful order: empty cart, fresh key, blank details (the payment method is kept). */
  private resetOrder(): void {
    this.cart.clear();
    this.idempotency.reset();
    this.form.reset({ note: '', customerName: '', customerPhone: '' });
  }

  private handlePlaceError(error: ApiError, keys: string[]): void {
    this.errorMessage.set(placeErrorMessage(error));
    switch (error.code) {
      case 'TABLE_REQUIRED':
        this.step.set('where');
        break;
      case 'INVALID_TABLE':
        this.tableId.set(null);
        this.step.set('where');
        void this.refreshTables();
        break;
      case 'ITEM_UNAVAILABLE':
        this.cart.markProblems(error.cartProblems, keys);
        void this.refreshMenu();
        break;
      case 'TAKEAWAY_DISABLED':
        this.config.update((c) => (c ? { ...c, takeawayEnabled: false } : c));
        this.orderType.set('DINE_IN');
        this.tableId.set(null);
        this.step.set('where');
        break;
      case 'ORDERING_CLOSED':
      case 'NOT_ACCEPTING_ORDERS':
      case 'OUTSIDE_OPENING_HOURS':
        void this.refreshConfig();
        break;
      case 'IDEMPOTENCY_KEY_REUSED':
        this.idempotency.reset();
        break;
    }
  }

  private applyMenu(menu: MenuResponse): void {
    this.menu.set(menu);
    this.cart.setPricesIncludeGst(menu.pricesIncludeGst);
    if (!this.cart.isEmpty()) this.cart.reconcile(menu);
  }

  private async refreshMenu(): Promise<void> {
    try {
      const menu = await firstValueFrom(this.waiterApi.menu());
      this.menu.set(menu);
      this.cart.setPricesIncludeGst(menu.pricesIncludeGst);
      // Keep the server's ITEM_UNAVAILABLE marks; only add marks the fresh menu reveals.
      if (!this.cart.isEmpty() && !this.cart.hasIssues()) this.cart.reconcile(menu);
    } catch {
      // keep the current menu
    }
  }

  private async refreshTables(): Promise<void> {
    try {
      this.tables.set(await firstValueFrom(this.waiterApi.tables()));
    } catch {
      // keep the current tables
    }
  }

  private async refreshConfig(): Promise<void> {
    try {
      this.config.set(await firstValueFrom(this.waiterApi.config()));
    } catch {
      // keep the current config
    }
  }
}
