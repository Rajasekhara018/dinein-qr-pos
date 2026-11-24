import 'package:dinein_kiosk/features/cart/providers/cart_provider.dart';
import 'package:dinein_kiosk/features/menu/domain/menu_models.dart';
import 'package:flutter_test/flutter_test.dart';

MenuItem _item({double price = 100, double gst = 5}) => MenuItem(
      id: 1,
      name: 'Burger',
      description: '',
      foodType: FoodType.veg,
      displayPrice: price,
      gstPercent: gst,
      available: true,
      variants: const [],
      addons: const [],
    );

void main() {
  group('CartNotifier', () {
    test('identical configuration merges into one line', () {
      final cart = CartNotifier();
      cart.add(item: _item());
      cart.add(item: _item());
      expect(cart.state.lines.length, 1);
      expect(cart.state.itemCount, 2);
    });

    test('different notes stay separate lines', () {
      final cart = CartNotifier();
      cart.add(item: _item(), note: 'no onion');
      cart.add(item: _item());
      expect(cart.state.lines.length, 2);
    });

    test('setting quantity to zero removes the line', () {
      final cart = CartNotifier();
      cart.add(item: _item());
      cart.setQuantity(cart.state.lines.first.id, 0);
      expect(cart.state.isEmpty, isTrue);
    });

    test('GST-inclusive prices split out GST without changing the total', () {
      final cart = CartNotifier()..setPricesIncludeGst(true);
      cart.add(item: _item(price: 105, gst: 5));
      expect(cart.state.total, closeTo(105, 0.001));
      expect(cart.state.gst, closeTo(5, 0.001));
      expect(cart.state.subtotal, closeTo(100, 0.001));
    });

    test('GST-exclusive prices add GST on top', () {
      final cart = CartNotifier()..setPricesIncludeGst(false);
      cart.add(item: _item(price: 100, gst: 5));
      expect(cart.state.total, closeTo(105, 0.001));
    });
  });
}
