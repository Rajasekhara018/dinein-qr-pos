import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Checkbox sizes (the web `HqCheckboxSize`).
enum HqCheckboxSize { compact, regular }

/// A checkbox with its label; tapping the label toggles it (the counterpart of `hq-checkbox`).
///
/// [onChanged] receives the new value. A null [onChanged] or `enabled: false` disables it. With [indeterminate] the box
/// shows a dash until the user taps it.
class HqCheckbox extends StatelessWidget {
  const HqCheckbox({
    super.key,
    required this.checked,
    this.onChanged,
    this.label,
    this.indeterminate = false,
    this.enabled = true,
    this.required = false,
    this.error,
    this.size = HqCheckboxSize.regular,
    this.semanticLabel,
  });

  final bool checked;
  final ValueChanged<bool>? onChanged;
  final String? label;
  final bool indeterminate;
  final bool enabled;
  final bool required;
  final String? error;
  final HqCheckboxSize size;
  final String? semanticLabel;

  bool get _active => enabled && onChanged != null;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final compact = size == HqCheckboxSize.compact;
    final hasError = error != null && error!.isNotEmpty;
    final box = SizedBox(
      width: compact ? 32 : 40,
      height: compact ? 32 : 40,
      child: Checkbox(
        value: indeterminate ? null : checked,
        tristate: indeterminate,
        activeColor: tokens.primary,
        checkColor: tokens.onPrimary,
        side: BorderSide(color: hasError ? tokens.warn : tokens.muted, width: 1.5),
        // Rounded corners, the mobile checkbox.
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(6))),
        splashRadius: 20,
        onChanged: _active ? (_) => onChanged!(!checked) : null,
      ),
    );
    final labelText = label == null
        ? null
        : Expanded(
            child: Text.rich(
              TextSpan(children: [
                TextSpan(text: label),
                if (required) TextSpan(text: ' *', style: TextStyle(color: tokens.warn)),
              ]),
              style: (compact ? text.bodySmall : text.bodyMedium)?.copyWith(color: _active ? tokens.ink : tokens.muted),
            ),
          );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          container: true,
          checked: checked,
          mixed: indeterminate,
          enabled: _active,
          label: semanticLabel ?? label,
          excludeSemantics: true,
          onTap: _active ? () => onChanged!(!checked) : null,
          child: InkWell(
            borderRadius: BorderRadius.circular(HqRadius.sm),
            onTap: _active ? () => onChanged!(!checked) : null,
            child: Row(children: [box, if (labelText != null) labelText]),
          ),
        ),
        if (hasError)
          Padding(
            padding: const EdgeInsets.only(left: HqSpacing.sm),
            child: Text(error!, style: text.bodySmall?.copyWith(color: tokens.warn)),
          ),
      ],
    );
  }
}
