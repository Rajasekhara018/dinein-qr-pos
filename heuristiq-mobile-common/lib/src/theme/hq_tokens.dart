import 'package:flutter/material.dart';

/// Colour role of a status badge or alert; maps to the web theme's `--hq-tone-<tone>-*` tokens.
enum HqStatusTone { success, warning, danger, info, neutral }

/// Background, text and border colour of one [HqStatusTone].
@immutable
class HqToneColors {
  const HqToneColors({required this.background, required this.foreground, required this.border});

  final Color background;
  final Color foreground;
  final Color border;
}

/// Spacing scale (logical pixels).
abstract final class HqSpacing {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;
}

/// Corner radii, from `--hq-control-radius`, `--hq-panel-radius` and `--hq-popup-radius` of the web theme.
abstract final class HqRadius {
  static const double sm = 8;
  static const double control = 12;
  static const double panel = 16;
  static const double popup = 22;
  static const double pill = 999;
}

/// Control sizes: `--hq-control-height` is 2.75rem (44) on the web; 48 is the touch-target minimum on mobile.
abstract final class HqSizes {
  static const double controlHeight = 44;
  static const double minTapTarget = 48;
}

/// Font scale steps, the same factors as the web `data-font-scale` attribute.
enum HqFontScale {
  small(0.875),
  normal(1),
  large(1.125),
  xlarge(1.25);

  const HqFontScale(this.factor);

  final double factor;
}

/// Design tokens of the RealBoom web theme (`hq-shared-lib/theme/styles.scss`) as a [ThemeExtension].
///
/// Light values are copied from the web `:root` block, dark values from `[data-theme="dark"]`. The dark tone colours and
/// the stat-card tones are derived (the web file does not define them for dark), see the README.
@immutable
class HqTokens extends ThemeExtension<HqTokens> {
  const HqTokens({
    required this.primary,
    required this.onPrimary,
    required this.primarySoft,
    required this.link,
    required this.secondary,
    required this.onSecondary,
    required this.accentFill,
    required this.onAccent,
    required this.accentText,
    required this.warn,
    required this.onWarn,
    required this.success,
    required this.onSuccess,
    required this.ink,
    required this.muted,
    required this.background,
    required this.surface,
    required this.fieldBorder,
    required this.toneSuccess,
    required this.toneWarning,
    required this.toneDanger,
    required this.toneInfo,
    required this.toneNeutral,
    required this.chartCategorical,
    required this.chartOther,
    required this.chartSequential,
  });

  /// Categorical chart slots 1..8 in a fixed order (series identity). Same hues as the web `HQ_CHART_CATEGORICAL_*`,
  /// validated there for colour-blind and contrast checks on light and dark surfaces. Never cycle: more than eight series fold
  /// into [chartOther].
  final List<Color> chartCategorical;

  /// Neutral colour of the folded "Other" series.
  final Color chartOther;

  /// Sequential ramp for magnitude (heatmaps), lightest to strongest on light, reversed on dark so near-zero recedes.
  final List<Color> chartSequential;

  final Color primary;
  final Color onPrimary;
  final Color primarySoft;
  final Color link;
  final Color secondary;
  final Color onSecondary;
  final Color accentFill;
  final Color onAccent;
  final Color accentText;
  final Color warn;
  final Color onWarn;
  final Color success;
  final Color onSuccess;

  /// `--edu-ink`: body text.
  final Color ink;

  /// `--edu-muted`: secondary text.
  final Color muted;

  /// Page background (`--app-bg-mid`).
  final Color background;

  /// Panels and cards (`--workspace-panel`).
  final Color surface;

  /// `--field-border`.
  final Color fieldBorder;

  final HqToneColors toneSuccess;
  final HqToneColors toneWarning;
  final HqToneColors toneDanger;
  final HqToneColors toneInfo;
  final HqToneColors toneNeutral;

