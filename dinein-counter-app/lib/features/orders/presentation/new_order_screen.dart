import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_provider.dart';
import '../../../core/network/api_client.dart';
import '../../../core/printing/bill.dart';
import '../../../core/printing/printer_service.dart';
import '../../../core/util/idempotency.dart';
import '../../../core/widgets/pos_widgets.dart';
import '../../cart/providers/cart_provider.dart';
import '../../menu/domain/menu_models.dart';
import '../data/counter_repository.dart';
import '../domain/order_models.dart';
import '../providers/order_providers.dart';
import 'item_sheet.dart';

/// Takes an order at the counter and the payment in one go.
class NewOrderScreen extends ConsumerStatefulWidget {
  const NewOrderScreen({super.key});

  @override
  ConsumerState<NewOrderScreen> createState() => _NewOrderScreenState();
}

class _NewOrderScreenState extends ConsumerState<NewOrderScreen> {
  int _category = 0;
  String _search = '';
  bool _placing = false;

  // Held until an order succeeds, so a retry after a timeout cannot charge or create the order twice.
  String _idempotencyKey = newIdempotencyKey();

  @override
  Widget build(BuildContext context) {
    final menu = ref.watch(counterMenuProvider);
    ref.listen(counterMenuProvider, (_, next) {
      final data = next.valueOrNull;
      if (data != null) ref.read(cartProvider.notifier).setPricesIncludeGst(data.pricesIncludeGst);
    });
    // A retry of the *same* order must reuse its key, but an edited order is a different order. If the first
    // attempt actually reached the server and only its reply was lost, reusing the key would replay the old one.
    ref.listen(cartProvider, (prev, next) {
      if (prev?.lines != next.lines) _idempotencyKey = newIdempotencyKey();
    });

    return Row(
      children: [
        Expanded(
          flex: 3,
          child: menu.when(
            skipLoadingOnReload: true,
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(apiMessage(e, fallback: 'Could not load the menu')),
                  const SizedBox(height: 12),
                  FilledButton(
                      onPressed: () => ref.invalidate(counterMenuProvider), child: const Text('Try again')),
                ],
              ),
            ),
            data: _menuPane,
          ),
        ),
        const VerticalDivider(width: 1),
        SizedBox(width: 400, child: _CartPane(placing: _placing, onPay: _pay)),
      ],
    );
  }

  Widget _menuPane(MenuData data) {
    if (data.categories.isEmpty) return const Center(child: Text('The menu is empty'));
    final index = _category.clamp(0, data.categories.length - 1);
    final query = _search.trim().toLowerCase();
    final items = query.isEmpty
        ? data.categories[index].items
        : [
            for (final c in data.categories)
              ...c.items.where((i) => i.name.toLowerCase().contains(query)),
          ];

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: TextField(
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search_rounded),
              labelText: 'Search items',
              border: OutlineInputBorder(),
              isDense: true,
            ),
            onChanged: (v) => setState(() => _search = v),
          ),
        ),
        if (query.isEmpty)
          SizedBox(
            height: 56,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              itemCount: data.categories.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (_, i) => ChoiceChip(
                label: Text(data.categories[i].name, style: const TextStyle(fontSize: 16)),
                selected: i == index,
                onSelected: (_) => setState(() => _category = i),
              ),
            ),
          ),
        Expanded(
          child: GridView.builder(
            padding: const EdgeInsets.all(16),
            gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
              maxCrossAxisExtent: 220,
              mainAxisExtent: 110,
              crossAxisSpacing: 12,
              mainAxisSpacing: 12,
            ),
            itemCount: items.length,
            itemBuilder: (_, i) => _ItemTile(item: items[i]),
          ),
        ),
      ],
    );
  }

  // ----- payment -------------------------------------------------------------------------------

  Future<void> _pay(PayMethod method) async {
    final cart = ref.read(cartProvider);
    final type = ref.read(orderTypeProvider);
    final table = ref.read(selectedTableProvider);
    if (cart.isEmpty || _placing) return;
    if (type == OrderType.dineIn && table == null) {
      _say('Choose a table for a dine-in order.');
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Take ${formatPriceExact(cart.total)} by ${method.label}?'),
        content: const Text('The final amount is calculated by the server and shown once the order is placed.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.of(context).pop(true), child: Text('Received ${method.label}')),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() => _placing = true);
    try {
      final placed = await ref.read(counterRepositoryProvider).placeOrder(
            cart: cart,
            type: type,
            method: method,
            idempotencyKey: _idempotencyKey,
            tableId: type == OrderType.dineIn ? table?.id : null,
            note: ref.read(orderNoteProvider),
          );
      final bill = _billFor(placed, cart, type, method);
      final outcome = await ref.read(printCoordinatorProvider).printBill(bill);

      _idempotencyKey = newIdempotencyKey();
      ref.read(cartProvider.notifier).clear();
      ref.read(selectedTableProvider.notifier).state = null;
      ref.read(orderNoteProvider.notifier).state = '';
      if (mounted) await _showSuccess(placed, method, outcome);
    } catch (e) {
      final code = apiCode(e);
      if (code == 'ITEM_UNAVAILABLE') ref.invalidate(counterMenuProvider);
      _say(apiMessage(e, fallback: 'Could not place the order'));
    } finally {
      if (mounted) setState(() => _placing = false);
    }
  }

  BillData _billFor(PlacedOrder placed, CartState cart, OrderType type, PayMethod method) {
    return BillData(
      restaurantName: ref.read(counterConfigProvider).valueOrNull?.restaurantName ?? '',
      orderNumber: placed.orderNumber,
      tokenNumber: placed.displayToken,
      orderTypeLabel: type.label.toUpperCase(),
      total: placed.total,
      paymentLabel: method.label,
      printedAt: DateTime.now(),
      cashier: ref.read(sessionProvider).user?.displayName ?? '',
      lines: [
        for (final l in cart.lines)
          BillLine(name: l.item.name, quantity: l.quantity, details: [
            if (l.variant != null) l.variant!.name,
            ...l.addons.map((a) => '+ ${a.name}'),
            if (l.note.isNotEmpty) '"${l.note}"',
          ]),
      ],
    );
  }

  Future<void> _showSuccess(PlacedOrder placed, PayMethod method, PrintOutcome outcome) {
    return showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.check_circle_rounded, size: 56, color: Color(0xFF2B8A3E)),
        title: Text('Token ${placed.displayToken}'),
        content: Text(
          '${formatPriceExact(placed.total)} received by ${method.label}. Sent to the kitchen.'
          '${outcome == PrintOutcome.failed ? '\n\nThe bill could not be printed. Tell the customer their token.' : ''}',
          textAlign: TextAlign.center,
        ),
        actions: [FilledButton(onPressed: () => Navigator.of(context).pop(), child: const Text('New order'))],
      ),
    );
  }

  void _say(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }
}

