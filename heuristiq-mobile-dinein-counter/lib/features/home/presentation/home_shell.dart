import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_provider.dart';
import '../../orders/presentation/kiosk_orders_screen.dart';
import '../../orders/presentation/new_order_screen.dart';
import '../../orders/providers/order_providers.dart';
import '../../settings/presentation/settings_screen.dart';

/// The counter's main frame: what to do (take a kiosk payment, take a new order) down the left, signed-in cashier below.
class HomeShell extends ConsumerStatefulWidget {
  const HomeShell({super.key});

  @override
  ConsumerState<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends ConsumerState<HomeShell> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(sessionProvider).user;
    // Load the restaurant's name once so the printed bill can carry it.
    ref.watch(counterConfigProvider);
    final kioskCount = ref.watch(kioskOrdersProvider).valueOrNull?.length ?? 0;

    final pages = <Widget>[
      const KioskOrdersScreen(),
      const NewOrderScreen(),
      const SettingsScreen(),
    ];

    return Scaffold(
      body: SafeArea(
        child: Row(
          children: [
            NavigationRail(
              selectedIndex: _index,
              labelType: NavigationRailLabelType.all,
              onDestinationSelected: (i) => setState(() => _index = i),
              leading: Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Icon(Icons.point_of_sale_rounded,
                    size: 36, color: Theme.of(context).colorScheme.primary),
              ),
              destinations: [
                NavigationRailDestination(
                  icon: Badge(
                    isLabelVisible: kioskCount > 0,
                    label: Text('$kioskCount'),
                    child: const Icon(Icons.qr_code_2_rounded),
                  ),
                  label: const Text('Kiosk orders'),
                ),
                const NavigationRailDestination(icon: Icon(Icons.add_shopping_cart_rounded), label: Text('New order')),
                const NavigationRailDestination(icon: Icon(Icons.settings_rounded), label: Text('Settings')),
              ],
              trailing: Expanded(
                child: Align(
                  alignment: Alignment.bottomCenter,
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: Text(user?.displayName ?? '',
                        style: Theme.of(context).textTheme.bodySmall, textAlign: TextAlign.center),
                  ),
                ),
              ),
            ),
            const VerticalDivider(width: 1),
            Expanded(child: pages[_index]),
          ],
        ),
      ),
    );
  }
}
