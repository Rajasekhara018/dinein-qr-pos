/**
 * TypeScript mirrors of the backend DTO records (the API contract).
 *
 * Conventions of the backend's JSON:
 * - Jackson omits nulls, so every nullable Java field is optional (`?`) here.
 * - `Instant` → ISO-8601 string in UTC (`IsoInstant`); `LocalDate` → `YYYY-MM-DD`; `LocalTime` → `HH:mm[:ss]`.
 * - Money (`BigDecimal`) → JSON number with at most 2 decimals (`Money`). Do arithmetic in paise (see util/money.ts).
 * - Ids are `Long` → `number`.
 */

export type IsoInstant = string;
export type IsoLocalDate = string;
export type IsoLocalTime = string;
/** Rupees as a JSON number (BigDecimal with scale ≤ 2). */
export type Money = number;

// ─── Enums ───────────────────────────────────────────────────────────────────────────────────────

export type FoodType = 'VEG' | 'NON_VEG' | 'EGG';

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'PREPARING'
  | 'READY'
  | 'COMPLETED'
  | 'EXPIRED'
  | 'PAYMENT_FAILED'
  | 'CANCELLED';

export const PAID_STATUSES: readonly OrderStatus[] = [
  'CONFIRMED',
  'PREPARING',
  'READY',
  'COMPLETED',
];
export const KITCHEN_STATUSES: readonly OrderStatus[] = ['CONFIRMED', 'PREPARING', 'READY'];

export type StaffRole = 'OWNER' | 'MANAGER' | 'KITCHEN' | 'WAITER';

/** How the guest eats. Missing on older payloads means `DINE_IN`. */
export type OrderType = 'DINE_IN' | 'TAKEAWAY';
export const ORDER_TYPES: readonly OrderType[] = ['DINE_IN', 'TAKEAWAY'];

/** Counter / waiter payment taken by staff (`payment.method` when `provider === 'OFFLINE'`). */
export type OfflinePaymentMethod = 'CASH' | 'UPI_AT_COUNTER' | 'CARD_AT_COUNTER';
export const OFFLINE_PAYMENT_METHODS: readonly OfflinePaymentMethod[] = [
  'CASH',
  'UPI_AT_COUNTER',
  'CARD_AT_COUNTER',
];

/** How a staff-assisted order is paid (`OrderDtos.StaffPaymentMethod`). */
export type StaffPaymentMethod = OfflinePaymentMethod | 'ONLINE';
export const STAFF_PAYMENT_METHODS: readonly StaffPaymentMethod[] = [
  ...OFFLINE_PAYMENT_METHODS,
  'ONLINE',
];

/** `payment.provider` of payments taken at the counter (not a gateway). */
export const OFFLINE_PROVIDER = 'OFFLINE';

export type CheckoutMode = 'SDK' | 'FORM_POST' | 'REDIRECT';

/** `payment.provider` codes (RAZORPAY, PAYU, PINELABS…); kept open for new providers. */
export type PaymentProviderCode = 'RAZORPAY' | 'PAYU' | 'PINELABS' | 'OFFLINE' | (string & {});

/** `PaymentStatus` enum as a string: CREATED | AUTHORIZED | CAPTURED | FAILED | REFUNDED (open for additions). */
export type PaymentStatusCode =
  'CREATED' | 'AUTHORIZED' | 'CAPTURED' | 'FAILED' | 'REFUNDED' | (string & {});

/** `RefundStatus` enum as a string. MANUAL = offline payment of a cancelled order: staff hand the money back. */
export type RefundStatusCode = 'PENDING' | 'PROCESSED' | 'FAILED' | 'MANUAL' | (string & {});

// ─── Errors ──────────────────────────────────────────────────────────────────────────────────────

/** `shared.exception.ErrorResponse` */
export interface ErrorResponse {
  code: string;
  message: string;
  details?: unknown[];
  traceId?: string;
}

/** Entry of `details` for VALIDATION_FAILED. */
export interface FieldErrorDetail {
  field: string;
  message: string;
}

