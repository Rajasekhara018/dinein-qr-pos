import 'package:flutter/material.dart';

import '../theme/hq_mobile_finish.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Width cap of a [HqModal] on wide screens (the web `HqModalSize`).
enum HqModalSize { sm, md, lg }

/// Height of a [HqDrawer] as a share of the screen (the web `HqDrawerSize`).
enum HqDrawerSize {
  sm(0.5),
  md(0.75),
  lg(0.92);

  const HqDrawerSize(this.fraction);

  final double fraction;
}

/// Shared frame of [HqModal] and [HqDrawer]: title, subtitle, close button, scrolling body and an optional footer.
class HqSheetFrame extends StatelessWidget {
  const HqSheetFrame({
    super.key,
    required this.title,
    required this.child,
    this.subtitle = '',
    this.showClose = true,
    this.closeLabel = '',
    this.footer,
  });

  final String title;
  final String subtitle;
  final bool showClose;
  final String closeLabel;
  final Widget child;

  /// Buttons pinned under the body (use a `HqFormActions`).
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(HqSpacing.lg, 0, HqSpacing.sm, HqSpacing.sm),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Semantics(header: true, child: Text(title, style: text.titleLarge?.copyWith(fontWeight: FontWeight.w800, color: tokens.ink, letterSpacing: -0.2))),
                    if (subtitle.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 2), child: Text(subtitle, style: text.bodyMedium?.copyWith(color: tokens.muted))),
                  ],
                ),
              ),
              if (showClose)
                // The close button in a soft tinted circle, the mobile sheet idiom.
                IconButton(
                  tooltip: closeLabel.isEmpty ? null : closeLabel,
                  style: IconButton.styleFrom(backgroundColor: HqMobile.trackFill(context), foregroundColor: tokens.ink),
                  icon: const Icon(Icons.close_rounded, size: 20),
                  onPressed: () => Navigator.of(context).maybePop(),
                ),
            ],
          ),
        ),
        Divider(height: 1, color: tokens.fieldBorder.withValues(alpha: 0.5)),
        Flexible(
          child: SingleChildScrollView(
            padding: EdgeInsets.fromLTRB(HqSpacing.lg, HqSpacing.lg, HqSpacing.lg, HqSpacing.lg + (footer == null ? MediaQuery.paddingOf(context).bottom : 0)),
            child: child,
          ),
        ),
        if (footer != null)
          // The actions stay pinned above the home indicator, on a softly lifted bar.
          DecoratedBox(
            decoration: BoxDecoration(
              color: tokens.surface,
              border: Border(top: BorderSide(color: tokens.fieldBorder.withValues(alpha: 0.5))),
            ),
            child: SafeArea(top: false, child: Padding(padding: const EdgeInsets.fromLTRB(HqSpacing.lg, HqSpacing.md, HqSpacing.lg, HqSpacing.md), child: footer)),
          ),
      ],
    );
  }
}

Future<T?> _showSheet<T>(
  BuildContext context, {
  required Widget Function(BuildContext) builder,
  required bool dismissible,
  required double maxWidth,
}) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    isDismissible: dismissible,
    enableDrag: dismissible,
    constraints: BoxConstraints(maxWidth: maxWidth),
    backgroundColor: context.hq.surface,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(HqMobile.sheetRadius))),
    builder: (sheetContext) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(sheetContext).bottom),
      child: builder(sheetContext),
    ),
  );
}

/// A bottom-sheet dialog sized to its content (the mobile counterpart of `hq-modal`).
///
/// Close it with `Navigator.of(context).pop(result)`; [show] completes with that result, or null when dismissed.
abstract final class HqModal {
  static Future<T?> show<T>(
    BuildContext context, {
    required String title,
    required WidgetBuilder builder,
    String subtitle = '',
    Widget? footer,
    HqModalSize size = HqModalSize.md,
    bool dismissible = true,
    bool showClose = true,
    String closeLabel = '',
  }) {
    final maxWidth = switch (size) {
      HqModalSize.sm => 420.0,
      HqModalSize.md => 560.0,
      HqModalSize.lg => 760.0,
    };
    return _showSheet<T>(
      context,
      dismissible: dismissible,
      maxWidth: maxWidth,
      builder: (sheetContext) => HqSheetFrame(
        title: title,
        subtitle: subtitle,
        showClose: showClose,
        closeLabel: closeLabel,
        footer: footer,
        child: Builder(builder: builder),
      ),
    );
  }
}

/// A tall, side-panel style bottom sheet for details and edit forms (the mobile counterpart of `hq-drawer`).
abstract final class HqDrawer {
  static Future<T?> show<T>(
    BuildContext context, {
    required String title,
    required WidgetBuilder builder,
    String subtitle = '',
    Widget? footer,
    HqDrawerSize size = HqDrawerSize.md,
    bool dismissible = true,
    bool showClose = true,
    String closeLabel = '',
  }) {
    return _showSheet<T>(
      context,
      dismissible: dismissible,
      maxWidth: 720,
      builder: (sheetContext) => SizedBox(
        height: MediaQuery.sizeOf(sheetContext).height * size.fraction,
        child: HqSheetFrame(
          title: title,
          subtitle: subtitle,
          showClose: showClose,
          closeLabel: closeLabel,
          footer: footer,
          child: Builder(builder: builder),
        ),
      ),
    );
  }
}
