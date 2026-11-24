import 'dart:convert';

import 'package:biometric_signature/biometric_signature_platform_interface.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

/// A raw uncompressed P-256 point (0x04 + 64 bytes), as some platforms export it.
final Uint8List _rawPoint = Uint8List.fromList([0x04, ...List<int>.generate(64, (i) => i)]);
final String _spki = base64Encode([0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00, ..._rawPoint]);

class _FakePlatform extends BiometricSignaturePlatform {
  _FakePlatform({this.canAuthenticate = true, KeyCreationResult? create, SignatureResult? sign, this.exists = true, this.throwOnCreate = false})
      : createResult = create ?? KeyCreationResult(code: BiometricError.success, publicKey: _spki, algorithm: 'EC'),
        signResult = sign ?? SignatureResult(code: BiometricError.success, signature: 'c2ln');

  final bool canAuthenticate;
  final KeyCreationResult createResult;
  final SignatureResult signResult;
  final bool exists;
  final bool throwOnCreate;
  CreateKeysConfig? lastCreateConfig;
  CreateSignatureConfig? lastSignConfig;
  String? lastPayload;
  String? lastAlias;
  String? lastPrompt;
  KeyFormat? lastKeyFormat;
  final List<String?> deleted = [];

  @override
  Future<BiometricAvailability> biometricAuthAvailableWithStrength(BiometricStrength biometricStrength) async => BiometricAvailability(canAuthenticate: canAuthenticate);

  @override
  Future<KeyCreationResult> createKeys(String? keyAlias, CreateKeysConfig? config, KeyFormat keyFormat, String? promptMessage) async {
    if (throwOnCreate) throw PlatformException(code: 'boom');
    lastAlias = keyAlias;
    lastCreateConfig = config;
    lastKeyFormat = keyFormat;
    lastPrompt = promptMessage;
    return createResult;
  }

  @override
  Future<SignatureResult> createSignature(String payload, String? keyAlias, CreateSignatureConfig? config, SignatureFormat signatureFormat, KeyFormat keyFormat, String? promptMessage) async {
    lastPayload = payload;
    lastAlias = keyAlias;
    lastSignConfig = config;
    lastPrompt = promptMessage;
    return signResult;
  }

  @override
  Future<KeyInfo> getKeyInfo(String? keyAlias, bool checkValidity, KeyFormat keyFormat) async => KeyInfo(exists: exists, isValid: true);

  @override
  Future<bool> deleteKeys(String? keyAlias) async {
    deleted.add(keyAlias);
    return true;
  }
}

const HqBiometricTexts _texts = HqBiometricTexts(reason: 'Sign in to the app', androidTitle: 'Sign in', androidHint: 'Confirm it is you', cancel: 'Cancel');

