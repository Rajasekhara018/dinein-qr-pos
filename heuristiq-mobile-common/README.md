# hq_flutter_common

The Flutter counterpart of `Rider-angular-common/projects/hq-shared-lib`. The investor app (`realboom-invest-flutter`)
builds every screen from these widgets so it looks and behaves like the web apps. Prefix `Hq`.

- **No translation dependency.** Every user-visible text is a constructor parameter (or a `*Texts` object). The app
  translates (its ARB files) and passes the result in. `HqDataListTexts.english()` and `HqFileUploadTexts.english()`
  exist for tests and the example only.
- **One owner per fact.** Privileges come from UAM (`GET /v1/uam/identity/me/privileges`), screen views go to
  Rider_AuditService, files go to Rider_DMS, all through the gateway. The package holds no app config: the app injects it
  (`HqConfig`, `HqApiClient`, `HqFileUploader`).

## Use

```yaml
dependencies:
  hq_flutter_common:
    path: ../heuristiq-mobile-common
```

```dart
MaterialApp.router(
  theme: HqTheme.light(),
  darkTheme: HqTheme.dark(),
  scaffoldMessengerKey: toast.messengerKey,
  ...
);
// above the router:
HqConfigScope(config: HqConfig(gatewayUrl: 'https://host/api'), child: HqPrivilegeScope(access: access, child: app))
```

## Component mapping: web to Flutter

