import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_radio_group.dart';
import '../ui/hq_sheet.dart';

/// A language the app offers: its code and its name written in that language (an endonym, so it is the same in every other
/// language and is not a translation).
@immutable
class HqLanguage {
  const HqLanguage({required this.code, required this.nativeName, this.rtl = false});

  /// BCP 47 language code (`en`, `hi`).
  final String code;
  final String nativeName;
  final bool rtl;

  Locale get locale => Locale(code);
}

/// The seven languages of the platform (`en`, `hi`, `ta`, `te`, `kn`, `ml`, `ar`), the same set as the web i18n files.
const List<HqLanguage> hqSupportedLanguages = <HqLanguage>[
  HqLanguage(code: 'en', nativeName: 'English'),
  HqLanguage(code: 'hi', nativeName: 'हिन्दी'),
  HqLanguage(code: 'ta', nativeName: 'தமிழ்'),
  HqLanguage(code: 'te', nativeName: 'తెలుగు'),
  HqLanguage(code: 'kn', nativeName: 'ಕನ್ನಡ'),
  HqLanguage(code: 'ml', nativeName: 'മലയാളം'),
  HqLanguage(code: 'ar', nativeName: 'العربية', rtl: true),
];

/// True when [code] is written right to left.
bool hqIsRtlLanguage(String code) => hqSupportedLanguages.any((l) => l.code == code && l.rtl);

/// The language for [code], or null.
HqLanguage? hqLanguageOf(String code) {
  for (final l in hqSupportedLanguages) {
    if (l.code == code) return l;
  }
  return null;
}

/// A button showing the current language that opens a list of [languages] (the counterpart of `hq-language-switcher`).
/// Names are the endonyms; the title and close label are passed in. The app keeps the choice and rebuilds with the new
/// `Locale` (right-to-left follows automatically for Arabic).
class HqLanguagePicker extends StatelessWidget {
  const HqLanguagePicker({super.key, required this.value, required this.onChanged, this.languages = hqSupportedLanguages, this.title = '', this.closeLabel = '', this.label = ''});

  final String value;
  final ValueChanged<String> onChanged;
  final List<HqLanguage> languages;
  final String title;
  final String closeLabel;

  /// Screen-reader text of the button (e.g. "Language").
  final String label;

  Future<void> _open(BuildContext context) async {
    final chosen = await HqModal.show<String>(
      context,
      title: title,
      closeLabel: closeLabel,
      builder: (modalContext) => HqRadioGroup<String>(
        value: value,
        options: [for (final l in languages) HqRadioOption(value: l.code, label: l.nativeName)],
        onChanged: (code) => Navigator.of(modalContext).pop(code),
      ),
    );
    if (chosen != null && chosen != value) onChanged(chosen);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final current = languages.where((l) => l.code == value).firstOrNull;
    return Semantics(
      button: true,
      label: '$label ${current?.nativeName ?? value}'.trim(),
      excludeSemantics: true,
      onTap: () => _open(context),
      child: InkWell(
        key: const ValueKey<String>('hq-language-picker'),
        borderRadius: BorderRadius.circular(HqRadius.pill),
        onTap: () => _open(context),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: HqSpacing.md, vertical: HqSpacing.sm),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.language, size: 20, color: tokens.muted),
              const SizedBox(width: HqSpacing.xs),
              Text(current?.nativeName ?? value, style: Theme.of(context).textTheme.labelLarge),
              Icon(Icons.arrow_drop_down, color: tokens.muted),
            ],
          ),
        ),
      ),
    );
  }
}
