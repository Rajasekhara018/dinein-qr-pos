import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../core/hq_format.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_chart_data.dart';
import 'hq_chart_frame.dart';

/// Series count past which a line chart stops drawing every point (see [HqChartData.downsampled]).
const int hqChartDefaultMaxPoints = 200;

/// Line chart over [HqChartData] (the chart library is `fl_chart`, kept behind this API so it can be swapped).
///
/// Touch a point to see a tooltip with the series name and value. Colours come from the theme tokens: series slot 1..8.
/// Long series are downsampled to [maxPoints].
class HqLineChart extends StatelessWidget {
  const HqLineChart({
    super.key,
    required this.data,
    this.height = 220,
    this.curved = true,
    this.showDots = false,
    this.showGrid = true,
    this.showLegend = true,
    this.area = false,
    this.maxPoints = hqChartDefaultMaxPoints,
    this.emptyText = '',
    this.markerXs = const <double>{},
  });

  final HqChartData data;
  final double height;
  final bool curved;
  final bool showDots;
  final bool showGrid;
  final bool showLegend;

  /// Fill under each line.
  final bool area;
  final int maxPoints;
  final String emptyText;

  /// X values that get a larger marker dot (valuation dates on a NAV chart).
  final Set<double> markerXs;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final shown = data.downsampled(maxPoints);
    final colors = [for (var i = 0; i < shown.series.length; i++) context.chartColorAt(i, override: shown.series[i].color)];
    final legend = [for (var i = 0; i < shown.series.length; i++) HqLegendItem(label: shown.series[i].label, color: colors[i])];
    return HqChartFrame(
      description: data.describe(),
      height: height,
      empty: shown.isEmpty,
      emptyText: emptyText,
      legend: showLegend && shown.series.length > 1 ? legend : const [],
      child: LineChart(_chartData(context, tokens, shown, colors)),
    );
  }

  LineChartData _chartData(BuildContext context, HqTokens tokens, HqChartData shown, List<Color> colors) {
    final labelStyle = Theme.of(context).textTheme.labelSmall?.copyWith(color: tokens.muted);
    var minY = shown.minY;
    var maxY = shown.maxY;
    if (minY == maxY) {
      minY -= 1;
      maxY += 1;
    }
    final pad = (maxY - minY) * 0.08;
    final span = shown.maxX - shown.minX;
    return LineChartData(
      minX: shown.minX,
      maxX: shown.maxX == shown.minX ? shown.maxX + 1 : shown.maxX,
      minY: minY - pad,
      maxY: maxY + pad,
      gridData: FlGridData(
        show: showGrid,
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
            interval: span <= 0 ? 1 : span / 4,
            getTitlesWidget: (value, meta) => SideTitleWidget(meta: meta, child: Text(shown.formatX(value), style: labelStyle)),
          ),
        ),
        leftTitles: AxisTitles(
          sideTitles: SideTitles(
            showTitles: true,
            reservedSize: 56,
            getTitlesWidget: (value, meta) => SideTitleWidget(meta: meta, child: Text(shown.formatValue(value), style: labelStyle)),
          ),
        ),
      ),
      lineBarsData: [
        for (var i = 0; i < shown.series.length; i++)
          LineChartBarData(
            spots: [for (final p in shown.series[i].points) FlSpot(p.x, p.y)],
            isCurved: curved,
            color: colors[i],
            barWidth: 2.5,
            dotData: FlDotData(
              show: showDots || markerXs.isNotEmpty,
              checkToShowDot: (spot, _) => showDots || markerXs.contains(spot.x),
              getDotPainter: (spot, percent, bar, index) => FlDotCirclePainter(
                radius: markerXs.contains(spot.x) ? 5 : 3,
                color: colors[i],
                strokeWidth: 2,
                strokeColor: tokens.surface,
              ),
            ),
            belowBarData: BarAreaData(show: area, color: colors[i].withValues(alpha: 0.16)),
          ),
      ],
      lineTouchData: LineTouchData(
        touchTooltipData: LineTouchTooltipData(
          getTooltipColor: (_) => tokens.surface,
          getTooltipItems: (spots) => [
            for (final spot in spots)
              LineTooltipItem(
                '${shown.series[spot.barIndex].label}\n${shown.formatX(spot.x)}  ${shown.formatValue(spot.y)}',
                TextStyle(color: tokens.ink, fontWeight: FontWeight.w600, fontSize: 12),
              ),
          ],
        ),
      ),
    );
  }
}

/// A line chart with the area under each line filled.
class HqAreaChart extends StatelessWidget {
  const HqAreaChart({super.key, required this.data, this.height = 220, this.curved = true, this.showLegend = true, this.emptyText = ''});

