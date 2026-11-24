import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

void main() {
  group('HqToastService with an action', () {
    testWidgets('runs the action once, closes itself, and closes from its close button', (tester) async {
      final toast = HqToastService();
      var undone = 0;
      await tester.pumpWidget(MaterialApp(
        theme: HqTheme.light(),
        scaffoldMessengerKey: toast.messengerKey,
        home: const Scaffold(body: SizedBox()),
      ));

      toast.info('Keerthi removed', closeLabel: 'Close', action: HqToastAction(label: 'Undo', onPressed: () => undone++));
      await tester.pumpAndSettle();
      expect(find.text('Undo'), findsOneWidget);
      await tester.tap(find.text('Undo'));
      await tester.pumpAndSettle();
      expect(undone, 1);
      expect(find.text('Keerthi removed'), findsNothing);

      toast.info('Keerthi removed', closeLabel: 'Close', action: HqToastAction(label: 'Undo', onPressed: () => undone++));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Close'));
      await tester.pumpAndSettle();
      expect(find.text('Keerthi removed'), findsNothing);
      expect(undone, 1);
    });

    testWidgets('a toast with an action still times out', (tester) async {
      final toast = HqToastService();
      await tester.pumpWidget(MaterialApp(theme: HqTheme.light(), scaffoldMessengerKey: toast.messengerKey, home: const Scaffold()));
      toast.warning('Wallet is short', duration: const Duration(seconds: 2), action: HqToastAction(label: 'View', onPressed: () {}));
      await tester.pumpAndSettle();
      expect(find.text('Wallet is short'), findsOneWidget);
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
      expect(find.text('Wallet is short'), findsNothing);
    });
  });

  group('HqButton extras', () {
    testWidgets('the inverse tone is white with primary text when filled, white text when outlined', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: HqTheme.light(),
        home: const Scaffold(
          body: Column(children: [
            HqButton(label: 'Subscribe', color: HqButtonColor.inverse, onPressed: _noop),
            HqButton(label: 'Cancel plan', color: HqButtonColor.inverse, appearance: HqButtonAppearance.outline, onPressed: _noop),
          ]),
        ),
      ));
      final filled = tester.widget<FilledButton>(find.byType(FilledButton));
      expect(filled.style!.backgroundColor!.resolve({}), const Color(0xFFFFFFFF));
      expect(filled.style!.foregroundColor!.resolve({}), HqTokens.light.primary);
      final outlined = tester.widget<OutlinedButton>(find.byType(OutlinedButton));
      expect(outlined.style!.foregroundColor!.resolve({}), const Color(0xFFFFFFFF));
    });

    testWidgets('a leading widget is drawn before the label, and loading replaces both', (tester) async {
      const logo = SizedBox(key: Key('logo'), width: 20, height: 20);
      await tester.pumpWidget(MaterialApp(
        theme: HqTheme.light(),
        home: const Scaffold(body: HqButton(label: 'Continue with Google', leading: logo, onPressed: _noop)),
      ));
      expect(find.byKey(const Key('logo')), findsOneWidget);
      expect(find.text('Continue with Google'), findsOneWidget);
      expect(tester.getTopLeft(find.byKey(const Key('logo'))).dx, lessThan(tester.getTopLeft(find.text('Continue with Google')).dx));

      await tester.pumpWidget(MaterialApp(
        theme: HqTheme.light(),
        home: const Scaffold(body: HqButton(label: 'Continue with Google', leading: logo, loading: true, onPressed: _noop)),
      ));
      expect(find.byKey(const Key('logo')), findsNothing);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });
  });
}

void _noop() {}
