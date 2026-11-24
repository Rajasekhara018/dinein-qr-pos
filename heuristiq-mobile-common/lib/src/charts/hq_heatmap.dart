import 'package:flutter/material.dart';

import '../core/hq_format.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_chart_data.dart';

/// Calendar heatmap: one month grid per entry of [months], each day coloured by its value on the theme's sequential ramp
/// (distributions received per day, for example). Days without a value stay neutral.
///
/// Weeks start on Monday. All texts come from the caller: [monthTitle] names a month, [weekdayLabels] are seven short
/// names starting Monday, [lessLabel] / [moreLabel] caption the colour scale.
class HqHeatmap extends StatelessWidget {
  const HqHeatmap({
    super.key,
    required this.values,
    required this.months,
    required this.monthTitle,
    required this.weekdayLabels,
    this.lessLabel = '',
    this.moreLabel = '',
    this.valueFormatter,
    this.onDayTap,
    this.semanticsLabel = '',
  }) : assert(weekdayLabels.length == 7, 'weekdayLabels needs seven entries, Monday first');

  /// Value per day; only the date part of each key is used.
  final Map<DateTime, double> values;

  /// The months to draw (the first day of each).
  final List<DateTime> months;
  final String Function(DateTime month) monthTitle;
  final List<String> weekdayLabels;
  final String lessLabel;
  final String moreLabel;
  final HqValueFormatter? valueFormatter;
  final void Function(DateTime day, double value)? onDayTap;
  final String semanticsLabel;

  static DateTime _day(DateTime d) => DateTime.utc(d.year, d.month, d.day);

  /// Index (0 to steps-1) of [value] on a ramp of [steps] colours for a series whose largest value is [max]; 0 for no value.
  static int bucket(double value, double max, int steps) {
    if (value <= 0 || max <= 0) return 0;
    return (value / max * (steps - 1)).ceil().clamp(1, steps - 1);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final byDay = {for (final e in values.entries) _day(e.key): e.value};
    final max = byDay.values.fold<double>(0, (a, b) => a > b ? a : b);
    final ramp = tokens.chartSequential;
    final format = valueFormatter ?? (double v) => v.toStringAsFixed(2);
    final total = byDay.values.fold<double>(0, (a, b) => a + b);
    return Semantics(
      container: true,
      label: semanticsLabel.isEmpty ? null : '$semanticsLabel ${format(total)}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final month in months) ...[
            Padding(
              padding: const EdgeInsets.only(top: HqSpacing.sm, bottom: HqSpacing.xs),
              child: Text(monthTitle(month), style: text.labelLarge?.copyWith(fontWeight: FontWeight.w700)),
            ),
            _month(context, tokens, month, byDay, max, ramp, format),
          ],
          if (lessLabel.isNotEmpty || moreLabel.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: HqSpacing.md),
              child: Row(
                children: [
                  Text(lessLabel, style: text.labelSmall?.copyWith(color: tokens.muted)),
                  const SizedBox(width: HqSpacing.xs),
                  for (var i = 1; i < ramp.length; i++) Container(width: 14, height: 14, margin: const EdgeInsets.only(right: 2), decoration: BoxDecoration(color: ramp[i], borderRadius: BorderRadius.circular(3))),
                  const SizedBox(width: HqSpacing.xs),
                  Text(moreLabel, style: text.labelSmall?.copyWith(color: tokens.muted)),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _month(BuildContext context, HqTokens tokens, DateTime month, Map<DateTime, double> byDay, double max, List<Color> ramp, HqValueFormatter format) {
    final text = Theme.of(context).textTheme;
    final first = DateTime.utc(month.year, month.month, 1);
    final daysInMonth = DateTime.utc(month.year, month.month + 1, 0).day;
    final leading = first.weekday - 1; // Monday first
    final cells = <Widget>[
      for (var i = 0; i < 7; i++)
        Center(child: Text(weekdayLabels[i], style: text.labelSmall?.copyWith(color: tokens.muted))),
      for (var i = 0; i < leading; i++) const SizedBox.shrink(),
      for (var d = 1; d <= daysInMonth; d++) _cell(context, tokens, DateTime.utc(month.year, month.month, d), byDay, max, ramp, format),
    ];
    return GridView.count(
      crossAxisCount: 7,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: 3,
      crossAxisSpacing: 3,
      children: cells,
    );
  }

  Widget _cell(BuildContext context, HqTokens tokens, DateTime day, Map<DateTime, double> byDay, double max, List<Color> ramp, HqValueFormatter format) {
    final value = byDay[day];
    final index = value == null ? 0 : bucket(value, max, ramp.length);
    final color = index == 0 ? tokens.fieldBorder : ramp[index];
    final dark = ThemeData.estimateBrightnessForColor(color) == Brightness.dark;
    final label = value == null ? hqFormatDate(day) : '${hqFormatDate(day)}: ${format(value)}';
    final cell = Container(
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(4)),
      alignment: Alignment.center,
      child: Text('${day.day}', style: TextStyle(fontSize: 10, color: dark ? Colors.white : tokens.ink)),
    );
    return Semantics(
      label: label,
      button: value != null && onDayTap != null,
      excludeSemantics: true,
      child: Tooltip(
        message: label,
        child: value != null && onDayTap != null ? InkWell(onTap: () => onDayTap!(day, value), child: cell) : cell,
      ),
    );
  }
}
