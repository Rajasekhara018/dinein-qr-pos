import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/hq_config.dart';
import '../theme/hq_theme.dart';
import 'hq_amount.dart';
import 'hq_field.dart';

/// What a [HqAmountInput] reports.
@immutable
class HqAmountValue {
  const HqAmountValue({required this.currency, required this.text, required this.minorUnits});

  /// ISO 4217 code of the amount.
  final String currency;

  /// Plain decimal text without grouping (`1234.50`), empty when nothing is typed. Send this to the API, never the grouped text.
  final String text;

  /// The amount in minor units (paise for INR), null when empty. Money is held as integers, never doubles.
  final int? minorUnits;

  @override
  bool operator ==(Object other) =>
      other is HqAmountValue && other.currency == currency && other.text == text && other.minorUnits == minorUnits;

  @override
  int get hashCode => Object.hash(currency, text, minorUnits);
}

/// Digit grouping of a [HqAmountInput]: [auto] follows the currency (Indian for INR), [indian] is 12,34,567.00,
/// [international] is 1,234,567.00.
enum HqAmountGrouping { auto, indian, international }

/// Money input with paise precision and Indian digit grouping for INR (the counterpart of `hq-amount-input`).
///
/// The currency is [currency], else the app's [HqConfig.defaultCurrency], else INR. With more than one entry in
/// [currencies] a currency picker is shown. [min] and [max] are plain decimal numbers or strings; [minError] and
/// [maxError] are the already-translated messages shown when the value is outside them.
class HqAmountInput extends StatefulWidget {
  const HqAmountInput({
    super.key,
    this.value,
    this.onChanged,
    this.label = '',
    this.showLabel = true,
    this.required = false,
    this.placeholder,
    this.hint,
    this.error,
    this.enabled = true,
    this.currency,
    this.currencies,
    this.decimals,
    this.grouping = HqAmountGrouping.auto,
    this.min = 0,
    this.max,
    this.minError,
    this.maxError,
  });

  /// Plain decimal text to show (`1234.5`); the field follows it when it changes.
  final String? value;
  final ValueChanged<HqAmountValue>? onChanged;
  final String label;
  final bool showLabel;
  final bool required;
  final String? placeholder;
  final String? hint;
  final String? error;
  final bool enabled;
  final String? currency;
  final List<String>? currencies;

  /// Decimal places; defaults to the currency's own and is capped at [hqAmountMaxDecimals].
  final int? decimals;

  /// How the digits are grouped: [HqAmountGrouping.auto] (Indian lakh/crore for INR, thousands otherwise), or forced.
  final HqAmountGrouping grouping;
  final Object? min;
  final Object? max;
  final String? minError;
  final String? maxError;

  @override
  State<HqAmountInput> createState() => _HqAmountInputState();
}

class _HqAmountInputState extends State<HqAmountInput> {
  final TextEditingController _controller = TextEditingController();
  String? _pickedCurrency;
  String? _lastExternal;
  bool _initialized = false;

  String _currency(BuildContext context) =>
      (_pickedCurrency ?? widget.currency ?? HqConfigScope.maybeOf(context)?.defaultCurrency ?? hqAmountFallbackCurrency).toUpperCase();

  int _decimals(String currency) => (widget.decimals ?? hqCurrencyDecimals(currency)).clamp(0, hqAmountMaxDecimals);

  HqAmountFormatter _formatter(String currency) =>
      HqAmountFormatter(decimals: _decimals(currency), indian: _indian(currency));

  bool _indian(String currency) => switch (widget.grouping) {
        HqAmountGrouping.indian => true,
        HqAmountGrouping.international => false,
        HqAmountGrouping.auto => hqUsesIndianGrouping(currency),
      };

  String _format(String currency, String plain) =>
      _formatter(currency).formatEditUpdate(TextEditingValue.empty, TextEditingValue(text: plain)).text;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_initialized) {
      _initialized = true;
      _syncFromValue();
    }
  }

  @override
  void didUpdateWidget(HqAmountInput oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value) _syncFromValue();
  }

  void _syncFromValue() {
    final external = widget.value ?? '';
    // The parent echoing what the user just typed must not rewrite the field (and move the cursor).
    if (external == _lastExternal) return;
    _lastExternal = external;
    final next = external.isEmpty ? '' : _format(_currency(context), external);
    if (next != _controller.text) _controller.text = next;
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  String _plain(String formatted) {
    final plain = hqNormalizeAmountText(formatted);
    return plain.endsWith('.') ? plain.substring(0, plain.length - 1) : plain;
  }

  void _emit(String currency, String formatted) {
    final plain = _plain(formatted);
    _lastExternal = plain;
    final minor = plain.isEmpty ? null : hqParseDecimalToMinor(plain, decimals: _decimals(currency));
    widget.onChanged?.call(HqAmountValue(currency: currency, text: plain, minorUnits: minor));
  }

  String? _rangeError(String currency) {
    final decimals = _decimals(currency);
    final minor = hqParseDecimalToMinor(_plain(_controller.text), decimals: decimals);
    if (minor == null) return null;
    final min = widget.min == null ? null : hqParseDecimalToMinor(widget.min.toString(), decimals: decimals);
    final max = widget.max == null ? null : hqParseDecimalToMinor(widget.max.toString(), decimals: decimals);
    if (min != null && minor < min) return widget.minError;
    if (max != null && minor > max) return widget.maxError;
    return null;
  }

  Widget _prefix(BuildContext context, String currency) {
    final tokens = context.hq;
    final choices = widget.currencies;
    if (choices == null || choices.length < 2) {
      return Text(hqCurrencySymbols[currency] ?? currency, style: TextStyle(color: tokens.muted, fontWeight: FontWeight.w600));
    }
    return DropdownButtonHideUnderline(
      child: DropdownButton<String>(
        value: choices.contains(currency) ? currency : null,
        isDense: true,
        items: [for (final c in choices) DropdownMenuItem<String>(value: c, child: Text(c))],
        onChanged: widget.enabled
            ? (next) {
                if (next == null) return;
                setState(() => _pickedCurrency = next);
                final reformatted = _format(next, hqNormalizeAmountText(_controller.text));
                _controller.text = reformatted;
                _emit(next, reformatted);
              }
            : null,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final currency = _currency(context);
    return HqField(
      controller: _controller,
      label: widget.label,
      showLabel: widget.showLabel,
      required: widget.required,
      placeholder: widget.placeholder,
      hint: widget.hint,
      error: widget.error ?? _rangeError(currency),
      enabled: widget.enabled,
      type: HqFieldType.number,
      leading: Padding(padding: const EdgeInsetsDirectional.symmetric(horizontal: 12), child: _prefix(context, currency)),
      inputFormatters: <TextInputFormatter>[_formatter(currency)],
      onChanged: (text) {
        setState(() {});
        _emit(currency, text);
      },
    );
  }
}
