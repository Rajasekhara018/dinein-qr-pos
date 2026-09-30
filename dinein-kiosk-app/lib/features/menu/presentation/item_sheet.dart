import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/kiosk_widgets.dart';
import '../../cart/providers/cart_provider.dart';
import '../domain/menu_models.dart';

Future<void> showItemSheet(BuildContext context, MenuItem item) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    constraints: const BoxConstraints(maxWidth: 720),
    builder: (_) => _ItemSheet(item: item),
  );
}

class _ItemSheet extends ConsumerStatefulWidget {
  const _ItemSheet({required this.item});

  final MenuItem item;

  @override
  ConsumerState<_ItemSheet> createState() => _ItemSheetState();
}

class _ItemSheetState extends ConsumerState<_ItemSheet> {
  late MenuVariant? _variant = widget.item.defaultVariant;
  final Set<int> _addonIds = {};
  final _note = TextEditingController();
  int _quantity = 1;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  List<MenuAddon> get _addons =>
      widget.item.addons.where((a) => _addonIds.contains(a.id)).toList();

  double get _unitPrice =>
      (_variant?.price ?? widget.item.displayPrice) +
      _addons.fold(0.0, (sum, a) => sum + a.price);

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
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Flexible(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
              shrinkWrap: true,
              children: [
                SizedBox(height: 200, child: ItemImage(url: item.imageUrl)),
                const SizedBox(height: 16),
                Row(
                  children: [
                    FoodTypeMarker(type: item.foodType),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(item.name,
                          style: text.headlineSmall
                              ?.copyWith(fontWeight: FontWeight.w800)),
                    ),
                  ],
                ),
                if (item.description.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Text(item.description, style: text.bodyLarge),
                ],
                if (item.variants.isNotEmpty) ...[
                  const SizedBox(height: 20),
                  Text('Choose size',
                      style: text.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
                  RadioGroup<int>(
                    groupValue: _variant?.id,
                    onChanged: (id) => setState(() {
                      _variant = item.variants.firstWhere((v) => v.id == id);
                    }),
                    child: Column(
                      children: [
                        for (final v in item.variants)
                          RadioListTile<int>(
                            value: v.id,
                            title: Text(v.name, style: text.titleMedium),
                            secondary: Text(formatPrice(v.price),
                                style: text.titleMedium),
                          ),
                      ],
                    ),
                  ),
                ],
                if (item.addons.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text('Add extras',
                      style: text.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
                  for (final a in item.addons)
                    CheckboxListTile(
                      value: _addonIds.contains(a.id),
                      onChanged: (checked) => setState(() {
                        checked == true
                            ? _addonIds.add(a.id)
                            : _addonIds.remove(a.id);
                      }),
                      title: Text(a.name, style: text.titleMedium),
                      secondary: Text('+ ${formatPrice(a.price)}',
                          style: text.titleMedium),
                    ),
                ],
                const SizedBox(height: 12),
                TextField(
                  controller: _note,
                  maxLength: 80,
                  decoration: const InputDecoration(
                    labelText: 'Special request (e.g. no onion)',
                    border: OutlineInputBorder(),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
            child: Row(
              children: [
                QuantityStepper(
                  quantity: _quantity,
                  onChanged: (q) => setState(() => _quantity = q),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: FilledButton(
                    onPressed: _add,
                    child: Text('Add  •  ${formatPrice(_unitPrice * _quantity)}'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
