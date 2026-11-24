import 'package:dio/dio.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/app_config.dart';
import '../../../core/network/dio_client.dart';

class StaffIdentity extends Equatable {
  const StaffIdentity({required this.displayName, required this.role});

  final String displayName;
  final String role;

  @override
  List<Object?> get props => [displayName, role];
}

abstract class StaffRepository {
  Future<StaffIdentity> unlock(String username, String pin);
}

/// The PIN accepted in demo mode, so the service menu can be tried without a backend.
const demoStaffPin = '1234';

final staffRepositoryProvider = Provider<StaffRepository>((ref) {
  if (AppConfig.demoMode) return DemoStaffRepository();
  return ApiStaffRepository(ref.watch(dioProvider));
});

class ApiStaffRepository implements StaffRepository {
  ApiStaffRepository(this._dio);

  final Dio _dio;

  @override
  Future<StaffIdentity> unlock(String username, String pin) async {
    try {
      final response = await _dio.post(
        '/api/v1/kiosk/staff-unlock',
        data: {'username': username.trim(), 'pin': pin},
      );
      final data = Map<String, dynamic>.from(response.data as Map);
      return StaffIdentity(
        displayName: data['displayName'] as String? ?? username,
        role: data['role'] as String? ?? '',
      );
    } on DioException catch (e) {
      // 403 = wrong username / PIN / not allowed. 423 = locked out, and the backend says for how long.
      if (e.response?.statusCode == 403) throw Exception('Wrong username or PIN');
      throwApiError(e, fallback: 'Could not check the PIN');
    }
  }
}

class DemoStaffRepository implements StaffRepository {
  @override
  Future<StaffIdentity> unlock(String username, String pin) async {
    await Future<void>.delayed(const Duration(milliseconds: 200));
    if (username.trim().isEmpty || pin != demoStaffPin) {
      throw Exception('Wrong username or PIN');
    }
    return StaffIdentity(displayName: username.trim(), role: 'WAITER');
  }
}
