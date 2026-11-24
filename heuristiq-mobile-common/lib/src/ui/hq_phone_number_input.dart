import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/hq_config.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_country_data.dart';
import 'hq_country_picker.dart';
import 'hq_field.dart';
import 'hq_flag.dart';

export 'hq_country_data.dart' show HqPhoneCountry, HqCountryData;
export 'hq_country_picker.dart';
export 'hq_flag.dart';

/// Longest phone number E.164 allows, calling code included (`HQ_PHONE_MAX_E164_DIGITS`).
const int hqPhoneMaxE164Digits = 15;

/// Country a number typed without a country code is read in, until the app provides one (`HQ_PHONE_FALLBACK_COUNTRY`).
const String hqPhoneFallbackCountry = 'IN';

/// Countries offered by default: every ISO 3166 country and territory (see [HqCountryData]), sorted by English name.
List<HqPhoneCountry> get hqPhoneCountries => HqCountryData.all;

/// What a [HqPhoneNumberInput] reports.
@immutable
class HqPhoneValue {
  const HqPhoneValue({required this.countryIso, required this.dialCode, required this.national, required this.e164});

  final String countryIso;
  final String dialCode;

  /// Digits typed after the country code.
  final String national;

  /// `+<code><national>` when the number is valid, otherwise null.
  final String? e164;

  bool get isValid => e164 != null;

  @override
  bool operator ==(Object other) =>
      other is HqPhoneValue && other.countryIso == countryIso && other.national == national && other.e164 == e164;

  @override
  int get hashCode => Object.hash(countryIso, national, e164);
}

/// True when [national] (digits only, no trunk prefix) is a valid number for [country]: total digits within E.164's 15,
/// then the country's pattern (India `[6-9]` + 9 digits, NANP `[2-9]` + 9 digits) or its length range.
bool hqIsValidPhone(String national, HqPhoneCountry country) {
  if (!RegExp(r'^\d+$').hasMatch(national)) return false;
  if (country.dialCode.length + national.length > hqPhoneMaxE164Digits) return false;
  final pattern = country.pattern;
  if (pattern != null) return RegExp(pattern).hasMatch(national);
  return national.length >= country.nationalMin && national.length <= country.nationalMax;
}

/// Builds the value for [rawNational] typed in [country]: non-digits are dropped and the trunk-prefix 0 is removed (except
/// for countries where it belongs to the number, such as Italy).
HqPhoneValue hqBuildPhoneValue(HqPhoneCountry country, String rawNational) {
  var digits = rawNational.replaceAll(RegExp(r'\D'), '');
  if (!country.keepsLeadingZero && digits.startsWith('0')) digits = digits.replaceFirst(RegExp(r'^0+'), '');
  final valid = digits.isNotEmpty && hqIsValidPhone(digits, country);
  return HqPhoneValue(
    countryIso: country.iso,
    dialCode: country.dialCode,
    national: digits,
    e164: valid ? '+${country.dialCode}$digits' : null,
  );
}

/// Phone field with a searchable country picker (the counterpart of `hq-phone-number-input`).
///
/// The starting country is [countryIso], else the app's [HqConfig.defaultPhoneCountry], else India. The picker lists
/// [preferredCountries] first. Country names are English unless [countryNames] (iso to localized name) is given. Every text
/// ([invalidError], [searchHint], [noResultsText], [pickerTitle], [closeLabel]) comes from the caller.
class HqPhoneNumberInput extends StatefulWidget {
  const HqPhoneNumberInput({
    super.key,
    this.value,
    this.onChanged,
    this.label = '',
    this.showLabel = true,
    this.required = false,
    this.placeholder,
    this.hint,
    this.error,
    this.invalidError,
    this.enabled = true,
    this.countryIso,
    this.countries,
    this.preferredCountries = const <String>['IN'],
    this.countryNames,
    this.pickerTitle = '',
    this.searchHint = '',
    this.noResultsText = '',
    this.closeLabel = '',
    this.flagStyle = HqFlagStyle.auto,
    this.flagBuilder,
    this.controller,
    this.validator,
    this.textInputAction,
    this.onSubmitted,
    this.focusNode,
    this.fieldKey,
  });

  /// National digits to show.
  final String? value;
  final ValueChanged<HqPhoneValue>? onChanged;
  final String label;
  final bool showLabel;
  final bool required;
  final String? placeholder;
  final String? hint;
  final String? error;
  final String? invalidError;
  final bool enabled;
  final String? countryIso;

  /// Limits the list (the web `allowedCountries`); null offers every country.
  final List<HqPhoneCountry>? countries;

  /// ISO codes pinned at the top of the picker, in order.
  final List<String> preferredCountries;

  /// Localized names by ISO code; missing entries fall back to English.
  final Map<String, String>? countryNames;
  final String pickerTitle;
  final String searchHint;
  final String noResultsText;
  final String closeLabel;
  final HqFlagStyle flagStyle;

  /// Replaces the flag in the field and the list (to show real flag images).
  final Widget Function(String iso)? flagBuilder;

  /// Holds the national digits; when given, the caller owns it and [value] is ignored. Lets a form read or reset the
  /// number without listening to [onChanged].
  final TextEditingController? controller;

