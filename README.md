# DineIn — QR self-ordering for a single restaurant

Guests scan the QR code on their table, browse the menu on their phone, pay online, and follow their order live.
The kitchen sees paid orders on a screen and marks them ready. The owner manages the menu, prices, photos, tables,
staff and sales from an admin panel.

| App | URL | Who | Devices |
|---|---|---|---|
| Guest ordering | `/menu?t=<table token>` | Customers (no login) | Phones, tablets |
| Kitchen display | `/kitchen` | Kitchen staff | Landscape tablet, TV, laptop |
| Admin panel | `/admin` | Owner, manager | Laptop, tablet, phone |

- The original brief is in [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md).
- Deliberate deviations and design choices are in [docs/DECISIONS.md](docs/DECISIONS.md). Main ones: Gradle,
  an NgModule-based Angular app, and pluggable payment and notification providers.

---

## Architecture

```
 phone / tablet / TV ──HTTPS──▶  Nginx (frontend container)
                                  ├─ /            Angular SPA (guest, kitchen, admin – lazy-loaded modules)
                                  ├─ /api/**  ──▶ Spring Boot backend :8080 ──▶ PostgreSQL 16
                                  └─ /ws      ──▶ STOMP over WebSocket (same backend)
 Razorpay / PayU  ──webhooks──▶  /api/webhooks/{provider}
```

**Backend** (`backend/`) — Java 21, Spring Boot 3.5, Gradle.
- Stack: Spring Security with stateless JWT, JPA/Hibernate, Flyway, STOMP WebSocket.
- Packages are feature-first under `com.heuristq.dinein`: `auth`, `staff`, `image`, `menu`, `table`, `guest`,
  `order`, `payment`, `notification`, `report`, `settings`, `realtime`, `shared`.
- **Money:** `BigDecimal` everywhere; paise (`long`) only at the payment-gateway edge.
- **Prices are computed on the server only.** Order lines snapshot name, price and GST, so later menu edits never
  change a past bill.
- **Images** are stored in PostgreSQL as re-encoded main (800 px) and thumbnail (200 px) versions.
  - Served from `/api/images/{id}` with `ETag` and a one-year `immutable` cache.
  - Menu responses carry only image URLs, never the bytes.
- **Payments** go through a `PaymentGateway` SPI.
  - Razorpay is complete, and PayU is implemented.
  - Pine Labs is a registered skeleton: it reports "not configured" until someone implements it.
  - The webhook is the source of truth. Client verify, webhook and the expiry job are idempotent and lock the order row.
- **Notifications** (email, SMS, push, in-app) go through per-channel provider SPIs.
  - Every channel defaults to a logging provider.
  - Messages are sent after the transaction commits and asynchronously, so a failing provider never affects an order.
- **Realtime:** WebSocket is only a notification channel. Clients refetch REST state on every (re)connect.

**Frontend** (`frontend/`) — Angular 21 (NgModules, signals, zoneless, OnPush), Tailwind CSS, Angular CDK, rx-stomp,
PWA service worker.

### Order lifecycle

```
PENDING_PAYMENT ─captured─▶ CONFIRMED ─start─▶ PREPARING ─ready─▶ READY ─served─▶ COMPLETED
      │                        │                  │
      │ 15 min, reconciled     └──── admin cancel + full refund ──▶ CANCELLED
      ▼ with the provider
EXPIRED / PAYMENT_FAILED  (a capture that arrives late still confirms the order)
```

- Only paid orders reach the kitchen.
- If the captured amount differs from the bill, or a second payment is captured, the order is **flagged**. It stays
  out of the kitchen until an admin sorts it out.

---

## Quick start with Docker (whole stack)

```bash
cp .env.example .env        # then edit secrets, see "Environment variables"
docker compose up -d --build
```

- Open <http://localhost:8081/admin> and sign in as `owner` with the `BOOTSTRAP_OWNER_PASSWORD` from `.env`.
- You must choose a new password on first login.
- To load the sample menu (3 tables, 3 categories, 8 items), set `SPRING_PROFILES_ACTIVE=dev` in `.env` for the
  first start. Switch back to `prod` afterwards: `dev` also turns off secure cookies and enables Swagger.

## Local development

