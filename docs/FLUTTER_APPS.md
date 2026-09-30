# Flutter Apps: Self-Order Kiosk and Counter POS

Status: Draft plan. Owner: TBD. Last updated: 2026-09-30.

## 1. Purpose

Add two separate Android applications, built in Flutter, on top of the existing Spring Boot backend:

| App | Users | Hardware | Priority |
|---|---|---|---|
| **Self-Order Kiosk** | Walk-in customers | Android kiosk (wall or floor mount) with built-in thermal printer | Build first |
| **Counter POS** | Cashier or billing staff | Android tablet or POS terminal, printer, cash drawer | Build after the kiosk pilot is stable |

The existing web apps (guest QR, kitchen, waiter, admin, order-status display, platform admin) stay as they are.

## 2. Guiding principles (risk control)

1. **Separate apps, separate repos.** `dinein-kiosk-app` and `dinein-counter-app`. Each has its own application ID, keystore, version, and release pipeline. A change in one cannot break the other.
2. **Thin clients, authoritative backend.** The server computes totals, GST, discounts and payment state. The apps only display them. This keeps duplicated code small.
3. **One app at a time.** Kiosk first. Pilot one device in one outlet with a counter backup before starting the counter POS.
4. **Additive backend changes only.** New tables and endpoints. Existing guest, kitchen and waiter flows are not modified.
5. **Every write is idempotent.** Retries and offline sync must never create duplicate orders or double charges.
6. **Payments: the webhook is the source of truth.** Polling is only a fallback.
7. **Remote kill switch.** Admin can disable kiosk ordering at any time, and pay-at-counter remains available.

## 3. Technology

| Concern | Choice |
|---|---|
| Framework | Flutter (stable), Android target |
| State management | Riverpod, with sealed-class state for the order flow |
| HTTP | `dio` with interceptors (device token, `Idempotency-Key`, `X-App-Version`, `X-App-Id`) |
| Local database | `drift` (SQLite): menu cache, offline order queue |
| Secure storage | `flutter_secure_storage` (device token) |
| Realtime | `stomp_dart_client` against the existing `/ws` endpoint |
| Images | `cached_network_image` |
| UPI QR | `qr_flutter` rendering the QR string returned by the backend |
| Printing | `esc_pos_utils_plus` with a `PrinterService` interface (USB, Bluetooth, or vendor SDK transport) |
| Kiosk lockdown | Android lock task mode via a small Kotlin platform channel, boot auto-start |
| Connectivity | `connectivity_plus`, `wakelock_plus` |
| Localisation | `flutter_localizations`, ARB files |

## 4. Self-Order Kiosk

### 4.1 Customer flow

`attract -> language -> order type -> menu -> item customise -> cart -> upsell -> payment -> token -> attract`

Every state has an inactivity timeout with a "Still there?" countdown that returns to the attract screen and clears the session.

### 4.2 Features

**P0: MVP**

- **Device pairing.** Admin generates a pairing code, the kiosk enters it and receives a device token stored in secure storage. Devices are bound to a restaurant (and an optional outlet).
- **Attract screen.** Branded welcome page (see section 6) with a "Touch to order" prompt.
- **Order type.** Dine In or Takeaway, changeable until checkout.
- **Menu browsing.**
  - 5-7 category tabs named the way customers think.
  - Large item photos, veg / non-veg marker, price, short description.
  - Out-of-stock items greyed out or hidden, in near real time.
- **Customisation.** Variants, add-ons, and notes such as "no onion" or "extra cheese". All modifiers must appear on the KOT.
- **Cart.** Add, remove and edit items with a live GST breakdown from the server. Respects `prices_include_gst`.
- **Upsell prompts.** Per-item suggestions (dip with a burger), combo suggestions (drink with a combo), and a final checkout suggestion (dessert). Configured in Admin.
- **Payment.**
  - Dynamic UPI QR generated after order confirmation, with status polling.
  - Payment timeout resets the screen gracefully, with retry and cancel.
  - Pay at counter: the customer gets a token and pays cash or card at the counter (uses the existing offline payment path).
- **Token screen.** Large token number, returns to the attract screen after a few seconds.
- **Printing.** Customer receipt (token, items, GST, payment mode) and a KOT for the kitchen.
- **Kitchen integration.** Orders arrive on the existing kitchen board with source `KIOSK` and a token number.

**P1: Operations (required before go-live)**

- **Staff PIN overlay.** Hidden long-press area opens a PIN-protected menu: restart, reprint last receipt, printer test, exit kiosk mode.
- **Offline mode.** Cached menu with version stamp. Pay-at-counter orders can be queued locally and synced with idempotency keys. UPI requires connectivity.
- **Live menu and stock push.** Price, availability and menu changes arrive over STOMP within seconds.
- **Device health.** Heartbeat with printer status, app version and last-seen time, reusing the existing device-offline monitor.
- **Lockdown.** Lock task mode, immersive full-screen, screen always on, auto-start on boot, auto-restart after a crash.
- **Kill switch.** Remote enable or disable, per payment method toggles.
- **Version gate.** "Please update" screen when the app is below the backend's minimum version.