| Web (`hq-shared-lib`) | Flutter | Notes |
|---|---|---|
| theme/styles.scss tokens | `HqTokens`, `HqTheme.light/dark`, `context.hq`, `HqSpacing`, `HqRadius`, `HqSizes`, `HqFontScale` | Light and dark copied from the web. Dark tone colours and stat-card tones are derived (not defined for dark on the web). Named web themes (ocean, forest...) are reachable with `HqTheme.light(primary: ...)`. |
| `hq-page-header` | `HqPageHeader` | Same inputs; `actions` replaces the projected buttons. |
| `hq-common-table` + `hq-table-footer` | `HqDataList` + `HqListFooter` | Phone version: card rows (first column is the title), search, sort menu, footer. Client-side or `serverSide: true`. Page is zero-based. |
| `hq-field` | `HqField` | Text, email, password (toggle), number, phone, url, multiline. Optional `validator` (joins a `Form`), `onTap` (read-only pickers) and `fieldKey`. Dates: `HqDateField`. |
| `hq-form-field` | `HqFormField` | Label, hint, error, required, inline. |
| `hq-searchable-select` | `HqSearchableSelect<T>` | Opens a searchable bottom sheet; single or multiple; loading / retry. `HqOption<T>`. |
| `hq-checkbox` | `HqCheckbox` | Indeterminate, error, compact. |
| `hq-radio-group` | `HqRadioGroup<T>` | Drawn with icons and explicit semantics. |
| `hq-amount-input` | `HqAmountInput`, `hqFormatMoney`, `hqParseDecimalToMinor` | Paise precision, Indian grouping for INR, currency from `HqConfig.defaultCurrency`, value reported as plain text and integer minor units. |
| `hq-phone-number-input` | `HqPhoneNumberInput`, `HqPhoneValue` | Country picker by ISO code and dial code (no translated names); India strict, others by length; reports E.164. Optional `controller`, `validator(HqPhoneValue)`, `textInputAction`, `onSubmitted`, `focusNode`, `fieldKey`. |
| `hq-form-grid`, `hq-form-actions` | `HqFormGrid`, `HqFormGridItem`, `HqFormActions` | Grid falls back to fewer columns on narrow screens. |
| form (Angular forms) | `HqForm` | Wraps Flutter `Form`. |
| `hqButton` | `HqButton` | Colours primary, accent, warn, success, secondary; appearances filled, outline, text; sizes sm, md, lg; loading, block, icon only. |
| `hq-status-badge` | `HqStatusBadge` | Tones success, warning, danger, info, neutral. |
| `hq-stat-cards` | `HqStatCards`, `HqStatCardItem` | `fill`, `flush`. |
| `hq-filter-bar` | `HqFilterBar` | Collapsible. |
| `hq-tabs` | `HqTabs<T>`, `HqTab<T>` | Appearances tabs, segmented, pills. |
| `hq-drawer` | `HqDrawer.show` | Tall bottom sheet. |
| `hq-modal` | `HqModal.show` | Content-sized bottom sheet. |
| `HqAppDialogService` | `HqDialog.confirm / alert / prompt` | `confirm(tone: HqDialogTone.warning)` adds an amber caution badge (`destructive` stays the red one). The multi-field `form()` dialog is not ported: use `HqModal` + `HqForm`. |
| `HqNotificationService` + `HqToastService` | `HqToastService` | success 3 s, error 5 s, warning 4 s, info 3 s; give `messengerKey` to `MaterialApp`. |
| `HqPrivilegeAccessService.can()` | `HqPrivilegeAccess.can()` | Unknown privileges allow (UI hint; the gateway is the real gate). |
| `*hqHasPrivilege` | `HqHasPrivilege` | |
| `permissionGuard` + `HQ_PERMISSION_PRIVILEGES` | `HqPermissionGuard.redirect` (go_router `redirect:`), `HqPermissionGate` | The key-to-privileges map belongs to the app. |
| `ScreenViewService` | `HqScreenAudit.report(screenKey)` | Same endpoint, payload, batching (20, 15 s, 200 cap), 429 pause. `appChannel` defaults to `CUSTOMER_APP`. |
| `hq-doc-upload`, `hq-multi-doc-upload`, `hq-file-drop` | `HqFileUpload` | See below. |
| `HqDocumentService` | `HqDmsUploader` | Multipart `{file, category, ownerId, checksum, documentType}` to `POST /v1/dms/documents`. |
| `HQ_AMOUNT_DEFAULT_CURRENCY`, `HQ_PHONE_DEFAULT_COUNTRY`, `HQ_CONFIG` | `HqConfig` + `HqConfigScope` | |
| `hq-step-rail` | `HqStepper`, `HqWizard` | Horizontal or vertical; completed / current / error / disabled. The wizard adds Back, Next / Submit, Save draft and per-step `validate`. |
| `hq-date-time-input` (date), `hq-date-range` | `HqDateField`, `HqDateRangePicker` | Plain dates; presets are passed in. |
| `hq-slide-toggle` | `HqSwitch` | Loading and compact. |
| `hq-language-switcher` | `HqLanguagePicker`, `hqSupportedLanguages` | The 7 platform languages by endonym; Arabic is RTL. |
| `hq-skeleton`, tooltip | `HqSkeleton`, `HqTooltip` | |
| charts | `HqLineChart`, `HqBarChart`, `HqPieChart`, `HqGauge`, `HqHeatmap`, scatter / radar, `HqDashboard` | On `fl_chart`. |
| (mobile only) | `HqAppShell`, `HqBottomNav`, `HqPullToRefresh`, `HqInfiniteList` | Bottom bar on phones, rail from 600 px, items filtered by privilege. |
| (mobile only) | `HqOtpInput`, `HqPinInput` | OTP with resend countdown and SMS auto-read (below); PIN shows dots. |
| (mobile only) | `HqBiometricAuth`, `HqBiometricLock`, `HqBiometricButton`, `HqBiometricNotice` | Face ID / fingerprint unlock (below). |
| (mobile only) | `HqTextArea`, `HqSearchBar`, `HqCopyField`, `HqQrCode`, `HqSlider`, `HqRangeSlider`, `HqStepperCounter` | |
| (mobile only) | `HqAvatar`, `HqBadge`, `HqChip(Group)`, `HqCard`, `HqKeyValueList`, `HqTimeline`, `HqDocumentTile`, `HqAccordion`, `HqMaskedValue`, `HqMoneyText`, `HqAlert`, `HqBanner`, `HqEmptyState`, `HqErrorState` | |
| not ported | | address input, date-time input (time part), legal profile, onboarding form, multi-field `HqDialog.form()`, idle timeout (the app shell owns it). Add them here when a screen needs one. |

