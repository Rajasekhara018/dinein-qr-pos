import 'package:flutter/foundation.dart';
import 'package:smart_auth/smart_auth.dart';

/// How the Android reader gets the SMS.
enum HqSmsReadMode {
  /// SMS User Consent API: the system asks the user once ("Allow RealBoom to read this message?"). Needs no change to the
  /// SMS text, so it works with any NMS template. The default.
  userConsent,

  /// SMS Retriever API: fully automatic, no prompt. The SMS must be short, contain the code and end with the app's
  /// 11-character hash ([HqAndroidSmsCodeReader.appSignature]); the hash differs per signing key (debug, release,
  /// Play App Signing), so the NMS template must carry the release hash.
  retriever,
}

/// Reads a one-time code from an incoming SMS so the user does not type it. Neither implementation needs the
/// `READ_SMS` / `RECEIVE_SMS` permission. The code is handed to the OTP field only; it is never stored or logged.
abstract interface class HqSmsCodeReader {
  /// Waits for the next SMS and returns the [length]-digit code in it, or null when there is none (cancelled, timed out
  /// after about five minutes, refused by the user, not supported on this platform, or no code in the message).
  Future<String?> read({required int length});

  /// Stops waiting; a pending [read] completes with null.
  Future<void> cancel();
}

/// The first run of exactly [length] digits in [sms] (not part of a longer number), or null.
String? hqExtractOtp(String sms, int length) => RegExp('(?<!\\d)\\d{$length}(?!\\d)').firstMatch(sms)?.group(0);

/// The Android implementation over Google Play services (through `smart_auth`). On iOS and other platforms [read]
/// returns null at once: iOS offers the code above the keyboard by itself because the OTP field sets
/// `AutofillHints.oneTimeCode`, and no app may read SMS there.
class HqAndroidSmsCodeReader implements HqSmsCodeReader {
  HqAndroidSmsCodeReader({this.mode = HqSmsReadMode.userConsent, this.senderPhoneNumber});

  final HqSmsReadMode mode;

  /// User Consent only: listen for messages from this sender alone (E.164 or the DLT sender id). Null accepts any sender
  /// that is not in the user's contacts.
  final String? senderPhoneNumber;

  SmartAuth get _api => SmartAuth.instance;

  static bool get _supported => !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  @override
  Future<String?> read({required int length}) async {
    if (!_supported) return null;
    final result = mode == HqSmsReadMode.retriever ? await _api.getSmsWithRetrieverApi() : await _api.getSmsWithUserConsentApi(senderPhoneNumber: senderPhoneNumber);
    final sms = result.isSuccess ? result.data?.sms : null;
    return sms == null ? null : hqExtractOtp(sms, length);
  }

  @override
  Future<void> cancel() async {
    if (!_supported) return;
    await (mode == HqSmsReadMode.retriever ? _api.removeSmsRetrieverApiListener() : _api.removeUserConsentApiListener());
  }

  /// The app hash the SMS Retriever API needs at the end of the SMS (e.g. `kg+TZ3A5qzS`), or null off Android. Read it
  /// once per signing key and give it to the NMS template owner; it is not a secret.
  static Future<String?> appSignature() async {
    if (!_supported) return null;
    final result = await SmartAuth.instance.getAppSignature();
    return result.isSuccess ? result.data : null;
  }
}
