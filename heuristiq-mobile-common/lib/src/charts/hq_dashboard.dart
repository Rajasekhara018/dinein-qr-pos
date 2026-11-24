import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_button.dart';
import '../ui/hq_form.dart';
import '../ui/hq_tabs.dart';
import 'hq_line_chart.dart';

/// Direction of a change.
enum HqTrend { up, down, flat }

/// A headline number with its change: value, delta, trend arrow and an optional sparkline.
///
/// The arrow and delta colour follow [positiveIsGood]: up is green when true, red when false (a rising cost is bad news).
/// Colour is never alone: the arrow and [deltaText] are always shown, and [semanticsLabel] reads the whole tile.
class HqKpiTile extends StatelessWidget {
  const HqKpiTile({
    super.key,
    required this.label,
    required this.value,
    this.deltaText,
    this.trend = HqTrend.flat,
    this.positiveIsGood = true,
    this.sparkline,
    this.icon,
    this.semanticsLabel,
  });

  final String label;

  /// Already-formatted value (use `hqFormatMoney`).
  final String value;
  final String? deltaText;
  final HqTrend trend;
  final bool positiveIsGood;
  final List<double>? sparkline;
  final IconData? icon;
  final String? semanticsLabel;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final good = switch (trend) {
      HqTrend.flat => null,
      HqTrend.up => positiveIsGood,
      HqTrend.down => !positiveIsGood,
    };
    final tone = good == null ? tokens.toneNeutral : (good ? tokens.toneSuccess : tokens.toneDanger);
    final arrow = switch (trend) {
      HqTrend.up => Icons.arrow_upward,
      HqTrend.down => Icons.arrow_downward,
      HqTrend.flat => Icons.arrow_forward,
    };
    return Semantics(
      container: true,
      label: semanticsLabel ?? '$label: $value${deltaText == null ? '' : ', $deltaText'}',
      excludeSemantics: true,
      child: DecoratedBox(
        decoration: BoxDecoration(color: tokens.surface, border: Border.all(color: tokens.fieldBorder), borderRadius: BorderRadius.circular(HqRadius.panel)),
        child: Padding(
          padding: const EdgeInsets.all(HqSpacing.lg),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(children: [
                      if (icon != null) ...[Icon(icon, size: 16, color: tokens.muted), const SizedBox(width: HqSpacing.xs)],
                      Flexible(child: Text(label, style: text.bodySmall?.copyWith(color: tokens.muted), maxLines: 1, overflow: TextOverflow.ellipsis)),
                    ]),
                    const SizedBox(height: HqSpacing.xs),
                    Text(value, style: text.headlineSmall?.copyWith(fontWeight: FontWeight.w700), maxLines: 1, overflow: TextOverflow.ellipsis),
                    if (deltaText != null)
                      Padding(
                        padding: const EdgeInsets.only(top: HqSpacing.xs),
                        child: DecoratedBox(
                          decoration: BoxDecoration(color: tone.background, borderRadius: BorderRadius.circular(HqRadius.pill)),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                            child: Row(mainAxisSize: MainAxisSize.min, children: [
                              Icon(arrow, size: 12, color: tone.foreground),
                              const SizedBox(width: 4),
                              Text(deltaText!, style: text.labelSmall?.copyWith(color: tone.foreground, fontWeight: FontWeight.w600)),
                            ]),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              if (sparkline != null && sparkline!.length > 1) HqSparkline(values: sparkline!, width: 72, height: 36, color: good == null ? null : (good ? tokens.success : tokens.warn)),
            ],
          ),
        ),
      ),
    );
  }
}

/// What a [HqDashboardCard] shows.
enum HqCardState { ready, loading, empty, error }

/// A titled card for a chart or a list on a dashboard: title, subtitle, optional period selector, and loading, empty and
/// error states (with retry). Texts are passed in.
class HqDashboardCard extends StatelessWidget {
  const HqDashboardCard({
    super.key,
    required this.title,
    required this.child,
    this.subtitle = '',
    this.state = HqCardState.ready,
    this.periods,
    this.period,
    this.onPeriodChanged,
    this.emptyText = '',
    this.errorText = '',
    this.retryLabel = '',
    this.onRetry,
    this.loadingLabel = '',
    this.minContentHeight = 120,
  });

