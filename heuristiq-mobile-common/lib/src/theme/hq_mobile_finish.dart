import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'hq_brand_style.dart';
import 'hq_theme.dart';
import 'hq_tokens.dart';

/// The mobile finish shared by every Hq widget: filled fields, soft layered shadows, rounder surfaces and motion that
/// respects the system's reduced-motion setting. Colours always come from [HqTokens] (and [HqBrandStyle] when set).
abstract final class HqMobile {
  /// Minimum height of a field (text, select, date): a comfortable thumb target.
  static const double fieldHeight = 52;

  /// Corner radius of cards and stat tiles.
  static const double cardRadius = 18;

  /// Corner radius of the top of bottom sheets.
  static const double sheetRadius = 28;

  /// Press feedback and selection slides.
  static const Duration press = Duration(milliseconds: 120);
  static const Duration slide = Duration(milliseconds: 200);

  static bool isDark(BuildContext context) => Theme.of(context).brightness == Brightness.dark;

  /// [duration], or zero when the system asks for reduced motion.
  static Duration motion(BuildContext context, Duration duration) => MediaQuery.disableAnimationsOf(context) ? Duration.zero : duration;

  /// Corner radius of controls: the brand's, or the standard one.
  static double controlRadius(BuildContext context) => context.hqBrand.controlRadius ?? HqRadius.control;

  /// Fill of a field at rest: the surface with a faint tint of the primary (light) or a faint lift (dark).
  static Color fieldFill(BuildContext context) {
    final t = context.hq;
    return isDark(context)
        ? Color.alphaBlend(Colors.white.withValues(alpha: 0.06), t.surface)
        : Color.alphaBlend(t.primary.withValues(alpha: 0.045), t.surface);
  }

  /// Fill of a soft track (a segmented control, a switch off-state).
  static Color trackFill(BuildContext context) {
    final t = context.hq;
    return isDark(context) ? Color.alphaBlend(Colors.white.withValues(alpha: 0.08), t.surface) : Color.alphaBlend(t.ink.withValues(alpha: 0.06), t.surface);
  }

  /// A layered soft shadow (light), or a deeper, quieter one (dark). [lift] scales it for raised states.
  static List<BoxShadow> softShadow(BuildContext context, {double lift = 1}) {
    final t = context.hq;
    if (isDark(context)) {
      return [BoxShadow(color: Colors.black.withValues(alpha: 0.35), blurRadius: 16 * lift, offset: Offset(0, 6 * lift))];
    }
    return [
      BoxShadow(color: t.ink.withValues(alpha: 0.06), blurRadius: 20 * lift, offset: Offset(0, 8 * lift)),
      BoxShadow(color: t.ink.withValues(alpha: 0.04), blurRadius: 4, offset: const Offset(0, 1)),
    ];
  }

  /// A hairline that lifts a surface off a dark background; none in light (the shadow does that).
  static Border? hairline(BuildContext context) => isDark(context) ? Border.all(color: Colors.white.withValues(alpha: 0.07)) : null;

  /// [decoration] in the filled mobile style: no border at rest, a primary ring when focused, a warn ring on error.
  static InputDecoration filled(BuildContext context, InputDecoration decoration, {bool invalid = false}) {
    final t = context.hq;
    final radius = BorderRadius.circular(controlRadius(context));
    OutlineInputBorder ring(Color color, double width) => OutlineInputBorder(borderRadius: radius, borderSide: BorderSide(color: color, width: width));
    final rest = OutlineInputBorder(borderRadius: radius, borderSide: invalid ? BorderSide(color: t.warn, width: 1.2) : BorderSide.none);
    return decoration.copyWith(
      filled: true,
      fillColor: fieldFill(context),
      isDense: false,
      constraints: decoration.constraints ?? const BoxConstraints(minHeight: fieldHeight),
      contentPadding: decoration.contentPadding ?? const EdgeInsets.symmetric(horizontal: HqSpacing.lg, vertical: 15),
      hintStyle: decoration.hintStyle ?? TextStyle(color: t.muted.withValues(alpha: 0.85)),
      border: rest,
      enabledBorder: rest,
      disabledBorder: OutlineInputBorder(borderRadius: radius, borderSide: BorderSide.none),
      focusedBorder: ring(invalid ? t.warn : t.primary, 1.5),
      errorBorder: ring(t.warn, 1.2),
      focusedErrorBorder: ring(t.warn, 1.5),
      errorStyle: TextStyle(color: t.warn, fontWeight: FontWeight.w500),
    );
  }

  /// The box of a tappable field that is not a text field (a select, a date): same fill, radius and error ring.
  static BoxDecoration fieldBox(BuildContext context, {bool error = false}) => BoxDecoration(
        color: fieldFill(context),
        borderRadius: BorderRadius.circular(controlRadius(context)),
        border: error ? Border.all(color: context.hq.warn, width: 1.2) : null,
      );
}

/// Press feedback for any tappable surface: a subtle scale-down and dim while the finger is down (none under reduced
/// motion). It only listens to the pointer, so the child's own tap handling is unchanged.
class HqPressable extends StatefulWidget {
  const HqPressable({super.key, required this.child, this.enabled = true, this.scale = 0.97});

  final Widget child;
  final bool enabled;
  final double scale;

  @override
  State<HqPressable> createState() => _HqPressableState();
}

class _HqPressableState extends State<HqPressable> {
  bool _down = false;

  void _set(bool down) {
    if (!widget.enabled || _down == down) return;
    setState(() => _down = down);
  }

  @override
  Widget build(BuildContext context) {
    final duration = HqMobile.motion(context, HqMobile.press);
    final pressed = _down && widget.enabled && duration != Duration.zero;
    return Listener(
      onPointerDown: (_) => _set(true),
      onPointerUp: (_) => _set(false),
      onPointerCancel: (_) => _set(false),
      child: AnimatedScale(
        scale: pressed ? widget.scale : 1,
        duration: duration,
        curve: Curves.easeOut,
        child: AnimatedOpacity(opacity: pressed ? 0.88 : 1, duration: duration, child: widget.child),
      ),
    );
  }
}

/// A light selection haptic before running [action] (a no-op where the platform has none).
VoidCallback? hqWithHaptic(VoidCallback? action) {
  if (action == null) return null;
  return () {
    HapticFeedback.selectionClick();
    action();
  };
}
