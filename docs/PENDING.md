# Pending items

Everything that is still open, as of 2026-09-27. All requested features are built: guest ordering, kitchen,
admin, waiter, Dine-in/Takeaway, cash and counter payments, Razorpay/PayU/Pine Labs, notifications, and `/api/v1`
versioning. Verification and the local end-to-end test are done; what remains below needs your dashboards,
credentials, a redeploy, or a real device.

Tick items off here as they are done.

## 1. Verify and deploy

- [x] Re-run the frontend checks: `npx ng lint` clean, `npx ng build` clean, `npx playwright test` 44 passed / 20
      intentionally skipped, unit tests 26 of 26 files.
- [x] The two spec files with the old broken import (`catalog-components.spec.ts`, `items-list.store.spec.ts`) are
      already committed correctly; nothing to re-stage.
- [x] All pending frontend changes are committed (the CSP fix, the `uat` build config, the Dockerfile path, the
      waiter app, Dine-in/Takeaway, admin cash payments), plus a new nginx fix below.
- [x] Ran the whole stack locally with `docker compose up --build` and walked a full order end to end — see
      section 3. Found and fixed a real bug in `frontend/nginx/default.conf` in the process:
      **`location /api/` was a plain prefix, so nginx's own regex rule for cached static assets
      (`\.(?:js|css|png|...)$`) was silently intercepting any API path ending in one of those extensions —
      concretely, `GET /api/v1/admin/tables/{id}/qr.png`, which is exactly the "Could not load the QR code" you
      hit on the UAT site. Fixed with `location ^~ /api/`, which makes it win over the regex rule. Verified locally
      that qr.png, qr.pdf, static asset caching and the WebSocket upgrade all still work.**
- [ ] Re-run the **Backend** pipeline, which applies database migration V3, then the **Frontend** pipeline — this
      nginx fix has not been deployed to UAT yet, so the QR code will still be broken there until you redeploy.
- [ ] Restart any local `bootRun` so it applies V3 and serves `/api/v1`.

## 2. Payments — needs your dashboard credentials, can't be done for you

- [ ] Add the gateway keys as Jenkins credentials. Pass them to the backend container with `withCredentials`, as
      `-e` flags in the Backend Jenkinsfile (`digital-apps/jenkins-pipeline/dine-in-qr/Backend/Jenkinsfile`):
  - [ ] Razorpay: `PAYMENT_PROVIDER=RAZORPAY`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`.
  - [ ] PayU: `PAYMENT_PROVIDER=PAYU`, `PAYU_KEY`, `PAYU_SALT`.
  - [ ] Pine Labs: `PAYMENT_PROVIDER=PINELABS`, `PINELABS_CLIENT_ID`, `PINELABS_CLIENT_SECRET`,
        `PINELABS_WEBHOOK_SECRET`. `PINELABS_BASE_URL` defaults to UAT; for live use `https://api.pluralpay.in`.

      Until this is done, online checkout fails with `PAYMENTS_NOT_CONFIGURED`. Cash and counter orders placed from
      the Waiter or Admin → New order screen still work — confirmed in the local end-to-end test below.
- [ ] Register the webhook in the provider dashboard: `http://35.154.15.238:85/api/v1/webhooks/{razorpay|payu|pinelabs}`.
      For Razorpay, subscribe to `payment.captured`, `payment.failed`, `order.paid`, `refund.processed` and
      `refund.failed`.
- [ ] Make one real test payment and one refund per configured gateway. So far all payment tests use mocked
      responses.
- [ ] Pine Labs: one UAT payment to settle the `// VERIFY:` points. They are listed in `docs/DECISIONS.md` under
      "Pine Labs Online (Plural)":
  - [ ] Recovering the payment link after a restart (does an idempotent replay return `redirect_url`?).
  - [ ] The webhook secret's format as shown in the dashboard.
  - [ ] The allowed characters in `merchant_order_reference`, and whether `failure_callback_url` is accepted.

## 3. End-to-end testing

- [x] `docker compose up --build` locally, then walked the whole flow: bootstrap owner login → forced password
      change → created a waiter account → waiter PIN login → placed a cash order for T1 (2× Paneer Tikka + add-on,
      ₹546) → order appeared in the kitchen queue with `orderType: DINE_IN` and `placedByStaff: true` →
      PREPARING → READY → waiter's Ready list showed it → served → COMPLETED → admin report summary showed 1
      order, ₹546 gross with the correct ₹13/₹13 CGST/SGST split, under `paymentChannels.CASH` and
      `orderTypes.DINE_IN`. Also checked: guest session and menu page for a second table, and the WebSocket
      upgrade through nginx (101 Switching Protocols). Everything matched what the code should do. The stack was
      torn down afterwards; no test data or containers were left running.
- [ ] Try it on a real phone (guest and waiter screens) and a tablet or TV (kitchen). So far layouts have only been
      checked in Playwright at 360, 768, 1024 and 1920 px, and in the local Docker test above.

## 4. Known gaps (optional)

### Backend
- [ ] Waiters can't switch a failed online payment to cash. Only admin has `mark-paid-offline`.
- [ ] `PaymentView` has `recordedByStaffId` but no name, so admin shows "Staff #id" when the recorder isn't the
      person who placed the order.
- [ ] "Order ready" in-app notifications target only the WAITER role. Owners and managers on the waiter screen see
      the live toast but not the inbox entries or unread count.
- [ ] Waiters can't get push notifications, because push registration is under `/api/v1/admin/**`.
- [ ] There is no order-list filter for flagged payments, so the dashboard's "Review orders" button opens today's
      orders instead.
- [ ] The settings response has no logo thumbnail URL.
- [ ] The item search returns only active items, so there is no view or restore for deleted items.

### Frontend
- [ ] There is no push-notification opt-in in admin; only in-app notifications are used.
- [ ] No unsaved-changes warning when leaving the item or settings forms.
- [ ] Kitchen: "Served" completes the order immediately, with no undo.
- [ ] Kitchen: READY tickets never turn amber or red. This was deliberate; revisit it if staff want it.

## 5. Before going live

- [ ] Add a domain and TLS, then set `APP_COOKIES_SECURE=true` and enable HSTS in `frontend/nginx/default.conf`.
      Phone cameras and some payment methods need HTTPS.
- [ ] If the domain or port changes, update `APP_PUBLIC_BASE_URL` and `APP_CORS_ALLOWED_ORIGINS`, re-register the
      webhooks and reprint the table QR codes.
- [ ] Schedule the daily `pg_dump` of the `dinein` database on the shared Postgres (commands in
      `docs/DEPLOYMENT.md`).
- [ ] Keep port 9030 (direct backend) limited to known IPs in the EC2 security group.
- [ ] Switch notifications from `LOG` to real providers when ready: `NOTIFY_EMAIL_PROVIDER`,
      `NOTIFY_SMS_PROVIDER` and `NOTIFY_PUSH_PROVIDER`, plus their credentials.

## 6. Housekeeping

- [x] Removed the leftover Pine Labs git worktree and its branch (`worktree-agent-ad8024a18bbf751d0`); its work
      was already merged into `master`.
