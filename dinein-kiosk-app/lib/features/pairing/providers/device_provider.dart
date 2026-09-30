import 'package:dio/dio.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/app_config.dart';
import '../../../core/network/dio_client.dart';
import '../../../core/storage/secure_storage.dart';

class DeviceState extends Equatable {
  const DeviceState({
    this.loading = true,
    this.paired = false,
    this.restaurantName,
    this.error,
  });

  final bool loading;
  final bool paired;
  final String? restaurantName;
  final String? error;

  @override
  List<Object?> get props => [loading, paired, restaurantName, error];
}

final deviceProvider = StateNotifierProvider<DeviceNotifier, DeviceState>((ref) {
  return DeviceNotifier(ref.watch(secureStorageProvider), ref.watch(dioProvider));
});

class DeviceNotifier extends StateNotifier<DeviceState> {
  DeviceNotifier(this._storage, this._dio) : super(const DeviceState()) {
    _load();
  }

  final SecureStorageService _storage;
  final Dio _dio;

  Future<void> _load() async {
    final token = await _storage.readDeviceToken();
    final name = await _storage.readRestaurantName();
    state = DeviceState(
      loading: false,
      paired: token != null,
      restaurantName: name,
    );
  }

  Future<bool> pair(String code) async {
    state = const DeviceState(loading: true);
    try {
      String token;
      String name;
      if (AppConfig.demoMode) {
        token = 'demo-device-token';
        name = 'Demo Restaurant';
      } else {
        final response = await _dio.post(
          '/api/v1/kiosk/pair',
          data: {'code': code},
        );
        final data = Map<String, dynamic>.from(response.data as Map);
        token = data['deviceToken'] as String;
        name = data['restaurantName'] as String? ?? '';
      }
      await _storage.saveDevice(token: token, restaurantName: name);
      state = DeviceState(loading: false, paired: true, restaurantName: name);
      return true;
    } on DioException catch (e) {
      state = DeviceState(loading: false, error: _message(e));
      return false;
    }
  }

  Future<void> unpair() async {
    await _storage.clearAll();
    state = const DeviceState(loading: false);
  }

  String _message(DioException e) {
    final data = e.response?.data;
    if (data is Map && data['message'] is String) return data['message'] as String;
    if (e.response?.statusCode == 404 || e.response?.statusCode == 400) {
      return 'That pairing code is not valid';
    }
    return 'Could not reach the server';
  }
}
