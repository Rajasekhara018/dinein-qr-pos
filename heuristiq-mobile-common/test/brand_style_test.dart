import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

const _brand = HqBrandStyle(
  primaryGradient: [Color(0xFFA31621), Color(0xFF7F111A)],
  primaryShadow: true,
  controlRadius: 16,
  dialogRadius: 28,
);

Widget _host(Widget child, {HqBrandStyle? brand}) => MaterialApp(
      theme: HqTheme.light().copyWith(extensions: [HqTokens.light, if (brand != null) brand]),
      home: Scaffold(body: Center(child: child)),
    );

BoxDecoration? _gradientDecoration(WidgetTester tester) {
  final boxes = tester.widgetList<DecoratedBox>(find.descendant(of: find.byType(HqButton), matching: find.byType(DecoratedBox)));
  for (final box in boxes) {
    final decoration = box.decoration;
    if (decoration is BoxDecoration && decoration.gradient != null) return decoration;
  }
  return null;
}

void main() {
  group('HqBrandStyle', () {
    testWidgets('without a brand style the primary button stays flat', (tester) async {
      await tester.pumpWidget(_host(HqButton(label: 'Save', onPressed: () {})));
      expect(_gradientDecoration(tester), isNull);
    });

    testWidgets('the filled primary button wears the brand gradient, soft shadow and radius', (tester) async {
      var taps = 0;
      await tester.pumpWidget(_host(HqButton(label: 'Send interest', onPressed: () => taps++), brand: _brand));
      final decoration = _gradientDecoration(tester)!;
      expect((decoration.gradient! as LinearGradient).colors, _brand.primaryGradient);
      expect(decoration.boxShadow, hasLength(1));
      expect(decoration.borderRadius, BorderRadius.circular(16));
      await tester.tap(find.text('Send interest'));
      expect(taps, 1);
    });

    testWidgets('a disabled branded button keeps the gradient without the shadow, and other buttons stay plain', (tester) async {
      await tester.pumpWidget(_host(const Column(children: [
        HqButton(label: 'Wait', onPressed: null),
        HqButton(label: 'Later', appearance: HqButtonAppearance.outline, onPressed: null),
        HqButton(label: 'Remove', color: HqButtonColor.warn, onPressed: null),
      ]), brand: _brand));
      final decorations = tester
          .widgetList<DecoratedBox>(find.byType(DecoratedBox))
          .map((box) => box.decoration)
          .whereType<BoxDecoration>()
          .where((d) => d.gradient != null)
          .toList();
      expect(decorations, hasLength(1), reason: 'only the filled primary button is branded');
      expect(decorations.single.boxShadow, isNull);
    });

    test('lerps between two styles and falls back for a missing gradient', () {
      const other = HqBrandStyle(primaryGradient: [Color(0xFF000000), Color(0xFF000000)], controlRadius: 8);
      final half = _brand.lerp(other, 0.5);
      expect(half.controlRadius, 12);
      expect(half.primaryGradient, hasLength(2));
      expect(_brand.lerp(const HqBrandStyle(), 0.2).primaryGradient, _brand.primaryGradient);
      expect(_brand.copyWith(primaryShadow: false).primaryShadow, isFalse);
      expect(_brand.lerp(null, 0.7), same(_brand));
    });
  });

  group('HqDialog.confirm with an icon', () {
    testWidgets('shows the featured layout and answers the choice', (tester) async {
      bool? answer;
      await tester.pumpWidget(_host(
        Builder(
          builder: (context) => HqButton(
            label: 'Open',
            onPressed: () async => answer = await HqDialog.confirm(
              context,
              title: 'Withdraw interest?',
              message: 'They will no longer see it.',
              confirmLabel: 'Withdraw',
              cancelLabel: 'Keep',
              destructive: true,
              icon: Icons.undo,
            ),
          ),
        ),
        brand: _brand,
      ));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.undo), findsOneWidget);
      expect(find.text('Withdraw interest?'), findsOneWidget);
      expect(find.text('They will no longer see it.'), findsOneWidget);
      final dialog = tester.widget<Dialog>(find.byType(Dialog));
      expect((dialog.shape! as RoundedRectangleBorder).borderRadius, BorderRadius.circular(28));

      await tester.tap(find.text('Withdraw'));
      await tester.pumpAndSettle();
      expect(answer, isTrue);
    });

    testWidgets('cancel answers false', (tester) async {
      bool? answer;
      await tester.pumpWidget(_host(Builder(
        builder: (context) => HqButton(
          label: 'Open',
          onPressed: () async => answer = await HqDialog.confirm(context, title: 'Sign out?', confirmLabel: 'Sign out', cancelLabel: 'Stay', icon: Icons.logout),
        ),
      )));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Stay'));
      await tester.pumpAndSettle();
      expect(answer, isFalse);
    });
  });

  group('HqDialog.confirm button layout', () {
    Future<void> open(WidgetTester tester, String confirm) async {
      await tester.binding.setSurfaceSize(const Size(400, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(_host(Builder(
        builder: (context) => HqButton(
          label: 'Open',
          onPressed: () => HqDialog.confirm(context, title: 'Delete your account?', confirmLabel: confirm, cancelLabel: 'Cancel', destructive: true, icon: Icons.delete),
        ),
      )));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
    }

    testWidgets('short labels sit side by side', (tester) async {
      await open(tester, 'Delete');
      expect(tester.getTopLeft(find.text('Cancel')).dy, tester.getTopLeft(find.text('Delete')).dy);
    });

    testWidgets('a label too long for half the width stacks the buttons, main action first, never cut', (tester) async {
      await open(tester, 'Delete my account forever');
      final confirm = find.text('Delete my account forever');
      expect(tester.getTopLeft(confirm).dy, lessThan(tester.getTopLeft(find.text('Cancel')).dy));
      final text = tester.widget<Text>(confirm);
      final painter = TextPainter(text: TextSpan(text: text.data, style: DefaultTextStyle.of(tester.element(confirm)).style), textDirection: TextDirection.ltr)..layout();
      expect(tester.getSize(confirm).width, greaterThanOrEqualTo(painter.width - 1));
    });
  });
}
