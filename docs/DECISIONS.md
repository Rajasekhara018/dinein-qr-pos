# Design decisions & deviations from the original brief

The original brief is in [REQUIREMENTS.md](REQUIREMENTS.md). Where this project deliberately differs, or where the
brief left a choice open, it is recorded here.

## Build & structure
- **Gradle** (Groovy DSL, wrapper 8.14.3) instead of Maven, to match the reference `gateway-backend` project.
- Backend package root `com.heuristq.dinein`, feature-first packages (`auth`, `staff`, `image`, `menu`, `table`,
  `guest`, `order`, `payment`, `settings`, `realtime`, `report`, `notification`) plus `shared/*` infrastructure.
- IDs are `BIGSERIAL` / `Long` everywhere.

## Frontend uses NgModules (`standalone: false`)
- At the owner's request the Angular app is NgModule-based, like the reference `gateway-frontend`: `AppModule` +
  `AppRoutingModule`, lazy feature modules (`GuestModule`, `KitchenModule`, `AdminModule`) each with a
  `*-routing-module.ts`, and a `SharedModule` exporting the UI kit. This overrides the brief's "standalone components
  only". Signals, zoneless change detection, OnPush, built-in control flow, functional guards/interceptors and lazy
  loading are kept.
- Every component is split into `name.ts` / `name.html` / `name.css` (`templateUrl` + `styleUrl`, no inline templates
  or styles); `angular.json` schematics are set so `ng generate component` follows the same convention.

## Payments are provider-agnostic
- `payment.gateway.PaymentGateway` is an SPI implemented by `RazorpayPaymentGateway` (complete),
  `PayuPaymentGateway` (hosted checkout + hash verification + callback + verify_payment + refund) and
  `PineLabsPaymentGateway` (Pine Labs Online / Plural hosted checkout, `REDIRECT` mode; see below).
- `app.payments.provider` selects the gateway for **new** payments. Every `payment` row stores its `provider`, so
  webhooks, refunds and reconciliation always use the gateway that took the payment.
- Schema uses generic columns: `payment.provider`, `provider_order_id`, `provider_payment_id`, `provider_refund_id`
  (unique per provider) instead of `razorpay_*`; `payment_event.provider` + `provider_event_id`.
- `POST /api/public/orders` and `POST /api/public/orders/{id}/retry-payment` return a `CheckoutResponse`:
  `{orderId, orderNumber, displayToken, status, provider, mode, checkout, amountPaise, currency, restaurantName}`
  where `mode` is:
  - `SDK` – `checkout` holds Razorpay Checkout.js options (`key`, `order_id`, `amount`, `currency`, `name`,
    `description`, `prefill`, `theme`, `scriptUrl`). On success the client posts the handler response to
    `POST /api/public/payments/verify`.
  - `FORM_POST` – `checkout = {action, method, fields}`; the client auto-submits a form (PayU). PayU posts back to
    `POST /api/public/payments/{provider}/callback`, which verifies and 303-redirects to `/menu/orders/{id}?payment=return|failed`.
  - `REDIRECT` – `checkout = {url}`; the client navigates there.
  - If `status != PENDING_PAYMENT` (idempotent replay after payment) provider fields are null → go to the order page.
- Webhooks: `POST /api/webhooks/{provider}` (e.g. `/api/webhooks/razorpay`, `/api/webhooks/payu`).
- A failed attempt keeps the order in `PENDING_PAYMENT` so the guest can retry; the expiry job (15 min) reconciles
  with the provider and then sets `PAYMENT_FAILED` (a failed attempt exists) or `EXPIRED`.
- Amount mismatch or a second captured payment sets `orders.payment_flagged` + `flag_reason`; the order is not sent to
  the kitchen and the expiry job skips it (admin resolves it manually).

