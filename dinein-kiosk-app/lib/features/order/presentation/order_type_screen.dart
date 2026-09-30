import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../session/session_provider.dart';
import '../data/order_repository.dart';

class OrderTypeScreen extends ConsumerWidget {
  const OrderTypeScreen({super.key});

  void _choose(BuildContext context, WidgetRef ref, OrderType type) {
    ref.read(orderTypeProvider.notifier).state = type;
    context.go('/menu');
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(
        leading: BackButton(onPressed: () => context.go('/attract')),
        backgroundColor: Colors.transparent,
      ),
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('Where will you eat?',
                    style: Theme.of(context).textTheme.displaySmall),
                const SizedBox(height: 40),
                Wrap(
                  spacing: 24,
                  runSpacing: 24,
                  alignment: WrapAlignment.center,
                  children: [
                    _ChoiceCard(
                      icon: Icons.restaurant_rounded,
                      label: 'Dine in',
                      onTap: () => _choose(context, ref, OrderType.dineIn),
                    ),
                    _ChoiceCard(
                      icon: Icons.shopping_bag_rounded,
                      label: 'Takeaway',
                      onTap: () => _choose(context, ref, OrderType.takeaway),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ChoiceCard extends StatelessWidget {
  const _ChoiceCard({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.primary;
    return SizedBox(
      width: 300,
      height: 300,
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 96, color: color),
              const SizedBox(height: 16),
              Text(label,
                  style: Theme.of(context)
                      .textTheme
                      .headlineMedium
                      ?.copyWith(fontWeight: FontWeight.w800)),
            ],
          ),
        ),
      ),
    );
  }
}
