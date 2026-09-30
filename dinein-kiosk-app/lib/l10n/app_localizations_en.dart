// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get whereWillYouEat => 'Where will you eat?';

  @override
  String get dineIn => 'Dine in';

  @override
  String get takeaway => 'Takeaway';

  @override
  String get menuTitle => 'Menu';

  @override
  String get soldOut => 'Sold out';

  @override
  String get menuUnavailable => 'The menu is not available right now';

  @override
  String get offlineMenuNotice => 'No connection. Showing the last saved menu.';

  @override
  String get tryAgain => 'Try again';

  @override
  String itemCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count items',
      one: '1 item',
    );
    return '$_temp0';
  }

  @override
  String get viewOrder => 'View order';

  @override
  String get chooseSize => 'Choose size';

  @override
  String get addExtras => 'Add extras';

  @override
  String get specialRequest => 'Special request (e.g. no onion)';

  @override
  String addWithPrice(String price) {
    return 'Add  •  $price';
  }

  @override
  String get yourOrder => 'Your order';

  @override
  String yourOrderWithType(String type) {
    return 'Your order  •  $type';
  }

  @override
  String get orderEmpty => 'Your order is empty';

  @override
  String get browseMenu => 'Browse menu';

  @override
  String get subtotal => 'Subtotal';

  @override
  String get gst => 'GST';

  @override
  String get total => 'Total';

  @override
  String get placeOrderPayAtCounter => 'Place order  •  pay at counter';

  @override
  String get orderPlaced => 'Order placed!';

  @override
  String get yourToken => 'Your token';

  @override
  String payAtCounterAmount(String amount) {
    return 'Please pay $amount at the counter';
  }

  @override
  String get weWillCall => 'We will call your number when it is ready';

  @override
  String doneWithSeconds(int seconds) {
    return 'Done  ($seconds)';
  }

  @override
  String get receiptNotPrinted =>
      'Your receipt could not be printed. Please remember your token.';

  @override
  String get stillThereTitle => 'Still there?';

  @override
  String stillThereBody(int seconds) {
    return 'Your order will be cleared in $seconds seconds.';
  }

  @override
  String get yesKeepOrdering => 'Yes, keep ordering';

  @override
  String get upsellTitle => 'Add something?';

  @override
  String get upsellAdd => 'Add';

  @override
  String get upsellChoose => 'Choose';

  @override
  String get noThanks => 'No, thanks';

  @override
  String get done => 'Done';

  @override
  String get largeText => 'Large text';

  @override
  String get standardText => 'Standard text';
}
