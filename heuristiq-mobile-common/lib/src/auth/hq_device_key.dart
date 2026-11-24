import 'dart:convert';

import 'package:biometric_signature/biometric_signature.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'hq_biometric_auth.dart';

/// The signing algorithm of a device key, with the code the server expects (`keyAlgorithm`).
enum HqDeviceKeyAlgorithm {
  /// ECDSA over P-256 with SHA-256; the signature is DER-encoded (SHA256withECDSA). Hardware-backed on both platforms.
  ecP256Sha256('EC_P256_SHA256'),

  /// RSA-2048 PKCS#1 v1.5 with SHA-256 (SHA256withRSA).
  rsa2048Sha256('RSA_2048_SHA256');

  const HqDeviceKeyAlgorithm(this.code);

  /// The wire code, e.g. `EC_P256_SHA256`.
  final String code;
}

/// The public half of a new device key, ready to send to the server.
@immutable
class HqDeviceKeyPair {
  const HqDeviceKeyPair({required this.publicKeyBase64Spki, required this.algorithm});

  /// Standard, padded base64 of the X.509 SubjectPublicKeyInfo DER.
  final String publicKeyBase64Spki;
  final HqDeviceKeyAlgorithm algorithm;
}

/// The answer of [HqDeviceKey.create]: the outcome, and the key pair on success.
@immutable
class HqDeviceKeyCreation {
  const HqDeviceKeyCreation(this.outcome, [this.keyPair]);

  final HqBiometricOutcome outcome;
  final HqDeviceKeyPair? keyPair;

  bool get isSuccess => outcome == HqBiometricOutcome.success && keyPair != null;
}

/// The answer of [HqDeviceKey.sign]: the outcome, the signature on success, and whether the key can no longer be used.
@immutable
class HqDeviceKeySignature {
  const HqDeviceKeySignature(this.outcome, {this.signatureBase64, this.keyLost = false});

  final HqBiometricOutcome outcome;

  /// Standard base64 of the signature bytes (DER for EC, PKCS#1 v1.5 for RSA), only on success.
  final String? signatureBase64;

  /// The key is gone or was invalidated (a face / finger was added or removed, the key was wiped): it will never sign
  /// again. The app should delete it, forget its enrolment and let the user switch the feature on again.
  final bool keyLost;

  bool get isSuccess => outcome == HqBiometricOutcome.success && signatureBase64 != null;
}

/// A hardware key pair on this device (Android Keystore / iOS Secure Enclave) whose private half never leaves the
/// hardware and signs only after the user's face or finger is checked. The server keeps the public key and verifies
/// signatures: unlike [HqBiometricLock], a successful sign-in proves the face / finger unlocked this device's key.
abstract interface class HqDeviceKey {
  /// True on a phone (Android / iOS) that can create and use a biometric-gated key now. Always false on the web.
  Future<bool> isSupported();

  /// True when this install has a usable key.
  Future<bool> exists();

  /// Creates (or replaces) the key pair after a biometric prompt and returns its public key.
  Future<HqDeviceKeyCreation> create({required HqBiometricTexts texts});

  /// Signs the UTF-8 bytes of [payload] after a biometric prompt.
  Future<HqDeviceKeySignature> sign(String payload, {required HqBiometricTexts texts});

  /// Deletes the key (no prompt). Safe to call when there is none.
  Future<void> delete();
}

/// The real [HqDeviceKey] over the `biometric_signature` plugin: an EC P-256 key, biometric only (no device PIN
/// fallback), invalidated when the enrolled faces / fingers change. [keyAlias] keeps each app's key separate.
class HqHardwareDeviceKey implements HqDeviceKey {
  HqHardwareDeviceKey({this.keyAlias = 'rou.deviceKey'});

  final String keyAlias;
  final BiometricSignature _plugin = BiometricSignature();

  static bool get _onPhone => !kIsWeb && (defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS);

  /// Android shows the prompt message as the dialog title; iOS shows it as the reason under Face ID / Touch ID.
  static String _promptMessage(HqBiometricTexts texts) => defaultTargetPlatform == TargetPlatform.android ? (texts.androidTitle ?? texts.reason) : texts.reason;

