import 'package:flutter/material.dart';

import '../theme/hq_brand_style.dart';
import '../theme/hq_mobile_finish.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';

/// Colour role of a [HqButton] (the web `HQ_BUTTON_COLORS`).
enum HqButtonColor { primary, accent, warn, success, secondary, inverse }

/// How a [HqButton] is drawn: solid fill, outline only, or text only.
enum HqButtonAppearance { filled, outline, text }

/// Button sizes (the web `sm` / `md` / `lg`).
enum HqButtonSize { sm, md, lg }

/// The common button, styled by the active [HqTokens] (the counterpart of the `hqButton` directive).
///
/// - [label] and/or [icon]; an [icon] without a [label] makes a square icon-only button (give it a [semanticLabel]).
/// - [loading] shows a spinner and disables the button; when it ends [onPressed] applies again.
/// - A null [onPressed] disables the button (the web `[disabled]`).
/// - [block] makes it full width.
class HqButton extends StatelessWidget {
  const HqButton({
    super.key,
    this.label,
    this.icon,
    this.leading,
    this.haptic = true,
    this.onPressed,
    this.color = HqButtonColor.primary,
    this.appearance = HqButtonAppearance.filled,
    this.size = HqButtonSize.md,
    this.loading = false,
    this.block = false,
    this.semanticLabel,
  }) : assert(label != null || icon != null, 'A HqButton needs a label or an icon');

  /// Drawn before the label instead of [icon], e.g. a partner's logo image ("Sign in with Google").
  final Widget? leading;

  /// A light selection haptic on tap (mobile feel); false for buttons tapped in quick succession.
  final bool haptic;

  final String? label;
  final IconData? icon;
  final VoidCallback? onPressed;
  final HqButtonColor color;
  final HqButtonAppearance appearance;
  final HqButtonSize size;
  final bool loading;
  final bool block;
  final String? semanticLabel;

  /// True for a button with an icon and no label.
  bool get iconOnly => label == null && icon != null;

  /// True when the button cannot be pressed (no handler, or busy loading).
  bool get inactive => loading || onPressed == null;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final palette = _palette(tokens);
    final height = switch (size) {
      // Mobile sizes: md is the 48 px touch-target minimum, lg the 52 px main call to action.
      HqButtonSize.sm => 40.0,
      HqButtonSize.md => HqSizes.minTapTarget,
      HqButtonSize.lg => 52.0,
    };
    final horizontal = switch (size) {
      HqButtonSize.sm => 12.0,
      HqButtonSize.md => 16.0,
      HqButtonSize.lg => 24.0,
    };
    final brand = context.hqBrand;
    final radius = BorderRadius.circular(brand.controlRadius ?? HqRadius.control);
    final shape = RoundedRectangleBorder(borderRadius: radius);
    // The brand finish (gradient, soft shadow) applies to the filled primary button only.
    final gradient = appearance == HqButtonAppearance.filled && color == HqButtonColor.primary ? brand.primaryGradient : null;
    final padding = iconOnly ? EdgeInsets.zero : EdgeInsets.symmetric(horizontal: horizontal);
    final minimumSize = Size(iconOnly ? height : 0, height);
    final foreground = appearance == HqButtonAppearance.filled ? palette.onFill : palette.text;
    final VoidCallback? handler = inactive ? null : (haptic ? hqWithHaptic(onPressed) : onPressed);
    // Semibold labels with a touch of tracking: the mobile button voice.
    final content = DefaultTextStyle.merge(style: TextStyle(fontWeight: FontWeight.w600, letterSpacing: 0.2, fontSize: size == HqButtonSize.lg ? 16 : null), child: _content(context, foreground));

