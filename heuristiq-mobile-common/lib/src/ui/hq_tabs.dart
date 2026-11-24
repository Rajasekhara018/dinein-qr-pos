import 'package:flutter/material.dart';

import '../theme/hq_brand_style.dart';
import '../theme/hq_mobile_finish.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// How a tab strip is drawn: underlined tabs, a segmented control, or separate pills (the web `HqTabsAppearance`).
enum HqTabsAppearance { tabs, segmented, pills }

/// Tab strip sizes.
enum HqTabsSize { sm, md }

/// One tab of a [HqTabs] strip.
@immutable
class HqTab<T> {
  const HqTab({required this.value, required this.label, this.icon, this.badge, this.disabled = false});

  final T value;
  final String label;
  final IconData? icon;

  /// A count or short text shown after the label.
  final Object? badge;
  final bool disabled;
}

/// A strip of tabs that selects a value (the counterpart of `hq-tabs`); the content below is the screen's own.
///
/// Mobile finish: segmented and pills are a soft filled track with a selected "thumb" (the primary colour, or the brand
/// gradient of [HqBrandStyle]) that slides between tabs; tabs are an underline that slides. With [block] the tabs share
/// the width and the thumb slides; otherwise each pill fills when selected and the strip scrolls.
class HqTabs<T> extends StatelessWidget {
  const HqTabs({
    super.key,
    required this.tabs,
    required this.value,
    required this.onChanged,
    this.appearance = HqTabsAppearance.segmented,
    this.size = HqTabsSize.md,
    this.block = false,
    this.enabled = true,
    this.semanticLabel,
  });

  final List<HqTab<T>> tabs;
  final T? value;
  final ValueChanged<T>? onChanged;
  final HqTabsAppearance appearance;
  final HqTabsSize size;

  /// Tabs share the full width instead of scrolling.
  final bool block;
  final bool enabled;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final selectedIndex = tabs.indexWhere((tab) => tab.value == value);
    final small = size == HqTabsSize.sm;
    final height = small ? 38.0 : HqSizes.controlHeight;
    final duration = HqMobile.motion(context, HqMobile.slide);
    final underline = appearance == HqTabsAppearance.tabs;
    final trackRadius = appearance == HqTabsAppearance.pills ? HqRadius.pill : HqMobile.controlRadius(context) + 4;

    Widget label(int i) {
      final tab = tabs[i];
      return _TabLabel<T>(
        tab: tab,
        selected: i == selectedIndex,
        block: block,
        appearance: appearance,
        small: small,
        height: height,
        enabled: enabled && !tab.disabled && onChanged != null,
        duration: duration,
        onTap: () => onChanged?.call(tab.value),
      );
    }

    final Widget strip;
    if (block) {
      strip = LayoutBuilder(builder: (context, constraints) {
        final inset = underline ? 0.0 : 4.0;
        final slot = (constraints.maxWidth - inset * 2) / (tabs.isEmpty ? 1 : tabs.length);
        return Container(
          decoration: underline
              ? BoxDecoration(border: Border(bottom: BorderSide(color: context.hq.fieldBorder)))
              : BoxDecoration(color: HqMobile.trackFill(context), borderRadius: BorderRadius.circular(trackRadius)),
          padding: EdgeInsets.all(inset),
          child: Stack(
            children: [
              if (selectedIndex >= 0)
                AnimatedPositioned(
                  duration: duration,
                  curve: Curves.easeOutCubic,
                  left: slot * selectedIndex,
                  width: slot,
                  top: underline ? null : 0,
                  bottom: 0,
                  height: underline ? 2.5 : null,
                  child: underline ? _Underline(color: context.hq.primary) : _Thumb(radius: trackRadius - inset),
                ),
              Row(children: [for (var i = 0; i < tabs.length; i++) Expanded(child: label(i))]),
            ],
          ),
        );
      });
    } else if (appearance == HqTabsAppearance.segmented) {
      // A scrolling segmented control: the track around content-sized tabs, the selected one filled.
      strip = Container(
        decoration: BoxDecoration(color: HqMobile.trackFill(context), borderRadius: BorderRadius.circular(trackRadius)),
        padding: const EdgeInsets.all(4),
        child: SingleChildScrollView(scrollDirection: Axis.horizontal, child: Row(mainAxisSize: MainAxisSize.min, children: [for (var i = 0; i < tabs.length; i++) label(i)])),
      );
    } else {
      strip = DecoratedBox(
        decoration: underline ? BoxDecoration(border: Border(bottom: BorderSide(color: context.hq.fieldBorder))) : const BoxDecoration(),
        child: SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (var i = 0; i < tabs.length; i++)
                Padding(padding: EdgeInsets.only(right: appearance == HqTabsAppearance.pills && i < tabs.length - 1 ? HqSpacing.sm : 0), child: label(i)),
            ],
          ),
        ),
      );
    }
    return Semantics(container: true, label: semanticLabel, child: strip);
  }
}

