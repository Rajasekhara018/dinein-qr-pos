import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/kiosk_widgets.dart';
import '../../../l10n/app_localizations.dart';
import '../../cart/providers/cart_provider.dart';
import '../../menu/data/menu_repository.dart';
import '../../menu/domain/menu_models.dart';
import '../../menu/presentation/item_sheet.dart';
import '../data/upsell_repository.dart';
import '../domain/upsell_models.dart';

/// Offered right after the customer adds an item ("add a drink?"). Does nothing when no rule applies.
Future<void> showItemAddedUpsell(BuildContext context, WidgetRef ref, MenuItem justAdded) {
  return _show(context, ref, UpsellPlacement.itemAdded, justAdded: justAdded);
}

/// Offered once before the order is placed ("something sweet?"). Returns true if a prompt was shown.
Future<bool> showCheckoutUpsell(BuildContext context, WidgetRef ref) async {
  return _show(context, ref, UpsellPlacement.checkout);
}

Future<bool> _show(
  BuildContext context,
  WidgetRef ref,
  UpsellPlacement placement, {
  MenuItem? justAdded,
}) async {
  final menu = ref.read(menuProvider).valueOrNull;
  if (menu == null) return false;
  final suggestions = suggestUpsells(
    rules: ref.read(upsellsProvider).valueOrNull ?? const [],
    menu: menu,
    cart: ref.read(cartProvider),
    placement: placement,
    justAdded: justAdded,
  );
  if (suggestions.isEmpty || !context.mounted) return false;
  await showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    constraints: const BoxConstraints(maxWidth: 720),
    builder: (_) => _UpsellSheet(suggestions: suggestions),
  );
  return true;
}

class _UpsellSheet extends ConsumerStatefulWidget {
  const _UpsellSheet({required this.suggestions});

  final List<UpsellSuggestion> suggestions;

  @override
  ConsumerState<_UpsellSheet> createState() => _UpsellSheetState();
}

class _UpsellSheetState extends ConsumerState<_UpsellSheet> {
  final Set<int> _added = {};

  Future<void> _add(MenuItem item) async {
    if (item.isCustomisable) {
      // Let them pick the size and extras. No further prompts from here: one nudge per add is enough.
      final added = await showItemSheet(context, item);
      if (added && mounted) setState(() => _added.add(item.id));
      return;
    }
    ref.read(cartProvider.notifier).add(item: item);
    setState(() => _added.add(item.id));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final text = Theme.of(context).textTheme;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              widget.suggestions.first.message ?? l10n.upsellTitle,
              style: text.headlineSmall?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 16),
            for (final s in widget.suggestions)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Row(
                  children: [
                    SizedBox(width: 72, height: 72, child: ItemImage(url: s.item.imageUrl)),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              FoodTypeMarker(type: s.item.foodType),
                              const SizedBox(width: 8),
                              Flexible(
                                child: Text(s.item.name,
                                    style: text.titleMedium
                                        ?.copyWith(fontWeight: FontWeight.w700)),
                              ),
                            ],
                          ),
                          Text(formatPrice(s.item.displayPrice), style: text.titleMedium),
                        ],
                      ),
                    ),
                    if (_added.contains(s.item.id))
                      Icon(Icons.check_circle_rounded,
                          size: 40, color: Theme.of(context).colorScheme.primary)
                    else
                      FilledButton(
                        onPressed: () => _add(s.item),
                        child: Text(s.item.isCustomisable ? l10n.upsellChoose : l10n.upsellAdd),
                      ),
                  ],
                ),
              ),
            const SizedBox(height: 4),
            OutlinedButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text(_added.isEmpty ? l10n.noThanks : l10n.done),
            ),
          ],
        ),
      ),
    );
  }
}
