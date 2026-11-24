import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// One choice of a [HqRadioGroup].
@immutable
class HqRadioOption<T> {
  const HqRadioOption({required this.value, required this.label, this.hint, this.disabled = false});

  final T value;
  final String label;
  final String? hint;
  final bool disabled;
}

/// A group of mutually exclusive choices (the counterpart of `hq-radio-group`).
///
/// Drawn with plain icons and explicit semantics so it does not depend on the Material `Radio` API, which changed between
/// Flutter releases.
class HqRadioGroup<T> extends StatelessWidget {
  const HqRadioGroup({
    super.key,
    required this.options,
    required this.value,
    required this.onChanged,
    this.label = '',
    this.required = false,
    this.enabled = true,
    this.direction = Axis.vertical,
    this.error,
  });

  final List<HqRadioOption<T>> options;
  final T? value;
  final ValueChanged<T>? onChanged;

  /// Group heading (already translated).
  final String label;
  final bool required;
  final bool enabled;
  final Axis direction;
  final String? error;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final items = [for (final option in options) _item(context, option)];
    return Semantics(
      container: true,
      label: label.isEmpty ? null : label,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (label.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(bottom: HqSpacing.xs),
              child: Text.rich(
                TextSpan(children: [
                  TextSpan(text: label),
                  if (required) TextSpan(text: ' *', style: TextStyle(color: tokens.warn)),
                ]),
                style: text.labelLarge?.copyWith(fontWeight: FontWeight.w600),
              ),
            ),
          direction == Axis.vertical ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: items) : Wrap(spacing: HqSpacing.lg, children: items),
          if (error != null && error!.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: HqSpacing.xs),
              child: Text(error!, style: text.bodySmall?.copyWith(color: tokens.warn)),
            ),
        ],
      ),
    );
  }

  Widget _item(BuildContext context, HqRadioOption<T> option) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final selected = option.value == value;
    final active = enabled && !option.disabled && onChanged != null;
    final color = !active ? tokens.muted.withValues(alpha: 0.6) : (selected ? tokens.primary : tokens.muted);
    return Semantics(
      container: true,
      inMutuallyExclusiveGroup: true,
      checked: selected,
      enabled: active,
      label: option.label,
      excludeSemantics: true,
      onTap: active ? () => onChanged!(option.value) : null,
      child: InkWell(
        borderRadius: BorderRadius.circular(HqRadius.sm),
        onTap: active ? () => onChanged!(option.value) : null,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: HqSizes.minTapTarget),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: HqSpacing.sm),
                child: Icon(selected ? Icons.radio_button_checked : Icons.radio_button_unchecked, color: color),
              ),
              Flexible(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(option.label, style: text.bodyMedium?.copyWith(color: active ? tokens.ink : tokens.muted)),
                    if (option.hint != null) Text(option.hint!, style: text.bodySmall?.copyWith(color: tokens.muted)),
                  ],
                ),
              ),
              const SizedBox(width: HqSpacing.sm),
            ],
          ),
        ),
      ),
    );
  }
}
