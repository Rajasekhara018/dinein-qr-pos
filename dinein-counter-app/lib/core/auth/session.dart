import 'package:dio/dio.dart';
import 'package:equatable/equatable.dart';

import '../storage/secure_storage.dart';

class StaffUser extends Equatable {
  const StaffUser({
    required this.id,
    required this.username,
    required this.displayName,
    required this.role,
    required this.restaurantId,
    this.mustChangePassword = false,
  });

  final int id;
  final String username;
  final String displayName;
  final String role;
  final int restaurantId;
  final bool mustChangePassword;

  factory StaffUser.fromJson(Map<String, dynamic> json) => StaffUser(
        id: (json['id'] as num).toInt(),
        username: json['username'] as String? ?? '',
        displayName: (json['displayName'] as String?)?.trim().isNotEmpty == true
            ? json['displayName'] as String
            : json['username'] as String? ?? '',
        role: json['role'] as String? ?? '',
        restaurantId: (json['restaurantId'] as num?)?.toInt() ?? 0,
        mustChangePassword: json['mustChangePassword'] as bool? ?? false,
      );

  @override
  List<Object?> get props => [id, username, displayName, role, restaurantId, mustChangePassword];
}

/// A manager or owner whose temporary password has not been replaced yet. The server limits such a login to the
/// change-password call, so every counter request would fail; better to say what to do.
class PasswordChangeRequired implements Exception {
  const PasswordChangeRequired();

  @override
  String toString() =>
      'You need to set a new password first. Sign in on the admin website, change it, then sign in here.';
}

class SessionExpired implements Exception {
  const SessionExpired();

  @override
  String toString() => 'Your session has ended. Please sign in again.';
}

/// Roles allowed to work the counter. Kitchen accounts sign in on the kitchen screen instead.
const counterRoles = {'WAITER', 'MANAGER', 'OWNER'};

const _refreshCookie = 'dinein_rt';
const _csrfCookie = 'XSRF-TOKEN';
const _csrfHeader = 'X-XSRF-TOKEN';

/// Signs a cashier in and keeps the short-lived access token fresh.
///
/// The backend's refresh token is a rotating cookie: each use returns a new one and re-using an old one is
/// treated as theft and ends the whole login. So refreshes are strictly serialised (one in flight at a time,
/// everyone else waits for its result), and the new token is saved before anything else uses it.
class SessionManager {
  SessionManager(this._dio, this._storage, {DateTime Function()? clock})
      : _now = clock ?? DateTime.now;

  /// A Dio with no auth interceptor: the session endpoints must not recurse into themselves.
  final Dio _dio;
  final SecureStorageService _storage;
  final DateTime Function() _now;

  String? _accessToken;
  DateTime? _expiresAt;
  StaffUser? _user;
  Future<void>? _refreshing;

  StaffUser? get user => _user;
  bool get hasSession => _accessToken != null;

  /// Either [password] or [pin] (PIN sign-in is for waiter accounts).
  Future<StaffUser> login(String username, {String? password, String? pin}) async {
    try {
      final response = await _dio.post(
        '/api/v1/auth/login',
        data: {
          'username': username.trim(),
          if (password != null && password.isNotEmpty) 'password': password,
          if ((password == null || password.isEmpty) && pin != null) 'pin': pin,
        },
      );
      final user = await _adopt(response);
      if (!counterRoles.contains(user.role)) {
        await signOutLocally();
        throw Exception('This account cannot use the counter. Kitchen accounts sign in on the kitchen screen.');
      }
      return user;
    } on DioException catch (e) {
      throw Exception(_message(e, 'Could not sign in'));
    }
  }

  /// Restores the session after a restart from the saved refresh token. Returns null when there is none
  /// or it no longer works; a network failure is rethrown so the caller can offer a retry instead of signing out.
  Future<StaffUser?> restore() async {
    if (await _storage.readRefreshToken() == null) return null;
    try {
      await _refresh();
    } on SessionExpired {
      return null;
    }
    return _user;
  }

