import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../ui/hq_amount.dart';

/// The character that hides digits.
const String hqMaskChar = '•';

/// Hides all but the last [visible] characters of [value]: `ABCDE1234F` becomes `••••••234F`.
/// A value no longer than [visible] is fully hidden. Use it for PAN and bank account numbers (show the last 4); never to
/// "protect" a value that should not be on the device at all.
String hqMaskTail(String value, {int visible = 4}) {
  final text = value.trim();
  if (text.isEmpty) return '';
  if (text.length <= visible) return hqMaskChar * text.length;
  return '${hqMaskChar * (text.length - visible)}${text.substring(text.length - visible)}';
}

/// A PAN as `••••••234F` (last four characters), or empty.
String hqMaskPan(String pan) => hqMaskTail(pan);

/// A bank account number showing its last four digits.
String hqMaskAccount(String account) => hqMaskTail(account);

/// An Aadhaar reference showing only the last four digits (the app must never hold the full number).
String hqMaskAadhaar(String aadhaar) => hqMaskTail(aadhaar.replaceAll(RegExp(r'\s'), ''));

/// Shows a sensitive identifier masked (last four visible). With [revealable] an eye button shows the full value as long as the
/// screen stays; it is a local convenience, the value is only what the server sent. Labels for the button are passed in.
class HqMaskedValue extends StatefulWidget {
  const HqMaskedValue({super.key, required this.value, this.visibleTail = 4, this.revealable = false, this.showLabel = '', this.hideLabel = '', this.style, this.semanticsLabel});

  final String value;
  final int visibleTail;
  final bool revealable;
  final String showLabel;
  final String hideLabel;
  final TextStyle? style;

  /// What a screen reader says instead of the bullets (e.g. "PAN ending 234F" built by the app).
  final String? semanticsLabel;

  @override
  State<HqMaskedValue> createState() => _HqMaskedValueState();
}

class _HqMaskedValueState extends State<HqMaskedValue> {
  bool _revealed = false;

  @override
  Widget build(BuildContext context) {
    final shown = _revealed ? widget.value : hqMaskTail(widget.value, visible: widget.visibleTail);
    final style = widget.style ?? Theme.of(context).textTheme.bodyLarge?.copyWith(fontFeatures: const [FontFeature.tabularFigures()], letterSpacing: 1);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(label: _revealed ? null : widget.semanticsLabel, excludeSemantics: !_revealed && widget.semanticsLabel != null, child: Directionality(textDirection: TextDirection.ltr, child: Text(shown, style: style))),
        if (widget.revealable)
          IconButton(
            tooltip: _revealed ? widget.hideLabel : widget.showLabel,
            icon: Icon(_revealed ? Icons.visibility_off_outlined : Icons.visibility_outlined, size: 20),
            onPressed: () => setState(() => _revealed = !_revealed),
          ),
      ],
    );
  }
}

/// A money amount as text, masked or not (the counterpart of `hqMoney` pipe). Grouping and symbol follow the currency
/// (`₹12,34,567.50` for INR). With [masked] the digits are hidden (a "hide balances" setting); with [colorBySign] a
/// negative amount is red and a positive one green (always with its sign, never colour alone).
class HqMoneyText extends StatelessWidget {
  const HqMoneyText({
    super.key,
    required this.amount,
    this.currency = hqAmountFallbackCurrency,
    this.decimals,
    this.masked = false,
    this.colorBySign = false,
    this.showPlus = false,
    this.style,
    this.fallback = '-',
    this.maskedSemanticsLabel = '',
  });

  /// A `num` or numeric string straight from the API.
  final Object? amount;
  final String currency;
  final int? decimals;
  final bool masked;
  final bool colorBySign;

  /// Prefix positive amounts with `+` (a gain).
  final bool showPlus;
  final TextStyle? style;
  final String fallback;

  /// What a screen reader says for a masked amount (e.g. "Hidden"); already translated.
  final String maskedSemanticsLabel;

  /// The text for [amount] (without masking).
  static String format(Object? amount, {String currency = hqAmountFallbackCurrency, int? decimals, bool showPlus = false, String fallback = '-'}) {
    final text = hqFormatMoney(amount, currency: currency, decimals: decimals, fallback: fallback);
    if (text == fallback) return text;
    return showPlus && !text.startsWith('-') ? '+$text' : text;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final base = style ?? Theme.of(context).textTheme.bodyLarge;
    final text = masked ? '${hqCurrencySymbols[currency.toUpperCase()] ?? currency}$hqMaskChar$hqMaskChar$hqMaskChar$hqMaskChar$hqMaskChar' : format(amount, currency: currency, decimals: decimals, showPlus: showPlus, fallback: fallback);
    Color? color;
    if (colorBySign && !masked) {
      final minor = amount == null ? null : hqParseDecimalToMinor(amount.toString(), decimals: 2);
      if (minor != null && minor != 0) color = minor < 0 ? tokens.warn : tokens.success;
    }
    return Semantics(
      label: masked && maskedSemanticsLabel.isNotEmpty ? maskedSemanticsLabel : null,
      excludeSemantics: masked && maskedSemanticsLabel.isNotEmpty,
      child: Text(text, style: base?.copyWith(color: color, fontFeatures: const [FontFeature.tabularFigures()])),
    );
  }
}
