import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';
import 'package:local_auth_android/local_auth_android.dart';
import 'package:local_auth_darwin/local_auth_darwin.dart';

import '../feedback/hq_alert.dart';
import '../ui/hq_button.dart';

/// A kind of biometric sensor.
enum HqBiometricKind { face, fingerprint, iris, other }

/// Whether biometric sign-in can be offered.
enum HqBiometricAvailability {
  /// Hardware present and at least one face or finger enrolled.
  available,

  /// Hardware present but nothing enrolled: offer the system settings, not the feature.
  notEnrolled,

  /// No hardware, or the platform does not support it.
  unsupported,
}

/// What the device offers.
@immutable
class HqBiometricStatus {
  const HqBiometricStatus(this.availability, [this.kinds = const <HqBiometricKind>{}]);

  static const HqBiometricStatus unsupported = HqBiometricStatus(HqBiometricAvailability.unsupported);

  final HqBiometricAvailability availability;
  final Set<HqBiometricKind> kinds;

  bool get isAvailable => availability == HqBiometricAvailability.available;

  /// The kind to name in the UI: face first (Face ID), then fingerprint, then iris. Android often reports only "strong" /
  /// "weak", which is [HqBiometricKind.other]: say "biometrics" then.
  HqBiometricKind? get primary {
    for (final k in const [HqBiometricKind.face, HqBiometricKind.fingerprint, HqBiometricKind.iris, HqBiometricKind.other]) {
      if (kinds.contains(k)) return k;
    }
    return null;
  }
}

/// The result of a prompt.
enum HqBiometricOutcome {
  success,

  /// The user cancelled, chose the fallback, or the face / finger did not match.
  cancelled,
  notAvailable,
  notEnrolled,

  /// Too many attempts; try again later (Android about 30 s).
  lockedOut,

  /// Too many attempts; only the device PIN / passcode unlocks the sensor again. Disable biometric unlock and sign in with
  /// password + OTP.
  permanentlyLockedOut,

  /// iOS: no device passcode, so biometrics cannot be used.
  passcodeNotSet,

  /// Anything else (no foreground activity, `MainActivity` not a `FlutterFragmentActivity`, another prompt open).
  failed,
}

/// The prompt's texts, already translated by the app (this package has no translation dependency).
@immutable
class HqBiometricTexts {
  const HqBiometricTexts({required this.reason, this.cancel, this.androidTitle, this.androidHint, this.iosFallback = ''});

  /// Why the app asks, shown in the system prompt ("Unlock RealBoom"). Required by both platforms.
  final String reason;
  final String? cancel;

  /// Android prompt title and the line under it.
  final String? androidTitle;
  final String? androidHint;

  /// iOS fallback button ("Use password"); empty hides it, so the user cancels and signs in the normal way.
  final String iosFallback;
}

/// Asks the operating system to check the user's face or fingerprint. The app never sees biometric data: it only learns
/// whether the check passed.
abstract interface class HqBiometricAuth {
  Future<HqBiometricStatus> status();

  /// Shows the system prompt. [biometricOnly] false also accepts the device PIN / passcode.
  Future<HqBiometricOutcome> authenticate(HqBiometricTexts texts, {bool biometricOnly = true});
}

/// The real implementation over `local_auth` (Face ID / Touch ID on iOS, BiometricPrompt on Android).
class HqLocalBiometricAuth implements HqBiometricAuth {
  HqLocalBiometricAuth({LocalAuthentication? auth}) : _auth = auth ?? LocalAuthentication();

  final LocalAuthentication _auth;

  @override
  Future<HqBiometricStatus> status() async {
    try {
      if (!await _auth.isDeviceSupported() || !await _auth.canCheckBiometrics) return HqBiometricStatus.unsupported;
      final enrolled = await _auth.getAvailableBiometrics();
      if (enrolled.isEmpty) return const HqBiometricStatus(HqBiometricAvailability.notEnrolled);
      return HqBiometricStatus(HqBiometricAvailability.available, {for (final t in enrolled) _kind(t)});
    } on PlatformException {
      return HqBiometricStatus.unsupported;
    } on MissingPluginException {
      return HqBiometricStatus.unsupported;
    }
  }