/// The selected thumb of a block segmented control or pills: primary (or the brand gradient) with a soft lift.
class _Thumb extends StatelessWidget {
  const _Thumb({required this.radius});

  final double radius;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final gradient = context.hqBrand.primaryGradient;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: gradient == null ? tokens.primary : null,
        gradient: gradient == null ? null : LinearGradient(colors: gradient, begin: Alignment.topLeft, end: Alignment.bottomRight),
        borderRadius: BorderRadius.circular(radius),
        boxShadow: [BoxShadow(color: tokens.primary.withValues(alpha: 0.28), blurRadius: 12, offset: const Offset(0, 4))],
      ),
    );
  }
}

class _Underline extends StatelessWidget {
  const _Underline({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) => DecoratedBox(decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(HqRadius.pill)));
}

class _TabLabel<T> extends StatelessWidget {
  const _TabLabel({
    required this.tab,
    required this.selected,
    required this.block,
    required this.appearance,
    required this.small,
    required this.height,
    required this.enabled,
    required this.duration,
    required this.onTap,
  });

  final HqTab<T> tab;
  final bool selected;

  /// Laid out over the strip's sliding thumb or underline (a [HqTabs.block] strip), so it draws no fill of its own.
  final bool block;
  final HqTabsAppearance appearance;
  final bool small;
  final double height;
  final bool enabled;
  final Duration duration;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final underline = appearance == HqTabsAppearance.tabs;
    final filledSelected = selected && !underline;
    final color = !enabled ? tokens.muted.withValues(alpha: 0.5) : (filledSelected ? tokens.onPrimary : (selected ? tokens.primary : tokens.muted));
    final radius = BorderRadius.circular(appearance == HqTabsAppearance.pills ? HqRadius.pill : HqMobile.controlRadius(context));
    final gradient = context.hqBrand.primaryGradient;

    // A content-sized pill or segment draws its own fill; a block tab sits on the sliding thumb.
    BoxDecoration? decoration;
    if (!underline && !block) {
      if (appearance == HqTabsAppearance.pills) {
        decoration = BoxDecoration(
          color: selected ? (gradient == null ? tokens.primary : null) : HqMobile.trackFill(context),
          gradient: selected && gradient != null ? LinearGradient(colors: gradient, begin: Alignment.topLeft, end: Alignment.bottomRight) : null,
          borderRadius: radius,
          boxShadow: selected ? [BoxShadow(color: tokens.primary.withValues(alpha: 0.25), blurRadius: 10, offset: const Offset(0, 4))] : null,
        );
      } else {
        decoration = BoxDecoration(
          color: selected ? (gradient == null ? tokens.primary : null) : Colors.transparent,
          gradient: selected && gradient != null ? LinearGradient(colors: gradient, begin: Alignment.topLeft, end: Alignment.bottomRight) : null,
          borderRadius: radius,
        );
      }
    } else if (underline && !block) {
      decoration = BoxDecoration(border: Border(bottom: BorderSide(color: selected ? tokens.primary : Colors.transparent, width: 2.5)));
    }

    final badge = tab.badge;
    return Semantics(
      button: true,
      selected: selected,
      enabled: enabled,
      label: tab.label,
      excludeSemantics: true,
      onTap: enabled ? hqWithHaptic(onTap) : null,
      child: HqPressable(
        enabled: enabled,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: enabled ? hqWithHaptic(onTap) : null,
          child: AnimatedContainer(
            duration: duration,
            curve: Curves.easeOutCubic,
            constraints: BoxConstraints(minHeight: height - (block && !underline ? 8 : 0)),
            padding: EdgeInsets.symmetric(horizontal: small ? HqSpacing.md : HqSpacing.lg),
            decoration: decoration,
            alignment: Alignment.center,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (tab.icon != null) ...[Icon(tab.icon, size: 18, color: color), const SizedBox(width: HqSpacing.xs)],
                Flexible(
                  child: AnimatedDefaultTextStyle(
                    duration: duration,
                    style: ((small ? text.labelMedium : text.labelLarge) ?? const TextStyle()).copyWith(color: color, fontWeight: selected ? FontWeight.w700 : FontWeight.w600),
                    child: Text(tab.label, maxLines: 1, overflow: TextOverflow.ellipsis),
                  ),
                ),
                if (badge != null) ...[
                  const SizedBox(width: HqSpacing.xs),
                  DecoratedBox(
                    decoration: BoxDecoration(
                      color: filledSelected ? tokens.onPrimary.withValues(alpha: 0.22) : tokens.primarySoft,
                      borderRadius: BorderRadius.circular(HqRadius.pill),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 1),
                      child: Text('$badge', style: text.labelSmall?.copyWith(color: filledSelected ? tokens.onPrimary : tokens.link, fontWeight: FontWeight.w700)),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

}