  @override
  Future<bool> isSupported() async {
    if (!_onPhone) return false;
    try {
      final availability = await _plugin.biometricAuthAvailable();
      return availability.canAuthenticate ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  @override
  Future<bool> exists() async {
    if (!_onPhone) return false;
    try {
      return await _plugin.biometricKeyExists(keyAlias: keyAlias, checkValidity: true);
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  @override
  Future<HqDeviceKeyCreation> create({required HqBiometricTexts texts}) async {
    if (!_onPhone) return const HqDeviceKeyCreation(HqBiometricOutcome.notAvailable);
    try {
      final result = await _plugin.createKeys(
        keyAlias: keyAlias,
        keyFormat: KeyFormat.base64,
        promptMessage: _promptMessage(texts),
        config: CreateKeysConfig(
          signatureType: SignatureType.ecdsa,
          enforceBiometric: true,
          setInvalidatedByBiometricEnrollment: true,
          useDeviceCredentials: false,
          enableDecryption: false,
          requireAuthentication: true,
          failIfExists: false,
          promptSubtitle: texts.androidHint,
          cancelButtonText: texts.cancel,
        ),
      );
      final outcome = hqDeviceKeyOutcomeOf(result.code);
      final spki = hqNormalizeSpkiBase64(result.publicKey ?? (result.publicKeyBytes == null ? null : base64Encode(result.publicKeyBytes!)));
      if (outcome != HqBiometricOutcome.success || spki == null) {
        return HqDeviceKeyCreation(outcome == HqBiometricOutcome.success ? HqBiometricOutcome.failed : outcome);
      }
      return HqDeviceKeyCreation(outcome, HqDeviceKeyPair(publicKeyBase64Spki: spki, algorithm: hqDeviceKeyAlgorithmOf(result.algorithm)));
    } on PlatformException {
      return const HqDeviceKeyCreation(HqBiometricOutcome.failed);
    } on MissingPluginException {
      return const HqDeviceKeyCreation(HqBiometricOutcome.notAvailable);
    }
  }

  @override
  Future<HqDeviceKeySignature> sign(String payload, {required HqBiometricTexts texts}) async {
    if (!_onPhone) return const HqDeviceKeySignature(HqBiometricOutcome.notAvailable);
    try {
      final result = await _plugin.createSignature(
        payload: payload,
        keyAlias: keyAlias,
        signatureFormat: SignatureFormat.base64,
        promptMessage: _promptMessage(texts),
        config: CreateSignatureConfig(promptSubtitle: texts.androidHint, cancelButtonText: texts.cancel, allowDeviceCredentials: false),
      );
      final outcome = hqDeviceKeyOutcomeOf(result.code);
      final lost = result.code == BiometricError.keyNotFound || result.code == BiometricError.keyInvalidated;
      final signature = result.signature ?? (result.signatureBytes == null ? null : base64Encode(result.signatureBytes!));
      if (outcome == HqBiometricOutcome.success && (signature == null || signature.isEmpty)) return const HqDeviceKeySignature(HqBiometricOutcome.failed);
      return HqDeviceKeySignature(outcome, signatureBase64: outcome == HqBiometricOutcome.success ? signature : null, keyLost: lost);
    } on PlatformException {
      return const HqDeviceKeySignature(HqBiometricOutcome.failed);
    } on MissingPluginException {
      return const HqDeviceKeySignature(HqBiometricOutcome.notAvailable);
    }
  }

  @override
  Future<void> delete() async {
    if (!_onPhone) return;
    try {
      await _plugin.deleteKeys(keyAlias: keyAlias);
    } on PlatformException {
      // Nothing to delete, or the keystore is unavailable: the next create replaces the key anyway.
    } on MissingPluginException {
      // No plugin on this platform: there is no key.
    }
  }
}

/// Maps a plugin result code to a prompt outcome. A missing code counts as a failure.
HqBiometricOutcome hqDeviceKeyOutcomeOf(BiometricError? code) => switch (code) {
      BiometricError.success => HqBiometricOutcome.success,
      BiometricError.userCanceled || BiometricError.systemCanceled || BiometricError.authenticationFailed => HqBiometricOutcome.cancelled,
      BiometricError.notAvailable || BiometricError.notSupported => HqBiometricOutcome.notAvailable,
      BiometricError.notEnrolled => HqBiometricOutcome.notEnrolled,
      BiometricError.lockedOut => HqBiometricOutcome.lockedOut,
      BiometricError.lockedOutPermanent => HqBiometricOutcome.permanentlyLockedOut,
      BiometricError.passcodeNotSet => HqBiometricOutcome.passcodeNotSet,
      _ => HqBiometricOutcome.failed,
    };

/// The algorithm the plugin reports ("EC", "ECDSA", "RSA"...). The plugin creates EC keys here; Windows Hello promotes
/// them to RSA.
HqDeviceKeyAlgorithm hqDeviceKeyAlgorithmOf(String? pluginAlgorithm) =>
    (pluginAlgorithm ?? '').toUpperCase().contains('RSA') ? HqDeviceKeyAlgorithm.rsa2048Sha256 : HqDeviceKeyAlgorithm.ecP256Sha256;

/// The DER prefix of a P-256 SubjectPublicKeyInfo (id-ecPublicKey, prime256v1, BIT STRING of 66 bytes).
const List<int> _p256SpkiPrefix = [0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00];

/// Turns a public key as a plugin may return it into standard, padded base64 of the SubjectPublicKeyInfo DER: strips a
/// PEM armour and whitespace, accepts URL-safe or unpadded base64, and wraps a raw 65-byte uncompressed P-256 point in
/// the SPKI header. Returns null for an empty or undecodable value.
String? hqNormalizeSpkiBase64(String? key) {
  if (key == null) return null;
  final body = key.replaceAll(RegExp(r'-----(BEGIN|END)[^-]*-----'), '').replaceAll(RegExp(r'\s'), '').replaceAll('-', '+').replaceAll('_', '/');
  if (body.isEmpty) return null;
  final Uint8List bytes;
  try {
    bytes = base64Decode(body.padRight((body.length + 3) ~/ 4 * 4, '='));
  } on FormatException {
    return null;
  }
  if (bytes.isEmpty) return null;
  if (bytes.length == 65 && bytes.first == 0x04) return base64Encode([..._p256SpkiPrefix, ...bytes]);
  return base64Encode(bytes);
}

/// An in-memory [HqDeviceKey] for tests and the example app: it "signs" by returning base64 of `sig:<payload>`, and
/// answers each prompt with the next of [outcomes] (repeating the last).
class HqMemoryDeviceKey implements HqDeviceKey {
  HqMemoryDeviceKey({this.supported = true, List<HqBiometricOutcome>? outcomes, this.algorithm = HqDeviceKeyAlgorithm.ecP256Sha256, this.publicKey = 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE'})
      : outcomes = outcomes ?? [HqBiometricOutcome.success];

  bool supported;
  final List<HqBiometricOutcome> outcomes;
  final HqDeviceKeyAlgorithm algorithm;
  final String publicKey;

  /// True while a key exists.
  bool hasKey = false;

  /// Set to make the next [sign] report the key as lost (invalidated).
  bool lost = false;
  int prompts = 0;
  int deletes = 0;
  final List<String> signed = <String>[];

  HqBiometricOutcome _next() => outcomes[prompts++ < outcomes.length ? prompts - 1 : outcomes.length - 1];

  @override
  Future<bool> isSupported() async => supported;

  @override
  Future<bool> exists() async => hasKey;

  @override
  Future<HqDeviceKeyCreation> create({required HqBiometricTexts texts}) async {
    final outcome = _next();
    if (outcome != HqBiometricOutcome.success) return HqDeviceKeyCreation(outcome);
    hasKey = true;
    lost = false;
    return HqDeviceKeyCreation(outcome, HqDeviceKeyPair(publicKeyBase64Spki: publicKey, algorithm: algorithm));
  }

  @override
  Future<HqDeviceKeySignature> sign(String payload, {required HqBiometricTexts texts}) async {
    if (!hasKey || lost) return const HqDeviceKeySignature(HqBiometricOutcome.failed, keyLost: true);
    final outcome = _next();
    if (outcome != HqBiometricOutcome.success) return HqDeviceKeySignature(outcome);
    signed.add(payload);
    return HqDeviceKeySignature(outcome, signatureBase64: base64Encode(utf8.encode('sig:$payload')));
  }

  @override
  Future<void> delete() async {
    deletes++;
    hasKey = false;
  }
}
