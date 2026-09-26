# Build Prompt: Restaurant QR Self-Ordering System (MVP)

> Original brief, kept verbatim for reference. Deliberate deviations are listed in [DECISIONS.md](DECISIONS.md)
> (notably: Gradle instead of Maven, provider-agnostic payments and notifications).

You are a senior full-stack engineer. Build a production-quality MVP of a **QR-based self-ordering system for a single restaurant**. Follow every requirement below. Where something is not specified, choose the simplest robust option and note it in the README.

---

## 1. Product overview

A single restaurant wants guests to scan a QR code on their table (or at the counter), browse the menu on their phone, order, pay online via **Razorpay**, and track their order. The kitchen sees paid orders live on a screen and marks them ready. The owner manages the menu, rates, images, tables and views sales from an admin panel.

The MVP consists of **3 web apps in one frontend project**, backed by **one Spring Boot backend** and **one PostgreSQL database**:

| App | Route prefix | Users | Primary devices |
|---|---|---|---|
| Admin panel | `/admin` | Owner, Manager | Laptop, tablet, phone |
| Guest ordering | `/menu` | Customers (no login) | Mobile phones (primary), tablets |
| Kitchen display | `/kitchen` | Kitchen staff | Tablet (landscape), TV, laptop |

---

## 2. Tech stack (mandatory)

### Backend
- Java 21, Spring Boot 3.x (Maven)
- Spring Web, Spring Data JPA (Hibernate), Spring Validation, Spring Security
- PostgreSQL 16, Flyway migrations (no `ddl-auto=update`)
- JWT auth (jjwt or spring-security-oauth2-resource-server with HMAC)
- Spring WebSocket with STOMP (simple in-memory broker)
- Razorpay Java SDK (`com.razorpay:razorpay-java`)
- Thumbnailator (or ImageIO) for image resizing
- Lombok optional; MapStruct optional; springdoc-openapi for Swagger UI
- JUnit 5 + Testcontainers (PostgreSQL) for integration tests

### Frontend
- **Angular 21** (latest stable 21.x) + TypeScript (strict mode), generated with Angular CLI
- **Standalone components only** (no NgModules), **zoneless change detection**, `ChangeDetectionStrategy.OnPush` everywhere
- **Signals** for state (`signal`, `computed`, `effect`, `input()`, `output()`, `model()`); built-in control flow (`@if`, `@for` with `track`, `@switch`, `@defer`)
- Dependency injection with `inject()`; functional route guards, resolvers and HTTP interceptors
- Angular Router with **lazy-loaded route groups**: `/admin/**`, `/menu/**`, `/kitchen/**` via `loadChildren` / `loadComponent`, so each app ships only its own code
- `HttpClient` (`provideHttpClient(withInterceptors([...]), withFetch())`); `httpResource()`/`rxResource()` or services returning signals for server state
- Cart state in a signal-based `CartStore` service (injectable, `providedIn: 'root'`)
- **Reactive Forms** (typed `FormGroup`/`FormArray`) with custom validators for all admin forms; `FormArray` for variant and addon editors
- **Tailwind CSS** (mobile-first) for layout and styling; **Angular CDK** for overlays, dialogs/bottom sheets, `BreakpointObserver`, and **drag-and-drop** (`@angular/cdk/drag-drop`) for category reordering. Angular Material components are optional; if used, theme them to match the Tailwind design tokens
- `@stomp/rx-stomp` (or `@stomp/stompjs`) wrapped in an injectable `RealtimeService` exposing signals/observables
- Razorpay Checkout.js loaded dynamically by a `RazorpayService` (typed wrapper, promise-based)
- `@angular/service-worker` (`ng add @angular/pwa`) so the guest and kitchen apps can be added to home screen
- `NgOptimizedImage` (`ngSrc`) for all menu images, with explicit width/height or `fill` mode
- Angular's built-in `CurrencyPipe` / `DatePipe` with `en-IN` locale registered; `LOCALE_ID` set to `en-IN`
- Unit tests with the Angular CLI's default test runner (Vitest) + Angular Testing utilities; e2e with Playwright
- ESLint (`angular-eslint`) + Prettier

