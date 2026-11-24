import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

void main() {
  group('HqButton', () {
    testWidgets('calls onPressed', (tester) async {
      var taps = 0;
      await tester.pumpWidget(hqTestApp(HqButton(label: 'Save', onPressed: () => taps++)));
      await tester.tap(find.text('Save'));
      expect(taps, 1);
    });

    testWidgets('loading shows a spinner and blocks taps', (tester) async {
      var taps = 0;
      await tester.pumpWidget(hqTestApp(HqButton(label: 'Save', loading: true, onPressed: () => taps++)));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Save'), findsNothing);
      await tester.tap(find.byType(FilledButton), warnIfMissed: false);
      expect(taps, 0);
    });

    testWidgets('a null handler disables it', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqButton(label: 'Save')));
      expect(tester.widget<FilledButton>(find.byType(FilledButton)).onPressed, isNull);
    });

    testWidgets('appearances map to the Material buttons', (tester) async {
      await tester.pumpWidget(hqTestApp(Column(children: [
        HqButton(label: 'a', appearance: HqButtonAppearance.outline, onPressed: () {}),
        HqButton(label: 'b', appearance: HqButtonAppearance.text, onPressed: () {}),
      ])));
      expect(find.byType(OutlinedButton), findsOneWidget);
      expect(find.byType(TextButton), findsOneWidget);
    });

    testWidgets('icon only button has a square size and block fills the width', (tester) async {
      await tester.pumpWidget(hqTestApp(Column(children: [
        HqButton(icon: Icons.edit, semanticLabel: 'Edit', onPressed: () {}),
        HqButton(label: 'Pay', block: true, onPressed: () {}),
      ])));
      // Material pads the tap target to 48; the button itself is square, so width equals height.
      final icon = tester.getSize(find.byType(FilledButton).first);
      expect(icon.width, icon.height);
      expect(icon.width, greaterThanOrEqualTo(HqSizes.controlHeight));
      expect(tester.getSize(find.byType(FilledButton).last).width, greaterThan(300));
    });

    testWidgets('warn colour uses the token', (tester) async {
      await tester.pumpWidget(hqTestApp(HqButton(label: 'Delete', color: HqButtonColor.warn, onPressed: () {})));
      final style = tester.widget<FilledButton>(find.byType(FilledButton)).style!;
      expect(style.backgroundColor!.resolve(<WidgetState>{}), HqTokens.light.warn);
    });
  });

  group('HqStatusBadge', () {
    testWidgets('shows the label in the tone colours', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqStatusBadge(label: 'Approved', tone: HqStatusTone.success)));
      expect(find.text('Approved'), findsOneWidget);
      final box = tester.widget<DecoratedBox>(find.byType(DecoratedBox).first).decoration as BoxDecoration;
      expect(box.color, HqTokens.light.toneSuccess.background);
    });

    testWidgets('renders an icon', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqStatusBadge(label: 'x', icon: Icons.check, size: HqStatusBadgeSize.sm)));
      expect(find.byIcon(Icons.check), findsOneWidget);
    });
  });

  group('HqPageHeader', () {
    testWidgets('shows title, subtitle, kicker, updated time and actions', (tester) async {
      await tester.pumpWidget(hqTestApp(HqPageHeader(
        title: 'Portfolio',
        subtitle: 'Your holdings',
        kicker: 'Invest',
        lastUpdated: DateTime(2026, 11, 22, 9, 5),
        lastUpdatedLabel: 'Updated',
        actions: const [Icon(Icons.refresh)],
      )));
      expect(find.text('Portfolio'), findsOneWidget);
      expect(find.text('Your holdings'), findsOneWidget);
      expect(find.text('Invest'), findsOneWidget);
      expect(find.text('Updated 2026-11-22 09:05'), findsOneWidget);
      expect(find.byIcon(Icons.refresh), findsOneWidget);
    });

    testWidgets('back button and breadcrumbs call their handlers', (tester) async {
      var back = 0;
      var crumb = 0;
      await tester.pumpWidget(hqTestApp(HqPageHeader(
        title: 'T',
        showBack: true,
        backLabel: 'Back',
        onBack: () => back++,
        breadcrumbs: [HqBreadcrumb(label: 'Home', onTap: () => crumb++), const HqBreadcrumb(label: 'Here')],
      )));
      await tester.tap(find.text('Back'));
      await tester.tap(find.text('Home'));
      expect(back, 1);
      expect(crumb, 1);
    });

    testWidgets('loading shows the progress bar', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqPageHeader(title: 'T', loading: true)));
      expect(find.byType(LinearProgressIndicator), findsOneWidget);
    });
  });

  group('HqStatCards', () {
    testWidgets('renders every card and an empty list renders nothing', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqStatCards(stats: [
        HqStatCardItem(label: 'Invested', value: '1,00,000', icon: Icons.savings, tone: HqStatTone.green),
        HqStatCardItem(label: 'Holdings', value: 3, icon: Icons.apartment),
      ], fill: true)));
      expect(find.text('Invested'), findsOneWidget);
      expect(find.text('1,00,000'), findsOneWidget);
      expect(find.text('3'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(const HqStatCards(stats: [])));
      expect(find.byType(Wrap), findsNothing);
    });
  });

  group('HqField', () {
    testWidgets('label, required marker, error and onChanged', (tester) async {
      String? changed;
      await tester.pumpWidget(hqTestApp(HqField(label: 'PAN', required: true, error: 'Invalid', onChanged: (v) => changed = v)));
      expect(find.textContaining('PAN'), findsWidgets);
      expect(find.text('Invalid'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'ABCDE1234F');
      expect(changed, 'ABCDE1234F');
    });

    testWidgets('password field is obscured and can be toggled', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqField(label: 'Password', type: HqFieldType.password, passwordShowLabel: 'Show')));
      expect(tester.widget<TextField>(find.byType(TextField)).obscureText, isTrue);
      await tester.tap(find.byTooltip('Show'));
      await tester.pump();
      expect(tester.widget<TextField>(find.byType(TextField)).obscureText, isFalse);
    });

    testWidgets('follows value changes', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqField(value: 'one')));
      expect(find.text('one'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(const HqField(value: 'two')));
      expect(find.text('two'), findsOneWidget);
    });
  });

  group('HqCheckbox', () {
    testWidgets('tapping the label toggles', (tester) async {
      bool? value;
      await tester.pumpWidget(hqTestApp(HqCheckbox(checked: false, label: 'I agree', onChanged: (v) => value = v)));
      await tester.tap(find.text('I agree'));
      expect(value, isTrue);
    });

    testWidgets('disabled does not call back and shows the error', (tester) async {
      var calls = 0;
      await tester.pumpWidget(hqTestApp(HqCheckbox(checked: false, label: 'x', enabled: false, error: 'Required', onChanged: (_) => calls++)));
      await tester.tap(find.text('x'), warnIfMissed: false);
      expect(calls, 0);
      expect(find.text('Required'), findsOneWidget);
    });
  });

  group('HqRadioGroup', () {
    testWidgets('selects an option and skips disabled ones', (tester) async {
      String? picked;
      await tester.pumpWidget(hqTestApp(HqRadioGroup<String>(
        label: 'Type',
        value: 'a',
        options: const [
          HqRadioOption(value: 'a', label: 'Alpha'),
          HqRadioOption(value: 'b', label: 'Beta'),
          HqRadioOption(value: 'c', label: 'Gamma', disabled: true),
        ],
        onChanged: (v) => picked = v,
      )));
      expect(find.byIcon(Icons.radio_button_checked), findsOneWidget);
      await tester.tap(find.text('Gamma'), warnIfMissed: false);
      expect(picked, isNull);
      await tester.tap(find.text('Beta'));
      expect(picked, 'b');
    });
  });

  group('HqPhoneNumberInput', () {
    test('validates India strictly and other countries by length', () {
      final india = HqCountryData.byIso('IN')!;
      expect(hqIsValidPhone('9876543210', india), isTrue);
      expect(hqIsValidPhone('5876543210', india), isFalse);
      expect(hqIsValidPhone('98765', india), isFalse);
      final uae = hqPhoneCountries.firstWhere((c) => c.iso == 'AE');
      expect(hqIsValidPhone('501234567', uae), isTrue);
      expect(hqBuildPhoneValue(india, '09876543210').e164, '+919876543210');
      expect(hqBuildPhoneValue(india, '12').e164, isNull);
    });

    testWidgets('reports e164 for a valid number and an error for an invalid one', (tester) async {
      HqPhoneValue? last;
      await tester.pumpWidget(hqTestApp(HqPhoneNumberInput(label: 'Mobile', invalidError: 'Invalid number', onChanged: (v) => last = v)));
      await tester.enterText(find.byType(TextField), '12345');
      await tester.pump();
      expect(find.text('Invalid number'), findsOneWidget);
      await tester.enterText(find.byType(TextField), '9876543210');
      await tester.pump();
      expect(find.text('Invalid number'), findsNothing);
      expect(last?.e164, '+919876543210');
      expect(last?.isValid, isTrue);
    });
  });

  group('HqSearchableSelect', () {
    const options = [
      HqOption(value: 1, label: 'Mumbai'),
      HqOption(value: 2, label: 'Chennai'),
      HqOption(value: 3, label: 'Delhi', subtitle: 'Capital'),
    ];

    test('filters by label and subtitle, ignoring case', () {
      expect(hqFilterOptions(options, 'mum').map((o) => o.value), [1]);
      expect(hqFilterOptions(options, 'CAPITAL').map((o) => o.value), [3]);
      expect(hqFilterOptions(options, '  ').length, 3);
    });

    testWidgets('opens a sheet, searches and picks', (tester) async {
      int? picked;
      await tester.pumpWidget(hqTestApp(HqSearchableSelect<int>(
        options: options,
        label: 'City',
        placeholder: 'Choose',
        searchHint: 'Search',
        emptyText: 'No match',
        onChanged: (v) => picked = v,
      )));
      expect(find.text('Choose'), findsOneWidget);
      await tester.tap(find.text('Choose'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).last, 'chen');
      await tester.pumpAndSettle();
      expect(find.text('Mumbai'), findsNothing);
      await tester.tap(find.text('Chennai'));
      await tester.pumpAndSettle();
      expect(picked, 2);
    });

    testWidgets('shows the empty text when nothing matches', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqSearchableSelect<int>(options: options, placeholder: 'Choose', emptyText: 'No match')));
      await tester.tap(find.text('Choose'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).last, 'zzz');
      await tester.pumpAndSettle();
      expect(find.text('No match'), findsOneWidget);
    });

    testWidgets('multiple select returns every ticked value on done', (tester) async {
      List<int>? picked;
      await tester.pumpWidget(hqTestApp(HqSearchableSelect<int>(
        options: options,
        multiple: true,
        placeholder: 'Choose',
        doneLabel: 'Done',
        onValuesChanged: (v) => picked = v,
      )));
      await tester.tap(find.text('Choose'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Mumbai'));
      await tester.tap(find.text('Delhi'));
      await tester.pump();
      await tester.tap(find.text('Done'));
      await tester.pumpAndSettle();
      expect(picked, [1, 3]);
    });

    testWidgets('a failed load retries instead of opening', (tester) async {
      var retries = 0;
      await tester.pumpWidget(hqTestApp(HqSearchableSelect<int>(
        options: const [],
        loadFailed: true,
        loadFailedText: 'Could not load',
        onRetry: () => retries++,
      )));
      await tester.tap(find.text('Could not load'));
      expect(retries, 1);
    });
  });

  group('forms', () {
    test('columnsFor falls back to fewer columns on narrow widths', () {
      expect(HqFormGrid.columnsFor(width: 360, cols: 3, minColumnWidth: 280, gap: 16), 1);
      expect(HqFormGrid.columnsFor(width: 620, cols: 3, minColumnWidth: 280, gap: 16), 2);
      expect(HqFormGrid.columnsFor(width: 1200, cols: 3, minColumnWidth: 280, gap: 16), 3);
      expect(HqFormGrid.columnsFor(width: double.infinity, cols: 3, minColumnWidth: 280, gap: 16), 1);
    });

    testWidgets('grid lays children out and a spanning item is wider', (tester) async {
      tester.view.physicalSize = const Size(1000, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(hqTestApp(const HqFormGrid(cols: 2, children: [
        SizedBox(key: Key('a'), height: 10),
        SizedBox(key: Key('b'), height: 10),
        HqFormGridItem(child: SizedBox(key: Key('c'), height: 10)),
      ])));
      final a = tester.getSize(find.byKey(const Key('a'))).width;
      final c = tester.getSize(find.byKey(const Key('c'))).width;
      expect(c, greaterThan(a));
    });

    testWidgets('actions render children and HqForm validates', (tester) async {
      final key = GlobalKey<FormState>();
      await tester.pumpWidget(hqTestApp(HqForm(
        formKey: key,
        child: Column(children: [
          TextFormField(validator: (v) => (v ?? '').isEmpty ? 'required' : null),
          const HqFormActions(children: [Text('Save'), Text('Cancel')]),
        ]),
      )));
      expect(find.text('Save'), findsOneWidget);
      expect(key.currentState!.validate(), isFalse);
      await tester.pump();
      expect(find.text('required'), findsOneWidget);
    });

    testWidgets('HqFormField shows the hint, replaced by the error', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqFormField(label: 'Name', hint: 'As on PAN', child: SizedBox())));
      expect(find.text('As on PAN'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(const HqFormField(label: 'Name', hint: 'As on PAN', error: 'Bad', child: SizedBox())));
      expect(find.text('As on PAN'), findsNothing);
      expect(find.text('Bad'), findsOneWidget);
    });
  });

  group('HqFilterBar', () {
    testWidgets('collapsible bar hides its children until opened', (tester) async {
      bool? expanded;
      await tester.pumpWidget(hqTestApp(HqFilterBar(
        collapsible: true,
        toggleText: 'Filters',
        onExpandedChanged: (v) => expanded = v,
        children: const [Text('Status filter')],
      )));
      expect(find.text('Status filter'), findsNothing);
      await tester.tap(find.text('Filters'));
      await tester.pumpAndSettle();
      expect(find.text('Status filter'), findsOneWidget);
      expect(expanded, isTrue);
    });

    testWidgets('plain bar shows children', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqFilterBar(children: [Text('x')])));
      expect(find.text('x'), findsOneWidget);
    });
  });

  group('HqTabs', () {
    for (final appearance in HqTabsAppearance.values) {
      testWidgets('$appearance selects a tab and skips disabled', (tester) async {
        String? value = 'a';
        await tester.pumpWidget(hqTestApp(StatefulBuilder(
          builder: (context, setState) => HqTabs<String>(
            appearance: appearance,
            value: value,
            onChanged: (v) => setState(() => value = v),
            tabs: const [
              HqTab(value: 'a', label: 'One'),
              HqTab(value: 'b', label: 'Two', badge: 4),
              HqTab(value: 'c', label: 'Three', disabled: true),
            ],
          ),
        )));
        await tester.tap(find.text('Three'), warnIfMissed: false);
        await tester.pump();
        expect(value, 'a');
        await tester.tap(find.text('Two'));
        await tester.pump();
        expect(value, 'b');
        expect(find.text('4'), findsOneWidget);
      });
    }

    testWidgets('a block strip slides one thumb behind equal-width tabs', (tester) async {
      String? value = 'a';
      await tester.pumpWidget(hqTestApp(SizedBox(
        width: 300,
        child: StatefulBuilder(
          builder: (context, setState) => HqTabs<String>(
            appearance: HqTabsAppearance.pills,
            block: true,
            value: value,
            onChanged: (v) => setState(() => value = v),
            tabs: const [HqTab(value: 'a', label: 'Received'), HqTab(value: 'b', label: 'Sent')],
          ),
        ),
      )));
      final thumb = find.byType(AnimatedPositioned);
      expect(thumb, findsOneWidget);
      final before = tester.getTopLeft(thumb).dx;
      await tester.tap(find.text('Sent'));
      await tester.pumpAndSettle();
      expect(value, 'b');
      expect(tester.getTopLeft(thumb).dx, greaterThan(before));
    });
  });

  group('HqModal and HqDrawer', () {
    for (final drawer in [false, true]) {
      testWidgets('${drawer ? 'drawer' : 'modal'} shows content and returns its result', (tester) async {
        String? result = 'unset';
        await tester.pumpWidget(hqTestApp(Builder(
          builder: (context) => HqButton(
            label: 'Open',
            onPressed: () async {
              Widget content(BuildContext c) => HqButton(label: 'Pick', onPressed: () => Navigator.of(c).pop('picked'));
              result = drawer
                  ? await HqDrawer.show<String>(context, title: 'Details', subtitle: 'Sub', closeLabel: 'Close', builder: content)
                  : await HqModal.show<String>(context, title: 'Details', subtitle: 'Sub', closeLabel: 'Close', builder: content);
            },
          ),
        )));
        await tester.tap(find.text('Open'));
        await tester.pumpAndSettle();
        expect(find.text('Details'), findsOneWidget);
        expect(find.text('Sub'), findsOneWidget);
        await tester.tap(find.text('Pick'));
        await tester.pumpAndSettle();
        expect(result, 'picked');
      });
    }

    testWidgets('the close button dismisses with null', (tester) async {
      String? result = 'unset';
      await tester.pumpWidget(hqTestApp(Builder(
        builder: (context) => HqButton(
          label: 'Open',
          onPressed: () async => result = await HqModal.show<String>(context, title: 'T', closeLabel: 'Close', builder: (_) => const Text('body')),
        ),
      )));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Close'));
      await tester.pumpAndSettle();
      expect(result, isNull);
    });
  });
}
