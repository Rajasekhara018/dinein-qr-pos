# Rider Payment Service

One of the five microservices (Ride | Driver | Pricing | Payment | Location) that
`Rider_BookingsAPI` is being split into per the HLD's Option B — Microservices
architecture. This service owns **payments, billing and ledger** (HLD §17).

## Scope

Owns:
- The payment state machine (cash + Razorpay online payments).
- Razorpay order creation, webhook handling, refunds.
- The gateway side of customer wallet top-ups (checkout session, webhook/poll verification, gateway reference and failure reason). Balances and the ledger belong to `Rider_WalletService`.
- Driver earnings records (fare, and the platform fee / driver net taken from the ride's own fare split) and payout requests.
  This service never computes a commission: the split comes from Rider_RideService (decided by the central commission plan).
- Split-fare requests and participants.

Explicitly out of scope (owned by other services in the split):
- Driver identity/profile/status/documents (Dispatch/Driver service).
- Ride lifecycle/state machine, fare calculation formula, GPS/dispatch (Ride/Pricing/Location services).
- Customer identity/profile (a Customer/Auth service — this service treats `customerId`/`driverId` as opaque foreign ids from other services' domains).

## Owned tables

Copied verbatim from the monolith's `db/migration/V1__baseline_schema.sql`:

| Table | Purpose |
|---|---|
| `wallet_transactions` | Customer wallet ledger (CREDIT/DEBIT rows) |
| `driver_payments` | Per-ride driver earnings (gross, platform fee, net, `split_status` V25: `SPLIT`, `PENDING_SPLIT` with empty fee/net until the ride's split is known, `SPLIT_MISMATCH` for a person to check) |
| `driver_payouts` | Driver payout requests to their bank account |
| `driver_safety_deposits` | Driver safety-deposit records |
| `split_fare_requests` / `split_fare_participants` | Split-fare requests and each participant's share/status |

New tables designed for this service (HLD §17.1/§17.2/§18 — the monolith has no
dedicated payment-state-machine table, it conflates "payment" with the cash-flow
tables above):

| Table | Purpose |
|---|---|
| `payments` | The payment-state-machine record: `internal_payment_id`, `provider_payment_id`, `provider_order_id`, `ride_id`, `customer_id`, `driver_id`, `amount`, `currency`, `status`, `method`, `idempotency_key` (unique), `failure_reason`, `invoice_number`, `receipt_number`, `receipt_generated_at`, `settled_at` (V2), `created_at`, `updated_at`, `version`. |
| `payment_webhook_events` | Webhook idempotency ledger — one row per processed provider event id, unique-indexed. |
| `driver_incentives` (V2) | Ported from Rider_BookingsAPI's `driver.incentive` package: `id`, `driver_id`, `rule_name`, `description`, `bonus_amount`, `week_start_date`, `awarded_at`, `status`. Unique on `(driver_id, rule_name, week_start_date)` so a rule is only ever awarded once per driver per week. |
| `shedlock` (V2) | ShedLock's own lock table, required by `shedlock-provider-jdbc-template` (guards `SettlementScheduler`). |

## Payment state machine (HLD §17.2)

```
INITIATED -> AUTHORIZED -> CAPTURED -> SETTLED
     \-> FAILED / CANCELLED / EXPIRED
CAPTURED/SETTLED -> REFUNDED / PARTIALLY_REFUNDED / REVERSED
```

This pass drives: `INITIATED` (on `POST /payments`) -> `CAPTURED` (CASH:
synchronously; RAZORPAY: only from the webhook, never from the client's own
response — see HLD §23.3 below) -> `REFUNDED`/`PARTIALLY_REFUNDED` (on
`POST /payments/{id}/refund`), or `FAILED` (from a `payment.failed` webhook).
`AUTHORIZED`, `CANCELLED`, `EXPIRED`, `REVERSED` are modeled in the enum for the
full state machine but there is no trigger for them yet in this pass.

`CAPTURED -> SETTLED` **is** now driven: `SettlementScheduler` (a `@Scheduled` +
ShedLock-guarded job, `app.settlement.check-interval-ms`, default hourly) calls
`PaymentSettlementService.settleOldCapturedPayments()`, which transitions every CAPTURED
payment whose `updated_at` is older than `app.settlement.delay-hours` (default
24h) to SETTLED and stamps `settled_at`. This is an internal bookkeeping
transition only — no external "settlement API" exists to call; a real gateway's
own settlement cycle (T+1/T+2 for Razorpay) is what this approximates.

**HLD §23.3 is enforced in code**: `PaymentService.createPayment` for the
RAZORPAY branch only ever creates a Razorpay order and returns it — it never
marks the `Payment` row CAPTURED. Only `PaymentWebhookHandler.handleWebhook`
(server-to-server, HMAC-verified) can transition a RAZORPAY payment to
CAPTURED/FAILED.

## Service structure (payment package)

`PaymentService` itself is only payment creation/retrieval — the entry point to
the state machine. Everything else that used to live there is now its own
focused collaborator, each independently testable:

| Class | Responsibility |
|---|---|
| `PaymentService` | `createPayment`/`getPayment` — cash capture, gateway checkout-session creation |
| `PaymentGatewayRouter` | Which gateway a payment/wallet-topup routes through when unspecified |
| `PaymentTransitionEffects` | Shared CAPTURED/FAILED side effects (receipt, driver earnings, Ride/NMS notification) — used by cash capture, the webhook, and the status-poll job |
| `PaymentWebhookHandler` | Razorpay ride-payment webhook (HMAC-verified, idempotent) |
| `PaymentStatusPollingService` | PAYU/TOUCAN/PINELABS status-poll job |
| `PaymentSettlementService` | CAPTURED -> SETTLED job |
| `PaymentRefundService` | Full/partial refunds |

## Multi-gateway support (PayU / Toucan / PineLabs / Razorpay)

`Payment.method` (CASH vs. online) is a separate concept from `Payment.gateway`
(`PaymentGatewayType`: RAZORPAY/PAYU/TOUCAN/PINELABS) — `method != CASH` means
"online/gateway-routed", and `gateway` selects which of the 4 providers
actually handles it. A hexagonal ports-and-adapters split
(`CreateSessionGatewayClient`/`RefundGatewayClient`/`StatusCheckGatewayClient`
ports, per-gateway adapters, `PaymentGatewayClientResolver`) means adding a
5th gateway never requires touching `PaymentService` itself.

The gateway used when a caller's `POST /payments` doesn't specify one
explicitly is controlled entirely by `app.payment.default-gateway`
(`DEFAULT_PAYMENT_GATEWAY` env var) — **no code change or redeploy** is needed
to switch the platform-wide default.

**Outcome resolution differs by gateway**: RAZORPAY stays exclusively
webhook-driven (`POST /payments/webhook`, HMAC-verified) — it is deliberately
excluded from the polling job below to avoid the two mechanisms racing on the
same payment. PAYU/TOUCAN/PINELABS have no webhook ingestion in this service,
so `PaymentStatusPollingScheduler` (a `@Scheduled` + ShedLock-guarded job,
`app.payment-polling.check-interval-ms`, default every 60s) calls
`PaymentStatusPollingService.pollPendingGatewayPayments()`, which:

- fetches every INITIATED/AUTHORIZED payment routed through PAYU/TOUCAN/PINELABS,
- transitions anything older than `app.payment-polling.max-age-hours` (default
  24h) straight to EXPIRED without calling the gateway,
- otherwise calls the gateway's `StatusCheckGatewayClient.fetchStatus(...)` and
  transitions to CAPTURED (SUCCESS) or FAILED (FAILED) via the same shared
  side-effect methods (`onPaymentCaptured`/`onPaymentFailed`) the webhook path
  uses — so receipt generation, driver-earnings computation, and the
  Ride/NMS notifications fire identically regardless of which mechanism
  resolved the payment,
- leaves a payment untouched on PENDING or if the status-check call itself
  throws (gateway unreachable) — retried on the next run, never treated as a
  failure just because we don't yet know the outcome,
- re-checks each candidate's current status immediately before applying a
  transition, so a payment some other path already resolved between being
  fetched and being processed in this loop is left alone.

See `PaymentStatusPollingServiceTest` for the captured/failed/pending/errored/
expired/already-resolved cases.

## Webhook idempotency (HLD §18)

`POST /payments/webhook`:
1. Verifies `X-Razorpay-Signature` as `HMAC-SHA256(rawBody, app.razorpay.webhook-secret)` — rejects with 400 if it doesn't match (raw body, not a re-serialized DTO, since Razorpay signs the exact bytes it sent).
2. Computes a dedupe key: the `X-Razorpay-Event-Id` header if present, else a SHA-256 digest of the raw payload.
3. Looks up that key in `payment_webhook_events` (unique-indexed on `provider_event_id`). If found, the event has already been fully processed — returns 200 immediately with **no** further state change or side effect.
4. Otherwise applies the state transition (`payment.captured`/`order.paid` -> CAPTURED, `payment.failed` -> FAILED), records the webhook event, and — only on this first-time processing — triggers driver-earnings computation and calls `RideServiceClient.notifyPaymentResult(...)`.

This means the exact same payload delivered twice (a real Razorpay retry, or a
defensive duplicate) is guaranteed to apply the transition and its side effects
exactly once. See `PaymentWebhookHandlerTest` for a test that
replays the same payload twice and asserts single application.

`DriverEarningsService.computeEarnings` is separately idempotent per ride
(`driver_payments.ride_id` lookup before insert), as a second line of defense.

## Outbound inter-service contract — Ride service

This service calls **out** to the Ride service (built in parallel by another
agent) via `RideServiceClient`, using Spring WebFlux's `WebClient` (blocking
`.block()` calls, since this service's controllers are synchronous), base URL
`${RIDE_SERVICE_URL:http://localhost:9013}/api/v1/ride`:

- `GET /rides/{rideId}` — fetches `{rideId, customerId, driverId, totalAmount, status}` before creating a payment or a split-fare request.
- `POST /rides/{rideId}/payment-result` — body `{status: "COMPLETED"|"FAILED", paymentId, finalAmount}`, called after this service's own `Payment` reaches CAPTURED/SETTLED (`status=COMPLETED`) or FAILED.

**Please reconcile this on the Ride-service side**: the exact field names/types
in `RideDetailsResponse` (`com.heuristiq.paymentservice.client.dto.RideDetailsResponse`)
and `PaymentResultNotification` are what this service currently sends/expects —
if the Ride service's actual DTOs differ, one side needs to adjust to match.

## API (`/api/v1/payment` prefix declared on each @RestController; the gateway no longer rewrites paths)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/payment/payments` | `{rideId, customerId, amount, method: CASH\|RAZORPAY, gateway?: RAZORPAY\|PAYU\|TOUCAN\|PINELABS, idempotencyKey}`. CASH -> CAPTURED + invoice immediately. Online -> creates a gateway checkout session (gateway defaults per `app.payment.default-gateway` if omitted), returns session details. Idempotent on `idempotencyKey`. |
| GET | `/api/v1/payment/payments/{paymentId}` | |
| POST | `/api/v1/payment/payments/webhook` | Razorpay webhook (public, HMAC-verified, idempotent). |
| GET | `/api/v1/payment/payments/{paymentId}/receipt` | Real receipt (HLD step 22) — total amount charged, method, receipt/invoice numbers, actual state-machine timestamps. No itemized fare breakdown (see `ReceiptResponse`'s `breakdownNote`). |
| POST | `/api/v1/payment/payments/{paymentId}/refund` | `{amount?, reason?}` — full refund if `amount` omitted. RAZORPAY payments now call Razorpay's real refund API first; CASH stays wallet-only. |
| POST | `/api/v1/payment/wallet/topup` | `{customerId, amount, idempotencyKey, gateway?}` — starts a real, gateway-verified top-up checkout session (see "Wallet top-up" below). Does **not** credit the wallet itself. |
| GET | `/api/v1/payment/wallet/topup/{topupId}` | Poll this after the checkout SDK reports success — `status` is `INITIATED` until the gateway confirms, then `PAID_AWAITING_CREDIT` until `Rider_WalletService` holds the credit, then `CAPTURED`. |
| POST | `/api/v1/payment/wallet/topup/webhook` | Razorpay webhook for top-ups (public, HMAC-verified, idempotent) — separate from `/api/v1/payment/payments/webhook`. |
| POST | `/api/v1/payment/drivers/{driverId}/payouts` | `{amount, accountHolderName, accountNumber, ifscCode, bankName?}` |
| GET | `/api/v1/payment/drivers/{driverId}/payouts` | |
| GET | `/api/v1/payment/drivers/{driverId}/earnings?rideId=` | |
| POST | `/api/v1/payment/drivers/{driverId}/incentives/calculate` | Awards any newly-eligible incentive for the current ISO week (Monday start). |
| GET | `/api/v1/payment/drivers/{driverId}/incentives` | All incentives ever awarded to this driver. |
| POST | `/api/v1/payment/split-fare` | `{rideId, initiatorUserId, members: [{phoneNumber, name}]}` |
| POST | `/api/v1/payment/split-fare/{id}/participants/{participantOrder}/pay` | `participantOrder` is the 0-based index into the participants list |
| GET | `/api/v1/payment/split-fare/{id}` | |
| GET | `/actuator/health` | Public |

## Settling the wallet sale of a gateway payment (docs/FRANCHISE_PLAN.md step 8)

The wallet books a ride, matrimony or food sale paid through the gateway as PENDING (its earnings wait). When a gateway payment
(method RAZORPAY, any gateway) is CAPTURED this service stores a SETTLE request in `sale_settlement_requests` (V26) in the same
transaction; on FAILED or EXPIRED a CANCEL request. `SaleSettlementScheduler` (ShedLock `send-sale-settlements`) sends them to
Rider_WalletService `POST /internal/sales/{eventId}/settle|cancel` through `ResilientCaller`, keys `sale-settle:<eventId>` /
`sale-cancel:<eventId>`, organization in `X-Tenant-Id`. One request per sale at a time, oldest first.

| Payment | Sale event id |
|---|---|
| ride (`sourceService` empty) | `ride-completed:<rideId>` |
| `sourceService=matrimonial-api` | `MATRIMONY:SALE:<sourceReference>` (the matrimony payment transaction id) |
| `sourceService=food-delivery-platform` (reserved; Royal Food pays through its own gateway today) | `food-order-delivered:<sourceReference>` |
| anything else, or a CASH payment | nothing (cash sales settle inside the wallet when the driver's dues are recovered) |

Answers: success or 409 `SALE_NOT_PENDING` = done; 404 on settle = the sale is booked after the capture, retried with backoff
(`rider.sale-settlement.initial-backoff` 30s doubling to `max-backoff` 1h, given up after `max-attempts` 80; the wallet's
STALE_SETTLEMENT alert keeps reporting the sale); 404 on cancel = nothing was booked, done; 400 = FAILED. A cancel is not sent
(SUPERSEDED) when the same ride or source reference has a captured payment by then, because FAILED/EXPIRED may still become
CAPTURED. Switch: `SALE_SETTLEMENT_ENABLED` (default true).

## Wallet balance: owned by Rider_WalletService

Payment keeps **no balance and no ledger of its own** for customers. `GET /wallet/{id}/balance` and
`GET /wallet/{id}/transactions` were removed; customers and `Rider_CustomerService` read balances from
`Rider_WalletService`. The old `wallet_accounts` / `wallet_transactions` tables are kept read-only for
reconciliation (nothing writes top-ups there any more; see `ops/README.md`). One exception remains and is an open
decision: a refund whose destination is WALLET (`PaymentRefundService`) still books its credit in
`wallet_transactions` until Rider_WalletService has an endpoint for refund credits.

## Wallet top-up — real gateway verification, not client-trusted

`WalletController`'s top-up endpoint used to be `POST /{customerId}/topup {amount}`, which
credited the wallet **immediately, with no payment verification at all** — a caller could top up
for free by just calling it. `WalletTopupService` replaces it with a real
INITIATED -> CAPTURED/FAILED/EXPIRED flow, backed by its own `wallet_topup_sessions` table (kept
separate from `payments` — that table's `ride_id` is `NOT NULL` and every ride-payment code path
assumes a real ride, neither of which a wallet top-up has):

1. `POST /wallet/topup` creates a checkout session via the same `PaymentGatewayClientResolver` +
   `CreateSessionGatewayClient`/`StatusCheckGatewayClient` adapters `PaymentService`/
   `PaymentStatusPollingService` use for ride payments (a short-lived, never-persisted `Payment`
   is built purely as the data-carrier those ports expect — verified against all 4 adapters that
   only amount/currency/customerId/internalPaymentId/providerOrderId/providerPaymentId are ever
   read).
2. The top-up is marked paid **only** once the gateway actually confirms:
   RAZORPAY via `POST /wallet/topup/webhook` (HMAC-verified, idempotent - same
   `RazorpayWebhookSignatureVerifier` component `PaymentWebhookHandler`'s own webhook uses);
   PAYU/TOUCAN/PINELABS via `WalletTopupStatusPollingScheduler` (mirrors
   `PaymentStatusPollingScheduler` exactly). `GET /wallet/topup/{topupId}` is what a client polls
   for that confirmation - the checkout SDK's own client-side success callback is never itself
   proof of a captured payment.
3. **Crediting the wallet.** A verified top-up is saved as `PAID_AWAITING_CREDIT` (the paid money is on record first),
   then `WalletTopupCreditService` makes ONE call to `Rider_WalletService` (`POST /internal/purchases`
   `{userId, appCode, amount, paymentId=<topupId>}`, `Idempotency-Key: PURCHASE:<topupId>`, `X-Tenant-Id` = the top-up's
   organization, correlation id forwarded, through `ResilientCaller`). On success the top-up becomes `CAPTURED`
   (the status the Customer app waits for). A repeat of the call (duplicate webhook, duplicate poll, the job, a crash in
   between) returns the first journal, so it can never credit twice.
4. **Wallet down or refusing** (503, timeout, 409 `PURCHASE_DISABLED`, any 4xx): the top-up stays
   `PAID_AWAITING_CREDIT` - never lost, never failed. `WalletTopupCreditScheduler` (ShedLock
   `retry-wallet-topup-credit`, every 30 s, at most `rider.wallet.credit-retry-batch-size` per run) retries it with a
   per-top-up backoff that doubles from 30 s up to 1 h. From `credit-alert-after-attempts` (10) failed attempts each
   attempt is logged at ERROR so monitoring raises it. A customer poll of `GET /wallet/topup/{id}` also nudges the credit
   once the backoff has run out. While pending, the response is the same JSON with `status = PAID_AWAITING_CREDIT`.
5. **`railcab.wallet.purchase-enabled` (Rider_WalletService) is OFF by default** and Wallet answers 409
   `PURCHASE_DISABLED` while it is. Payment's wiring is complete, but enabling credit purchases in each environment is a
   **finance / compliance decision that must be confirmed with the owner** (design Q1: whether INR top-ups become wallet
   credits at all, RBI PPI). Until then paid top-ups queue as `PAID_AWAITING_CREDIT` (customers have paid at the gateway
   and see no credit). The wallet app `rider.wallet.app-code` must have `enabled` and `can_issue` set in the wallet.
6. **Still a `PURCHASE`, not a `SALE` (2026-11-09).** The owner asked for credit top-ups to book as a Wallet `SALE`. It is held
   back and nothing here changed (no new switch): spending the credits already books revenue (`CONSUME` and `HOLD_CAPTURE`
   credit `SYS_APP_REVENUE`), so a `SALE` with a revenue share at top-up would count the same revenue twice, a `SALE` cannot
   give the buyer spendable credits today, and it would bypass Wallet's `purchase-enabled` compliance gate. The options and the
   recommendation are in `docs/OPERATING_MODEL_DESIGN.md` (slice 4, "Wallet credit top-ups as a SALE"). Matrimony's own
   purchases (subscriptions, credit packs, the onboarding fee) do not come through here: matrimonial-api posts them as sales
   through Rider_FranchiseService, and reverses them there when Ops refunds them.

## Configuration (`application.properties`)

| Property | Env var | Default |
|---|---|---|
| `server.port` | — | `9014` |
| `spring.datasource.url` | `DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/rider_payment` |
| `spring.datasource.username`/`password` | `DATASOURCE_USERNAME`/`DATASOURCE_PASSWORD` | `rider`/`rider` |
| `app.razorpay.key-id` | `RAZORPAY_KEY_ID` | `rzp_test_SYcoE53g3bDHZo` |
| `app.razorpay.key-secret` | `RAZORPAY_KEY_SECRET` | *(empty — required for real orders)* |
| `app.razorpay.webhook-secret` | `RAZORPAY_WEBHOOK_SECRET` | *(empty — webhook rejected until set)* |
| `app.razorpay.base-url` | — | `https://api.razorpay.com/v1` (real refund API base) |
| `app.settlement.delay-hours` | — | `24` (CAPTURED -> SETTLED threshold) |
| `app.settlement.check-interval-ms` | — | `3600000` (SettlementScheduler fixed-rate interval) |
| `app.payment.default-gateway` | `DEFAULT_PAYMENT_GATEWAY` | `RAZORPAY` — which gateway an online payment uses when the caller doesn't specify one |
| `app.payment-polling.check-interval-ms` | `PAYMENT_POLLING_INTERVAL_MS` | `60000` (PaymentStatusPollingScheduler fixed-rate interval) |
| `rider.wallet.base-url` / `rider.wallet.app-code` | `WALLET_SERVICE_URL` / `WALLET_APP_CODE` | `http://localhost:9121/api/v1/wallet/internal` / `BESTRIDE_RIDER_APP` (required, no default, in staging/prod) |
| `rider.wallet.credit-retry-*`, `rider.wallet.credit-alert-after-attempts` | `WALLET_CREDIT_RETRY_*` | interval 30 s, batch 50, backoff 30 s to 1 h, alert after 10 attempts |
| `app.payment-polling.max-age-hours` | `PAYMENT_POLLING_MAX_AGE_HOURS` | `24` (unresolved PAYU/TOUCAN/PINELABS payment -> EXPIRED threshold) |
| `payment.gateway.payu.*` / `payment.gateway.toucan.*` / `payment.gateway.pinelabs.*` | `PAYU_*`/`TOUCAN_*`/`PINELABS_*` | *(empty — credential-bearing keys are env-var-only, no literal defaults)* |
| `app.driver-split.retry-interval-ms` / `batch-size` / `window` | `DRIVER_SPLIT_RETRY_INTERVAL_MS` / `DRIVER_SPLIT_RETRY_BATCH_SIZE` / `DRIVER_SPLIT_RETRY_WINDOW` | `60000` / `50` / `P7D`. `driver_payments` always takes its fee and net from the ride's own split (Rider_RideService `GET /api/v1/internal/rides/{rideId}/split`). No split yet, a 409/404 from Ride or Ride down: the row is saved as `PENDING_SPLIT` with empty fee/net, and `DriverSplitRetryScheduler` (ShedLock `driver-payment-split-retry`) asks again; never a fixed percentage (the old `PLATFORM_FEE_PERCENT` 20% and `PAYMENT_RIDE_SPLIT_ENABLED` are removed). The driver earnings API returns `splitStatus` on each record. |
| `app.ride-service.base-url` | `RIDE_SERVICE_URL` | `http://localhost:9013` |
| `jwt.secret` | `JWT_SECRET` | dev default (must be ≥32 chars) |

Profiles: `dev` (default, local Postgres, SQL logging on), `local` (docker-compose,
Postgres reachable at hostname `postgres`), `prod` (quiet logging, everything via env vars).

## Deliberately deferred (out of scope for this pass)

- Payout disbursement automation (bank file generation / payout gateway
  integration) — `driver_payouts` stays in `PENDING` until a real operator
  drives it further. (Settlement, `CAPTURED -> SETTLED`, is no longer
  deferred — see `SettlementScheduler` above.)
- Full reconciliation job cross-checking Razorpay's own transaction reports
  against local `payments` rows (HLD §23.3 mentions internal reconciliation as
  a third leg alongside provider response + webhook — only the webhook leg is
  implemented here).
- Reconciling a refund's *asynchronous* outcome via Razorpay's `refund.processed`/
  `refund.failed` webhooks — `POST /payments/{id}/refund` now makes the real,
  synchronous `POST /v1/payments/{id}/refund` call and only updates local state
  on a successful (2xx) response, but a refund that Razorpay later reverses or
  fails asynchronously isn't fed back into this service yet (same class of gap
  as the webhook-only reconciliation above).
- Itemized fare breakdown on `GET /payments/{id}/receipt` — this service only
  has a single fare total from `RideServiceClient.getRide`, not a base/surge/tax
  breakdown (see `ReceiptResponse`'s `breakdownNote`).
- `HIGH_RATING` and `ZERO_CANCELLATION` driver-incentive rules (present in
  Rider_BookingsAPI's `driver.incentive` package) — this service has no driver
  rating or ride-cancellation data source, so only `TRIP_COUNT` (driven by real
  `driver_payments` rows) is ported. See `DriverIncentiveContext`'s javadoc.
- Corporate/business billing, invoicing beyond a simple cash-payment invoice number.
- `AUTHORIZED`/`REVERSED` transitions (modeled in the `PaymentStatus` enum, no trigger yet).
- Mongo persistence option (this service is Postgres/JPA-only, unlike the monolith's dual-provider setup) — deliberately trimmed per the task brief.

## Running

```bash
# Compile / test (no live Postgres/Razorpay/Ride service required)
./gradlew compileJava
./gradlew test

# Local Postgres (own docker-compose, port 5433 on host to avoid clashing with the monolith's)
docker compose up -d postgres

# Run the app (dev profile, needs Postgres reachable at localhost:5432 unless DATASOURCE_URL overridden)
DATASOURCE_URL=jdbc:postgresql://localhost:5433/rider_payment ./gradlew bootRun

# Or fully containerized
docker compose up --build
```