class _ItemTile extends ConsumerWidget {
  const _ItemTile({required this.item});

  final MenuItem item;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final soldOut = !item.available;
    return Opacity(
      opacity: soldOut ? 0.45 : 1,
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: soldOut
              ? null
              : () {
                  // One tap for a plain item; options open a sheet.
                  if (item.isCustomisable) {
                    showItemSheet(context, item);
                  } else {
                    ref.read(cartProvider.notifier).add(item: item);
                  }
                },
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    FoodTypeMarker(type: item.foodType),
                    const SizedBox(width: 6),
                    if (soldOut) const Text('Sold out', style: TextStyle(fontWeight: FontWeight.w700)),
                  ],
                ),
                const SizedBox(height: 4),
                Expanded(
                  child: Text(item.name,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                ),
                Text(formatPrice(item.displayPrice),
                    style: TextStyle(
                        fontSize: 16, fontWeight: FontWeight.w800, color: Theme.of(context).colorScheme.primary)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CartPane extends ConsumerWidget {
  const _CartPane({required this.placing, required this.onPay});

  final bool placing;
  final ValueChanged<PayMethod> onPay;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cart = ref.watch(cartProvider);
    final type = ref.watch(orderTypeProvider);
    final table = ref.watch(selectedTableProvider);
    final takeawayEnabled = ref.watch(counterConfigProvider).valueOrNull?.takeawayEnabled ?? true;
    final text = Theme.of(context).textTheme;

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              SegmentedButton<OrderType>(
                segments: [
                  ButtonSegment(value: OrderType.takeaway, label: const Text('Takeaway'), enabled: takeawayEnabled),
                  const ButtonSegment(value: OrderType.dineIn, label: Text('Dine in')),
                ],
                selected: {type},
                onSelectionChanged: (s) => ref.read(orderTypeProvider.notifier).state = s.first,
              ),
              if (type == OrderType.dineIn) ...[
                const SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: () => _pickTable(context, ref),
                    icon: const Icon(Icons.table_restaurant_rounded),
                    label: Text(table == null ? 'Choose table' : 'Table ${table.label}'),
                  ),
                ),
              ],
            ],
          ),
        ),
        const Divider(height: 1),
        Expanded(
          child: cart.isEmpty
              ? Center(child: Text('Tap items to add them', style: text.titleMedium))
              : ListView.separated(
                  padding: const EdgeInsets.all(12),
                  itemCount: cart.lines.length,
                  separatorBuilder: (_, _) => const Divider(height: 1),
                  itemBuilder: (_, i) => _CartRow(line: cart.lines[i]),
                ),
        ),
        const Divider(height: 1),
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              TextField(
                decoration: const InputDecoration(
                    labelText: 'Order note (optional)', isDense: true, border: OutlineInputBorder()),
                maxLength: 120,
                onChanged: (v) => ref.read(orderNoteProvider.notifier).state = v,
              ),
              _TotalRow('Subtotal', cart.subtotal),
              _TotalRow('GST', cart.gst),
              _TotalRow('Total', cart.total, bold: true),
              const SizedBox(height: 12),
              Row(
                children: [
                  for (final m in PayMethod.values)
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 3),
                        child: FilledButton(
                          onPressed: cart.isEmpty || placing ? null : () => onPay(m),
                          child: placing
                              ? const SizedBox(
                                  width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                              : Text(m.label),
                        ),
                      ),
                    ),
                ],
              ),
              TextButton(
                onPressed: cart.isEmpty || placing ? null : () => ref.read(cartProvider.notifier).clear(),
                child: const Text('Clear order'),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _pickTable(BuildContext context, WidgetRef ref) async {
    final chosen = await showDialog<DiningTable>(
      context: context,
      builder: (_) => const _TablePicker(),
    );
    if (chosen != null) ref.read(selectedTableProvider.notifier).state = chosen;
  }
}

