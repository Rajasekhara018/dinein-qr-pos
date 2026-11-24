import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Badge sizes (the web `sm` / `md`).
enum HqStatusBadgeSize { sm, md }

/// A small pill showing a status in a tone colour (the counterpart of `hq-status-badge`).
///
/// [label] is the already-translated text; map the backend status code to a [HqStatusTone] in the screen's constants file.
class HqStatusBadge extends StatelessWidget {
  const HqStatusBadge({
    super.key,
    required this.label,
    this.tone = HqStatusTone.neutral,
    this.icon,
    this.size = HqStatusBadgeSize.md,
  });

  final String label;
  final HqStatusTone tone;
  final IconData? icon;
  final HqStatusBadgeSize size;

  @override
  Widget build(BuildContext context) {
    final colors = context.hq.tone(tone);
    final small = size == HqStatusBadgeSize.sm;
    final style = Theme.of(context).textTheme.labelSmall?.copyWith(
          color: colors.foreground,
          fontWeight: FontWeight.w600,
          fontSize: small ? 11 : 12.5,
        );
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.background,
        // A soft pill: the tone fill with only a hairline of its border.
        border: Border.all(color: colors.border.withValues(alpha: 0.45), width: 0.8),
        borderRadius: BorderRadius.circular(HqRadius.pill),
      ),
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: small ? 8 : 10, vertical: small ? 3 : 6),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (icon != null)
              ...[Icon(icon, size: small ? 12 : 14, color: colors.foreground), const SizedBox(width: 4)]
            else
              ...[
                // A small dot in the tone colour, the mobile status idiom.
                Container(width: small ? 5 : 6, height: small ? 5 : 6, decoration: BoxDecoration(color: colors.foreground, shape: BoxShape.circle)),
                SizedBox(width: small ? 5 : 6),
              ],
            Flexible(child: Text(label, style: style, maxLines: 1, overflow: TextOverflow.ellipsis)),
          ],
        ),
      ),
    );
  }
}