Suggested frontend structure:
```
frontend/src/app/
├── core/          # interceptors (auth, error, idempotency), guards, auth store, realtime service, razorpay service, api models
├── shared/        # UI components (button, card, bottom-sheet, veg-marker, price, skeleton, empty-state), pipes, directives
├── features/
│   ├── guest/     # menu, item-sheet, cart, checkout, order-status, my-orders  (routes: /menu/**)
│   ├── kitchen/   # login, board, ticket-card                                 (routes: /kitchen/**)
│   └── admin/     # login, dashboard, categories, items, tables, orders, reports, settings, staff (routes: /admin/**)
├── app.routes.ts
└── app.config.ts
```

### Infra
- `docker-compose.yml` with `postgres`, `backend`, `frontend` (Nginx serving the built SPA and reverse-proxying `/api` and `/ws` to the backend)
- All secrets via environment variables; provide `.env.example`

### Repository layout
```
restaurant-ordering/
├── backend/
├── frontend/
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## 3. Domain model & database (Flyway `V1__init.sql`)

Use `BIGSERIAL` ids (or UUIDs — pick one and be consistent), `created_at`/`updated_at` timestamps on all tables, and `NUMERIC(10,2)` for all money. In Java, use `BigDecimal` for money — never `double`/`float`. Razorpay amounts are in **paise** (`long`), convert explicitly.

### 3.1 Images — stored in the database
```sql
image (
  id              BIGSERIAL PK,
  content_type    VARCHAR(50)  NOT NULL,   -- image/jpeg, image/png, image/webp
  data            BYTEA        NOT NULL,   -- resized main image (max 800px longest side)
  thumbnail       BYTEA        NOT NULL,   -- 200px longest side
  size_bytes      INT          NOT NULL,
  width           INT, height  INT,
  sha256          CHAR(64)     NOT NULL,   -- used as ETag, and to dedupe
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
)
```
Rules:
- Images live in their own table. `category` and `item` reference them via `image_id` FK. **Never** map the `BYTEA` columns on the category/item entities, so menu queries never load image bytes. Use a separate `ImageEntity` with lazy loading, or load bytes only through a dedicated repository projection.
- Upload accepts JPEG, PNG, WEBP only. Validate by magic bytes, not just the extension/Content-Type header. Max upload size 5 MB.
- On upload: strip EXIF, auto-rotate by EXIF orientation, resize to max 800px (quality ~0.8) and generate a 200px thumbnail. Store both.
- Serve via `GET /api/images/{id}` and `GET /api/images/{id}/thumb` with correct `Content-Type`, `ETag: "<sha256>"`, `Cache-Control: public, max-age=31536000, immutable`, and support `If-None-Match` → `304`. When an admin replaces an image, create a new image row (new id) so caches never serve stale images.
- Orphaned images (not referenced by any category/item) are cleaned up by a nightly scheduled job.

### 3.2 Menu
```sql
category (
  id, name VARCHAR(80) NOT NULL, description VARCHAR(300),
  image_id FK -> image NULL,
  display_order INT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at, updated_at,
  UNIQUE (lower(name))
)

item (
  id, category_id FK -> category NOT NULL,
  name VARCHAR(120) NOT NULL, description VARCHAR(500),
  image_id FK -> image NULL,
  base_price NUMERIC(10,2),                 -- used when item has no variants
  food_type VARCHAR(10) NOT NULL,           -- VEG | NON_VEG | EGG
  gst_percent NUMERIC(4,2) NOT NULL DEFAULT 5.00,
  is_available BOOLEAN NOT NULL DEFAULT true,  -- out-of-stock toggle
  is_active BOOLEAN NOT NULL DEFAULT true,     -- soft delete
  display_order INT NOT NULL DEFAULT 0,
  created_at, updated_at,
  UNIQUE (category_id, lower(name))
)

item_variant (
  id, item_id FK NOT NULL, name VARCHAR(50) NOT NULL,   -- Half, Full, 630 ml
  price NUMERIC(10,2) NOT NULL CHECK (price > 0),
  is_default BOOLEAN NOT NULL DEFAULT false,
  display_order INT NOT NULL DEFAULT 0, is_active BOOLEAN NOT NULL DEFAULT true
)

