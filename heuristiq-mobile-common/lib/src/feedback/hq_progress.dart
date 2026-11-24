import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';

/// Spinner sizes (the web `HqSpinnerSize`).
enum HqSpinnerSize {
  sm(16),
  md(24),
  lg(40);

  const HqSpinnerSize(this.dimension);

  final double dimension;
}

/// An indeterminate spinner (the counterpart of `hq-spinner`). [label] is announced to screen readers.
class HqSpinner extends StatelessWidget {
  const HqSpinner({super.key, this.size = HqSpinnerSize.md, this.label = '', this.color});

  final HqSpinnerSize size;
  final String label;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    return Semantics(
      label: label,
      liveRegion: true,
      child: SizedBox(
        width: size.dimension,
        height: size.dimension,
        child: CircularProgressIndicator(strokeWidth: size.dimension / 8 < 2 ? 2 : size.dimension / 8, color: color ?? tokens.primary),
      ),
    );
  }
}

/// A ring showing a share of a whole, with the value text in the middle. Without [fraction] it spins (indeterminate).
class HqCircularProgress extends StatelessWidget {
  const HqCircularProgress({super.key, this.fraction, this.valueText = '', this.label = '', this.size = 72, this.thickness = 8, this.color});

  /// 0 to 1; null for indeterminate.
  final double? fraction;

  /// Text in the middle (already formatted, e.g. "60%").
  final String valueText;

  /// Screen-reader description (e.g. "KYC completion").
  final String label;
  final double size;
  final double thickness;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final clamped = fraction?.clamp(0, 1).toDouble();
    final fill = color ?? ((clamped ?? 0) >= 1 ? tokens.success : tokens.primary);
    return Semantics(
      label: label,
      value: clamped == null ? null : '${(clamped * 100).round()}%',
      child: ExcludeSemantics(
        child: SizedBox(
          width: size,
          height: size,
          child: Stack(
            alignment: Alignment.center,
            children: [
              SizedBox.expand(
                child: CircularProgressIndicator(value: clamped, strokeWidth: thickness, color: fill, backgroundColor: tokens.fieldBorder),
              ),
              if (valueText.isNotEmpty) Text(valueText, style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
            ],
          ),
        ),
      ),
    );
  }
}
