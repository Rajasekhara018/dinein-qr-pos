import 'package:flutter/services.dart';

/// Currency an amount field starts on until the app provides one (`HQ_AMOUNT_FALLBACK_CURRENCY`).
const String hqAmountFallbackCurrency = 'INR';

/// Decimal places of a currency this table does not list (`HQ_AMOUNT_FALLBACK_DECIMALS`).
const int hqAmountFallbackDecimals = 2;

/// Most decimal places any currency uses; a caller-set `decimals` is capped here (`HQ_AMOUNT_MAX_DECIMALS`).
const int hqAmountMaxDecimals = 4;

const Map<String, int> _currencyDecimals = <String, int>{
  'JPY': 0,
  'KRW': 0,
  'VND': 0,
  'KWD': 3,
  'BHD': 3,
  'OMR': 3,
  'JOD': 3,
  'TND': 3,
};

/// Display symbols; a currency not listed is shown by its ISO code.
const Map<String, String> hqCurrencySymbols = <String, String>{
  'INR': '₹',
  'USD': r'$',
  'EUR': '€',
  'GBP': '£',
};

/// Decimal places [currency] uses (paise precision, 2, for INR).
int hqCurrencyDecimals(String currency) => _currencyDecimals[currency.toUpperCase()] ?? hqAmountFallbackDecimals;

/// INR is grouped 12,34,567; every other currency 1,234,567.
bool hqUsesIndianGrouping(String currency) => currency.toUpperCase() == 'INR';

/// Groups the digits of a whole number: Indian (last 3, then pairs) or international (threes).
String hqGroupDigits(String digits, {bool indian = true}) {
  if (digits.length <= 3) return digits;
  final head = digits.substring(0, digits.length - 3);
  final tail = digits.substring(digits.length - 3);
  final size = indian ? 2 : 3;
  final parts = <String>[];
  for (var end = head.length; end > 0; end -= size) {
    final start = end - size < 0 ? 0 : end - size;
    parts.insert(0, head.substring(start, end));
  }
  return '${parts.join(',')},$tail';
}

/// Formats [minor] units (paise for INR) as a grouped decimal string, e.g. `123456` -> `1,234.56`.
String hqFormatMinor(int minor, {int decimals = 2, bool indian = true}) {
  final negative = minor < 0;
  final digits = minor.abs().toString().padLeft(decimals + 1, '0');
  final whole = digits.substring(0, digits.length - decimals);
  final fraction = decimals == 0 ? '' : '.${digits.substring(digits.length - decimals)}';
  return '${negative ? '-' : ''}${hqGroupDigits(whole, indian: indian)}$fraction';
}

/// Parses a plain decimal string (`1234.5`, `-7`, `.25`) into minor units, rounding HALF_EVEN at [decimals] places, the
/// same mode the services use for money. Returns null when [text] is not a plain decimal (exponent, letters, empty).
int? hqParseDecimalToMinor(String text, {int decimals = 2}) {
  final trimmed = text.trim();
  final match = RegExp(r'^(-?)(\d*)(?:\.(\d*))?$').firstMatch(trimmed);
  if (match == null) return null;
  final negative = match.group(1) == '-';
  final whole = match.group(2) ?? '';
  final fraction = match.group(3) ?? '';
  if (whole.isEmpty && fraction.isEmpty) return null;
  final kept = fraction.length >= decimals ? fraction.substring(0, decimals) : fraction.padRight(decimals, '0');
  final rest = fraction.length > decimals ? fraction.substring(decimals) : '';
  var magnitude = int.parse('${whole.isEmpty ? '0' : whole}$kept');
  if (rest.isNotEmpty) {
    final first = int.parse(rest[0]);
    final restNonZero = rest.substring(1).replaceAll('0', '').isNotEmpty;
    final lastKeptOdd = magnitude.isOdd;
    final roundUp = first > 5 || (first == 5 && (restNonZero || lastKeptOdd));
    if (roundUp) magnitude += 1;
  }
  return negative ? -magnitude : magnitude;
}

/// Formats an API amount (a `num` or numeric string, e.g. the BigDecimal of the investment service) for display:
/// `1234567.5` + INR -> `₹12,34,567.50`. Returns [fallback] for null or non-numeric input.
String hqFormatMoney(Object? amount, {String currency = hqAmountFallbackCurrency, int? decimals, bool showSymbol = true, String fallback = '-'}) {
  if (amount == null) return fallback;
  final places = (decimals ?? hqCurrencyDecimals(currency)).clamp(0, hqAmountMaxDecimals);
  final minor = hqParseDecimalToMinor(amount.toString(), decimals: places);
  if (minor == null) return fallback;
  final body = hqFormatMinor(minor, decimals: places, indian: hqUsesIndianGrouping(currency));
  if (!showSymbol) return body;
  final symbol = hqCurrencySymbols[currency.toUpperCase()] ?? '${currency.toUpperCase()} ';
  return minor < 0 ? '-$symbol${body.substring(1)}' : '$symbol$body';
}

/// Removes the grouping commas from an amount field's text.
String hqNormalizeAmountText(String formatted) => formatted.replaceAll(',', '');

/// Keeps only digits and one decimal point, limits the fraction to [decimals] places and groups the whole part as the user types.
/// The cursor is placed at the end of the text.
class HqAmountFormatter extends TextInputFormatter {
  const HqAmountFormatter({required this.decimals, required this.indian});

  final int decimals;
  final bool indian;

  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    final raw = newValue.text.replaceAll(RegExp(r'[^0-9.]'), '');
    final dot = raw.indexOf('.');
    var whole = dot < 0 ? raw : raw.substring(0, dot);
    String? fraction = dot < 0 || decimals == 0 ? null : raw.substring(dot + 1).replaceAll('.', '');
    if (fraction != null && fraction.length > decimals) fraction = fraction.substring(0, decimals);
    whole = whole.replaceFirst(RegExp(r'^0+(?=\d)'), '');
    if (whole.isEmpty && fraction != null) whole = '0';
    final grouped = hqGroupDigits(whole, indian: indian);
    final text = fraction == null ? grouped : '$grouped.$fraction';
    return TextEditingValue(text: text, selection: TextSelection.collapsed(offset: text.length));
  }
}