## Pine Labs Online (Plural)
- Built from the public Pine Labs Online API reference and cross-checked against the owner's production Pine Labs
  integration (same token, checkout and status endpoints, base URLs, `integration_mode=REDIRECT`,
  `allowed_payment_methods`, and an unsigned callback). Endpoints: token `POST /api/auth/v1/token` (`expires_at`), hosted
  checkout `POST /api/checkout/v1/orders`, status `GET /api/pay/v1/orders/{order_id}`, refund
  `POST /api/pay/v1/refunds/{order_id}`, webhook events and the signature scheme. Every request sends `Request-ID`
  and `Request-Timestamp`. UAT `https://pluraluat.v2.pinepg.in`, live `https://api.pluralpay.in`.
- The token is cached in memory and refreshed 60 s before `expires_at` (or at half its lifetime if shorter). A `401`
  drops the token and retries once.
- The `redirect_url` is cached in memory per Pine Labs order id so a retry of an open attempt reuses the same link.
  After a restart it is recovered by re-posting the order's original `merchant_order_reference`, which the checkout
  API treats as an idempotency key.
- `reusableAfterFailure() == false`: after a recorded failure the retry gets a new Pine Labs order.
- The callback redirect is unsigned (confirmed by the production integration) and never trusted. Our `callback_url`
  carries `?dinein_order=<our order id>` because the redirect may not include Pine Labs' `order_id`. Either id is
  only a lookup key: status, amount and payment id always come from the order status API. It accepts GET/query and form POST, which the
  existing callback route and its CSRF exclusion already cover.
- Responses are read in documented snake_case with camelCase fallbacks (`orderId`, `redirectUrl`, `paymentMethod`),
  which the production integration has seen from the sandbox. A payment without `payment_amount` uses the order's
  `order_amount.value` / `amount`, so a genuine capture is never mis-read as 0 and flagged as a mismatch.
- Status mapping: payment `PROCESSED` → captured, `AUTHORIZED` → authorized, `FAILED`/`CANCELLED` → failed, anything
  else → pending. An order closed as `FAILED`/`CANCELLED` with no failed attempt counts as one failure for the expiry
  job.
- Webhooks: HMAC-SHA256 with the base64-decoded secret over `webhook-id.webhook-timestamp.rawBody`, header
  `v1,<base64>` (several space-separated signatures are accepted). The check uses constant-time comparison and a
  5-minute timestamp tolerance, and `webhook-id` is the de-duplication key. `ORDER_PROCESSED` → captured;
  `ORDER_FAILED`/`ORDER_CANCELLED`/`PAYMENT_FAILED` → failed; `REFUND_PROCESSED`/`REFUND_FAILED` → refund events. The
  refund event's charged payment id is read from `parent_order_id` through the status API, because the refund
  payload only carries refund-order payments.
- Refunds use `merchant_order_reference = refund-<orderId>-<random>`. A retry after a *failed* refund is therefore not
  answered with the old failed refund. A second full refund would be refused by Pine Labs because nothing refundable
  is left.
- CSP unchanged: Pine Labs is a top-level navigation, not a frame or form target.
- **VERIFY with a UAT account** (marked `// VERIFY:` in code):
  1. ~~Callback signature~~ — resolved: the redirect is unsigned; security relies on the status API check.
  2. That the idempotent replay of `POST /api/checkout/v1/orders` returns `redirect_url` (used only for link
     recovery after a restart).
  3. The webhook secret's format as shown in the dashboard (base64 per docs; a `whsec_` prefix is stripped; a
     non-base64 value falls back to raw bytes).
  4. The allowed charset of `merchant_order_reference` (we send `[A-Za-z0-9-]`, ≤ 50 chars) and whether
     `failure_callback_url` is accepted on hosted checkout (it appears in webhook payloads).

## Notifications are provider-agnostic
- Channels: EMAIL, SMS, PUSH, IN_APP, each behind a sender SPI with a registry; providers are chosen by config
  (`app.notifications.*`), with a logging provider as the default/fallback. Dispatch happens after commit and
  asynchronously so a provider failure never affects ordering.

