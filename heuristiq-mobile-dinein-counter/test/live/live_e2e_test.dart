// Runs the counter's real session, repository and providers against a running backend. Skipped unless asked for:
//
//   flutter test test/live/live_e2e_test.dart \
//     --dart-define=LIVE_E2E=true --dart-define=COUNTER_API_BASE=http://localhost:8080
//
// The backend must run with the dev profile (sample restaurant "Spice Route Kitchen"). Use a throwaway
// database: it creates staff, a kiosk, and places and pays orders. Sign-in is rate limited to 5 a minute per
// address by the backend, so wait a minute between runs.
import 'package:dinein_counter/core/auth/session.dart';
import 'package:dinein_counter/core/config/app_config.dart';
import 'package:dinein_counter/core/network/api_client.dart';
import 'package:dinein_counter/core/storage/secure_storage.dart';
import 'package:dinein_counter/features/cart/providers/cart_provider.dart';
import 'package:dinein_counter/features/menu/domain/menu_models.dart';
import 'package:dinein_counter/features/orders/data/counter_repository.dart';
import 'package:dinein_counter/features/orders/domain/order_models.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _live = bool.fromEnvironment('LIVE_E2E');

class _MemoryStorage extends SecureStorageService {
  String? refreshToken;

  @override
  Future<String?> readRefreshToken() async => refreshToken;

  @override
  Future<void> saveRefreshToken(String token) async => refreshToken = token;

  @override
  Future<void> clear() async => refreshToken = null;
}

/// The owner's admin API and the kiosk's public side, used only to set the scene.
class _Scene {
  _Scene(this.dio, this.owner);

  final Dio dio;
  final Options owner;

  static const _bootstrapPassword = 'ChangeMe@123';
  static const _ownerPassword = 'E2eOwner#2026x';

  static Future<_Scene> start() async {
    final dio = Dio(BaseOptions(baseUrl: AppConfig.apiBase, validateStatus: (_) => true));
    var response =
        await dio.post('/api/v1/auth/login', data: {'username': 'owner', 'password': _bootstrapPassword});
    if (response.statusCode == 200) {
      final token = response.data['accessToken'] as String;
      response = await dio.post(
        '/api/v1/auth/change-password',
        data: {'currentPassword': _bootstrapPassword, 'newPassword': _ownerPassword},
        options: Options(headers: {'Authorization': 'Bearer $token'}),
      );
    } else {
      response = await dio.post('/api/v1/auth/login', data: {'username': 'owner', 'password': _ownerPassword});
    }
    expect(response.statusCode, 200, reason: 'owner sign-in failed: ${response.data}');
    return _Scene(dio, Options(headers: {'Authorization': 'Bearer ${response.data['accessToken']}'}));
  }

  Future<Map<String, dynamic>> admin(String path, Object body) async {
    final r = await dio.post('/api/v1/admin$path', data: body, options: owner);
    expect(r.statusCode, anyOf(200, 201), reason: 'POST $path -> ${r.statusCode} ${r.data}');
    return Map<String, dynamic>.from(r.data as Map);
  }

  Future<String> newStaff(String role, {String? pin}) async {
    final username = 'e2e-${role.toLowerCase()}-${DateTime.now().microsecondsSinceEpoch}';
    await admin('/staff', {
      'username': username,
      'displayName': 'E2E ${role.toLowerCase()}',
      'role': role,
      'password': 'Staff#2026xy',
      'pin': ?pin,
    });
    return username;
  }

  /// What a customer does at the kiosk: pair a kiosk, order two items, get a token.
  Future<({int orderId, int token, double total})> kioskOrder(MenuData menu) async {
    final device = await admin('/kiosk-devices', {'name': 'E2E kiosk ${DateTime.now().millisecondsSinceEpoch}'});
    final pair = await dio.post('/api/v1/kiosk/pair', data: {'code': device['pairingCode']});
    expect(pair.statusCode, 200, reason: '${pair.data}');
    final kiosk = Options(headers: {'Authorization': 'Bearer ${pair.data['deviceToken']}'});

    final plain = _plainItem(menu);
    final response = await dio.post(
      '/api/v1/kiosk/orders',
      options: kiosk.copyWith(headers: {
        ...kiosk.headers!,
        'Idempotency-Key': 'e2e-${DateTime.now().microsecondsSinceEpoch}',
      }),
      data: {
        'orderType': 'TAKEAWAY',
        'paymentMode': 'PAY_AT_COUNTER',
        'items': [
          {'itemId': plain.id, 'variantId': null, 'addonIds': <int>[], 'quantity': 2, 'notes': 'no onion'},
        ],
      },
    );
    expect(response.statusCode, 200, reason: 'kiosk order failed: ${response.data}');
    return (
      orderId: (response.data['orderId'] as num).toInt(),
      token: (response.data['tokenNumber'] as num).toInt(),
      total: (response.data['total'] as num).toDouble(),
    );
  }
}