addon (
  id, item_id FK NOT NULL, name VARCHAR(50) NOT NULL,
  price NUMERIC(10,2) NOT NULL CHECK (price >= 0),
  is_active BOOLEAN NOT NULL DEFAULT true
)
```
Rules: an item must have either `base_price > 0` or at least one active variant. Items/categories are never hard-deleted. Disabling a category hides all its items from guests.

### 3.3 Tables & QR
```sql
dining_table (
  id, label VARCHAR(20) NOT NULL UNIQUE,     -- T1, T2, COUNTER
  qr_token VARCHAR(64) NOT NULL UNIQUE,      -- random, rotatable
  is_active BOOLEAN NOT NULL DEFAULT true, created_at, updated_at
)
```
The QR encodes `https://<host>/menu?t=<qr_token>`. `qr_token` is a cryptographically random 32-byte URL-safe string. Admin can regenerate a table's token (invalidates old printed QR).

### 3.4 Orders
```sql
orders (
  id, order_number VARCHAR(20) NOT NULL UNIQUE,   -- human-friendly daily number, e.g. 260926-042
  display_token INT NOT NULL,                     -- short daily token shown to guest/kitchen (resets daily)
  table_id FK -> dining_table,
  guest_session_id VARCHAR(64) NOT NULL,
  customer_name VARCHAR(60), customer_phone VARCHAR(15),
  notes VARCHAR(300),
  status VARCHAR(20) NOT NULL,                    -- see state machine
  subtotal NUMERIC(10,2) NOT NULL,
  tax_total NUMERIC(10,2) NOT NULL,
  grand_total NUMERIC(10,2) NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL UNIQUE,
  placed_at, paid_at, ready_at, completed_at, cancelled_at,
  created_at, updated_at
)

order_item (
  id, order_id FK NOT NULL,
  item_id FK, variant_id FK NULL,
  item_name VARCHAR(120) NOT NULL,     -- SNAPSHOT at order time
  variant_name VARCHAR(50),            -- SNAPSHOT
  unit_price NUMERIC(10,2) NOT NULL,   -- SNAPSHOT (variant/base price + addons)
  quantity INT NOT NULL CHECK (quantity BETWEEN 1 AND 50),
  gst_percent NUMERIC(4,2) NOT NULL,   -- SNAPSHOT
  line_total NUMERIC(10,2) NOT NULL,
  notes VARCHAR(200)
)

order_item_addon (
  id, order_item_id FK NOT NULL, addon_name VARCHAR(50) NOT NULL, price NUMERIC(10,2) NOT NULL
)
```
**Snapshot rule:** order lines copy name, price and GST at order time. Later menu/rate changes must never alter past orders.

### 3.5 Payments
```sql
payment (
  id, order_id FK NOT NULL,
  razorpay_order_id VARCHAR(40) NOT NULL UNIQUE,
  razorpay_payment_id VARCHAR(40) UNIQUE,
  amount_paise BIGINT NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'INR',
  status VARCHAR(20) NOT NULL,          -- CREATED | AUTHORIZED | CAPTURED | FAILED | REFUNDED
  method VARCHAR(20),                   -- upi, card, netbanking, wallet (from webhook)
  failure_reason VARCHAR(300),
  created_at, updated_at
)

payment_event (                          -- raw webhook log, for audit & idempotency
  id, razorpay_event_id VARCHAR(60) UNIQUE, event_type VARCHAR(50),
  payload JSONB NOT NULL, signature_valid BOOLEAN NOT NULL,
  processed_at TIMESTAMPTZ, created_at
)
```

### 3.6 Staff & settings
```sql
staff_user (
  id, username VARCHAR(50) UNIQUE NOT NULL, password_hash VARCHAR(100) NOT NULL,  -- BCrypt
  display_name VARCHAR(80), role VARCHAR(20) NOT NULL,   -- OWNER | MANAGER | KITCHEN
  is_active BOOLEAN NOT NULL DEFAULT true, last_login_at, created_at, updated_at
)

device_token (                            -- long-lived kitchen device sessions, revocable
  id, staff_user_id FK, device_name VARCHAR(60), token_hash CHAR(64) UNIQUE,
  expires_at, revoked_at, last_seen_at, created_at
)

restaurant_settings (                     -- single row
  id, name, address, phone, gstin, fssai_no,
  logo_image_id FK -> image NULL,
  is_accepting_orders BOOLEAN NOT NULL DEFAULT true,
  opening_time TIME, closing_time TIME,
  currency CHAR(3) DEFAULT 'INR', updated_at
)
```

