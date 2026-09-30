import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../features/menu/domain/menu_models.dart';

final _money = NumberFormat.currency(locale: 'en_IN', symbol: '₹', decimalDigits: 0);
final _moneyExact = NumberFormat.currency(locale: 'en_IN', symbol: '₹', decimalDigits: 2);

String formatPrice(double value) => _money.format(value);
String formatPriceExact(double value) => _moneyExact.format(value);

/// The Indian veg / non-veg / egg square marker.
class FoodTypeMarker extends StatelessWidget {
  const FoodTypeMarker({super.key, required this.type});

  final FoodType type;

  @override
  Widget build(BuildContext context) {
    if (type == FoodType.other) return const SizedBox.shrink();
    final color = switch (type) {
      FoodType.veg => const Color(0xFF2B8A3E),
      FoodType.nonVeg => const Color(0xFFC92A2A),
      _ => const Color(0xFFF59F00),
    };
    return Container(
      width: 18,
      height: 18,
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        border: Border.all(color: color, width: 2),
        borderRadius: BorderRadius.circular(4),
      ),
      child: DecoratedBox(decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
    );
  }
}

class QuantityStepper extends StatelessWidget {
  const QuantityStepper({
    super.key,
    required this.quantity,
    required this.onChanged,
    this.min = 1,
  });

  final int quantity;
  final ValueChanged<int> onChanged;
  final int min;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton.filledTonal(
          onPressed: quantity > min ? () => onChanged(quantity - 1) : null,
          icon: const Icon(Icons.remove_rounded),
          tooltip: 'Less',
        ),
        SizedBox(
          width: 44,
          child: Text('$quantity',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
        ),
        IconButton.filledTonal(
          onPressed: quantity < 99 ? () => onChanged(quantity + 1) : null,
          icon: const Icon(Icons.add_rounded),
          tooltip: 'More',
        ),
      ],
    );
  }
}
