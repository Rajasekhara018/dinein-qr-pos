import 'package:flutter/material.dart';

import '../theme/hq_mobile_finish.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Accent of a stat card (the web `HqStatTone`).
enum HqStatTone { blue, purple, orange, green }

/// One headline number.
@immutable
class HqStatCardItem {
  const HqStatCardItem({required this.label, required this.value, required this.icon, this.tone = HqStatTone.blue});

  /// Already-translated label, e.g. "Total invested".
  final String label;

  /// A number or an already-formatted string.
  final Object value;
  final IconData icon;
  final HqStatTone tone;
}

/// Row of headline number cards shown above a list (the counterpart of `hq-stat-cards`).
///
/// With [fill] the cards share the row evenly (two per row on a phone, four on a tablet); otherwise each keeps a fixed width
/// and wraps. [flush] removes the space below the row.
class HqStatCards extends StatelessWidget {
  const HqStatCards({super.key, required this.stats, this.fill = false, this.flush = false});

  final List<HqStatCardItem> stats;
  final bool fill;
  final bool flush;

  static const double _fixedWidth = 160;

  static Color toneColor(HqStatTone tone) => switch (tone) {
        HqStatTone.blue => const Color(0xFF2563EB),
        HqStatTone.purple => const Color(0xFF7C3AED),
        HqStatTone.orange => const Color(0xFFEA580C),
        HqStatTone.green => const Color(0xFF15803D),
      };

  @override
  Widget build(BuildContext context) {
    if (stats.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: EdgeInsets.only(bottom: flush ? 0 : HqSpacing.lg),
      child: LayoutBuilder(
        builder: (context, constraints) {
          var cardWidth = _fixedWidth;
          if (fill && constraints.maxWidth.isFinite) {
            final perRow = constraints.maxWidth >= 600 ? 4 : 2;
            final columns = stats.length < perRow ? stats.length : perRow;
            cardWidth = (constraints.maxWidth - HqSpacing.md * (columns - 1)) / columns;
          }
          return Wrap(
            spacing: HqSpacing.md,
            runSpacing: HqSpacing.md,
            children: [for (final stat in stats) SizedBox(width: cardWidth, child: _StatCard(item: stat))],
          );
        },
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.item});

  final HqStatCardItem item;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final accent = HqStatCards.toneColor(item.tone);
    final text = Theme.of(context).textTheme;
    return Semantics(
      container: true,
      label: '${item.label}: ${item.value}',
      excludeSemantics: true,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: tokens.surface,
          // The mobile finish: softly lifted (shadow in light, hairline in dark) instead of an outline.
          border: HqMobile.hairline(context),
          borderRadius: BorderRadius.circular(HqMobile.cardRadius),
          boxShadow: HqMobile.softShadow(context),
        ),
        child: Padding(
          padding: const EdgeInsets.all(HqSpacing.md),
          child: Row(
            children: [
              DecoratedBox(
                decoration: BoxDecoration(color: accent.withValues(alpha: 0.12), shape: BoxShape.circle),
                child: Padding(padding: const EdgeInsets.all(HqSpacing.sm), child: Icon(item.icon, color: accent, size: 20)),
              ),
              const SizedBox(width: HqSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('${item.value}', style: text.titleLarge?.copyWith(fontWeight: FontWeight.w700), maxLines: 1, overflow: TextOverflow.ellipsis),
                    Text(item.label, style: text.bodySmall?.copyWith(color: tokens.muted), maxLines: 2, overflow: TextOverflow.ellipsis),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
