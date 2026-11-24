import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

void main() {
  group('HqToastService', () {
    for (final entry in <String, void Function(HqToastService, String)>{
      'success': (s, m) => s.success(m),
      'error': (s, m) => s.error(m),
      'warning': (s, m) => s.warning(m),
      'info': (s, m) => s.info(m),
    }.entries) {
      testWidgets('${entry.key} shows the message then goes away', (tester) async {
        final service = HqToastService();
        await tester.pumpWidget(hqTestApp(const SizedBox(), messengerKey: service.messengerKey));
        entry.value(service, 'Saved ${entry.key}');
        await tester.pump();
        expect(find.text('Saved ${entry.key}'), findsOneWidget);
        // The auto-dismiss timer starts when the enter animation ends, so advance in two steps.
        await tester.pump(const Duration(seconds: 1));
        await tester.pump(const Duration(seconds: 6));
        await tester.pumpAndSettle();
        expect(find.text('Saved ${entry.key}'), findsNothing);
      });
    }

    testWidgets('a new toast replaces the one on screen; before mounting it does nothing', (tester) async {
      final service = HqToastService();
      service.info('ignored');
      await tester.pumpWidget(hqTestApp(const SizedBox(), messengerKey: service.messengerKey));
      service.info('first');
      await tester.pump();
      service.error('second');
      await tester.pumpAndSettle();
      expect(find.text('second'), findsOneWidget);
      expect(find.text('first'), findsNothing);
    });
  });

  group('HqDialog', () {
    Widget host(Future<void> Function(BuildContext) action) =>
        Builder(builder: (context) => HqButton(label: 'Go', onPressed: () => action(context)));

    testWidgets('confirm returns true and false', (tester) async {
      bool? result;
      await tester.pumpWidget(hqTestApp(host((c) async => result = await HqDialog.confirm(c, title: 'Sure?', message: 'Really', confirmLabel: 'Yes', cancelLabel: 'No'))));
      await tester.tap(find.text('Go'));
      await tester.pumpAndSettle();
      expect(find.text('Really'), findsOneWidget);
      await tester.tap(find.text('Yes'));
      await tester.pumpAndSettle();
      expect(result, isTrue);
      await tester.tap(find.text('Go'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('No'));
      await tester.pumpAndSettle();
      expect(result, isFalse);
    });

    testWidgets('alert closes with its button', (tester) async {
      await tester.pumpWidget(hqTestApp(host((c) => HqDialog.alert(c, title: 'Heads up', okLabel: 'OK'))));
      await tester.tap(find.text('Go'));
      await tester.pumpAndSettle();
      expect(find.text('Heads up'), findsOneWidget);
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();
      expect(find.text('Heads up'), findsNothing);
    });

    testWidgets('prompt requires a value when asked, then returns it trimmed', (tester) async {
      String? result = 'unset';
      await tester.pumpWidget(hqTestApp(host((c) async => result = await HqDialog.prompt(c, title: 'Reason', label: 'Reason', confirmLabel: 'Submit', cancelLabel: 'Cancel', required: true, requiredMessage: 'Enter a reason'))));
      await tester.tap(find.text('Go'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit'));
      await tester.pump();
      expect(find.text('Enter a reason'), findsOneWidget);
      await tester.enterText(find.byType(TextField), '  duplicate  ');
      await tester.tap(find.text('Submit'));
      await tester.pumpAndSettle();
      expect(result, 'duplicate');
    });

    testWidgets('a password prompt hides the text and returns it untrimmed', (tester) async {
      String? result = 'unset';
      await tester.pumpWidget(hqTestApp(host((c) async => result = await HqDialog.prompt(c, title: 'Password', label: 'Password', confirmLabel: 'Submit', cancelLabel: 'Cancel', type: HqFieldType.password))));
      await tester.tap(find.text('Go'));
      await tester.pumpAndSettle();
      expect(tester.widget<TextField>(find.byType(TextField)).obscureText, isTrue);
      await tester.enterText(find.byType(TextField), ' secret ');
      await tester.tap(find.text('Submit'));
      await tester.pumpAndSettle();
      expect(result, ' secret ');
    });

    testWidgets('prompt cancel returns null', (tester) async {
      String? result = 'unset';
      await tester.pumpWidget(hqTestApp(host((c) async => result = await HqDialog.prompt(c, title: 'T', confirmLabel: 'Ok', cancelLabel: 'Cancel'))));
      await tester.tap(find.text('Go'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(result, isNull);
    });
  });
}
