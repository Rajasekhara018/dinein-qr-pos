import 'dart:convert';

import 'package:dinein_counter/core/printing/bill.dart';
import 'package:dinein_counter/core/printing/escpos.dart';
import 'package:dinein_counter/features/orders/domain/kiosk_orders_logic.dart';
import 'package:dinein_counter/features/orders/domain/order_models.dart';
import 'package:flutter_test/flutter_test.dart';

KioskOrder order(int token, {String status = 'PENDING_PAYMENT', DateTime? placedAt}) => KioskOrder(
      id: token,
      orderNumber: '260930-$token',
      displayToken: token,
      orderType: OrderType.takeaway,
      status: status,
      grandTotal: 100,
      placedAt: placedAt ?? DateTime(2026, 9, 30, 12),
      items: const [],
    );

void main() {
  group('token search', () {
    final orders = [order(4), order(14), order(104), order(204), order(41)];
    List<int> tokens(String q) => filterByToken(orders, q).map((o) => o.displayToken).toList();

    test('empty search shows everything', () => expect(tokens(''), [4, 14, 104, 204, 41]));
    test('narrows by leading digits as the cashier types', () {
      expect(tokens('4'), [4, 41]);
      expect(tokens('10'), [104]);
      expect(tokens('104'), [104]);
    });
    test('ignores stray non-digits', () => expect(tokens(' #20 '), [204]));
    test('no match gives an empty list', () => expect(tokens('999'), isEmpty));
  });

  group('waiting time', () {
    final placed = DateTime(2026, 9, 30, 12);
    test('just now', () => expect(waitingFor(placed, placed.add(const Duration(seconds: 20))), 'just now'));
    test('minutes', () => expect(waitingFor(placed, placed.add(const Duration(minutes: 7))), '7 min'));
    test('hours', () {
      expect(waitingFor(placed, placed.add(const Duration(hours: 1))), '1 h');
      expect(waitingFor(placed, placed.add(const Duration(hours: 1, minutes: 10))), '1 h 10 min');
    });
  });

  group('kiosk order parsing', () {
    test('parses the backend view', () {
      final o = KioskOrder.fromJson({
        'id': 5,
        'orderNumber': '260930-005',
        'displayToken': 5,
        'orderType': 'DINE_IN',
        'status': 'EXPIRED',
        'grandTotal': 262.5,
        'placedAt': '2026-09-30T06:30:00Z',
        'items': [
          {'name': 'Burger', 'variantName': 'Large', 'quantity': 2, 'notes': 'no onion', 'addons': ['Cheese']},
        ],
      });
      expect(o.orderType, OrderType.dineIn);
      expect(o.waitedAWhile, isTrue);
      expect(o.items.single.details, 'Large  •  + Cheese  •  "no onion"');
    });

    test('a placed order converts paise to rupees', () {
      final p = PlacedOrder.fromJson({
        'orderId': 9,
        'orderNumber': 'x',
        'displayToken': 12,
        'status': 'CONFIRMED',
        'amountPaise': 78000,
      });
      expect(p.total, 780);
    });
  });

  group('bill slip', () {
    BillData bill({String name = 'Burger Hub', List<BillLine>? lines}) => BillData(
          restaurantName: name,
          orderNumber: '260930-012',
          tokenNumber: 12,
          orderTypeLabel: 'TAKEAWAY',
          total: 322.5,
          paymentLabel: 'UPI',
          printedAt: DateTime(2026, 9, 30, 14, 5),
          cashier: 'Asha',
          lines: lines ?? const [BillLine(name: 'Classic Veg Burger', quantity: 2, details: ['+ Extra cheese'])],
        );

    String text(List<int> b) => latin1.decode(b, allowInvalid: true);

    test('shows what was bought, the total, how it was paid and who served', () {
      final t = text(buildBillSlip(bill(), columns: 48));
      expect(t, contains('2 x Classic Veg Burger'));
      expect(t, contains('+ Extra cheese'));
      expect(t, contains('Rs.322.50'));
      expect(t, contains('Paid by'));
      expect(t, contains('UPI'));
      expect(t, contains('Order 260930-012'));
      expect(t, contains('Served by Asha'));
    });

    test('starts with a reset and ends with a cut', () {
      final b = buildBillSlip(bill(), columns: 48);
      expect(b.take(2), [0x1B, 0x40]);
      expect(b.sublist(b.length - 4), [0x1D, 0x56, 0x42, 0x00]);
    });

    test('non-ASCII names never produce raw multi-byte output', () {
      final b = buildBillSlip(bill(lines: const [BillLine(name: 'पनीर टिक्का', quantity: 1)]), columns: 32);
      expect(b.every((x) => x < 0x80), isTrue);
    });
  });
}
