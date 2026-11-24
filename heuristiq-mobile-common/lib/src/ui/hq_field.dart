import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme/hq_mobile_finish.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_form_field.dart';

/// Kind of text a [HqField] edits (the web `HqFieldType`).
enum HqFieldType { text, email, password, number, phone, url, multiline }

/// The text input: label above, placeholder, hint, error, counter, password toggle (the counterpart of `hq-field`).
///
/// Give either a [controller] (you own it) or a [value] (the field owns an internal controller and follows [value]).
/// Every text is a parameter; nothing is translated here.
///
/// Inside a `Form`, give a [validator]: the field then takes part in `FormState.validate()` and shows the message it
/// returns (an explicit [error] still shows while the validator passes). Without one it is a plain text field, as before.
class HqField extends StatefulWidget {
  const HqField({
    super.key,
    this.controller,
    this.value,
    this.onChanged,
    this.onSubmitted,
    this.label = '',
    this.showLabel = true,
    this.required = false,
    this.placeholder,
    this.hint,
    this.error,
    this.invalid = false,
    this.maxLength,
    this.showCounter = false,
    this.readOnly = false,
    this.enabled = true,
    this.type = HqFieldType.text,
    this.icon,
    this.rows,
    this.maxRows,
    this.inputFormatters,
    this.textInputAction,
    this.autofillHints,
    this.focusNode,
    this.passwordToggle = true,
    this.passwordShowLabel,
    this.passwordHideLabel,
    this.autofocus = false,
    this.textCapitalization = TextCapitalization.none,
    this.prefixText,
    this.prefix,
    this.leading,
    this.suffix,
    this.trailing,
    this.validator,
    this.autovalidateMode,
    this.onTap,
    this.fieldKey,
  });

  final TextEditingController? controller;
  final String? value;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;
  final String label;
  final bool showLabel;
  final bool required;
  final String? placeholder;
  final String? hint;
  final String? error;

  /// Red border without a message (the message is shown elsewhere, e.g. by a [HqFormField]).
  final bool invalid;
  final int? maxLength;
  final bool showCounter;
  final bool readOnly;
  final bool enabled;
  final HqFieldType type;
  final IconData? icon;
  final int? rows;
  final int? maxRows;
  final List<TextInputFormatter>? inputFormatters;
  final TextInputAction? textInputAction;
  final Iterable<String>? autofillHints;
  final FocusNode? focusNode;
  final bool passwordToggle;

  /// Tooltips of the show/hide password button (already translated).
  final String? passwordShowLabel;
  final String? passwordHideLabel;
  final bool autofocus;
  final TextCapitalization textCapitalization;
  final String? prefixText;

  /// Widget shown in front of the text (a currency or country picker); not used together with [icon].
  final Widget? prefix;

  /// Widget always shown at the start of the field (a currency or country picker); replaces [icon].
  final Widget? leading;
  final Widget? suffix;

  /// Widget always shown at the end of the field, even while it is empty (e.g. a button that opens a list of
  /// suggestions). Not used together with the password show/hide toggle.
  final Widget? trailing;

  /// Validates the text inside an enclosing `Form`; returns the (already translated) message, or null when valid.
  final FormFieldValidator<String>? validator;

  /// When the [validator] runs on its own (defaults to `Form`'s setting).
  final AutovalidateMode? autovalidateMode;

  /// Called when the field is tapped (e.g. a [readOnly] field that opens a picker or a search screen).
  final GestureTapCallback? onTap;

  /// Key of the inner text field (a `TextField`, or a `TextFormField` when a [validator] is given), for tests and
  /// `Scrollable.ensureVisible`.
  final Key? fieldKey;

  @override
  State<HqField> createState() => _HqFieldState();
}

class _HqFieldState extends State<HqField> {
  late final TextEditingController _own = TextEditingController(text: widget.value ?? '');
  bool _obscured = true;

  TextEditingController get _controller => widget.controller ?? _own;

