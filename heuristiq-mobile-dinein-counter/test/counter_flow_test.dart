import 'package:dinein_counter/core/auth/session.dart';
import 'package:dinein_counter/core/network/api_client.dart';
import 'package:dinein_counter/core/printing/printer_service.dart';
import 'package:dinein_counter/core/storage/secure_storage.dart';
import 'package:dinein_counter/features/cart/providers/cart_provider.dart';
import 'package:dinein_counter/features/menu/domain/menu_models.dart';
import 'package:dinein_counter/features/orders/data/counter_repository.dart';
import 'package:dinein_counter/features/orders/domain/order_models.dart';
import 'package:dinein_counter/main.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

// ----- fakes --------------------------------------------------------------------------------------

class _NoStorage extends SecureStorageService {
  @override
  Future<String?> readRefreshToken() async => null;
  @override
  Future<void> saveRefreshToken(String token) async {}
  @override
  Future<void> clear() async {}
}

const _asha = StaffUser(
  id: 7,
  username: 'asha',
  displayName: 'Asha',
  role: 'WAITER',
  restaurantId: 1,
);

class _FakeManager extends SessionManager {
  _FakeManager({this.signedIn = true}) : super(Dio(), _NoStorage());

  bool signedIn;
  Object? loginError;
  final logins = <String>[];

  @override
  Future<StaffUser?> restore() async => signedIn ? _asha : null;

  @override
  Future<StaffUser> login(String username, {String? password, String? pin}) async {
    if (loginError != null) throw loginError!;
    logins.add('$username/${pin ?? password}');
    signedIn = true;
    return _asha;
  }

  @override
  Future<void> logout() async => signedIn = false;

  @override
  StaffUser? get user => signedIn ? _asha : null;
}

class _PlaceCall {
  _PlaceCall(this.key, this.type, this.method, this.tableId, this.itemIds);

  final String key;
  final OrderType type;
  final PayMethod method;
  final int? tableId;
  final List<int> itemIds;
}

DioException _apiError(int status, String code, String message) {
  final options = RequestOptions(path: '/x');
  return DioException(
    requestOptions: options,
    type: DioExceptionType.badResponse,
    response: Response(
      requestOptions: options,
      statusCode: status,
      data: {'code': code, 'message': message},
    ),
  );
}

class _FakeRepo implements CounterRepository {
  _FakeRepo({List<KioskOrder>? pending}) : pending = pending ?? [];

  final List<KioskOrder> pending;
  final paid = <(int, PayMethod)>[];
  final placeCalls = <_PlaceCall>[];
  Object? payError;
  int failPlacements = 0;
  int _token = 200;

  @override
  Future<List<KioskOrder>> pendingKioskOrders() async => List.of(pending);

  @override
  Future<void> payKioskOrder(int orderId, PayMethod method) async {
    if (payError != null) throw payError!;
    paid.add((orderId, method));
    pending.removeWhere((o) => o.id == orderId);
  }

  @override
  Future<CounterConfig> config() async =>
      const CounterConfig(restaurantName: 'Burger Hub', takeawayEnabled: true, acceptingOrders: true);

  @override
  Future<MenuData> menu() async => MenuData.fromJson(_menu);

  @override
  Future<List<DiningTable>> tables() async => const [DiningTable(id: 1, label: 'T1'), DiningTable(id: 2, label: 'T2')];

  @override
  Future<PlacedOrder> placeOrder({
    required CartState cart,
    required OrderType type,
    required PayMethod method,
    required String idempotencyKey,
    int? tableId,
    String? note,
  }) async {
    placeCalls.add(_PlaceCall(idempotencyKey, type, method, tableId, [for (final l in cart.lines) l.item.id]));
    if (failPlacements > 0) {
      failPlacements--;
      throw Exception('Network down');
    }
    _token++;
    return PlacedOrder(
      orderId: _token,
      orderNumber: '260930-$_token',
      displayToken: _token,
      status: 'CONFIRMED',
      total: cart.total,
    );
  }
}

class _RecordingPrinter implements PrinterTransport {
  final sent = <List<int>>[];

  @override
  Future<void> send(List<int> bytes) async => sent.add(bytes);