## Mobile finish

The widgets mirror the web components in function but are drawn for phones, so an app looks native without extra code:

- **Fields** (`HqField` and everything built on it: search bar, phone, amount, text area, copy field; plus the select and date fields): filled, no border at rest, a 1.5 px primary ring when focused, a warn ring on error, 52 px high, label above. `HqField.trailing` puts an always-visible widget at the end (a suggestions arrow, an info button).
- **Tabs** (`HqTabs`): segmented and pills are a soft track with a selected thumb (primary, or the brand gradient) that slides between tabs when `block` is set; scrolling pills fill when selected; `tabs` is a sliding underline.
- **Buttons** (`HqButton`): 40 / 48 / 52 px (sm / md / lg), semibold labels, press feedback (scale and dim) and a light haptic (`haptic: false` to turn it off). `HqButtonColor.inverse` is white on a coloured or gradient surface; `HqButton.leading` draws a widget such as a partner logo before the label.
- **Badges and chips**: soft pills with a hairline; a status badge without an icon shows a tone dot; chips fill with the primary when selected.
- **Cards and stat cards**: 18 px radius, lifted by a soft layered shadow (light) or a hairline (dark) instead of an outline.
- **Sheets** (`HqModal`, `HqDrawer`, the select sheet): 28 px top corners, drag handle, bold title, the close button in a tinted circle, a pinned footer above the home indicator, and 52 px option rows with the chosen one tinted.
- **Toasts** (`HqToastService`): a floating card with the tone in a tinted icon circle; `HqToastAction` adds a button (e.g. Undo) and `closeLabel` a close button, and a toast with an action still closes by itself.
- **Switch and checkbox**: the platform-adaptive switch (iOS style on Apple devices) with a white thumb; rounded checkboxes.
- All motion is short (120 to 200 ms) and switches off when the system asks for reduced motion (`HqMobile.motion`).

`HqBrandStyle` (a `ThemeExtension`, optional) adds a brand on top: a gradient and glow on the primary button and the selected tab, control and dialog radii, and `stateBadge` (empty and error states with a tinted icon badge that fades in). `HqDialog.confirm(icon: ...)` shows the featured confirm layout. The shared look lives in `HqMobile` and `HqPressable` (`src/theme/hq_mobile_finish.dart`) for apps that build their own widgets.

## Errors

The package never hands a plugin's `PlatformException` to the app. Each failure is a typed value the app maps to its
own translated text: `HqApiException` (HTTP), `HqPickerException` → `HqPermissionDeniedException` /
`HqPickerUnavailableException` (file picker), `HqBiometricOutcome` (biometric prompt) and `null` from
`HqSmsCodeReader.read` (no code). Widgets that catch these show the matching text from their `*Texts` object.

## OTP auto-read

```dart
HqOtpInput(
  length: 6,
  onCompleted: verify,
  smsReader: HqAndroidSmsCodeReader(senderPhoneNumber: 'RLBOOM'),  // Android; ignored elsewhere
  onResend: resend, resendLabel: t.resend, resendCountdown: (s) => t.resendIn(s),
)
```

- **iOS:** nothing to set up. The field sets `AutofillHints.oneTimeCode`, so the keyboard shows the code from Messages
  ("From Messages 482913") and one tap fills it. No app can read SMS on iOS. Keep the code in the SMS text as plain
  digits. Optionally end the SMS with `@<app-domain> #482913` (domain-bound code, needs Associated Domains) so iOS offers
  it only in this app.
