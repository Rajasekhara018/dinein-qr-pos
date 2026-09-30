import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../cart/providers/cart_provider.dart';
import '../order/data/order_repository.dart';

final orderTypeProvider = StateProvider<OrderType?>((ref) => null);

/// Clears everything a customer entered so nothing carries over to the next one.
final kioskSessionProvider = Provider<KioskSession>((ref) => KioskSession(ref));

class KioskSession {
  KioskSession(this._ref);

  final Ref _ref;

  void reset() {
    _ref.read(cartProvider.notifier).clear();
    _ref.read(orderTypeProvider.notifier).state = null;
  }
}
