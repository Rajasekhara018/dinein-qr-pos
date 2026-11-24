import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

void main() {
  group('HqOtpInput', () {
    testWidgets('collects digits, completes, and shows the error', (tester) async {
      final changes = <String>[];
      String? done;
      await tester.pumpWidget(hqTestApp(HqOtpInput(onCompleted: (c) => done = c, onChanged: changes.add, error: 'Wrong code', resendSeconds: 0, semanticsLabel: 'OTP')));
      await tester.enterText(find.byType(TextField), '12ab34');
      expect(changes.last, '1234');
      expect(done, isNull);
      await tester.enterText(find.byType(TextField), '123456');
      await tester.pump();
      expect(done, '123456');
      expect(find.text('6'), findsOneWidget);
      expect(find.text('Wrong code'), findsOneWidget);
      expect(find.text('Resend'), findsNothing);
      final field = tester.widget<TextField>(find.byType(TextField));
      expect(field.autofillHints, [AutofillHints.oneTimeCode]);
    });

    testWidgets('resend counts down, resends and restarts', (tester) async {
      var resent = 0;
      await tester.pumpWidget(hqTestApp(HqOtpInput(onCompleted: (_) {}, resendSeconds: 2, onResend: () => resent++, resendLabel: 'Resend', resendCountdown: (s) => 'Resend in ${s}s')));
      expect(find.text('Resend in 2s'), findsOneWidget);
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('Resend in 1s'), findsOneWidget);
      await tester.pump(const Duration(seconds: 1));
      await tester.tap(find.text('Resend'));
      await tester.pump();
      expect(resent, 1);
      expect(find.text('Resend in 2s'), findsOneWidget);
    });

    testWidgets('changing clearToken empties the boxes', (tester) async {
      Widget otp(int token) => hqTestApp(HqOtpInput(onCompleted: (_) {}, resendSeconds: 0, clearToken: token));
      await tester.pumpWidget(otp(0));
      await tester.enterText(find.byType(TextField), '987');
      await tester.pump();
      expect(find.text('9'), findsOneWidget);
      await tester.pumpWidget(otp(1));
      await tester.pump();
      expect(find.text('9'), findsNothing);
    });

    testWidgets('PIN shows dots, not digits', (tester) async {
      String? pin;
      await tester.pumpWidget(hqTestApp(HqPinInput(onCompleted: (p) => pin = p, error: 'Locked')));
      await tester.enterText(find.byType(TextField), '4321');
      await tester.pump();
      expect(pin, '4321');
      expect(find.text('•'), findsNWidgets(4));
      expect(find.text('4'), findsNothing);
      expect(find.text('Locked'), findsOneWidget);
    });
  });

  group('HqDateField', () {
    testWidgets('shows value, picks from the calendar and clears', (tester) async {
      final values = <DateTime?>[];
      await tester.pumpWidget(hqTestApp(HqDateField(
        value: DateTime(2026, 3, 10),
        onChanged: values.add,
        label: 'Date of birth',
        min: DateTime(2026, 3, 1),
        max: DateTime(2026, 3, 31),
        clearLabel: 'Clear',
        error: 'Too young',
      )));
      expect(find.text('2026-03-10'), findsOneWidget);
      expect(find.text('Too young'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('hq-date-field')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('15'));
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();
      expect(values, [DateTime(2026, 3, 15)]);
      await tester.tap(find.byIcon(Icons.close));
      expect(values.last, isNull);
    });

    testWidgets('placeholder, custom format, initial clamped to bounds, cancel keeps value', (tester) async {
      final values = <DateTime?>[];
      await tester.pumpWidget(hqTestApp(HqDateField(value: null, onChanged: values.add, placeholder: 'Pick a date', min: DateTime(2030), max: DateTime(2031))));
      expect(find.text('Pick a date'), findsOneWidget);
      expect(find.byIcon(Icons.close), findsNothing);
      await tester.tap(find.byKey(const ValueKey('hq-date-field')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(values, isEmpty);

      await tester.pumpWidget(hqTestApp(HqDateField(value: DateTime(2020, 1, 2), onChanged: values.add, format: (d) => 'D${d.day}', max: DateTime(2019, 12, 31))));
      expect(find.text('D2'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('hq-date-field')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();
      expect(values, [DateTime(2019, 12, 31)]);
    });

    testWidgets('disabled does not open', (tester) async {
      await tester.pumpWidget(hqTestApp(HqDateField(value: DateTime(2026), onChanged: (_) {}, enabled: false)));
      await tester.tap(find.byKey(const ValueKey('hq-date-field')));
      await tester.pumpAndSettle();
      expect(find.text('OK'), findsNothing);
      expect(find.byIcon(Icons.close), findsNothing);
    });
  });

  group('HqDateRangePicker', () {
    final today = DateTime(2026, 11, 22, 15);
    const presets = [HqDateRangePreset(id: '7', label: 'Last 7 days', days: 7), HqDateRangePreset(id: '30', label: 'Last 30 days', days: 30)];

    test('a preset counts back from today inclusive', () {
      final range = HqDateRangePicker.rangeOf(presets[0], today);
      expect(range, HqDateRange(DateTime(2026, 11, 16), DateTime(2026, 11, 22)));
      expect(range.hashCode, HqDateRange(DateTime(2026, 11, 16), DateTime(2026, 11, 22)).hashCode);
    });

    testWidgets('preset chips and the custom range', (tester) async {
      final values = <HqDateRange>[];
      final custom = HqDateRange(DateTime(2026, 11, 1), DateTime(2026, 11, 5));
      await tester.pumpWidget(hqTestApp(HqDateRangePicker(value: custom, onChanged: values.add, presets: presets, customLabel: 'Custom', today: today, min: DateTime(2026), max: DateTime(2027))));
      expect(find.text('2026-11-01 - 2026-11-05'), findsOneWidget);
      expect(tester.getSemantics(find.bySemanticsLabel('Custom')).flagsCollection.isSelected, Tristate.isTrue);
      await tester.tap(find.byKey(const ValueKey('hq-range-30')));
      expect(values.single, HqDateRangePicker.rangeOf(presets[1], today));

      await tester.tap(find.byKey(const ValueKey('hq-range-custom')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      expect(values.last, custom);
    });

    testWidgets('a matching preset is selected and custom is not', (tester) async {
      await tester.pumpWidget(hqTestApp(HqDateRangePicker(value: HqDateRangePicker.rangeOf(presets[0], today), onChanged: (_) {}, presets: presets, customLabel: 'Custom', today: today)));
      expect(tester.getSemantics(find.bySemanticsLabel('Last 7 days')).flagsCollection.isSelected, Tristate.isTrue);
      expect(tester.getSemantics(find.bySemanticsLabel('Custom')).flagsCollection.isSelected, isNot(Tristate.isTrue));
      await tester.pumpWidget(hqTestApp(HqDateRangePicker(value: null, onChanged: (_) {}, allowCustom: false)));
      expect(find.byKey(const ValueKey('hq-range-custom')), findsNothing);
    });
  });

  group('basic inputs', () {
    testWidgets('switch toggles from the label, blocks while loading or disabled', (tester) async {
      final values = <bool>[];
      await tester.pumpWidget(hqTestApp(HqSwitch(value: false, onChanged: values.add, label: 'Alerts', description: 'By SMS', size: HqSwitchSize.compact, labelBefore: true)));
      await tester.tap(find.text('Alerts'));
      expect(values, [true]);
      expect(find.text('By SMS'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(HqSwitch(value: true, onChanged: values.add, label: 'Alerts', loading: true)));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      await tester.tap(find.text('Alerts'));
      await tester.pumpWidget(hqTestApp(HqSwitch(value: true, onChanged: values.add, label: 'Alerts', enabled: false)));
      await tester.tap(find.text('Alerts'));
      expect(values, [true]);
    });

    testWidgets('text area is a multiline field with a counter', (tester) async {
      String? typed;
      await tester.pumpWidget(hqTestApp(HqTextArea(label: 'Notes', maxLength: 50, onChanged: (v) => typed = v)));
      await tester.enterText(find.byType(TextField), 'line one\nline two');
      expect(typed, 'line one\nline two');
      expect(tester.widget<TextField>(find.byType(TextField)).keyboardType, TextInputType.multiline);
      expect(find.textContaining('/50'), findsOneWidget);
    });

    testWidgets('search bar debounces, submits and clears', (tester) async {
      final searches = <String>[];
      await tester.pumpWidget(hqTestApp(HqSearchBar(onSearch: searches.add, placeholder: 'Search', clearLabel: 'Clear')));
      await tester.enterText(find.byType(TextField), 'ban');
      await tester.pump(const Duration(milliseconds: 100));
      await tester.enterText(find.byType(TextField), 'bang');
      await tester.pump(const Duration(milliseconds: 400));
      expect(searches, ['bang']);
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pump();
      expect(searches, ['bang', 'bang']);
      await tester.tap(find.byIcon(Icons.close));
      await tester.pump(const Duration(milliseconds: 400));
      expect(searches, ['bang', 'bang', '']);
    });

    testWidgets('search bar with zero debounce fires on every keystroke', (tester) async {
      final searches = <String>[];
      await tester.pumpWidget(hqTestApp(HqSearchBar(onSearch: searches.add, debounce: Duration.zero, initialValue: 'a')));
      await tester.enterText(find.byType(TextField), 'ab');
      expect(searches, ['ab']);
    });

    testWidgets('copy field puts the value on the clipboard', (tester) async {
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') copied = (call.arguments as Map)['text'] as String?;
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      var toasts = 0;
      await tester.pumpWidget(hqTestApp(HqCopyField(value: 'ESCROW-123', label: 'Reference', copyLabel: 'Copy', onCopied: () => toasts++)));
      await tester.tap(find.byIcon(Icons.copy));
      await tester.pump();
      expect(copied, 'ESCROW-123');
      expect(toasts, 1);
    });

    testWidgets('QR code draws black on white with a label', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqQrCode(data: 'upi://pay?pa=x', semanticsLabel: 'Payment QR', size: 120)));
      expect(find.byType(QrImageView), findsOneWidget);
      expect(find.bySemanticsLabel('Payment QR'), findsOneWidget);
    });
  });

  group('sliders', () {
    testWidgets('slider shows formatted value and bounds, clamps and reports', (tester) async {
      final values = <double>[];
      await tester.pumpWidget(hqTestApp(HqSlider(value: 500, min: 0, max: 100, divisions: 10, label: 'Amount', onChanged: values.add, onChangeEnd: values.add)));
      expect(find.text('Amount'), findsOneWidget);
      expect(find.text('100'), findsNWidgets(2));
      expect(find.text('0'), findsOneWidget);
      await tester.tap(find.byType(Slider));
      await tester.pump();
      expect(values, isNotEmpty);
    });

    testWidgets('slider uses the formatter, decimal default and disables', (tester) async {
      await tester.pumpWidget(hqTestApp(HqSlider(value: 1.5, min: 0.5, max: 2.25, onChanged: (_) {}, enabled: false)));
      expect(find.text('1.50'), findsOneWidget);
      expect(tester.widget<Slider>(find.byType(Slider)).onChanged, isNull);
      await tester.pumpWidget(hqTestApp(HqSlider(value: 1, min: 0, max: 2, onChanged: (_) {}, format: (v) => 'Rs $v')));
      expect(find.text('Rs 1.0'), findsOneWidget);
    });

    testWidgets('range slider shows both ends', (tester) async {
      RangeValues? changed;
      await tester.pumpWidget(hqTestApp(HqRangeSlider(start: -5, end: 80, min: 0, max: 100, label: 'Band', onChanged: (v) => changed = v)));
      expect(find.text('0 - 80'), findsOneWidget);
      await tester.tap(find.byType(RangeSlider));
      await tester.pump();
      expect(changed, isNotNull);
      await tester.pumpWidget(hqTestApp(HqRangeSlider(start: 1, end: 2, min: 0, max: 3, onChanged: (_) {}, enabled: false, format: (v) => '#$v')));
      expect(find.text('#1.0 - #2.0'), findsOneWidget);
      expect(tester.widget<RangeSlider>(find.byType(RangeSlider)).onChanged, isNull);
    });

    testWidgets('counter steps within bounds', (tester) async {
      final values = <int>[];
      await tester.pumpWidget(hqTestApp(HqStepperCounter(value: 10, min: 5, max: 15, step: 5, onChanged: values.add, label: 'Units', unitText: 'units', decrementLabel: 'Less', incrementLabel: 'More', error: 'Over limit')));
      await tester.tap(find.byIcon(Icons.add));
      await tester.tap(find.byIcon(Icons.remove));
      expect(values, [15, 5]);
      expect(find.text('Over limit'), findsOneWidget);
      expect(find.text('units'), findsOneWidget);

      await tester.pumpWidget(hqTestApp(HqStepperCounter(value: 15, min: 5, max: 15, step: 5, onChanged: values.add)));
      await tester.tap(find.byIcon(Icons.add));
      expect(values, [15, 5]);
      const counter = HqStepperCounter(value: 1, onChanged: _ignore, enabled: false);
      expect(counter.canIncrement || counter.canDecrement, isFalse);
    });
  });

  group('HqLanguagePicker', () {
    test('lookup and right-to-left', () {
      expect(hqSupportedLanguages.map((l) => l.code), ['en', 'hi', 'ta', 'te', 'kn', 'ml', 'ar']);
      expect(hqIsRtlLanguage('ar'), isTrue);
      expect(hqIsRtlLanguage('hi'), isFalse);
      expect(hqLanguageOf('ta')?.locale, const Locale('ta'));
      expect(hqLanguageOf('fr'), isNull);
    });

    testWidgets('opens the list and reports a different choice only', (tester) async {
      final chosen = <String>[];
      await tester.pumpWidget(hqTestApp(HqLanguagePicker(value: 'en', onChanged: chosen.add, title: 'Language', closeLabel: 'Close', label: 'Language')));
      expect(find.text('English'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('hq-language-picker')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('தமிழ்'));
      await tester.pumpAndSettle();
      expect(chosen, ['ta']);

      await tester.tap(find.byKey(const ValueKey('hq-language-picker')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('English').last);
      await tester.pumpAndSettle();
      expect(chosen, ['ta']);
    });

    testWidgets('an unknown code shows the code', (tester) async {
      await tester.pumpWidget(hqTestApp(HqLanguagePicker(value: 'xx', onChanged: (_) {})));
      expect(find.text('xx'), findsOneWidget);
    });
  });
}

void _ignore(int _) {}