  final HqChartData data;
  final double height;
  final bool curved;
  final bool showLegend;
  final String emptyText;

  @override
  Widget build(BuildContext context) =>
      HqLineChart(data: data, height: height, curved: curved, showLegend: showLegend, emptyText: emptyText, area: true);
}

/// A tiny trend line without axes or touch, for tiles and list rows. Uses the first series.
class HqSparkline extends StatelessWidget {
  const HqSparkline({super.key, required this.values, this.height = 32, this.width, this.color, this.semanticsLabel = ''});

  final List<double> values;
  final double height;
  final double? width;
  final Color? color;

  /// Description for screen readers (e.g. "Last 6 months, up 4 percent"), already translated.
  final String semanticsLabel;

  @override
  Widget build(BuildContext context) {
    if (values.length < 2) return SizedBox(height: height, width: width);
    final tokens = context.hq;
    final line = color ?? tokens.chartCategorical.first;
    final points = hqDownsample([for (var i = 0; i < values.length; i++) HqChartPoint(x: i.toDouble(), y: values[i])], 60);
    var minY = points.map((p) => p.y).reduce((a, b) => a < b ? a : b);
    var maxY = points.map((p) => p.y).reduce((a, b) => a > b ? a : b);
    if (minY == maxY) {
      minY -= 1;
      maxY += 1;
    }
    return Semantics(
      image: true,
      label: semanticsLabel,
      child: ExcludeSemantics(
        child: SizedBox(
          height: height,
          width: width,
          child: LineChart(
            LineChartData(
              minY: minY,
              maxY: maxY,
              gridData: const FlGridData(show: false),
              borderData: FlBorderData(show: false),
              titlesData: const FlTitlesData(show: false),
              lineTouchData: const LineTouchData(enabled: false),
              lineBarsData: [
                LineChartBarData(
                  spots: [for (final p in points) FlSpot(p.x, p.y)],
                  isCurved: true,
                  color: line,
                  barWidth: 2,
                  dotData: const FlDotData(show: false),
                  belowBarData: BarAreaData(show: true, color: line.withValues(alpha: 0.12)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// One NAV observation.
@immutable
class HqNavPoint {
  const HqNavPoint({required this.at, required this.nav, this.valuation = false});

  final DateTime at;

  /// NAV per unit on [at].
  final double nav;

  /// True when this point is a valuation (a report-backed NAV update), drawn with a marker.
  final bool valuation;
}

/// NAV per unit over time with a marker on each valuation date (the investment app's "price chart"; there are no candles).
class HqNavChart extends StatelessWidget {
  const HqNavChart({
    super.key,
    required this.points,
    required this.seriesLabel,
    required this.valuationLabel,
    this.currency = 'INR',
    this.height = 220,
    this.dateFormatter,
    this.semanticsLabel = '',
    this.emptyText = '',
  });

  final List<HqNavPoint> points;

  /// Legend and tooltip name of the NAV line, e.g. "NAV per unit".
  final String seriesLabel;

  /// Legend name of the markers, e.g. "Valuation".
  final String valuationLabel;
  final String currency;
  final double height;

  /// Formats a date on the axis and in the tooltip; defaults to `yyyy-MM-dd`.
  final String Function(DateTime)? dateFormatter;
  final String semanticsLabel;
  final String emptyText;

  static const int _msPerDay = 86400000;

  @override
  Widget build(BuildContext context) {
    final sorted = [...points]..sort((a, b) => a.at.compareTo(b.at));
    double dayOf(DateTime at) => at.toUtc().millisecondsSinceEpoch / _msPerDay;
    final format = dateFormatter ?? (DateTime d) => hqFormatDate(d.toUtc());
    final data = HqChartData(
      series: [
        HqChartSeries(id: 'nav', label: seriesLabel, points: [for (final p in sorted) HqChartPoint(x: dayOf(p.at), y: p.nav)]),
      ],
      valueFormatter: hqAmountFormatter(currency: currency, compact: true),
      xFormatter: (x) => format(DateTime.fromMillisecondsSinceEpoch((x * _msPerDay).round(), isUtc: true)),
      semanticsLabel: semanticsLabel,
    );
    final markers = {for (final p in sorted) if (p.valuation) dayOf(p.at)};
    final tokens = context.hq;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        HqLineChart(data: data, height: height, emptyText: emptyText, markerXs: markers, showLegend: false, area: true),
        if (markers.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: HqSpacing.md),
            child: HqChartLegend(items: [
              HqLegendItem(label: seriesLabel, color: tokens.chartCategorical.first),
              HqLegendItem(label: valuationLabel, color: tokens.chartCategorical.first),
            ]),
          ),
      ],
    );
  }
}
