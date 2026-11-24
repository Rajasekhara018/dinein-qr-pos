import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:local_auth/local_auth.dart';
import 'package:local_auth_platform_interface/types/auth_messages.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

class _FakeLocalAuth extends LocalAuthentication {
  _FakeLocalAuth({this.supported = true, this.canCheck = true, this.enrolled = const [], this.result = true, this.error, this.statusError});

  final bool supported;
  final bool canCheck;
  final List<BiometricType> enrolled;
  final bool result;
  final PlatformException? error;
  final Object? statusError;
  AuthenticationOptions? lastOptions;
  List<AuthMessages>? lastMessages;
  String? lastReason;

  @override
  Future<bool> isDeviceSupported() async {
    if (statusError != null) throw statusError!;
    return supported;
  }

  @override
  Future<bool> get canCheckBiometrics async => canCheck;

  @override
  Future<List<BiometricType>> getAvailableBiometrics() async => enrolled;

  @override
  Future<bool> authenticate({required String localizedReason, Iterable<AuthMessages> authMessages = const [], AuthenticationOptions options = const AuthenticationOptions()}) async {
    lastReason = localizedReason;
    lastOptions = options;
    lastMessages = authMessages.toList();
    if (error != null) throw error!;
    return result;
  }
}

class _FakeBiometrics implements HqBiometricAuth {
  _FakeBiometrics({this.statusValue = const HqBiometricStatus(HqBiometricAvailability.available, {HqBiometricKind.face}), List<HqBiometricOutcome>? outcomes}) : outcomes = outcomes ?? [HqBiometricOutcome.success];

  final HqBiometricStatus statusValue;
  final List<HqBiometricOutcome> outcomes;
  int prompts = 0;

  @override
  Future<HqBiometricStatus> status() async => statusValue;

  @override
  Future<HqBiometricOutcome> authenticate(HqBiometricTexts texts, {bool biometricOnly = true}) async => outcomes[prompts++ % outcomes.length];
}

class _FakeSmsReader implements HqSmsCodeReader {
  final List<Completer<String?>> reads = [];
  int cancels = 0;

  @override
  Future<String?> read({required int length}) {
    final c = Completer<String?>();
    reads.add(c);
    return c.future;
  }

  @override
  Future<void> cancel() async {
    cancels++;
    for (final c in reads) {
      if (!c.isCompleted) c.complete(null);
    }
  }
}

