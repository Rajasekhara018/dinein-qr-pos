import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'hq_biometric_auth.dart';

/// Small secret storage. The device implementation is the Android Keystore / iOS Keychain; tests use
/// [HqMemorySecureStore].
abstract interface class HqSecureStore {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

/// The Keychain / Keystore store (`flutter_secure_storage`). iOS items are `unlocked_this_device`: readable only while
/// the phone is unlocked, never synced to iCloud and never restored to another phone from a backup. Android uses
/// EncryptedSharedPreferences with a Keystore key.
class HqDeviceSecureStore implements HqSecureStore {
  HqDeviceSecureStore({FlutterSecureStorage? storage}) : _storage = storage ?? defaultStorage;

  /// This device only, never synced or restored to another phone; Android EncryptedSharedPreferences.
  static const FlutterSecureStorage defaultStorage = FlutterSecureStorage(
    iOptions: IOSOptions(accessibility: KeychainAccessibility.unlocked_this_device),
    // Since flutter_secure_storage 11 Android always encrypts (AES-GCM, key in the Android Keystore).
    aOptions: AndroidOptions(),
  );

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) => _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);
}

/// An in-memory [HqSecureStore] for tests and the example app.
class HqMemorySecureStore implements HqSecureStore {
  final Map<String, String> values = <String, String>{};

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;

  @override
  Future<void> delete(String key) async => values.remove(key);
}

/// The answer of [HqBiometricLock.unlock]: the outcome, and the secret when it was a success.
@immutable
class HqBiometricUnlock {
  const HqBiometricUnlock(this.outcome, [this.secret]);

  final HqBiometricOutcome outcome;

  /// The stored secret (a refresh token), only on success.
  final String? secret;

  bool get isSuccess => outcome == HqBiometricOutcome.success && secret != null;
}

/// "Unlock with Face ID / fingerprint": keeps one secret (normally the refresh token from a full password + OTP sign-in)
/// in the secure store and releases it after a successful biometric check.
///
/// The check is done by the operating system and the app only gets pass / fail, so the secret is protected by the
/// Keychain / Keystore and the device lock, not cryptographically by the face or finger. The server must therefore treat
/// a biometric unlock as "holds this device's refresh token" and nothing more: the token is device-bound, short-lived and
/// revocable, and high-value actions (subscription payment, payout bank change, nominee change) still ask for an OTP.
///
/// Rules applied here:
/// - Enabling needs a successful biometric check first, so a passer-by with the unlocked phone cannot switch it on
///   without the owner's face / finger.
/// - A permanent lockout or a device with no enrolled biometrics disables the lock and wipes the secret: the user signs
///   in with password + OTP and can enable it again.
/// - [disable] wipes the secret; the app calls it on sign-out and when the server rejects the stored token.
class HqBiometricLock {
  HqBiometricLock({required this.auth, required this.store, this.keyPrefix = 'rou.biometric'});

  final HqBiometricAuth auth;
  final HqSecureStore store;

  /// Prefix of the stored keys; give each app its own.
  final String keyPrefix;

  String get _secretKey => '$keyPrefix.secret';

  /// True when a secret is stored, i.e. biometric unlock is switched on for this device.
  Future<bool> isEnabled() async => (await store.read(_secretKey))?.isNotEmpty ?? false;

  /// Checks the user's face / finger and, on success, stores [secret]. Returns the outcome; nothing is stored otherwise.
  Future<HqBiometricOutcome> enable(String secret, HqBiometricTexts texts) async {
    if (secret.isEmpty) throw ArgumentError.value(secret, 'secret', 'must not be empty');
    final status = await auth.status();
    if (!status.isAvailable) {
      return status.availability == HqBiometricAvailability.notEnrolled ? HqBiometricOutcome.notEnrolled : HqBiometricOutcome.notAvailable;
    }
    final outcome = await auth.authenticate(texts);
    if (outcome == HqBiometricOutcome.success) await store.write(_secretKey, secret);
    return outcome;
  }

  /// Replaces the stored secret without a prompt (after the app refreshed the token), only while the lock is enabled.
  Future<void> updateSecret(String secret) async {
    if (secret.isEmpty) throw ArgumentError.value(secret, 'secret', 'must not be empty');
    if (await isEnabled()) await store.write(_secretKey, secret);
  }

  /// Asks for the face / finger and returns the stored secret on success.
  Future<HqBiometricUnlock> unlock(HqBiometricTexts texts) async {
    if (!await isEnabled()) return const HqBiometricUnlock(HqBiometricOutcome.notAvailable);
    final outcome = await auth.authenticate(texts);
    if (outcome == HqBiometricOutcome.permanentlyLockedOut || outcome == HqBiometricOutcome.notEnrolled) {
      await disable();
      return HqBiometricUnlock(outcome);
    }
    if (outcome != HqBiometricOutcome.success) return HqBiometricUnlock(outcome);
    final secret = await store.read(_secretKey);
    return secret == null ? const HqBiometricUnlock(HqBiometricOutcome.notAvailable) : HqBiometricUnlock(outcome, secret);
  }

  /// Switches biometric unlock off and wipes the secret.
  Future<void> disable() => store.delete(_secretKey);
}
