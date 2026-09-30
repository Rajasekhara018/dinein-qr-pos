import 'package:flutter/material.dart';

import '../branding/branding.dart';

/// Kiosk sizing: large touch targets and text that reads at arm's length.
class KioskSizes {
  KioskSizes._();

  static const double minTouch = 56;
  static const double radius = 20;
  static const double gutter = 24;
}

ThemeData buildKioskTheme(Branding branding) {
  final scheme = ColorScheme.fromSeed(
    seedColor: branding.primary,
    primary: branding.primary,
    secondary: branding.secondary,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: const Color(0xFFF8F9FA),
    textTheme: Typography.blackMountainView.apply(
      bodyColor: branding.secondary,
      displayColor: branding.secondary,
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
        side: const BorderSide(color: Color(0xFFE9ECEF)),
      ),
    ),
  );
}