const _texts = HqBiometricTexts(reason: 'Unlock RealBoom', cancel: 'Cancel', androidTitle: 'Unlock', androidHint: 'Touch the sensor', iosFallback: 'Use password');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('SMS code reader', () {
    test('extracts exactly the code length, not part of a longer number', () {
      expect(hqExtractOtp('Your RealBoom OTP is 482913. Valid 5 min. Ref 12345678', 6), '482913');
      expect(hqExtractOtp('Call 18001234567 then use 4821', 4), '4821');
      expect(hqExtractOtp('no code here', 6), isNull);
    });

    test('off Android it returns null at once and cancel is a no-op', () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      final reader = HqAndroidSmsCodeReader();
      expect(await reader.read(length: 6), isNull);
      await reader.cancel();
      expect(await HqAndroidSmsCodeReader.appSignature(), isNull);
    });

    group('on Android', () {
      final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      const codec = StandardMessageCodec();
      const prefix = 'dev.flutter.pigeon.smart_auth.SmartAuthApi.';
      final calls = <String>[];

      void reply(String method, List<Object?> answer) => messenger.setMockMessageHandler('$prefix$method', (message) async {
            calls.add('$method ${codec.decodeMessage(message)}');
            return codec.encodeMessage(answer);
          });

      setUp(() {
        debugDefaultTargetPlatformOverride = TargetPlatform.android;
        calls.clear();
      });
      tearDown(() {
        debugDefaultTargetPlatformOverride = null;
        for (final m in ['getSmsWithUserConsentApi', 'getSmsWithRetrieverApi', 'removeUserConsentListener', 'removeSmsRetrieverListener', 'getAppSignature']) {
          messenger.setMockMessageHandler('$prefix$m', null);
        }
      });

      test('user consent reads the code and passes the sender', () async {
        reply('getSmsWithUserConsentApi', ['Your RealBoom OTP is 482913']);
        expect(await HqAndroidSmsCodeReader(senderPhoneNumber: 'RLBOOM').read(length: 6), '482913');
        expect(calls.single, contains('RLBOOM'));
      });

      test('retriever mode, a failure returns null, and cancel removes the right listener', () async {
        reply('getSmsWithRetrieverApi', ['<#> 7788 is your code kg+TZ3A5qzS']);
        reply('removeSmsRetrieverListener', [null]);
        reply('removeUserConsentListener', [null]);
        final retriever = HqAndroidSmsCodeReader(mode: HqSmsReadMode.retriever);
        expect(await retriever.read(length: 4), '7788');
        await retriever.cancel();
        await HqAndroidSmsCodeReader().cancel();
        expect(calls.where((c) => c.startsWith('remove')).map((c) => c.split(' ').first), ['removeSmsRetrieverListener', 'removeUserConsentListener']);

        reply('getSmsWithUserConsentApi', ['TIMEOUT', 'no sms', null]);
        expect(await HqAndroidSmsCodeReader().read(length: 6), isNull);
      });

      test('app signature', () async {
        reply('getAppSignature', ['kg+TZ3A5qzS']);
        expect(await HqAndroidSmsCodeReader.appSignature(), 'kg+TZ3A5qzS');
        reply('getAppSignature', ['ERR', 'x', null]);
        expect(await HqAndroidSmsCodeReader.appSignature(), isNull);
      });
    });
  });

  group('HqOtpInput auto-read', () {
    testWidgets('a code from the SMS fills the boxes and completes', (tester) async {
      final reader = _FakeSmsReader();
      String? done;
      await tester.pumpWidget(hqTestApp(HqOtpInput(onCompleted: (c) => done = c, resendSeconds: 0, smsReader: reader)));
      expect(reader.reads, hasLength(1));
      reader.reads.single.complete('48-29-13-99');
      await tester.pump();
      expect(done, '482913');
      expect(find.text('3'), findsOneWidget);
    });

    testWidgets('resend restarts the read; a stale result is ignored; dispose cancels', (tester) async {
      final reader = _FakeSmsReader();
      final done = <String>[];
      await tester.pumpWidget(hqTestApp(HqOtpInput(onCompleted: done.add, resendSeconds: 1, onResend: () {}, resendLabel: 'Resend', smsReader: reader)));
      await tester.pump(const Duration(seconds: 1));
      await tester.tap(find.text('Resend'));
      await tester.pump();
      expect(reader.cancels, 1);
      expect(reader.reads, hasLength(2));
      reader.reads[1].complete('111222');
      await tester.pump();
      expect(done, ['111222']);
      await tester.pumpWidget(hqTestApp(const SizedBox()));
      expect(reader.cancels, 2);
    });

    testWidgets('no read while disabled; a null result changes nothing', (tester) async {
      final reader = _FakeSmsReader();
      await tester.pumpWidget(hqTestApp(HqOtpInput(onCompleted: (_) {}, resendSeconds: 0, smsReader: reader, enabled: false)));
      expect(reader.reads, isEmpty);
      final other = _FakeSmsReader();
      final done = <String>[];
      await tester.pumpWidget(hqTestApp(HqOtpInput(key: const ValueKey('b'), onCompleted: done.add, resendSeconds: 0, smsReader: other)));
      other.reads.single.complete(null);
      await tester.pump();
      expect(done, isEmpty);
    });
  });

  group('HqLocalBiometricAuth', () {
    test('status: unsupported, not enrolled, available with kinds', () async {
      expect((await HqLocalBiometricAuth(auth: _FakeLocalAuth(supported: false)).status()).availability, HqBiometricAvailability.unsupported);
      expect((await HqLocalBiometricAuth(auth: _FakeLocalAuth(canCheck: false)).status()).availability, HqBiometricAvailability.unsupported);
      expect((await HqLocalBiometricAuth(auth: _FakeLocalAuth()).status()).availability, HqBiometricAvailability.notEnrolled);
      final face = await HqLocalBiometricAuth(auth: _FakeLocalAuth(enrolled: [BiometricType.fingerprint, BiometricType.face, BiometricType.strong])).status();
      expect(face.isAvailable, isTrue);
      expect(face.kinds, {HqBiometricKind.fingerprint, HqBiometricKind.face, HqBiometricKind.other});
      expect(face.primary, HqBiometricKind.face);
      expect((await HqLocalBiometricAuth(auth: _FakeLocalAuth(enrolled: [BiometricType.weak])).status()).primary, HqBiometricKind.other);
      expect((await HqLocalBiometricAuth(auth: _FakeLocalAuth(enrolled: [BiometricType.iris])).status()).primary, HqBiometricKind.iris);
      expect(HqBiometricStatus.unsupported.primary, isNull);
    });

    test('status: a plugin error or a missing plugin means unsupported, not a crash', () async {
      expect((await HqLocalBiometricAuth(auth: _FakeLocalAuth(statusError: PlatformException(code: 'x'))).status()).availability, HqBiometricAvailability.unsupported);
      expect((await HqLocalBiometricAuth(auth: _FakeLocalAuth(statusError: MissingPluginException())).status()).availability, HqBiometricAvailability.unsupported);
    });

    test('authenticate passes texts and safe options, maps the answer', () async {
      final fake = _FakeLocalAuth();
      expect(await HqLocalBiometricAuth(auth: fake).authenticate(_texts), HqBiometricOutcome.success);
      expect(fake.lastReason, 'Unlock RealBoom');
      expect(fake.lastOptions!.biometricOnly, isTrue);
      expect(fake.lastOptions!.useErrorDialogs, isFalse);
      expect(fake.lastOptions!.stickyAuth, isTrue);
      expect(fake.lastMessages, hasLength(2));
      expect(await HqLocalBiometricAuth(auth: _FakeLocalAuth(result: false)).authenticate(_texts, biometricOnly: false), HqBiometricOutcome.cancelled);
      expect(await HqLocalBiometricAuth(auth: _FakeLocalAuth(error: PlatformException(code: 'LockedOut'))).authenticate(_texts), HqBiometricOutcome.lockedOut);
    });

    test('every platform error code has an outcome', () {
      const expected = {
        'NotAvailable': HqBiometricOutcome.notAvailable,
        'BiometricNotAvailable': HqBiometricOutcome.notAvailable,
        'OtherOperatingSystem': HqBiometricOutcome.notAvailable,
        'biometricOnlyNotSupported': HqBiometricOutcome.notAvailable,
        'NotEnrolled': HqBiometricOutcome.notEnrolled,
        'LockedOut': HqBiometricOutcome.lockedOut,
        'PermanentlyLockedOut': HqBiometricOutcome.permanentlyLockedOut,
        'PasscodeNotSet': HqBiometricOutcome.passcodeNotSet,
        'UserCancelled': HqBiometricOutcome.cancelled,
        'UserFallback': HqBiometricOutcome.cancelled,
        'SystemCancel': HqBiometricOutcome.cancelled,
        'no_fragment_activity': HqBiometricOutcome.failed,
        'auth_in_progress': HqBiometricOutcome.failed,
      };
      expected.forEach((code, outcome) => expect(hqBiometricOutcomeOf(code), outcome, reason: code));
    });
  });

  group('messages and notice', () {
    final messages = HqBiometricMessages.english();

    test('every unsuccessful case has its own message; success and cancel are quiet', () {
      expect(messages.of(HqBiometricOutcome.success), isNull);
      expect(messages.of(HqBiometricOutcome.cancelled), isNull);
      final texts = {for (final o in HqBiometricOutcome.values) if (o != HqBiometricOutcome.success && o != HqBiometricOutcome.cancelled) messages.of(o)};
      expect(texts, isNot(contains(null)));
      expect(texts.length, 6);
      expect(messages.ofStatus(HqBiometricStatus.unsupported), messages.unsupported);
      expect(messages.ofStatus(const HqBiometricStatus(HqBiometricAvailability.notEnrolled)), messages.notEnrolled);
      expect(messages.ofStatus(const HqBiometricStatus(HqBiometricAvailability.available)), isNull);
      const withCancel = HqBiometricMessages(unsupported: 'u', notEnrolled: 'n', lockedOut: 'l', permanentlyLockedOut: 'p', passcodeNotSet: 'c', failed: 'f', cancelled: 'x');
      expect(withCancel.of(HqBiometricOutcome.cancelled), 'x');
    });

    testWidgets('notice shows the device message, prefers the outcome, and hides when nothing to say', (tester) async {
      await tester.pumpWidget(hqTestApp(HqBiometricNotice(messages: messages, status: HqBiometricStatus.unsupported)));
      expect(find.text(messages.unsupported), findsOneWidget);
      expect(find.byIcon(HqAlert.defaultIcon(HqAlertTone.info)), findsOneWidget);

      await tester.pumpWidget(hqTestApp(HqBiometricNotice(messages: messages, status: const HqBiometricStatus(HqBiometricAvailability.notEnrolled), outcome: HqBiometricOutcome.lockedOut, action: const Text('Open settings'))));
      expect(find.text(messages.lockedOut), findsOneWidget);
      expect(find.text('Open settings'), findsOneWidget);
      expect(find.byIcon(HqAlert.defaultIcon(HqAlertTone.warning)), findsOneWidget);

      await tester.pumpWidget(hqTestApp(HqBiometricNotice(messages: messages, status: const HqBiometricStatus(HqBiometricAvailability.available))));
      expect(find.byType(HqAlert), findsNothing);
      await tester.pumpWidget(hqTestApp(HqBiometricNotice(messages: messages, outcome: HqBiometricOutcome.success)));
      expect(find.byType(HqAlert), findsNothing);
    });

    testWidgets('button icon follows the kind', (tester) async {
      var taps = 0;
      await tester.pumpWidget(hqTestApp(HqBiometricButton(label: 'Unlock with Face ID', kind: HqBiometricKind.face, onPressed: () => taps++)));
      expect(find.byIcon(Icons.face), findsOneWidget);
      await tester.tap(find.text('Unlock with Face ID'));
      expect(taps, 1);
      expect(hqBiometricIcon(HqBiometricKind.fingerprint), Icons.fingerprint);
      expect(hqBiometricIcon(HqBiometricKind.iris), Icons.remove_red_eye_outlined);
      expect(hqBiometricIcon(null), Icons.lock_open);
    });
  });

  group('HqBiometricLock', () {
    test('enable needs a successful check; unlock returns the secret; disable wipes it', () async {
      final store = HqMemorySecureStore();
      final bio = _FakeBiometrics(outcomes: [HqBiometricOutcome.cancelled, HqBiometricOutcome.success]);
      final lock = HqBiometricLock(auth: bio, store: store, keyPrefix: 'invest');
      expect(await lock.enable('refresh-1', _texts), HqBiometricOutcome.cancelled);
      expect(await lock.isEnabled(), isFalse);
      expect((await lock.unlock(_texts)).outcome, HqBiometricOutcome.notAvailable);
      expect(await lock.enable('refresh-1', _texts), HqBiometricOutcome.success);
      expect(store.values, {'invest.secret': 'refresh-1'});

      final unlocked = await lock.unlock(_texts);
      expect(unlocked.outcome, HqBiometricOutcome.cancelled);
      expect(unlocked.isSuccess, isFalse);
      final again = await lock.unlock(_texts);
      expect(again.isSuccess, isTrue);
      expect(again.secret, 'refresh-1');

      await lock.updateSecret('refresh-2');
      expect(store.values['invest.secret'], 'refresh-2');
      await lock.disable();
      expect(await lock.isEnabled(), isFalse);
      await lock.updateSecret('refresh-3');
      expect(store.values, isEmpty);
    });

    test('enable is refused on an unsupported or not-enrolled device without prompting', () async {
      final unsupported = _FakeBiometrics(statusValue: HqBiometricStatus.unsupported);
      expect(await HqBiometricLock(auth: unsupported, store: HqMemorySecureStore()).enable('s', _texts), HqBiometricOutcome.notAvailable);
      final none = _FakeBiometrics(statusValue: const HqBiometricStatus(HqBiometricAvailability.notEnrolled));
      expect(await HqBiometricLock(auth: none, store: HqMemorySecureStore()).enable('s', _texts), HqBiometricOutcome.notEnrolled);
      expect(unsupported.prompts + none.prompts, 0);
      expect(() => HqBiometricLock(auth: none, store: HqMemorySecureStore()).enable('', _texts), throwsArgumentError);
      expect(() => HqBiometricLock(auth: none, store: HqMemorySecureStore()).updateSecret(''), throwsArgumentError);
    });

    for (final outcome in [HqBiometricOutcome.permanentlyLockedOut, HqBiometricOutcome.notEnrolled]) {
      test('$outcome on unlock disables the lock', () async {
        final store = HqMemorySecureStore()..values['rou.biometric.secret'] = 'token';
        final result = await HqBiometricLock(auth: _FakeBiometrics(outcomes: [outcome]), store: store).unlock(_texts);
        expect(result.outcome, outcome);
        expect(store.values, isEmpty);
      });
    }

    test('a temporary lockout keeps the lock', () async {
      final store = HqMemorySecureStore()..values['rou.biometric.secret'] = 'token';
      expect((await HqBiometricLock(auth: _FakeBiometrics(outcomes: [HqBiometricOutcome.lockedOut]), store: store).unlock(_texts)).outcome, HqBiometricOutcome.lockedOut);
      expect(store.values, isNotEmpty);
    });

    test('device store reads and writes through flutter_secure_storage', () async {
      const channel = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
      final data = <String, String>{};
      final options = <Map<Object?, Object?>>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (call) async {
        final args = call.arguments as Map;
        options.add(args['options'] as Map);
        switch (call.method) {
          case 'write':
            data[args['key'] as String] = args['value'] as String;
          case 'read':
            return data[args['key']];
          case 'delete':
            data.remove(args['key']);
        }
        return null;
      });
      addTearDown(() => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null));
      final store = HqDeviceSecureStore();
      await store.write('k', 'v');
      expect(await store.read('k'), 'v');
      await store.delete('k');
      expect(await store.read('k'), isNull);
      expect(options, isNotEmpty);
      expect(HqDeviceSecureStore.defaultStorage.iOptions.toMap()['accessibility'], 'unlocked_this_device');
      // flutter_secure_storage 11 always encrypts on Android (AES-GCM, key in the Android Keystore).
      expect(HqDeviceSecureStore.defaultStorage.aOptions.toMap()['storageCipherAlgorithm'], 'AES_GCM_NoPadding');
    });
  });
}
