import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../ui/hq_amount.dart';

/// Turns a number into the text a chart shows (axis labels, tooltips, legends).
typedef HqValueFormatter = String Function(double value);

/// One point of a series. On a category chart `x` is the category index; on a time chart it is an epoch day (or any
/// monotonic number) formatted by [HqChartData.xFormatter].
@immutable
class HqChartPoint {
  const HqChartPoint({required this.x, required this.y, this.label});

  final double x;
  final double y;

  /// Optional text for this point (a tooltip heading); already translated or API-provided.
  final String? label;
}

/// One named line, bar set, slice group or radar polygon.
@immutable
class HqChartSeries {
  const HqChartSeries({required this.id, required this.label, required this.points, this.color});

  /// Stable id; the colour slot follows the order of the series, never the id, so a filter that drops a series does not
  /// repaint the others only when the caller keeps the remaining order.
  final String id;

  /// Already-translated name shown in the legend and tooltip.
  final String label;
  final List<HqChartPoint> points;

  /// Overrides the palette slot (use sparingly: colours come from the theme tokens).
  final Color? color;

  double get total => points.fold<double>(0, (sum, p) => sum + p.y);
}

/// The one data model every chart accepts, so a screen can swap a line for bars or a donut without remapping.
///
/// - Line, area, sparkline, scatter: each series is a line or cloud of points.
/// - Bars: points are matched to [xLabels] by position; series are grouped or stacked.
/// - Pie and donut: one series with several points gives a slice per point (labels from [xLabels]); several series give a
///   slice per series (its total).
/// - Radar: [xLabels] are the axes, points the values.
///
/// Every text is passed in: [semanticsLabel] describes the chart for screen readers, [xLabels] name the categories.
@immutable
class HqChartData {
  const HqChartData({
    required this.series,
    this.xLabels = const <String>[],
    this.valueFormatter,
    this.xFormatter,
    this.semanticsLabel = '',
  });

  /// One series from plain values; x is 0, 1, 2...
  factory HqChartData.fromValues({
    required String id,
    required String label,
    required List<double> values,
    List<String> xLabels = const <String>[],
    HqValueFormatter? valueFormatter,
    HqValueFormatter? xFormatter,
    String semanticsLabel = '',
  }) {
    return HqChartData(
      series: [
        HqChartSeries(id: id, label: label, points: [for (var i = 0; i < values.length; i++) HqChartPoint(x: i.toDouble(), y: values[i])]),
      ],
      xLabels: xLabels,
      valueFormatter: valueFormatter,
      xFormatter: xFormatter,
      semanticsLabel: semanticsLabel,
    );
  }

  final List<HqChartSeries> series;
  final List<String> xLabels;

  /// Formats y values; defaults to a plain number.
  final HqValueFormatter? valueFormatter;

  /// Formats x values (dates on a time chart); defaults to the matching entry of [xLabels], else the number.
  final HqValueFormatter? xFormatter;
  final String semanticsLabel;

  bool get isEmpty => series.every((s) => s.points.isEmpty);

  double get minX => _all.isEmpty ? 0 : _all.map((p) => p.x).reduce((a, b) => a < b ? a : b);
  double get maxX => _all.isEmpty ? 0 : _all.map((p) => p.x).reduce((a, b) => a > b ? a : b);
  double get minY => _all.isEmpty ? 0 : _all.map((p) => p.y).reduce((a, b) => a < b ? a : b);
  double get maxY => _all.isEmpty ? 0 : _all.map((p) => p.y).reduce((a, b) => a > b ? a : b);

  Iterable<HqChartPoint> get _all => series.expand((s) => s.points);

  /// The most points any series has (the category count of a bar chart).
  int get categoryCount => series.isEmpty ? 0 : series.map((s) => s.points.length).reduce((a, b) => a > b ? a : b);

  String formatValue(double value) => (valueFormatter ?? _plain)(value);

  String formatX(double x) {
    final formatter = xFormatter;
    if (formatter != null) return formatter(x);
    final index = x.round();
    if ((x - index).abs() < 0.001 && index >= 0 && index < xLabels.length) return xLabels[index];
    return _plain(x);
  }

  /// A copy where no series has more than [maxPoints] points, using largest-triangle-three-buckets so peaks and dips survive.
  HqChartData downsampled(int maxPoints) {
    if (series.every((s) => s.points.length <= maxPoints)) return this;
    return HqChartData(
      series: [
        for (final s in series) HqChartSeries(id: s.id, label: s.label, color: s.color, points: hqDownsample(s.points, maxPoints)),
      ],
      xLabels: xLabels,
      valueFormatter: valueFormatter,
      xFormatter: xFormatter,
      semanticsLabel: semanticsLabel,
    );
  }

  /// Text for screen readers: the [semanticsLabel] then each series with its (first twelve) values. Numbers and punctuation
  /// only; every word comes from the labels passed in.
  String describe() {
    final buffer = StringBuffer(semanticsLabel);
    for (final s in series) {
      if (buffer.isNotEmpty) buffer.write('. ');
      buffer.write('${s.label}: ');
      buffer.write(s.points.take(12).map((p) => '${p.label ?? formatX(p.x)} ${formatValue(p.y)}').join('; '));
    }
    return buffer.toString();
  }

