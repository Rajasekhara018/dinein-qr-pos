import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Android lock task mode: keeps customers inside the app (no home, recents or status bar).
///
/// Without device-owner provisioning Android asks for confirmation once ("screen pinning") and staff can
/// leave with the system gesture. As device owner it locks silently. See docs/KIOSK_SETUP.md.
/// Every call is safe on platforms or test runs without the native side.
class KioskLockdown {
  static const _channel = MethodChannel('com.heuristq.dinein_kiosk/lockdown');

  Future<void> enter() => _invoke('enter');

  /// Lets staff leave the app until it is next started.
  Future<void> exit() => _invoke('exit');

  Future<bool> isLocked() async {
    try {
      return await _channel.invokeMethod<bool>('isLocked') ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<void> _invoke(String method) async {
    try {
      await _channel.invokeMethod<void>(method);
    } on PlatformException {
      // Lockdown is a hardening layer; the app still works without it.
    } on MissingPluginException {
      // Not running on the kiosk build (tests, desktop).
    }
  }
}

final kioskLockdownProvider = Provider<KioskLockdown>((ref) => KioskLockdown());
