import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/attract/presentation/attract_screen.dart';
import '../../features/cart/presentation/cart_screen.dart';
import '../../features/menu/presentation/menu_screen.dart';
import '../../features/order/data/order_repository.dart';
import '../../features/order/presentation/confirmation_screen.dart';
import '../../features/order/presentation/order_type_screen.dart';
import '../../features/pairing/presentation/pairing_screen.dart';
import '../../features/pairing/providers/device_provider.dart';

final rootNavigatorKey = GlobalKey<NavigatorState>();

/// Screens where the idle timer must not run: nobody is mid-order there.
const idleExemptRoutes = {'/splash', '/pair', '/attract'};

final appRouterProvider = Provider<GoRouter>((ref) {
  final refresh = ValueNotifier<int>(0);
  ref.listen(deviceProvider, (_, _) => refresh.value++);
  ref.onDispose(refresh.dispose);

  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: '/splash',
    refreshListenable: refresh,
    redirect: (context, state) {
      final device = ref.read(deviceProvider);
      final location = state.matchedLocation;
      if (device.loading) return location == '/splash' ? null : '/splash';
      if (!device.paired) return location == '/pair' ? null : '/pair';
      if (location == '/splash' || location == '/pair') return '/attract';
      return null;
    },
    routes: [
      GoRoute(
        path: '/splash',
        builder: (_, _) =>
            const Scaffold(body: Center(child: CircularProgressIndicator())),
      ),
      GoRoute(path: '/pair', builder: (_, _) => const PairingScreen()),
      GoRoute(path: '/attract', builder: (_, _) => const AttractScreen()),
      GoRoute(path: '/order-type', builder: (_, _) => const OrderTypeScreen()),
      GoRoute(path: '/menu', builder: (_, _) => const MenuScreen()),
      GoRoute(path: '/cart', builder: (_, _) => const CartScreen()),
      GoRoute(
        path: '/confirmation',
        redirect: (_, state) => state.extra is OrderResult ? null : '/attract',
        builder: (_, state) =>
            ConfirmationScreen(result: state.extra! as OrderResult),
      ),
    ],
  );
});
