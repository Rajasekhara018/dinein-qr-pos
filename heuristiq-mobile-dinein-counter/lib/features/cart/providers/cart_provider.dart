import 'package:equatable/equatable.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../menu/domain/menu_models.dart';

class CartLine extends Equatable {
  const CartLine({
    required this.id,
    required this.item,
    required this.addons,
    required this.quantity,
    this.variant,
    this.note = '',
  });

  final int id;
  final MenuItem item;
  final MenuVariant? variant;
  final List<MenuAddon> addons;
  final int quantity;
  final String note;

  double get unitPrice =>
      (variant?.price ?? item.displayPrice) +
      addons.fold(0.0, (sum, a) => sum + a.price);

  double get lineTotal => unitPrice * quantity;

  CartLine withQuantity(int q) => CartLine(
        id: id,
        item: item,
        variant: variant,
        addons: addons,
        quantity: q,
        note: note,
      );

  @override
  List<Object?> get props => [id, item, variant, addons, quantity, note];
}

/// Display estimate only. The server returns the authoritative totals when the
/// order is placed, so the receipt and the payment amount always come from it.
class CartState extends Equatable {
  const CartState({this.lines = const [], this.pricesIncludeGst = true});

  final List<CartLine> lines;
  final bool pricesIncludeGst;

  int get itemCount => lines.fold(0, (sum, l) => sum + l.quantity);
  bool get isEmpty => lines.isEmpty;

  double get gst => lines.fold(0.0, (sum, l) {
        final rate = l.item.gstPercent;
        return sum +
            (pricesIncludeGst
                ? l.lineTotal * rate / (100 + rate)
                : l.lineTotal * rate / 100);
      });

  double get subtotal {
    final listed = lines.fold(0.0, (sum, l) => sum + l.lineTotal);
    return pricesIncludeGst ? listed - gst : listed;
  }

  double get total => subtotal + gst;

  @override
  List<Object?> get props => [lines, pricesIncludeGst];
}

final cartProvider = StateNotifierProvider<CartNotifier, CartState>((ref) {
  return CartNotifier();
});

class CartNotifier extends StateNotifier<CartState> {
  CartNotifier() : super(const CartState());

  int _nextId = 1;

  void setPricesIncludeGst(bool value) {
    if (value == state.pricesIncludeGst) return;
    state = CartState(lines: state.lines, pricesIncludeGst: value);
  }

  void add({
    required MenuItem item,
    MenuVariant? variant,
    List<MenuAddon> addons = const [],
    int quantity = 1,
    String note = '',
  }) {
    final addonIds = addons.map((a) => a.id).toSet();
    final trimmed = note.trim();
    // Identical configuration merges into one line instead of stacking rows.
    final existing = state.lines.where((l) =>
        l.item.id == item.id &&
        l.variant?.id == variant?.id &&
        l.note == trimmed &&
        l.addons.length == addonIds.length &&
        l.addons.every((a) => addonIds.contains(a.id)));
    if (existing.isNotEmpty) {
      final line = existing.first;
      setQuantity(line.id, line.quantity + quantity);
      return;
    }
    state = CartState(
      lines: [
        ...state.lines,
        CartLine(
          id: _nextId++,
          item: item,
          variant: variant,
          addons: addons,
          quantity: quantity,
          note: trimmed,
        ),
      ],
      pricesIncludeGst: state.pricesIncludeGst,
    );
  }

  void setQuantity(int lineId, int quantity) {
    if (quantity <= 0) {
      remove(lineId);
      return;
    }
    state = CartState(
      lines: [
        for (final l in state.lines)
          if (l.id == lineId) l.withQuantity(quantity) else l,
      ],
      pricesIncludeGst: state.pricesIncludeGst,
    );
  }

  void remove(int lineId) {
    state = CartState(
      lines: state.lines.where((l) => l.id != lineId).toList(),
      pricesIncludeGst: state.pricesIncludeGst,
    );
  }

  void clear() {
    state = CartState(pricesIncludeGst: state.pricesIncludeGst);
  }
}
