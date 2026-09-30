import 'package:equatable/equatable.dart';

enum OrderType {
  dineIn('DINE_IN', 'Dine in'),
  takeaway('TAKEAWAY', 'Takeaway');

  const OrderType(this.wire, this.label);

  final String wire;
  final String label;

  static OrderType fromWire(String? value) =>
      value == 'TAKEAWAY' ? OrderType.takeaway : OrderType.dineIn;
}

/// How the customer paid at the counter. [wire] is the backend's name for it.
enum PayMethod {
  cash('CASH', 'Cash'),
  upi('UPI_AT_COUNTER', 'UPI'),
  card('CARD_AT_COUNTER', 'Card');

  const PayMethod(this.wire, this.label);

  final String wire;
  final String label;
}

class CounterOrderLine extends Equatable {
  const CounterOrderLine({
    required this.name,
    required this.quantity,
    this.variantName,
    this.notes,
    this.addons = const [],
  });

  final String name;
  final String? variantName;
  final int quantity;
  final String? notes;
  final List<String> addons;

  factory CounterOrderLine.fromJson(Map<String, dynamic> json) => CounterOrderLine(
        name: json['name'] as String? ?? '',
        variantName: json['variantName'] as String?,
        quantity: (json['quantity'] as num?)?.toInt() ?? 1,
        notes: json['notes'] as String?,
        addons: [for (final a in json['addons'] as List? ?? const []) '$a'],
      );

  /// Size, extras and note, on one line.
  String get details => [
        if (variantName != null && variantName!.isNotEmpty) variantName!,
        ...addons.map((a) => '+ $a'),
        if (notes != null && notes!.isNotEmpty) '"$notes"',
      ].join('  •  ');

  @override
  List<Object?> get props => [name, variantName, quantity, notes, addons];
}

/// A kiosk order still waiting for the customer to pay at the counter.
class KioskOrder extends Equatable {
  const KioskOrder({
    required this.id,
    required this.orderNumber,
    required this.displayToken,
    required this.orderType,
    required this.status,
    required this.grandTotal,
    required this.placedAt,
    required this.items,
  });

  final int id;
  final String orderNumber;
  final int displayToken;
  final OrderType orderType;

  /// PENDING_PAYMENT, or EXPIRED (waited past the payment timeout but still payable).
  final String status;
  final double grandTotal;
  final DateTime placedAt;
  final List<CounterOrderLine> items;

  bool get waitedAWhile => status == 'EXPIRED';

  factory KioskOrder.fromJson(Map<String, dynamic> json) => KioskOrder(
        id: (json['id'] as num).toInt(),
        orderNumber: json['orderNumber'] as String? ?? '',
        displayToken: (json['displayToken'] as num).toInt(),
        orderType: OrderType.fromWire(json['orderType'] as String?),
        status: json['status'] as String? ?? 'PENDING_PAYMENT',
        grandTotal: (json['grandTotal'] as num).toDouble(),
        placedAt: DateTime.tryParse(json['placedAt'] as String? ?? '') ?? DateTime.now(),
        items: [
          for (final i in json['items'] as List? ?? const [])
            CounterOrderLine.fromJson(Map<String, dynamic>.from(i as Map)),
        ],
      );

  @override
  List<Object?> get props => [id, orderNumber, displayToken, orderType, status, grandTotal, placedAt, items];
}

/// Restaurant settings the counter needs.
class CounterConfig extends Equatable {
  const CounterConfig({
    required this.restaurantName,
    required this.takeawayEnabled,
    required this.acceptingOrders,
  });

  final String restaurantName;
  final bool takeawayEnabled;
  final bool acceptingOrders;

  factory CounterConfig.fromJson(Map<String, dynamic> json) => CounterConfig(
        restaurantName: json['restaurantName'] as String? ?? '',
        takeawayEnabled: json['takeawayEnabled'] as bool? ?? true,
        acceptingOrders: json['acceptingOrders'] as bool? ?? true,
      );

  @override
  List<Object?> get props => [restaurantName, takeawayEnabled, acceptingOrders];
}

class DiningTable extends Equatable {
  const DiningTable({required this.id, required this.label});

  final int id;
  final String label;

  factory DiningTable.fromJson(Map<String, dynamic> json) =>
      DiningTable(id: (json['id'] as num).toInt(), label: json['label'] as String? ?? '');

  @override
  List<Object?> get props => [id, label];
}

/// The server's answer for an order placed and paid at the counter. Totals here are what was charged.
class PlacedOrder extends Equatable {
  const PlacedOrder({
    required this.orderId,
    required this.orderNumber,
    required this.displayToken,
    required this.status,
    required this.total,
  });

  final int orderId;
  final String orderNumber;
  final int displayToken;
  final String status;
  final double total;

  factory PlacedOrder.fromJson(Map<String, dynamic> json) => PlacedOrder(
        orderId: (json['orderId'] as num).toInt(),
        orderNumber: json['orderNumber'] as String? ?? '',
        displayToken: (json['displayToken'] as num).toInt(),
        status: json['status'] as String? ?? '',
        total: ((json['amountPaise'] as num?)?.toDouble() ?? 0) / 100,
      );

  @override
  List<Object?> get props => [orderId, orderNumber, displayToken, status, total];
}
