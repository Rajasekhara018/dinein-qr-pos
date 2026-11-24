import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../cart/providers/cart_provider.dart';
import '../menu/data/menu_repository.dart';
import '../order/data/order_repository.dart';
import '../upsell/data/upsell_repository.dart';

final orderTypeProvider = StateProvider<OrderType?>((ref) => null);

const supportedLanguages = [Locale('en'), Locale('hi')];

/// The customer's language for this visit. Owner-written text (headline, button label) is not translated.
final localeProvider = StateProvider<Locale>((ref) => const Locale('en'));

/// Large text and higher contrast for this visit.
final accessibilityModeProvider = StateProvider<bool>((ref) => false);

/// True while a staff member has the service menu open, so the idle timer leaves them alone.
final staffMenuOpenProvider = StateProvider<bool>((ref) => false);

/// Clears everything a customer entered so nothing carries over to the next one.
final kioskSessionProvider = Provider<KioskSession>((ref) => KioskSession(ref));

class KioskSession {
  KioskSession(this._ref);

  final Ref _ref;

  void reset() {
    _ref.read(cartProvider.notifier).clear();
    _ref.read(orderTypeProvider.notifier).state = null;
    _ref.read(localeProvider.notifier).state = const Locale('en');
    _ref.read(accessibilityModeProvider.notifier).state = false;
    // Each new customer starts from the freshest menu; the ETag keeps this cheap.
    _ref.invalidate(menuProvider);
    _ref.invalidate(upsellsProvider);
  }
}
