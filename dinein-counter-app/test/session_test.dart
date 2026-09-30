import 'dart:convert';
import 'dart:typed_data';

import 'package:dinein_counter/core/auth/session.dart';
import 'package:dinein_counter/core/storage/secure_storage.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

class MemoryStorage extends SecureStorageService {
  String? refreshToken;

  @override
  Future<String?> readRefreshToken() async => refreshToken;

  @override
  Future<void> saveRefreshToken(String token) async => refreshToken = token;

  @override
  Future<void> clear() async => refreshToken = null;
}

/// A tiny stand-in for the backend's auth endpoints: rotating refresh cookie plus CSRF cookie.
class FakeAuthServer implements HttpClientAdapter {
  FakeAuthServer({this.role = 'WAITER'});

  final String role;
  final requests = <RequestOptions>[];
  int _rotation = 0;
  String currentRefresh = 'rt-0';
  bool offline = false;
  bool rejectRefresh = false;
  Duration refreshDelay = Duration.zero;
  int accessCounter = 0;

  int get refreshCalls => requests.where((r) => r.path.endsWith('/auth/refresh')).length;

  Map<String, dynamic> _session({int expiresIn = 900}) => {
        'accessToken': 'access-${++accessCounter}',
        'expiresIn': expiresIn,
        'user': {
          'id': 7,
          'username': 'asha',
          'displayName': 'Asha',
          'role': role,
          'restaurantId': 1,
          'mustChangePassword': false,
        },
      };

  ResponseBody _json(Object body, {int status = 200, List<String> cookies = const []}) =>
      ResponseBody.fromString(jsonEncode(body), status, headers: {
        Headers.contentTypeHeader: ['application/json'],
        if (cookies.isNotEmpty) 'set-cookie': cookies,
      });

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream,
      Future<void>? cancelFuture) async {
    requests.add(options);
    if (offline) {
      throw DioException(requestOptions: options, type: DioExceptionType.connectionError);
    }
    final path = options.path;
    if (path.endsWith('/auth/csrf')) {
      return ResponseBody.fromString('', 204, headers: {
        'set-cookie': ['XSRF-TOKEN=csrf-abc; Path=/; SameSite=Lax'],
      });
    }
    if (path.endsWith('/auth/login')) {
      currentRefresh = 'rt-${++_rotation}';
      return _json(_session(), cookies: ['dinein_rt=$currentRefresh; Path=/api/v1/auth; HttpOnly']);
    }
    if (path.endsWith('/auth/refresh')) {
      await Future<void>.delayed(refreshDelay);
      final cookie = '${options.headers['Cookie']}';
      final csrf = options.headers['X-XSRF-TOKEN'];
      final valid = !rejectRefresh &&
          cookie.contains('dinein_rt=$currentRefresh') &&
          csrf == 'csrf-abc' &&
          cookie.contains('XSRF-TOKEN=csrf-abc');
      if (!valid) return _json({'code': 'UNAUTHORIZED', 'message': 'Session expired'}, status: 401);
      currentRefresh = 'rt-${++_rotation}';
      return _json(_session(), cookies: ['dinein_rt=$currentRefresh; Path=/api/v1/auth; HttpOnly']);
    }
    if (path.endsWith('/auth/logout')) return ResponseBody.fromString('', 204);
    return _json({'code': 'NOT_FOUND', 'message': 'no'}, status: 404);
  }

  @override
  void close({bool force = false}) {}
}

SessionManager managerFor(FakeAuthServer server, MemoryStorage storage, {DateTime Function()? clock}) {
  final dio = Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = server;
  return SessionManager(dio, storage, clock: clock);
}

