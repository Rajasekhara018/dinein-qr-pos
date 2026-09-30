// Runs the kiosk's real repositories and providers against a running backend. Skipped unless asked for:
//
//   flutter test test/live/live_e2e_test.dart \
//     --dart-define=LIVE_E2E=true --dart-define=KIOSK_DEMO=false \
//     --dart-define=KIOSK_API_BASE=http://localhost:8080
//
// The backend must run with the dev profile (sample restaurant "Spice Route Kitchen"). Uses a throwaway
// database: it creates a waiter, a kiosk and an upsell rule, and places orders.
import 'package:dinein_kiosk/core/branding/branding.dart';
import 'package:dinein_kiosk/core/config/app_config.dart';
import 'package:dinein_kiosk/core/storage/secure_storage.dart';
import 'package:dinein_kiosk/features/cart/providers/cart_provider.dart';
import 'package:dinein_kiosk/features/menu/data/menu_repository.dart';
import 'package:dinein_kiosk/features/menu/domain/menu_models.dart';
import 'package:dinein_kiosk/features/order/data/order_repository.dart';
import 'package:dinein_kiosk/features/pairing/providers/device_provider.dart';
import 'package:dinein_kiosk/features/staff/data/staff_repository.dart';
import 'package:dinein_kiosk/features/upsell/data/upsell_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _live = bool.fromEnvironment('LIVE_E2E');

class _MemoryStorage extends SecureStorageService {
  String? token;
  String? name;

  @override
  Future<String?> readDeviceToken() async => token;

  @override
  Future<String?> readRestaurantName() async => name;

  @override
  Future<void> saveDevice({required String token, required String restaurantName}) async {
    this.token = token;
    name = restaurantName;
  }

  @override
  Future<void> clearAll() async {
    token = null;
    name = null;
  }
}

/// The owner's admin API, used only to set the scene (create a kiosk, a waiter, upsell rules).
class _Admin {
  _Admin(this.dio, this.auth);

  final Dio dio;
  final Options auth;

  static const _bootstrapPassword = 'ChangeMe@123';
  static const _ownerPassword = 'E2eOwner#2026x';

