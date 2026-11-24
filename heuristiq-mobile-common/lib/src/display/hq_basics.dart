import 'package:flutter/material.dart';

import '../theme/hq_mobile_finish.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// A round avatar: an image, or the initials of [name] on a categorical colour taken from the name so the same person always
/// gets the same colour (the colour is never the only identifier; [name] is the semantics label).
class HqAvatar extends StatelessWidget {
  const HqAvatar({super.key, required this.name, this.image, this.size = 40, this.semanticsLabel});

  final String name;
  final ImageProvider<Object>? image;
  final double size;
  final String? semanticsLabel;

  /// Up to two initials: first letters of the first and last word.
  static String initialsOf(String name) {
    final words = name.trim().split(RegExp(r'\s+')).where((w) => w.isNotEmpty).toList();
    if (words.isEmpty) return '';
    final first = String.fromCharCode(words.first.runes.first);
    if (words.length == 1) return first.toUpperCase();
    return '$first${String.fromCharCode(words.last.runes.first)}'.toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final slot = name.runes.fold<int>(0, (sum, r) => sum + r) % tokens.chartCategorical.length;
    final background = tokens.chartCategorical[slot];
    final onColor = ThemeData.estimateBrightnessForColor(background) == Brightness.dark ? Colors.white : const Color(0xFF111827);
    return Semantics(
      label: semanticsLabel ?? name,
      image: true,
      child: ExcludeSemantics(
        child: CircleAvatar(
          radius: size / 2,
          backgroundColor: background,
          foregroundImage: image,
          child: Text(initialsOf(name), style: TextStyle(color: onColor, fontSize: size * 0.38, fontWeight: FontWeight.w700)),
        ),
      ),
    );
  }
}

/// A count or dot badge, standalone or pinned to the corner of [child] (the notification count on an icon).
/// Counts above [max] show as `max+`.
class HqBadge extends StatelessWidget {
  const HqBadge({super.key, this.count, this.child, this.max = 99, this.dot = false, this.tone = HqStatusTone.danger, this.semanticsLabel});

  final int? count;
  final Widget? child;
  final int max;

  /// A small dot without a number.
  final bool dot;
  final HqStatusTone tone;

  /// Screen-reader text for the badge (e.g. "3 unread"); already translated.
  final String? semanticsLabel;

  /// The text of a count: the number, or `max+` above [max].
  static String countText(int count, int max) => count > max ? '$max+' : '$count';

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final colors = tokens.tone(tone);
    final solid = tone == HqStatusTone.danger ? tokens.warn : colors.foreground;
    final show = dot || (count != null && count! > 0);
    final badge = !show
        ? const SizedBox.shrink()
        : Semantics(
            label: semanticsLabel,
            child: ExcludeSemantics(
              child: dot
                  ? Container(width: 10, height: 10, decoration: BoxDecoration(color: solid, shape: BoxShape.circle, border: Border.all(color: tokens.surface, width: 1.5)))
                  : Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                      constraints: const BoxConstraints(minWidth: 18, minHeight: 18),
                      decoration: BoxDecoration(color: solid, borderRadius: BorderRadius.circular(HqRadius.pill)),
                      child: Center(
                        widthFactor: 1,
                        child: Text(countText(count!, max), style: TextStyle(color: tone == HqStatusTone.danger ? tokens.onWarn : tokens.surface, fontSize: 11, fontWeight: FontWeight.w700)),
                      ),
                    ),
            ),
          );
    if (child == null) return badge;
    return Stack(
      clipBehavior: Clip.none,
      children: [child!, PositionedDirectional(top: -6, end: -6, child: badge)],
    );
  }
}

/// One choice of a [HqChipGroup].
@immutable
class HqChipOption<T> {
  const HqChipOption({required this.value, required this.label, this.icon, this.disabled = false});

  final T value;
  final String label;
  final IconData? icon;
  final bool disabled;
}

