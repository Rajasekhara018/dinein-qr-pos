import 'package:flutter/material.dart';

import '../branding/branding.dart';

/// Kiosk sizing: large touch targets and text that reads at arm's length.
class KioskSizes {
  KioskSizes._();

  static const double minTouch = 56;
  static const double radius = 20;
  static const double gutter = 24;

  /// Text scale used by the customer's "large text" switch.
  static const double largeTextScale = 1.3;
}

/// [highContrast] is the customer's accessibility mode: black text on white,
/// and a darkened brand colour so buttons keep at least a 4.5:1 contrast with their labels.
ThemeData buildKioskTheme(Branding branding, {bool highContrast = false}) {
  final primary = highContrast ? _darken(branding.primary, 0.35) : branding.primary;
  final ink = highContrast ? Colors.black : branding.secondary;
  final scheme = ColorScheme.fromSeed(
    seedColor: primary,
    primary: primary,
    secondary: branding.secondary,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: highContrast ? Colors.white : const Color(0xFFF8F9FA),
    textTheme: Typography.blackMountainView.apply(
      bodyColor: ink,
      displayColor: ink,
      fontSizeFactor: 1.15,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(160, KioskSizes.minTouch),
        textStyle: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(KioskSizes.radius),
        ),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(120, KioskSizes.minTouch),
        textStyle: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(KioskSizes.radius),
        ),
      ),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(KioskSizes.radius),
        side: BorderSide(
          color: highContrast ? Colors.black : const Color(0xFFE9ECEF),
          width: highContrast ? 2 : 1,
        ),
      ),
    ),
  );
}

Color _darken(Color color, double amount) {
  final hsl = HSLColor.fromColor(color);
  return hsl.withLightness((hsl.lightness - amount).clamp(0.0, 1.0)).toColor();
}