Seed (Flyway `V2__seed.sql` or a dev-profile `CommandLineRunner`): one OWNER user (password from env `BOOTSTRAP_OWNER_PASSWORD`, must be changed on first login), settings row, 3 tables, 3 sample categories with ~8 items (some with variants/addons).

---

## 4. Order state machine

```
PENDING_PAYMENT ──(payment captured)──▶ CONFIRMED ──(kitchen: start)──▶ PREPARING ──(kitchen: ready)──▶ READY ──(staff: served/picked)──▶ COMPLETED
       │                                    │
       │(payment failed / 15 min timeout)   │(admin cancel → refund)
       ▼                                    ▼
   EXPIRED / PAYMENT_FAILED            CANCELLED (+ Razorpay refund)
```
- Implement transitions in one `OrderStateMachine`/service method that rejects illegal transitions with `409 Conflict`.
- Only `CONFIRMED` (paid) orders appear in the kitchen.
- A scheduled job expires `PENDING_PAYMENT` orders older than 15 minutes (but first checks Razorpay order status to avoid expiring a paid order whose webhook was delayed).
- Every transition writes timestamps and publishes a WebSocket event.

---

## 5. Pricing & tax (server-side only)

- The browser sends only item ids, variant ids, addon ids, quantities and notes. **Never trust prices from the client.**
- Server recomputes: `unit_price = (variant.price or item.base_price) + sum(addons)`, `line_total = unit_price × qty`.
- GST: prices are **exclusive of GST** by default (make this a setting: `prices_include_gst`). Compute tax per line with `gst_percent`, split display into CGST and SGST (half each). Round with `RoundingMode.HALF_UP` to 2 decimals at line level; totals are sums of rounded lines.
- Reject the order if any item/variant/addon is inactive, unavailable, or its category is inactive — return a clear error listing the offending items so the guest UI can update the cart.
- Reject orders when `is_accepting_orders = false` or outside opening hours.

---

## 6. Razorpay integration (mandatory details)

Config via env: `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`. Use Razorpay **test mode** keys in dev. The key secret never reaches the frontend.

### Flow
1. **Place order** — `POST /api/public/orders` (with `Idempotency-Key` header generated by the client per checkout attempt). Server validates cart, computes totals, creates `orders` row in `PENDING_PAYMENT`, then creates a **Razorpay Order** (`amount` in paise, `currency: INR`, `receipt: order_number`, `notes: {orderId}`), stores a `payment` row (`CREATED`), and returns `{orderId, orderNumber, razorpayOrderId, amountPaise, keyId, restaurantName}`. Same idempotency key returns the same response (no duplicate orders).
2. **Checkout** — frontend loads `https://checkout.razorpay.com/v1/checkout.js` and opens Razorpay Checkout with `order_id`, `key`, `amount`, `name`, `prefill` (name/phone if provided), theme colour, and `modal.ondismiss` handling. UPI should be prominent on mobile.
3. **Client verify** — on success handler, frontend posts `{razorpay_order_id, razorpay_payment_id, razorpay_signature}` to `POST /api/public/payments/verify`. Server verifies `HMAC_SHA256(razorpay_order_id + "|" + razorpay_payment_id, key_secret)` using constant-time comparison (Razorpay SDK `Utils.verifyPaymentSignature` is fine). If valid, fetch the payment from Razorpay API to confirm status is `captured` (enable auto-capture on the Razorpay order via `payment_capture: 1`) and amount matches, then mark payment `CAPTURED` and order `CONFIRMED`.
4. **Webhook (source of truth)** — `POST /api/webhooks/razorpay`. Verify `X-Razorpay-Signature` = `HMAC_SHA256(raw_request_body, webhook_secret)` against the **raw body bytes** (read before JSON parsing). Store every event in `payment_event`. Handle `payment.captured`, `order.paid`, `payment.failed`, `refund.processed`. Deduplicate by `x-razorpay-event-id` header. Always return `200` quickly for valid signatures (even duplicates); return `400` for invalid signatures.
5. Steps 3 and 4 may arrive in any order or twice — the "mark paid" logic must be **idempotent** (use `SELECT ... FOR UPDATE` on the order row or optimistic locking; transition to `CONFIRMED` only from `PENDING_PAYMENT`). Verify the captured amount equals `grand_total` in paise; if not, flag the order and do not send it to the kitchen.
6. **Cancellation/refund** — admin cancelling a `CONFIRMED`/`PREPARING` order triggers a full refund via Razorpay Refund API; record refund status.
7. **Failure UX** — if payment fails or the modal is dismissed, the guest sees "Payment not completed" with **Retry payment** (reuses the same order and Razorpay order while `PENDING_PAYMENT`) and **Edit cart** options.