    final Widget button = switch (appearance) {
      HqButtonAppearance.filled when gradient != null => _GradientFill(
          gradient: gradient,
          radius: radius,
          shadow: brand.primaryShadow && handler != null ? palette.fill : null,
          enabled: handler != null,
          child: FilledButton(
            onPressed: handler,
            style: FilledButton.styleFrom(
              backgroundColor: Colors.transparent,
              shadowColor: Colors.transparent,
              foregroundColor: palette.onFill,
              disabledBackgroundColor: Colors.transparent,
              disabledForegroundColor: palette.onFill.withValues(alpha: 0.8),
              minimumSize: minimumSize,
              padding: padding,
              shape: shape,
            ),
            child: content,
          ),
        ),
      HqButtonAppearance.filled => FilledButton(
          onPressed: handler,
          style: FilledButton.styleFrom(
            backgroundColor: palette.fill,
            foregroundColor: palette.onFill,
            disabledBackgroundColor: palette.fill.withValues(alpha: 0.45),
            disabledForegroundColor: palette.onFill.withValues(alpha: 0.8),
            minimumSize: minimumSize,
            padding: padding,
            shape: shape,
          ),
          child: content,
        ),
      HqButtonAppearance.outline => OutlinedButton(
          onPressed: handler,
          style: OutlinedButton.styleFrom(
            foregroundColor: palette.text,
            side: BorderSide(color: handler == null ? palette.text.withValues(alpha: 0.4) : palette.text),
            minimumSize: minimumSize,
            padding: padding,
            shape: shape,
          ),
          child: content,
        ),
      HqButtonAppearance.text => TextButton(
          onPressed: handler,
          style: TextButton.styleFrom(
            foregroundColor: palette.text,
            minimumSize: minimumSize,
            padding: padding,
            shape: shape,
          ),
          child: content,
        ),
    };

    Widget result = button;
    if (semanticLabel != null) {
      result = Semantics(label: semanticLabel, button: true, enabled: !inactive, excludeSemantics: true, onTap: handler, child: button);
    }
    // Press feedback: a subtle scale and dim while the finger is down (none under reduced motion).
    result = HqPressable(enabled: !inactive, child: result);
    return block ? SizedBox(width: double.infinity, child: result) : result;
  }

  Widget _content(BuildContext context, Color foreground) {
    if (loading) {
      return SizedBox(
        width: 20,
        height: 20,
        child: CircularProgressIndicator(strokeWidth: 2, color: foreground),
      );
    }
    if (iconOnly) return Icon(icon, size: 20);
    final lead = leading;
    if (lead != null && label != null) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(width: 20, height: 20, child: lead),
          const SizedBox(width: HqSpacing.sm),
          Flexible(child: Text(label!, maxLines: 1, overflow: TextOverflow.ellipsis)),
        ],
      );
    }
    if (icon == null) return Text(label!, maxLines: 1, overflow: TextOverflow.ellipsis);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 18),
        const SizedBox(width: HqSpacing.sm),
        Flexible(child: Text(label!, maxLines: 1, overflow: TextOverflow.ellipsis)),
      ],
    );
  }

  _HqButtonPalette _palette(HqTokens t) => switch (color) {
        HqButtonColor.primary => _HqButtonPalette(t.primary, t.onPrimary, t.link),
        HqButtonColor.accent => _HqButtonPalette(t.accentFill, t.onAccent, t.accentText),
        HqButtonColor.warn => _HqButtonPalette(t.warn, t.onWarn, t.warn),
        HqButtonColor.success => _HqButtonPalette(t.success, t.onSuccess, t.success),
        HqButtonColor.secondary => _HqButtonPalette(t.secondary, t.onSecondary, t.ink),
        // White on a coloured or gradient surface (a banner, a featured card): white fill with primary text, or white text.
        HqButtonColor.inverse => _HqButtonPalette(const Color(0xFFFFFFFF), t.primary, const Color(0xFFFFFFFF)),
      };
}

/// The gradient (and optional soft shadow) behind a transparent filled button: the brand finish of [HqBrandStyle].
class _GradientFill extends StatelessWidget {
  const _GradientFill({required this.gradient, required this.radius, required this.shadow, required this.enabled, required this.child});

  final List<Color> gradient;
  final BorderRadius radius;
  final Color? shadow;
  final bool enabled;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final glow = shadow;
    return Opacity(
      opacity: enabled ? 1 : 0.5,
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(colors: gradient, begin: Alignment.topLeft, end: Alignment.bottomRight),
          borderRadius: radius,
          boxShadow: glow == null ? null : [BoxShadow(color: glow.withValues(alpha: 0.28), blurRadius: 18, offset: const Offset(0, 8))],
        ),
        child: child,
      ),
    );
  }
}

class _HqButtonPalette {
  const _HqButtonPalette(this.fill, this.onFill, this.text);

  final Color fill;
  final Color onFill;

  /// Colour of text and border for the outline and text appearances.
  final Color text;
}
