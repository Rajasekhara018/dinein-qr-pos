import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:http/http.dart' as http;

import '../core/hq_api_client.dart';

/// Gateway path (below the gateway base, which ends in `/api`): Rider_AuditService serves `/api/v1/audit/...`.
const String hqScreenViewsPath = '/v1/audit/screen-views';

/// Matches Rider_AuditService's screen key pattern (same as the web `SCREEN_KEY_PATTERN`).
final RegExp hqScreenKeyPattern = RegExp(r'^[a-z][a-z0-9_-]*(\.[a-z0-9_-]+)*$');

const int hqScreenViewMaxKeyLength = 100;
const int hqScreenViewMaxBuffered = 200;
const int hqScreenViewBatchSize = 20;
const Duration hqScreenViewFlushInterval = Duration(seconds: 15);
const Duration hqScreenViewRateLimitPause = Duration(seconds: 60);

/// Reports each screen the signed-in user opens to the central audit trail, exactly as the web `ScreenViewService` does:
/// `POST {gateway}/v1/audit/screen-views` with `{events: [{eventId, screenKey, occurredAt, appChannel, feature, details}]}`.
///
/// Events are buffered in memory (capped at 200), sent in batches of 20 every 15 seconds and when [flush] is called (call it
/// when the app goes to the background). Everything is best effort: nothing throws, a failed batch is dropped, nothing is sent
/// while signed out, and after HTTP 429 reporting pauses for a minute. The user and role come from the bearer token, never
/// the body. Screen keys are lower-case dotted (`invest.portfolio`) and are never translated or renamed after release.
class HqScreenAudit {
  HqScreenAudit({
    required String gatewayUrl,
    required this.tokenProvider,
    this.appChannel = 'CUSTOMER_APP',
    this.platform = 'mobile',
    this.appVersion,
    http.Client? httpClient,
    DateTime Function()? clock,
    String Function()? idGenerator,
    Duration flushInterval = hqScreenViewFlushInterval,
    bool autoFlush = true,
  })  : _endpoint = Uri.parse('${gatewayUrl.replaceAll(RegExp(r'/+$'), '')}$hqScreenViewsPath'),
        _http = httpClient ?? http.Client(),
        _clock = clock ?? DateTime.now,
        _newId = idGenerator ?? _defaultId {
    if (autoFlush) _timer = Timer.periodic(flushInterval, (_) => unawaited(flush()));
  }

  final HqTokenProvider tokenProvider;

  /// `appChannel` of every event; Rider_AuditService accepts WEB, ADMIN_WEB, WALLET_WEB, CUSTOMER_APP, DRIVER_APP, SERVICE.
  final String appChannel;
  final String platform;
  final String? appVersion;
  final Uri _endpoint;
  final http.Client _http;
  final DateTime Function() _clock;
  final String Function() _newId;
  Timer? _timer;

  final List<Map<String, Object?>> _buffer = [];
  String? _lastKey;
  DateTime? _pausedUntil;
  bool _flushing = false;
  static int _sequence = 0;

  /// Events waiting to be sent.
  int get pendingCount => _buffer.length;

  /// Buffers one screen view. A key that does not match [hqScreenKeyPattern], or the same key twice in a row, is ignored.
  void report(String screenKey) {
    try {
      final key = screenKey.length > hqScreenViewMaxKeyLength ? screenKey.substring(0, hqScreenViewMaxKeyLength) : screenKey;
      if (!hqScreenKeyPattern.hasMatch(key) || key == _lastKey) return;
      final previous = _lastKey;
      _lastKey = key;
      final dot = key.indexOf('.');
      _buffer.add(<String, Object?>{
        'eventId': _newId(),
        'screenKey': key,
        'occurredAt': _clock().toUtc().toIso8601String(),
        'appChannel': appChannel,
        'feature': dot > 0 ? key.substring(0, dot) : key,
        'details': <String, Object?>{
          'platform': platform,
          if (appVersion != null) 'appVersion': appVersion,
          if (previous != null) 'previousScreenKey': previous,
        },
      });
      if (_buffer.length > hqScreenViewMaxBuffered) _buffer.removeRange(0, _buffer.length - hqScreenViewMaxBuffered);
      if (_buffer.length >= hqScreenViewBatchSize) unawaited(flush());
    } on Object {
      // Audit must never affect the app.
    }
  }

  /// Forgets buffered events and the last key (call on sign-out so the next user's trail starts clean).
  void reset() {
    _buffer.clear();
    _lastKey = null;
  }

  /// Sends buffered events now, one batch of up to 20 at a time. Never throws.
  Future<void> flush() async {
    if (_buffer.isEmpty || _flushing) return;
    final pause = _pausedUntil;
    if (pause != null && _clock().isBefore(pause)) return;
    _flushing = true;
    try {
      final token = await tokenProvider();
      if (token == null || token.isEmpty) {
        _buffer.clear();
        return;
      }
      while (_buffer.isNotEmpty) {
        final batch = _buffer.sublist(0, min(hqScreenViewBatchSize, _buffer.length));
        _buffer.removeRange(0, batch.length);
        final response = await _http.post(
          _endpoint,
          headers: <String, String>{'Content-Type': 'application/json', 'Authorization': 'Bearer $token'},
          body: jsonEncode(<String, Object?>{'events': batch}),
        );
        if (response.statusCode == 429) {
          _pausedUntil = _clock().add(hqScreenViewRateLimitPause);
          return;
        }
      }
    } on Object {
      // Offline or network error: the batch is dropped.
    } finally {
      _flushing = false;
    }
  }

  /// Stops the timer and drops buffered events.
  void dispose() {
    _timer?.cancel();
    _timer = null;
    _buffer.clear();
  }

  static String _defaultId() {
    final random = Random().nextInt(1 << 32).toRadixString(36);
    return '${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}-${(_sequence++).toRadixString(36)}-$random';
  }
}