## Extra columns beyond the brief
- `orders.version`, `item.version` (optimistic locking), `orders.payment_flagged`, `flag_reason`, `cancel_reason`,
  `preparing_at`, `prices_include_gst` (snapshot so bills render the same forever).
- `order_item.food_type`, `order_item.tax_amount`, `order_item_addon.addon_id` (snapshots / reporting).
- `staff_user.pin_hash`, `must_change_password`, `failed_login_attempts`, `locked_until`.
- `refresh_token` table (hashed, rotated, family-based reuse detection).
- `daily_order_counter` for daily tokens / order numbers `yyMMdd-NNN` (IST business date).
- `restaurant_settings.prices_include_gst`, `brand_color`, `kitchen_warn_minutes`, `kitchen_alert_minutes`,
  `ready_auto_hide_minutes`, `takeaway_enabled`.
- `orders.order_type`, `orders.placed_by_staff_id`, `payment.recorded_by_staff_id` (V3; see "Order types, waiters and
  offline payments").

## Security details
- Access JWT (HS256, 15 min) in memory on the client; refresh token in HttpOnly `dinein_rt` cookie
  (`SameSite=Strict`, path `/api/auth`), rotated on every refresh.
- Kitchen devices: `Authorization: Bearer dvc_<token>` (30 days, hashed, revocable). Devices always act as KITCHEN.
- A JWT with `pwc=true` (password change required) only grants `PASSWORD_CHANGE_REQUIRED`; the client must call
  `POST /api/auth/change-password` before anything else works.
- Guest session: stateless HMAC-signed `dinein_gs` cookie (HttpOnly, SameSite=Lax, 12 h).
- CSRF (cookie `XSRF-TOKEN` → header `X-XSRF-TOKEN`, Angular's built-in names) is enforced only on cookie-authenticated
  mutations: `/api/auth/refresh`, `/api/auth/logout`, `/api/public/**` (except provider callbacks). The SPA should
  call `GET /api/auth/csrf` once at startup if the cookie is missing.

## Realtime
- STOMP over native WebSocket at `/ws`. Staff authenticate with the `Authorization: Bearer …` STOMP CONNECT header;
  guests are identified by their cookie at handshake. Topics: `/topic/kitchen/orders` (all staff: kitchen and waiter
  screens), `/topic/orders/{id}` (owning guest, owner/manager/waiter), `/topic/staff/notifications` (owner/manager
  users), `/topic/waiter/notifications` (waiter/owner/manager users), `/topic/menu` (anyone). Clients cannot SEND.
- Event body: `{type, orderId, status, order, at}`; `type` ∈ `ORDER_CONFIRMED` (with kitchen order payload),
  `ORDER_STATUS_CHANGED`, `ORDER_CANCELLED`, `MENU_UPDATED`. `ORDER_STATUS_CHANGED` with `status = READY` also carries
  the kitchen order payload, so waiter screens can alert without a refetch.
- Waiters reuse `/topic/kitchen/orders` instead of a separate waiter topic: they need exactly the kitchen's events
  (READY above all), and one topic means one publisher path. The kitchen order payload now includes `orderType`,
  `tableId` and `placedByStaff`.

## Order types, waiters and offline payments
- `orders.order_type` is `DINE_IN` (default) or `TAKEAWAY`. Guests send an optional `orderType`; staff send it with
  their order. `restaurant_settings.takeaway_enabled` (default true) switches takeaway off for guests **and** staff
  (`400 TAKEAWAY_DISABLED`). `PUT /api/admin/settings` treats a missing `takeawayEnabled` as "unchanged" so older
  clients cannot switch it off by accident. The accepting-orders switch and opening hours also apply to staff orders.
- Role `WAITER`. Waiters sign in on `POST /api/auth/login` with password **or PIN** (the kitchen's PIN idea, but a
  normal user session, not a device token: waiters are individuals and their actions are attributed to them). PIN
  sign-in on that endpoint is refused for other roles (`403 PIN_LOGIN_NOT_ALLOWED`); kitchen accounts still sign in
  only on the kitchen screen. Like kitchen accounts, waiters are not forced to change an admin-set password.
- `/api/waiter/**` is open to WAITER, MANAGER and OWNER. `GET /api/waiter/menu` returns the guest menu **including
  unavailable items** (flagged `available: false`), because the waiter has to tell the guest; ordering one is rejected
  exactly as for guests. Waiter order lists do not auto-hide READY orders (the kitchen's does): they stay until served.
- Staff-assisted orders (`POST /api/waiter/orders`, `POST /api/admin/orders`) go through the same placement core as
  guest orders (`OrderPlacementService#createOrder`: menu validation, server-side pricing, snapshots, order numbers).
  They have no guest session: `orders.guest_session_id` is now nullable and `orders.placed_by_staff_id` records the
  staff member (a CHECK requires one of the two). Guest ownership checks are null-safe (`OrderEntity#belongsToGuest`).
  The idempotency key travels in the body (`idempotencyKey`); a replay returns the same order.
- `paymentMethod: ONLINE` returns the same `CheckoutResponse` as the guest flow and expires the same way. Redirect-based
  gateways return to `/waiter/orders/{id}` instead of `/menu/orders/{id}`; SDK checkouts are verified with
  `POST /api/waiter/payments/verify`.
- Offline methods (`CASH`, `UPI_AT_COUNTER`, `CARD_AT_COUNTER`) create the order and, in the same transaction, a
  `payment` row with `provider = OFFLINE`, `method`, `recorded_by_staff_id`, which is then confirmed through
  `PaymentStateService#confirmCapture`, the path online captures use. So kitchen realtime, notifications, flags and
  order numbers behave identically. The response is a `CheckoutResponse` with `status = CONFIRMED`,
  `provider = OFFLINE`, `amountPaise`, and null `mode`/`checkout`.
- `POST /api/admin/orders/{id}/mark-paid-offline {method}` settles PENDING_PAYMENT, EXPIRED or PAYMENT_FAILED orders
  under the order row lock (`SELECT … FOR UPDATE`): `409 ALREADY_PAID` if any payment is captured, `409 PAYMENT_FLAGGED`
  for flagged orders. A later online capture of the same order is flagged "paid twice", as for two online captures.
- `OFFLINE` is deliberately **not** a `PaymentGateway` bean. `PaymentGatewayRegistry#get("OFFLINE")` always fails
  (and no gateway may claim that code), and callers branch on `PaymentEntity#isOffline()`: `RefundService` marks the
  refund `MANUAL` (new `refund_status`, money handed back in cash, `manualRefundDue` in the admin order detail),
  `PaymentExpiryJob` skips offline rows, and webhooks/verify reject the code. A no-op gateway would have made
  `OFFLINE` selectable as the active provider and reachable at `/api/webhooks/offline`.
- Waiters get an in-app "order ready" notification (`ORDER_READY` to role `WAITER`) with their own inbox endpoints
  under `/api/waiter/notifications` and topic `/topic/waiter/notifications`. Waiter push notifications are not wired
  (push registration lives under `/api/admin`).
- Reports: the summary adds `paymentChannels` (`ONLINE`, `CASH`, `UPI_AT_COUNTER`, `CARD_AT_COUNTER`), `orderTypes`
  and `manualRefundAmount`; the CSV adds `order_type`, `refund_status` and `placed_by`; the admin order list takes an
  `orderType` filter.

## Images
- Uploads are re-encoded to JPEG (opaque) or PNG (with alpha) — WEBP is accepted as input but not produced, because
  ImageIO has no WEBP writer. Dedupe by SHA-256 of the processed main image.