  static Future<_Admin> signIn() async {
    final dio = Dio(BaseOptions(baseUrl: AppConfig.apiBase, validateStatus: (_) => true));
    var response = await dio.post('/api/v1/auth/login', data: {'username': 'owner', 'password': _bootstrapPassword});
    if (response.statusCode == 200) {
      // First run: the bootstrap password is temporary and must be replaced before anything else works.
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
    return _Admin(dio, Options(headers: {'Authorization': 'Bearer ${response.data['accessToken']}'}));
  }

  Future<Map<String, dynamic>> post(String path, Object body) async {
    final r = await dio.post('/api/v1/admin$path', data: body, options: auth);
    expect(r.statusCode, anyOf(200, 201), reason: 'POST $path -> ${r.statusCode} ${r.data}');
    return Map<String, dynamic>.from(r.data as Map);
  }

  Future<Response<dynamic>> put(String path, Object body) =>
      dio.put('/api/v1/admin$path', data: body, options: auth);

  Future<String> newWaiter(String pin) async {
    final username = 'e2e-w-${DateTime.now().microsecondsSinceEpoch}';
    await post('/staff', {
      'username': username,
      'displayName': 'E2E Waiter',
      'role': 'WAITER',
      'password': 'Waiter#2026x',
      'pin': pin,
    });
    return username;
  }

  Future<String> newKioskPairingCode() async {
    final device = await post('/kiosk-devices', {'name': 'E2E kiosk ${DateTime.now().millisecondsSinceEpoch}'});
    return device['pairingCode'] as String;
  }
}

void main() {
  late _Admin admin;
  late ProviderContainer app; // a kiosk that is already paired; most tests just use it
  late _MemoryStorage storage;

  ProviderContainer newApp(_MemoryStorage s) =>
      ProviderContainer(overrides: [secureStorageProvider.overrideWithValue(s)]);

  setUpAll(() async {
    if (!_live) return;
    SharedPreferences.setMockInitialValues({});
    PackageInfo.setMockInitialValues(
      appName: 'kiosk',
      packageName: 'com.heuristq.dinein_kiosk',
      version: '1.0.0',
      buildNumber: '1',
      buildSignature: '',
    );
    admin = await _Admin.signIn();

    // Pairing is rate limited to 5 attempts a minute per address (by design), so pair once and share it.
    storage = _MemoryStorage();
    app = newApp(storage);
    final code = await admin.newKioskPairingCode();
    expect(await app.read(deviceProvider.notifier).pair(code), isTrue,
        reason: 'pairing failed: ${app.read(deviceProvider).error}');
  });

  tearDownAll(() {
    if (_live) app.dispose();
  });

  Future<void> pairKiosk() async {} // the shared kiosk is already paired

  test('a wrong pairing code is rejected with a readable message', () async {
    final other = newApp(_MemoryStorage());
    addTearDown(other.dispose);
    await other.read(deviceProvider.notifier).pair('000000');
    final state = other.read(deviceProvider);
    expect(state.paired, isFalse);
    expect(state.error, isNotNull);
  }, skip: !_live);

  test('a real pairing code pairs the kiosk, and the code cannot be used twice', () async {
    final code = await admin.newKioskPairingCode();
    final firstStorage = _MemoryStorage();
    final first = newApp(firstStorage);
    addTearDown(first.dispose);
    expect(await first.read(deviceProvider.notifier).pair(code), isTrue);
    expect(first.read(deviceProvider).paired, isTrue);
    expect(firstStorage.token, startsWith('kdt_'));
    expect(first.read(deviceProvider).restaurantName, 'Spice Route Kitchen');

    final second = newApp(_MemoryStorage());
    addTearDown(second.dispose);
    expect(await second.read(deviceProvider.notifier).pair(code), isFalse);
  }, skip: !_live);

  test('menu: the real payload parses, and a repeat fetch is answered by the ETag (304)', () async {
    await pairKiosk();
    final repo = app.read(menuRepositoryProvider);

    final first = await repo.fetchMenu();
    expect(first.stale, isFalse);
    expect(first.categories.map((c) => c.name), containsAll(['Starters', 'Mains', 'Drinks']));
    final items = [for (final c in first.categories) ...c.items];
    expect(items.length, greaterThanOrEqualTo(8));
    expect(items.every((i) => i.displayPrice > 0), isTrue);
    expect(items.any((i) => i.variants.isNotEmpty), isTrue, reason: 'the seed has sized items');
    expect(items.any((i) => i.addons.isNotEmpty), isTrue, reason: 'the seed has extras');

    final second = await repo.fetchMenu();
    expect(second.version, first.version);
    expect(second.stale, isFalse);
  }, skip: !_live);

  test('branding: defaults come back with the restaurant name and only pay-at-counter', () async {
    await pairKiosk();
    final branding = await app.read(brandingProvider.future);
    expect(branding.restaurantName, 'Spice Route Kitchen');
    expect(branding.paymentModes, [PaymentMode.payAtCounter]);
  }, skip: !_live);

  test('branding: an owner change reaches the kiosk (colours, texts, timeout)', () async {
    await pairKiosk();
    final saved = await admin.put('/kiosk-branding', {
      'kioskEnabled': true,
      'primaryColor': '#1C7ED6',
      'secondaryColor': '#212529',
      'headline': 'Fresh off the tandoor',
      'subtext': 'Order here, pay at the counter',
      'startButtonLabel': 'Start',
      'idleTimeoutSeconds': 45,
    });
    expect(saved.statusCode, 200, reason: '${saved.data}');

    final branding = await app.read(brandingProvider.future);
    expect(branding.headline, 'Fresh off the tandoor');
    expect(branding.startButtonLabel, 'Start');
    expect(branding.idleTimeoutSeconds, 45);
    expect(branding.primary.toARGB32(), 0xFF1C7ED6);
  }, skip: !_live);

  test('upsell rules created by the owner reach the kiosk', () async {
    await pairKiosk();
    final menu = await app.read(menuRepositoryProvider).fetchMenu();
    final drink = menu.categories.firstWhere((c) => c.name == 'Drinks').items.first;
    final rule = await admin.post('/kiosk-upsells', {
      'suggestedItemId': drink.id,
      'placement': 'CHECKOUT',
      'message': 'Something to drink?',
    });

    final rules = await app.read(upsellRepositoryProvider).fetchRules();
    final mine = rules.firstWhere((r) => r.id == (rule['id'] as num).toInt());
    expect(mine.suggestedItemId, drink.id);
    expect(mine.message, 'Something to drink?');
  }, skip: !_live);

  group('ordering', () {
    late MenuData menu;

    setUp(() async {
      await pairKiosk();
      await admin.put('/kiosk-branding', {'kioskEnabled': true});
      menu = await app.read(menuRepositoryProvider).fetchMenu();
    });

    // A cart of one plain item and one item with a default size, the way the customer builds it.
    CartState cartOfTwo() {
      final all = [for (final c in menu.categories) ...c.items].where((i) => i.available).toList();
      final plain = all.firstWhere((i) => i.variants.isEmpty);
      final sized = all.firstWhere((i) => i.variants.isNotEmpty);
      final cart = CartNotifier()..setPricesIncludeGst(menu.pricesIncludeGst);
      cart.add(item: plain, quantity: 2, note: 'no onion');
      cart.add(item: sized, variant: sized.defaultVariant);
      return cart.state;
    }

    test('places a real pay-at-counter order: token, server total, and a replay returns the same order',
        () async {
      final cart = cartOfTwo();
      final key = newIdempotencyKey();
      final orders = app.read(orderRepositoryProvider);

      final first = await orders.placeOrder(
        cart: cart,
        orderType: OrderType.takeaway,
        paymentMode: PaymentMode.payAtCounter,
        idempotencyKey: key,
      );
      expect(first.tokenNumber, greaterThan(0));
      expect(first.total, greaterThan(0));
      // The app's on-screen estimate must agree with what the server will actually charge.
      expect((first.total - cart.total).abs(), lessThan(1.0),
          reason: 'client estimate ${cart.total} vs server ${first.total}');

      final replay = await orders.placeOrder(
        cart: cart,
        orderType: OrderType.takeaway,
        paymentMode: PaymentMode.payAtCounter,
        idempotencyKey: key,
      );
      expect(replay.orderId, first.orderId);
      expect(replay.tokenNumber, first.tokenNumber);
    }, skip: !_live);

    test('dine-in works without a table on the kiosk', () async {
      final result = await app.read(orderRepositoryProvider).placeOrder(
            cart: cartOfTwo(),
            orderType: OrderType.dineIn,
            paymentMode: PaymentMode.payAtCounter,
            idempotencyKey: newIdempotencyKey(),
          );
      expect(result.tokenNumber, greaterThan(0));
    }, skip: !_live);

    test('an unavailable item is refused with the backend message', () async {
      final item = [for (final c in menu.categories) ...c.items].firstWhere((i) => i.variants.isEmpty);
      final cart = (CartNotifier()..add(item: item)).state;
      // Take the item off the menu the way an owner does, then try to order it with a stale menu.
      final r = await admin.dio.patch(
        '/api/v1/admin/items/${item.id}/availability',
        data: {'available': false},
        options: admin.auth,
      );
      expect(r.statusCode, 200, reason: '${r.data}');
      addTearDown(() => admin.dio.patch('/api/v1/admin/items/${item.id}/availability',
          data: {'available': true}, options: admin.auth));

      await expectLater(
        app.read(orderRepositoryProvider).placeOrder(
              cart: cart,
              orderType: OrderType.takeaway,
              paymentMode: PaymentMode.payAtCounter,
              idempotencyKey: newIdempotencyKey(),
            ),
        throwsA(predicate((e) => '$e'.contains('no longer available'))),
      );
    }, skip: !_live);

    test('the owner\'s kill switch stops orders with a message the customer can read', () async {
      await admin.put('/kiosk-branding', {'kioskEnabled': false});
      addTearDown(() => admin.put('/kiosk-branding', {'kioskEnabled': true}));

      await expectLater(
        app.read(orderRepositoryProvider).placeOrder(
              cart: cartOfTwo(),
              orderType: OrderType.takeaway,
              paymentMode: PaymentMode.payAtCounter,
              idempotencyKey: newIdempotencyKey(),
            ),
        throwsA(predicate((e) => '$e'.contains('switched off'))),
      );
    }, skip: !_live);
  });

  group('staff unlock', () {
    test('a waiter unlocks with their PIN; a wrong PIN is refused without unpairing the kiosk', () async {
      await pairKiosk();
      final waiter = await admin.newWaiter('2468');
      final staff = app.read(staffRepositoryProvider);

      final identity = await staff.unlock(waiter, '2468');
      expect(identity.role, 'WAITER');
      expect(identity.displayName, 'E2E Waiter');

      await expectLater(staff.unlock(waiter, '9999'),
          throwsA(predicate((e) => '$e'.contains('Wrong username or PIN'))));

      // The wrong PIN must not have cleared the kiosk's pairing (a 401 would).
      expect(storage.token, isNotNull);
      final menu = await app.read(menuRepositoryProvider).fetchMenu();
      expect(menu.categories, isNotEmpty);
    }, skip: !_live);
  });
}
