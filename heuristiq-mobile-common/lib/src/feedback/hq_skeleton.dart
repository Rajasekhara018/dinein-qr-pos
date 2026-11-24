import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Shape of a [HqSkeleton] (the web `HqSkeletonVariant`).
enum HqSkeletonVariant { text, block, circle, card, list, table }

/// Placeholder shown while content loads, with a shimmer that stops when the system asks for reduced motion
/// (the counterpart of `hq-skeleton`). [label] is announced to screen readers ("Loading").
class HqSkeleton extends StatefulWidget {
  const HqSkeleton({
    super.key,
    this.variant = HqSkeletonVariant.text,
    this.lines = 3,
    this.rows = 4,
    this.cols = 3,
    this.width,
    this.height,
    this.label = '',
  });

  final HqSkeletonVariant variant;

  /// Text lines ([HqSkeletonVariant.text]).
  final int lines;

  /// Rows of [HqSkeletonVariant.list] and [HqSkeletonVariant.table].
  final int rows;

  /// Columns of [HqSkeletonVariant.table].
  final int cols;
  final double? width;
  final double? height;
  final String label;

  @override
  State<HqSkeleton> createState() => _HqSkeletonState();
}

class _HqSkeletonState extends State<HqSkeleton> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 1400));

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final reduce = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    if (reduce) {
      _controller.stop();
    } else if (!_controller.isAnimating) {
      _controller.repeat();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final base = tokens.fieldBorder;
    return Semantics(
      label: widget.label,
      container: true,
      child: ExcludeSemantics(
        child: AnimatedBuilder(
          animation: _controller,
          builder: (context, _) {
            final color = Color.lerp(base, base.withValues(alpha: base.a * 0.35), (0.5 - (_controller.value - 0.5).abs()) * 2)!;
            return _shape(color);
          },
        ),
      ),
    );
  }

  Widget _bar(Color color, {double? width, double height = 12, double radius = 6}) =>
      Container(width: width, height: height, decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(radius)));

  Widget _shape(Color color) {
    switch (widget.variant) {
      case HqSkeletonVariant.text:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (var i = 0; i < widget.lines; i++)
              Padding(
                padding: const EdgeInsets.only(bottom: HqSpacing.sm),
                child: LayoutBuilder(builder: (context, c) => _bar(color, width: i == widget.lines - 1 && widget.lines > 1 && c.maxWidth.isFinite ? c.maxWidth * 0.6 : widget.width)),
              ),
          ],
        );
      case HqSkeletonVariant.block:
        return _bar(color, width: widget.width, height: widget.height ?? 96, radius: HqRadius.control);
      case HqSkeletonVariant.circle:
        final size = widget.width ?? widget.height ?? 40;
        return Container(width: size, height: size, decoration: BoxDecoration(color: color, shape: BoxShape.circle));
      case HqSkeletonVariant.card:
        return _bar(color, width: widget.width, height: widget.height ?? 140, radius: HqRadius.panel);
      case HqSkeletonVariant.list:
        return Column(
          children: [
            for (var i = 0; i < widget.rows; i++)
              Padding(
                padding: const EdgeInsets.only(bottom: HqSpacing.md),
                child: Row(children: [
                  Container(width: 40, height: 40, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
                  const SizedBox(width: HqSpacing.md),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [_bar(color), const SizedBox(height: HqSpacing.xs), _bar(color, height: 10)])),
                ]),
              ),
          ],
        );
      case HqSkeletonVariant.table:
        return Column(
          children: [
            for (var r = 0; r < widget.rows; r++)
              Padding(
                padding: const EdgeInsets.only(bottom: HqSpacing.sm),
                child: Row(children: [for (var c = 0; c < widget.cols; c++) Expanded(child: Padding(padding: const EdgeInsets.only(right: HqSpacing.sm), child: _bar(color)))]),
              ),
          ],
        );
    }
  }
}
