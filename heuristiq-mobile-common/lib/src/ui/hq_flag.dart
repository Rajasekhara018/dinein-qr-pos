import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_country_data.dart';

/// How a [HqFlag] is drawn.
///
/// - [emoji]: the regional-indicator emoji flag from the ISO code. Android, iOS and macOS draw it; Windows, most Linux and
///   some browsers show two letters instead.
/// - [badge]: a rounded badge with the ISO code. Works everywhere, needs no font or asset.
/// - [auto]: emoji on Android, iOS, macOS and the web, badge on native Windows, Linux and Fuchsia. On the web, Flutter
///   downloads the Noto Color Emoji font for glyphs the system lacks, so flags draw even in Chrome on Windows (without a
///   connection to fetch the font, the browser shows the two letters).
///
/// Decision: no bundled flag-image package. It would add hundreds of assets for a cosmetic gain, and the web library itself
/// falls back to the ISO letters (`hq-phone-flag-font.scss` only patches Windows). An app that wants real flags everywhere
/// can pass its own widget through `HqPhoneNumberInput.flagBuilder`.
enum HqFlagStyle { auto, emoji, badge }

/// A country flag for [iso].
class HqFlag extends StatelessWidget {
  const HqFlag(
      {super.key,
      required this.iso,
      this.size = 24,
      this.style = HqFlagStyle.auto});

  final String iso;

  /// Font size of the emoji, and the height of the badge.
  final double size;
  final HqFlagStyle style;

  /// True when [style] resolves to the emoji on this platform.
  static bool usesEmoji(HqFlagStyle style,
      {TargetPlatform? platform, bool? web}) {
    switch (style) {
      case HqFlagStyle.emoji:
        return true;
      case HqFlagStyle.badge:
        return false;
      case HqFlagStyle.auto:
        if (web ?? kIsWeb) return true;
        final p = platform ?? defaultTargetPlatform;
        return p == TargetPlatform.android ||
            p == TargetPlatform.iOS ||
            p == TargetPlatform.macOS;
    }
  }

  @override
  Widget build(BuildContext context) {
    final code = iso.toUpperCase();
    final emoji = HqCountryData.flagEmoji(code);
    // Decorative: the country name next to it carries the meaning.
    if (emoji.isNotEmpty && usesEmoji(style)) {
      return ExcludeSemantics(
          child: Text(emoji, style: TextStyle(fontSize: size, height: 1)));
    }
    final tokens = context.hq;
    return ExcludeSemantics(
      child: Container(
        padding: EdgeInsets.symmetric(horizontal: size * 0.25),
        constraints: BoxConstraints(minWidth: size * 1.4, minHeight: size),
        decoration: BoxDecoration(
            color: tokens.primarySoft,
            border: Border.all(color: tokens.fieldBorder),
            borderRadius: BorderRadius.circular(HqRadius.sm / 2)),
        child: Center(
          widthFactor: 1,
          heightFactor: 1,
          child: Directionality(
            textDirection: TextDirection.ltr,
            child: Text(code,
                style: TextStyle(
                    fontSize: size * 0.5,
                    fontWeight: FontWeight.w700,
                    color: tokens.ink,
                    height: 1)),
          ),
        ),
      ),
    );
  }
}
