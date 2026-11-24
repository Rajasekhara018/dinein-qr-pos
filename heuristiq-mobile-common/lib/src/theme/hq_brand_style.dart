import 'dart:ui' show lerpDouble;

import 'package:flutter/material.dart';

/// Optional brand finish for the shared widgets, on top of [HqTokens]: a gradient and a soft coloured shadow on the
/// primary button, rounder controls and dialogs. An app that adds none keeps the plain look of the web apps; a consumer
/// app that wants a premium feel (e.g. the matrimony app) adds one to its `ThemeData.extensions`.
@immutable
class HqBrandStyle extends ThemeExtension<HqBrandStyle> {
  const HqBrandStyle({
    this.primaryGradient,
    this.primaryShadow = false,
    this.controlRadius,
    this.dialogRadius,
    this.stateBadge = false,
  });

  /// Colours of the primary button's fill, top-left to bottom-right; null keeps the flat primary colour.
  final List<Color>? primaryGradient;

  /// A soft shadow in the primary colour under an enabled filled primary button.
  final bool primaryShadow;

  /// Corner radius of buttons; null keeps [HqRadius.control].
  final double? controlRadius;

  /// Corner radius of dialogs; null keeps [HqRadius.popup].
  final double? dialogRadius;

  /// Empty and error states show their icon in a soft tinted badge (primary, or the warn colour for errors) and fade
  /// in gently (no motion when the platform asks for reduced animations). False keeps the plain muted icon.
  final bool stateBadge;

  @override
  HqBrandStyle copyWith({
    List<Color>? primaryGradient,
    bool? primaryShadow,
    double? controlRadius,
    double? dialogRadius,
    bool? stateBadge,
  }) =>
      HqBrandStyle(
        primaryGradient: primaryGradient ?? this.primaryGradient,
        primaryShadow: primaryShadow ?? this.primaryShadow,
        controlRadius: controlRadius ?? this.controlRadius,
        dialogRadius: dialogRadius ?? this.dialogRadius,
        stateBadge: stateBadge ?? this.stateBadge,
      );

  @override
  HqBrandStyle lerp(ThemeExtension<HqBrandStyle>? other, double t) {
    if (other is! HqBrandStyle) return this;
    final a = primaryGradient;
    final b = other.primaryGradient;
    return HqBrandStyle(
      primaryGradient: a != null && b != null && a.length == b.length
          ? [for (var i = 0; i < a.length; i++) Color.lerp(a[i], b[i], t)!]
          : (t < 0.5 ? a : b),
      primaryShadow: t < 0.5 ? primaryShadow : other.primaryShadow,
      controlRadius: _lerpNullable(controlRadius, other.controlRadius, t),
      dialogRadius: _lerpNullable(dialogRadius, other.dialogRadius, t),
      stateBadge: t < 0.5 ? stateBadge : other.stateBadge,
    );
  }

  static double? _lerpNullable(double? a, double? b, double t) =>
      a == null || b == null ? (t < 0.5 ? a : b) : lerpDouble(a, b, t);
}

/// The brand finish in scope, or the plain default (no gradient, no shadow, standard radii).
extension HqBrandStyleContext on BuildContext {
  HqBrandStyle get hqBrand =>
      Theme.of(this).extension<HqBrandStyle>() ?? const HqBrandStyle();
}
