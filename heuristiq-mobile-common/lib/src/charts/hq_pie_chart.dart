import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_chart_data.dart';
import 'hq_chart_frame.dart';

/// One slice, resolved from [HqChartData].
@immutable
class HqSlice {
  const HqSlice({required this.label, required this.value, required this.color});

  final String label;
  final double value;
  final Color color;
}

/// Resolves the slices of [data]: a single series with several points gives a slice per point (labelled from
/// [HqChartData.xLabels]); otherwise a slice per series (its total). Negative values are ignored. More than eight slices
/// fold into one neutral [otherLabel] slice (never a ninth hue).
List<HqSlice> hqSlicesOf(BuildContext context, HqChartData data, {String otherLabel = ''}) {
  final raw = <(String, double, Color?)>[];
  if (data.series.length == 1 && data.series.first.points.length > 1) {
    final series = data.series.first;
    for (var i = 0; i < series.points.length; i++) {
      final p = series.points[i];
      raw.add((p.label ?? data.formatX(p.x), p.y, null));
    }
  } else {
    for (final s in data.series) {
      raw.add((s.label, s.total, s.color));
    }
  }
  final positive = raw.where((r) => r.$2 > 0).toList();
  final tokens = context.hq;
  final max = tokens.chartCategorical.length;
  final slices = <HqSlice>[];
  for (var i = 0; i < positive.length; i++) {
    if (positive.length > max && i >= max - 1) {
      final rest = positive.skip(max - 1).fold<double>(0, (sum, r) => sum + r.$2);
      slices.add(HqSlice(label: otherLabel, value: rest, color: tokens.chartOther));
      break;
    }
    slices.add(HqSlice(label: positive[i].$1, value: positive[i].$2, color: context.chartColorAt(i, override: positive[i].$3)));
  }
  return slices;
}

/// Pie chart over [HqChartData] (portfolio allocation by property, city or type). Touch a slice to see its value and share.
/// [percentFormatter] turns a 0-100 share into text; the default is one decimal and a percent sign.
class HqPieChart extends StatefulWidget {
  const HqPieChart({
    super.key,
    required this.data,
    this.height = 220,
    this.donut = false,
    this.otherLabel = '',
    this.centerLabel,
    this.centerValue,
    this.showLegend = true,
    this.percentFormatter,
    this.emptyText = '',
  });

  final HqChartData data;
  final double height;
  final bool donut;

  /// Name of the folded slice when there are more than eight.
  final String otherLabel;

  /// Donut centre text when no slice is touched (e.g. "Total" and the formatted total).
  final String? centerLabel;
  final String? centerValue;
  final bool showLegend;
  final String Function(double percent)? percentFormatter;
  final String emptyText;

  @override
  State<HqPieChart> createState() => _HqPieChartState();
}

class _HqPieChartState extends State<HqPieChart> {
  int? _touched;

  String _percent(double p) => (widget.percentFormatter ?? (v) => '${v.toStringAsFixed(1)}%')(p);

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final slices = hqSlicesOf(context, widget.data, otherLabel: widget.otherLabel);
    final total = slices.fold<double>(0, (sum, s) => sum + s.value);
    final touched = (_touched != null && _touched! < slices.length) ? slices[_touched!] : null;
    final centerTop = touched?.label ?? widget.centerLabel;
    final centerBottom = touched == null ? widget.centerValue : '${widget.data.formatValue(touched.value)}  ${_percent(touched.value / total * 100)}';
    final chart = Stack(
      alignment: Alignment.center,
      children: [
        PieChart(
          PieChartData(
            sectionsSpace: 2,
            centerSpaceRadius: widget.donut ? 56 : 0,
            sections: [
              for (var i = 0; i < slices.length; i++)
                PieChartSectionData(
                  value: slices[i].value,
                  color: slices[i].color,
                  showTitle: false,
                  radius: i == _touched ? 62 : 54,
                  borderSide: BorderSide(color: tokens.surface, width: 1),
                ),
            ],
            pieTouchData: PieTouchData(
              touchCallback: (event, response) {
                final index = response?.touchedSection?.touchedSectionIndex;
                final next = (event.isInterestedForInteractions && index != null && index >= 0) ? index : null;
                if (next != _touched) setState(() => _touched = next);
              },
            ),
          ),
        ),
        if (widget.donut && (centerTop != null || centerBottom != null))
          Padding(
            padding: const EdgeInsets.all(HqSpacing.xl),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (centerTop != null) Text(centerTop, style: text.bodySmall?.copyWith(color: tokens.muted), textAlign: TextAlign.center, maxLines: 2, overflow: TextOverflow.ellipsis),
                if (centerBottom != null) Text(centerBottom, style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700), textAlign: TextAlign.center),
              ],
            ),
          ),
      ],
    );
    return HqChartFrame(
      description: widget.data.describe(),
      height: widget.height,
      empty: slices.isEmpty,
      emptyText: widget.emptyText,
      legend: widget.showLegend
          ? [for (final s in slices) HqLegendItem(label: s.label, color: s.color, valueText: _percent(s.value / total * 100))]
          : const [],
      child: chart,
    );
  }
}

/// A [HqPieChart] with a hole and centre text: the allocation donut.
class HqDonutChart extends StatelessWidget {
  const HqDonutChart({
    super.key,
    required this.data,
    this.height = 220,
    this.otherLabel = '',
    this.centerLabel,
    this.centerValue,
    this.showLegend = true,
    this.percentFormatter,
    this.emptyText = '',
  });

  final HqChartData data;
  final double height;
  final String otherLabel;
  final String? centerLabel;
  final String? centerValue;
  final bool showLegend;
  final String Function(double percent)? percentFormatter;
  final String emptyText;

  @override
  Widget build(BuildContext context) => HqPieChart(
        data: data,
        height: height,
        donut: true,
        otherLabel: otherLabel,
        centerLabel: centerLabel,
        centerValue: centerValue,
        showLegend: showLegend,
        percentFormatter: percentFormatter,
        emptyText: emptyText,
      );
}
