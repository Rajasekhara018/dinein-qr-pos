import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

/// Supplies the bearer token of the signed-in user (null when signed out).
typedef HqTokenProvider = FutureOr<String?> Function();

/// A call to the gateway failed: network problem, non-2xx status, or `success: false` in the envelope.
///
/// The message comes from the server and is not translated; show a localized text chosen from [statusCode] / [errorCode].
class HqApiException implements Exception {
  const HqApiException({required this.statusCode, required this.message, this.errorCode});

  /// HTTP status, or 0 when no response arrived (offline, timeout).
  final int statusCode;
  final String message;
  final String? errorCode;

  bool get isUnauthorized => statusCode == 401;
  bool get isNetworkError => statusCode == 0;

  @override
  String toString() => 'HqApiException($statusCode, $errorCode, $message)';
}

/// Small JSON client for the gateway that unwraps the `CommonResponseObject` envelope
/// (`{success, message, responseObject, errorCode, timestamp}`) every Rider service returns.
class HqApiClient {
  HqApiClient({
    required String baseUrl,
    this.tokenProvider,
    http.Client? httpClient,
    this.correlationId,
    this.timeout = const Duration(seconds: 20),
  })  : baseUrl = baseUrl.replaceAll(RegExp(r'/+$'), ''),
        _http = httpClient ?? http.Client();

  /// Gateway base including `/api`, without a trailing slash.
  final String baseUrl;
  final HqTokenProvider? tokenProvider;

  /// Returns the correlation id forwarded as `X-Correlation-ID`.
  final String Function()? correlationId;
  final Duration timeout;
  final http.Client _http;

  /// GET [path] (below [baseUrl]); returns the envelope's `responseObject`.
  Future<Object?> get(String path, {Map<String, String>? query}) =>
      _send('GET', path, query: query);

  /// POST [body] as JSON to [path]; returns the envelope's `responseObject`.
  Future<Object?> post(String path, {Object? body, Map<String, String>? query}) =>
      _send('POST', path, body: body, query: query);

  /// Releases the underlying HTTP client.
  void close() => _http.close();

  Future<Object?> _send(String method, String path, {Object? body, Map<String, String>? query}) async {
    final uri = Uri.parse('$baseUrl$path').replace(queryParameters: (query == null || query.isEmpty) ? null : query);
    final headers = <String, String>{'Accept': 'application/json'};
    if (body != null) headers['Content-Type'] = 'application/json';
    final token = await tokenProvider?.call();
    if (token != null && token.isNotEmpty) headers['Authorization'] = 'Bearer $token';
    final correlation = correlationId?.call();
    if (correlation != null && correlation.isNotEmpty) headers['X-Correlation-ID'] = correlation;

    final http.Response response;
    try {
      final request = http.Request(method, uri)..headers.addAll(headers);
      if (body != null) request.body = jsonEncode(body);
      final streamed = await _http.send(request).timeout(timeout);
      response = await http.Response.fromStream(streamed).timeout(timeout);
    } on TimeoutException {
      throw const HqApiException(statusCode: 0, message: 'timeout');
    } on http.ClientException catch (e) {
      throw HqApiException(statusCode: 0, message: e.message);
    }

    Object? decoded;
    if (response.bodyBytes.isNotEmpty) {
      try {
        decoded = jsonDecode(utf8.decode(response.bodyBytes));
      } on FormatException {
        decoded = null;
      }
    }
    final envelope = decoded is Map<String, dynamic> ? decoded : null;
    final failed = response.statusCode < 200 || response.statusCode >= 300 || envelope?['success'] == false;
    if (failed) {
      throw HqApiException(
        statusCode: response.statusCode,
        message: (envelope?['message'] as String?) ?? response.reasonPhrase ?? '',
        errorCode: envelope?['errorCode'] as String?,
      );
    }
    return envelope == null ? decoded : envelope['responseObject'];
  }
}
