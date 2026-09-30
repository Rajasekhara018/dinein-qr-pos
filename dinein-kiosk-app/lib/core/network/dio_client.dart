import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../config/app_config.dart';
import '../storage/secure_storage.dart';
import '../update/update_required.dart';

final dioProvider = Provider<Dio>((ref) {
  final storage = ref.watch(secureStorageProvider);
  final dio = Dio(
    BaseOptions(
      baseUrl: AppConfig.apiBase,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 20),
      headers: {'X-App-Id': AppConfig.appId},
    ),
  );

  String? version;
  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        final token = await storage.readDeviceToken();
        if (token != null) options.headers['Authorization'] = 'Bearer $token';
        version ??= (await PackageInfo.fromPlatform()).version;
        options.headers['X-App-Version'] = version;
        handler.next(options);
      },
      onError: (error, handler) async {
        final status = error.response?.statusCode;
        // A revoked or unknown device token sends the kiosk back to pairing.
        // (The staff-unlock endpoint answers a wrong PIN with 403, never 401.)
        if (status == 401) await storage.clearAll();
        // This app is older than the backend supports.
        if (status == 426) ref.read(updateRequiredProvider.notifier).state = true;
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

/// Turns a failed request into a message the UI can show, using the
/// backend's `message` or `error` field when it sends one.
Never throwApiError(DioException e, {String fallback = 'Something went wrong'}) {
  if (isConnectivityError(e)) {
    throw Exception("Can't reach the server. Please try again or order at the counter.");
  }
  final data = e.response?.data;
  if (data is Map) {
    final message = data['message'] ?? data['error'];
    if (message is String && message.isNotEmpty) throw Exception(message);
  }
  throw Exception(fallback);
}