Prerequisites: JDK 21, Node 22, Docker (for PostgreSQL and the integration tests).

```bash
# 1. Database
docker run -d --name dinein-pg -e POSTGRES_DB=dinein -e POSTGRES_USER=dinein -e POSTGRES_PASSWORD=dinein \
  -p 5432:5432 postgres:16-alpine

# 2. Backend on :8080 – the dev profile is the default: sample data, Swagger UI, relaxed cookies.
cd backend
./gradlew bootRun
#   Swagger UI:  http://localhost:8080/swagger-ui.html
#   First owner: owner / ChangeMe@123 (dev default; change it on first login)

# 3. Frontend on :4200 – proxy.conf.json forwards /api and /ws to :8080, so no CORS setup is needed.
cd frontend
npm install
npm start
```

Guest menu for the sample table T1: open `/admin` → **Tables** and click the table's link. You can also read the token
with `psql` (`select label, qr_token from dining_table;`) and open `http://localhost:4200/menu?t=<qr_token>`.

### Tests

| Command | What it runs |
|---|---|
| `cd backend && ./gradlew test` | Unit tests: pricing/GST rounding, state machine, Razorpay/PayU signatures, image validation, guest-session tampering, notifications |
| `cd backend && ./gradlew integrationTest` | Testcontainers (needs Docker): place → verify → kitchen; duplicate webhooks; webhook/verify in either order; amount mismatch; unavailable items; idempotent placement; price-change snapshot; expiry reconciliation; auth lockout, refresh rotation/reuse, CSRF, roles, kitchen PIN devices |
| `cd frontend && npx ng test --watch=false` | Vitest unit/component tests |
| `cd frontend && npx playwright test` | Guest happy path end to end (backend and checkout are mocked) |

---

## Environment variables

Set them in `.env` (Docker) or your shell. Everything secret comes from the environment.

| Variable | Purpose |
|---|---|
| `APP_PUBLIC_BASE_URL` | Public origin, e.g. `https://order.myrestaurant.in`. Used in QR codes and payment redirects. |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed browser origin(s); normally the same as above |
| `APP_COOKIES_SECURE` | `true` in production (HTTPS) |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database (Compose passes them to the backend as `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`) |
| `APP_JWT_SECRET` | HMAC key for staff access tokens, **at least 32 bytes** |
| `APP_GUEST_SESSION_SECRET` | HMAC key for guest cookies, at least 32 bytes |
| `BOOTSTRAP_OWNER_USERNAME`, `BOOTSTRAP_OWNER_PASSWORD` | First owner, created only when no owner exists; must change password on first login |
| `PAYMENT_PROVIDER` | `RAZORPAY` (default), `PAYU` or `PINELABS` |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | Razorpay API keys and webhook secret |
| `PAYU_KEY`, `PAYU_SALT`, `PAYU_BASE_URL`, `PAYU_POST_SERVICE_URL`, `PAYU_DEFAULT_EMAIL` | PayU (test URLs by default) |
| `NOTIFY_*`, `SMTP_*`, `TWILIO_*`, `MSG91_*`, `FCM_*` | Notification providers; see the Notifications section below and `.env.example` |
| `SPRING_PROFILES_ACTIVE` | `prod` (JSON logs, secure cookies) or `dev` (sample data, Swagger) |

Generate secrets with `openssl rand -base64 48`.

---

## Payments

`PAYMENT_PROVIDER` chooses the gateway for **new** payments. Each payment remembers its provider, so switching later
does not break refunds or webhooks for older orders.

### Razorpay test mode