- **Android, `HqSmsReadMode.userConsent` (default):** Google Play services shows "Allow RealBoom to read this
  message?" when the SMS arrives, and the code fills on Allow. No SMS template change and no permission.
  `senderPhoneNumber` limits it to the NMS sender id.
- **Android, `HqSmsReadMode.retriever`:** fully automatic, no prompt. The SMS must be at most 140 bytes, contain the
  code and end with the app's 11-character hash (`HqAndroidSmsCodeReader.appSignature()`). The hash depends on the
  signing key, so the Rider_NMS OTP template must carry the **release / Play App Signing** hash.
- `READ_SMS` / `RECEIVE_SMS` are never needed; Play rejects apps that ask for them for OTP.
- The reader starts when the field appears, restarts on resend and stops on dispose. Typing always works. The code is
  never logged or stored.

## Biometric unlock (Face ID / fingerprint)

```dart
final status = await biometrics.status();               // available / notEnrolled / unsupported + kinds
if (!status.isAvailable) HqBiometricNotice(messages: m, status: status);   // "This device does not support..."

final lock = HqBiometricLock(auth: HqLocalBiometricAuth(), store: HqDeviceSecureStore(), keyPrefix: 'invest');
await lock.enable(refreshToken, texts);                 // after password + OTP sign-in, asks for face / finger first
final r = await lock.unlock(texts);                     // r.isSuccess → r.secret; otherwise HqBiometricNotice(outcome: r.outcome)
await lock.disable();                                   // on sign-out and when the server rejects the token
```

- **Device checks:** `status()` reports `unsupported` (no sensor, or the OS cannot do it) and `notEnrolled` (a sensor
  but no face / finger set up). Prompt results map to `HqBiometricOutcome`: cancelled, locked out (temporary),
  permanently locked out, no device passcode (iOS), not available, failed. `HqBiometricMessages` holds one translated
  text per case, and `HqBiometricNotice` shows it (nothing for success or a plain cancel).
- **What it protects:** the OS checks the face or finger and the app only learns pass or fail. The secret is protected
  by the Keychain / Keystore (`unlocked_this_device`, never synced or restored to another phone) and the device lock,
  not cryptographically by the biometric. So the server treats a biometric unlock as "holds this device's refresh
  token" and nothing more. The token is device-bound, short-lived and revocable, and high-value actions (payment,
  payout bank change, nominee change) still need an OTP.
- A permanent lockout or removed enrolment wipes the secret; the user signs in with password + OTP again.

### Platform setup (the app, not this package)

Android: `MainActivity` must extend `FlutterFragmentActivity` (otherwise every prompt is `failed`), and add
`<uses-permission android:name="android.permission.USE_BIOMETRIC"/>`. iOS `Info.plist`:

```xml
<key>NSFaceIDUsageDescription</key><string>Unlock RealBoom with Face ID.</string>
```

Without it iOS ends the app the first time it asks for Face ID. Use localized `InfoPlist.strings` for the real text.

## Sign in with Face ID / fingerprint (device key)

```dart
final key = HqHardwareDeviceKey(keyAlias: 'pkm.signin');   // web: isSupported() is false
if (await key.isSupported()) {
  final c = await key.create(texts: texts);                 // biometric prompt; c.keyPair.publicKeyBase64Spki + .algorithm.code
  // send the public key to the server (enrol); on failure: await key.delete();
}
final s = await key.sign(challenge, texts: texts);          // biometric prompt; s.signatureBase64, or s.outcome / s.keyLost
await key.delete();                                         // when the user switches it off
```

- Unlike `HqBiometricLock`, this is cryptographic: the private key is created in the Android Keystore / iOS Secure
  Enclave (package `biometric_signature`), is biometric only (no device PIN fallback) and is invalidated when faces /
  fingers are added or removed. The server stores the public key and verifies each signature.
