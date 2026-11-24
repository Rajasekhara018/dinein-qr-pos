import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final secureStorageProvider = Provider<SecureStorageService>((ref) {
  return SecureStorageService();
});

class SecureStorageService {
  SecureStorageService() : _storage = const FlutterSecureStorage();

  final FlutterSecureStorage _storage;

  static const _deviceTokenKey = 'kioskDeviceToken';
  static const _restaurantNameKey = 'kioskRestaurantName';

  Future<String?> readDeviceToken() => _storage.read(key: _deviceTokenKey);

  Future<String?> readRestaurantName() =>
      _storage.read(key: _restaurantNameKey);

  Future<void> saveDevice({
    required String token,
    required String restaurantName,
  }) async {
    await _storage.write(key: _deviceTokenKey, value: token);
    await _storage.write(key: _restaurantNameKey, value: restaurantName);
  }

  Future<void> clearAll() => _storage.deleteAll();
}
