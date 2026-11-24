import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Turns a slider value into the text shown above it and read by screen readers.
typedef HqSliderFormatter = String Function(double value);

SliderThemeData _sliderTheme(BuildContext context) {
  final tokens = context.hq;
  return SliderTheme.of(context).copyWith(
    activeTrackColor: tokens.primary,
    inactiveTrackColor: tokens.fieldBorder,
    thumbColor: tokens.primary,
    overlayColor: tokens.primarySoft,
    valueIndicatorColor: tokens.primary,
    valueIndicatorTextStyle: TextStyle(color: tokens.onPrimary),
  );
}

/// A single-value slider with a label, the formatted value and the min and max at the ends (an amount to invest, a number of
/// units). With [divisions] it snaps to steps ((max - min) / divisions). [format] renders the value (`hqFormatMoney` for an
/// amount); the same text is the semantics value.
class HqSlider extends StatelessWidget {
  const HqSlider({super.key, required this.value, required this.min, required this.max, required this.onChanged, this.divisions, this.label = '', this.format, this.enabled = true, this.onChangeEnd});

  final double value;
  final double min;
  final double max;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;
  final int? divisions;
  final String label;
  final HqSliderFormatter? format;
  final bool enabled;

  String _fmt(double v) => (format ?? (x) => x.toStringAsFixed(x == x.roundToDouble() ? 0 : 2))(v);

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final clamped = value.clamp(min, max).toDouble();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(child: Text(label, style: text.labelLarge?.copyWith(fontWeight: FontWeight.w600))),
            Directionality(textDirection: TextDirection.ltr, child: Text(_fmt(clamped), style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700))),
          ],
        ),
        SliderTheme(
          data: _sliderTheme(context),
          child: Slider(
            value: clamped,
            min: min,
            max: max,
            divisions: divisions,
            semanticFormatterCallback: _fmt,
            label: _fmt(clamped),
            onChanged: enabled ? onChanged : null,
            onChangeEnd: onChangeEnd,
          ),
        ),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Directionality(textDirection: TextDirection.ltr, child: Text(_fmt(min), style: text.bodySmall?.copyWith(color: tokens.muted))),
            Directionality(textDirection: TextDirection.ltr, child: Text(_fmt(max), style: text.bodySmall?.copyWith(color: tokens.muted))),
          ],
        ),
      ],
    );
  }
}

/// A two-handle slider for a range (a minimum and maximum investment, a price band).
class HqRangeSlider extends StatelessWidget {
  const HqRangeSlider({super.key, required this.start, required this.end, required this.min, required this.max, required this.onChanged, this.divisions, this.label = '', this.format, this.enabled = true});

  final double start;
  final double end;
  final double min;
  final double max;
  final ValueChanged<RangeValues>? onChanged;
  final int? divisions;
  final String label;
  final HqSliderFormatter? format;
  final bool enabled;

  String _fmt(double v) => (format ?? (x) => x.toStringAsFixed(x == x.roundToDouble() ? 0 : 2))(v);

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final values = RangeValues(start.clamp(min, max).toDouble(), end.clamp(min, max).toDouble());
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(child: Text(label, style: text.labelLarge?.copyWith(fontWeight: FontWeight.w600))),
            Directionality(textDirection: TextDirection.ltr, child: Text('${_fmt(values.start)} - ${_fmt(values.end)}', style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700))),
          ],
        ),
        SliderTheme(
          data: _sliderTheme(context),
          child: RangeSlider(
            values: values,
            min: min,
            max: max,
            divisions: divisions,
            labels: RangeLabels(_fmt(values.start), _fmt(values.end)),
            onChanged: enabled ? onChanged : null,
          ),
        ),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Directionality(textDirection: TextDirection.ltr, child: Text(_fmt(min), style: text.bodySmall?.copyWith(color: tokens.muted))),
            Directionality(textDirection: TextDirection.ltr, child: Text(_fmt(max), style: text.bodySmall?.copyWith(color: tokens.muted))),
          ],
        ),
      ],
    );
  }
}

/// A whole-number stepper with minus and plus buttons (units to buy). Never leaves [min]..[max] and moves by [step].
/// [unitText] shows after the number ("units"); the buttons' labels are passed in for screen readers.
class HqStepperCounter extends StatelessWidget {
  const HqStepperCounter({
    super.key,
    required this.value,
    required this.onChanged,
    this.min = 0,
    this.max = 1000000,
    this.step = 1,
    this.label = '',
    this.unitText = '',
    this.decrementLabel = '',
    this.incrementLabel = '',
    this.enabled = true,
    this.error,
  });

  final int value;
  final ValueChanged<int> onChanged;
  final int min;
  final int max;
  final int step;
  final String label;
  final String unitText;
  final String decrementLabel;
  final String incrementLabel;
  final bool enabled;
  final String? error;

  bool get canDecrement => enabled && value - step >= min;
  bool get canIncrement => enabled && value + step <= max;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    Widget button(IconData icon, String tooltip, VoidCallback? onTap) => IconButton.outlined(
          tooltip: tooltip,
          icon: Icon(icon),
          onPressed: onTap,
          style: IconButton.styleFrom(minimumSize: const Size(HqSizes.minTapTarget, HqSizes.minTapTarget), side: BorderSide(color: onTap == null ? tokens.fieldBorder : tokens.primary), foregroundColor: tokens.primary),
        );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (label.isNotEmpty) Padding(padding: const EdgeInsets.only(bottom: HqSpacing.xs), child: Text(label, style: text.labelLarge?.copyWith(fontWeight: FontWeight.w600))),
        Semantics(
          container: true,
          label: label,
          value: '$value $unitText'.trim(),
          increasedValue: canIncrement ? '${value + step}' : null,
          decreasedValue: canDecrement ? '${value - step}' : null,
          onIncrease: canIncrement ? () => onChanged(value + step) : null,
          onDecrease: canDecrement ? () => onChanged(value - step) : null,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              button(Icons.remove, decrementLabel, canDecrement ? () => onChanged(value - step) : null),
              ConstrainedBox(
                constraints: const BoxConstraints(minWidth: 72),
                child: Column(
                  children: [
                    Text('$value', style: text.headlineSmall?.copyWith(fontWeight: FontWeight.w700), textAlign: TextAlign.center),
                    if (unitText.isNotEmpty) Text(unitText, style: text.bodySmall?.copyWith(color: tokens.muted)),
                  ],
                ),
              ),
              button(Icons.add, incrementLabel, canIncrement ? () => onChanged(value + step) : null),
            ],
          ),
        ),
        if (error != null && error!.isNotEmpty) Padding(padding: const EdgeInsets.only(top: HqSpacing.xs), child: Text(error!, style: text.bodySmall?.copyWith(color: tokens.warn))),
      ],
    );
  }
}
