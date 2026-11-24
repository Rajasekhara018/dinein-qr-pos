import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// One legend entry.
@immutable
class HqLegendItem {
  const HqLegendItem({required this.label, required this.color, this.valueText});

  final String label;
  final Color color;

  /// Value shown after the label (a formatted amount or percentage).
  final String? valueText;
}

/// Legend under a chart: a colour swatch, the label and an optional value. Colour is never the only carrier of meaning,
/// the text is always there.
class HqChartLegend extends StatelessWidget {
  const HqChartLegend({super.key, required this.items});

  final List<HqLegendItem> items;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const SizedBox.shrink();
    final tokens = context.hq;
    final text = Theme.of(context).textTheme.bodySmall?.copyWith(color: tokens.ink);
    return Wrap(
      spacing: HqSpacing.lg,
      runSpacing: HqSpacing.xs,
      children: [
        for (final item in items)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(width: 10, height: 10, decoration: BoxDecoration(color: item.color, borderRadius: BorderRadius.circular(3))),
              const SizedBox(width: HqSpacing.xs),
              Flexible(child: Text(item.valueText == null ? item.label : '${item.label}  ${item.valueText}', style: text)),
            ],
          ),
      ],
    );
  }
}

/// Shared shell of the charts: a screen-reader description, the chart, an optional legend and the empty state.
///
/// The chart itself is hidden from the accessibility tree (it is a picture); [description] is read instead.
class HqChartFrame extends StatelessWidget {
  const HqChartFrame({
    super.key,
    required this.description,
    required this.child,
    this.legend = const <HqLegendItem>[],
    this.height,
    this.empty = false,
    this.emptyText = '',
  });

  final String description;
  final Widget child;
  final List<HqLegendItem> legend;
  final double? height;
  final bool empty;
  final String emptyText;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final body = empty
        ? Center(child: Text(emptyText, style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: tokens.muted)))
        : ExcludeSemantics(child: child);
    return Semantics(
      container: true,
      image: true,
      label: description,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          height == null ? body : SizedBox(height: height, child: body),
          if (!empty && legend.isNotEmpty) Padding(padding: const EdgeInsets.only(top: HqSpacing.md), child: HqChartLegend(items: legend)),
        ],
      ),
    );
  }
}
