Kiosk App: Plan and Features
Written for: you, as the owner and lead developer deciding what to build.

The repo already has most of the backend a kiosk needs. Menu, orders, payments, realtime, kitchen board and RBAC are all in place. The kiosk is mainly a new Angular app at /kiosk, plus a few backend additions.

What to reuse and what is missing
Kiosk need	Already in repo	Gap
Menu, variants, addons, GST	Menu entities, snapshotted order lines	Kiosk-specific ordering (upsell, sort, images)
Order flow	DINE_IN / TAKEAWAY, OrderNumberService, kitchen board	Token numbers, kiosk order source
Online payments	Razorpay, PayU, Pine Labs behind a PaymentGateway SPI	Dynamic UPI QR with polling (current flows are hosted redirect and Checkout.js)
Pay at counter	Offline payments (CASH, UPI_AT_COUNTER, CARD_AT_COUNTER)	Token-based "pay at counter" flow
Device auth	Kitchen DeviceTokenEntity, heartbeat monitor	Kiosk device type and pairing
Realtime	STOMP /ws, public display	Kiosk config and menu-change push
Receipts and KOT	None	Thermal printing
Offline mode	PWA service worker only	Order queue and sync
Multi-outlet	Restaurant-level tenancy only	Outlet or branch layer (optional, later)
The Petpooja article describes pairing with an external POS. Here you are the POS, so "pairing" becomes registering a kiosk device against your own restaurant. There is no third-party integration to build.

Features
P0: MVP (a customer can order and pay at a kiosk)
Kiosk device registration. An admin generates a pairing code in Admin, and the kiosk enters it and receives a device token (like the kitchen token). Add a KIOSK device type. Bind each device to a restaurant, with an optional outlet and a name.
Attract screen. Idle loop with a "Touch to order" prompt. It resets after inactivity.
Order type choice. Dine In or Takeaway, changeable until checkout.
Menu browsing.
5–7 category tabs, large touch targets and item photos.
Veg/non-veg marker, and out-of-stock items hidden or greyed out.
Kiosk-first layout: 1080×1920 portrait and 1920×1080 landscape, no hover states.
Item customization. Variants, addons and "no onion / extra cheese" notes. These modifiers must show up on the KOT.
Cart. Add, remove and edit items, with a live GST breakdown. Respect the prices_include_gst setting.
Upsell prompts. Per-item and at-checkout suggestions such as a dip with a burger, a drink with a combo, and a dessert at the end. Configure these in Admin.
Checkout and payment.
Dynamic UPI QR, generated after the customer confirms, with status polling.
Cancel and retry, and a payment timeout that resets the screen gracefully.
"Pay at counter" toggle: the customer gets a token, then pays cash or card at the counter. Reuse the offline payment path.
Card tap is a later phase (see P2).
Token and confirmation screen. It shows a large token number, then returns to the attract screen after a few seconds. Orders go straight to the kitchen board with a source badge of KIOSK.
Receipt printing. Thermal receipt with token, items, GST and payment mode, plus a KOT print for the kitchen.
Idle and session safety. Auto-reset with a countdown ("Still there?") and no data left behind between customers.
P1: Operations (needed before going live)
Kiosk admin panel.
Device list with online/offline status from heartbeat, last-seen time and printer status.
Enable or disable kiosk ordering.
Toggles for pay-at-counter, UPI and card.
Upsell rule editor, and per-item "hide on kiosk" and photo management.
Staff roles.
New KIOSK_HELPER permission for restarting a kiosk, cancelling an order and reprinting.
Handoff view: a token board showing "ready" orders. The existing public display mostly covers this.
Counter backup: pending "pay at counter" orders appear in the waiter/staff view for collection.
Staff PIN overlay on the kiosk. Long-press a hidden corner to enter a PIN and access restart, reprint and exit kiosk mode.
Offline mode.
Queue orders locally (IndexedDB) when Wi-Fi drops.
Only pay-at-counter orders can be placed offline. UPI needs connectivity.
Sync when back online, with idempotency keys to prevent duplicates.
Cache the menu with a version stamp.
Menu freshness. Push menu, price and stock changes to kiosks over STOMP, so stock-outs take effect within seconds.
Reports. Kiosk vs counter vs QR split, average order value, upsell attach rate, payment-mode split, abandonment, and end-of-day kiosk reconciliation.
Audit log entries for kiosk actions and staff overrides. The audit module already exists.
P2: Later
Card tap via a Pine Labs POS terminal integration (a separate integration from the online gateway).
Outlet/branch hierarchy with central menu and price pushes, plus per-outlet overrides.
Multi-language (Hindi and regional), an accessibility mode (large text, high contrast, reachable UI) and loyalty or phone number capture.
Digital receipt via WhatsApp or SMS, and remote kiosk config.
Camera-free analytics: dwell time, drop-off screen and heatmap of taps.
Technical plan
Backend