  String get text => sent.expand((b) => b).map((b) => b < 0x80 ? String.fromCharCode(b) : '?').join();
}

const _menu = <String, dynamic>{
  'version': 'test',
  'pricesIncludeGst': true,
  'categories': [
    {
      'id': 1,
      'name': 'Burgers',
      'items': [
        {
          'id': 101,
          'name': 'Classic Veg Burger',
          'foodType': 'VEG',
          'displayPrice': 129,
          'gstPercent': 5,
          'available': true,
          'variants': [],
          'addons': [],
        },
        {
          'id': 103,
          'name': 'Sold Out Burger',
          'foodType': 'VEG',
          'displayPrice': 149,
          'gstPercent': 5,
          'available': false,
          'variants': [],
          'addons': [],
        },
      ],
    },
    {
      'id': 2,
      'name': 'Sides',
      'items': [
        {
          'id': 201,
          'name': 'Loaded Fries',
          'foodType': 'VEG',
          'displayPrice': 99,
          'gstPercent': 5,
          'available': true,
          'variants': [
            {'id': 1, 'name': 'Regular', 'price': 99, 'isDefault': true},
            {'id': 2, 'name': 'Large', 'price': 139, 'isDefault': false},
          ],
          'addons': [],
        },
      ],
    },
  ],
};

KioskOrder _kioskOrder(int token, {double total = 250, String status = 'PENDING_PAYMENT'}) => KioskOrder(
      id: token,
      orderNumber: '260930-$token',
      displayToken: token,
      orderType: OrderType.takeaway,
      status: status,
      grandTotal: total,
      placedAt: DateTime.now().subtract(const Duration(minutes: 3)),
      items: const [
        CounterOrderLine(name: 'Classic Veg Burger', quantity: 2, addons: ['Extra cheese'], notes: 'no onion'),
      ],
    );

// ----- harness ------------------------------------------------------------------------------------

Future<void> _pump(WidgetTester tester, {int ms = 400}) async {
  for (var i = 0; i < 4; i++) {
    await tester.pump(Duration(milliseconds: ms ~/ 4));
  }
}

Future<void> _launch(
  WidgetTester tester, {
  required _FakeRepo repo,
  _FakeManager? manager,
  _RecordingPrinter? printer,
}) async {
  SharedPreferences.setMockInitialValues({});
  tester.view.physicalSize = const Size(1280, 800);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);

  await tester.pumpWidget(ProviderScope(
    overrides: [
      sessionManagerProvider.overrideWithValue(manager ?? _FakeManager()),
      counterRepositoryProvider.overrideWithValue(repo),
      if (printer != null) printerTransportProvider.overrideWithValue(printer),
    ],
    child: const CounterApp(),
  ));
  await _pump(tester);
}

Future<void> _finish(WidgetTester tester) async {
  // Dispose the app so the order list's refresh timer is cancelled.
  await tester.pumpWidget(const SizedBox());
  await tester.pump(const Duration(seconds: 1));
}

Finder _inDialog(Finder f) => find.descendant(of: find.byType(AlertDialog), matching: f);