/// A selectable chip (a filter such as "Active"). Selected chips show a check mark as well as the colour.
class HqChip extends StatelessWidget {
  const HqChip({super.key, required this.label, this.selected = false, this.onSelected, this.onDeleted, this.deleteLabel = '', this.icon, this.enabled = true});

  final String label;
  final bool selected;
  final ValueChanged<bool>? onSelected;

  /// Shows a delete button (an applied filter) that calls this.
  final VoidCallback? onDeleted;
  final String deleteLabel;
  final IconData? icon;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final active = enabled && (onSelected != null || onDeleted != null);
    return Semantics(
      button: onSelected != null,
      selected: selected,
      enabled: active,
      label: label,
      excludeSemantics: true,
      onTap: enabled && onSelected != null ? () => onSelected!(!selected) : null,
      // The mobile finish: a soft filled pill (no outline) that fills with the primary when selected.
      child: HqPressable(
        enabled: enabled && onSelected != null,
        child: AnimatedContainer(
          duration: HqMobile.motion(context, HqMobile.slide),
          curve: Curves.easeOutCubic,
          decoration: ShapeDecoration(
            color: selected ? tokens.primary : HqMobile.trackFill(context),
            shape: const StadiumBorder(),
            shadows: selected ? [BoxShadow(color: tokens.primary.withValues(alpha: 0.22), blurRadius: 10, offset: const Offset(0, 4))] : null,
          ),
          child: Material(
            type: MaterialType.transparency,
            child: InkWell(
              customBorder: const StadiumBorder(),
              onTap: enabled && onSelected != null ? hqWithHaptic(() => onSelected!(!selected)) : null,
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: HqSizes.controlHeight),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: HqSpacing.lg),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (selected) ...[Icon(Icons.check, size: 16, color: tokens.onPrimary), const SizedBox(width: HqSpacing.xs)] else if (icon != null) ...[Icon(icon, size: 16, color: tokens.muted), const SizedBox(width: HqSpacing.xs)],
                      Flexible(child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: Theme.of(context).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600, color: enabled ? (selected ? tokens.onPrimary : tokens.ink) : tokens.muted))),
                      if (onDeleted != null) ...[
                        const SizedBox(width: HqSpacing.xs),
                        InkResponse(onTap: enabled ? onDeleted : null, radius: 16, child: Semantics(button: true, label: deleteLabel, child: Icon(Icons.close_rounded, size: 16, color: selected ? tokens.onPrimary : tokens.muted))),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A wrapping row of [HqChip]s for single or multiple choice.
class HqChipGroup<T> extends StatelessWidget {
  const HqChipGroup({super.key, required this.options, required this.values, required this.onChanged, this.multiple = false, this.enabled = true, this.label = ''});

  final List<HqChipOption<T>> options;

  /// The chosen values (at most one without [multiple]).
  final List<T> values;
  final ValueChanged<List<T>> onChanged;
  final bool multiple;
  final bool enabled;

  /// Heading read by screen readers.
  final String label;

  void _toggle(T value, bool on) {
    if (multiple) {
      onChanged(on ? [...values, value] : [for (final v in values) if (v != value) v]);
    } else {
      onChanged(on ? [value] : <T>[]);
    }
  }

  @override
  Widget build(BuildContext context) => Semantics(
        container: true,
        label: label.isEmpty ? null : label,
        child: Wrap(
          spacing: HqSpacing.sm,
          runSpacing: HqSpacing.sm,
          children: [
            for (final o in options)
              HqChip(label: o.label, icon: o.icon, selected: values.contains(o.value), enabled: enabled && !o.disabled, onSelected: (on) => _toggle(o.value, on)),
          ],
        ),
      );
}

/// Padding of a [HqCard] (the web `HqCardPadding`).
enum HqCardPadding { none, sm, md, lg }

/// Look of a [HqCard].
enum HqCardVariant { outlined, elevated, flat }

/// A surface with an optional header (icon, title, subtitle) and action row (the counterpart of `hq-card`). With [onTap]
/// the whole card is a button.
class HqCard extends StatelessWidget {
  const HqCard({
    super.key,
    required this.child,
    this.title = '',
    this.subtitle = '',
    this.icon,
    this.padding = HqCardPadding.md,
    this.variant = HqCardVariant.outlined,
    this.onTap,
    this.actions = const <Widget>[],
  });

  final Widget child;
  final String title;
  final String subtitle;
  final IconData? icon;
  final HqCardPadding padding;
  final HqCardVariant variant;
  final VoidCallback? onTap;

  /// Buttons shown at the end of the header.
  final List<Widget> actions;

  EdgeInsets get _padding => switch (padding) {
        HqCardPadding.none => EdgeInsets.zero,
        HqCardPadding.sm => const EdgeInsets.all(HqSpacing.sm),
        HqCardPadding.md => const EdgeInsets.all(HqSpacing.lg),
        HqCardPadding.lg => const EdgeInsets.all(HqSpacing.xl),
      };

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final hasHeader = title.isNotEmpty || subtitle.isNotEmpty || icon != null || actions.isNotEmpty;
    final body = Padding(
      padding: _padding,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (hasHeader)
            Padding(
              padding: const EdgeInsets.only(bottom: HqSpacing.md),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (icon != null) ...[
                    // The header icon in a soft tinted circle.
                    Container(
                      width: 36,
                      height: 36,
                      decoration: BoxDecoration(color: tokens.primary.withValues(alpha: 0.10), shape: BoxShape.circle),
                      child: Icon(icon, color: tokens.primary, size: 20),
                    ),
                    const SizedBox(width: HqSpacing.md),
                  ],
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (title.isNotEmpty) Semantics(header: true, child: Text(title, style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700))),
                        if (subtitle.isNotEmpty) Text(subtitle, style: text.bodySmall?.copyWith(color: tokens.muted)),
                      ],
                    ),
                  ),
                  ...actions,
                ],
              ),
            ),
          child,
        ],
      ),
    );
    // The mobile finish: rounder, lifted by a soft layered shadow (light) or a hairline (dark) rather than an outline.
    final radius = BorderRadius.circular(HqMobile.cardRadius);
    final flat = variant == HqCardVariant.flat;
    final card = DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: radius,
        boxShadow: flat ? null : HqMobile.softShadow(context, lift: variant == HqCardVariant.elevated ? 1.5 : 1),
      ),
      child: Material(
        color: flat ? tokens.background : tokens.surface,
        shape: RoundedRectangleBorder(
          borderRadius: radius,
          side: !flat && HqMobile.isDark(context) ? BorderSide(color: Colors.white.withValues(alpha: 0.07)) : BorderSide.none,
        ),
        clipBehavior: Clip.antiAlias,
        child: onTap == null ? body : InkWell(onTap: onTap, child: body),
      ),
    );
    return onTap == null ? card : HqPressable(child: card);
  }
}

/// A [HqCard] whose title is required: one section of a long screen (the counterpart of the web section card pattern).
class HqSectionCard extends StatelessWidget {
  const HqSectionCard({super.key, required this.title, required this.child, this.subtitle = '', this.actions = const <Widget>[]});

  final String title;
  final String subtitle;
  final Widget child;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) => HqCard(title: title, subtitle: subtitle, actions: actions, child: child);
}

/// A thin rule, optionally with a centred [label] ("or").
class HqDivider extends StatelessWidget {
  const HqDivider({super.key, this.label = '', this.vertical = false});

  final String label;
  final bool vertical;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    if (vertical) return VerticalDivider(color: tokens.fieldBorder, width: 1);
    if (label.isEmpty) return Divider(color: tokens.fieldBorder, height: 1);
    return Row(
      children: [
        Expanded(child: Divider(color: tokens.fieldBorder)),
        Padding(padding: const EdgeInsets.symmetric(horizontal: HqSpacing.md), child: Text(label, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: tokens.muted))),
        Expanded(child: Divider(color: tokens.fieldBorder)),
      ],
    );
  }
}