void main() {
  test('login stores the refresh cookie and returns the cashier', () async {
    final server = FakeAuthServer();
    final storage = MemoryStorage();
    final manager = managerFor(server, storage);

    final user = await manager.login('asha', pin: '1234');

    expect(user.displayName, 'Asha');
    expect(user.role, 'WAITER');
    expect(storage.refreshToken, 'rt-1');
    final body = server.requests.firstWhere((r) => r.path.endsWith('/login')).data as Map;
    expect(body, {'username': 'asha', 'pin': '1234'});
  });

  test('a password login sends the password, not a PIN', () async {
    final server = FakeAuthServer(role: 'MANAGER');
    final manager = managerFor(server, MemoryStorage());
    await manager.login('boss', password: 'Secret123');
    final body = server.requests.firstWhere((r) => r.path.endsWith('/login')).data as Map;
    expect(body, {'username': 'boss', 'password': 'Secret123'});
  });

  test('a kitchen account cannot use the counter and is signed out again', () async {
    final storage = MemoryStorage();
    final manager = managerFor(FakeAuthServer(role: 'KITCHEN'), storage);

    await expectLater(manager.login('cook', pin: '1234'),
        throwsA(predicate((e) => '$e'.contains('cannot use the counter'))));
    expect(storage.refreshToken, isNull);
    expect(manager.hasSession, isFalse);
  });

  test('the access token is reused until it is about to expire', () async {
    final server = FakeAuthServer();
    final manager = managerFor(server, MemoryStorage());
    await manager.login('asha', pin: '1234');

    final first = await manager.accessToken();
    final second = await manager.accessToken();

    expect(first, second);
    expect(server.refreshCalls, 0);
  });

  test('near expiry it refreshes with the cookie and CSRF token, and saves the rotated token', () async {
    final server = FakeAuthServer();
    final storage = MemoryStorage();
    var now = DateTime(2026, 9, 30, 10);
    final manager = managerFor(server, storage, clock: () => now);
    await manager.login('asha', pin: '1234');
    final before = await manager.accessToken();

    now = now.add(const Duration(minutes: 14, seconds: 30)); // 30 s left of 15 min
    final after = await manager.accessToken();

    expect(after, isNot(before));
    expect(server.refreshCalls, 1);
    expect(storage.refreshToken, 'rt-2', reason: 'the rotated refresh token must be saved');
    final refresh = server.requests.firstWhere((r) => r.path.endsWith('/refresh'));
    expect('${refresh.headers['Cookie']}', contains('dinein_rt=rt-1'));
    expect(refresh.headers['X-XSRF-TOKEN'], 'csrf-abc');
  });

  test('many requests at once trigger exactly one refresh (a rotated token must never be used twice)', () async {
    final server = FakeAuthServer()..refreshDelay = const Duration(milliseconds: 50);
    var now = DateTime(2026, 9, 30, 10);
    final manager = managerFor(server, MemoryStorage(), clock: () => now);
    await manager.login('asha', pin: '1234');
    now = now.add(const Duration(minutes: 14, seconds: 30));

    final tokens = await Future.wait([for (var i = 0; i < 6; i++) manager.accessToken()]);

    expect(server.refreshCalls, 1);
    expect(tokens.toSet(), hasLength(1));
  });

  test('a rejected refresh ends the session and clears the saved token', () async {
    final server = FakeAuthServer();
    final storage = MemoryStorage();
    var now = DateTime(2026, 9, 30, 10);
    final manager = managerFor(server, storage, clock: () => now);
    await manager.login('asha', pin: '1234');

    server.rejectRefresh = true;
    now = now.add(const Duration(minutes: 20));

    await expectLater(manager.accessToken(), throwsA(isA<SessionExpired>()));
    expect(storage.refreshToken, isNull);
    expect(manager.hasSession, isFalse);
  });

  test('losing the network during a refresh keeps the session so it can be retried', () async {
    final server = FakeAuthServer();
    final storage = MemoryStorage();
    var now = DateTime(2026, 9, 30, 10);
    final manager = managerFor(server, storage, clock: () => now);
    await manager.login('asha', pin: '1234');

    server.offline = true;
    now = now.add(const Duration(minutes: 20));
    await expectLater(manager.accessToken(), throwsA(isA<DioException>()));
    expect(storage.refreshToken, 'rt-1', reason: 'an offline tablet must not forget its login');

    server.offline = false;
    expect(await manager.accessToken(), startsWith('access-'));
  });

  test('restore signs a restarted tablet back in from the saved token', () async {
    final server = FakeAuthServer();
    final storage = MemoryStorage()..refreshToken = 'rt-0';
    final manager = managerFor(server, storage);

    final user = await manager.restore();

    expect(user?.displayName, 'Asha');
    expect(storage.refreshToken, 'rt-1');
  });

  test('restore with nothing saved, or a dead token, gives no session', () async {
    expect(await managerFor(FakeAuthServer(), MemoryStorage()).restore(), isNull);

    final server = FakeAuthServer()..rejectRefresh = true;
    final storage = MemoryStorage()..refreshToken = 'rt-0';
    expect(await managerFor(server, storage).restore(), isNull);
    expect(storage.refreshToken, isNull);
  });

  test('logout tells the server and forgets everything locally', () async {
    final server = FakeAuthServer();
    final storage = MemoryStorage();
    final manager = managerFor(server, storage);
    await manager.login('asha', pin: '1234');

    await manager.logout();

    expect(server.requests.any((r) => r.path.endsWith('/auth/logout')), isTrue);
    expect(storage.refreshToken, isNull);
    expect(manager.user, isNull);
  });

  test('logout still signs out locally when the server cannot be reached', () async {
    final server = FakeAuthServer();
    final storage = MemoryStorage();
    final manager = managerFor(server, storage);
    await manager.login('asha', pin: '1234');

    server.offline = true;
    await manager.logout();

    expect(storage.refreshToken, isNull);
  });
}
