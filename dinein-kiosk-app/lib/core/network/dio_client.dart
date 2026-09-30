import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../config/app_config.dart';
import '../storage/secure_storage.dart';

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
        // A revoked or unknown device token sends the kiosk back to pairing.
        if (error.response?.statusCode == 401) await storage.clearAll();
        handler.next(error);
      },
    ),
  );
  return dio;
});

/// Turns a failed request into a message the UI can show, using the
/// backend's `message` or `error` field when it sends one.
Never throwApiError(DioException e, {String fallback = 'Something went wrong'}) {
  final data = e.response?.data;
  if (data is Map) {
    final message = data['message'] ?? data['error'];
    if (message is String && message.isNotEmpty) throw Exception(message);
  }
  throw Exception(fallback);
}