Document webhook setup (URL, secret, events) in the README, including using a tunnel (ngrok / cloudflared) for local testing.

---

## 7. Authentication & authorization

- **Guests:** no login. On first valid QR scan, `GET /api/public/session?t=<qr_token>` validates the table token and issues an anonymous `guest_session` HttpOnly, Secure, SameSite=Lax cookie (signed, 12h). Guests can only read their own orders (`guest_session_id` match). Optional name/phone at checkout (validate 10-digit Indian mobile), no OTP in MVP.
- **Admin (OWNER, MANAGER):** `POST /api/auth/login` with username/password → short-lived access JWT (15 min) + refresh token (7 days, HttpOnly cookie, rotation on refresh). BCrypt (strength 12). Lock account for 15 min after 5 failed attempts. Force password change on first login for the bootstrap owner.
  - OWNER: everything, including staff management, settings and sales reports.
  - MANAGER: menu, tables, orders; no staff management, no settings.
- **Kitchen (KITCHEN):** login once on the device, or log in with a 4–6 digit PIN, then issue a long-lived **device token** (30 days, stored hashed in `device_token`), revocable by the owner from the admin panel.
- Spring Security: stateless for `/api/**` with JWT; CSRF protection for cookie-based endpoints (refresh, guest session); CORS locked to the frontend origin; method-level `@PreAuthorize`.
- Rate limiting (Bucket4j or a simple in-memory limiter): login 5/min/IP, order placement 10/min/session, image upload 30/min/user.
- WebSocket: authenticate the STOMP `CONNECT` frame (JWT/device token for kitchen/admin topics; guest session cookie for the guest's own order topic).

---

## 8. REST API (prefix `/api`)

Return consistent JSON errors: `{ "code": "ITEM_UNAVAILABLE", "message": "...", "details": [...] , "traceId": "..." }` via a `@RestControllerAdvice`. Use DTOs (never expose entities), bean validation on all inputs, pagination on list endpoints.

### Public (guest)
| Method | Path | Notes |
|---|---|---|
| GET | `/public/session?t={qrToken}` | Validate table, set guest cookie, return table label + restaurant info |
| GET | `/public/menu` | Active categories → available items with variants, addons, `imageUrl`, `thumbUrl`. Cacheable (ETag on menu version) |
| POST | `/public/orders` | Place order (requires `Idempotency-Key`) → Razorpay order details |
| POST | `/public/payments/verify` | Client-side signature verification |
| POST | `/public/orders/{id}/retry-payment` | Returns Razorpay details for a `PENDING_PAYMENT` order |
| GET | `/public/orders/{id}` | Guest's own order status + lines + bill |
| GET | `/public/orders` | Guest's orders in current session |
| GET | `/images/{id}`, `/images/{id}/thumb` | Public image bytes with caching headers |

### Webhook
| POST | `/webhooks/razorpay` | Signature-verified, idempotent |

### Auth
| POST | `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/change-password`, `/auth/kitchen-device` |

### Admin (OWNER/MANAGER)
| Method | Path |
|---|---|
| GET/POST | `/admin/categories` |
| PUT | `/admin/categories/{id}` |
| PATCH | `/admin/categories/{id}/status` |
| PATCH | `/admin/categories/reorder` (body: ordered id list) |
| GET | `/admin/items?categoryId=&q=&page=` |
| POST | `/admin/items` (with variants + addons) |
| GET/PUT | `/admin/items/{id}` |
| PATCH | `/admin/items/{id}/availability` |
| PATCH | `/admin/items/{id}/price` (quick inline rate edit; supports variant prices) |
| DELETE | `/admin/items/{id}` (soft delete) |
| POST | `/admin/images` (multipart `file`) → `{imageId, url, thumbUrl}` |
| GET/POST/PUT | `/admin/tables`, `/admin/tables/{id}` |
| POST | `/admin/tables/{id}/regenerate-qr` |
| GET | `/admin/tables/qr.pdf?ids=` (printable A4 PDF of QR cards with table label and restaurant name) |
| GET | `/admin/orders?status=&date=&page=` |
| GET | `/admin/orders/{id}` |
| PATCH | `/admin/orders/{id}/status` (e.g., mark COMPLETED) |
| POST | `/admin/orders/{id}/cancel` (refund) |
| GET | `/admin/reports/summary?from=&to=` (orders count, gross, tax, net, payment method split, top items) — OWNER only |
| GET/PUT | `/admin/settings` — OWNER only |
| GET/POST/PUT | `/admin/staff`, `/admin/staff/{id}` — OWNER only |
| GET/DELETE | `/admin/devices`, `/admin/devices/{id}` (revoke kitchen device) — OWNER only |

### Kitchen (KITCHEN, also OWNER/MANAGER)
| GET | `/kitchen/orders?status=CONFIRMED,PREPARING,READY` |
| PATCH | `/kitchen/orders/{id}/status` (CONFIRMED→PREPARING→READY) |

---

## 9. Real-time (STOMP over WebSocket at `/ws`)

| Topic | Subscribers | Events |
|---|---|---|
| `/topic/kitchen/orders` | Kitchen, admin | `ORDER_CONFIRMED` (full order payload), `ORDER_STATUS_CHANGED`, `ORDER_CANCELLED` |
| `/topic/orders/{orderId}` | That guest only | `ORDER_STATUS_CHANGED` |
| `/topic/menu` | Guests | `MENU_UPDATED` (price/availability change → refetch menu) |

Clients must auto-reconnect with backoff, and **refetch the REST state on reconnect** (WebSocket is a notification channel, REST is the source of truth). Show a visible "Reconnecting…" banner when disconnected.

---

## 10. Frontend requirements

### 10.1 Responsiveness (mandatory for every page)
Build **mobile-first** with Tailwind breakpoints and test at these widths: **360px, 390px, 414px (phones), 768px (tablet portrait), 1024px (tablet landscape), 1280px and 1920px (desktop / TV)**.
- No horizontal scrolling at any width; no fixed pixel widths on containers; use fluid grids (`grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 …`), `flex-wrap`, `min-w-0`, and `max-w-*` containers.
- Touch targets ≥ 44×44px; form inputs ≥ 16px font size (prevents iOS zoom on focus).
- Use `100dvh` (not `100vh`) for full-height layouts; respect safe-area insets (`env(safe-area-inset-bottom)`) for sticky bottom bars on iPhones.
- Images: fixed aspect-ratio boxes (`aspect-square` / `aspect-[4/3]`) with `object-cover`, `loading="lazy"`, use `thumbUrl` in lists and `imageUrl` in detail views; show a neutral placeholder while loading or when there's no image. No layout shift.
- Tables in admin become **stacked cards on mobile** (<768px) and real tables on larger screens.
- Navigation: admin uses a collapsible sidebar on ≥1024px, a hamburger drawer below that.
- Modals become full-screen bottom sheets on mobile, centered dialogs on desktop.
- Typography scales with `clamp()` or responsive Tailwind classes; kitchen screen text must be readable from 2 metres on a TV.
- Support both portrait and landscape orientations; kitchen is optimized for landscape.
- Respect `prefers-reduced-motion`; support dark mode for the kitchen screen (default dark).
- Accessibility: semantic HTML, labels on all inputs, visible focus states, colour contrast ≥ WCAG AA, veg/non-veg shown with both colour **and** icon/shape.

### 10.2 Guest ordering app (`/menu`) — phone-first
- Entry via `/menu?t=<token>`; invalid/inactive token → friendly "Please scan the QR on your table" page. Show "Ordering is closed" if not accepting orders.
- Header: restaurant logo/name, table label.
- Sticky horizontal **category chips** that scroll-spy with the item list; search box to filter items.
- Item cards: thumbnail, name, veg/non-veg marker, short description, price (or "from ₹X" if variants), **Add** button → becomes **− qty +** stepper. Unavailable items greyed with "Out of stock".
- Item with variants/addons opens a bottom sheet to choose variant, addons, notes, quantity; shows live price.
- **Sticky bottom cart bar** ("3 items · ₹540 — View cart") with safe-area padding.
- Cart page: lines with edit/remove, order notes, optional name & phone, bill breakdown (subtotal, CGST, SGST, total), **Pay ₹X** button.
- Cart persisted in `localStorage` per table (wrapped in try/catch), cleared after successful payment.
- After payment: order status page with big **token number**, progress stepper (Paid → Preparing → Ready), live updates via WebSocket, sound/vibration when READY (if permitted), and a bill view the guest can screenshot/print.
- "My orders" list for the current session; "Order more" button.
- Tablet layout (≥768px): 2–3 column item grid; desktop (≥1024px): item grid + persistent cart sidebar.
- Performance: first load < 2s on 4G; menu JSON excludes image bytes; code-split routes.

### 10.3 Kitchen display (`/kitchen`) — tablet/TV, landscape-optimized
- Login screen (username/password or PIN) → remembers device.
- Board with columns **New (CONFIRMED)**, **Preparing**, **Ready**; on phones collapse into tabs.
- Order ticket card: token number (large), table label, elapsed time since paid (updates every second), items with quantity (large), variants/addons/notes highlighted, action button (**Start** / **Ready** / **Served**).
- Card colour escalates by age (e.g., >10 min amber, >20 min red) — thresholds configurable.
- Audible chime + flash on new order (requires one "Enable sound" tap on first load due to browser autoplay rules). Screen wake lock (`navigator.wakeLock`) while open.
- Oldest first ordering. Ready orders auto-hide after N minutes or when marked Served.
- Connection status indicator; full refetch on reconnect.

### 10.4 Admin panel (`/admin`) — desktop-first but fully usable on phone
- Login, forced password change, logout.
- **Dashboard:** today's orders, revenue, average order value, orders by status, live list of recent orders.
- **Categories:** list with thumbnail, item count, active toggle, **drag-and-drop reorder** (Angular CDK `cdkDropList`; on mobile provide up/down buttons as well), add/edit dialog with image upload.
- **Items:** category filter (sidebar on desktop, dropdown on mobile), search, table/cards with thumbnail, name, veg marker, price, availability toggle, active status. **Inline rate editing** with optimistic update and undo toast. Add/edit form: category, name, description, food type, GST %, image upload (drag-drop + camera capture on mobile via `accept="image/*" capture`), "Has sizes" switch → variant rows editor, addons editor, preview of how it looks on the guest menu.
- **Image upload UX:** client-side preview, show progress, client-side pre-resize large photos (e.g., browser-image-compression) before upload, clear error messages for wrong type/too large.
- **Tables:** list, add, activate/deactivate, regenerate QR, preview QR, download printable QR PDF (single or all).
- **Orders:** filter by date/status, order detail with lines, payment info (Razorpay payment id, method), mark completed, cancel & refund (with confirmation).
- **Reports (OWNER):** date range, totals, tax split, payment method split, top 10 items, CSV export.
- **Settings (OWNER):** restaurant details, GSTIN, logo, opening hours, accepting-orders switch, prices-include-GST switch.
- **Staff & devices (OWNER):** manage users/roles, reset passwords, revoke kitchen devices.

### 10.5 Shared UI
- Consistent design tokens (colours, radius, spacing) in Tailwind config; brand colour configurable.
- Toasts for success/error, skeleton loaders (`@defer` placeholders where useful), empty states, a global `ErrorHandler`, an HTTP error interceptor, and a per-route error state component (Angular has no error boundaries; handle errors explicitly).
- Use the CDK `BreakpointObserver` (exposed as a signal via `toSignal`) only where layout logic can't be expressed in Tailwind classes, e.g. switching dialog ↔ bottom sheet.
- Currency formatting with `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })`.
- All times shown in IST (`Asia/Kolkata`).

---

## 11. Non-functional requirements

- **Security:** HTTPS in production; security headers (CSP allowing `checkout.razorpay.com` and `api.razorpay.com`, HSTS, X-Content-Type-Options, frame-ancestors none except Razorpay needs); never log secrets, full JWTs, or payment signatures; validate and sanitise all text inputs; image uploads validated by magic bytes and re-encoded (never serve the raw upload).
- **Data integrity:** DB constraints mirror business rules; transactions around order placement and payment state changes; optimistic locking (`@Version`) on `orders` and `item`.
- **Observability:** structured JSON logs with `traceId` per request (MDC), Spring Boot Actuator health/readiness endpoints, log every order state transition and payment event.
- **Performance:** menu endpoint served from an in-memory cache invalidated on any menu change; DB indexes on `orders(status, placed_at)`, `orders(guest_session_id)`, `item(category_id, is_active)`, `payment(razorpay_order_id)`.
- **Backups:** README documents a daily `pg_dump` (images are in the DB, so backups include them — note the size implications).
- **Config profiles:** `dev` (seed data, Razorpay test keys, Angular dev server on port 4200 using `proxy.conf.json` for `/api` and `/ws`, so no CORS is needed in dev) and `prod`.

---

## 12. Testing

- Unit tests: pricing/GST calculation (including rounding), order state machine transitions, Razorpay signature verification (valid/invalid/tampered), image validation.
- Integration tests (Testcontainers): place order → verify payment → order appears in kitchen query; webhook idempotency (same event twice); webhook before client verify and vice versa; amount mismatch; unavailable item rejection; idempotent order placement.
- Frontend: unit tests for `CartStore` pricing display logic, guards and interceptors, and component tests for the item sheet and cart (Angular CLI default runner / Vitest with `TestBed`); one Playwright e2e for guest happy path using Razorpay test mode (or a mocked checkout in CI).
- Manual responsive checklist in README covering the widths listed in 10.1.

---

## 13. Deliverables

1. Complete source for `backend/` and `frontend/` following the layout above.
2. Flyway migrations and seed data.
3. `docker-compose.yml` that brings up the full stack with one command, plus `.env.example`.
4. OpenAPI/Swagger UI at `/swagger-ui.html` (dev profile).
5. `README.md` covering: architecture overview, local setup, env variables, Razorpay test setup and webhook configuration (with tunnel), creating the first owner, printing table QRs, kitchen tablet setup (kiosk browser, keep screen on), backup/restore, and the responsive test checklist.

## 14. Build order (commit after each step, keep the app runnable)

1. Project scaffolding, Docker Compose, Flyway, health endpoint.
2. Auth (staff login, JWT, roles, bootstrap owner, forced password change).
3. Image storage module (upload, resize, serve with caching).
4. Menu admin: categories, items, variants, addons, availability, inline rates.
5. Tables & QR generation (PNG + printable PDF).
6. Public menu + guest session + guest ordering UI (cart, bottom sheet, responsive layouts).
7. Order placement with server-side pricing and idempotency.
8. Razorpay: order creation, Checkout, client verify, webhook, retry, expiry job.
9. WebSocket + kitchen display.
10. Admin orders, cancel/refund, dashboard, reports, settings, staff/devices.
11. Tests, responsive QA pass at all listed widths, README.

## 15. Acceptance criteria

- An owner can log in, create categories and items with images, variants, addons and rates, and see them immediately on the guest menu.
- Images are stored in PostgreSQL, served with caching headers, and menu listing responses contain no image bytes.
- A guest can scan a table QR on a phone, add items, pay via Razorpay (test mode, UPI and card), and see live status through to READY.
- The kitchen receives only paid orders, in real time, with a sound alert, and can move them to READY.
- Duplicate taps, duplicate webhooks and out-of-order verify/webhook calls never create duplicate orders or double-confirm payments.
- Changing an item's rate does not change any existing order or bill.
- Every page works without horizontal scroll and remains fully usable at 360px, 768px, 1024px and 1920px widths.