**P2: Later**

- Card tap via a Pine Labs POS terminal SDK.
- Outlet and branch hierarchy with central menu and price pushes.
- Loyalty and phone number capture.
- Digital receipt via WhatsApp or SMS.
- Tap analytics (drop-off screen, dwell time).
- Remote configuration and OTA update flow.

## 5. Counter POS

### 5.1 Features

**P0**

- Fast order entry: category grid, search, barcode scan.
- Modifiers and notes, dine-in and takeaway, table or token assignment.
- Hold and recall bills, split bills.
- **Pending kiosk orders:** look up a "pay at counter" order by token and take payment.
- Payments: cash, card at counter, UPI QR, split tender. Print receipt and open the cash drawer.
- Reprint receipt and KOT.

**P1**

- Discounts and refunds with role permissions and manager PIN override.
- Shift open and close, cash reconciliation, end-of-day report.
- Offline mode with sync.
- Audit log entries for every override.

**Backend gaps to fill:** shifts, discounts, refunds by role, cash drawer events. The existing offline payment types (`CASH`, `UPI_AT_COUNTER`, `CARD_AT_COUNTER`) and mark-paid-offline path are the base.

## 6. Branding and customisation

Owners control the look of both apps from Admin, with no app update. Branding is stored per restaurant on the backend and fetched at startup, cached locally, with a safe default theme as fallback.

| Area | Options |
|---|---|
| Identity | Logo (light and dark), app name, tagline |
| Colours | Primary, secondary, accent, background, text, button (a full Material 3 theme is generated) |
| Typography | Choice from a curated font list |
| Welcome page | Background image, video or slideshow, headline and subtext, button label, layout template (full-bleed, split, carousel), promo banners, language selector on or off |
| Ordering screens | Grid or list categories, card style, corner radius, photo size, veg marker style |
| Copy | Order-type labels, upsell text, thank-you message, footer |
| Receipt | Logo, header and footer lines, GSTIN, FSSAI number, feedback QR, thank-you text |
| Behaviour | Idle timeout, enabled payment methods, pay-at-counter label, languages |
| Scheduled promos | Banners shown by time of day or day of week (for example a lunch combo 12:00-15:00) |

Guardrails:

- Colour contrast is validated so screens stay readable.
- v1 ships 3-4 fixed welcome templates. A free-form designer is out of scope.
- Admin shows a live preview.
- Uploaded images and video are size-limited and processed on upload.
- Branding is versioned so apps refresh only when it changes.

## 7. Modern hotel and restaurant features

Features expected by modern hotels, QSRs and food courts, with phasing. "Backend" means new server work, "App" means client work.

| Feature | Kiosk | Counter | Phase | Notes |
|---|---|---|---|---|
| Multi-language UI (English, Hindi, regional) | Yes | Yes | P1 | ARB files, per-restaurant language list |
| Accessibility mode (large text, high contrast, reachable layout) | Yes | n/a | P1 | Needed for wheelchair users and elderly customers |
| Dietary and allergen filters (veg, vegan, Jain, gluten-free, nut-free) | Yes | Yes | P1 | Needs allergen fields on menu items |
| Spice level, portion size, nutrition and calorie info | Yes | n/a | P2 | Item metadata |
| Combos and meal builders | Yes | Yes | P1 | Combo rules in the catalog |
| Time-based menus (breakfast, lunch, happy hour) | Yes | Yes | P1 | Schedule per category or item |
| Dynamic upsell and cross-sell | Yes | Prompt only | P0/P1 | Rules first, data-driven later |
| Kitchen station routing | Yes | Yes | P0 | Reuses `KitchenStationEntity` |
| Token and order-ready display | Yes | Yes | P0 | Reuses the public display |
| Dine-in table assignment or table tent number | Yes | Yes | P1 | Reuses table entities |
| Gift cards and vouchers | Yes | Yes | P2 | Backend module |
| Loyalty points and coupons | Yes | Yes | P2 | Phone number identification |
| Corporate and staff accounts (bill to account) | n/a | Yes | P2 | Credit ledger |
| Hotel room charge (post to room folio) | n/a | Yes | P2 | Needs a PMS integration, a separate project |
| In-room dining and room service ordering | via guest QR | via counter | P2 | Room number as the order location |
| Multi-outlet and food-court mode | Yes | Yes | P2 | Outlet hierarchy, per-outlet menus and prices |
| Digital receipt (WhatsApp, SMS, email) | Yes | Yes | P2 | Notification SPI already exists |
| Feedback and rating after order | Yes | n/a | P2 | QR on the receipt or a token-screen prompt |
| Real-time prep-time estimate on the token screen | Yes | n/a | P2 | Based on kitchen load |
| Sales analytics (kiosk vs counter vs QR, upsell rate, abandonment) | Admin | Admin | P1 | Reports module |
| Fraud and safety controls (PIN overrides, audit trail) | Yes | Yes | P1 | Audit module exists |
| Remote device management (restart, config, update) | Yes | Yes | P2 | Or use an MDM |

