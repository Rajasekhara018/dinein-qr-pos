import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_hi.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('hi'),
  ];

  /// No description provided for @whereWillYouEat.
  ///
  /// In en, this message translates to:
  /// **'Where will you eat?'**
  String get whereWillYouEat;

  /// No description provided for @dineIn.
  ///
  /// In en, this message translates to:
  /// **'Dine in'**
  String get dineIn;

  /// No description provided for @takeaway.
  ///
  /// In en, this message translates to:
  /// **'Takeaway'**
  String get takeaway;

  /// No description provided for @menuTitle.
  ///
  /// In en, this message translates to:
  /// **'Menu'**
  String get menuTitle;

  /// No description provided for @soldOut.
  ///
  /// In en, this message translates to:
  /// **'Sold out'**
  String get soldOut;

  /// No description provided for @menuUnavailable.
  ///
  /// In en, this message translates to:
  /// **'The menu is not available right now'**
  String get menuUnavailable;

  /// No description provided for @offlineMenuNotice.
  ///
  /// In en, this message translates to:
  /// **'No connection. Showing the last saved menu.'**
  String get offlineMenuNotice;

  /// No description provided for @tryAgain.
  ///
  /// In en, this message translates to:
  /// **'Try again'**
  String get tryAgain;

  /// No description provided for @itemCount.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 item} other{{count} items}}'**
  String itemCount(int count);

  /// No description provided for @viewOrder.
  ///
  /// In en, this message translates to:
  /// **'View order'**
  String get viewOrder;

  /// No description provided for @chooseSize.
  ///
  /// In en, this message translates to:
  /// **'Choose size'**
  String get chooseSize;

  /// No description provided for @addExtras.
  ///
  /// In en, this message translates to:
  /// **'Add extras'**
  String get addExtras;

  /// No description provided for @specialRequest.
  ///
  /// In en, this message translates to:
  /// **'Special request (e.g. no onion)'**
  String get specialRequest;

  /// No description provided for @addWithPrice.
  ///
  /// In en, this message translates to:
  /// **'Add  •  {price}'**
  String addWithPrice(String price);

  /// No description provided for @yourOrder.
  ///
  /// In en, this message translates to:
  /// **'Your order'**
  String get yourOrder;

  /// No description provided for @yourOrderWithType.
  ///
  /// In en, this message translates to:
  /// **'Your order  •  {type}'**
  String yourOrderWithType(String type);

  /// No description provided for @orderEmpty.
  ///
  /// In en, this message translates to:
  /// **'Your order is empty'**
  String get orderEmpty;

  /// No description provided for @browseMenu.
  ///
  /// In en, this message translates to:
  /// **'Browse menu'**
  String get browseMenu;

  /// No description provided for @subtotal.
  ///
  /// In en, this message translates to:
  /// **'Subtotal'**
  String get subtotal;

  /// No description provided for @gst.
  ///
  /// In en, this message translates to:
  /// **'GST'**
  String get gst;

  /// No description provided for @total.
  ///
  /// In en, this message translates to:
  /// **'Total'**
  String get total;

  /// No description provided for @placeOrderPayAtCounter.
  ///
  /// In en, this message translates to:
  /// **'Place order  •  pay at counter'**
  String get placeOrderPayAtCounter;

  /// No description provided for @orderPlaced.
  ///
  /// In en, this message translates to:
  /// **'Order placed!'**
  String get orderPlaced;

  /// No description provided for @yourToken.
  ///
  /// In en, this message translates to:
  /// **'Your token'**
  String get yourToken;

  /// No description provided for @payAtCounterAmount.
  ///
  /// In en, this message translates to:
  /// **'Please pay {amount} at the counter'**
  String payAtCounterAmount(String amount);

  /// No description provided for @weWillCall.
  ///
  /// In en, this message translates to:
  /// **'We will call your number when it is ready'**
  String get weWillCall;

  /// No description provided for @doneWithSeconds.
  ///
  /// In en, this message translates to:
  /// **'Done  ({seconds})'**
  String doneWithSeconds(int seconds);

  /// No description provided for @receiptNotPrinted.
  ///
  /// In en, this message translates to:
  /// **'Your receipt could not be printed. Please remember your token.'**
  String get receiptNotPrinted;

  /// No description provided for @stillThereTitle.
  ///
  /// In en, this message translates to:
  /// **'Still there?'**
  String get stillThereTitle;

  /// No description provided for @stillThereBody.
  ///
  /// In en, this message translates to:
  /// **'Your order will be cleared in {seconds} seconds.'**
  String stillThereBody(int seconds);

  /// No description provided for @yesKeepOrdering.
  ///
  /// In en, this message translates to:
  /// **'Yes, keep ordering'**
  String get yesKeepOrdering;

  /// No description provided for @upsellTitle.
  ///
  /// In en, this message translates to:
  /// **'Add something?'**
  String get upsellTitle;

  /// No description provided for @upsellAdd.
  ///
  /// In en, this message translates to:
  /// **'Add'**
  String get upsellAdd;

  /// No description provided for @upsellChoose.
  ///
  /// In en, this message translates to:
  /// **'Choose'**
  String get upsellChoose;

  /// No description provided for @noThanks.
  ///
  /// In en, this message translates to:
  /// **'No, thanks'**
  String get noThanks;

  /// No description provided for @done.
  ///
  /// In en, this message translates to:
  /// **'Done'**
  String get done;

  /// No description provided for @largeText.
  ///
  /// In en, this message translates to:
  /// **'Large text'**
  String get largeText;

  /// No description provided for @standardText.
  ///
  /// In en, this message translates to:
  /// **'Standard text'**
  String get standardText;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'hi'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'hi':
      return AppLocalizationsHi();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
