import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

const _steps = [
  HqStep(id: 'kyc', title: 'KYC'),
  HqStep(id: 'risk', title: 'Risk', subtitle: 'Optional', optional: true, icon: Icons.shield),
  HqStep(id: 'bank', title: 'Bank'),
  HqStep(id: 'done', title: 'Done', state: HqStepState.disabled),
];

void main() {
  group('HqStepper', () {
    test('derives states from the current index unless forced', () {
      final stepper = HqStepper(steps: _steps, currentIndex: 1, texts: HqStepperTexts.english());
      expect(stepper.stateOf(0), HqStepState.completed);
      expect(stepper.stateOf(1), HqStepState.current);
      expect(stepper.stateOf(2), HqStepState.upcoming);
      expect(stepper.stateOf(3), HqStepState.disabled);
    });

    test('english texts name every state', () {
      final texts = HqStepperTexts.english();
      expect(texts.stepOf(2, 4), 'Step 2 of 4');
      expect({for (final s in HqStepState.values) texts.of(s)}.length, HqStepState.values.length);
    });

    for (final axis in Axis.values) {
      testWidgets('$axis: completed and current steps are tappable, upcoming are not', (tester) async {
        final tapped = <int>[];
        await tester.pumpWidget(hqTestApp(SingleChildScrollView(
          child: HqStepper(
            axis: axis,
            steps: [..._steps.take(3), const HqStep(id: 'err', title: 'Err', state: HqStepState.error), _steps[3]],
            currentIndex: 1,
            texts: HqStepperTexts.english(),
            onStepTapped: tapped.add,
          ),
        )));
        expect(find.byIcon(Icons.check), findsOneWidget);
        expect(find.byIcon(Icons.lock_outline), findsOneWidget);
        expect(find.byIcon(Icons.priority_high), findsOneWidget);
        await tester.tap(find.byKey(const ValueKey('hq-step-kyc')));
        await tester.tap(find.byKey(const ValueKey('hq-step-risk')));
        await tester.tap(find.byKey(const ValueKey('hq-step-bank')));
        await tester.tap(find.byKey(const ValueKey('hq-step-err')));
        expect(tapped, [0, 1, 3]);
        expect(find.bySemanticsLabel('Step 1 of 5: KYC, completed'), findsOneWidget);
      });
    }

    testWidgets('vertical layout shows only the current step content', (tester) async {
      await tester.pumpWidget(hqTestApp(SingleChildScrollView(
        child: HqStepper(
          axis: Axis.vertical,
          currentIndex: 0,
          texts: HqStepperTexts.english(),
          steps: const [HqStep(id: 'a', title: 'A', content: Text('content A')), HqStep(id: 'b', title: 'B', content: Text('content B'))],
        ),
      )));
      expect(find.text('content A'), findsOneWidget);
      expect(find.text('content B'), findsNothing);
    });
  });

  group('HqWizard', () {
    Widget wizard({required List<String?> validations, required List<String> log, String? submitError, bool draft = true}) {
      var call = 0;
      return hqTestApp(HqWizard(
        stepperTexts: HqStepperTexts.english(),
        texts: const HqWizardTexts(back: 'Back', next: 'Next', submit: 'Submit', saveDraft: 'Save draft'),
        onSubmit: () {
          log.add('submit');
          return submitError;
        },
        onSaveDraft: draft ? (i) => log.add('draft $i') : null,
        onStepChanged: (i) => log.add('step $i'),
        steps: [
          HqWizardStep(step: const HqStep(id: 'one', title: 'One'), builder: (_) => const Text('screen one'), validate: () => validations[call++]),
          HqWizardStep(step: const HqStep(id: 'two', title: 'Two'), builder: (_) => const Text('screen two')),
        ],
      ));
    }

    testWidgets('a validation error keeps the user on the step and shows it', (tester) async {
      final log = <String>[];
      await tester.pumpWidget(wizard(validations: ['PAN is required', null], log: log));
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      expect(find.text('PAN is required'), findsOneWidget);
      expect(find.text('screen one'), findsOneWidget);
      expect(find.byIcon(Icons.priority_high), findsOneWidget);

      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      expect(find.text('screen two'), findsOneWidget);
      expect(find.text('PAN is required'), findsNothing);
      expect(find.text('Submit'), findsOneWidget);
      expect(log, ['step 1']);
    });

    testWidgets('back, jump to a completed step, draft and submit', (tester) async {
      final log = <String>[];
      await tester.pumpWidget(wizard(validations: [null, null], log: log, submitError: 'Server said no'));
      expect(find.text('Back'), findsNothing);
      await tester.tap(find.text('Save draft'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit'));
      await tester.pumpAndSettle();
      expect(find.text('Server said no'), findsOneWidget);
      await tester.tap(find.text('Back'));
      await tester.pumpAndSettle();
      expect(find.text('screen one'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('hq-step-two')));
      await tester.pumpAndSettle();
      expect(find.text('screen two'), findsOneWidget);
      expect(log, ['draft 0', 'step 1', 'submit', 'step 0', 'step 1']);
    });

    testWidgets('a successful submit clears the error and stays on the last step', (tester) async {
      final log = <String>[];
      await tester.pumpWidget(wizard(validations: [null, null], log: log, draft: false));
      expect(find.text('Save draft'), findsNothing);
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Submit'));
      await tester.pumpAndSettle();
      expect(log, ['step 1', 'submit']);
      expect(find.text('screen two'), findsOneWidget);
    });
  });

  group('shell', () {
    const items = [
      HqNavItem(id: 'home', label: 'Home', icon: Icons.home),
      HqNavItem(id: 'portfolio', label: 'Portfolio', icon: Icons.pie_chart, privileges: ['INVEST_PORTFOLIO_LIST'], badgeCount: 3, badgeLabel: '3 new'),
      HqNavItem(id: 'admin', label: 'Admin', icon: Icons.settings, selectedIcon: Icons.settings_applications, privileges: ['ADMIN_X']),
    ];

    test('visible items follow privileges; no access scope shows all', () {
      final access = HqPrivilegeAccess()..setPermissions(['INVEST_PORTFOLIO_LIST']);
      expect(hqVisibleNavItems(items, access).map((i) => i.id), ['home', 'portfolio']);
      expect(hqVisibleNavItems(items, null).length, 3);
    });

    testWidgets('bottom bar on phones, hides items without privilege and reports taps', (tester) async {
      tester.view.physicalSize = const Size(400, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final chosen = <String>[];
      final access = HqPrivilegeAccess()..setPermissions(['INVEST_PORTFOLIO_LIST']);
      await tester.pumpWidget(HqPrivilegeScope(
        access: access,
        child: MaterialApp(theme: HqTheme.light(), home: HqAppShell(items: items, currentId: 'unknown', onSelected: chosen.add, body: const Text('body'))),
      ));
      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.text('Admin'), findsNothing);
      expect(find.text('3'), findsWidgets);
      await tester.tap(find.text('Portfolio'));
      expect(chosen, ['portfolio']);
    });

    testWidgets('no bar with fewer than two visible items', (tester) async {
      tester.view.physicalSize = const Size(400, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(HqPrivilegeScope(
        access: HqPrivilegeAccess()..setPermissions(const []),
        child: MaterialApp(theme: HqTheme.light(), home: HqAppShell(items: items, currentId: 'home', onSelected: (_) {}, body: const Text('body'))),
      ));
      expect(find.byType(NavigationBar), findsNothing);
    });

    testWidgets('side rail from 600 px', (tester) async {
      tester.view.physicalSize = const Size(1200, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final chosen = <String>[];
      await tester.pumpWidget(MaterialApp(
        theme: HqTheme.light(),
        home: HqAppShell(items: items, currentId: 'admin', onSelected: chosen.add, body: const Text('body'), appBar: AppBar(title: const Text('t'))),
      ));
      expect(find.byType(NavigationRail), findsOneWidget);
      expect(find.byType(NavigationBar), findsNothing);
      await tester.tap(find.text('Home'));
      expect(chosen, ['home']);
    });

    testWidgets('infinite list asks for more, shows loading and retry', (tester) async {
      var loads = 0;
      Widget list({bool loading = false, bool failed = false, bool hasMore = true}) => hqTestApp(SizedBox(
            height: 400,
            child: HqInfiniteList<int>(
              items: const [1, 2, 3],
              itemBuilder: (_, item, __) => Text('item $item'),
              hasMore: hasMore,
              onLoadMore: () => loads++,
              isLoadingMore: loading,
              loadFailed: failed,
              retryLabel: 'Retry',
              loadingLabel: 'Loading',
              onRefresh: () async {},
            ),
          ));
      await tester.pumpWidget(list());
      await tester.pump();
      expect(loads, 1);
      expect(find.byType(RefreshIndicator), findsOneWidget);

      await tester.pumpWidget(list(loading: true));
      await tester.pump();
      expect(loads, 1);
      expect(find.bySemanticsLabel('Loading'), findsOneWidget);

      await tester.pumpWidget(list(failed: true));
      await tester.pump();
      expect(loads, 1);
      await tester.tap(find.text('Retry'));
      expect(loads, 2);

      await tester.pumpWidget(list(hasMore: false));
      await tester.pump();
      expect(loads, 2);
    });
  });
}
