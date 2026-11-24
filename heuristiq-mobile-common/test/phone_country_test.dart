import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

List<String> _isos(List<HqPhoneCountry> list) => list.map((c) => c.iso).toList();

void main() {
  group('HqCountryData', () {
    test('every entry has a unique ISO code, a sane calling code, a name and a valid length range', () {
      final all = HqCountryData.all;
      expect(all.length, greaterThan(240));
      expect(_isos(all).toSet().length, all.length);
      for (final c in all) {
        expect(RegExp(r'^[A-Z]{2}$').hasMatch(c.iso), isTrue, reason: c.iso);
        expect(RegExp(r'^[1-9]\d{0,2}$').hasMatch(c.dialCode), isTrue, reason: '${c.iso} ${c.dialCode}');
        expect(c.name, isNotEmpty, reason: c.iso);
        expect(c.nationalMin, greaterThan(0), reason: c.iso);
        expect(c.nationalMax, greaterThanOrEqualTo(c.nationalMin), reason: c.iso);
        expect(c.dialCode.length + c.nationalMax, lessThanOrEqualTo(hqPhoneMaxE164Digits + 3), reason: c.iso);
      }
    });

    test('is sorted by name with accented names under their base letter', () {
      final names = HqCountryData.all.map((c) => c.name).toList();
      expect(names.first, 'Afghanistan');
      expect(names.indexOf('Åland Islands'), lessThan(names.indexOf('Albania')));
      expect(names.indexOf('Türkiye'), greaterThan(names.indexOf('Tunisia')));
    });

    test('knows the main countries and shared calling codes', () {
      expect(HqCountryData.byIso('in')!.dialCode, '91');
      expect(HqCountryData.byIso('AE')!.dialCode, '971');
      expect(HqCountryData.byIso('XX'), isNull);
      for (final iso in ['US', 'CA', 'JM', 'PR', 'TT']) {
        expect(HqCountryData.byIso(iso)!.dialCode, '1', reason: iso);
      }
      expect(HqCountryData.byIso('JM')!.areaCodes, ['876']);
      expect(HqCountryData.byIso('JM')!.dialDisplay, '+1 876');
      expect(HqCountryData.byIso('DO')!.areaCodes.length, 3);
      expect(HqCountryData.byIso('DO')!.dialDisplay, '+1');
      expect(HqCountryData.all.where((c) => c.dialCode == '44').map((c) => c.iso), containsAll(['GB', 'GG', 'JE', 'IM']));
      expect(HqCountryData.all.where((c) => c.dialCode == '7').map((c) => c.iso), containsAll(['RU', 'KZ']));
    });

    test('flag emoji from ISO letters', () {
      expect(HqCountryData.flagEmoji('IN'), '\u{1F1EE}\u{1F1F3}');
      expect(HqCountryData.flagEmoji('us'), '\u{1F1FA}\u{1F1F8}');
      expect(HqCountryData.flagEmoji('XYZ'), '');
      expect(HqCountryData.flagEmoji('1A'), '');
      expect(HqCountryData.byIso('IN')!.flag, '\u{1F1EE}\u{1F1F3}');
    });
  });

  group('search', () {
    final all = HqCountryData.all;

    test('by dial code with or without plus and spaces', () {
      for (final q in ['91', '+91', ' +91 ']) {
        expect(_isos(HqCountryData.search(all, q)), contains('IN'), reason: q);
      }
      expect(_isos(HqCountryData.search(all, '+91')), isNot(contains('US')));
      expect(_isos(HqCountryData.search(all, '1')), containsAll(['US', 'CA', 'JM']));
      expect(_isos(HqCountryData.search(all, '1 876')), ['JM']);
      expect(_isos(HqCountryData.search(all, '+1876')), ['JM']);
    });

    test('by name, case-insensitive, prefix before substring', () {
      final result = _isos(HqCountryData.search(all, 'ind'));
      expect(result.first, 'IN');
      expect(result, containsAll(['ID', 'IO']));
      expect(_isos(HqCountryData.search(all, 'UNITED')), containsAll(['AE', 'GB', 'US']));
      expect(_isos(HqCountryData.search(all, 'land')), contains('FI'));
    });

    test('by ISO code: an exact code ranks first', () {
      expect(_isos(HqCountryData.search(all, 'IN')).first, 'IN');
      expect(_isos(HqCountryData.search(all, 'ae')).first, 'AE');
    });

    test('localized names are searched too, and nothing matches gibberish', () {
      final hits = HqCountryData.search(all, 'भारत', names: const {'IN': 'भारत'});
      expect(_isos(hits), ['IN']);
      expect(HqCountryData.search(all, 'zzzzqq'), isEmpty);
      expect(HqCountryData.search(all, '   ').length, all.length);
    });

    test('preferred countries are pinned in order', () {
      final ordered = HqCountryData.withPreferredFirst(all, ['AE', 'IN', 'zz']);
      expect(_isos(ordered).take(2), ['AE', 'IN']);
      expect(ordered.length, all.length);
      expect(_isos(ordered).where((i) => i == 'IN').length, 1);
    });
  });

  group('validation', () {
    final india = HqCountryData.byIso('IN')!;
    final us = HqCountryData.byIso('US')!;

    test('India is strict: 6-9 then nine digits', () {
      expect(hqIsValidPhone('9876543210', india), isTrue);
      expect(hqIsValidPhone('6000000000', india), isTrue);
      expect(hqIsValidPhone('5876543210', india), isFalse);
      expect(hqIsValidPhone('987654321', india), isFalse);
      expect(hqIsValidPhone('98765432100', india), isFalse);
      expect(hqIsValidPhone('98765x3210', india), isFalse);
    });

    test('NANP numbers need a first digit 2-9 and ten digits', () {
      expect(hqIsValidPhone('2025550123', us), isTrue);
      expect(hqIsValidPhone('1025550123', us), isFalse);
      expect(hqIsValidPhone('202555012', us), isFalse);
      expect(hqBuildPhoneValue(HqCountryData.byIso('JM')!, '8765550123').e164, '+18765550123');
      expect(hqBuildPhoneValue(us, '(202) 555-0123').e164, '+12025550123');
    });

    test('E.164 never exceeds 15 digits', () {
      final se = HqCountryData.byIso('SE')!;
      expect(hqIsValidPhone('1234567890123', se), isTrue);
      expect(hqIsValidPhone('12345678901234', se), isFalse);
    });

    test('trunk prefix zero is dropped except where it belongs to the number', () {
      expect(hqBuildPhoneValue(india, '09876543210').national, '9876543210');
      expect(hqBuildPhoneValue(HqCountryData.byIso('GB')!, '07911123456').e164, '+447911123456');
      final it = HqCountryData.byIso('IT')!;
      expect(hqBuildPhoneValue(it, '0612345678').national, '0612345678');
      expect(hqBuildPhoneValue(it, '0612345678').e164, '+390612345678');
    });
  });

  group('HqFlag', () {
    test('auto picks the emoji on mobile and macOS, the badge on web, Windows and Linux', () {
      expect(HqFlag.usesEmoji(HqFlagStyle.auto, platform: TargetPlatform.android, web: false), isTrue);
      expect(HqFlag.usesEmoji(HqFlagStyle.auto, platform: TargetPlatform.iOS, web: false), isTrue);
      expect(HqFlag.usesEmoji(HqFlagStyle.auto, platform: TargetPlatform.macOS, web: false), isTrue);
      expect(HqFlag.usesEmoji(HqFlagStyle.auto, platform: TargetPlatform.windows, web: false), isFalse);
      expect(HqFlag.usesEmoji(HqFlagStyle.auto, platform: TargetPlatform.linux, web: false), isFalse);
      // On the web Flutter fetches Noto Color Emoji, so flags draw on every OS, Windows included.
      expect(HqFlag.usesEmoji(HqFlagStyle.auto, platform: TargetPlatform.android, web: true), isTrue);
      expect(HqFlag.usesEmoji(HqFlagStyle.auto, platform: TargetPlatform.windows, web: true), isTrue);
      expect(HqFlag.usesEmoji(HqFlagStyle.emoji, platform: TargetPlatform.windows, web: false), isTrue);
      expect(HqFlag.usesEmoji(HqFlagStyle.badge, platform: TargetPlatform.android, web: false), isFalse);
    });

    testWidgets('badge style shows the ISO code; emoji style shows the flag', (tester) async {
      await tester.pumpWidget(hqTestApp(const Column(children: [HqFlag(iso: 'in', style: HqFlagStyle.badge), HqFlag(iso: 'US', style: HqFlagStyle.emoji)])));
      expect(find.text('IN'), findsOneWidget);
      expect(find.text('\u{1F1FA}\u{1F1F8}'), findsOneWidget);
    });
  });

  group('HqPhoneNumberInput picker', () {
    Future<void> openPicker(WidgetTester tester) async {
      await tester.tap(find.byKey(const ValueKey('hq-phone-country')));
      await tester.pumpAndSettle();
    }

    Widget input({ValueChanged<HqPhoneValue>? onChanged, Map<String, String>? names, List<String> preferred = const ['IN'], String? iso}) => HqPhoneNumberInput(
          label: 'Mobile',
          countryIso: iso,
          onChanged: onChanged,
          countryNames: names,
          preferredCountries: preferred,
          pickerTitle: 'Country code',
          searchHint: 'Search country or code',
          noResultsText: 'No country found',
          closeLabel: 'Close',
          flagStyle: HqFlagStyle.badge,
          invalidError: 'Invalid number',
        );

    testWidgets('shows India first with its code, opens a searchable list and reports the right E.164', (tester) async {
      HqPhoneValue? last;
      await tester.pumpWidget(hqTestApp(input(onChanged: (v) => last = v)));
      expect(find.text('+91'), findsOneWidget);
      await openPicker(tester);
      expect(find.text('Country code'), findsOneWidget);
      expect(find.text('India'), findsOneWidget);
      await tester.enterText(find.byType(TextField).last, '+971');
      await tester.pumpAndSettle();
      expect(find.text('United Arab Emirates'), findsOneWidget);
      expect(find.text('India'), findsNothing);
      await tester.tap(find.text('United Arab Emirates'));
      await tester.pumpAndSettle();
      expect(find.text('+971'), findsOneWidget);
      await tester.enterText(find.byType(TextField), '501234567');
      await tester.pump();
      expect(last?.e164, '+971501234567');
      expect(last?.countryIso, 'AE');
    });

    testWidgets('preferred countries are pinned above the alphabetical list', (tester) async {
      await tester.pumpWidget(hqTestApp(input(preferred: const ['AE', 'IN'])));
      await openPicker(tester);
      final ae = tester.getTopLeft(find.byKey(const ValueKey('hq-country-AE'))).dy;
      final india = tester.getTopLeft(find.byKey(const ValueKey('hq-country-IN'))).dy;
      final afghanistan = tester.getTopLeft(find.byKey(const ValueKey('hq-country-AF'))).dy;
      expect(ae, lessThan(india));
      expect(india, lessThan(afghanistan));
    });

    testWidgets('typing a name, then enter, picks the first result', (tester) async {
      HqPhoneValue? last;
      await tester.pumpWidget(hqTestApp(input(onChanged: (v) => last = v)));
      await openPicker(tester);
      await tester.enterText(find.byType(TextField).last, 'singa');
      await tester.pumpAndSettle();
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pumpAndSettle();
      expect(find.text('+65'), findsOneWidget);
      await tester.enterText(find.byType(TextField), '81234567');
      await tester.pump();
      expect(last?.e164, '+6581234567');
    });

    testWidgets('no match shows the passed-in text', (tester) async {
      await tester.pumpWidget(hqTestApp(input()));
      await openPicker(tester);
      await tester.enterText(find.byType(TextField).last, 'qqqqzz');
      await tester.pumpAndSettle();
      expect(find.text('No country found'), findsOneWidget);
    });

    testWidgets('localized names are shown and searched', (tester) async {
      await tester.pumpWidget(hqTestApp(input(names: const {'IN': 'भारत', 'AE': 'संयुक्त अरब अमीरात'})));
      await openPicker(tester);
      expect(find.text('भारत'), findsOneWidget);
      await tester.enterText(find.byType(TextField).last, 'अरब');
      await tester.pumpAndSettle();
      expect(find.text('संयुक्त अरब अमीरात'), findsOneWidget);
    });

    testWidgets('the picker closes with its close button without changing the country', (tester) async {
      await tester.pumpWidget(hqTestApp(input()));
      await openPicker(tester);
      await tester.tap(find.byTooltip('Close'));
      await tester.pumpAndSettle();
      expect(find.text('Country code'), findsNothing);
      expect(find.text('+91'), findsOneWidget);
    });

    testWidgets('a +1 territory shows its area code and keeps India strict when switching back', (tester) async {
      HqPhoneValue? last;
      await tester.pumpWidget(hqTestApp(input(onChanged: (v) => last = v, iso: 'JM')));
      expect(find.text('+1 876'), findsOneWidget);
      await tester.enterText(find.byType(TextField), '2025550123');
      await tester.pump();
      expect(last?.e164, '+12025550123');
      await openPicker(tester);
      await tester.tap(find.byKey(const ValueKey('hq-country-IN')));
      await tester.pumpAndSettle();
      expect(last?.isValid, isFalse);
      expect(find.text('Invalid number'), findsOneWidget);
    });

    testWidgets('the list is right to left in Arabic and calling codes stay left to right', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: HqTheme.light(),
        locale: const Locale('ar'),
        supportedLocales: const [Locale('ar')],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        home: Scaffold(body: Padding(padding: const EdgeInsets.all(16), child: input(names: const {'IN': 'الهند'}))),
      ));
      await openPicker(tester);
      final context = tester.element(find.byKey(const ValueKey('hq-country-IN')));
      expect(Directionality.of(context), TextDirection.rtl);
      expect(find.text('الهند'), findsOneWidget);
      final dial = tester.element(find.text('+91').last);
      expect(Directionality.of(dial), TextDirection.ltr);
      // The name sits at the right edge, the calling code at the left.
      expect(tester.getTopRight(find.text('الهند')).dx, greaterThan(tester.getTopLeft(find.text('+91').last).dx));
    });

    testWidgets('limiting the countries limits the list', (tester) async {
      await tester.pumpWidget(hqTestApp(HqPhoneNumberInput(countries: [HqCountryData.byIso('IN')!, HqCountryData.byIso('AE')!], searchHint: 's', noResultsText: 'none', flagStyle: HqFlagStyle.badge)));
      await openPicker(tester);
      expect(find.byKey(const ValueKey('hq-country-AE')), findsOneWidget);
      expect(find.byKey(const ValueKey('hq-country-US')), findsNothing);
    });

    testWidgets('on a wide screen the picker is a dialog', (tester) async {
      tester.view.physicalSize = const Size(1000, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(hqTestApp(input()));
      await openPicker(tester);
      expect(find.byType(Dialog), findsOneWidget);
      expect(find.byType(BottomSheet), findsNothing);
    });
  });
}