  final String title;
  final String subtitle;
  final Widget child;
  final HqCardState state;

  /// Period choices (1M, 6M, 1Y); with [period] and [onPeriodChanged] a selector is shown.
  final List<HqTab<String>>? periods;
  final String? period;
  final ValueChanged<String>? onPeriodChanged;
  final String emptyText;
  final String errorText;
  final String retryLabel;
  final VoidCallback? onRetry;
  final String loadingLabel;
  final double minContentHeight;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final Widget body = switch (state) {
      HqCardState.ready => child,
      HqCardState.loading => Center(child: CircularProgressIndicator(semanticsLabel: loadingLabel.isEmpty ? null : loadingLabel)),
      HqCardState.empty => Center(child: Text(emptyText, textAlign: TextAlign.center, style: text.bodyMedium?.copyWith(color: tokens.muted))),
      HqCardState.error => Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline, color: tokens.warn),
            const SizedBox(height: HqSpacing.xs),
            Text(errorText, textAlign: TextAlign.center, style: text.bodyMedium),
            if (onRetry != null) ...[
              const SizedBox(height: HqSpacing.sm),
              HqButton(label: retryLabel.isEmpty ? null : retryLabel, icon: retryLabel.isEmpty ? Icons.refresh : null, appearance: HqButtonAppearance.outline, size: HqButtonSize.sm, onPressed: onRetry),
            ],
          ],
        ),
    };
    return DecoratedBox(
      decoration: BoxDecoration(color: tokens.surface, border: Border.all(color: tokens.fieldBorder), borderRadius: BorderRadius.circular(HqRadius.panel)),
      child: Padding(
        padding: const EdgeInsets.all(HqSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Semantics(header: true, child: Text(title, style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700))),
            if (subtitle.isNotEmpty) Text(subtitle, style: text.bodySmall?.copyWith(color: tokens.muted)),
            if (periods != null && onPeriodChanged != null)
              Padding(
                padding: const EdgeInsets.only(top: HqSpacing.sm),
                child: HqTabs<String>(tabs: periods!, value: period, onChanged: onPeriodChanged, size: HqTabsSize.sm),
              ),
            const SizedBox(height: HqSpacing.md),
            state == HqCardState.ready ? body : ConstrainedBox(constraints: BoxConstraints(minHeight: minContentHeight), child: body),
          ],
        ),
      ),
    );
  }
}

/// Makes a child of [HqDashboardGrid] span more than one column on a tablet.
class HqDashboardItem extends StatelessWidget {
  const HqDashboardItem({super.key, required this.child, this.span = 2});

  final Widget child;
  final int span;

  @override
  Widget build(BuildContext context) => child;
}

/// Responsive grid of dashboard cards: one column on a phone, two from 600 px, three from 1000 px; an item wrapped in
/// [HqDashboardItem] can span several. With [onRefresh] it supports pull to refresh. It scrolls itself.
class HqDashboardGrid extends StatelessWidget {
  const HqDashboardGrid({super.key, required this.children, this.onRefresh, this.gap = HqSpacing.lg, this.header});

  final List<Widget> children;
  final Future<void> Function()? onRefresh;
  final double gap;

  /// Shown above the grid, scrolling with it (a `HqPageHeader` or `HqStatCards`).
  final Widget? header;

  /// Columns for a [width].
  static int columnsFor(double width) => width >= 1000 ? 3 : (width >= 600 ? 2 : 1);

  @override
  Widget build(BuildContext context) {
    final grid = LayoutBuilder(
      builder: (context, constraints) {
        final columns = columnsFor(constraints.maxWidth);
        final unit = (constraints.maxWidth - gap * (columns - 1)) / columns;
        int span(Widget w) => w is HqDashboardItem ? w.span.clamp(1, columns) : (w is HqFormGridItem ? w.span.clamp(1, columns) : 1);
        final scroll = SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsets.zero,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (header != null) header!,
              Wrap(
                spacing: gap,
                runSpacing: gap,
                children: [for (final c in children) SizedBox(width: span(c) * unit + (span(c) - 1) * gap, child: c)],
              ),
            ],
          ),
        );
        return onRefresh == null ? scroll : RefreshIndicator(onRefresh: onRefresh!, child: scroll);
      },
    );
    return grid;
  }
}
