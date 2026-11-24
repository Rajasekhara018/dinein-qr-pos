import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Tone of a [HqAlert]: the five status tones plus [decisionPending], a placeholder for a legal or product decision that has
/// not been made yet (so a screen can say so instead of guessing; see `END_TO_END_FLOW.md` "Open decisions").
enum HqAlertTone { info, success, warning, danger, neutral, decisionPending }

/// A message banner with an icon, optional title and body, an optional action and a dismiss button (the counterpart of
/// `hq-alert`). The tone always comes with an icon, never colour alone. Announced to screen readers as a live region.
class HqAlert extends StatelessWidget {
  const HqAlert({
    super.key,
    required this.message,
    this.title = '',
    this.tone = HqAlertTone.info,
    this.icon,
    this.dismissible = false,
    this.onDismissed,
    this.dismissLabel = '',
    this.action,
    this.dense = false,
  });

  /// Body text (already translated).
  final String message;
  final String title;
  final HqAlertTone tone;
  final IconData? icon;
  final bool dismissible;
  final VoidCallback? onDismissed;

  /// Tooltip and semantics of the close button.
  final String dismissLabel;

  /// A button or link shown under the message (use a `HqButton` with text appearance).
  final Widget? action;
  final bool dense;

  static IconData defaultIcon(HqAlertTone tone) => switch (tone) {
        HqAlertTone.info => Icons.info_outline,
        HqAlertTone.success => Icons.check_circle_outline,
        HqAlertTone.warning => Icons.warning_amber_outlined,
        HqAlertTone.danger => Icons.error_outline,
        HqAlertTone.neutral => Icons.chat_bubble_outline,
        HqAlertTone.decisionPending => Icons.hourglass_empty,
      };

  HqToneColors _colors(HqTokens t) => switch (tone) {
        HqAlertTone.info => t.toneInfo,
        HqAlertTone.success => t.toneSuccess,
        HqAlertTone.warning => t.toneWarning,
        HqAlertTone.danger => t.toneDanger,
        HqAlertTone.neutral => t.toneNeutral,
        HqAlertTone.decisionPending => t.toneWarning,
      };

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final colors = _colors(tokens);
    final text = Theme.of(context).textTheme;
    final pad = dense ? HqSpacing.sm : HqSpacing.md;
    return Semantics(
      container: true,
      liveRegion: true,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: colors.background,
          border: Border.all(color: colors.border, width: tone == HqAlertTone.decisionPending ? 2 : 1),
          borderRadius: BorderRadius.circular(HqRadius.control),
        ),
        child: Padding(
          padding: EdgeInsets.all(pad),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon ?? defaultIcon(tone), color: colors.foreground, size: dense ? 18 : 22),
              SizedBox(width: pad),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (title.isNotEmpty) Text(title, style: text.titleSmall?.copyWith(color: colors.foreground, fontWeight: FontWeight.w700)),
                    Text(message, style: text.bodyMedium?.copyWith(color: colors.foreground)),
                    if (action != null) Padding(padding: const EdgeInsets.only(top: HqSpacing.sm), child: action),
                  ],
                ),
              ),
              if (dismissible)
                InkResponse(
                  onTap: onDismissed,
                  radius: 20,
                  child: Semantics(
                    button: true,
                    label: dismissLabel,
                    child: Padding(padding: const EdgeInsets.all(2), child: Icon(Icons.close, size: 18, color: colors.foreground)),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// A [HqAlert] meant to sit at the top of a screen; same widget, named for what it is used for.
class HqBanner extends StatelessWidget {
  const HqBanner({
    super.key,
    required this.message,
    this.title = '',
    this.tone = HqAlertTone.info,
    this.dismissible = true,
    this.onDismissed,
    this.dismissLabel = '',
    this.action,
  });

  final String message;
  final String title;
  final HqAlertTone tone;
  final bool dismissible;
  final VoidCallback? onDismissed;
  final String dismissLabel;
  final Widget? action;

  @override
  Widget build(BuildContext context) => HqAlert(
        message: message,
        title: title,
        tone: tone,
        dismissible: dismissible,
        onDismissed: onDismissed,
        dismissLabel: dismissLabel,
        action: action,
      );
}
