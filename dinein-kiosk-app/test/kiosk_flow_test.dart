import 'package:dinein_kiosk/core/printing/printer_service.dart';
import 'package:dinein_kiosk/core/storage/secure_storage.dart';
import 'package:dinein_kiosk/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Keeps the device token in memory instead of the Android keystore.
class _MemoryStorage extends SecureStorageService {
  String? _token;
  String? _name;

  @override
  Future<String?> readDeviceToken() async => _token;

  @override
  Future<String?> readRestaurantName() async => _name;

  @override
  Future<void> saveDevice({required String token, required String restaurantName}) async {
    _token = token;
    _name = restaurantName;
  }

  @override
  Future<void> clearAll() async {
    _token = null;
    _name = null;
  }
}

class _RecordingPrinter implements PrinterTransport {
  _RecordingPrinter({this.fail = false});

  final bool fail;
  final sent = <List<int>>[];

  @override
  Future<void> send(List<int> bytes) async {
    if (fail) throw Exception('printer offline');
    sent.add(bytes);
  }

  String get text => sent.expand((b) => b).map((b) => b < 0x80 ? String.fromCharCode(b) : '?').join();
}

Future<void> _pumpFor(WidgetTester tester, {int ms = 600}) async {
  // pumpAndSettle would never return: the welcome button pulses forever.
  for (var i = 0; i < 4; i++) {
    await tester.pump(Duration(milliseconds: ms ~/ 4));
  }
}

Future<void> _launch(WidgetTester tester, {_RecordingPrinter? printer, bool paired = true}) async {
  SharedPreferences.setMockInitialValues({});
  tester.view.physicalSize = const Size(1080, 1920);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);

  final storage = _MemoryStorage();
  if (paired) await storage.saveDevice(token: 't', restaurantName: 'Demo');
  await tester.pumpWidget(ProviderScope(
    overrides: [
      secureStorageProvider.overrideWithValue(storage),
      if (printer != null) printerTransportProvider.overrideWithValue(printer),
    ],
    child: const KioskApp(),
  ));
  await _pumpFor(tester);
}

Future<void> _finish(WidgetTester tester) async {
  // Dispose the app so its idle and countdown timers are cancelled.
  await tester.pumpWidget(const SizedBox());
  await tester.pump(const Duration(seconds: 1));
}

/// Welcome -> dine in -> burger -> (drink prompt: no thanks) -> cart -> (dessert prompt: no thanks) -> place order.
Future<void> _orderABurger(WidgetTester tester) async {
  await tester.tap(find.text('Touch to order'));
  await _pumpFor(tester);
  await tester.tap(find.text('Dine in'));
  await _pumpFor(tester);

  await tester.tap(find.text('Classic Veg Burger'));
  await _pumpFor(tester);
  await tester.tap(find.textContaining('Add  •'));
  await _pumpFor(tester);

  // The drink prompt for burgers.
  expect(find.text('Add a drink?'), findsOneWidget);
  await tester.tap(find.text('No, thanks'));
  await _pumpFor(tester);

  await tester.tap(find.text('View order'));
  await _pumpFor(tester);
  await tester.tap(find.textContaining('Place order'));
  await _pumpFor(tester);

  // One last suggestion before the order is placed; declining returns to the cart.
  expect(find.text('Something sweet to finish?'), findsOneWidget);
  await tester.tap(find.text('No, thanks'));
  await _pumpFor(tester);

  await tester.tap(find.textContaining('Place order'));
  await _pumpFor(tester, ms: 1000);
}