class _TotalRow extends StatelessWidget {
  const _TotalRow(this.label, this.value, {this.bold = false});

  final String label;
  final double value;
  final bool bold;

  @override
  Widget build(BuildContext context) {
    final style = bold
        ? Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w900)
        : Theme.of(context).textTheme.bodyLarge;
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [Text(label, style: style), Text(formatPriceExact(value), style: style)],
      ),
    );
  }
}

class _CartRow extends ConsumerWidget {
  const _CartRow({required this.line});

  final CartLine line;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final details = [
      if (line.variant != null) line.variant!.name,
      ...line.addons.map((a) => '+ ${a.name}'),
      if (line.note.isNotEmpty) '"${line.note}"',
    ].join('  •  ');
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(line.item.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                if (details.isNotEmpty) Text(details, style: Theme.of(context).textTheme.bodySmall),
                Text(formatPrice(line.lineTotal)),
              ],
            ),
          ),
          QuantityStepper(
            quantity: line.quantity,
            min: 0,
            onChanged: (q) => ref.read(cartProvider.notifier).setQuantity(line.id, q),
          ),
        ],
      ),
    );
  }
}

class _TablePicker extends ConsumerWidget {
  const _TablePicker();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tables = ref.watch(tablesProvider);
    return AlertDialog(
      title: const Text('Choose table'),
      content: SizedBox(
        width: 420,
        child: tables.when(
          loading: () => const SizedBox(height: 80, child: Center(child: CircularProgressIndicator())),
          error: (e, _) => Text(apiMessage(e, fallback: 'Could not load tables')),
          data: (list) => list.isEmpty
              ? const Text('No tables are set up.')
              : Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final t in list)
                      ActionChip(
                        label: Text(t.label, style: const TextStyle(fontSize: 18)),
                        onPressed: () => Navigator.of(context).pop(t),
                      ),
                  ],
                ),
        ),
      ),
      actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel'))],
    );
  }
}
