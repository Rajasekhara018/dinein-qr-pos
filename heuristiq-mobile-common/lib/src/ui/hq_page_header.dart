import 'package:flutter/material.dart';

import '../core/hq_format.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_button.dart';

/// One step of the breadcrumb trail; the last step has no [onTap].
@immutable
class HqBreadcrumb {
  const HqBreadcrumb({required this.label, this.onTap});

  final String label;
  final VoidCallback? onTap;
}

/// Screen heading: breadcrumbs, kicker, title, subtitle, last-updated time, loading bar, back button and actions
/// (the counterpart of `hq-page-header`).
class HqPageHeader extends StatelessWidget {
  const HqPageHeader({
    super.key,
    required this.title,
    this.kicker = '',
    this.subtitle = '',
    this.breadcrumbs,
    this.lastUpdated,
    this.lastUpdatedLabel = '',
    this.dateFormatter,
    this.loading = false,
    this.loadingLabel = '',
    this.showBack = false,
    this.backLabel = '',
    this.onBack,
    this.actions = const <Widget>[],
  });

  final String title;
  final String kicker;
  final String subtitle;
  final List<HqBreadcrumb>? breadcrumbs;
  final DateTime? lastUpdated;

  /// Text before the time, e.g. "Updated".
  final String lastUpdatedLabel;

  /// Formats [lastUpdated]; defaults to `yyyy-MM-dd HH:mm`. Pass a locale-aware formatter from the app.
  final String Function(DateTime)? dateFormatter;
  final bool loading;

  /// Accessibility label of the loading bar.
  final String loadingLabel;
  final bool showBack;
  final String backLabel;
  final VoidCallback? onBack;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final crumbs = breadcrumbs ?? const <HqBreadcrumb>[];
    final updated = lastUpdated;
    return Padding(
      padding: const EdgeInsets.only(bottom: HqSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (loading)
            Padding(
              padding: const EdgeInsets.only(bottom: HqSpacing.sm),
              child: LinearProgressIndicator(semanticsLabel: loadingLabel.isEmpty ? null : loadingLabel, color: tokens.primary),
            ),
          if (showBack)
            HqButton(
              label: backLabel.isEmpty ? null : backLabel,
              icon: Icons.arrow_back,
              appearance: HqButtonAppearance.text,
              size: HqButtonSize.sm,
              semanticLabel: backLabel.isEmpty ? null : backLabel,
              onPressed: onBack,
            ),
          if (crumbs.isNotEmpty)
            Wrap(
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                for (var i = 0; i < crumbs.length; i++) ...[
                  InkWell(
                    onTap: crumbs[i].onTap,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: HqSpacing.xs),
                      child: Text(
                        crumbs[i].label,
                        style: text.bodySmall?.copyWith(color: crumbs[i].onTap == null ? tokens.muted : tokens.link),
                      ),
                    ),
                  ),
                  if (i < crumbs.length - 1) Icon(Icons.chevron_right, size: 16, color: tokens.muted),
                ],
              ],
            ),
          if (kicker.isNotEmpty) Text(kicker, style: text.labelMedium?.copyWith(color: tokens.link, fontWeight: FontWeight.w700)),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: Semantics(header: true, child: Text(title, style: text.headlineSmall?.copyWith(fontWeight: FontWeight.w700)))),
              if (actions.isNotEmpty) Wrap(spacing: HqSpacing.sm, children: actions),
            ],
          ),
          if (subtitle.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: HqSpacing.xs),
              child: Text(subtitle, style: text.bodyMedium?.copyWith(color: tokens.muted)),
            ),
          if (updated != null)
            Padding(
              padding: const EdgeInsets.only(top: HqSpacing.xs),
              child: Text(
                '${lastUpdatedLabel.isEmpty ? '' : '$lastUpdatedLabel '}${(dateFormatter ?? hqFormatDateTime)(updated)}',
                style: text.bodySmall?.copyWith(color: tokens.muted),
              ),
            ),
        ],
      ),
    );
  }
}