MenuItem _plainItem(MenuData menu) =>
    [for (final c in menu.categories) ...c.items].firstWhere((i) => i.available && i.variants.isEmpty);

void main() {
  late _Scene scene;
  late ProviderContainer app;
  late _MemoryStorage storage;
  late SessionManager session;
  late CounterRepository repo;
  late MenuData menu;

  setUpAll(() async {
    if (!_live) return;
    SharedPreferences.setMockInitialValues({});
    PackageInfo.setMockInitialValues(
      appName: 'counter',
      packageName: 'com.heuristq.dinein_counter',
      version: '1.0.0',
      buildNumber: '1',
      buildSignature: '',
    );
    scene = await _Scene.start();

    // One waiter session shared by the ordinary tests (sign-in is rate limited by the backend).
    final waiter = await scene.newStaff('WAITER', pin: '2468');
    storage = _MemoryStorage();
    app = ProviderContainer(overrides: [secureStorageProvider.overrideWithValue(storage)]);
    session = app.read(sessionManagerProvider);
    await session.login(waiter, pin: '2468');
    repo = app.read(counterRepositoryProvider);
    menu = await repo.menu();
  });

  tearDownAll(() {
    if (_live) app.dispose();
  });

  test('a waiter signs in with a PIN and the real refresh cookie is captured', () async {
    expect(session.user?.role, 'WAITER');
    expect(session.user?.displayName, 'E2E waiter');
    expect(session.user?.mustChangePassword, isFalse);
    expect(storage.refreshToken, isNotNull, reason: 'the dinein_rt cookie must be read from Set-Cookie');
  }, skip: !_live);

  test('a manager with a temporary password is told what to do, and nothing is kept', () async {
    final manager = await scene.newStaff('MANAGER');
    final s = ProviderContainer(overrides: [secureStorageProvider.overrideWithValue(_MemoryStorage())]);
    addTearDown(s.dispose);

    await expectLater(s.read(sessionManagerProvider).login(manager, password: 'Staff#2026xy'),
        throwsA(isA<PasswordChangeRequired>()));
    expect(s.read(sessionManagerProvider).hasSession, isFalse);
  }, skip: !_live);

  test('the refresh flow works against the real backend: cookie + CSRF, rotating, repeatable', () async {
    final before = storage.refreshToken;
    await session.forceRefresh();
    final afterFirst = storage.refreshToken;
    await session.forceRefresh();
    final afterSecond = storage.refreshToken;

    // (Access tokens can legitimately repeat: their timestamps are whole seconds, so two refreshes within one
    // second yield the same JWT. The refresh token is what rotates.)
    expect({before, afterFirst, afterSecond}, hasLength(3), reason: 'the refresh token rotates every time');
    // And the refreshed session really works.
    expect((await repo.config()).restaurantName, 'Spice Route Kitchen');
  }, skip: !_live);

  test('many requests at once share a single refresh (a rotated token must never be reused)', () async {
    await session.forceRefresh();
    final calls = await Future.wait([for (var i = 0; i < 5; i++) repo.config()]);
    expect(calls.map((c) => c.restaurantName).toSet(), {'Spice Route Kitchen'});
    // Five refreshes at once. If each used the same old cookie, the backend's reuse detection would end the whole
    // login on the second one; the session surviving proves they were collapsed into a single refresh.
    await Future.wait([for (var i = 0; i < 5; i++) session.forceRefresh()]);
    expect((await repo.config()).restaurantName, 'Spice Route Kitchen');
  }, skip: !_live);

  test('config, menu and tables load from the real waiter API', () async {
    final config = await repo.config();
    expect(config.restaurantName, 'Spice Route Kitchen');
    expect(config.takeawayEnabled, isTrue);
    expect(menu.categories.map((c) => c.name), containsAll(['Starters', 'Mains', 'Drinks']));
    final tables = await repo.tables();
    expect(tables.length, greaterThanOrEqualTo(3));
  }, skip: !_live);

  test('a kiosk order appears at the counter with its items, and paying it sends it to the kitchen', () async {
    final placed = await scene.kioskOrder(menu);

    final pending = await repo.pendingKioskOrders();
    final mine = pending.firstWhere((o) => o.id == placed.orderId, orElse: () => fail('not listed'));
    expect(mine.displayToken, placed.token);
    expect(mine.grandTotal, closeTo(placed.total, 0.001));
    expect(mine.status, 'PENDING_PAYMENT');
    expect(mine.orderType, OrderType.takeaway);
    expect(mine.items.single.quantity, 2);
    expect(mine.items.single.notes, 'no onion');

    // Before payment the kitchen does not see it.
    final kitchenBefore = await scene.dio.get('/api/v1/kitchen/orders', options: scene.owner);
    expect((kitchenBefore.data as List).any((o) => o['id'] == placed.orderId), isFalse);

    await repo.payKioskOrder(placed.orderId, PayMethod.upi);

    expect((await repo.pendingKioskOrders()).any((o) => o.id == placed.orderId), isFalse);
    final kitchenAfter = await scene.dio.get('/api/v1/kitchen/orders', options: scene.owner);
    final onBoard = (kitchenAfter.data as List).firstWhere((o) => o['id'] == placed.orderId, orElse: () => null);
    expect(onBoard, isNotNull, reason: 'a paid kiosk order must reach the kitchen board');
    expect(onBoard['status'], 'CONFIRMED');
  }, skip: !_live);

  test('paying the same kiosk order twice is refused with a code the app understands', () async {
    final placed = await scene.kioskOrder(menu);
    await repo.payKioskOrder(placed.orderId, PayMethod.cash);

    Object? error;
    try {
      await repo.payKioskOrder(placed.orderId, PayMethod.cash);
    } catch (e) {
      error = e;
    }
    expect(error, isNotNull);
    expect(apiCode(error!), anyOf('ALREADY_PAID', 'ORDER_NOT_PAYABLE'));
    expect(apiMessage(error), isNotEmpty);
  }, skip: !_live);

  test('a counter order is placed, paid and confirmed in one step, and its replay returns the same order',
      () async {
    final item = _plainItem(menu);
    final cart = (CartNotifier()
          ..setPricesIncludeGst(menu.pricesIncludeGst)
          ..add(item: item, quantity: 2))
        .state;

    final key = 'e2e-counter-${DateTime.now().microsecondsSinceEpoch}';
    final placed = await repo.placeOrder(
      cart: cart,
      type: OrderType.takeaway,
      method: PayMethod.cash,
      idempotencyKey: key,
    );
    expect(placed.status, 'CONFIRMED');
    expect(placed.displayToken, greaterThan(0));
    expect((placed.total - cart.total).abs(), lessThan(1.0),
        reason: 'client estimate ${cart.total} vs server ${placed.total}');

    final replay = await repo.placeOrder(
      cart: cart,
      type: OrderType.takeaway,
      method: PayMethod.cash,
      idempotencyKey: key,
    );
    expect(replay.orderId, placed.orderId);
  }, skip: !_live);

  test('dine-in needs a table; with one it is accepted', () async {
    final cart = (CartNotifier()..add(item: _plainItem(menu))).state;

    await expectLater(
      repo.placeOrder(
        cart: cart,
        type: OrderType.dineIn,
        method: PayMethod.card,
        idempotencyKey: 'e2e-dine-${DateTime.now().microsecondsSinceEpoch}',
      ),
      throwsA(isA<DioException>()),
    );

    final table = (await repo.tables()).first;
    final placed = await repo.placeOrder(
      cart: cart,
      type: OrderType.dineIn,
      method: PayMethod.card,
      idempotencyKey: 'e2e-dine2-${DateTime.now().microsecondsSinceEpoch}',
      tableId: table.id,
    );
    expect(placed.status, 'CONFIRMED');
  }, skip: !_live);

  test('an item that went off the menu is refused with a message the cashier can read', () async {
    final item = _plainItem(menu);
    final cart = (CartNotifier()..add(item: item)).state;
    final off = await scene.dio.patch('/api/v1/admin/items/${item.id}/availability',
        data: {'available': false}, options: scene.owner);
    expect(off.statusCode, 200);
    addTearDown(() => scene.dio
        .patch('/api/v1/admin/items/${item.id}/availability', data: {'available': true}, options: scene.owner));

    Object? error;
    try {
      await repo.placeOrder(
        cart: cart,
        type: OrderType.takeaway,
        method: PayMethod.cash,
        idempotencyKey: 'e2e-off-${DateTime.now().microsecondsSinceEpoch}',
      );
    } catch (e) {
      error = e;
    }
    expect(error, isNotNull);
    expect(apiCode(error!), 'ITEM_UNAVAILABLE');
    expect(apiMessage(error), contains('no longer available'));
  }, skip: !_live);

  test('re-using an old refresh token ends the session, as the backend treats it as theft', () async {
    final waiter = await scene.newStaff('WAITER', pin: '1357');
    final s = ProviderContainer(overrides: [secureStorageProvider.overrideWithValue(_MemoryStorage())]);
    addTearDown(s.dispose);
    final manager = s.read(sessionManagerProvider);
    final store = s.read(secureStorageProvider) as _MemoryStorage;
    await manager.login(waiter, pin: '1357');

    final stale = store.refreshToken!;
    await manager.forceRefresh(); // rotates: `stale` is now spent
    store.refreshToken = stale; // as if an old copy were replayed

    await expectLater(manager.forceRefresh(), throwsA(isA<SessionExpired>()));
    expect(store.refreshToken, isNull);
  }, skip: !_live);
}