/** Reasons used in `CartProblem.reason`. */
export type CartProblemReason =
  | 'ITEM_NOT_FOUND'
  | 'CATEGORY_UNAVAILABLE'
  | 'ITEM_UNAVAILABLE'
  | 'VARIANT_REQUIRED'
  | 'VARIANT_UNAVAILABLE'
  | 'ADDON_UNAVAILABLE'
  | (string & {});

/** `OrderDtos.CartProblem` — entry of `details` for ITEM_UNAVAILABLE (409). */
export interface CartProblem {
  lineIndex: number;
  itemId?: number;
  variantId?: number;
  addonId?: number;
  name?: string;
  reason: CartProblemReason;
}

/** Well-known error codes returned by the backend. */
export type ApiErrorCode =
  | 'ITEM_UNAVAILABLE'
  | 'ORDERING_CLOSED'
  | 'OUTSIDE_OPENING_HOURS'
  | 'RATE_LIMITED'
  | 'VALIDATION_FAILED'
  | 'BAD_REQUEST'
  | 'MISSING_HEADER'
  | 'NOT_FOUND'
  | 'INVALID_TABLE'
  | 'GUEST_SESSION_REQUIRED'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'CSRF_INVALID'
  | 'INVALID_CREDENTIALS'
  | 'ACCOUNT_LOCKED'
  | 'SESSION_EXPIRED'
  | 'KITCHEN_ACCOUNT'
  | 'ORDER_NOT_PAYABLE'
  | 'IDEMPOTENCY_KEY_REUSED'
  | 'ILLEGAL_TRANSITION'
  | 'PAYMENTS_NOT_CONFIGURED'
  | 'PAYMENT_PROVIDER_ERROR'
  | 'PROVIDER_NOT_IMPLEMENTED'
  | 'REFUND_FAILED'
  | 'CONCURRENT_MODIFICATION'
  | 'DUPLICATE_OR_INVALID'
  | 'FILE_TOO_LARGE'
  | 'UNSUPPORTED_IMAGE'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR'
  | 'CREDENTIALS_REQUIRED'
  | 'PIN_LOGIN_NOT_ALLOWED'
  | 'TAKEAWAY_DISABLED'
  | 'TABLE_REQUIRED'
  | 'NOT_STAFF_ORDER'
  | 'ALREADY_PAID'
  | 'PAYMENT_FLAGGED'
  | (string & {});

// ─── Shared ──────────────────────────────────────────────────────────────────────────────────────

/** `shared.web.PageResponse<T>` */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ─── Auth (`auth.dto.AuthDtos`) ──────────────────────────────────────────────────────────────────

