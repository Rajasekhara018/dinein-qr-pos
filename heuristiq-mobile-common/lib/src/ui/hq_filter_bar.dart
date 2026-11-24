import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// A panel holding filter controls above a list (the counterpart of `hq-filter-bar`).
///
/// With [collapsible] the controls fold behind a [toggleText] header; [expanded] is the starting state.
class HqFilterBar extends StatefulWidget {
  const HqFilterBar({
    super.key,
    required this.children,
    this.collapsible = false,
    this.dense = false,
    this.toggleText = '',
    this.expanded = false,
    this.onExpandedChanged,
    this.semanticLabel,
  });

  final List<Widget> children;
  final bool collapsible;
  final bool dense;
  final String toggleText;
  final bool expanded;
  final ValueChanged<bool>? onExpandedChanged;
  final String? semanticLabel;

  @override
  State<HqFilterBar> createState() => _HqFilterBarState();
}

class _HqFilterBarState extends State<HqFilterBar> {
  late bool _expanded = widget.expanded;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final gap = widget.dense ? HqSpacing.sm : HqSpacing.md;
    final body = Padding(
      padding: EdgeInsets.all(gap),
      child: Wrap(spacing: gap, runSpacing: gap, crossAxisAlignment: WrapCrossAlignment.end, children: widget.children),
    );
    return Semantics(
      container: true,
      label: widget.semanticLabel,
      child: Container(
        margin: const EdgeInsets.only(bottom: HqSpacing.md),
        decoration: BoxDecoration(
          color: tokens.surface,
          borderRadius: BorderRadius.circular(HqRadius.panel),
          border: Border.all(color: tokens.fieldBorder),
        ),
        child: widget.collapsible
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  InkWell(
                    borderRadius: BorderRadius.circular(HqRadius.panel),
                    onTap: () {
                      setState(() => _expanded = !_expanded);
                      widget.onExpandedChanged?.call(_expanded);
                    },
                    child: Padding(
                      padding: EdgeInsets.all(gap),
                      child: Row(
                        children: [
                          Icon(Icons.filter_list, color: tokens.muted),
                          const SizedBox(width: HqSpacing.sm),
                          Expanded(child: Text(widget.toggleText, style: Theme.of(context).textTheme.labelLarge)),
                          Icon(_expanded ? Icons.expand_less : Icons.expand_more, color: tokens.muted),
                        ],
                      ),
                    ),
                  ),
                  AnimatedSize(
                    duration: const Duration(milliseconds: 180),
                    alignment: Alignment.topCenter,
                    child: _expanded ? body : const SizedBox(width: double.infinity),
                  ),
                ],
              )
            : body,
      ),
    );
  }
}