  /// Validates the number inside an enclosing `Form`: gets the value parsed in the selected country and returns the
  /// already translated message, or null when valid.
  final String? Function(HqPhoneValue value)? validator;

  final TextInputAction? textInputAction;
  final ValueChanged<String>? onSubmitted;
  final FocusNode? focusNode;

  /// Key of the inner text field (see [HqField.fieldKey]).
  final Key? fieldKey;

  @override
  State<HqPhoneNumberInput> createState() => _HqPhoneNumberInputState();
}

class _HqPhoneNumberInputState extends State<HqPhoneNumberInput> {
  final TextEditingController _own = TextEditingController();

  TextEditingController get _controller => widget.controller ?? _own;

  /// Redraws the invalid state when the caller changes its [HqPhoneNumberInput.controller] in code.
  void _onExternalChange() {
    if (mounted) setState(() {});
  }

  @override
  void initState() {
    super.initState();
    widget.controller?.addListener(_onExternalChange);
  }
  String? _pickedIso;
  bool _initialized = false;

  List<HqPhoneCountry> get _countries => widget.countries ?? HqCountryData.all;

  HqPhoneCountry _country(BuildContext context) {
    final iso = (_pickedIso ?? widget.countryIso ?? HqConfigScope.maybeOf(context)?.defaultPhoneCountry ?? hqPhoneFallbackCountry).toUpperCase();
    return _countries.firstWhere((c) => c.iso == iso, orElse: () => _countries.first);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_initialized) {
      _initialized = true;
      if (widget.controller == null) _own.text = widget.value ?? '';
    }
  }

  @override
  void didUpdateWidget(HqPhoneNumberInput oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.controller, widget.controller)) {
      oldWidget.controller?.removeListener(_onExternalChange);
      widget.controller?.addListener(_onExternalChange);
    }
    final next = widget.value;
    if (widget.controller == null && oldWidget.value != next && next != null && next != _own.text) _own.text = next;
  }

  @override
  void dispose() {
    widget.controller?.removeListener(_onExternalChange);
    _own.dispose();
    super.dispose();
  }

  Future<void> _pick(HqPhoneCountry current) async {
    final iso = await HqCountryPicker.show(
      context,
      countries: _countries,
      selectedIso: current.iso,
      preferred: widget.preferredCountries,
      countryNames: widget.countryNames,
      title: widget.pickerTitle,
      searchHint: widget.searchHint,
      noResultsText: widget.noResultsText,
      closeLabel: widget.closeLabel,
      flagStyle: widget.flagStyle,
    );
    if (iso == null || !mounted) return;
    setState(() => _pickedIso = iso);
    final picked = _countries.firstWhere((c) => c.iso == iso);
    widget.onChanged?.call(hqBuildPhoneValue(picked, _controller.text));
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final country = _country(context);
    final typed = _controller.text.replaceAll(RegExp(r'\D'), '');
    final invalid = typed.isNotEmpty && !hqBuildPhoneValue(country, typed).isValid;
    final name = widget.countryNames?[country.iso] ?? country.name;
    final trigger = Semantics(
      button: true,
      enabled: widget.enabled,
      label: '$name ${country.dialDisplay}',
      excludeSemantics: true,
      onTap: widget.enabled ? () => _pick(country) : null,
      child: InkWell(
        key: const ValueKey<String>('hq-phone-country'),
        borderRadius: BorderRadius.circular(HqRadius.sm),
        onTap: widget.enabled ? () => _pick(country) : null,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: HqSizes.minTapTarget - 8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              widget.flagBuilder?.call(country.iso) ?? HqFlag(iso: country.iso, style: widget.flagStyle, size: 20),
              const SizedBox(width: HqSpacing.xs),
              Directionality(textDirection: TextDirection.ltr, child: Text(country.dialDisplay, style: text.bodyLarge)),
              Icon(Icons.arrow_drop_down, color: tokens.muted),
            ],
          ),
        ),
      ),
    );
    return HqField(
      controller: _controller,
      label: widget.label,
      showLabel: widget.showLabel,
      required: widget.required,
      placeholder: widget.placeholder,
      hint: widget.hint,
      error: widget.error ?? (invalid ? widget.invalidError : null),
      invalid: invalid,
      enabled: widget.enabled,
      type: HqFieldType.phone,
      maxLength: hqPhoneMaxE164Digits,
      inputFormatters: <TextInputFormatter>[FilteringTextInputFormatter.digitsOnly],
      autofillHints: const <String>[AutofillHints.telephoneNumberNational],
      leading: Padding(padding: const EdgeInsetsDirectional.only(start: 8, end: 8), child: trigger),
      fieldKey: widget.fieldKey,
      focusNode: widget.focusNode,
      textInputAction: widget.textInputAction,
      onSubmitted: widget.onSubmitted,
      validator: widget.validator == null ? null : (text) => widget.validator!(hqBuildPhoneValue(country, text ?? '')),
      onChanged: (value) {
        setState(() {});
        widget.onChanged?.call(hqBuildPhoneValue(country, value));
      },
    );
  }
}
