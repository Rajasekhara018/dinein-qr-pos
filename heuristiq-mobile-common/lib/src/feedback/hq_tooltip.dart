import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// A tooltip that appears on long press (touch) or hover, styled from the theme tokens (the counterpart of `hqTooltip`).
/// [message] is read by screen readers as the hint of the child. A blank message or [disabled] shows the child alone.
class HqTooltip extends StatelessWidget {
  const HqTooltip({super.key, required this.message, required this.child, this.disabled = false, this.preferBelow = true, this.showDelay = const Duration(milliseconds: 400)});

  final String message;
  final Widget child;
  final bool disabled;
  final bool preferBelow;
  final Duration showDelay;

  @override
  Widget build(BuildContext context) {
    if (disabled || message.trim().isEmpty) return child;
    final tokens = context.hq;
    return Tooltip(
      message: message,
      preferBelow: preferBelow,
      waitDuration: showDelay,
      triggerMode: TooltipTriggerMode.longPress,
      decoration: BoxDecoration(color: tokens.ink, borderRadius: BorderRadius.circular(HqRadius.sm)),
      textStyle: Theme.of(context).textTheme.bodySmall?.copyWith(color: tokens.surface),
      child: child,
    );
  }
}