void main() {
  testWidgets('an unpaired kiosk asks for a pairing code, and pairing opens the welcome page',
      (tester) async {
    await _launch(tester, paired: false);
    expect(find.text('Set up this kiosk'), findsOneWidget);

    await tester.enterText(find.byType(TextField), '123456');
    await tester.tap(find.text('Pair kiosk'));
    await _pumpFor(tester);

    expect(find.text('Touch to order'), findsOneWidget);
    await _finish(tester);
  });

  testWidgets('a customer can order end to end, sees prompts, gets a token, and the slip prints',
      (tester) async {
    final printer = _RecordingPrinter();
    await _launch(tester, printer: printer);
    await _orderABurger(tester);

    expect(find.text('Order placed!'), findsOneWidget);
    expect(find.text('Your token'), findsOneWidget);
    expect(find.text('101'), findsOneWidget);
    expect(find.textContaining('at the counter'), findsWidgets);

    expect(printer.sent, hasLength(1));
    expect(printer.text, contains('101'));
    expect(printer.text, contains('1 x Classic Veg Burger'));
    expect(find.textContaining('could not be printed'), findsNothing);
    await _finish(tester);
  });

  testWidgets('a printer failure never blocks the order and tells the customer to remember the token',
      (tester) async {
    await _launch(tester, printer: _RecordingPrinter(fail: true));
    await _orderABurger(tester);

    expect(find.text('Order placed!'), findsOneWidget);
    expect(find.textContaining('could not be printed'), findsOneWidget);
    await _finish(tester);
  });

  testWidgets('no printer set up: the order still completes without any print warning', (tester) async {
    await _launch(tester);
    await _orderABurger(tester);

    expect(find.text('Order placed!'), findsOneWidget);
    expect(find.textContaining('could not be printed'), findsNothing);
    await _finish(tester);
  });

  testWidgets('the customer can switch the app to Hindi', (tester) async {
    await _launch(tester);
    await tester.tap(find.text('हिंदी'));
    await _pumpFor(tester);
    await tester.tap(find.text('Touch to order'));
    await _pumpFor(tester);

    expect(find.text('आप कहाँ खाएँगे?'), findsOneWidget);
    expect(find.text('यहीं खाएँगे'), findsOneWidget);
    await _finish(tester);
  });

  testWidgets('large text mode enlarges text, and the next customer starts back at normal size',
      (tester) async {
    await _launch(tester);
    double scaleNow() =>
        MediaQuery.of(tester.element(find.byType(Scaffold).first)).textScaler.scale(10);

    expect(scaleNow(), closeTo(10, 0.01));
    await tester.tap(find.text('Large text'));
    await _pumpFor(tester);
    expect(scaleNow(), closeTo(13, 0.01));

    // Going through the flow and returning to the welcome page resets it.
    await tester.tap(find.text('Touch to order'));
    await _pumpFor(tester);
    expect(scaleNow(), closeTo(13, 0.01));
    await tester.tap(find.byType(BackButton));
    await _pumpFor(tester);
    expect(scaleNow(), closeTo(10, 0.01));
    await _finish(tester);
  });

  testWidgets('staff can unlock the service menu with their PIN and print a test slip', (tester) async {
    final printer = _RecordingPrinter();
    await _launch(tester, printer: printer);

    await tester.longPressAt(const Offset(20, 20));
    await _pumpFor(tester);
    expect(find.text('Staff access'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextField, 'Username'), 'asha');
    await tester.enterText(find.widgetWithText(TextField, 'PIN'), '9999');
    await tester.tap(find.text('Unlock'));
    await _pumpFor(tester);
    expect(find.text('Wrong username or PIN'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextField, 'PIN'), '1234');
    await tester.tap(find.text('Unlock'));
    await _pumpFor(tester);
    expect(find.textContaining('Service menu'), findsOneWidget);

    await tester.tap(find.text('Print test slip'));
    await _pumpFor(tester);
    expect(find.text('Sent to the printer.'), findsOneWidget);
    expect(printer.text, contains('PRINTER TEST'));

    await tester.tap(find.text('Reprint last receipt'));
    await _pumpFor(tester);
    expect(find.text('There is no receipt to reprint yet.'), findsOneWidget);
    await _finish(tester);
  });

  testWidgets('the staff corner does nothing on a tap, so customers cannot stumble into it', (tester) async {
    await _launch(tester);
    await tester.tapAt(const Offset(20, 20));
    await _pumpFor(tester);
    expect(find.text('Staff access'), findsNothing);
    await _finish(tester);
  });

  testWidgets('unpairing from the service menu sends the kiosk back to the pairing screen', (tester) async {
    await _launch(tester);
    await tester.longPressAt(const Offset(20, 20));
    await _pumpFor(tester);
    await tester.enterText(find.widgetWithText(TextField, 'Username'), 'asha');
    await tester.enterText(find.widgetWithText(TextField, 'PIN'), '1234');
    await tester.tap(find.text('Unlock'));
    await _pumpFor(tester);

    await tester.tap(find.text('Unpair this kiosk'));
    await _pumpFor(tester);
    await tester.tap(find.text('Unpair'));
    await _pumpFor(tester);

    expect(find.text('Set up this kiosk'), findsOneWidget);
    await _finish(tester);
  });
}
