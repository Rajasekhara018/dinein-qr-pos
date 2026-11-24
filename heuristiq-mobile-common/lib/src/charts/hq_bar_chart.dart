import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_chart_data.dart';
import 'hq_chart_frame.dart';

/// Bar chart over [HqChartData]: categories from [HqChartData.xLabels] (matched to points by position), one bar per series
/// in a group, or one stacked bar. [horizontal] draws a ranked list of bars (labels left, values right), which reads better
/// for long names on a phone.
class HqBarChart extends StatelessWidget {
  const HqBarChart({
    super.key,
    required this.data,
    this.height = 220,
    this.stacked = false,
    this.horizontal = false,
    this.showLegend = true,
    this.emptyText = '',
  });

  final HqChartData data;
  final double height;
  final bool stacked;
  final bool horizontal;
  final bool showLegend;
  final String emptyText;

  double _y(HqChartSeries s, int i) => i < s.points.length ? s.points[i].y : 0;

  @override
  Widget build(BuildContext context) {
    final colors = [for (var i = 0; i < data.series.length; i++) context.chartColorAt(i, override: data.series[i].color)];
    final legend = [for (var i = 0; i < data.series.length; i++) HqLegendItem(label: data.series[i].label, color: colors[i])];
    return HqChartFrame(
      description: data.describe(),
      height: horizontal ? null : height,
      empty: data.isEmpty,
      emptyText: emptyText,
      legend: showLegend && data.series.length > 1 ? legend : const [],
      child: horizontal ? _horizontal(context, colors) : BarChart(_vertical(context, colors)),
    );
  }

  BarChartData _vertical(BuildContext context, List<Color> colors) {
    final tokens = context.hq;
    final labelStyle = Theme.of(context).textTheme.labelSmall?.copyWith(color: tokens.muted);
    final count = data.categoryCount;
    final width = stacked || data.series.length <= 1 ? 18.0 : (14.0 / data.series.length).clamp(5.0, 14.0);
    return BarChartData(
      alignment: BarChartAlignment.spaceAround,
      maxY: _maxY() * 1.1,
      gridData: FlGridData(
        drawVerticalLine: false,
        getDrawingHorizontalLine: (_) => FlLine(color: tokens.fieldBorder, strokeWidth: 1),
      ),
      borderData: FlBorderData(show: false),
      titlesData: FlTitlesData(
        topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
        rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
        bottomTitles: AxisTitles(
          sideTitles: SideTitles(
            showTitles: true,
            reservedSize: 28,
            getTitlesWidget: (value, meta) => SideTitleWidget(meta: meta, child: Text(data.formatX(value), style: labelStyle)),
          ),
        ),
        leftTitles: AxisTitles(
          sideTitles: SideTitles(
            showTitles: true,
            reservedSize: 56,
            getTitlesWidget: (value, meta) => SideTitleWidget(meta: meta, child: Text(data.formatValue(value), style: labelStyle)),
          ),
        ),
      ),
      barGroups: [
        for (var i = 0; i < count; i++)
          BarChartGroupData(
            x: i,
            barsSpace: 2,
            barRods: stacked
                ? [
                    BarChartRodData(
                      toY: [for (final s in data.series) _y(s, i)].fold<double>(0, (a, b) => a + b),
                      width: width,
                      borderRadius: BorderRadius.circular(3),
                      rodStackItems: _stack(i, colors),
                    ),
                  ]
                : [
                    for (var s = 0; s < data.series.length; s++)
                      BarChartRodData(toY: _y(data.series[s], i), color: colors[s], width: width, borderRadius: BorderRadius.circular(3)),
                  ],
          ),
      ],
      barTouchData: BarTouchData(
        touchTooltipData: BarTouchTooltipData(
          getTooltipColor: (_) => tokens.surface,
          getTooltipItem: (group, groupIndex, rod, rodIndex) {
            final heading = data.formatX(group.x.toDouble());
            final name = stacked ? '' : '${data.series[rodIndex].label}  ';
            return BarTooltipItem(
              '$heading\n$name${data.formatValue(rod.toY)}',
              TextStyle(color: tokens.ink, fontWeight: FontWeight.w600, fontSize: 12),
            );
          },
        ),
      ),
    );
  }

  List<BarChartRodStackItem> _stack(int category, List<Color> colors) {
    var from = 0.0;
    final items = <BarChartRodStackItem>[];
    for (var s = 0; s < data.series.length; s++) {
      final to = from + _y(data.series[s], category);
      items.add(BarChartRodStackItem(from, to, colors[s]));
      from = to;
    }
    return items;
  }

  double _maxY() {
    var best = 0.0;
    for (var i = 0; i < data.categoryCount; i++) {
      final values = [for (final s in data.series) _y(s, i)];
      final candidate = stacked ? values.fold<double>(0, (a, b) => a + b) : values.reduce((a, b) => a > b ? a : b);
      if (candidate > best) best = candidate;
    }
    return best <= 0 ? 1 : best;
  }

  Widget _horizontal(BuildContext context, List<Color> colors) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final maxTotal = _maxY();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < data.categoryCount; i++)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: HqSpacing.xs),
            child: Row(
              children: [
                SizedBox(width: 96, child: Text(data.formatX(i.toDouble()), style: text.bodySmall?.copyWith(color: tokens.ink), maxLines: 2, overflow: TextOverflow.ellipsis)),
                const SizedBox(width: HqSpacing.sm),
                Expanded(
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      final unit = constraints.maxWidth / maxTotal;
                      if (stacked) {
                        return Row(children: [
                          for (var s = 0; s < data.series.length; s++)
                            Container(width: _y(data.series[s], i) * unit, height: 14, color: colors[s]),
                        ]);
                      }
                      return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        for (var s = 0; s < data.series.length; s++)
                          Padding(
                            padding: const EdgeInsets.symmetric(vertical: 1),
                            child: Tooltip(
                              message: '${data.series[s].label}  ${data.formatValue(_y(data.series[s], i))}',
                              child: Container(
                                width: _y(data.series[s], i) * unit,
                                height: data.series.length > 1 ? 8 : 14,
                                decoration: BoxDecoration(color: colors[s], borderRadius: BorderRadius.circular(3)),
                              ),
                            ),
                          ),
                      ]);
                    },
                  ),
                ),
                const SizedBox(width: HqSpacing.sm),
                Text(
                  data.formatValue(stacked ? [for (final s in data.series) _y(s, i)].fold<double>(0, (a, b) => a + b) : _y(data.series.first, i)),
                  style: text.bodySmall?.copyWith(fontWeight: FontWeight.w600),
                ),
              ],
            ),
          ),
      ],
    );
  }
}
