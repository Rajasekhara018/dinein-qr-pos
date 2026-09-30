import 'package:equatable/equatable.dart';

import '../../cart/providers/cart_provider.dart';
import '../../menu/domain/menu_models.dart';

enum UpsellPlacement { itemAdded, checkout }

class UpsellRule extends Equatable {
  const UpsellRule({
    required this.id,
    required this.suggestedItemId,
    required this.placement,
    this.triggerItemId,
    this.triggerCategoryId,
    this.message,
  });

  final int id;
  final int? triggerItemId;
  final int? triggerCategoryId;
  final int suggestedItemId;
  final UpsellPlacement placement;
  final String? message;

  factory UpsellRule.fromJson(Map<String, dynamic> json) => UpsellRule(
        id: (json['id'] as num).toInt(),
        triggerItemId: (json['triggerItemId'] as num?)?.toInt(),
        triggerCategoryId: (json['triggerCategoryId'] as num?)?.toInt(),
        suggestedItemId: (json['suggestedItemId'] as num).toInt(),
        placement: json['placement'] == 'CHECKOUT'
            ? UpsellPlacement.checkout
            : UpsellPlacement.itemAdded,
        message: json['message'] as String?,
      );

  @override
  List<Object?> get props =>
      [id, triggerItemId, triggerCategoryId, suggestedItemId, placement, message];
}

class UpsellSuggestion extends Equatable {
  const UpsellSuggestion({required this.item, this.message});

  final MenuItem item;
  final String? message;

  @override
  List<Object?> get props => [item, message];
}

const _maxSuggestions = 3;

/// Which items to suggest, given the owner's rules, the live menu and the cart.
///
/// A suggestion is dropped when its item is no longer on the menu, is sold out,
/// is already in the cart, or is the item that was just added. Duplicates
/// collapse to the first rule that named them, and at most three are shown so
/// the prompt stays a nudge rather than a second menu.
List<UpsellSuggestion> suggestUpsells({
  required List<UpsellRule> rules,
  required MenuData menu,
  required CartState cart,
  required UpsellPlacement placement,
  MenuItem? justAdded,
}) {
  final inCart = cart.lines.map((l) => l.item.id).toSet();
  final cartCategories = {
    for (final line in cart.lines) menu.categoryIdOf(line.item.id),
  }..remove(null);

  bool triggered(UpsellRule rule) {
    final anyOrder = rule.triggerItemId == null && rule.triggerCategoryId == null;
    if (anyOrder) return true;
    if (placement == UpsellPlacement.itemAdded) {
      if (justAdded == null) return false;
      return rule.triggerItemId == justAdded.id ||
          (rule.triggerCategoryId != null &&
              rule.triggerCategoryId == menu.categoryIdOf(justAdded.id));
    }
    return (rule.triggerItemId != null && inCart.contains(rule.triggerItemId)) ||
        (rule.triggerCategoryId != null &&
            cartCategories.contains(rule.triggerCategoryId));
  }

  final seen = <int>{};
  final result = <UpsellSuggestion>[];
  for (final rule in rules) {
    if (rule.placement != placement || !triggered(rule)) continue;
    final item = menu.itemById(rule.suggestedItemId);
    if (item == null || !item.available) continue;
    if (inCart.contains(item.id) || item.id == justAdded?.id) continue;
    if (!seen.add(item.id)) continue;
    result.add(UpsellSuggestion(item: item, message: rule.message));
    if (result.length == _maxSuggestions) break;
  }
  return result;
}