  @override
  void didUpdateWidget(HqField oldWidget) {
    super.didUpdateWidget(oldWidget);
    final value = widget.value;
    if (widget.controller == null && value != null && value != _own.text) {
      _own.value = TextEditingValue(text: value, selection: TextSelection.collapsed(offset: value.length));
    }
  }

  @override
  void dispose() {
    _own.dispose();
    super.dispose();
  }

  TextInputType get _keyboard => switch (widget.type) {
        HqFieldType.email => TextInputType.emailAddress,
        HqFieldType.number => const TextInputType.numberWithOptions(decimal: true),
        HqFieldType.phone => TextInputType.phone,
        HqFieldType.url => TextInputType.url,
        HqFieldType.multiline => TextInputType.multiline,
        _ => TextInputType.text,
      };

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final isPassword = widget.type == HqFieldType.password;
    final multiline = widget.type == HqFieldType.multiline;
    final hasError = widget.error != null && widget.error!.isNotEmpty;
    // The mobile finish: a filled field with no border at rest, a primary ring when focused, a warn ring on error.
    final decoration = HqMobile.filled(context, InputDecoration(
      hintText: widget.placeholder,
      helperText: hasError ? null : widget.hint,
      errorText: hasError ? widget.error : null,
      counterText: widget.showCounter ? null : '',
      prefixIcon: widget.leading ?? (widget.icon == null ? null : Padding(padding: const EdgeInsets.only(left: HqSpacing.xs), child: Icon(widget.icon, color: tokens.muted, size: 20))),
      prefixIconConstraints: widget.leading == null ? null : const BoxConstraints(minWidth: 0, minHeight: 0),
      prefixText: widget.prefixText,
      prefix: widget.prefix,
      suffix: widget.suffix,
      suffixIcon: widget.trailing ?? (isPassword && widget.passwordToggle
          ? IconButton(
              tooltip: _obscured ? widget.passwordShowLabel : widget.passwordHideLabel,
              icon: Icon(_obscured ? Icons.visibility_outlined : Icons.visibility_off_outlined),
              onPressed: () => setState(() => _obscured = !_obscured),
            )
          : null),
    ), invalid: widget.invalid && !hasError);
    final minLines = multiline ? (widget.rows ?? 3) : 1;
    final maxLines = isPassword ? 1 : (multiline ? (widget.maxRows ?? widget.rows ?? 3) : 1);
    final Widget field = widget.validator == null
        ? TextField(
            key: widget.fieldKey,
            controller: _controller,
            focusNode: widget.focusNode,
            onChanged: widget.onChanged,
            onSubmitted: widget.onSubmitted,
            onTap: widget.onTap,
            decoration: decoration,
            obscureText: isPassword && _obscured,
            enabled: widget.enabled,
            readOnly: widget.readOnly,
            autofocus: widget.autofocus,
            maxLength: widget.maxLength,
            keyboardType: _keyboard,
            textInputAction: widget.textInputAction,
            textCapitalization: widget.textCapitalization,
            autofillHints: widget.autofillHints,
            inputFormatters: widget.inputFormatters,
            minLines: minLines,
            maxLines: maxLines,
          )
        : TextFormField(
            key: widget.fieldKey,
            controller: _controller,
            focusNode: widget.focusNode,
            onChanged: widget.onChanged,
            onFieldSubmitted: widget.onSubmitted,
            onTap: widget.onTap,
            validator: widget.validator,
            autovalidateMode: widget.autovalidateMode,
            decoration: decoration,
            obscureText: isPassword && _obscured,
            enabled: widget.enabled,
            readOnly: widget.readOnly,
            autofocus: widget.autofocus,
            maxLength: widget.maxLength,
            keyboardType: _keyboard,
            textInputAction: widget.textInputAction,
            textCapitalization: widget.textCapitalization,
            autofillHints: widget.autofillHints,
            inputFormatters: widget.inputFormatters,
            minLines: minLines,
            maxLines: maxLines,
          );
    if (!widget.showLabel || widget.label.isEmpty) return field;
    return HqFormField(label: widget.label, required: widget.required, child: Semantics(label: widget.label, child: field));
  }
}
