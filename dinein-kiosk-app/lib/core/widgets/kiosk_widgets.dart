import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../features/menu/domain/menu_models.dart';
import '../config/app_config.dart';

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
    final color = switch (type) {
      FoodType.veg => const Color(0xFF2B8A3E),
      FoodType.nonVeg => const Color(0xFFC92A2A),
      FoodType.egg => const Color(0xFFF59F00),
      FoodType.other => Colors.transparent,
    };
    if (type == FoodType.other) return const SizedBox.shrink();
    return Container(
      width: 20,
      height: 20,
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        border: Border.all(color: color, width: 2),
        borderRadius: BorderRadius.circular(4),
      ),
      child: DecoratedBox(
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      ),
    );
  }
}

class ItemImage extends StatelessWidget {
  const ItemImage({super.key, required this.url, this.radius = 16});

  final String? url;
  final double radius;

  @override
  Widget build(BuildContext context) {
    final resolved = AppConfig.resolveUrl(url);
    final placeholder = ColoredBox(
      color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.08),
      child: Center(
        child: Icon(
          Icons.fastfood_rounded,
          size: 48,
          color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.4),
        ),
      ),
    );
    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: resolved == null
          ? placeholder
          : CachedNetworkImage(
              imageUrl: resolved,
              fit: BoxFit.cover,
              placeholder: (_, _) => placeholder,
              errorWidget: (_, _, _) => placeholder,
            ),
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
        _StepButton(
          icon: Icons.remove_rounded,
          onPressed: quantity > min ? () => onChanged(quantity - 1) : null,
        ),
        SizedBox(
          width: 48,
          child: Text(
            '$quantity',
            textAlign: TextAlign.center,
            style: Theme.of(context)
                .textTheme
                .titleLarge
                ?.copyWith(fontWeight: FontWeight.w800),
          ),
        ),
        _StepButton(
          icon: Icons.add_rounded,
          onPressed: quantity < 99 ? () => onChanged(quantity + 1) : null,
        ),
      ],
    );
  }
}

class _StepButton extends StatelessWidget {
  const _StepButton({required this.icon, required this.onPressed});

  final IconData icon;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return IconButton.filledTonal(
      onPressed: onPressed,
      icon: Icon(icon, size: 28),
      constraints: const BoxConstraints(minWidth: 56, minHeight: 56),
    );
  }
}
