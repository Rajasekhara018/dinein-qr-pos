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

## Payments are provider-agnostic
- `payment.gateway.PaymentGateway` is an SPI implemented by `RazorpayPaymentGateway` (complete),
  `PayuPaymentGateway` (hosted checkout + hash verification + callback + verify_payment + refund) and
  `PineLabsPaymentGateway` (registered skeleton, reports "not configured" until implemented).
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
  `ready_auto_hide_minutes`.

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
  guests are identified by their cookie at handshake. Topics: `/topic/kitchen/orders` (staff),
  `/topic/orders/{id}` (owning guest, owner/manager), `/topic/menu` (anyone). Clients cannot SEND.
- Event body: `{type, orderId, status, order, at}`; `type` ∈ `ORDER_CONFIRMED` (with kitchen order payload),
  `ORDER_STATUS_CHANGED`, `ORDER_CANCELLED`, `MENU_UPDATED`.

## Images
- Uploads are re-encoded to JPEG (opaque) or PNG (with alpha) — WEBP is accepted as input but not produced, because
  ImageIO has no WEBP writer. Dedupe by SHA-256 of the processed main image.
