import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../menu/domain/menu_models.dart';
import '../data/counter_repository.dart';
import '../domain/order_models.dart';

/// Unpaid kiosk orders, refreshed every 10 seconds while the screen is open.
final kioskOrdersProvider = FutureProvider.autoDispose<List<KioskOrder>>((ref) {
  final timer = Timer(const Duration(seconds: 10), ref.invalidateSelf);
  ref.onDispose(timer.cancel);
  return ref.watch(counterRepositoryProvider).pendingKioskOrders();
});

/// What the cashier has typed into the token search box.
final tokenSearchProvider = StateProvider.autoDispose<String>((ref) => '');

final counterConfigProvider = FutureProvider<CounterConfig>((ref) {
  return ref.watch(counterRepositoryProvider).config();
});

final counterMenuProvider = FutureProvider<MenuData>((ref) {
  return ref.watch(counterRepositoryProvider).menu();
});

final tablesProvider = FutureProvider.autoDispose<List<DiningTable>>((ref) {
  return ref.watch(counterRepositoryProvider).tables();
});

final orderTypeProvider = StateProvider<OrderType>((ref) => OrderType.takeaway);

final selectedTableProvider = StateProvider<DiningTable?>((ref) => null);

final orderNoteProvider = StateProvider<String>((ref) => '');
