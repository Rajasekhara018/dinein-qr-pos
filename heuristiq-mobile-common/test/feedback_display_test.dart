import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

void main() {
  group('HqAlert and HqBanner', () {
    testWidgets('shows title, message, tone icon and an action', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqAlert(title: 'Heads up', message: 'KYC is pending', tone: HqAlertTone.warning, action: Text('Fix now'))));
      expect(find.text('Heads up'), findsOneWidget);
      expect(find.text('KYC is pending'), findsOneWidget);
      expect(find.text('Fix now'), findsOneWidget);
      expect(find.byIcon(Icons.warning_amber_outlined), findsOneWidget);
    });

    testWidgets('every tone has its own icon and the decision-pending tone has a hourglass', (tester) async {
      for (final tone in HqAlertTone.values) {
        await tester.pumpWidget(hqTestApp(HqAlert(message: 'm', tone: tone)));
        expect(find.byIcon(HqAlert.defaultIcon(tone)), findsOneWidget, reason: '$tone');
      }
      expect(HqAlert.defaultIcon(HqAlertTone.decisionPending), Icons.hourglass_empty);
    });

    testWidgets('tone colours come from the tokens', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqAlert(message: 'm', tone: HqAlertTone.danger)));
      final box = tester.widget<DecoratedBox>(find.byType(DecoratedBox).first).decoration as BoxDecoration;
      expect(box.color, HqTokens.light.toneDanger.background);
    });

    testWidgets('dismiss calls back', (tester) async {
      var dismissed = 0;
      await tester.pumpWidget(hqTestApp(HqAlert(message: 'm', dismissible: true, dismissLabel: 'Dismiss', onDismissed: () => dismissed++)));
      await tester.tap(find.byIcon(Icons.close));
      expect(dismissed, 1);
    });

    testWidgets('banner is dismissible by default', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqBanner(message: 'New terms', tone: HqAlertTone.info)));
      expect(find.byIcon(Icons.close), findsOneWidget);
    });
  });

  group('states', () {
    testWidgets('empty state shows title, message and action', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqEmptyState(title: 'No holdings', message: 'Invest to see them here', action: Text('Browse'))));
      expect(find.text('No holdings'), findsOneWidget);
      expect(find.text('Invest to see them here'), findsOneWidget);
      expect(find.text('Browse'), findsOneWidget);
    });

    testWidgets('error state retries', (tester) async {
      var retries = 0;
      await tester.pumpWidget(hqTestApp(HqErrorState(title: 'Failed', message: 'Server down', retryLabel: 'Retry', onRetry: () => retries++)));
      await tester.tap(find.text('Retry'));
      expect(retries, 1);
      await tester.pumpWidget(hqTestApp(const HqErrorState(title: 'Failed')));
      expect(find.byType(HqButton), findsNothing);
    });

    testWidgets('view state switches between the four views', (tester) async {
      Widget view(HqViewStatus s) => HqViewState(status: s, content: const Text('content'), empty: const Text('empty'), error: const Text('error'));
      await tester.pumpWidget(hqTestApp(view(HqViewStatus.content)));
      expect(find.text('content'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(view(HqViewStatus.empty)));
      expect(find.text('empty'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(view(HqViewStatus.error)));
      expect(find.text('error'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(view(HqViewStatus.loading)));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });
  });

  group('HqSkeleton', () {
    for (final variant in HqSkeletonVariant.values) {
      testWidgets('$variant renders and can be disposed', (tester) async {
        await tester.pumpWidget(hqTestApp(SizedBox(width: 300, child: HqSkeleton(variant: variant, label: 'Loading'))));
        await tester.pump(const Duration(milliseconds: 300));
        expect(find.byType(HqSkeleton), findsOneWidget);
        await tester.pumpWidget(hqTestApp(const SizedBox()));
      });
    }

    testWidgets('list variant draws the asked number of rows', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqSkeleton(variant: HqSkeletonVariant.list, rows: 5)));
      expect(find.byType(CircleAvatar), findsNothing);
      expect(find.byType(Row), findsNWidgets(5));
    });

    testWidgets('reduced motion stops the shimmer', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: HqTheme.light(),
        home: const MediaQuery(data: MediaQueryData(disableAnimations: true), child: Scaffold(body: HqSkeleton())),
      ));
      // No repeating animation: pumpAndSettle would time out if the controller still ran.
      await tester.pumpAndSettle();
    });
  });

  group('progress', () {
    testWidgets('indeterminate bar and ring', (tester) async {
      await tester.pumpWidget(hqTestApp(const Column(children: [HqProgressBar(fraction: 0, indeterminate: true, label: 'Syncing'), HqCircularProgress()])));
      expect(find.byType(LinearProgressIndicator), findsOneWidget);
      expect(tester.widget<CircularProgressIndicator>(find.byType(CircularProgressIndicator)).value, isNull);
      expect(find.text('Syncing'), findsOneWidget);
    });

    testWidgets('determinate ring shows its value and the success colour when full', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqCircularProgress(fraction: 1.2, valueText: '100%', label: 'KYC')));
      final indicator = tester.widget<CircularProgressIndicator>(find.byType(CircularProgressIndicator));
      expect(indicator.value, 1);
      expect(indicator.color, HqTokens.light.success);
      expect(find.text('100%'), findsOneWidget);
    });

    testWidgets('spinner sizes', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqSpinner(size: HqSpinnerSize.lg, label: 'Loading')));
      expect(tester.getSize(find.byType(CircularProgressIndicator)), const Size(40, 40));
    });
  });

  group('HqTooltip and confirm', () {
    testWidgets('tooltip wraps with a message and is skipped when blank or disabled', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqTooltip(message: 'Hint', child: Icon(Icons.info))));
      expect(find.byType(Tooltip), findsOneWidget);
      await tester.pumpWidget(hqTestApp(const HqTooltip(message: '  ', child: Icon(Icons.info))));
      expect(find.byType(Tooltip), findsNothing);
      await tester.pumpWidget(hqTestApp(const HqTooltip(message: 'Hint', disabled: true, child: Icon(Icons.info))));
      expect(find.byType(Tooltip), findsNothing);
    });

    testWidgets('inline confirm requires a reason when asked and passes it on', (tester) async {
      String? reason;
      var cancelled = 0;
      await tester.pumpWidget(hqTestApp(HqInlineConfirm(
        title: 'Cancel subscription?',
        confirmLabel: 'Confirm',
        cancelLabel: 'Keep',
        withReason: true,
        reasonLabel: 'Reason',
        reasonRequiredMessage: 'Enter a reason',
        onConfirmed: (r) => reason = r,
        onCancelled: () => cancelled++,
      )));
      await tester.tap(find.text('Confirm'));
      await tester.pump();
      expect(find.text('Enter a reason'), findsOneWidget);
      expect(reason, isNull);
      await tester.enterText(find.byType(TextField), '  changed my mind ');
      await tester.tap(find.text('Confirm'));
      expect(reason, 'changed my mind');
      await tester.tap(find.text('Keep'));
      expect(cancelled, 1);
    });

    testWidgets('inline confirm without reason confirms with an empty string; busy disables cancel', (tester) async {
      String? reason;
      await tester.pumpWidget(hqTestApp(HqInlineConfirm(title: 'Sure?', confirmLabel: 'Yes', cancelLabel: 'No', onConfirmed: (r) => reason = r, onCancelled: () {})));
      await tester.tap(find.text('Yes'));
      expect(reason, '');
      await tester.pumpWidget(hqTestApp(HqInlineConfirm(title: 'Sure?', confirmLabel: 'Yes', cancelLabel: 'No', busy: true, onConfirmed: (_) {}, onCancelled: () {})));
      expect(tester.widget<TextButton>(find.byType(TextButton)).onPressed, isNull);
    });

    testWidgets('confirm dialog entry point returns the choice', (tester) async {
      bool? result;
      await tester.pumpWidget(hqTestApp(Builder(builder: (c) => HqButton(label: 'Go', onPressed: () async => result = await HqConfirmDialog.show(c, title: 'Sure?', confirmLabel: 'Yes', cancelLabel: 'No')))));
      await tester.tap(find.text('Go'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Yes'));
      await tester.pumpAndSettle();
      expect(result, isTrue);
    });
  });

  group('HqAvatar and HqBadge', () {
    test('initials', () {
      expect(HqAvatar.initialsOf('Asha Rao'), 'AR');
      expect(HqAvatar.initialsOf('asha'), 'A');
      expect(HqAvatar.initialsOf('  A  B  C '), 'AC');
      expect(HqAvatar.initialsOf(''), '');
    });

    testWidgets('avatar shows initials on a stable palette colour', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqAvatar(name: 'Asha Rao')));
      expect(find.text('AR'), findsOneWidget);
      final first = tester.widget<CircleAvatar>(find.byType(CircleAvatar)).backgroundColor;
      await tester.pumpWidget(hqTestApp(const HqAvatar(name: 'Asha Rao')));
      expect(tester.widget<CircleAvatar>(find.byType(CircleAvatar)).backgroundColor, first);
      expect(HqTokens.light.chartCategorical, contains(first));
    });

    test('count text caps at max', () {
      expect(HqBadge.countText(5, 99), '5');
      expect(HqBadge.countText(150, 99), '99+');
    });

    testWidgets('badge pins a count to its child and hides at zero', (tester) async {
      await tester.pumpWidget(hqTestApp(const Padding(padding: EdgeInsets.all(20), child: HqBadge(count: 120, child: Icon(Icons.notifications)))));
      expect(find.text('99+'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(const HqBadge(count: 0, child: Icon(Icons.notifications))));
      expect(find.byType(Text), findsNothing);
      await tester.pumpWidget(hqTestApp(const HqBadge(dot: true)));
      expect(find.byType(Container), findsOneWidget);
    });
  });

  group('chips', () {
    testWidgets('chip toggles and shows a check when selected', (tester) async {
      bool? value;
      await tester.pumpWidget(hqTestApp(HqChip(label: 'Active', onSelected: (v) => value = v)));
      await tester.tap(find.text('Active'));
      expect(value, isTrue);
      await tester.pumpWidget(hqTestApp(HqChip(label: 'Active', selected: true, onSelected: (_) {})));
      expect(find.byIcon(Icons.check), findsOneWidget);
    });

    testWidgets('applied chip can be deleted', (tester) async {
      var deleted = 0;
      await tester.pumpWidget(hqTestApp(HqChip(label: 'Mumbai', deleteLabel: 'Remove filter', onDeleted: () => deleted++)));
      await tester.tap(find.byIcon(Icons.close));
      expect(deleted, 1);
    });

    testWidgets('single choice group replaces, multiple adds and removes', (tester) async {
      var values = <String>['a'];
      Widget group(bool multiple) => StatefulBuilder(
            builder: (context, setState) => HqChipGroup<String>(
              multiple: multiple,
              values: values,
              onChanged: (v) => setState(() => values = v),
              options: const [HqChipOption(value: 'a', label: 'A'), HqChipOption(value: 'b', label: 'B'), HqChipOption(value: 'c', label: 'C', disabled: true)],
            ),
          );
      await tester.pumpWidget(hqTestApp(group(false)));
      await tester.tap(find.text('B'));
      await tester.pump();
      expect(values, ['b']);
      await tester.tap(find.text('B'));
      await tester.pump();
      expect(values, isEmpty);
      await tester.pumpWidget(hqTestApp(group(true)));
      await tester.tap(find.text('A'));
      await tester.pump();
      await tester.tap(find.text('B'));
      await tester.pump();
      expect(values, ['a', 'b']);
      await tester.tap(find.text('A'));
      await tester.pump();
      expect(values, ['b']);
      await tester.tap(find.text('C'), warnIfMissed: false);
      expect(values, ['b']);
    });
  });

  group('cards and divider', () {
    testWidgets('card with header and tap', (tester) async {
      var taps = 0;
      await tester.pumpWidget(hqTestApp(HqCard(title: 'Lakeview', subtitle: 'Mumbai', icon: Icons.apartment, onTap: () => taps++, actions: const [Icon(Icons.more_vert)], child: const Text('body'))));
      expect(find.text('Lakeview'), findsOneWidget);
      expect(find.text('Mumbai'), findsOneWidget);
      expect(find.byIcon(Icons.more_vert), findsOneWidget);
      await tester.tap(find.text('body'));
      expect(taps, 1);
    });

    testWidgets('variants and padding build', (tester) async {
      for (final variant in HqCardVariant.values) {
        for (final padding in HqCardPadding.values) {
          await tester.pumpWidget(hqTestApp(HqCard(variant: variant, padding: padding, child: const Text('x'))));
          expect(find.text('x'), findsOneWidget);
        }
      }
    });

    testWidgets('section card needs a title; divider shows its label', (tester) async {
      await tester.pumpWidget(hqTestApp(const Column(children: [HqSectionCard(title: 'Terms', child: Text('t')), HqDivider(label: 'or')])));
      expect(find.text('Terms'), findsOneWidget);
      expect(find.text('or'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(const HqDivider()));
      expect(find.byType(Divider), findsOneWidget);
    });
  });

  group('masking', () {
    test('mask helpers keep the last four', () {
      expect(hqMaskTail('ABCDE1234F'), '••••••234F');
      expect(hqMaskPan('ABCDE1234F'), endsWith('234F'));
      expect(hqMaskAccount('123456789012'), '••••••••9012');
      expect(hqMaskTail('123'), '•••');
      expect(hqMaskTail(''), '');
      expect(hqMaskAadhaar('1234 5678 9012'), '••••••••9012');
      expect(hqMaskTail('ABCDEF', visible: 2), '••••EF');
    });

    testWidgets('masked value hides and reveals', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqMaskedValue(value: 'ABCDE1234F', revealable: true, showLabel: 'Show', hideLabel: 'Hide')));
      expect(find.text('ABCDE1234F'), findsNothing);
      expect(find.textContaining('234F'), findsOneWidget);
      await tester.tap(find.byTooltip('Show'));
      await tester.pump();
      expect(find.text('ABCDE1234F'), findsOneWidget);
      await tester.tap(find.byTooltip('Hide'));
      await tester.pump();
      expect(find.text('ABCDE1234F'), findsNothing);
    });

    testWidgets('not revealable by default', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqMaskedValue(value: 'ABCDE1234F')));
      expect(find.byType(IconButton), findsNothing);
    });

    test('money text format', () {
      expect(HqMoneyText.format(1234567.5), '₹12,34,567.50');
      expect(HqMoneyText.format(250, showPlus: true), '+₹250.00');
      expect(HqMoneyText.format(-250, showPlus: true), '-₹250.00');
      expect(HqMoneyText.format(null), '-');
    });

    testWidgets('money text masks and colours by sign', (tester) async {
      await tester.pumpWidget(hqTestApp(const Column(children: [
        HqMoneyText(amount: 1500, masked: true, maskedSemanticsLabel: 'Hidden'),
        HqMoneyText(amount: -20, colorBySign: true),
        HqMoneyText(amount: 20, colorBySign: true, showPlus: true),
      ])));
      expect(find.text('₹•••••'), findsOneWidget);
      final negative = tester.widget<Text>(find.text('-₹20.00'));
      expect(negative.style?.color, HqTokens.light.warn);
      final positive = tester.widget<Text>(find.text('+₹20.00'));
      expect(positive.style?.color, HqTokens.light.success);
    });
  });

  group('details', () {
    testWidgets('key value list shows rows, a dash for empty and copies', (tester) async {
      var copied = 0;
      String? clipboard;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') clipboard = (call.arguments as Map)['text'] as String?;
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      await tester.pumpWidget(hqTestApp(HqKeyValueList(
        copyLabel: 'Copy',
        copiedMessage: () => copied++,
        items: const [HqKeyValueItem(label: 'RERA', value: 'P51700012345', copyable: true), HqKeyValueItem(label: 'City'), HqKeyValueItem(label: 'Status', valueWidget: Text('Live'))],
      )));
      expect(find.text('RERA'), findsOneWidget);
      expect(find.text('-'), findsOneWidget);
      expect(find.text('Live'), findsOneWidget);
      await tester.tap(find.byTooltip('Copy'));
      await tester.pump();
      expect(clipboard, 'P51700012345');
      expect(copied, 1);
    });

    testWidgets('timeline lists entries with tone icons and shows the empty text', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqTimeline(entries: [
        HqTimelineEntry(id: '1', title: 'Payment received', when: '22 Nov', tone: HqStatusTone.success, description: 'UPI'),
        HqTimelineEntry(id: '2', title: 'Submitted', tone: HqStatusTone.info),
      ])));
      expect(find.text('Payment received'), findsOneWidget);
      expect(find.text('22 Nov'), findsOneWidget);
      expect(find.text('UPI'), findsOneWidget);
      expect(find.byIcon(Icons.check_circle), findsOneWidget);
      expect(find.byIcon(Icons.info), findsOneWidget);
      await tester.pumpWidget(hqTestApp(const HqTimeline(entries: [], emptyText: 'No history')));
      expect(find.text('No history'), findsOneWidget);
    });

    testWidgets('document tile maps status to tone and calls preview and remove', (tester) async {
      var previews = 0;
      var removed = 0;
      await tester.pumpWidget(hqTestApp(HqDocumentTile(
        name: 'pan.pdf',
        detail: '120 KB',
        status: HqDocumentStatus.verified,
        statusLabel: 'Verified',
        isPdf: true,
        previewLabel: 'Preview',
        removeLabel: 'Remove',
        onPreview: () => previews++,
        onRemove: () => removed++,
      )));
      expect(find.text('pan.pdf'), findsOneWidget);
      expect(find.text('Verified'), findsOneWidget);
      expect(find.byIcon(Icons.picture_as_pdf_outlined), findsOneWidget);
      expect(find.byIcon(Icons.verified), findsOneWidget);
      await tester.tap(find.byTooltip('Preview'));
      await tester.tap(find.byTooltip('Remove'));
      expect(previews, 1);
      expect(removed, 1);
    });

    testWidgets('accordion opens one at a time or several', (tester) async {
      Widget accordion(bool multiple) => HqAccordion(
            key: ValueKey<bool>(multiple),
            multiple: multiple,
            items: const [HqAccordionItem(id: 'a', title: 'First', child: Text('one')), HqAccordionItem(id: 'b', title: 'Second', child: Text('two'))],
          );
      await tester.pumpWidget(hqTestApp(accordion(false)));
      expect(find.text('one'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('hq-accordion-a')));
      await tester.pumpAndSettle();
      expect(find.text('one'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('hq-accordion-b')));
      await tester.pumpAndSettle();
      expect(find.text('one'), findsNothing);
      expect(find.text('two'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(accordion(true)));
      await tester.tap(find.byKey(const ValueKey('hq-accordion-a')));
      await tester.tap(find.byKey(const ValueKey('hq-accordion-b')));
      await tester.pumpAndSettle();
      expect(find.text('one'), findsOneWidget);
      expect(find.text('two'), findsOneWidget);
    });
  });
}
