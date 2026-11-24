import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import 'hq_chart_data.dart';
import 'hq_chart_frame.dart';

/// Scatter plot over [HqChartData]: each series is a cloud of points (risk against return per property, for example).
/// Touch a dot for its series, point label and values.
class HqScatter extends StatelessWidget {
  const HqScatter({super.key, required this.data, this.height = 240, this.showLegend = true, this.emptyText = ''});

  final HqChartData data;
  final double height;
  final bool showLegend;
  final String emptyText;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final labelStyle = Theme.of(context).textTheme.labelSmall?.copyWith(color: tokens.muted);
    final shown = data.downsampled(hqChartDefaultMaxPointsForScatter);
    final colors = [for (var i = 0; i < shown.series.length; i++) context.chartColorAt(i, override: shown.series[i].color)];
    final spots = <ScatterSpot>[];
    final owners = <(double, double), (int, HqChartPoint)>{};
    for (var s = 0; s < shown.series.length; s++) {
      for (final p in shown.series[s].points) {
        spots.add(ScatterSpot(p.x, p.y, dotPainter: FlDotCirclePainter(radius: 5, color: colors[s].withValues(alpha: 0.85), strokeWidth: 1.5, strokeColor: tokens.surface)));
        owners[(p.x, p.y)] = (s, p);
      }
    }
    var minY = shown.minY;
    var maxY = shown.maxY;
    var minX = shown.minX;
    var maxX = shown.maxX;
    if (minY == maxY) {
      minY -= 1;
      maxY += 1;
    }
    if (minX == maxX) {
      minX -= 1;
      maxX += 1;
    }
    return HqChartFrame(
      description: data.describe(),
      height: height,
      empty: shown.isEmpty,
      emptyText: emptyText,
      legend: showLegend && shown.series.length > 1 ? [for (var i = 0; i < shown.series.length; i++) HqLegendItem(label: shown.series[i].label, color: colors[i])] : const [],
      child: ScatterChart(
        ScatterChartData(
          scatterSpots: spots,
          minX: minX,
          maxX: maxX,
          minY: minY,
          maxY: maxY,
          borderData: FlBorderData(show: false),
          gridData: FlGridData(getDrawingHorizontalLine: (_) => FlLine(color: tokens.fieldBorder, strokeWidth: 1), getDrawingVerticalLine: (_) => FlLine(color: tokens.fieldBorder, strokeWidth: 1)),
          titlesData: FlTitlesData(
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            bottomTitles: AxisTitles(sideTitles: SideTitles(showTitles: true, reservedSize: 28, getTitlesWidget: (v, meta) => SideTitleWidget(meta: meta, child: Text(shown.formatX(v), style: labelStyle)))),
            leftTitles: AxisTitles(sideTitles: SideTitles(showTitles: true, reservedSize: 56, getTitlesWidget: (v, meta) => SideTitleWidget(meta: meta, child: Text(shown.formatValue(v), style: labelStyle)))),
          ),
          scatterTouchData: ScatterTouchData(
            touchTooltipData: ScatterTouchTooltipData(
              getTooltipColor: (_) => tokens.surface,
              getTooltipItems: (spot) {
                final owner = owners[(spot.x, spot.y)];
                final name = owner == null ? '' : '${shown.series[owner.$1].label}${owner.$2.label == null ? '' : '  ${owner.$2.label}'}\n';
                return ScatterTooltipItem('$name${shown.formatX(spot.x)}  ${shown.formatValue(spot.y)}', textStyle: TextStyle(color: tokens.ink, fontWeight: FontWeight.w600, fontSize: 12));
              },
            ),
          ),
        ),
      ),
    );
  }
}

/// Points kept per series in a scatter plot.
const int hqChartDefaultMaxPointsForScatter = 400;

/// Radar (spider) chart over [HqChartData]: [HqChartData.xLabels] are the axes, each series a polygon (a risk profile
/// against a benchmark). Values run from 0 to [maxValue] (default: the largest value).
class HqRadar extends StatelessWidget {
  const HqRadar({super.key, required this.data, this.maxValue, this.height = 260, this.ticks = 4, this.showLegend = true, this.emptyText = ''});

  final HqChartData data;
  final double? maxValue;
  final double height;
  final int ticks;
  final bool showLegend;
  final String emptyText;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme.labelSmall?.copyWith(color: tokens.ink);
    final colors = [for (var i = 0; i < data.series.length; i++) context.chartColorAt(i, override: data.series[i].color)];
    final axes = data.categoryCount;
    final top = maxValue ?? (data.maxY <= 0 ? 1 : data.maxY);
    return HqChartFrame(
      description: data.describe(),
      height: height,
      empty: data.isEmpty || axes < 3,
      emptyText: emptyText,
      legend: showLegend && data.series.length > 1 ? [for (var i = 0; i < data.series.length; i++) HqLegendItem(label: data.series[i].label, color: colors[i])] : const [],
      child: data.isEmpty || axes < 3 ? const SizedBox.shrink() : RadarChart(
        RadarChartData(
          tickCount: ticks,
          radarShape: RadarShape.polygon,
          titlePositionPercentageOffset: 0.15,
          titleTextStyle: text,
          ticksTextStyle: const TextStyle(color: Colors.transparent, fontSize: 0),
          tickBorderData: BorderSide(color: tokens.fieldBorder),
          gridBorderData: BorderSide(color: tokens.fieldBorder),
          radarBorderData: BorderSide(color: tokens.fieldBorder),
          getTitle: (index, angle) => RadarChartTitle(text: index < data.xLabels.length ? data.xLabels[index] : '${index + 1}'),
          dataSets: [
            // An invisible set pins the scale to 0..top so every series shares it.
            RadarDataSet(dataEntries: [for (var i = 0; i < axes; i++) RadarEntry(value: top)], fillColor: Colors.transparent, borderColor: Colors.transparent, borderWidth: 0, entryRadius: 0),
            for (var s = 0; s < data.series.length; s++)
              RadarDataSet(
                dataEntries: [for (var i = 0; i < axes; i++) RadarEntry(value: i < data.series[s].points.length ? data.series[s].points[i].y : 0)],
                fillColor: colors[s].withValues(alpha: 0.2),
                borderColor: colors[s],
                borderWidth: 2,
                entryRadius: 3,
              ),
          ],
        ),
      ),
    );
  }
}
