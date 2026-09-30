# Counter POS setup guide

For whoever sets up the billing counter. About 10 minutes.

## What the counter app does

- **Kiosk orders:** customers who ordered on the self-order kiosk come to the counter and say their token number.
  The cashier types the token, checks the amount, asks how the customer is paying (cash, UPI or card), and confirms.
  Confirming the payment is what sends the order to the kitchen. Unpaid orders wait here; if one waits past the
  15-minute payment timeout it is flagged "waited a while" but can still be paid.
- **New order:** the cashier taps items (items with sizes or extras open their options first), chooses
  takeaway or dine-in (dine-in needs a table), takes payment, and the order goes to the kitchen.
- **Bill printing:** a bill prints after every payment. A printer problem never blocks a sale; the screen tells the
  cashier to give the customer their token.

Prices, GST and totals always come from the server. The tablet only sends item ids and quantities, so a wrong
price on the tablet cannot be charged.

## Sign in

Waiters sign in with **username and PIN**. Managers and owners use their **password**. Kitchen accounts cannot use the
counter; they sign in on the kitchen screen. The tablet remembers the login for up to 7 days of inactivity, so a
restart does not ask for the password again. **Sign out** from Settings when the shift ends.

## Printer

1. **Settings > Bill printer.**
2. Enter the printer's IP address (usually port 9100) and choose 80 mm or 58 mm paper.
3. **Print test slip.** The line of digits should be exactly as wide as the divider above it.

Give the printer a fixed IP or a DHCP reservation. If the address changes, printing stops quietly.

A printer built into a POS terminal (USB, serial or vendor SDK) or a Bluetooth printer needs a small addition that
implements `PrinterTransport` for that model. Bills are plain ASCII: the rupee sign prints as "Rs." and
non-English item names print as "?".

## Installing

- Point the build at your server: `--dart-define=COUNTER_API_BASE=https://your-server`.
- The app runs in landscape and is meant for a tablet of about 10 inches.
- Debug builds allow plain HTTP for a local server; release builds require HTTPS.

## Not in this version

- Discounts, refunds and manager PIN overrides.
- Shift open/close, cash reconciliation and an end-of-day report.
- Cash drawer opening, hold/recall and split bills.
- UPI QR shown on screen: the cashier records that the customer paid, they do not pay through the app.
- Working offline. Every action needs the server; the app says so instead of queueing a sale it cannot confirm.