  /// Light theme (web `:root`).
  static const HqTokens light = HqTokens(
    primary: Color(0xFF0B6E4F),
    onPrimary: Color(0xFFFFFFFF),
    primarySoft: Color(0x1F0B6E4F),
    link: Color(0xFF0B6E4F),
    secondary: Color(0xFFD4A017),
    onSecondary: Color(0xFF1B2A4A),
    accentFill: Color(0xFFD4A017),
    onAccent: Color(0xFF111827),
    accentText: Color(0xFF7A5A06),
    warn: Color(0xFFDC2626),
    onWarn: Color(0xFFFFFFFF),
    success: Color(0xFF15803D),
    onSuccess: Color(0xFFFFFFFF),
    ink: Color(0xFF1B2A4A),
    muted: Color(0xFF5A6A7E),
    background: Color(0xFFEEF2F8),
    surface: Color(0xFFFFFFFF),
    fieldBorder: Color(0x2E0B6E4F),
    toneSuccess: HqToneColors(background: Color(0xFFDCFCE7), foreground: Color(0xFF166534), border: Color(0xFF86EFAC)),
    toneWarning: HqToneColors(background: Color(0xFFFEF3C7), foreground: Color(0xFF92400E), border: Color(0xFFFCD34D)),
    toneDanger: HqToneColors(background: Color(0xFFFEE2E2), foreground: Color(0xFF991B1B), border: Color(0xFFFCA5A5)),
    toneInfo: HqToneColors(background: Color(0xFFE0F2FE), foreground: Color(0xFF075985), border: Color(0xFF7DD3FC)),
    toneNeutral: HqToneColors(background: Color(0xFFF1F5F9), foreground: Color(0xFF334155), border: Color(0xFFCBD5E1)),
    chartCategorical: <Color>[
      Color(0xFF2A78D6),
      Color(0xFFEB6834),
      Color(0xFF1BAF7A),
      Color(0xFFEDA100),
      Color(0xFFE87BA4),
      Color(0xFF008300),
      Color(0xFF6250D6),
      Color(0xFFE34948),
    ],
    chartOther: Color(0xFF9AA1AB),
    chartSequential: _sequentialLight,
  );

  /// Dark theme (web `[data-theme="dark"]`).
  static const HqTokens dark = HqTokens(
    primary: Color(0xFF0F7A58),
    onPrimary: Color(0xFFFFFFFF),
    primarySoft: Color(0x290F7A58),
    link: Color(0xFF4BB081),
    secondary: Color(0xFFD4A017),
    onSecondary: Color(0xFF1B2A4A),
    accentFill: Color(0xFFD4A017),
    onAccent: Color(0xFF111827),
    accentText: Color(0xFFD4A017),
    warn: Color(0xFFF87171),
    onWarn: Color(0xFF111827),
    success: Color(0xFF4ADE80),
    onSuccess: Color(0xFF111827),
    ink: Color(0xFFE2E8F0),
    muted: Color(0xFF94A3B8),
    background: Color(0xFF111C35),
    surface: Color(0xFF16213E),
    fieldBorder: Color(0x1FFFFFFF),
    toneSuccess: HqToneColors(background: Color(0x294ADE80), foreground: Color(0xFF86EFAC), border: Color(0x664ADE80)),
    toneWarning: HqToneColors(background: Color(0x29FBBF24), foreground: Color(0xFFFCD34D), border: Color(0x66FBBF24)),
    toneDanger: HqToneColors(background: Color(0x29F87171), foreground: Color(0xFFFCA5A5), border: Color(0x66F87171)),
    toneInfo: HqToneColors(background: Color(0x2938BDF8), foreground: Color(0xFF7DD3FC), border: Color(0x6638BDF8)),
    toneNeutral: HqToneColors(background: Color(0x29CBD5E1), foreground: Color(0xFFCBD5E1), border: Color(0x66CBD5E1)),
    chartCategorical: <Color>[
      Color(0xFF3987E5),
      Color(0xFFD95926),
      Color(0xFF199E70),
      Color(0xFFC98500),
      Color(0xFFD55181),
      Color(0xFF008300),
      Color(0xFF9085E9),
      Color(0xFFE66767),
    ],
    chartOther: Color(0xFF6B7280),
    chartSequential: _sequentialDark,
  );

