# Kiosk setup guide

For whoever installs a kiosk in an outlet. Allow about 20 minutes per device.

## 1. Before the device arrives

- A power socket within 2 metres of where the kiosk stands, and a mounting surface that can take the weight
  (a glass partition cannot).
- Wi-Fi at the kiosk. The kiosk needs the internet only to reach the ordering server.
- The kiosk's printer, if it has one, on the same network as the kiosk (see section 5).

## 2. Create the kiosk in Admin

1. Sign in to Admin as owner or manager and open **Kiosks**.
2. **Add kiosk**, give it a name such as "Entrance kiosk".
3. Admin shows a **6-digit pairing code once**. It expires after 15 minutes. If it expires or is lost, choose
   **New pairing code** on that kiosk.

## 3. Install and pair the app

1. Install the signed APK on the device.
2. Open the app. Enter the 6 digits. The welcome page appears with your branding.
3. Set the look under **Kiosks > Kiosk appearance**: logo, colours, headline, button text, idle timeout.

Pointing the app at a different server is a build-time choice:
`--dart-define=KIOSK_API_BASE=https://your-server` and `--dart-define=KIOSK_DEMO=false`.
The default build runs in demo mode with a built-in menu, for trying the app without a server.

## 4. Lock the device to the app

The kiosk pins itself with Android's lock task mode so customers cannot leave the app.

**Simple setup (a confirmation prompt appears once).** Make the kiosk the home app: press Home, choose
**Self-Order Kiosk** and **Always**. Android also starts it at boot and brings it back after a crash. On first
launch Android asks to pin the screen; accept. Staff can leave with the system pinning gesture.

**Recommended for a public device (no prompt, cannot be bypassed).** Provision the app as device owner on a
factory-fresh device, before any Google account is added:

```
adb shell dpm set-device-owner com.heuristq.dinein_kiosk/.KioskDeviceAdminReceiver
```

It fails with "already several accounts" if an account exists; factory-reset and retry. To remove device-owner
status later, unpair and reset the device.

**Staff access.** Press and hold the top-left corner of any screen for about a second. Enter a staff username and
PIN (waiter, manager or owner accounts that have a PIN). The service menu can:

- go back to the welcome screen
- reprint the last receipt, and print a test slip
- open Printer settings
- **Exit kiosk mode**, which lifts the lock until the app is next opened
- unpair the kiosk

Wrong PINs are counted against the staff account and lock it after too many attempts, the same as everywhere
else in the system.

## 5. Printer

The kiosk prints the customer's **token slip** (token number, items, total, "pay at the counter") right after an
order. A printer problem never blocks an order; the customer's screen tells them to remember their token.

- **Network printer (built in).** In the service menu open **Printer settings**, enter the printer's IP address
  (usually port 9100) and pick 58 mm or 80 mm paper, then **Print test slip**. Give the printer a fixed IP or a
  DHCP reservation, or the address changes and printing quietly stops.
- **Printer built into the kiosk over USB/serial or a vendor SDK.** Not covered by the built-in transport; it needs
  a small addition that implements `PrinterTransport` for that model. Tell us the kiosk model.
- The slip is plain ASCII: the rupee sign prints as "Rs." and non-English item names print as "?" because thermal
  printers differ in code pages. Item names should also have an English form.

## 6. How customers pay today

The kiosk supports **pay at counter** only. The customer gets a token and pays at the counter, where the cashier
opens the **Kiosk orders** screen in the waiter app, finds the token, and takes cash, UPI or card. Payment
confirmation is what sends the order to the kitchen. An order that is not paid within 15 minutes is marked
expired but can still be paid.

Keep one billing counter open, and put a floor helper near the kiosk for the first weeks.

## 7. When the network drops

- The kiosk keeps showing the last saved menu, with a notice.
- It cannot place an order without a connection (the customer sees "Can't reach the server" and can order at the
  counter). Orders are not queued offline, deliberately: a token number can only come from the server, and a
  queued order the cashier cannot find would be worse than none.
- Menu and price changes arrive within seconds when online. Each new customer also starts on the freshest menu.

## 8. Forcing an update

Set `app.kiosk.min-version` (for example `1.2.0`) on the server. Kiosks running an older version show an
"update required" screen and send customers to the counter. Leave it empty to disable the check.

## 9. Turning kiosk ordering off

**Kiosks > Kiosk appearance > Kiosk ordering** switches off new kiosk orders instantly for the restaurant. The
counter keeps working.