Explicitly out of scope for now: aggregator (Swiggy, Zomato) orders on the kiosk, which stay on the existing POS integration, and PMS integration for room charges, which needs its own design.

## 8. Backend changes

All additive.

- **Migration V15:** `kiosk_device` table (restaurant_id, outlet_id, name, token hash, status, last_seen, printer_status, app_version, config JSON), `order.source` (`GUEST_QR`, `STAFF`, `KIOSK`, `COUNTER`) and `order.token_number` (daily reset per restaurant).
- **Migration V16:** `upsell_rule`.
- **Migration V17:** `restaurant_branding` (versioned, per restaurant).
- **Kiosk API:** `/api/kiosk/**` for pairing, config, heartbeat and branding, with a kiosk device principal in `SecurityConfig`.
- **Payments:** add a UPI QR capability to the `PaymentGateway` SPI. Confirmation by webhook, with a polling status endpoint as fallback.
- **Idempotent order creation** via the `Idempotency-Key` header.
- **Server-side totals:** a pricing endpoint that returns line totals, GST and grand total for a cart.
- **App version gate:** minimum supported version per app ID.
- **Admin UI (Angular):** kiosk device list, branding editor with preview, upsell rules, payment toggles, kill switch.
- **Counter POS additions (later):** shifts, discounts, refunds, cash drawer events.

## 9. Repositories, builds and deployment

| | Kiosk | Counter POS |
|---|---|---|
| Repo | `dinein-kiosk-app` | `dinein-counter-app` |
| `applicationId` | `com.heuristq.dinein.kiosk` | `com.heuristq.dinein.counter` |
| Keystore | Own, kept in CI credentials, never in git | Own |
| Flavors | `dev`, `uat`, `prod` | `dev`, `uat`, `prod` |
| Tags | `kiosk-vX.Y.Z` | `counter-vX.Y.Z` |
| Pipeline | Own Jenkins job | Own Jenkins job |

Pipeline stages: analyze, test, build signed APK or AAB, archive, publish, tag.

Distribution:

- UAT and testers: Firebase App Distribution.
- Small fleets: self-hosted signed APK with an in-app update check.
- Larger fleets: Managed Google Play private track or an MDM.

Rollout safety:

- Pilot one device first, then the rest.
- Keep the previous APK for rollback.
- Update outside service hours, or apply only when the app is idle.
- Additive API changes only, so old apps keep working during a backend release.

## 10. Hardware and Android requirements

- Lock orientation (portrait for wall-mount, landscape for floor-mount, decided per device).
- Immersive full screen, screen always on, lock task mode. Full lockdown needs device-owner provisioning, documented in a setup guide.
- Boot auto-start and crash recovery.
- Printer transport depends on the model: built-in (serial, USB or vendor SDK) or external (USB, Bluetooth ESC/POS). Hidden behind `PrinterService`.
- Touch targets at least 56dp, no hover-dependent UI, readable text at arm's length.
- Verify the mounting surface and power socket before delivery. This is a common cause of install delays.

## 11. Phases

| Phase | Scope | Effort |
|---|---|---|
| 1 | Backend: V15, kiosk API, server totals, idempotency, version gate | 1 week |
| 2 | Kiosk app: pairing, menu, customise, cart, pay-at-counter, token, `KIOSK` source on kitchen board | 2 weeks |
| 3 | Kiosk payments (UPI QR) and printing, then pilot on one device | 1.5 weeks |
| 4 | Branding backend, admin editor, welcome templates, upsell engine | 1.5 weeks |
| 5 | Kiosk operations: offline, lockdown, PIN overlay, live menu push, reports | 1.5 weeks |
| 6 | Counter POS (only after the kiosk pilot is stable) | 4 weeks |

Efforts are rough estimates and should be revisited after phase 1.

## 12. Test scenarios (kiosk)

- Add 12 items and remove 6, then check out. The cart must update correctly.
- Switch from Dine In to Takeaway mid-order. The token must reflect the change.
- Let the UPI screen time out. The kiosk must reset gracefully.
- Place a customised order. The KOT must print the modifiers.
- Drop Wi-Fi mid-order. The order must queue and sync later (pay-at-counter only).
- Double-tap submit and retry after a timeout. No duplicate orders.
- Webhook arrives before the poll, and the reverse. Payment state must stay correct.
- Printer out of paper. The order must still complete and staff must be alerted.
- Menu price changed while the kiosk is idle. The kiosk must show the new price.
- App older than the minimum version. The update screen must appear.

## 13. Open decisions

1. Kiosk hardware model (decides the printing transport).
2. UPI QR provider. Assumed Pine Labs, using the existing RailCab reference as an API source only. Never copy secrets.
3. Kiosk orientation: portrait, landscape, or both.
4. Counter POS device: tablet or dedicated terminal.
5. Launch languages.
6. Whether outlets are needed in v1. Assumed deferred.