void main() {
  late BiometricSignaturePlatform original;

  setUp(() {
    original = BiometricSignaturePlatform.instance;
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
  });

  tearDown(() {
    BiometricSignaturePlatform.instance = original;
    debugDefaultTargetPlatformOverride = null;
  });

  group('hqNormalizeSpkiBase64', () {
    test('keeps a base64 SPKI as it is', () => expect(hqNormalizeSpkiBase64(_spki), _spki));

    test('strips a PEM armour and line breaks', () {
      final pem = '-----BEGIN PUBLIC KEY-----\n${_spki.substring(0, 40)}\n${_spki.substring(40)}\n-----END PUBLIC KEY-----\n';
      expect(hqNormalizeSpkiBase64(pem), _spki);
    });

    test('wraps a raw P-256 point in the SPKI header', () => expect(hqNormalizeSpkiBase64(base64Encode(_rawPoint)), _spki));

    test('accepts URL-safe, unpadded base64', () {
      final urlSafe = base64Url.encode(base64Decode(_spki)).replaceAll('=', '');
      expect(hqNormalizeSpkiBase64(urlSafe), _spki);
    });

    test('returns null for nothing or garbage', () {
      expect(hqNormalizeSpkiBase64(null), isNull);
      expect(hqNormalizeSpkiBase64('  '), isNull);
      expect(hqNormalizeSpkiBase64('***'), isNull);
    });
  });

  test('plugin codes map to biometric outcomes', () {
    expect(hqDeviceKeyOutcomeOf(BiometricError.success), HqBiometricOutcome.success);
    expect(hqDeviceKeyOutcomeOf(BiometricError.userCanceled), HqBiometricOutcome.cancelled);
    expect(hqDeviceKeyOutcomeOf(BiometricError.systemCanceled), HqBiometricOutcome.cancelled);
    expect(hqDeviceKeyOutcomeOf(BiometricError.notEnrolled), HqBiometricOutcome.notEnrolled);
    expect(hqDeviceKeyOutcomeOf(BiometricError.notSupported), HqBiometricOutcome.notAvailable);
    expect(hqDeviceKeyOutcomeOf(BiometricError.lockedOut), HqBiometricOutcome.lockedOut);
    expect(hqDeviceKeyOutcomeOf(BiometricError.lockedOutPermanent), HqBiometricOutcome.permanentlyLockedOut);
    expect(hqDeviceKeyOutcomeOf(BiometricError.passcodeNotSet), HqBiometricOutcome.passcodeNotSet);
    expect(hqDeviceKeyOutcomeOf(BiometricError.keyInvalidated), HqBiometricOutcome.failed);
    expect(hqDeviceKeyOutcomeOf(null), HqBiometricOutcome.failed);
  });

  test('algorithm codes follow the plugin', () {
    expect(hqDeviceKeyAlgorithmOf('EC'), HqDeviceKeyAlgorithm.ecP256Sha256);
    expect(hqDeviceKeyAlgorithmOf('RSA'), HqDeviceKeyAlgorithm.rsa2048Sha256);
    expect(HqDeviceKeyAlgorithm.ecP256Sha256.code, 'EC_P256_SHA256');
    expect(HqDeviceKeyAlgorithm.rsa2048Sha256.code, 'RSA_2048_SHA256');
  });

  group('HqHardwareDeviceKey', () {
    test('creates a biometric-only EC key under its alias and returns the SPKI', () async {
      final fake = _FakePlatform(create: KeyCreationResult(code: BiometricError.success, publicKey: base64Encode(_rawPoint), algorithm: 'EC'));
      BiometricSignaturePlatform.instance = fake;
      final created = await HqHardwareDeviceKey(keyAlias: 'pkm.signin').create(texts: _texts);

      expect(created.isSuccess, isTrue);
      expect(created.keyPair!.publicKeyBase64Spki, _spki);
      expect(created.keyPair!.algorithm, HqDeviceKeyAlgorithm.ecP256Sha256);
      expect(fake.lastAlias, 'pkm.signin');
      expect(fake.lastKeyFormat, KeyFormat.base64);
      expect(fake.lastPrompt, 'Sign in');
      expect(fake.lastCreateConfig!.signatureType, SignatureType.ecdsa);
      expect(fake.lastCreateConfig!.enforceBiometric, isTrue);
      expect(fake.lastCreateConfig!.useDeviceCredentials, isFalse);
      expect(fake.lastCreateConfig!.setInvalidatedByBiometricEnrollment, isTrue);
      expect(fake.lastCreateConfig!.cancelButtonText, 'Cancel');
    });

    test('iOS shows the reason in the prompt', () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
      final fake = _FakePlatform();
      BiometricSignaturePlatform.instance = fake;
      await HqHardwareDeviceKey().create(texts: _texts);
      expect(fake.lastPrompt, 'Sign in to the app');
    });

    test('a cancelled or failed creation returns no key', () async {
      BiometricSignaturePlatform.instance = _FakePlatform(create: KeyCreationResult(code: BiometricError.userCanceled));
      expect((await HqHardwareDeviceKey().create(texts: _texts)).outcome, HqBiometricOutcome.cancelled);

      BiometricSignaturePlatform.instance = _FakePlatform(create: KeyCreationResult(code: BiometricError.success));
      final noKey = await HqHardwareDeviceKey().create(texts: _texts);
      expect(noKey.isSuccess, isFalse);
      expect(noKey.outcome, HqBiometricOutcome.failed);

      BiometricSignaturePlatform.instance = _FakePlatform(throwOnCreate: true);
      expect((await HqHardwareDeviceKey().create(texts: _texts)).outcome, HqBiometricOutcome.failed);
    });

    test('signs the payload as given and returns the base64 signature', () async {
      final fake = _FakePlatform();
      BiometricSignaturePlatform.instance = fake;
      final signed = await HqHardwareDeviceKey(keyAlias: 'k').sign('Y2hhbGxlbmdl', texts: _texts);

      expect(signed.isSuccess, isTrue);
      expect(signed.signatureBase64, 'c2ln');
      expect(signed.keyLost, isFalse);
      expect(fake.lastPayload, 'Y2hhbGxlbmdl');
      expect(fake.lastSignConfig!.allowDeviceCredentials, isFalse);
    });

    test('an invalidated or missing key is reported as lost', () async {
      BiometricSignaturePlatform.instance = _FakePlatform(sign: SignatureResult(code: BiometricError.keyInvalidated));
      final invalidated = await HqHardwareDeviceKey().sign('x', texts: _texts);
      expect(invalidated.keyLost, isTrue);
      expect(invalidated.isSuccess, isFalse);

      BiometricSignaturePlatform.instance = _FakePlatform(sign: SignatureResult(code: BiometricError.keyNotFound));
      expect((await HqHardwareDeviceKey().sign('x', texts: _texts)).keyLost, isTrue);

      BiometricSignaturePlatform.instance = _FakePlatform(sign: SignatureResult(code: BiometricError.userCanceled));
      final cancelled = await HqHardwareDeviceKey().sign('x', texts: _texts);
      expect(cancelled.outcome, HqBiometricOutcome.cancelled);
      expect(cancelled.keyLost, isFalse);
    });

    test('support, existence and delete go to the plugin', () async {
      final fake = _FakePlatform(canAuthenticate: false, exists: true);
      BiometricSignaturePlatform.instance = fake;
      final key = HqHardwareDeviceKey(keyAlias: 'k');
      expect(await key.isSupported(), isFalse);
      expect(await key.exists(), isTrue);
      await key.delete();
      expect(fake.deleted, ['k']);
    });

    test('is not offered off the phone platforms', () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.windows;
      final fake = _FakePlatform();
      BiometricSignaturePlatform.instance = fake;
      final key = HqHardwareDeviceKey();
      expect(await key.isSupported(), isFalse);
      expect(await key.exists(), isFalse);
      expect((await key.create(texts: _texts)).outcome, HqBiometricOutcome.notAvailable);
      expect((await key.sign('x', texts: _texts)).outcome, HqBiometricOutcome.notAvailable);
      await key.delete();
      expect(fake.deleted, isEmpty);
    });
  });

  test('HqMemoryDeviceKey creates, signs and deletes', () async {
    final key = HqMemoryDeviceKey(outcomes: [HqBiometricOutcome.success, HqBiometricOutcome.cancelled]);
    expect(await key.exists(), isFalse);
    expect((await key.create(texts: _texts)).isSuccess, isTrue);
    expect(await key.exists(), isTrue);
    final cancelled = await key.sign('c', texts: _texts);
    expect(cancelled.outcome, HqBiometricOutcome.cancelled);
    await key.delete();
    expect(await key.exists(), isFalse);
    expect((await key.sign('c', texts: _texts)).keyLost, isTrue);
  });
}