void main() {
  group('signing in', () {
    testWidgets('a signed-out tablet shows the login, and a PIN sign-in opens the counter', (tester) async {
      final manager = _FakeManager(signedIn: false);
      await _launch(tester, repo: _FakeRepo(), manager: manager);
      expect(find.text('Counter POS'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextField, 'Username'), 'asha');
      await tester.enterText(find.widgetWithText(TextField, 'PIN'), '1234');
      await tester.tap(find.text('Sign in'));
      await _pump(tester);

      expect(manager.logins, ['asha/1234']);
      expect(find.text('Kiosk orders'), findsWidgets);
      await _finish(tester);
    });

    testWidgets('a wrong PIN shows the server message and stays on the login', (tester) async {
      final manager = _FakeManager(signedIn: false)..loginError = Exception('Invalid username or password');
      await _launch(tester, repo: _FakeRepo(), manager: manager);

      await tester.enterText(find.widgetWithText(TextField, 'Username'), 'asha');
      await tester.enterText(find.widgetWithText(TextField, 'PIN'), '0000');
      await tester.tap(find.text('Sign in'));
      await _pump(tester);

      expect(find.text('Invalid username or password'), findsOneWidget);
      expect(find.text('Counter POS'), findsOneWidget);
      await _finish(tester);
    });

    testWidgets('a tablet with a saved login goes straight to the counter', (tester) async {
      await _launch(tester, repo: _FakeRepo());
      expect(find.text('Customer token'), findsOneWidget);
      await _finish(tester);
    });
  });

  group('kiosk orders', () {
    testWidgets('lists waiting orders and narrows them by token', (tester) async {
      await _launch(tester, repo: _FakeRepo(pending: [_kioskOrder(104), _kioskOrder(204), _kioskOrder(41)]));
      expect(find.text('104'), findsOneWidget);
      expect(find.text('204'), findsOneWidget);
      expect(find.text('41'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextField, 'Customer token'), '10');
      await _pump(tester);
      expect(find.text('104'), findsOneWidget);
      expect(find.text('204'), findsNothing);
      expect(find.text('41'), findsNothing);
      await _finish(tester);
    });

    testWidgets('shows a friendly message when nothing is waiting', (tester) async {
      await _launch(tester, repo: _FakeRepo());
      expect(find.text('No kiosk orders are waiting for payment'), findsOneWidget);
      await _finish(tester);
    });

    testWidgets('takes payment: asks how, asks to confirm the amount, records it, prints the bill',
        (tester) async {
      final repo = _FakeRepo(pending: [_kioskOrder(104, total: 250)]);
      final printer = _RecordingPrinter();
      await _launch(tester, repo: repo, printer: printer);

      await tester.tap(find.text('104'));
      await _pump(tester);
      expect(find.text('Token 104'), findsOneWidget);
      expect(find.text('How did the customer pay?'), findsOneWidget);

      await tester.tap(_inDialog(find.text('UPI')));
      await _pump(tester);
      expect(find.textContaining('Confirm you have received ₹250.00 by UPI'), findsOneWidget);
      expect(repo.paid, isEmpty, reason: 'nothing is recorded until the cashier confirms');

      await tester.tap(find.text('Received UPI'));
      await _pump(tester, ms: 800);

      expect(repo.paid, [(104, PayMethod.upi)]);
      expect(find.textContaining('Token 104 paid by UPI'), findsOneWidget);
      expect(printer.text, contains('Paid by'));
      expect(printer.text, contains('UPI'));
      expect(printer.text, contains('2 x Classic Veg Burger'));
      // The paid order leaves the list.
      expect(find.text('No kiosk orders are waiting for payment'), findsOneWidget);
      await _finish(tester);
    });

    testWidgets('the cashier can go back and pick another method before confirming', (tester) async {
      final repo = _FakeRepo(pending: [_kioskOrder(104)]);
      await _launch(tester, repo: repo);
      await tester.tap(find.text('104'));
      await _pump(tester);
      await tester.tap(_inDialog(find.text('Card')));
      await _pump(tester);
      await tester.tap(find.text('Back'));
      await _pump(tester);
      expect(find.text('How did the customer pay?'), findsOneWidget);
      expect(repo.paid, isEmpty);
      await _finish(tester);
    });

    testWidgets('an order someone else already settled shows the reason and cannot be taken again',
        (tester) async {
      final repo = _FakeRepo(pending: [_kioskOrder(104)])
        ..payError = _apiError(409, 'ALREADY_PAID', 'This order has already been paid');
      await _launch(tester, repo: repo);

      await tester.tap(find.text('104'));
      await _pump(tester);
      await tester.tap(_inDialog(find.text('Cash')));
      await _pump(tester);
      await tester.tap(find.text('Received Cash'));
      await _pump(tester);

      expect(find.text('This order has already been paid'), findsOneWidget);
      expect(find.text('Received Cash'), findsNothing);
      expect(find.text('Close'), findsOneWidget);
      await _finish(tester);
    });

    testWidgets('a network failure while paying can be retried', (tester) async {
      final repo = _FakeRepo(pending: [_kioskOrder(104)])..payError = Exception('Network down');
      await _launch(tester, repo: repo);

      await tester.tap(find.text('104'));
      await _pump(tester);
      await tester.tap(_inDialog(find.text('Cash')));
      await _pump(tester);
      await tester.tap(find.text('Received Cash'));
      await _pump(tester);
      expect(find.text('Network down'), findsOneWidget);
      expect(find.text('Received Cash'), findsOneWidget, reason: 'still able to retry');

      repo.payError = null;
      await tester.tap(find.text('Received Cash'));
      await _pump(tester, ms: 800);
      expect(repo.paid, [(104, PayMethod.cash)]);
      await _finish(tester);
    });

    testWidgets('an order that waited past the timeout is flagged but still payable', (tester) async {
      await _launch(tester, repo: _FakeRepo(pending: [_kioskOrder(104, status: 'EXPIRED')]));
      expect(find.textContaining('Waited a while'), findsOneWidget);
      await _finish(tester);
    });
  });

  group('new counter order', () {
    Future<void> openNewOrder(WidgetTester tester) async {
      await tester.tap(find.text('New order'));
      await _pump(tester);
    }

    testWidgets('one tap adds a plain item, and paying places a takeaway order and prints the bill',
        (tester) async {
      final repo = _FakeRepo();
      final printer = _RecordingPrinter();
      await _launch(tester, repo: repo, printer: printer);
      await openNewOrder(tester);

      await tester.tap(find.text('Classic Veg Burger'));
      await _pump(tester);
      expect(find.text('Tap items to add them'), findsNothing);

      await tester.tap(find.widgetWithText(FilledButton, 'Cash'));
      await _pump(tester);
      expect(find.textContaining('Take ₹129.00 by Cash?'), findsOneWidget);
      await tester.tap(find.text('Received Cash'));
      await _pump(tester, ms: 800);

      expect(repo.placeCalls, hasLength(1));
      expect(repo.placeCalls.single.type, OrderType.takeaway);
      expect(repo.placeCalls.single.method, PayMethod.cash);
      expect(repo.placeCalls.single.tableId, isNull);
      expect(repo.placeCalls.single.itemIds, [101]);
      expect(find.text('Token 201'), findsOneWidget);
      expect(printer.text, contains('Order 260930-201'));
      expect(printer.text, contains('Served by Asha'));

      await tester.tap(_inDialog(find.text('New order')));
      await _pump(tester);
      expect(find.text('Tap items to add them'), findsOneWidget, reason: 'the cart is cleared for the next customer');
      await _finish(tester);
    });

    testWidgets('an item with sizes opens the options first', (tester) async {
      final repo = _FakeRepo();
      await _launch(tester, repo: repo);
      await openNewOrder(tester);

      await tester.tap(find.text('Sides'));
      await _pump(tester);
      await tester.tap(find.text('Loaded Fries'));
      await _pump(tester);
      await tester.tap(find.text('Large'));
      await _pump(tester);
      await tester.tap(find.textContaining('Add  •'));
      await _pump(tester);

      expect(find.textContaining('Large'), findsWidgets);
      await tester.tap(find.widgetWithText(FilledButton, 'Card'));
      await _pump(tester);
      expect(find.textContaining('Take ₹139.00 by Card?'), findsOneWidget);
      await _finish(tester);
    });

    testWidgets('a sold-out item cannot be added', (tester) async {
      await _launch(tester, repo: _FakeRepo());
      await openNewOrder(tester);
      await tester.tap(find.text('Sold Out Burger'));
      await _pump(tester);
      expect(find.text('Tap items to add them'), findsOneWidget);
      await _finish(tester);
    });

    testWidgets('dine-in needs a table before it can be paid', (tester) async {
      final repo = _FakeRepo();
      await _launch(tester, repo: repo);
      await openNewOrder(tester);
      await tester.tap(find.text('Classic Veg Burger'));
      await _pump(tester);
      await tester.tap(find.text('Dine in'));
      await _pump(tester);

      await tester.tap(find.widgetWithText(FilledButton, 'Cash'));
      await _pump(tester);
      expect(find.text('Choose a table for a dine-in order.'), findsOneWidget);
      expect(repo.placeCalls, isEmpty);

      await tester.tap(find.text('Choose table'));
      await _pump(tester);
      await tester.tap(find.text('T2'));
      await _pump(tester);
      expect(find.text('Table T2'), findsOneWidget);

      await tester.tap(find.widgetWithText(FilledButton, 'Cash'));
      await _pump(tester);
      await tester.tap(find.text('Received Cash'));
      await _pump(tester, ms: 800);
      expect(repo.placeCalls.single.type, OrderType.dineIn);
      expect(repo.placeCalls.single.tableId, 2);
      await _finish(tester);
    });

    testWidgets('cancelling the amount check places nothing', (tester) async {
      final repo = _FakeRepo();
      await _launch(tester, repo: repo);
      await openNewOrder(tester);
      await tester.tap(find.text('Classic Veg Burger'));
      await _pump(tester);
      await tester.tap(find.widgetWithText(FilledButton, 'Cash'));
      await _pump(tester);
      await tester.tap(find.text('Cancel'));
      await _pump(tester);
      expect(repo.placeCalls, isEmpty);
      await _finish(tester);
    });

    testWidgets(
        'a retry of the same order reuses the idempotency key, an edited order gets a new one',
        (tester) async {
      final repo = _FakeRepo()..failPlacements = 1;
      await _launch(tester, repo: repo);
      await openNewOrder(tester);
      await tester.tap(find.text('Classic Veg Burger').first);
      await _pump(tester);

      Future<void> payCash() async {
        await tester.tap(find.widgetWithText(FilledButton, 'Cash'));
        await _pump(tester);
        await tester.tap(find.text('Received Cash'));
        await _pump(tester, ms: 800);
      }

      await payCash(); // the network drops: the request may or may not have reached the server
      expect(find.text('Network down'), findsOneWidget);
      await payCash(); // same order, retried
      expect(repo.placeCalls, hasLength(2));
      expect(repo.placeCalls[0].key, repo.placeCalls[1].key,
          reason: 'a retry must be recognised by the server as the same order');
      await tester.tap(_inDialog(find.text('New order')));
      await _pump(tester);

      // A brand-new order gets its own key.
      await tester.tap(find.text('Classic Veg Burger').first);
      await _pump(tester);
      await payCash();
      expect(repo.placeCalls, hasLength(3));
      expect(repo.placeCalls[2].key, isNot(repo.placeCalls[1].key));
      await tester.tap(_inDialog(find.text('New order')));
      await _pump(tester);

      // Editing the order after a failed attempt also gets a new key.
      repo.failPlacements = 1;
      await tester.tap(find.text('Classic Veg Burger').first);
      await _pump(tester);
      await payCash();
      final failedKey = repo.placeCalls[3].key;
      await tester.tap(find.text('Classic Veg Burger').first); // customer adds another before the retry
      await _pump(tester);
      await payCash();
      expect(repo.placeCalls[4].key, isNot(failedKey),
          reason: 'if the failed attempt did reach the server, replaying its key would return the old items');
      await _finish(tester);
    });
  });

  testWidgets('signing out from settings returns to the login', (tester) async {
    final manager = _FakeManager();
    await _launch(tester, repo: _FakeRepo(), manager: manager);
    await tester.tap(find.text('Settings'));
    await _pump(tester);
    await tester.tap(find.text('Sign out'));
    await _pump(tester);
    expect(find.text('Sign in'), findsOneWidget);
    expect(manager.signedIn, isFalse);
    await _finish(tester);
  });

  testWidgets('settings can print a test slip and reprint the last bill', (tester) async {
    final printer = _RecordingPrinter();
    await _launch(tester, repo: _FakeRepo(), printer: printer);
    await tester.tap(find.text('Settings'));
    await _pump(tester);

    await tester.tap(find.text('Reprint last bill'));
    await _pump(tester);
    expect(find.text('There is no bill to reprint yet.'), findsOneWidget);

    await tester.tap(find.text('Print test slip'));
    await _pump(tester);
    expect(find.text('Sent to the printer.'), findsOneWidget);
    expect(printer.text, contains('PRINTER TEST'));
    await _finish(tester);
  });
}
