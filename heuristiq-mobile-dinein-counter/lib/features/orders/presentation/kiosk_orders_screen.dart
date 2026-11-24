import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_provider.dart';
import '../../../core/network/api_client.dart';
import '../../../core/printing/bill.dart';
import '../../../core/printing/printer_service.dart';
import '../../../core/widgets/pos_widgets.dart';
import '../data/counter_repository.dart';
import '../domain/kiosk_orders_logic.dart';
import '../domain/order_models.dart';
import '../providers/order_providers.dart';

/// Customers who ordered on the kiosk come here to pay. The cashier types the token, checks the amount, and takes
/// the money; confirming it is what sends the order to the kitchen.
class KioskOrdersScreen extends ConsumerWidget {
  const KioskOrdersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final orders = ref.watch(kioskOrdersProvider);
    final query = ref.watch(tokenSearchProvider);

    return Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  autofocus: true,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
                  style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800),
                  decoration: const InputDecoration(
                    prefixIcon: Icon(Icons.pin_rounded),
                    labelText: 'Customer token',
                    border: OutlineInputBorder(),
                  ),
                  onChanged: (v) => ref.read(tokenSearchProvider.notifier).state = v,
                ),
              ),
              const SizedBox(width: 12),
              IconButton.filledTonal(
                tooltip: 'Refresh',
                onPressed: () => ref.invalidate(kioskOrdersProvider),
                icon: const Icon(Icons.refresh_rounded),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Expanded(
            child: orders.when(
              skipLoadingOnReload: true,
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => _ErrorState(
                message: apiMessage(e, fallback: 'Could not load kiosk orders'),
                onRetry: () => ref.invalidate(kioskOrdersProvider),
              ),
              data: (all) {
                final shown = filterByToken(all, query);
                if (all.isEmpty) return const _EmptyState(text: 'No kiosk orders are waiting for payment');
                if (shown.isEmpty) return _EmptyState(text: 'No waiting order with token "$query"');
                return GridView.builder(
                  gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                    maxCrossAxisExtent: 360,
                    mainAxisExtent: 250,
                    crossAxisSpacing: 16,
                    mainAxisSpacing: 16,
                  ),
                  itemCount: shown.length,
                  itemBuilder: (_, i) => _OrderCard(order: shown[i]),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _OrderCard extends ConsumerWidget {
  const _OrderCard({required this.order});

  final KioskOrder order;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final text = Theme.of(context).textTheme;
    final summary = order.items.map((i) => '${i.quantity} x ${i.name}').join(', ');
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => _collect(context, ref, order),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Text('${order.displayToken}',
                      style: text.displaySmall?.copyWith(fontWeight: FontWeight.w900)),
                  const Spacer(),
                  Chip(label: Text(order.orderType.label), visualDensity: VisualDensity.compact),
                ],
              ),
              Text(formatPriceExact(order.grandTotal),
                  style: text.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w800, color: Theme.of(context).colorScheme.primary)),
              const SizedBox(height: 6),
              Expanded(child: Text(summary, maxLines: 3, overflow: TextOverflow.ellipsis)),
              Row(
                children: [
                  Icon(Icons.schedule_rounded, size: 18, color: order.waitedAWhile ? Colors.orange : null),
                  const SizedBox(width: 4),
                  Expanded(
                    child: Text(
                      order.waitedAWhile
                          ? 'Waited a while  •  still payable'
                          : waitingFor(order.placedAt, DateTime.now()),
                      style: text.bodySmall,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Center(
        child: Text(text, style: Theme.of(context).textTheme.titleLarge, textAlign: TextAlign.center),
      );
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_rounded, size: 56),
            const SizedBox(height: 12),
            Text(message, style: Theme.of(context).textTheme.titleMedium, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            FilledButton(onPressed: onRetry, child: const Text('Try again')),
          ],
        ),
      );
}

Future<void> _collect(BuildContext context, WidgetRef ref, KioskOrder order) async {
  final paid = await showDialog<PayMethod>(
    context: context,
    barrierDismissible: false,
    builder: (_) => _PaymentDialog(order: order),
  );
  ref.invalidate(kioskOrdersProvider);
  if (paid == null || !context.mounted) return;

  final messenger = ScaffoldMessenger.of(context);
  final config = ref.read(counterConfigProvider).valueOrNull;
  final cashier = ref.read(sessionProvider).user?.displayName ?? '';
  final bill = BillData(
    restaurantName: config?.restaurantName ?? '',
    orderNumber: order.orderNumber,
    tokenNumber: order.displayToken,
    orderTypeLabel: order.orderType.label.toUpperCase(),
    total: order.grandTotal,
    paymentLabel: paid.label,
    printedAt: DateTime.now(),
    cashier: cashier,
    lines: [
      for (final i in order.items)
        BillLine(name: i.name, quantity: i.quantity, details: [
          if (i.variantName != null && i.variantName!.isNotEmpty) i.variantName!,
          ...i.addons.map((a) => '+ $a'),
          if (i.notes != null && i.notes!.isNotEmpty) '"${i.notes}"',
        ]),
    ],
  );
  final outcome = await ref.read(printCoordinatorProvider).printBill(bill);
  messenger.showSnackBar(SnackBar(
    content: Text('Token ${order.displayToken} paid by ${paid.label}. Sent to the kitchen.'
        '${outcome == PrintOutcome.failed ? ' The bill could not be printed.' : ''}'),
  ));
}

class _PaymentDialog extends ConsumerStatefulWidget {
  const _PaymentDialog({required this.order});

  final KioskOrder order;

  @override
  ConsumerState<_PaymentDialog> createState() => _PaymentDialogState();
}

class _PaymentDialogState extends ConsumerState<_PaymentDialog> {
  PayMethod? _method;
  bool _busy = false;
  String? _error;
  bool _finalError = false;

  Future<void> _confirm() async {
    final method = _method;
    if (method == null || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(counterRepositoryProvider).payKioskOrder(widget.order.id, method);
      if (mounted) Navigator.of(context).pop(method);
    } catch (e) {
      // ALREADY_PAID / ORDER_NOT_PAYABLE / PAYMENT_FLAGGED mean the order is not for this cashier to take now.
      final code = apiCode(e);
      if (mounted) {
        setState(() {
          _busy = false;
          _error = apiMessage(e, fallback: 'Could not record the payment');
          _finalError = code != null && code != 'INTERNAL_ERROR';
          _method = _finalError ? null : _method;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final order = widget.order;
    final text = Theme.of(context).textTheme;
    return AlertDialog(
      title: Row(
        children: [
          Text('Token ${order.displayToken}', style: const TextStyle(fontWeight: FontWeight.w900)),
          const Spacer(),
          Text(formatPriceExact(order.grandTotal),
              style: TextStyle(fontWeight: FontWeight.w800, color: Theme.of(context).colorScheme.primary)),
        ],
      ),
      content: SizedBox(
        width: 460,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Flexible(
              child: ListView(
                shrinkWrap: true,
                children: [
                  for (final i in order.items)
                    ListTile(
                      dense: true,
                      title: Text('${i.quantity} x ${i.name}'),
                      subtitle: i.details.isEmpty ? null : Text(i.details),
                    ),
                ],
              ),
            ),
            const Divider(),
            if (_method == null) ...[
              Text('How did the customer pay?', style: text.titleMedium),
              const SizedBox(height: 12),
              Row(
                children: [
                  for (final m in PayMethod.values)
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: FilledButton.tonal(
                          onPressed: _busy || _finalError ? null : () => setState(() => _method = m),
                          child: Text(m.label),
                        ),
                      ),
                    ),
                ],
              ),
            ] else
              Text(
                'Confirm you have received ${formatPriceExact(order.grandTotal)} by ${_method!.label}.',
                style: text.titleMedium,
              ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, style: text.bodyLarge?.copyWith(color: Theme.of(context).colorScheme.error)),
            ],
          ],
        ),
      ),
      actions: [
        if (_method != null && !_finalError)
          TextButton(
            onPressed: _busy ? null : () => setState(() {
              _method = null;
              _error = null;
            }),
            child: const Text('Back'),
          ),
        TextButton(
          onPressed: _busy ? null : () => Navigator.of(context).pop(),
          child: Text(_finalError ? 'Close' : 'Cancel'),
        ),
        if (_method != null && !_finalError)
          FilledButton(
            onPressed: _busy ? null : _confirm,
            child: _busy
                ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 3))
                : Text('Received ${_method!.label}'),
          ),
      ],
    );
  }
}
