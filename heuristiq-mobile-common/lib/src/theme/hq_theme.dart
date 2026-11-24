import 'package:flutter/material.dart';

import 'hq_tokens.dart';

/// Builds the app [ThemeData] from [HqTokens], the Flutter counterpart of `hq-shared-lib/theme/styles.scss`.
abstract final class HqTheme {
  /// Light theme. [primary] overrides the brand colour (the web themes only change `--hq-primary`).
  static ThemeData light({HqFontScale fontScale = HqFontScale.normal, Color? primary}) =>
      _build(_withPrimary(HqTokens.light, primary), Brightness.light, fontScale);

  /// Dark theme.
  static ThemeData dark({HqFontScale fontScale = HqFontScale.normal, Color? primary}) =>
      _build(_withPrimary(HqTokens.dark, primary), Brightness.dark, fontScale);

  /// The tokens of the theme in scope (light tokens when the theme carries none).
  static HqTokens of(BuildContext context) => Theme.of(context).extension<HqTokens>() ?? HqTokens.light;

  static HqTokens _withPrimary(HqTokens tokens, Color? primary) =>
      primary == null ? tokens : tokens.copyWith(primary: primary, link: primary);

  static ThemeData _build(HqTokens t, Brightness brightness, HqFontScale fontScale) {
    final base = ThemeData(brightness: brightness, useMaterial3: true);
    final scheme = ColorScheme(
      brightness: brightness,
      primary: t.primary,
      onPrimary: t.onPrimary,
      secondary: t.secondary,
      onSecondary: t.onSecondary,
      error: t.warn,
      onError: t.onWarn,
      surface: t.surface,
      onSurface: t.ink,
    );
    final radius = BorderRadius.circular(HqRadius.control);
    OutlineInputBorder border(Color color, [double width = 1]) =>
        OutlineInputBorder(borderRadius: radius, borderSide: BorderSide(color: color, width: width));
    return base.copyWith(
      colorScheme: scheme,
      scaffoldBackgroundColor: t.background,
      canvasColor: t.surface,
      textTheme: base.textTheme.apply(bodyColor: t.ink, displayColor: t.ink, fontSizeFactor: fontScale.factor),
      dividerTheme: DividerThemeData(color: t.fieldBorder, space: 1, thickness: 1),
      appBarTheme: AppBarTheme(
        backgroundColor: t.surface,
        foregroundColor: t.ink,
        elevation: 0,
        scrolledUnderElevation: 1,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: t.surface,
        isDense: true,
        contentPadding: const EdgeInsets.symmetric(horizontal: HqSpacing.md, vertical: HqSpacing.md),
        hintStyle: TextStyle(color: t.muted),
        border: border(t.fieldBorder),
        enabledBorder: border(t.fieldBorder),
        focusedBorder: border(t.primary, 2),
        errorBorder: border(t.warn),
        focusedErrorBorder: border(t.warn, 2),
        disabledBorder: border(t.fieldBorder),
      ),
      extensions: <ThemeExtension<dynamic>>[t],
    );
  }
}

/// `context.hq` returns the [HqTokens] of the theme in scope.
extension HqThemeContext on BuildContext {
  HqTokens get hq => HqTheme.of(this);
}
