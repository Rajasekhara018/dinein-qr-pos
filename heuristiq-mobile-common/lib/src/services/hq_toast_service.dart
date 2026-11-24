import 'package:flutter/material.dart';

import '../theme/hq_mobile_finish.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Kind of toast (the web `HqToastType`).
enum HqToastType { success, error, warning, info }

/// Raises toasts as floating snack bars: the counterpart of `HqNotificationService` + `hq-toast-host`.
///
/// Create one for the app and give its [messengerKey] to `MaterialApp.scaffoldMessengerKey`; after that any layer can call
/// [success], [error], [warning] or [info] without a `BuildContext`. Default durations match the web: 3 s, 5 s, 4 s, 3 s.
/// A button inside a toast (e.g. "Undo"). Tapping it closes the toast, then runs [onPressed].
class HqToastAction {
  const HqToastAction({required this.label, required this.onPressed});

  final String label;
  final VoidCallback onPressed;
}

class HqToastService {
  HqToastService({GlobalKey<ScaffoldMessengerState>? messengerKey})
      : messengerKey = messengerKey ?? GlobalKey<ScaffoldMessengerState>();

  final GlobalKey<ScaffoldMessengerState> messengerKey;

  void success(String message, {Duration duration = const Duration(seconds: 3), HqToastAction? action, String? closeLabel}) =>
      show(HqToastType.success, message, duration, action: action, closeLabel: closeLabel);

  void error(String message, {Duration duration = const Duration(seconds: 5), HqToastAction? action, String? closeLabel}) =>
      show(HqToastType.error, message, duration, action: action, closeLabel: closeLabel);

  void warning(String message, {Duration duration = const Duration(seconds: 4), HqToastAction? action, String? closeLabel}) =>
      show(HqToastType.warning, message, duration, action: action, closeLabel: closeLabel);

  void info(String message, {Duration duration = const Duration(seconds: 3), HqToastAction? action, String? closeLabel}) =>
      show(HqToastType.info, message, duration, action: action, closeLabel: closeLabel);

  /// Shows [message] replacing any toast on screen. Does nothing before the messenger is mounted.
  ///
  /// An [action] adds a button (e.g. "Undo"); [closeLabel] adds a close button announced with that text. A toast with an
  /// action still closes by itself after [duration] (unlike a plain SnackBar with an action, which would stay forever).
  void show(HqToastType type, String message, Duration duration, {HqToastAction? action, String? closeLabel}) =>
      showOn(messengerKey.currentState, type, message, duration, action: action, closeLabel: closeLabel);

  /// The same toast on a given [messenger] (e.g. the nearest `ScaffoldMessenger.of(context)`, or one captured before an
  /// `await`), for apps that raise toasts from a `BuildContext`. Does nothing when [messenger] is null or unmounted.
  static ScaffoldFeatureController<SnackBar, SnackBarClosedReason>? showOn(
    ScaffoldMessengerState? messenger,
    HqToastType type,
    String message,
    Duration duration, {
    HqToastAction? action,
    String? closeLabel,
  }) {
    if (messenger == null || !messenger.mounted) return null;
    messenger.hideCurrentSnackBar();
    return messenger.showSnackBar(
      SnackBar(
        behavior: SnackBarBehavior.floating,
        backgroundColor: Colors.transparent,
        elevation: 0,
        padding: EdgeInsets.zero,
        duration: duration,
        content: _ToastBody(
          type: type,
          message: message,
          action: action,
          closeLabel: closeLabel,
          onDismiss: messenger.hideCurrentSnackBar,
        ),
      ),
    );
  }

  /// Hides the toast on screen, if any.
  void clear() => messengerKey.currentState?.hideCurrentSnackBar();
}

class _ToastBody extends StatelessWidget {
  const _ToastBody({required this.type, required this.message, this.action, this.closeLabel, this.onDismiss});

  final HqToastType type;
  final String message;
  final HqToastAction? action;
  final String? closeLabel;
  final VoidCallback? onDismiss;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final (tone, icon) = switch (type) {
      HqToastType.success => (HqStatusTone.success, Icons.check_circle_outline),
      HqToastType.error => (HqStatusTone.danger, Icons.error_outline),
      HqToastType.warning => (HqStatusTone.warning, Icons.warning_amber_outlined),
      HqToastType.info => (HqStatusTone.info, Icons.info_outline),
    };
    final colors = tokens.tone(tone);
    return Semantics(
      liveRegion: true,
      child: DecoratedBox(
        // The mobile finish: a floating card (surface, rounded, softly lifted) with the tone in a tinted icon circle.
        decoration: BoxDecoration(
          color: tokens.surface,
          border: Border.all(color: colors.border.withValues(alpha: 0.45), width: 0.8),
          borderRadius: BorderRadius.circular(16),
          boxShadow: HqMobile.softShadow(context, lift: 1.4),
        ),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(HqSpacing.md, HqSpacing.md, HqSpacing.sm, HqSpacing.md),
          child: Row(
            children: [
              Container(
                width: 34,
                height: 34,
                decoration: BoxDecoration(color: Color.alphaBlend(colors.background, tokens.surface), shape: BoxShape.circle),
                child: Icon(icon, color: colors.foreground, size: 20),
              ),
              const SizedBox(width: HqSpacing.md),
              Expanded(child: Text(message, style: TextStyle(color: tokens.ink, fontWeight: FontWeight.w600, height: 1.3))),
              if (action case final act?) ...[
                const SizedBox(width: HqSpacing.sm),
                TextButton(
                  style: TextButton.styleFrom(foregroundColor: colors.foreground, minimumSize: const Size(0, HqSizes.controlHeight)),
                  onPressed: () {
                    onDismiss?.call();
                    act.onPressed();
                  },
                  child: Text(act.label, style: const TextStyle(fontWeight: FontWeight.w700)),
                ),
              ],
              if (closeLabel case final close?)
                IconButton(
                  tooltip: close,
                  color: tokens.muted,
                  icon: const Icon(Icons.close, size: 18),
                  onPressed: onDismiss,
                ),
            ],
          ),
        ),
      ),
    );
  }
}
