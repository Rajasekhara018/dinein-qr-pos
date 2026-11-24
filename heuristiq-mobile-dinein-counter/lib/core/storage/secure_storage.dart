import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final secureStorageProvider = Provider<SecureStorageService>((ref) => SecureStorageService());

/// Holds the cashier's refresh token, so a restarted tablet can sign back in without asking for the password.
class SecureStorageService {
  SecureStorageService() : _storage = const FlutterSecureStorage();

  final FlutterSecureStorage _storage;

  static const _refreshKey = 'counterRefreshToken';

  Future<String?> readRefreshToken() => _storage.read(key: _refreshKey);

  Future<void> saveRefreshToken(String token) => _storage.write(key: _refreshKey, value: token);

  Future<void> clear() => _storage.deleteAll();
}