  /// The access token to send, refreshed first if it is about to expire. Throws [SessionExpired] if it cannot be.
  Future<String> accessToken() async {
    final expiresAt = _expiresAt;
    if (_accessToken == null ||
        expiresAt == null ||
        expiresAt.difference(_now()) < const Duration(seconds: 45)) {
      await _refresh();
    }
    return _accessToken ?? (throw const SessionExpired());
  }

  /// Called when the server rejected the current access token.
  Future<String> forceRefresh() async {
    _accessToken = null;
    await _refresh();
    return _accessToken ?? (throw const SessionExpired());
  }

  Future<void> logout() async {
    final refreshToken = await _storage.readRefreshToken();
    if (refreshToken != null) {
      try {
        final csrf = await _fetchCsrf();
        await _dio.post(
          '/api/v1/auth/logout',
          options: Options(headers: _cookieHeaders(refreshToken, csrf)),
        );
      } catch (_) {
        // Signing out locally is what matters; the server-side token also expires on its own.
      }
    }
    await signOutLocally();
  }

  Future<void> signOutLocally() async {
    _accessToken = null;
    _expiresAt = null;
    _user = null;
    await _storage.clear();
  }

  // ----- refresh -------------------------------------------------------------------------------

  Future<void> _refresh() {
    return _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);
  }

  Future<void> _doRefresh() async {
    final refreshToken = await _storage.readRefreshToken();
    if (refreshToken == null) {
      await signOutLocally();
      throw const SessionExpired();
    }
    try {
      final csrf = await _fetchCsrf();
      final response = await _dio.post(
        '/api/v1/auth/refresh',
        options: Options(headers: _cookieHeaders(refreshToken, csrf)),
      );
      await _adopt(response);
    } on DioException catch (e) {
      final status = e.response?.statusCode;
      if (status == 401 || status == 403) {
        await signOutLocally();
        throw const SessionExpired();
      }
      rethrow; // No connection etc.: keep the session, the caller may retry.
    }
  }

  Future<String> _fetchCsrf() async {
    final response = await _dio.get('/api/v1/auth/csrf');
    return _cookie(response, _csrfCookie) ?? '';
  }

  Map<String, String> _cookieHeaders(String refreshToken, String csrf) => {
        'Cookie': '$_refreshCookie=$refreshToken; $_csrfCookie=$csrf',
        _csrfHeader: csrf,
      };

  Future<StaffUser> _adopt(Response<dynamic> response) async {
    final data = Map<String, dynamic>.from(response.data as Map);
    final incoming = StaffUser.fromJson(Map<String, dynamic>.from(data['user'] as Map));
    if (incoming.mustChangePassword) {
      await signOutLocally();
      throw const PasswordChangeRequired();
    }
    final refreshToken = _cookie(response, _refreshCookie);
    if (refreshToken != null && refreshToken.isNotEmpty) {
      // Saved before the new access token is used anywhere: a crash now must not lose the rotated token.
      await _storage.saveRefreshToken(refreshToken);
    }
    _accessToken = data['accessToken'] as String;
    _expiresAt = _now().add(Duration(seconds: (data['expiresIn'] as num?)?.toInt() ?? 900));
    _user = StaffUser.fromJson(Map<String, dynamic>.from(data['user'] as Map));
    return _user!;
  }

  static String? _cookie(Response<dynamic> response, String name) {
    for (final header in response.headers['set-cookie'] ?? const <String>[]) {
      final first = header.split(';').first.trim();
      if (first.startsWith('$name=')) return first.substring(name.length + 1);
    }
    return null;
  }

  static String _message(DioException e, String fallback) {
    final data = e.response?.data;
    if (data is Map && data['message'] is String) return data['message'] as String;
    if (e.type == DioExceptionType.connectionError ||
        e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout) {
      return "Can't reach the server. Check the network and try again.";
    }
    return fallback;
  }
}
