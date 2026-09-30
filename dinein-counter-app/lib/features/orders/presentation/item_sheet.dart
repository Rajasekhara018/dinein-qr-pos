import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/pos_widgets.dart';
import '../../cart/providers/cart_provider.dart';
import '../../menu/domain/menu_models.dart';

/// Size, extras and a note for an item that has options. Items without options are added with one tap.
Future<void> showItemSheet(BuildContext context, MenuItem item) {
  return showDialog<void>(context: context, builder: (_) => _ItemDialog(item: item));
}

class _ItemDialog extends ConsumerStatefulWidget {
  const _ItemDialog({required this.item});

  final MenuItem item;

  @override
  ConsumerState<_ItemDialog> createState() => _ItemDialogState();
}

class _ItemDialogState extends ConsumerState<_ItemDialog> {
  late MenuVariant? _variant = widget.item.defaultVariant;
  final Set<int> _addonIds = {};
  final _note = TextEditingController();
  int _quantity = 1;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  List<MenuAddon> get _addons => widget.item.addons.where((a) => _addonIds.contains(a.id)).toList();

  double get _unitPrice =>
      (_variant?.price ?? widget.item.displayPrice) + _addons.fold(0.0, (sum, a) => sum + a.price);

  void _add() {
    ref.read(cartProvider.notifier).add(
          item: widget.item,
          variant: _variant,
          addons: _addons,
          quantity: _quantity,
          note: _note.text,
        );
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final text = Theme.of(context).textTheme;
    return AlertDialog(
      title: Row(
        children: [
          FoodTypeMarker(type: item.foodType),
          const SizedBox(width: 8),
          Expanded(child: Text(item.name)),
        ],
      ),
      content: SizedBox(
        width: 460,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (item.variants.isNotEmpty) ...[
                Text('Size', style: text.titleMedium),
                RadioGroup<int>(
                  groupValue: _variant?.id,
                  onChanged: (id) => setState(() => _variant = item.variants.firstWhere((v) => v.id == id)),
                  child: Column(
                    children: [
                      for (final v in item.variants)
                        RadioListTile<int>(
                          dense: true,
                          value: v.id,
                          title: Text(v.name),
                          secondary: Text(formatPrice(v.price)),
                        ),
                    ],
                  ),
                ),
              ],
              if (item.addons.isNotEmpty) ...[
                Text('Extras', style: text.titleMedium),
                for (final a in item.addons)
                  CheckboxListTile(
                    dense: true,
                    value: _addonIds.contains(a.id),
                    onChanged: (v) => setState(() => v == true ? _addonIds.add(a.id) : _addonIds.remove(a.id)),
                    title: Text(a.name),
                    secondary: Text('+ ${formatPrice(a.price)}'),
                  ),
              ],
              const SizedBox(height: 8),
              TextField(
                controller: _note,
                maxLength: 80,
                decoration: const InputDecoration(
                    labelText: 'Note for the kitchen (e.g. no onion)', border: OutlineInputBorder()),
              ),
              Center(child: QuantityStepper(quantity: _quantity, onChanged: (q) => setState(() => _quantity = q))),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
        FilledButton(onPressed: _add, child: Text('Add  •  ${formatPrice(_unitPrice * _quantity)}')),
      ],
    );
  }
}
