import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

void main() {
  group('grouping', () {
    test('Indian grouping', () {
      expect(hqGroupDigits('1234567'), '12,34,567');
      expect(hqGroupDigits('123'), '123');
      expect(hqGroupDigits('1000'), '1,000');
      expect(hqGroupDigits('100000'), '1,00,000');
    });
    test('international grouping', () {
      expect(hqGroupDigits('1234567', indian: false), '1,234,567');
    });
    test('formats minor units with paise precision', () {
      expect(hqFormatMinor(123456789), '12,34,567.89');
      expect(hqFormatMinor(5), '0.05');
      expect(hqFormatMinor(-250), '-2.50');
      expect(hqFormatMinor(1500, decimals: 0), '1,500');
    });
  });

  group('hqParseDecimalToMinor rounds HALF_EVEN', () {
    test('exact values', () {
      expect(hqParseDecimalToMinor('1234.5'), 123450);
      expect(hqParseDecimalToMinor('.25'), 25);
      expect(hqParseDecimalToMinor('-7'), -700);
    });
    test('half goes to the even digit', () {
      expect(hqParseDecimalToMinor('0.125'), 12);
      expect(hqParseDecimalToMinor('0.135'), 14);
      expect(hqParseDecimalToMinor('0.1251'), 13);
      expect(hqParseDecimalToMinor('2.675'), 268);
    });
    test('rejects non decimals', () {
      expect(hqParseDecimalToMinor(''), isNull);
      expect(hqParseDecimalToMinor('1e5'), isNull);
      expect(hqParseDecimalToMinor('abc'), isNull);
    });
  });

  group('hqFormatMoney', () {
    test('INR with symbol and Indian grouping', () {
      expect(hqFormatMoney(1234567.5), '₹12,34,567.50');
      expect(hqFormatMoney('99.999'), '₹100.00');
    });
    test('other currencies group in threes and use their decimals', () {
      expect(hqFormatMoney(1234567.5, currency: 'USD'), r'$1,234,567.50');
      expect(hqFormatMoney(12.3456, currency: 'KWD'), 'KWD 12.346');
      expect(hqFormatMoney(1500, currency: 'JPY', showSymbol: false), '1,500');
    });
    test('null and junk fall back', () {
      expect(hqFormatMoney(null), '-');
      expect(hqFormatMoney('n/a', fallback: '?'), '?');
    });
    test('negative amounts keep the sign before the symbol', () {
      expect(hqFormatMoney(-1234.5), '-₹1,234.50');
    });
  });

  test('formatter limits decimals, groups and drops junk', () {
    const formatter = HqAmountFormatter(decimals: 2, indian: true);
    String run(String input) => formatter.formatEditUpdate(TextEditingValue.empty, TextEditingValue(text: input)).text;
    expect(run('1234567.899'), '12,34,567.89');
    expect(run('a1b2'), '12');
    expect(run('.5'), '0.5');
    expect(run('007'), '7');
    expect(run('1.2.3'), '1.23');
  });

  group('HqAmountInput', () {
    testWidgets('reports plain text and minor units while showing grouped text', (tester) async {
      HqAmountValue? last;
      await tester.pumpWidget(hqTestApp(HqAmountInput(label: 'Amount', onChanged: (v) => last = v)));
      await tester.enterText(find.byType(TextField), '1234567.5');
      await tester.pump();
      expect(find.text('12,34,567.5'), findsOneWidget);
      expect(last?.text, '1234567.5');
      expect(last?.minorUnits, 123456750);
      expect(last?.currency, 'INR');
    });

    testWidgets('uses the configured default currency', (tester) async {
      HqAmountValue? last;
      await tester.pumpWidget(hqTestApp(
        HqConfigScope(
          config: const HqConfig(gatewayUrl: 'https://x/api', defaultCurrency: 'USD'),
          child: HqAmountInput(onChanged: (v) => last = v),
        ),
      ));
      await tester.enterText(find.byType(TextField), '1234567');
      await tester.pump();
      expect(find.text('1,234,567'), findsOneWidget);
      expect(last?.currency, 'USD');
    });

    testWidgets('shows the passed-in message below the minimum', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqAmountInput(min: 5000, minError: 'Minimum is 5,000')));
      await tester.enterText(find.byType(TextField), '100');
      await tester.pump();
      expect(find.text('Minimum is 5,000'), findsOneWidget);
      await tester.enterText(find.byType(TextField), '6000');
      await tester.pump();
      expect(find.text('Minimum is 5,000'), findsNothing);
    });

    testWidgets('shows the maximum message', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqAmountInput(max: 100, maxError: 'Too much')));
      await tester.enterText(find.byType(TextField), '101');
      await tester.pump();
      expect(find.text('Too much'), findsOneWidget);
    });
  });
}
