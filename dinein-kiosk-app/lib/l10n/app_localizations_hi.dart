// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Hindi (`hi`).
class AppLocalizationsHi extends AppLocalizations {
  AppLocalizationsHi([String locale = 'hi']) : super(locale);

  @override
  String get whereWillYouEat => 'आप कहाँ खाएँगे?';

  @override
  String get dineIn => 'यहीं खाएँगे';

  @override
  String get takeaway => 'पैक करवाएँ';

  @override
  String get menuTitle => 'मेन्यू';

  @override
  String get soldOut => 'उपलब्ध नहीं';

  @override
  String get menuUnavailable => 'मेन्यू अभी उपलब्ध नहीं है';

  @override
  String get offlineMenuNotice =>
      'कनेक्शन नहीं है। पिछला सेव किया हुआ मेन्यू दिख रहा है।';

  @override
  String get tryAgain => 'फिर से कोशिश करें';

  @override
  String itemCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count आइटम',
      one: '1 आइटम',
    );
    return '$_temp0';
  }

  @override
  String get viewOrder => 'ऑर्डर देखें';

  @override
  String get chooseSize => 'साइज़ चुनें';

  @override
  String get addExtras => 'एक्स्ट्रा जोड़ें';

  @override
  String get specialRequest => 'विशेष अनुरोध (जैसे प्याज़ नहीं)';

  @override
  String addWithPrice(String price) {
    return 'जोड़ें  •  $price';
  }

  @override
  String get yourOrder => 'आपका ऑर्डर';

  @override
  String yourOrderWithType(String type) {
    return 'आपका ऑर्डर  •  $type';
  }

  @override
  String get orderEmpty => 'आपका ऑर्डर खाली है';

  @override
  String get browseMenu => 'मेन्यू देखें';

  @override
  String get subtotal => 'उप-योग';

  @override
  String get gst => 'जीएसटी';

  @override
  String get total => 'कुल';

  @override
  String get placeOrderPayAtCounter => 'ऑर्डर करें  •  काउंटर पर भुगतान करें';

  @override
  String get orderPlaced => 'ऑर्डर हो गया!';

  @override
  String get yourToken => 'आपका टोकन';

  @override
  String payAtCounterAmount(String amount) {
    return 'कृपया काउंटर पर $amount का भुगतान करें';
  }

  @override
  String get weWillCall => 'तैयार होने पर हम आपका नंबर बुलाएँगे';

  @override
  String doneWithSeconds(int seconds) {
    return 'हो गया  ($seconds)';
  }

  @override
  String get receiptNotPrinted =>
      'आपकी रसीद प्रिंट नहीं हो सकी। कृपया अपना टोकन याद रखें।';

  @override
  String get stillThereTitle => 'क्या आप वहाँ हैं?';

  @override
  String stillThereBody(int seconds) {
    return 'आपका ऑर्डर $seconds सेकंड में हट जाएगा।';
  }

  @override
  String get yesKeepOrdering => 'हाँ, ऑर्डर जारी रखें';

  @override
  String get upsellTitle => 'कुछ और जोड़ेंगे?';

  @override
  String get upsellAdd => 'जोड़ें';

  @override
  String get upsellChoose => 'चुनें';

  @override
  String get noThanks => 'नहीं, धन्यवाद';

  @override
  String get done => 'हो गया';

  @override
  String get largeText => 'बड़े अक्षर';

  @override
  String get standardText => 'सामान्य अक्षर';
}