1. In the [Razorpay Dashboard](https://dashboard.razorpay.com/), switch to **Test Mode**, then open
   **Account & Settings → API Keys** and generate a key. Put the key id and secret into `RAZORPAY_KEY_ID` and
   `RAZORPAY_KEY_SECRET`. The secret never reaches the browser.
2. Orders are created with `payment_capture: 1` (auto-capture).
3. Test payments:
   - **UPI:** use `success@razorpay` / `failure@razorpay`.
   - **Card:** use Razorpay's published test cards (e.g. a Visa test card, any future expiry, any CVV). Test OTPs
     always succeed.

### Webhooks (source of truth)

In the dashboard, open **Account & Settings → Webhooks → Add new webhook**:

- **URL:** `https://<your public host>/api/webhooks/razorpay`
- **Secret:** a random string. Put the same value in `RAZORPAY_WEBHOOK_SECRET`.
- **Events:** `payment.captured`, `payment.failed`, `order.paid`, `refund.processed` (optionally `refund.failed`)

How the backend handles them:
- It verifies `X-Razorpay-Signature` over the raw body.
- It stores every event in `payment_event` and de-duplicates by `X-Razorpay-Event-Id`.
- It answers `200` for valid events (including duplicates) and `400` for bad signatures.

**Local testing with a tunnel.** Razorpay must be able to reach your machine:

```bash
# Frontend dev server (proxies /api to the backend):
cloudflared tunnel --url http://localhost:4200
# or
ngrok http 4200
```

Use the printed HTTPS URL + `/api/webhooks/razorpay` as the webhook URL, and set `APP_PUBLIC_BASE_URL` to the same
origin so QR codes and redirects point there.

### PayU

1. Set `PAYMENT_PROVIDER=PAYU`, `PAYU_KEY` and `PAYU_SALT` (test credentials from the PayU dashboard).
2. The guest is sent to PayU's hosted page.
3. PayU posts the result back to `/api/public/payments/payu/callback`. The backend checks the hash, confirms the
   payment with the `verify_payment` API, and then redirects the guest to their order page.
4. Configure the PayU webhook to `https://<host>/api/webhooks/payu`.

### Adding a provider (e.g. Pine Labs)

1. Implement `payment.gateway.PaymentGateway`; `PineLabsPaymentGateway` is the ready-made skeleton.
2. Create the provider order, return a `CheckoutPayload`:
   - `SDK` for a JS checkout
   - `FORM_POST` for a hosted form
   - `REDIRECT` for a URL
3. Implement the remaining methods:
   - verify the callback signature
   - fetch the authoritative payment status
   - parse and verify webhooks over the raw body
   - refund
4. Add its properties under `app.payments.<provider>`, then select it with `PAYMENT_PROVIDER`.

The frontend already handles all three checkout modes.

### Refunds

In **Admin → Orders**, cancelling a `CONFIRMED` or `PREPARING` order starts a full refund through the gateway that
took the payment.
- If the provider call fails, the order stays cancelled and the refund is marked `FAILED`. Cancel again to retry.
- Refund completion arrives by webhook.

---

## Notifications

Each channel has a provider setting, and every channel defaults to `LOG`. Nothing is sent until you configure a real
provider.

| Channel | Providers | Sent when |
|---|---|---|
| In-app (admin bell, live) | built in | New paid order; payment flagged; refund failed |
| Email | `LOG`, `SMTP` | Payment flagged; refund failed (to owners with an email address) |
| SMS | `LOG`, `TWILIO`, `MSG91` (DLT templates) | Order ready; order cancelled (guest's phone, if given) |
| Push | `LOG`, `FCM` | Order ready (guest devices that opted in); staff alerts |

Staff email and phone are set in **Admin → Staff**. See `.env.example` for all variables.

---

## Operating the restaurant

### First owner
The backend creates `BOOTSTRAP_OWNER_USERNAME` (default `owner`) with `BOOTSTRAP_OWNER_PASSWORD`, but only when no
owner exists.
- On first login the owner must set a new password.
- Then create managers and kitchen accounts in **Admin → Staff**. Kitchen accounts can have a 4–6 digit PIN.
- After 5 wrong passwords an account locks for 15 minutes.

### Printing table QR codes
In **Admin → Tables**:
- Add tables (e.g. `T1`…`T12`, `COUNTER`).
- **Download QR PDF** gives A4 sheets with six cut-out cards (restaurant name, table label, QR code). You can also
  print a single table's card.
- **Regenerate QR** invalidates that table's printed code. Use it if a card is photographed and misused.

### Kitchen tablet / TV
1. Open `https://<host>/kitchen` in Chrome or Edge and sign in with a kitchen username + password or PIN.
2. The device stays signed in for 30 days. The owner can revoke it any time in **Admin → Staff & devices**.
3. Tap **Enable sound** once. Browsers block audio until a tap, and the chime plays on each new order.
4. Keep the screen on:
   - The page requests a screen wake lock while it is open.
   - Also set the device's display timeout to *never* while charging.
5. Kiosk mode:
   - **Android:** add to Home Screen (PWA) and use *screen pinning*, or a kiosk browser (e.g. Fully Kiosk).
   - **Windows / TV stick:** launch Chrome with `--kiosk https://<host>/kitchen`.
6. Use landscape. Columns are New / Preparing / Ready; on phones they collapse into tabs.
   - Tickets turn amber and then red with age. The thresholds are set in **Admin → Settings**.

### Backups & restore
All data, **including menu images**, is in PostgreSQL. A daily logical backup is enough:

```bash
# Daily (cron, 03:00): compressed custom-format dump, keep 14 days
docker compose exec -T postgres pg_dump -U dinein -d dinein -Fc > /backups/dinein-$(date +%F).dump
find /backups -name 'dinein-*.dump' -mtime +14 -delete

# Restore into an empty database
docker compose exec -T postgres pg_restore -U dinein -d dinein --clean --if-exists < /backups/dinein-2026-09-26.dump
```

Size: images are stored re-encoded (≈50–150 KB main + ≈10 KB thumbnail each). A menu of 200 photos adds roughly
20–30 MB to every dump. Orphaned images (no longer used by any item, category or logo) are deleted nightly at 03:30
IST after a 24-hour grace period. Copy dumps off the server (object storage or another machine).

### Observability
- **Health:** `GET /actuator/health/liveness` and `/actuator/health/readiness`. They are not exposed through the
  public Nginx.
- **Logs:** the prod profile writes JSON logs (logstash format) with a `traceId` per request. The id is also returned
  in the `X-Trace-Id` header and in every error body.
- **What is logged:** every order transition (`order.transition`), payment event (`payment.*`, `webhook.*`) and auth
  event (`auth.*`).
- **Never logged:** secrets, tokens and signatures.

---

## Security summary

- HTTPS in production (terminate TLS in front of Nginx and set `APP_COOKIES_SECURE=true`). Enable the HSTS line in
  `frontend/nginx/default.conf`.
- Nginx sends a CSP that allows only Razorpay's checkout/API and PayU form targets, plus `frame-ancestors 'none'` and
  `nosniff`.
- Staff access tokens last 15 minutes. The refresh token is an HttpOnly, `SameSite=Strict` cookie that rotates on
  every use; reusing an old one revokes the whole session family.
- Guests get a signed HttpOnly cookie (12 h) and can only read their own orders.
- CSRF protection covers the cookie-authenticated endpoints: refresh, logout and all guest `POST`s.
- Rate limits: login 5/min/IP, order placement 10/min/guest, image upload 30/min/user.
- Uploads are validated by magic bytes, capped at 5 MB and pixel count, and re-encoded. The original file is never
  stored or served.

---

## Responsive test checklist

Check every page at these widths in browser devtools. There must be no horizontal scroll, touch targets must be at
least 44 px, and inputs at least 16 px.

| Width | Guest `/menu` | Cart & pay | Order status | Kitchen | Admin |
|---|---|---|---|---|---|
| 360 (small phone) | ☐ chips scroll, cards 1-col | ☐ sticky Pay bar clears the home indicator | ☐ token readable | ☐ tabs instead of columns | ☐ drawer nav, tables → cards |
| 390 / 414 (phones) | ☐ | ☐ | ☐ | ☐ | ☐ |
| 768 (tablet portrait) | ☐ 2-col grid | ☐ | ☐ | ☐ 3 columns fit | ☐ real tables, dialogs centred |
| 1024 (tablet landscape) | ☐ grid + cart sidebar | ☐ | ☐ | ☐ primary layout | ☐ sidebar nav |
| 1280 | ☐ | ☐ | ☐ | ☐ | ☐ |
| 1920 (TV / desktop) | ☐ content max-width, not stretched | ☐ | ☐ | ☐ readable from 2 m | ☐ |

Also check:
- ☐ Portrait and landscape
- ☐ iOS Safari safe areas (sticky bars)
- ☐ `prefers-reduced-motion`
- ☐ Kitchen dark mode
- ☐ Keyboard focus rings
- ☐ Veg / non-veg markers distinguishable in greyscale