  static String _plain(double v) => v == v.roundToDouble() ? v.toStringAsFixed(0) : v.toStringAsFixed(2);
}

/// Largest-triangle-three-buckets downsampling: keeps the first and last point and, per bucket, the point that forms the
/// largest triangle with its neighbours. Returns [points] unchanged when it is already short enough.
List<HqChartPoint> hqDownsample(List<HqChartPoint> points, int threshold) {
  if (threshold < 3 || points.length <= threshold) return points;
  final sampled = <HqChartPoint>[points.first];
  final bucketSize = (points.length - 2) / (threshold - 2);
  var a = 0;
  for (var i = 0; i < threshold - 2; i++) {
    final nextStart = ((i + 1) * bucketSize).floor() + 1;
    final nextEnd = (((i + 2) * bucketSize).floor() + 1).clamp(0, points.length);
    var avgX = 0.0;
    var avgY = 0.0;
    final nextLength = nextEnd - nextStart;
    for (var j = nextStart; j < nextEnd; j++) {
      avgX += points[j].x;
      avgY += points[j].y;
    }
    avgX = nextLength == 0 ? points.last.x : avgX / nextLength;
    avgY = nextLength == 0 ? points.last.y : avgY / nextLength;
    final start = (i * bucketSize).floor() + 1;
    final end = ((i + 1) * bucketSize).floor() + 1;
    var maxArea = -1.0;
    var chosen = start;
    for (var j = start; j < end; j++) {
      final area = ((points[a].x - avgX) * (points[j].y - points[a].y) - (points[a].x - points[j].x) * (avgY - points[a].y)).abs();
      if (area > maxArea) {
        maxArea = area;
        chosen = j;
      }
    }
    sampled.add(points[chosen]);
    a = chosen;
  }
  sampled.add(points.last);
  return sampled;
}

/// Colour slot lookup: slots 1..8 in a fixed order, then the neutral "Other" colour; never cycled.
extension HqChartColors on BuildContext {
  Color chartColorAt(int index, {Color? override}) {
    if (override != null) return override;
    final tokens = hq;
    return index < tokens.chartCategorical.length ? tokens.chartCategorical[index] : tokens.chartOther;
  }
}

/// A [HqValueFormatter] that prints amounts in [currency] with its symbol, Indian grouping for INR and paise precision;
/// with [compact] it uses lakh/crore (INR) or K/M/B (others) instead, for axes and tight tiles.
HqValueFormatter hqAmountFormatter({String currency = hqAmountFallbackCurrency, bool compact = false, int? decimals, HqCompactUnits units = const HqCompactUnits()}) {
  return (value) {
    if (compact) return hqFormatMoneyCompact(value, currency: currency, units: units);
    return hqFormatMoney(value.toString(), currency: currency, decimals: decimals);
  };
}

/// Suffixes of the compact number format. Defaults are the common abbreviations; pass localized ones when needed.
@immutable
class HqCompactUnits {
  const HqCompactUnits({this.thousand = 'K', this.lakh = 'L', this.crore = 'Cr', this.million = 'M', this.billion = 'B'});

  final String thousand;
  final String lakh;
  final String crore;
  final String million;
  final String billion;
}

/// Compact number for chart labels: Indian style (`12.5 L`, `1.2 Cr`) or international (`12.5K`, `1.2M`). For display only,
/// never for money that is stored or sent (use the minor-unit helpers for that).
String hqFormatCompact(num value, {bool indian = true, int fractionDigits = 1, HqCompactUnits units = const HqCompactUnits()}) {
  final abs = value.abs().toDouble();
  final sign = value < 0 ? '-' : '';
  String scaled(double divisor, String suffix, {String gap = ''}) {
    var text = (abs / divisor).toStringAsFixed(fractionDigits);
    if (text.contains('.')) text = text.replaceFirst(RegExp(r'\.?0+$'), '');
    return '$sign$text$gap$suffix';
  }

  if (indian) {
    if (abs >= 1e7) return scaled(1e7, units.crore, gap: ' ');
    if (abs >= 1e5) return scaled(1e5, units.lakh, gap: ' ');
    if (abs >= 1e3) return scaled(1e3, units.thousand);
  } else {
    if (abs >= 1e9) return scaled(1e9, units.billion);
    if (abs >= 1e6) return scaled(1e6, units.million);
    if (abs >= 1e3) return scaled(1e3, units.thousand);
  }
  var text = abs.toStringAsFixed(abs == abs.roundToDouble() ? 0 : fractionDigits);
  if (text.contains('.')) text = text.replaceFirst(RegExp(r'\.?0+$'), '');
  return '$sign$text';
}

/// [hqFormatCompact] with the currency symbol in front (INR uses lakh and crore).
String hqFormatMoneyCompact(num value, {String currency = hqAmountFallbackCurrency, HqCompactUnits units = const HqCompactUnits()}) {
  final symbol = hqCurrencySymbols[currency.toUpperCase()] ?? '${currency.toUpperCase()} ';
  final body = hqFormatCompact(value.abs(), indian: hqUsesIndianGrouping(currency), units: units);
  return value < 0 ? '-$symbol$body' : '$symbol$body';
}