Migration V15: kiosk_device table (restaurant_id, outlet_id, name, token hash, status, last_seen, printer_status, config JSON). Add order.source (GUEST_QR, STAFF, KIOSK) and order.token_number.
Migration V16: upsell_rule (trigger item or category, suggested items, placement, sort order).
kiosk package with pairing, config and heartbeat endpoints (/api/kiosk/**), plus a KIOSK role or device principal in SecurityConfig.
Payments: add a createUpiQr capability to the PaymentGateway SPI. Pine Labs has a UPI QR API in the RailCab reference, so use it as the API source without copying secrets. Verify status through webhook plus polling fallback. Keep the webhook as the source of truth.
Idempotent order creation (Idempotency-Key) for offline sync.
Token numbering: daily reset per restaurant, extending OrderNumberService.
Print payload endpoints (receipt and KOT as ESC/POS-ready JSON or text).
Frontend (Angular 21)

New features/kiosk module, lazy-loaded at /kiosk. Reuse the guest cart and item-sheet components where possible, restyled for kiosk.
Kiosk state machine with signals: idle → orderType → menu → cart → payment → confirmation → idle.
Service worker caching plus an IndexedDB order queue.
Printing: this is the biggest technical decision. A browser cannot print silently to a thermal printer. See the question below.
Hardware and deployment

Chrome in kiosk mode on a Windows or Android kiosk, with auto-start and a locked-down shell. Wall- or floor-mount hardware with a built-in thermal printer (Addsoft, Posbank).
A small local print agent, or a WebUSB/WebSerial or vendor SDK route, depending on the printer.
Phases
Phase	Scope	Rough effort
1	Device pairing, kiosk shell, browse, customize, cart, pay-at-counter, token, kitchen KIOSK source	2 weeks
2	UPI QR payment, receipt and KOT printing	1.5 weeks
3	Admin panel, upsell engine, roles and PIN overlay, menu push	1.5 weeks
4	Offline queue, reports, audit, hardening	1.5 weeks
5	P2 items as needed	Unscheduled
Test checklist (from the article's scenarios)
Add 12 items and remove 6. Switch from Dine In to Takeaway mid-order. Let the UPI screen time out. Place a customised order and check the KOT shows the modifiers. Drop the Wi-Fi mid-order. Also add duplicate-submit and double-payment tests, and a webhook-arrives-before-poll race.

Decisions I need from you
Printing: which hardware and approach? A local print agent is the most robust, and WebUSB is simpler but less reliable.
UPI QR: should it go through Pine Labs (the reference code you have) or another gateway?
Orientation and target: portrait wall-mount, landscape floor-mount, or both?
Outlets: should the outlet layer be in v1 or deferred?
Once you answer these, I can start Phase 1 with the migration and the kiosk backend package.


I've sent the survey to the caller through SubagentHandback. In short:

Stack: Java 21 / Spring Boot 3.5 backend, PostgreSQL 16, Angular 21 frontend.
Payments: Razorpay, PayU and Pine Labs Online are implemented.
Multi-tenant: restaurant-level tenancy exists, with no outlet hierarchy.
Not found: no printing or KOT code, no offline order queue, and no kiosk code.
There is no CLAUDE.md.

The survey agent has finished, and I've already used its report in the plan above. Nothing new has come in from you since then, so I haven't started any work.

I need your answers to four questions before I start Phase 1:

Printing: which hardware and approach? A local print agent is the most robust, and WebUSB is simpler but less reliable.
UPI QR: should it go through Pine Labs (using the RailCab reference code) or another gateway?
Orientation: portrait wall-mount, landscape floor-mount, or both?
Outlets: should the outlet layer be in v1 or deferred?