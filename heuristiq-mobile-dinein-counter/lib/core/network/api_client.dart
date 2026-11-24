import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../auth/session.dart';
import '../auth/session_provider.dart';
import '../config/app_config.dart';
import '../storage/secure_storage.dart';

BaseOptions _baseOptions() => BaseOptions(
      baseUrl: AppConfig.apiBase,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 20),
      headers: {'X-App-Id': AppConfig.appId},
    );

/// No auth interceptor: used only by the session endpoints, which must not refresh themselves.
final plainDioProvider = Provider<Dio>((ref) => Dio(_baseOptions()));

final sessionManagerProvider = Provider<SessionManager>((ref) {
  return SessionManager(ref.watch(plainDioProvider), ref.watch(secureStorageProvider));
});

const _retriedKey = 'retriedAfterRefresh';

/// The Dio every feature uses: adds the cashier's access token, refreshing it first when it is about to
/// expire, and retries a request once if the server still says 401.
final apiDioProvider = Provider<Dio>((ref) {
  final session = ref.watch(sessionManagerProvider);
  final dio = Dio(_baseOptions());
  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        try {
          options.headers['Authorization'] = 'Bearer ${await session.accessToken()}';
          handler.next(options);
        } on SessionExpired catch (e) {
          ref.read(sessionProvider.notifier).expired();
          handler.reject(DioException(
            requestOptions: options,
            error: e,
            type: DioExceptionType.cancel,
          ));
        } on DioException catch (e) {
          handler.reject(DioException(
              requestOptions: options, error: e.error, type: e.type, response: e.response));
        }
      },
      onError: (error, handler) async {
        final options = error.requestOptions;
        if (error.response?.statusCode == 401 && options.extra[_retriedKey] != true) {
          try {
            final token = await session.forceRefresh();
            options.extra[_retriedKey] = true;
            options.headers['Authorization'] = 'Bearer $token';
            return handler.resolve(await dio.fetch<dynamic>(options));
          } on SessionExpired {
            ref.read(sessionProvider.notifier).expired();
          } on DioException catch (e) {
            return handler.next(e);
          }
        }
        handler.next(error);
      },
    ),
  );
  return dio;
});

bool isConnectivityError(DioException e) =>
    e.type == DioExceptionType.connectionError ||
    e.type == DioExceptionType.connectionTimeout ||
    e.type == DioExceptionType.sendTimeout ||
    e.type == DioExceptionType.receiveTimeout;

/// A message fit for a cashier: the backend's own wording when it sent one.
String apiMessage(Object error, {String fallback = 'Something went wrong'}) {
  if (error is DioException) {
    if (error.error is SessionExpired) return '${error.error}';
    if (isConnectivityError(error)) return "Can't reach the server. Check the network and try again.";
    final data = error.response?.data;
    if (data is Map) {
      final message = data['message'] ?? data['error'];
      if (message is String && message.isNotEmpty) return message;
    }
    return fallback;
  }
  final text = '$error';
  return text.startsWith('Exception: ') ? text.substring('Exception: '.length) : text;
}

/// The backend's error code (for example ALREADY_PAID), if it sent one.
String? apiCode(Object error) {
  if (error is DioException) {
    final data = error.response?.data;
    if (data is Map && data['code'] is String) return data['code'] as String;
  }
  return null;
}