/** Either `password` or `pin` (WAITER accounts only) must be provided. */
export interface LoginRequest {
  username: string;
  password?: string;
  pin?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** Either `password` or `pin` must be provided. */
export interface KitchenDeviceRequest {
  username: string;
  password?: string;
  pin?: string;
  deviceName?: string;
}

export interface StaffInfo {
  id: number;
  username: string;
  displayName?: string;
  role: StaffRole;
  mustChangePassword: boolean;
  /** Can use `/platform` (onboard/list restaurants) with this login, no shared key needed. */
  platformAdmin: boolean;
  /** This account's own restaurant (the JWT's `rid` claim) — for building restaurant-scoped realtime topics. */
  restaurantId: number;
}

export interface TokenResponse {
  accessToken: string;
  /** Seconds. */
  expiresIn: number;
  user: StaffInfo;
}

export interface DeviceTokenResponse {
  deviceToken: string;
  expiresAt: IsoInstant;
  user: StaffInfo;
}

export interface MeResponse {
  user: StaffInfo;
  device: boolean;
}

// ─── Settings (`settings.dto.SettingsDtos`) ──────────────────────────────────────────────────────

export interface SettingsResponse {
  name: string;
  address?: string;
  phone?: string;
  gstin?: string;
  fssaiNo?: string;
  logoImageId?: number;
  logoUrl?: string;
  acceptingOrders: boolean;
  pricesIncludeGst: boolean;
  openingTime?: IsoLocalTime;
  closingTime?: IsoLocalTime;
  currency?: string;
  brandColor?: string;
  kitchenWarnMinutes: number;
  kitchenAlertMinutes: number;
  readyAutoHideMinutes: number;
  takeawayEnabled?: boolean;
}

export interface UpdateSettingsRequest {
  name: string;
  address?: string | null;
  phone?: string | null;
  /** `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$` or empty. */
  gstin?: string | null;
  /** 14 digits or empty. */
  fssaiNo?: string | null;
  logoImageId?: number | null;
  acceptingOrders: boolean;
  pricesIncludeGst: boolean;
  openingTime?: IsoLocalTime | null;
  closingTime?: IsoLocalTime | null;
  /** `#RRGGBB` */
  brandColor?: string | null;
  kitchenWarnMinutes: number;
  kitchenAlertMinutes: number;
  readyAutoHideMinutes: number;
  /** Null/omitted keeps the current value. */
  takeawayEnabled?: boolean | null;
}

/** What guests see: branding and whether ordering is possible right now. */
export interface PublicRestaurantInfo {
  name: string;
  address?: string;
  phone?: string;
  gstin?: string;
  fssaiNo?: string;
  logoUrl?: string;
  brandColor?: string;
  acceptingOrders: boolean;
  openNow: boolean;
  openingTime?: IsoLocalTime;
  closingTime?: IsoLocalTime;
  pricesIncludeGst: boolean;
  /** Whether guests may choose TAKEAWAY (missing on older backends = no). */
  takeawayEnabled?: boolean;
}

// ─── Guest session (`guest.PublicSessionController`) ─────────────────────────────────────────────

export interface TableInfo {
  id: number;
  label: string;
}

export interface SessionResponse {
  table: TableInfo;
  restaurant: PublicRestaurantInfo;
  expiresAt: IsoInstant;
}

// ─── Public menu (`menu.dto.PublicMenuDtos`) ─────────────────────────────────────────────────────

export interface MenuResponse {
  version: string;
  pricesIncludeGst: boolean;
  categories: MenuCategory[];
}

export interface MenuCategory {
  id: number;
  name: string;
  description?: string;
  imageUrl?: string;
  thumbUrl?: string;
  items: MenuItem[];
}

export interface MenuItem {
  id: number;
  name: string;
  description?: string;
  foodType: FoodType;
  basePrice?: Money;
  /** Base price, or the lowest/default variant price for items with variants. */
  displayPrice?: Money;
  gstPercent: Money;
  available: boolean;
  imageUrl?: string;
  thumbUrl?: string;
  variants: MenuVariant[];
  addons: MenuAddon[];
}

export interface MenuVariant {
  id: number;
  name: string;
  price: Money;
  isDefault: boolean;
}

export interface MenuAddon {
  id: number;
  name: string;
  price: Money;
}

// ─── Menu admin (`menu.dto.MenuAdminDtos`) ───────────────────────────────────────────────────────

export interface CategoryRequest {
  name: string;
  description?: string | null;
  imageId?: number | null;
  active?: boolean | null;
  /** One of this restaurant's own kitchen stations, or null/omitted to leave unassigned. */
  stationId?: number | null;
}

export interface CategoryResponse {
  id: number;
  name: string;
  description?: string;
  imageId?: number;
  imageUrl?: string;
  thumbUrl?: string;
  displayOrder: number;
  active: boolean;
  itemCount: number;
  stationId?: number;
  stationName?: string;
}

// ─── Kitchen stations (`menu.dto.KitchenStationDtos`) ────────────────────────────────────────────

export interface KitchenStationRequest {
  name: string;
  active?: boolean | null;
}

export interface KitchenStationResponse {
  id: number;
  name: string;
  displayOrder: number;
  active: boolean;
}

export interface StatusRequest {
  active: boolean;
}

export interface ReorderRequest {
  ids: number[];
}

export interface VariantRequest {
  id?: number | null;
  name: string;
  price: Money;
  isDefault: boolean;
}

export interface AddonRequest {
  id?: number | null;
  name: string;
  price: Money;
}

export interface ItemRequest {
  categoryId: number;
  name: string;
  description?: string | null;
  imageId?: number | null;
  basePrice?: Money | null;
  foodType: FoodType;
  /** 0.00–28.00 */
  gstPercent: Money;
  available?: boolean | null;
  variants?: VariantRequest[];
  addons?: AddonRequest[];
  /** Optimistic-lock version from the last read; null/omitted on create. */
  version?: number | null;
}

export interface VariantResponse {
  id: number;
  name: string;
  price: Money;
  isDefault: boolean;
}

export interface AddonResponse {
  id: number;
  name: string;
  price: Money;
}

export interface ItemResponse {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  description?: string;
  imageId?: number;
  imageUrl?: string;
  thumbUrl?: string;
  basePrice?: Money;
  foodType: FoodType;
  gstPercent: Money;
  available: boolean;
  active: boolean;
  displayOrder: number;
  version: number;
  displayPrice?: Money;
  hasVariants: boolean;
  variants: VariantResponse[];
  addons: AddonResponse[];
}

export interface ItemSearchParams {
  categoryId?: number;
  q?: string;
  available?: boolean;
  page?: number;
  size?: number;
}

export interface AvailabilityRequest {
  available: boolean;
}

export interface VariantPrice {
  id: number;
  price: Money;
}

/** Quick inline rate edit: either the base price or specific variant prices. */
export interface PriceRequest {
  basePrice?: Money | null;
  variants?: VariantPrice[];
  version?: number | null;
}

// ─── Images (`image.ImageService.UploadResult`) ──────────────────────────────────────────────────

export interface UploadResult {
  imageId: number;
  url: string;
  thumbUrl: string;
  width: number;
  height: number;
}

// ─── Tables (`table.dto.TableDtos`) ──────────────────────────────────────────────────────────────

export interface TableRequest {
  /** `^[A-Za-z0-9 _-]{1,20}$` */
  label: string;
  active?: boolean | null;
}

export interface TableResponse {
  id: number;
  label: string;
  active: boolean;
  /** Has an unfinished order (paid or not) right now — the floor-view "occupied" indicator. */
  occupied: boolean;
  reserved: boolean;
  reservedUntil?: IsoInstant;
  reservedNote?: string;
  qrUrl: string;
  qrImageUrl: string;
  createdAt: IsoInstant;
  updatedAt: IsoInstant;
}

export interface ReserveTableRequest {
  /** Must be in the future. */
  until: IsoInstant;
  note?: string | null;
}

export interface MoveOrderRequest {
  tableId: number;
}

export interface MergeTablesRequest {
  fromTableId: number;
  toTableId: number;
}

export interface SplitOrderRequest {
  /** Every item id from the order, partitioned into 2+ groups (one new order per group). */
  itemGroups: number[][];
}

// ─── Staff & devices (`staff.dto`) ───────────────────────────────────────────────────────────────

export interface CreateStaffRequest {
  username: string;
  displayName?: string | null;
  role: StaffRole;
  password: string;
  /** 4–6 digits. */
  pin?: string | null;
  /** ≤ 120 chars, valid e-mail. */
  email?: string | null;
  /** 10 digits or empty. */
  phone?: string | null;
}

export interface UpdateStaffRequest {
  displayName?: string | null;
  role: StaffRole;
  active: boolean;
  newPassword?: string | null;
  pin?: string | null;
  clearPin: boolean;
  /** Blank/null clears it. */
  email?: string | null;
  /** Blank/null clears it. */
  phone?: string | null;
}

export interface StaffResponse {
  id: number;
  username: string;
  displayName?: string;
  role: StaffRole;
  active: boolean;
  mustChangePassword: boolean;
  hasPin: boolean;
  lastLoginAt?: IsoInstant;
  lockedUntil?: IsoInstant;
  createdAt: IsoInstant;
  email?: string;
  phone?: string;
}

export interface DeviceResponse {
  id: number;
  deviceName?: string;
  username: string;
  createdAt: IsoInstant;
  lastSeenAt?: IsoInstant;
  expiresAt: IsoInstant;
  revokedAt?: IsoInstant;
  active: boolean;
  applicationVersion?: string;
}

// ─── Orders (`order.dto.OrderDtos`) ──────────────────────────────────────────────────────────────

/** Only identifiers and quantities: prices are always recomputed on the server. */
export interface CartLineRequest {
  itemId: number;
  variantId?: number | null;
  addonIds?: number[];
  /** 1–50 */
  quantity: number;
  /** ≤ 200 chars */
  notes?: string | null;
}

export interface PlaceOrderRequest {
  /** 1–50 lines */
  items: CartLineRequest[];
  /** ≤ 300 chars */
  notes?: string | null;
  /** ≤ 60 chars */
  customerName?: string | null;
  /** `^$|^[6-9]\d{9}$` */
  customerPhone?: string | null;
  /** Optional; the server defaults to DINE_IN (400 TAKEAWAY_DISABLED when takeaway is off). */
  orderType?: OrderType;
}

/** Staff-assisted order (`POST /api/v1/waiter/orders`, `POST /api/v1/admin/orders`). */
export interface StaffPlaceOrderRequest {
  /** Required for DINE_IN (400 TABLE_REQUIRED), optional for TAKEAWAY. */
  tableId: number | null;
  orderType: OrderType;
  /** 1–50 lines, same shape as the guest cart. */
  items: CartLineRequest[];
  /** ≤ 300 chars (the backend also accepts `notes`). */
  note: string | null;
  customerName: string | null;
  customerPhone: string | null;
  paymentMethod: StaffPaymentMethod;
  /** `^[A-Za-z0-9_-]{8,64}$`; a replay returns the same order. */
  idempotencyKey: string;
}

export interface MarkPaidOfflineRequest {
  method: OfflinePaymentMethod;
}

export interface AddonView {
  name: string;
  price: Money;
}

export interface OrderLineView {
  /** This order line's own row id (not the menu item id) — what SplitOrderRequest groups by. */
  id?: number;
  itemId?: number;
  variantId?: number;
  name: string;
  variantName?: string;
  foodType?: FoodType;
  addons: AddonView[];
  unitPrice: Money;
  quantity: number;
  gstPercent: Money;
  lineTotal: Money;
  taxAmount?: Money;
  notes?: string;
}

export interface BillView {
  subtotal: Money;
  taxTotal: Money;
  cgst: Money;
  sgst: Money;
  grandTotal: Money;
  pricesIncludeGst: boolean;
}

export interface PaymentView {
  provider?: PaymentProviderCode;
  status?: PaymentStatusCode;
  method?: string;
  providerPaymentId?: string;
  providerOrderId?: string;
  amountPaise?: number;
  failureReason?: string;
  refundStatus?: RefundStatusCode;
  providerRefundId?: string;
  /** Staff member who recorded an OFFLINE payment. */
  recordedByStaffId?: number;
  updatedAt?: IsoInstant;
}

export interface GuestOrderView {
  id: number;
  orderNumber: string;
  displayToken: number;
  status: OrderStatus;
  orderType?: OrderType;
  tableLabel?: string;
  customerName?: string;
  notes?: string;
  items: OrderLineView[];
  bill: BillView;
  payment?: PaymentView;
  canRetryPayment: boolean;
  placedAt?: IsoInstant;
  paidAt?: IsoInstant;
  preparingAt?: IsoInstant;
  readyAt?: IsoInstant;
  completedAt?: IsoInstant;
  cancelledAt?: IsoInstant;
}

export interface GuestOrderSummary {
  id: number;
  orderNumber: string;
  displayToken: number;
  status: OrderStatus;
  orderType?: OrderType;
  grandTotal: Money;
  itemCount: number;
  placedAt?: IsoInstant;
}

export interface KitchenLineView {
  name: string;
  variantName?: string;
  foodType?: FoodType;
  addons: string[];
  quantity: number;
  notes?: string;
  stationId?: number;
  stationName?: string;
}

export interface KitchenOrderView {
  id: number;
  orderNumber: string;
  displayToken: number;
  status: OrderStatus;
  orderType?: OrderType;
  tableId?: number;
  tableLabel?: string;
  notes?: string;
  items: KitchenLineView[];
  /** Taken by a waiter or at the counter. */
  placedByStaff?: boolean;
  /** Staff flagged this order for the kitchen to work first. */
  priority?: boolean;
  paidAt?: IsoInstant;
  preparingAt?: IsoInstant;
  readyAt?: IsoInstant;
}

/** `order.KitchenController.KitchenConfig` */
export interface KitchenConfig {
  restaurantName: string;
  warnMinutes: number;
  alertMinutes: number;
  readyAutoHideMinutes: number;
}

export interface AdminOrderSummary {
  id: number;
  orderNumber: string;
  displayToken: number;
  status: OrderStatus;
  orderType?: OrderType;
  tableLabel?: string;
  customerName?: string;
  grandTotal: Money;
  itemCount: number;
  /** Gateway code or OFFLINE (then `paymentMethod` is CASH, UPI_AT_COUNTER or CARD_AT_COUNTER). */
  paymentProvider?: PaymentProviderCode;
  paymentMethod?: string;
  paymentFlagged: boolean;
  placedByStaffId?: number;
  placedAt?: IsoInstant;
  paidAt?: IsoInstant;
}

export interface AdminOrderView {
  id: number;
  orderNumber: string;
  displayToken: number;
  status: OrderStatus;
  tableLabel?: string;
  customerName?: string;
  customerPhone?: string;
  notes?: string;
  items: OrderLineView[];
  bill: BillView;
  payments: PaymentView[];
  paymentFlagged: boolean;
  flagReason?: string;
  cancelReason?: string;
  orderType?: OrderType;
  placedByStaffId?: number;
  placedByStaffName?: string;
  /** Cancelled after an OFFLINE payment: the money has to be handed back (refundStatus MANUAL). */
  manualRefundDue?: boolean;
  placedAt?: IsoInstant;
  paidAt?: IsoInstant;
  preparingAt?: IsoInstant;
  readyAt?: IsoInstant;
  completedAt?: IsoInstant;
  cancelledAt?: IsoInstant;
  /** This order's own restaurant (not whichever restaurant the viewer's session is currently pointed at) — for the printed receipt header. */
  restaurantName?: string;
  restaurantAddress?: string;
}

export interface AdminOrderSearchParams {
  status?: OrderStatus[];
  orderType?: OrderType;
  /** IST business date `YYYY-MM-DD`. */
  date?: IsoLocalDate;
  q?: string;
  page?: number;
  size?: number;
}

export interface StatusChangeRequest {
  status: OrderStatus;
}

export interface CancelRequest {
  /** ≤ 300 chars */
  reason?: string | null;
}

// ─── Reports (`report.dto.ReportDtos`) ───────────────────────────────────────────────────────────

export interface MethodSplit {
  method?: string;
  count: number;
  amount: Money;
}

/** Paid orders per channel: ONLINE or an offline method (CASH, UPI_AT_COUNTER, CARD_AT_COUNTER). */
export interface PaymentChannelSplit {
  channel: StaffPaymentMethod | (string & {});
  count: number;
  amount: Money;
}

export interface OrderTypeSplit {
  orderType: OrderType | (string & {});
  count: number;
  amount: Money;
}

export interface TopItem {
  name: string;
  quantity: number;
  revenue: Money;
}

export interface DailyPoint {
  date: IsoLocalDate;
  orders: number;
  gross: Money;
}

export interface SalesSummary {
  from: IsoLocalDate;
  to: IsoLocalDate;
  ordersCount: number;
  gross: Money;
  tax: Money;
  cgst: Money;
  sgst: Money;
  /** Revenue excluding GST. */
  net: Money;
  averageOrderValue: Money;
  cancelledCount: number;
  refundedAmount: Money;
  /** Offline money of cancelled orders that staff handed back. */
  manualRefundAmount?: Money;
  paymentMethods: MethodSplit[];
  paymentChannels?: PaymentChannelSplit[];
  orderTypes?: OrderTypeSplit[];
  topItems: TopItem[];
  daily: DailyPoint[];
  waiterPerformance?: WaiterPerformance[];
}

/** One staff member who placed orders on a guest's behalf (waiter/counter), ranked by revenue. */
export interface WaiterPerformance {
  staffId: number;
  staffName: string;
  ordersCount: number;
  revenue: Money;
}

export interface Dashboard {
  date: IsoLocalDate;
  ordersToday: number;
  revenueToday: Money;
  averageOrderValue: Money;
  ordersByStatus: Partial<Record<OrderStatus, number>>;
  activeKitchenOrders: number;
  flaggedOrders: number;
  recentOrders: AdminOrderSummary[];
}

// ─── Payments (`payment.dto.PaymentDtos`) ────────────────────────────────────────────────────────

/** Razorpay Checkout.js options as sent by the backend for `mode === 'SDK'` (RAZORPAY). */
export interface RazorpayCheckoutPayload {
  key: string;
  order_id: string;
  amount: number;
  currency: string;
  name: string;
  description?: string;
  prefill?: { name?: string; contact?: string; email?: string };
  theme?: { color?: string };
  scriptUrl: string;
  [extra: string]: unknown;
}

/** `mode === 'FORM_POST'` payload (e.g. PayU hosted checkout). */
export interface FormPostCheckoutPayload {
  action: string;
  method?: string;
  fields: Record<string, string | number>;
}

/** `mode === 'REDIRECT'` payload. */
export interface RedirectCheckoutPayload {
  url: string;
}

/**
 * What the browser needs to start paying. When `status !== 'PENDING_PAYMENT'` (idempotent replay after payment)
 * the provider fields (`provider`, `mode`, `checkout`, `amountPaise`, `currency`) are omitted.
 */
export interface CheckoutResponse {
  orderId: number;
  orderNumber: string;
  displayToken: number;
  status: OrderStatus;
  provider?: PaymentProviderCode;
  mode?: CheckoutMode;
  checkout?: Record<string, unknown>;
  amountPaise?: number;
  currency?: string;
  restaurantName?: string;
}

/** Body for `POST /api/v1/public/payments/verify` (Razorpay success handler response, or provider fields). */
export type PaymentVerifyRequest = Record<string, string>;

export interface RazorpaySuccessResponse {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

// ─── Realtime (`realtime.RealtimeEvent`) ─────────────────────────────────────────────────────────

export type RealtimeEventType =
  | 'ORDER_CONFIRMED'
  | 'ORDER_STATUS_CHANGED'
  | 'ORDER_CANCELLED'
  | 'ORDER_PRIORITY_CHANGED'
  | 'MENU_UPDATED';

export interface RealtimeEvent<TOrder = unknown> {
  type: RealtimeEventType;
  orderId?: number;
  status?: OrderStatus;
  /** Full kitchen payload (`KitchenOrderView`) for ORDER_CONFIRMED. */
  order?: TOrder;
  at: IsoInstant;
}

export type KitchenRealtimeEvent = RealtimeEvent<KitchenOrderView>;

export const TOPICS = {
  /** Restaurant-scoped: the backend rejects a subscription for any restaurant but the caller's own. */
  kitchenOrders: (restaurantId: number) => `/topic/kitchen/${restaurantId}/orders`,
  waiterNotifications: (restaurantId: number) => `/topic/waiter/${restaurantId}/notifications`,
  menu: '/topic/menu',
  order: (orderId: number) => `/topic/orders/${orderId}`,
  /** Public, login-free: the customer-facing order-ready display board. */
  display: (restaurantId: number) => `/topic/display/${restaurantId}`,
} as const;

/** One PREPARING/READY/COMPLETED/CANCELLED transition, pushed to `TOPICS.display`. */
export interface DisplayEvent {
  displayToken: number;
  status: OrderStatus;
}

/** `GET /api/v1/public/display` — the display board's initial snapshot. */
export interface DisplayBoardView {
  preparing: number[];
  ready: number[];
}

// ─── Admin notifications (`notification.dto.NotificationDtos`) ──────────────────────────────────

/** `NotificationEvent` enum as a string (open for additions). */
export type NotificationEventCode =
  | 'ORDER_CONFIRMED'
  | 'ORDER_READY'
  | 'ORDER_CANCELLED'
  | 'PAYMENT_FLAGGED'
  | 'REFUND_FAILED'
  | (string & {});

export type NotificationSeverity = 'INFO' | 'HIGH' | (string & {});

/** In-app notification of the staff inbox. `link` is app-relative, e.g. `/admin/orders/42`. */
export interface NotificationView {
  id: number;
  event: NotificationEventCode;
  title: string;
  body?: string;
  link?: string;
  severity: NotificationSeverity;
  orderId?: number;
  read: boolean;
  createdAt: IsoInstant;
}

export interface UnreadCount {
  count: number;
}

/** `InAppAudience`: every user of a role (`recipient` = role name) or one user (`recipient` = user id). */
export type NotificationAudience = 'STAFF_ROLE' | 'STAFF_USER' | (string & {});

/** Message on `/topic/staff/notifications` (shared by owners and managers — filter by audience/recipient). */
export interface StaffNotificationMessage {
  type: 'NOTIFICATION' | (string & {});
  audience: NotificationAudience;
  recipient: string;
  notification: NotificationView;
}

export interface NotificationSearchParams {
  unreadOnly?: boolean;
  page?: number;
  size?: number;
}

export const ADMIN_TOPICS = {
  staffNotifications: (restaurantId: number) => `/topic/staff/${restaurantId}/notifications`,
} as const;

// ─── Waiter screen (`order.WaiterController`) ────────────────────────────────────────────────────

/** `GET /api/v1/waiter/config` */
export interface WaiterConfig {
  restaurantName: string;
  currency?: string;
  acceptingOrders: boolean;
  openNow: boolean;
  takeawayEnabled: boolean;
  pricesIncludeGst: boolean;
  /** Whether `paymentMethod: ONLINE` can be offered (a gateway is configured). */
  onlinePaymentsAvailable: boolean;
  kitchenWarnMinutes: number;
  kitchenAlertMinutes: number;
  staff: StaffInfo;
}

// ─── Platform onboarding (`restaurant.PlatformOnboardingController`) ─────────────────────────────
// Key-authenticated (`X-Platform-Admin-Key`), not JWT/cookie: see core/api/platform.api.ts.

export type RestaurantStatus = 'ACTIVE' | 'SUSPENDED';

/**
 * `POST /api/v1/platform/restaurants` request; `slug` is derived from `restaurantName` when omitted. Every field
 * past `slug` is optional and mirrors what the owner would otherwise set up themselves at `/admin/settings` or
 * `/admin/staff` after their first login — left blank, `RestaurantSettingsEntity`'s own defaults apply.
 */
export interface OnboardRestaurantRequest {
  restaurantName: string;
  slug?: string | null;
  address?: string | null;
  phone?: string | null;
  gstin?: string | null;
  fssaiNo?: string | null;
  pricesIncludeGst?: boolean | null;
  openingTime?: IsoLocalTime | null;
  closingTime?: IsoLocalTime | null;
  brandColor?: string | null;
  takeawayEnabled?: boolean | null;
  ownerDisplayName?: string | null;
  /** Blank derives `{slug}.owner`, matching the previous behaviour. */
  ownerUsername?: string | null;
  ownerEmail?: string | null;
  ownerPhone?: string | null;
  /** Blank auto-generates one, matching the previous behaviour. */
  ownerPassword?: string | null;
}

/** `temporaryPassword` is returned once, here, and never shown again — hand it to the merchant out of band. */
export interface OnboardRestaurantResponse {
  restaurantId: number;
  restaurantName: string;
  slug: string;
  ownerUsername: string;
  temporaryPassword: string;
}

export interface OwnerSummary {
  id: number;
  username: string;
  displayName?: string;
  active: boolean;
}

/** `GET /api/v1/platform/restaurants` list item. */
export interface RestaurantSummary {
  id: number;
  name: string;
  slug: string;
  status: RestaurantStatus;
  createdAt: IsoInstant;
  owners: OwnerSummary[];
}

/** One active table with counts of its open (paid, not yet served) orders. */
export interface WaiterTableView {
  id: number;
  label: string;
  openOrders: number;
  confirmed: number;
  preparing: number;
  ready: number;
  oldestOpenSince?: IsoInstant;
}