  // Web `HQ_CHART_SEQUENTIAL` steps 100..700 (every second step), lightest first.
  static const List<Color> _sequentialLight = <Color>[
    Color(0xFFCDE2FB),
    Color(0xFF9EC5F4),
    Color(0xFF6DA7EC),
    Color(0xFF3987E5),
    Color(0xFF256ABF),
    Color(0xFF184F95),
    Color(0xFF0D366B),
  ];

  static const List<Color> _sequentialDark = <Color>[
    Color(0xFF0D366B),
    Color(0xFF184F95),
    Color(0xFF256ABF),
    Color(0xFF3987E5),
    Color(0xFF6DA7EC),
    Color(0xFF9EC5F4),
    Color(0xFFCDE2FB),
  ];

  /// Colours of [tone].
  HqToneColors tone(HqStatusTone tone) => switch (tone) {
        HqStatusTone.success => toneSuccess,
        HqStatusTone.warning => toneWarning,
        HqStatusTone.danger => toneDanger,
        HqStatusTone.info => toneInfo,
        HqStatusTone.neutral => toneNeutral,
      };

  @override
  HqTokens copyWith({
    Color? primary,
    Color? onPrimary,
    Color? primarySoft,
    Color? link,
    Color? secondary,
    Color? onSecondary,
    Color? accentFill,
    Color? onAccent,
    Color? accentText,
    Color? warn,
    Color? onWarn,
    Color? success,
    Color? onSuccess,
    Color? ink,
    Color? muted,
    Color? background,
    Color? surface,
    Color? fieldBorder,
    HqToneColors? toneSuccess,
    HqToneColors? toneWarning,
    HqToneColors? toneDanger,
    HqToneColors? toneInfo,
    HqToneColors? toneNeutral,
    List<Color>? chartCategorical,
    Color? chartOther,
    List<Color>? chartSequential,
  }) {
    return HqTokens(
      primary: primary ?? this.primary,
      onPrimary: onPrimary ?? this.onPrimary,
      primarySoft: primarySoft ?? this.primarySoft,
      link: link ?? this.link,
      secondary: secondary ?? this.secondary,
      onSecondary: onSecondary ?? this.onSecondary,
      accentFill: accentFill ?? this.accentFill,
      onAccent: onAccent ?? this.onAccent,
      accentText: accentText ?? this.accentText,
      warn: warn ?? this.warn,
      onWarn: onWarn ?? this.onWarn,
      success: success ?? this.success,
      onSuccess: onSuccess ?? this.onSuccess,
      ink: ink ?? this.ink,
      muted: muted ?? this.muted,
      background: background ?? this.background,
      surface: surface ?? this.surface,
      fieldBorder: fieldBorder ?? this.fieldBorder,
      toneSuccess: toneSuccess ?? this.toneSuccess,
      toneWarning: toneWarning ?? this.toneWarning,
      toneDanger: toneDanger ?? this.toneDanger,
      toneInfo: toneInfo ?? this.toneInfo,
      toneNeutral: toneNeutral ?? this.toneNeutral,
      chartCategorical: chartCategorical ?? this.chartCategorical,
      chartOther: chartOther ?? this.chartOther,
      chartSequential: chartSequential ?? this.chartSequential,
    );
  }

  /// Switches at the halfway point: the tokens are used as a pair (light and dark), not animated colour by colour.
  @override
  HqTokens lerp(ThemeExtension<HqTokens>? other, double t) {
    if (other is! HqTokens) return this;
    return t < 0.5 ? this : other;
  }
}