- What is produced: `EC_P256_SHA256` on Android and iOS (`HqDeviceKeyAlgorithm.code`); the public key is standard base64
  of the X.509 SubjectPublicKeyInfo DER (`hqNormalizeSpkiBase64` strips PEM and wraps a raw P-256 point); the signature
  is standard base64 of the DER ECDSA signature (SHA256withECDSA) over the UTF-8 bytes of the payload.
- Failures map to `HqBiometricOutcome` (show them with `HqBiometricMessages`); `keyLost` means the key was invalidated
  or is gone: delete it and let the user switch the feature on again. `HqMemoryDeviceKey` is a fake for tests.
- Platform setup: as above (`FlutterFragmentActivity`, `USE_BIOMETRIC`, `NSFaceIDUsageDescription`); Android also needs
  `minSdk` 23+ and `compileSdk` 35+ (the plugin uses `androidx.biometric` 1.4).

## HqFileUpload

```dart
HqFileUpload(
  picker: HqDeviceFilePicker(),
  uploader: HqDmsUploader(gatewayUrl: ..., tokenProvider: ..., category: 'KYC', ownerId: userId, documentType: 'PAN_CARD'),
  texts: texts,                       // translated HqFileUploadTexts
  accept: ['image/*', 'application/pdf'],
  typeHints: 'PDF, JPEG, PNG',
  maxBytes: 9 * 1024 * 1024,          // DMS limit; minimum is 50 bytes
  maxFiles: 2,
  allowCamera: true,
  imageOptions: const HqImageOptions(maxDimension: 2048, quality: 85),
  onChanged: (documentIds) { ... },   // DMS ids of the files that uploaded
)
```

Source by `accept`: only images opens the gallery (Gallery / Camera sheet with `allowCamera`); only documents opens the
system file manager filtered to those types; a mix shows a sheet with Gallery, Camera, Files. `maxFiles` above 1 allows
multi-select. Each file is checked for type (extension, exact MIME or `image/*`, same rule as the web `isAcceptedFile`),
minimum size and `maxBytes` before upload. Images are downscaled and re-encoded to JPEG when `imageOptions` is set, which
drops EXIF (including GPS); the size limit applies to the processed file. The control shows progress, retry, remove (which
deletes the DMS document) and a preview (image thumbnail, file icon). When photos or camera access is refused it shows the
passed-in message with an "open settings" action.

Limits to know: `http` cannot report upload progress, so `HqDmsUploader` goes from 0 to 1 when the call ends (supply
another `HqFileUploader` for a moving bar). The `image` package cannot decode HEIC; such a file is uploaded unchanged
(EXIF not stripped) unless `image_picker` already converted it. Check HEIC behaviour on a real iPhone.

### Platform setup (the app, not this package)

Android `AndroidManifest.xml`:

```xml
<!-- Camera source only. The gallery uses the Android 13+ system photo picker and needs no storage permission. -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

Do not add `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES`: `image_picker` uses the photo picker on Android 13+ and the
system document picker below it. `file_picker` uses the system file manager, no permission.

iOS `Info.plist`:

```xml
<key>NSCameraUsageDescription</key><string>Take a photo of your document for KYC.</string>
<key>NSPhotoLibraryUsageDescription</key><string>Choose a photo of your document for KYC.</string>
```

Use localized `InfoPlist.strings` for the real texts. The gallery uses PHPicker, which works with "Limited Photos"
access without asking. If the user refuses the camera, iOS will not ask again: the "open settings" action is the only way
back, which is why the control shows it. `permission_handler` is used only for `openAppSettings()`.

## Tests

```
flutter test --coverage
```

Widget tests exist per component, with a fake picker, fake uploader and fake image processor for `HqFileUpload` and
`MockClient` for HTTP. Coverage targets are in the repository `CLAUDE.md` (90% target, 70% floor).

## Example

`example/` is a small app showing every component. `cd example && flutter run`.
