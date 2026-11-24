import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// A Flutter [Form] wrapper so app screens never import `Form` themselves.
class HqForm extends StatelessWidget {
  const HqForm({super.key, required this.child, this.formKey, this.onChanged, this.autovalidateMode = AutovalidateMode.disabled});

  final Widget child;
  final GlobalKey<FormState>? formKey;
  final VoidCallback? onChanged;
  final AutovalidateMode autovalidateMode;

  @override
  Widget build(BuildContext context) =>
      Form(key: formKey, onChanged: onChanged, autovalidateMode: autovalidateMode, child: child);
}

/// Makes a [HqFormGrid] child span [span] columns (the web `.span-2` style).
class HqFormGridItem extends StatelessWidget {
  const HqFormGridItem({super.key, required this.child, this.span = 2});

  final Widget child;
  final int span;

  @override
  Widget build(BuildContext context) => child;
}

/// Lays form fields out in up to [cols] columns; on a narrow screen it falls back to fewer columns, down to one
/// (the counterpart of `hq-form-grid`).
class HqFormGrid extends StatelessWidget {
  const HqFormGrid({
    super.key,
    required this.children,
    this.cols = 1,
    this.minColumnWidth = 280,
    this.gap = HqSpacing.lg,
  });

  final List<Widget> children;

  /// Most columns to use (1 to 4).
  final int cols;

  /// A column is never narrower than this; fewer columns are used when the width does not allow [cols].
  final double minColumnWidth;
  final double gap;

  /// How many columns fit in [width].
  static int columnsFor({required double width, required int cols, required double minColumnWidth, required double gap}) {
    if (!width.isFinite) return 1;
    final fit = ((width + gap) / (minColumnWidth + gap)).floor();
    final limit = cols.clamp(1, 4);
    return fit.clamp(1, limit);
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = columnsFor(width: constraints.maxWidth, cols: cols, minColumnWidth: minColumnWidth, gap: gap);
        final width = constraints.maxWidth.isFinite ? constraints.maxWidth : minColumnWidth;
        final unit = (width - gap * (columns - 1)) / columns;
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: [
            for (final child in children)
              SizedBox(
                width: _span(child, columns) * unit + (_span(child, columns) - 1) * gap,
                child: child,
              ),
          ],
        );
      },
    );
  }

  int _span(Widget child, int columns) => child is HqFormGridItem ? child.span.clamp(1, columns) : 1;
}

/// Where the buttons of a [HqFormActions] row sit.
enum HqFormActionsAlign { start, center, end, spread }

/// The button row under a form: Save / Cancel (the counterpart of `hq-form-actions`).
///
/// With [sticky] the row is pinned (put it in `Scaffold.bottomNavigationBar`) with a top border and safe-area padding.
class HqFormActions extends StatelessWidget {
  const HqFormActions({super.key, required this.children, this.align = HqFormActionsAlign.end, this.sticky = false});

  final List<Widget> children;
  final HqFormActionsAlign align;
  final bool sticky;

  WrapAlignment get _alignment => switch (align) {
        HqFormActionsAlign.start => WrapAlignment.start,
        HqFormActionsAlign.center => WrapAlignment.center,
        HqFormActionsAlign.end => WrapAlignment.end,
        HqFormActionsAlign.spread => WrapAlignment.spaceBetween,
      };

  @override
  Widget build(BuildContext context) {
    final row = SizedBox(
      width: double.infinity,
      child: Wrap(alignment: _alignment, spacing: HqSpacing.md, runSpacing: HqSpacing.sm, children: children),
    );
    if (!sticky) return Padding(padding: const EdgeInsets.only(top: HqSpacing.lg), child: row);
    final tokens = context.hq;
    return Material(
      color: tokens.surface,
      child: DecoratedBox(
        decoration: BoxDecoration(border: Border(top: BorderSide(color: tokens.fieldBorder))),
        child: SafeArea(top: false, child: Padding(padding: const EdgeInsets.all(HqSpacing.md), child: row)),
      ),
    );
  }
}
