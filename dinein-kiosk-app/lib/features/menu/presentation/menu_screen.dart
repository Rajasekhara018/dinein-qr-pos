import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/widgets/kiosk_widgets.dart';
import '../../cart/providers/cart_provider.dart';
import '../data/menu_repository.dart';
import '../domain/menu_models.dart';
import 'item_sheet.dart';

class MenuScreen extends ConsumerStatefulWidget {
  const MenuScreen({super.key});

  @override
  ConsumerState<MenuScreen> createState() => _MenuScreenState();
}

class _MenuScreenState extends ConsumerState<MenuScreen> {
  int _selected = 0;

  @override
  Widget build(BuildContext context) {
    final menu = ref.watch(menuProvider);
    final cart = ref.watch(cartProvider);

    ref.listen(menuProvider, (_, next) {
      final data = next.valueOrNull;
      if (data != null) {
        ref.read(cartProvider.notifier).setPricesIncludeGst(data.pricesIncludeGst);
      }
    });

    return Scaffold(
      appBar: AppBar(
        leading: BackButton(onPressed: () => context.go('/order-type')),
        title: const Text('Menu'),
      ),
      body: menu.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _MenuError(
          message: '$e'.replaceFirst('Exception: ', ''),
          onRetry: () => ref.invalidate(menuProvider),
        ),
        data: (data) {
          if (data.categories.isEmpty) {
            return const Center(child: Text('The menu is not available right now'));
          }
          final index = _selected.clamp(0, data.categories.length - 1);
          final category = data.categories[index];
          return Row(
            children: [
              _CategoryRail(
                categories: data.categories,
                selected: index,
                onSelect: (i) => setState(() => _selected = i),
              ),
              Expanded(child: _ItemGrid(category: category)),
            ],
          );
        },
      ),
      bottomNavigationBar: cart.isEmpty ? null : _CartBar(cart: cart),
    );
  }
}

class _CategoryRail extends StatelessWidget {
  const _CategoryRail({
    required this.categories,
    required this.selected,
    required this.onSelect,
  });

  final List<MenuCategory> categories;
  final int selected;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      width: 200,
      color: Colors.white,
      child: ListView.builder(
        padding: const EdgeInsets.symmetric(vertical: 8),
        itemCount: categories.length,
        itemBuilder: (context, i) {
          final isSelected = i == selected;
          return InkWell(
            onTap: () => onSelect(i),
            child: Container(
              constraints: const BoxConstraints(minHeight: 72),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              alignment: Alignment.centerLeft,
              decoration: BoxDecoration(
                color: isSelected ? scheme.primary.withValues(alpha: 0.1) : null,
                border: Border(
                  left: BorderSide(
                    width: 6,
                    color: isSelected ? scheme.primary : Colors.transparent,
                  ),
                ),
              ),
              child: Text(
                categories[i].name,
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      fontWeight: isSelected ? FontWeight.w800 : FontWeight.w500,
                      color: isSelected ? scheme.primary : null,
                    ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _ItemGrid extends StatelessWidget {
  const _ItemGrid({required this.category});

  final MenuCategory category;

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding: const EdgeInsets.all(20),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
        maxCrossAxisExtent: 300,
        mainAxisExtent: 340,
        crossAxisSpacing: 20,
        mainAxisSpacing: 20,
      ),
      itemCount: category.items.length,
      itemBuilder: (_, i) => _ItemCard(item: category.items[i]),
    );
  }
}

class _ItemCard extends ConsumerWidget {
  const _ItemCard({required this.item});

  final MenuItem item;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final soldOut = !item.available;
    return Opacity(
      opacity: soldOut ? 0.5 : 1,
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: soldOut ? null : () => showItemSheet(context, item),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: SizedBox(
                    width: double.infinity,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        ItemImage(url: item.imageUrl),
                        if (soldOut)
                          Center(
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 12, vertical: 6),
                              decoration: BoxDecoration(
                                color: Colors.black87,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: const Text('Sold out',
                                  style: TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w700)),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    FoodTypeMarker(type: item.foodType),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        item.name,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context)
                            .textTheme
                            .titleMedium
                            ?.copyWith(fontWeight: FontWeight.w700),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  formatPrice(item.displayPrice),
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w800,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _CartBar extends StatelessWidget {
  const _CartBar({required this.cart});

  final CartState cart;

  @override
  Widget build(BuildContext context) {
    return Material(
      elevation: 12,
      color: Colors.white,
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  '${cart.itemCount} ${cart.itemCount == 1 ? 'item' : 'items'}'
                  '  •  ${formatPrice(cart.total)}',
                  style: Theme.of(context)
                      .textTheme
                      .headlineSmall
                      ?.copyWith(fontWeight: FontWeight.w800),
                ),
              ),
              FilledButton.icon(
                onPressed: () => context.go('/cart'),
                icon: const Icon(Icons.shopping_cart_checkout_rounded),
                label: const Text('View order'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MenuError extends StatelessWidget {
  const _MenuError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.cloud_off_rounded, size: 64),
          const SizedBox(height: 16),
          Text(message, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          FilledButton(onPressed: onRetry, child: const Text('Try again')),
        ],
      ),
    );
  }
}
