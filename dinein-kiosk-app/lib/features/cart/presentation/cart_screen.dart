import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/branding/branding.dart';
import '../../../core/widgets/kiosk_widgets.dart';
import '../../order/data/order_repository.dart';
import '../../session/session_provider.dart';
import '../providers/cart_provider.dart';

class CartScreen extends ConsumerStatefulWidget {
  const CartScreen({super.key});

  @override
  ConsumerState<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends ConsumerState<CartScreen> {
  // Held for the whole checkout so a retry after a timeout cannot create a
  // second order. Replaced only after an order succeeds.
  String _idempotencyKey = newIdempotencyKey();
  bool _placing = false;
  String? _error;

  Future<void> _placeOrder() async {
    final cart = ref.read(cartProvider);
    final orderType = ref.read(orderTypeProvider);
    if (cart.isEmpty || orderType == null || _placing) return;

    final branding = ref.read(brandingProvider).valueOrNull ?? Branding.defaults;
    setState(() {
      _placing = true;
      _error = null;
    });
    try {
      final result = await ref.read(orderRepositoryProvider).placeOrder(
            cart: cart,
            orderType: orderType,
            paymentMode: branding.paymentModes.first,
            idempotencyKey: _idempotencyKey,
          );
      ref.read(kioskSessionProvider).reset();
      _idempotencyKey = newIdempotencyKey();
      if (mounted) context.go('/confirmation', extra: result);
    } catch (e) {
      if (mounted) {
        setState(() {
          _placing = false;
          _error = '$e'.replaceFirst('Exception: ', '');
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final cart = ref.watch(cartProvider);
    final orderType = ref.watch(orderTypeProvider);

    if (cart.isEmpty && !_placing) {
      return Scaffold(
        appBar: AppBar(
          leading: BackButton(onPressed: () => context.go('/menu')),
          title: const Text('Your order'),
        ),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Your order is empty',
                  style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => context.go('/menu'),
                child: const Text('Browse menu'),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        leading: BackButton(onPressed: _placing ? null : () => context.go('/menu')),
        title: Text(
            'Your order  •  ${orderType == OrderType.takeaway ? 'Takeaway' : 'Dine in'}'),
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.separated(
              padding: const EdgeInsets.all(20),
              itemCount: cart.lines.length,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (_, i) => _CartLineTile(line: cart.lines[i], enabled: !_placing),
            ),
          ),
          _Summary(cart: cart, placing: _placing, error: _error, onPlace: _placeOrder),
        ],
      ),
    );
  }
}

class _CartLineTile extends ConsumerWidget {
  const _CartLineTile({required this.line, required this.enabled});

  final CartLine line;
  final bool enabled;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final text = Theme.of(context).textTheme;
    final details = [
      if (line.variant != null) line.variant!.name,
      ...line.addons.map((a) => '+ ${a.name}'),
      if (line.note.isNotEmpty) '"${line.note}"',
    ].join('  •  ');
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            SizedBox(width: 88, height: 88, child: ItemImage(url: line.item.imageUrl)),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(line.item.name,
                      style: text.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
                  if (details.isNotEmpty) Text(details, style: text.bodyMedium),
                  const SizedBox(height: 4),
                  Text(formatPrice(line.lineTotal),
                      style: text.titleMedium?.copyWith(
                          color: Theme.of(context).colorScheme.primary,
                          fontWeight: FontWeight.w800)),
                ],
              ),
            ),
            IgnorePointer(
              ignoring: !enabled,
              child: QuantityStepper(
                quantity: line.quantity,
                min: 0,
                onChanged: (q) =>
                    ref.read(cartProvider.notifier).setQuantity(line.id, q),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Summary extends StatelessWidget {
  const _Summary({
    required this.cart,
    required this.placing,
    required this.error,
    required this.onPlace,
  });

  final CartState cart;
  final bool placing;
  final String? error;
  final VoidCallback onPlace;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    Widget row(String label, double value, {bool bold = false}) => Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(label,
                style: bold ? text.headlineSmall : text.titleMedium),
            Text(formatPriceExact(value),
                style: (bold ? text.headlineSmall : text.titleMedium)
                    ?.copyWith(fontWeight: bold ? FontWeight.w900 : null)),
          ],
        );
    return Material(
      elevation: 12,
      color: Colors.white,
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              row('Subtotal', cart.subtotal),
              const SizedBox(height: 4),
              row('GST', cart.gst),
              const Divider(height: 24),
              row('Total', cart.total, bold: true),
              if (error != null) ...[
                const SizedBox(height: 12),
                Text(error!,
                    style: text.titleMedium
                        ?.copyWith(color: Theme.of(context).colorScheme.error)),
              ],
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: placing ? null : onPlace,
                  child: placing
                      ? const SizedBox(
                          width: 28,
                          height: 28,
                          child: CircularProgressIndicator(strokeWidth: 3))
                      : Text(error == null
                          ? 'Place order  •  pay at counter'
                          : 'Try again'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
