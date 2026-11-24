import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Label, hint and error around any control (the counterpart of `hq-form-field`).
///
/// The label is shown above the control with a `*` when [required]; the [error] replaces the [hint] when set.
class HqFormField extends StatelessWidget {
  const HqFormField({
    super.key,
    required this.child,
    this.label = '',
    this.hint,
    this.error,
    this.required = false,
    this.inline = false,
  });

  final Widget child;
  final String label;
  final String? hint;
  final String? error;
  final bool required;

  /// Label beside the control instead of above it.
  final bool inline;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final labelWidget = label.isEmpty
        ? null
        : Text.rich(
            TextSpan(children: [
              TextSpan(text: label),
              if (required) TextSpan(text: ' *', style: TextStyle(color: tokens.warn)),
            ]),
            style: Theme.of(context).textTheme.labelLarge?.copyWith(color: tokens.ink, fontWeight: FontWeight.w600),
          );
    final message = (error != null && error!.isNotEmpty) ? error : hint;
    final isError = error != null && error!.isNotEmpty;
    final messageWidget = (message == null || message.isEmpty)
        ? null
        : Padding(
            padding: const EdgeInsets.only(top: HqSpacing.xs),
            child: Text(
              message,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(color: isError ? tokens.warn : tokens.muted),
            ),
          );
    if (inline) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (labelWidget != null) ...[labelWidget, const SizedBox(width: HqSpacing.md)],
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [child, if (messageWidget != null) messageWidget]),
          ),
        ],
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (labelWidget != null) Padding(padding: const EdgeInsets.only(bottom: HqSpacing.xs), child: labelWidget),
        child,
        if (messageWidget != null) messageWidget,
      ],
    );
  }
}
