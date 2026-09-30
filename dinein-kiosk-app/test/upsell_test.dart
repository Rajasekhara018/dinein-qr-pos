import 'package:dinein_kiosk/features/cart/providers/cart_provider.dart';
import 'package:dinein_kiosk/features/menu/data/menu_repository.dart';
import 'package:dinein_kiosk/features/menu/domain/menu_models.dart';
import 'package:dinein_kiosk/features/upsell/domain/upsell_models.dart';
import 'package:flutter_test/flutter_test.dart';

// Demo menu: 1 Burgers (101 veg burger, 102 zinger, 103 sold out), 2 Sides (201, 202),
// 3 Drinks (301 cold coffee, 302 lemonade), 4 Desserts (401 lava cake).
final menu = MenuData.fromJson(demoMenuJson);

MenuItem item(int id) => menu.itemById(id)!;

CartState cartWith(List<int> itemIds) {
  final notifier = CartNotifier();
  for (final id in itemIds) {
    notifier.add(item: item(id));
  }
  return notifier.state;
}

List<int> ids(List<UpsellSuggestion> s) => s.map((x) => x.item.id).toList();

void main() {
  const drinkAfterBurger = UpsellRule(
    id: 1,
    triggerCategoryId: 1,
    suggestedItemId: 301,
    placement: UpsellPlacement.itemAdded,
    message: 'Add a drink?',
  );

  group('item-added prompts', () {
    test('a category trigger fires for any item of that category', () {
      final s = suggestUpsells(
        rules: [drinkAfterBurger],
        menu: menu,
        cart: cartWith([102]),
        placement: UpsellPlacement.itemAdded,
        justAdded: item(102),
      );
      expect(ids(s), [301]);
      expect(s.single.message, 'Add a drink?');
    });

    test('it does not fire for an item of another category', () {
      final s = suggestUpsells(
        rules: [drinkAfterBurger],
        menu: menu,
        cart: cartWith([201]),
        placement: UpsellPlacement.itemAdded,
        justAdded: item(201),
      );
      expect(s, isEmpty);
    });

    test('an item trigger only fires for that exact item', () {
      const rule = UpsellRule(
        id: 2,
        triggerItemId: 101,
        suggestedItemId: 202,
        placement: UpsellPlacement.itemAdded,
      );
      expect(
        ids(suggestUpsells(
          rules: [rule],
          menu: menu,
          cart: cartWith([101]),
          placement: UpsellPlacement.itemAdded,
          justAdded: item(101),
        )),
        [202],
      );
      expect(
        suggestUpsells(
          rules: [rule],
          menu: menu,
          cart: cartWith([102]),
          placement: UpsellPlacement.itemAdded,
          justAdded: item(102),
        ),
        isEmpty,
      );
    });

    test('nothing is suggested that is already in the cart', () {
      final s = suggestUpsells(
        rules: [drinkAfterBurger],
        menu: menu,
        cart: cartWith([102, 301]),
        placement: UpsellPlacement.itemAdded,
        justAdded: item(102),
      );
      expect(s, isEmpty);
    });

    test('a sold-out or missing suggestion is skipped', () {
      const soldOut = UpsellRule(
        id: 3,
        triggerCategoryId: 2,
        suggestedItemId: 103,
        placement: UpsellPlacement.itemAdded,
      );
      const missing = UpsellRule(
        id: 4,
        triggerCategoryId: 2,
        suggestedItemId: 9999,
        placement: UpsellPlacement.itemAdded,
      );
      expect(
        suggestUpsells(
          rules: [soldOut, missing],
          menu: menu,
          cart: cartWith([201]),
          placement: UpsellPlacement.itemAdded,
          justAdded: item(201),
        ),
        isEmpty,
      );
    });

    test('an item never suggests itself', () {
      const self = UpsellRule(
        id: 5,
        suggestedItemId: 201,
        placement: UpsellPlacement.itemAdded,
      );
      expect(
        suggestUpsells(
          rules: [self],
          menu: menu,
          cart: cartWith([201]),
          placement: UpsellPlacement.itemAdded,
          justAdded: item(201),
        ),
        isEmpty,
      );
    });

    test('duplicates collapse and at most three are shown', () {
      final rules = [
        for (final id in [301, 302, 401, 202, 301])
          UpsellRule(
            id: id,
            suggestedItemId: id,
            placement: UpsellPlacement.itemAdded,
          ),
      ];
      final s = suggestUpsells(
        rules: rules,
        menu: menu,
        cart: cartWith([101]),
        placement: UpsellPlacement.itemAdded,
        justAdded: item(101),
      );
      expect(ids(s), [301, 302, 401]);
    });
  });

  group('checkout prompts', () {
    const dessert = UpsellRule(
      id: 10,
      suggestedItemId: 401,
      placement: UpsellPlacement.checkout,
      message: 'Something sweet?',
    );

    test('a rule with no trigger applies to any order', () {
      expect(
        ids(suggestUpsells(
          rules: [dessert],
          menu: menu,
          cart: cartWith([101]),
          placement: UpsellPlacement.checkout,
        )),
        [401],
      );
    });

    test('an item trigger looks at the whole cart', () {
      const rule = UpsellRule(
        id: 11,
        triggerItemId: 201,
        suggestedItemId: 302,
        placement: UpsellPlacement.checkout,
      );
      expect(
        ids(suggestUpsells(
          rules: [rule],
          menu: menu,
          cart: cartWith([101, 201]),
          placement: UpsellPlacement.checkout,
        )),
        [302],
      );
      expect(
        suggestUpsells(
          rules: [rule],
          menu: menu,
          cart: cartWith([101]),
          placement: UpsellPlacement.checkout,
        ),
        isEmpty,
      );
    });

    test('item-added rules do not show at checkout and vice versa', () {
      expect(
        suggestUpsells(
          rules: [drinkAfterBurger],
          menu: menu,
          cart: cartWith([101]),
          placement: UpsellPlacement.checkout,
        ),
        isEmpty,
      );
      expect(
        suggestUpsells(
          rules: [dessert],
          menu: menu,
          cart: cartWith([101]),
          placement: UpsellPlacement.itemAdded,
          justAdded: item(101),
        ),
        isEmpty,
      );
    });

    test('a customer who already added the dessert is not asked again', () {
      expect(
        suggestUpsells(
          rules: [dessert],
          menu: menu,
          cart: cartWith([101, 401]),
          placement: UpsellPlacement.checkout,
        ),
        isEmpty,
      );
    });
  });

  test('rules parse from the backend JSON', () {
    final rule = UpsellRule.fromJson({
      'id': 7,
      'triggerItemId': null,
      'triggerCategoryId': 3,
      'suggestedItemId': 301,
      'placement': 'CHECKOUT',
      'message': 'Cold drink?',
    });
    expect(rule.placement, UpsellPlacement.checkout);
    expect(rule.triggerCategoryId, 3);
    expect(rule.triggerItemId, isNull);
  });
}
