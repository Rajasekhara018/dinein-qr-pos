import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../cart/providers/cart_provider.dart';
import '../../menu/domain/menu_models.dart';
import '../domain/order_models.dart';

/// Everything the counter asks the backend. Prices, GST and totals always come back from the server;
/// the app only sends ids and quantities.
abstract class CounterRepository {
  Future<List<KioskOrder>> pendingKioskOrders();

  Future<void> payKioskOrder(int orderId, PayMethod method);

  Future<CounterConfig> config();

  Future<MenuData> menu();

  Future<List<DiningTable>> tables();

  /// Places an order and takes the payment in one step. [idempotencyKey] makes a retry return the same order.
  Future<PlacedOrder> placeOrder({
    required CartState cart,
    required OrderType type,
    required PayMethod method,
    required String idempotencyKey,
    int? tableId,
    String? note,
  });
}

final counterRepositoryProvider = Provider<CounterRepository>((ref) {
  return ApiCounterRepository(ref.watch(apiDioProvider));
});

class ApiCounterRepository implements CounterRepository {
  ApiCounterRepository(this._dio);

  final Dio _dio;

  @override
  Future<List<KioskOrder>> pendingKioskOrders() async {
    final response = await _dio.get('/api/v1/waiter/kiosk-orders');
    return [
      for (final o in response.data as List) KioskOrder.fromJson(Map<String, dynamic>.from(o as Map)),
    ];
  }

  @override
  Future<void> payKioskOrder(int orderId, PayMethod method) async {
    await _dio.post('/api/v1/waiter/kiosk-orders/$orderId/pay', data: {'method': method.wire});
  }

  @override
  Future<CounterConfig> config() async {
    final response = await _dio.get('/api/v1/waiter/config');
    return CounterConfig.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  @override
  Future<MenuData> menu() async {
    final response = await _dio.get('/api/v1/waiter/menu');
    return MenuData.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  @override
  Future<List<DiningTable>> tables() async {
    final response = await _dio.get('/api/v1/waiter/tables');
    return [
      for (final t in response.data as List) DiningTable.fromJson(Map<String, dynamic>.from(t as Map)),
    ];
  }

  @override
  Future<PlacedOrder> placeOrder({
    required CartState cart,
    required OrderType type,
    required PayMethod method,
    required String idempotencyKey,
    int? tableId,
    String? note,
  }) async {
    final response = await _dio.post('/api/v1/waiter/orders', data: {
      'orderType': type.wire,
      'tableId': tableId,
      'paymentMethod': method.wire,
      'idempotencyKey': idempotencyKey,
      if (note != null && note.trim().isNotEmpty) 'note': note.trim(),
      'items': [
        for (final l in cart.lines)
          {
            'itemId': l.item.id,
            'variantId': l.variant?.id,
            'addonIds': l.addons.map((a) => a.id).toList(),
            'quantity': l.quantity,
            'notes': l.note,
          },
      ],
    });
    return PlacedOrder.fromJson(Map<String, dynamic>.from(response.data as Map));
  }
}