  static HqBiometricKind _kind(BiometricType type) => switch (type) {
        BiometricType.face => HqBiometricKind.face,
        BiometricType.fingerprint => HqBiometricKind.fingerprint,
        BiometricType.iris => HqBiometricKind.iris,
        _ => HqBiometricKind.other,
      };

  @override
  Future<HqBiometricOutcome> authenticate(HqBiometricTexts texts, {bool biometricOnly = true}) async {
    try {
      final ok = await _auth.authenticate(
        localizedReason: texts.reason,
        authMessages: [
          AndroidAuthMessages(signInTitle: texts.androidTitle, biometricHint: texts.androidHint, cancelButton: texts.cancel),
          IOSAuthMessages(cancelButton: texts.cancel, localizedFallbackTitle: texts.iosFallback),
        ],
        // No plugin error dialogs: the app shows its own translated message for each outcome. Sticky so a call or app
        // switch does not fail the prompt.
        options: AuthenticationOptions(biometricOnly: biometricOnly, stickyAuth: true, sensitiveTransaction: true, useErrorDialogs: false),
      );
      return ok ? HqBiometricOutcome.success : HqBiometricOutcome.cancelled;
    } on PlatformException catch (e) {
      return hqBiometricOutcomeOf(e.code);
    }
  }
}

/// Maps a `local_auth` error code (both platforms) to an outcome.
HqBiometricOutcome hqBiometricOutcomeOf(String code) => switch (code) {
      'NotAvailable' || 'BiometricNotAvailable' || 'OtherOperatingSystem' || 'biometricOnlyNotSupported' => HqBiometricOutcome.notAvailable,
      'NotEnrolled' => HqBiometricOutcome.notEnrolled,
      'LockedOut' => HqBiometricOutcome.lockedOut,
      'PermanentlyLockedOut' => HqBiometricOutcome.permanentlyLockedOut,
      'PasscodeNotSet' => HqBiometricOutcome.passcodeNotSet,
      'UserCancelled' || 'UserFallback' || 'SystemCancel' => HqBiometricOutcome.cancelled,
      _ => HqBiometricOutcome.failed,
    };

/// The user-facing message for every case where biometrics cannot be used or the prompt did not succeed, already
/// translated by the app. [of] and [ofStatus] pick the right one; null means "say nothing" (success, or a plain cancel).
@immutable
class HqBiometricMessages {
  const HqBiometricMessages({
    required this.unsupported,
    required this.notEnrolled,
    required this.lockedOut,
    required this.permanentlyLockedOut,
    required this.passcodeNotSet,
    required this.failed,
    this.cancelled,
  });

  /// "This device does not support fingerprint or Face ID. Sign in with your password and OTP."
  final String unsupported;

  /// "Set up fingerprint or Face ID in your phone's settings first."
  final String notEnrolled;

  /// "Too many attempts. Try again in 30 seconds."
  final String lockedOut;

  /// "Biometrics are locked. Unlock your phone with its PIN, then sign in with your password and OTP."
  final String permanentlyLockedOut;

  /// iOS: "Set a device passcode to use Face ID."
  final String passcodeNotSet;

  /// "Biometric check is not available right now. Sign in with your password and OTP."
  final String failed;

  /// Shown after a cancel; null keeps quiet (the user chose to cancel).
  final String? cancelled;

