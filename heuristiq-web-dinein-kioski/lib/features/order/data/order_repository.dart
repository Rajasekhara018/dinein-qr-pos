import 'dart:math';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/branding/branding.dart';
import '../../../core/config/app_config.dart';
import '../../../core/network/dio_client.dart';
import '../../cart/providers/cart_provider.dart';

enum OrderType { dineIn, takeaway }

class OrderResult {
  const OrderResult({
    required this.orderId,
    required this.tokenNumber,
    required this.total,
  });

  final String orderId;
  final int tokenNumber;
  final double total;
}

abstract class OrderRepository {
  Future<OrderResult> placeOrder({
    required CartState cart,
    required OrderType orderType,
    required PaymentMode paymentMode,
    required String idempotencyKey,
  });
}

final orderRepositoryProvider = Provider<OrderRepository>((ref) {
  if (AppConfig.demoMode) return DemoOrderRepository();
  return ApiOrderRepository(ref.watch(dioProvider));
});

/// One key per checkout attempt. A retry after a timeout reuses it, so the
/// backend can return the original order instead of creating a duplicate.
String newIdempotencyKey() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  return bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
}

class ApiOrderRepository implements OrderRepository {
  ApiOrderRepository(this._dio);

  final Dio _dio;

  @override
  Future<OrderResult> placeOrder({
    required CartState cart,
    required OrderType orderType,
    required PaymentMode paymentMode,
    required String idempotencyKey,
  }) async {
    try {
      final response = await _dio.post(
        '/api/v1/kiosk/orders',
        options: Options(headers: {'Idempotency-Key': idempotencyKey}),
        data: {
          'orderType': orderType == OrderType.dineIn ? 'DINE_IN' : 'TAKEAWAY',
          'paymentMode':
              paymentMode == PaymentMode.upi ? 'UPI' : 'PAY_AT_COUNTER',
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
        },
      );
      final data = Map<String, dynamic>.from(response.data as Map);
      return OrderResult(
        orderId: '${data['orderId']}',
        tokenNumber: (data['tokenNumber'] as num).toInt(),
        total: (data['total'] as num?)?.toDouble() ?? cart.total,
      );
    } on DioException catch (e) {
      throwApiError(e, fallback: 'Could not place your order');
    }
  }
}

class DemoOrderRepository implements OrderRepository {
  static int _token = 100;

  @override
  Future<OrderResult> placeOrder({
    required CartState cart,
    required OrderType orderType,
    required PaymentMode paymentMode,
    required String idempotencyKey,
  }) async {
    await Future<void>.delayed(const Duration(milliseconds: 600));
    _token++;
    return OrderResult(
      orderId: 'demo-$_token',
      tokenNumber: _token,
      total: cart.total,
    );
  }
}
