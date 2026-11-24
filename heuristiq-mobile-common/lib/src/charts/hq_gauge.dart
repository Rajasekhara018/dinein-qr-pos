import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Semicircle gauge for a share of a whole: funding progress, ownership percent. Drawn with a painter (no chart library).
///
/// [value] runs from 0 to [max]. [valueText] (already formatted, e.g. "72%") and [label] sit in the middle; [semanticsLabel]
/// is what a screen reader says (it should contain the value as words).
class HqGaugeChart extends StatelessWidget {
  const HqGaugeChart({
    super.key,
    required this.value,
    this.max = 100,
    this.valueText = '',
    this.label = '',
    this.semanticsLabel = '',
    this.color,
    this.thickness = 14,
    this.size = 180,
  });

  final double value;
  final double max;
  final String valueText;
  final String label;
  final String semanticsLabel;

  /// Defaults to the primary colour (the success colour once full).
  final Color? color;
  final double thickness;

  /// Width of the gauge; the height is half of it plus room for the text.
  final double size;

  /// The filled share, 0 to 1.
  double get fraction => max <= 0 ? 0 : (value / max).clamp(0, 1).toDouble();

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final arc = color ?? (fraction >= 1 ? tokens.success : tokens.primary);
    return Semantics(
      label: semanticsLabel,
      value: semanticsLabel.isEmpty ? '${(fraction * 100).round()}%' : null,
      child: ExcludeSemantics(
        child: SizedBox(
          width: size,
          height: size / 2 + 28,
          child: Stack(
            alignment: Alignment.bottomCenter,
            children: [
              CustomPaint(
                size: Size(size, size / 2),
                painter: _GaugePainter(fraction: fraction, track: tokens.fieldBorder, arc: arc, thickness: thickness),
              ),
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (valueText.isNotEmpty) Text(valueText, style: text.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
                  if (label.isNotEmpty) Text(label, style: text.bodySmall?.copyWith(color: tokens.muted), textAlign: TextAlign.center, maxLines: 2, overflow: TextOverflow.ellipsis),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _GaugePainter extends CustomPainter {
  const _GaugePainter({required this.fraction, required this.track, required this.arc, required this.thickness});

  final double fraction;
  final Color track;
  final Color arc;
  final double thickness;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Rect.fromLTWH(thickness / 2, thickness / 2, size.width - thickness, (size.height - thickness / 2) * 2);
    final base = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = thickness
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(rect, math.pi, math.pi, false, base..color = track);
    if (fraction > 0) canvas.drawArc(rect, math.pi, math.pi * fraction, false, base..color = arc);
  }

  @override
  bool shouldRepaint(_GaugePainter old) =>
      old.fraction != fraction || old.track != track || old.arc != arc || old.thickness != thickness;
}

/// A horizontal progress bar with an optional label above and value text beside it.
class HqProgressBar extends StatelessWidget {
  const HqProgressBar({
    super.key,
    required this.fraction,
    this.label = '',
    this.valueText = '',
    this.color,
    this.height = 10,
    this.semanticsLabel = '',
    this.indeterminate = false,
  });

  /// Unknown duration: an animated bar instead of a fill ([fraction] and [valueText] are ignored).
  final bool indeterminate;

  /// 0 to 1.
  final double fraction;
  final String label;
  final String valueText;
  final Color? color;
  final double height;
  final String semanticsLabel;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final clamped = fraction.clamp(0, 1).toDouble();
    final fill = color ?? (clamped >= 1 ? tokens.success : tokens.primary);
    if (indeterminate) {
      return Semantics(
        label: semanticsLabel.isEmpty ? label : semanticsLabel,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (label.isNotEmpty) Padding(padding: const EdgeInsets.only(bottom: HqSpacing.xs), child: Text(label, style: text.bodySmall?.copyWith(color: tokens.ink))),
            ClipRRect(
              borderRadius: BorderRadius.circular(HqRadius.pill),
              child: LinearProgressIndicator(minHeight: height, color: color ?? tokens.primary, backgroundColor: tokens.fieldBorder),
            ),
          ],
        ),
      );
    }
    return Semantics(
      label: semanticsLabel.isEmpty ? label : semanticsLabel,
      value: '${(clamped * 100).round()}%',
      child: ExcludeSemantics(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (label.isNotEmpty || valueText.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: HqSpacing.xs),
                child: Row(
                  children: [
                    Expanded(child: Text(label, style: text.bodySmall?.copyWith(color: tokens.ink))),
                    Text(valueText, style: text.bodySmall?.copyWith(fontWeight: FontWeight.w600)),
                  ],
                ),
              ),
            ClipRRect(
              borderRadius: BorderRadius.circular(HqRadius.pill),
              child: LayoutBuilder(
                builder: (context, constraints) => Stack(
                  children: [
                    Container(height: height, color: tokens.fieldBorder),
                    Container(height: height, width: constraints.maxWidth * clamped, color: fill),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Funding progress of a round: a bar, the raised and target amounts and an optional footnote (investors, days left).
/// All texts are passed in already formatted.
class HqFundingProgress extends StatelessWidget {
  const HqFundingProgress({
    super.key,
    required this.fraction,
    required this.title,
    required this.raisedText,
    required this.targetText,
    this.percentText = '',
    this.footnote = '',
    this.semanticsLabel = '',
  });

  final double fraction;
  final String title;
  final String raisedText;
  final String targetText;
  final String percentText;
  final String footnote;
  final String semanticsLabel;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(title, style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: HqSpacing.sm),
        HqProgressBar(fraction: fraction, valueText: percentText, semanticsLabel: semanticsLabel.isEmpty ? title : semanticsLabel),
        const SizedBox(height: HqSpacing.xs),
        Row(
          children: [
            Expanded(child: Text(raisedText, style: text.bodySmall?.copyWith(fontWeight: FontWeight.w600))),
            Text(targetText, style: text.bodySmall?.copyWith(color: tokens.muted)),
          ],
        ),
        if (footnote.isNotEmpty) Text(footnote, style: text.bodySmall?.copyWith(color: tokens.muted)),
      ],
    );
  }
}