  /// English texts for tests and the example app; real apps pass translated ones.
  factory HqBiometricMessages.english() => const HqBiometricMessages(
        unsupported: 'This device does not support fingerprint or Face ID. Sign in with your password and OTP.',
        notEnrolled: "Set up fingerprint or Face ID in your phone's settings first.",
        lockedOut: 'Too many attempts. Try again in a moment.',
        permanentlyLockedOut: 'Biometrics are locked. Unlock your phone with its PIN, then sign in with your password and OTP.',
        passcodeNotSet: 'Set a device passcode to use Face ID.',
        failed: 'Biometric check is not available right now. Sign in with your password and OTP.',
      );

  /// The message for a prompt [outcome], or null for success (and for cancel unless [cancelled] is set).
  String? of(HqBiometricOutcome outcome) => switch (outcome) {
        HqBiometricOutcome.success => null,
        HqBiometricOutcome.cancelled => cancelled,
        HqBiometricOutcome.notAvailable => unsupported,
        HqBiometricOutcome.notEnrolled => notEnrolled,
        HqBiometricOutcome.lockedOut => lockedOut,
        HqBiometricOutcome.permanentlyLockedOut => permanentlyLockedOut,
        HqBiometricOutcome.passcodeNotSet => passcodeNotSet,
        HqBiometricOutcome.failed => failed,
      };

  /// The message for a device [status], or null when biometrics are available.
  String? ofStatus(HqBiometricStatus status) => switch (status.availability) {
        HqBiometricAvailability.available => null,
        HqBiometricAvailability.notEnrolled => notEnrolled,
        HqBiometricAvailability.unsupported => unsupported,
      };
}

/// Tone of the notice for an outcome: lockouts and failures warn, the rest inform.
HqAlertTone hqBiometricTone(HqBiometricOutcome outcome) => switch (outcome) {
      HqBiometricOutcome.lockedOut || HqBiometricOutcome.permanentlyLockedOut || HqBiometricOutcome.failed => HqAlertTone.warning,
      _ => HqAlertTone.info,
    };

/// An inline notice explaining why biometrics cannot be used (device unsupported, nothing enrolled, locked out...). Pass
/// either a [status] (settings screen: "can this phone do it?") or an [outcome] (after a prompt). Draws nothing when there
/// is nothing to say. [action] is e.g. an "Open settings" button for [HqBiometricAvailability.notEnrolled].
class HqBiometricNotice extends StatelessWidget {
  const HqBiometricNotice({super.key, required this.messages, this.status, this.outcome, this.action});

  final HqBiometricMessages messages;
  final HqBiometricStatus? status;
  final HqBiometricOutcome? outcome;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final fromOutcome = outcome == null ? null : messages.of(outcome!);
    final text = fromOutcome ?? (status == null ? null : messages.ofStatus(status!));
    if (text == null || text.isEmpty) return const SizedBox.shrink();
    final tone = fromOutcome != null ? hqBiometricTone(outcome!) : HqAlertTone.info;
    return HqAlert(message: text, tone: tone, action: action);
  }
}

/// The icon for a biometric kind (Face ID face, fingerprint, or a generic lock).
IconData hqBiometricIcon(HqBiometricKind? kind) => switch (kind) {
      HqBiometricKind.face => Icons.face,
      HqBiometricKind.fingerprint => Icons.fingerprint,
      HqBiometricKind.iris => Icons.remove_red_eye_outlined,
      _ => Icons.lock_open,
    };

/// A button that shows the right biometric icon ("Unlock with Face ID"). The label is passed in already translated; the
/// app picks the text from [HqBiometricStatus.primary].
class HqBiometricButton extends StatelessWidget {
  const HqBiometricButton({super.key, required this.label, required this.kind, required this.onPressed, this.loading = false, this.block = true});

  final String label;
  final HqBiometricKind? kind;
  final VoidCallback? onPressed;
  final bool loading;
  final bool block;

  @override
  Widget build(BuildContext context) => HqButton(label: label, icon: hqBiometricIcon(kind), appearance: HqButtonAppearance.outline, loading: loading, block: block, onPressed: onPressed);
}
