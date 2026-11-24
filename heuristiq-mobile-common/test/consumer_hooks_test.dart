import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

/// Optional hooks added for consumer apps (field validator/onTap/fieldKey, phone controller, confirm tone, toast showOn,
/// brand state badge). Without them
/// every widget must behave exactly as before.
void main() {
  group('HqField form hooks', () {
    testWidgets('without a validator it is a plain TextField carrying fieldKey', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqField(label: 'Name', fieldKey: ValueKey<String>('name-field'))));
      expect(tester.widget<TextField>(find.byKey(const ValueKey<String>('name-field'))), isA<TextField>());
      expect(find.byType(TextFormField), findsNothing);
    });

    testWidgets('a validator joins the Form and shows its message', (tester) async {
      final form = GlobalKey<FormState>();
      await tester.pumpWidget(hqTestApp(Form(
        key: form,
        child: HqField(
          label: 'Name',
          fieldKey: const ValueKey<String>('name-field'),
          validator: (v) => (v ?? '').isEmpty ? 'Name is required' : null,
        ),
      )));
      expect(find.byType(TextFormField), findsOneWidget);
      expect(form.currentState!.validate(), isFalse);
      await tester.pump();
      expect(find.text('Name is required'), findsOneWidget);

      await tester.enterText(find.byKey(const ValueKey<String>('name-field')), 'Asha');
      expect(form.currentState!.validate(), isTrue);
      await tester.pump();
      expect(find.text('Name is required'), findsNothing);
    });

    testWidgets('an explicit error still shows while the validator passes', (tester) async {
      await tester.pumpWidget(hqTestApp(Form(
        child: HqField(label: 'Name', value: 'x', error: 'Taken', validator: (_) => null),
      )));
      expect(find.text('Taken'), findsOneWidget);
    });

    testWidgets('onTap fires on a read-only field', (tester) async {
      var taps = 0;
      await tester.pumpWidget(hqTestApp(HqField(label: 'Pickup', readOnly: true, onTap: () => taps++)));
      await tester.tap(find.byType(TextField));
      expect(taps, 1);
    });
  });

  group('HqPhoneNumberInput hooks', () {
    testWidgets('uses the caller controller and follows changes made in code', (tester) async {
      final controller = TextEditingController(text: '98765');
      addTearDown(controller.dispose);
      await tester.pumpWidget(hqTestApp(HqPhoneNumberInput(
        label: 'Mobile',
        countryIso: 'IN',
        controller: controller,
        invalidError: 'Invalid number',
      )));
      expect(find.text('98765'), findsOneWidget);
      expect(find.text('Invalid number'), findsOneWidget);

      controller.text = '9876543210';
      await tester.pump();
      expect(find.text('Invalid number'), findsNothing);
    });

    testWidgets('typing updates the caller controller', (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      await tester.pumpWidget(hqTestApp(HqPhoneNumberInput(label: 'Mobile', countryIso: 'IN', controller: controller)));
      await tester.enterText(find.byType(TextField), '9876543210');
      expect(controller.text, '9876543210');
    });

    testWidgets('the validator gets the parsed value inside a Form', (tester) async {
      final form = GlobalKey<FormState>();
      HqPhoneValue? seen;
      await tester.pumpWidget(hqTestApp(Form(
        key: form,
        child: HqPhoneNumberInput(
          label: 'Mobile',
          countryIso: 'IN',
          fieldKey: const ValueKey<String>('phone-field'),
          validator: (value) {
            seen = value;
            return value.isValid ? null : 'Enter a valid number';
          },
        ),
      )));
      expect(form.currentState!.validate(), isFalse);
      await tester.pump();
      expect(find.text('Enter a valid number'), findsOneWidget);

      await tester.enterText(find.byKey(const ValueKey<String>('phone-field')), '9876543210');
      expect(form.currentState!.validate(), isTrue);
      expect(seen!.e164, '+919876543210');
    });

    testWidgets('onSubmitted reports the typed digits', (tester) async {
      String? submitted;
      await tester.pumpWidget(hqTestApp(HqPhoneNumberInput(
        label: 'Mobile',
        countryIso: 'IN',
        textInputAction: TextInputAction.done,
        onSubmitted: (v) => submitted = v,
      )));
      await tester.enterText(find.byType(TextField), '9876543210');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      expect(submitted, '9876543210');
    });
  });

  group('HqBrandStyle.stateBadge', () {
    ThemeData themed({required bool badge}) =>
        HqTheme.light().copyWith(extensions: [HqTokens.light, HqBrandStyle(stateBadge: badge)]);

    Iterable<BoxDecoration> badges(WidgetTester tester) => tester
        .widgetList<Container>(find.byType(Container))
        .map((c) => c.decoration)
        .whereType<BoxDecoration>()
        .where((d) => d.shape == BoxShape.circle && d.gradient != null);

    testWidgets('off by default: the plain muted 48 px icon, no badge, no entrance motion', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqEmptyState(title: 'No rides yet', icon: Icons.local_taxi)));
      expect(tester.widget<Icon>(find.byIcon(Icons.local_taxi)).size, 48);
      expect(badges(tester), isEmpty);
      expect(find.byType(TweenAnimationBuilder<double>), findsNothing);
    });

    testWidgets('on: empty state icon sits in a primary-tinted badge and fades in', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqEmptyState(title: 'No rides yet', icon: Icons.local_taxi), theme: themed(badge: true)));
      final icon = tester.widget<Icon>(find.byIcon(Icons.local_taxi));
      expect(icon.size, 34);
      expect(icon.color, HqTokens.light.primary);
      expect(badges(tester), hasLength(1));
      expect(find.byType(TweenAnimationBuilder<double>), findsOneWidget);
      await tester.pumpAndSettle();
      expect(find.text('No rides yet'), findsOneWidget);
    });

    testWidgets('on: error state badge uses the warn colour', (tester) async {
      await tester.pumpWidget(hqTestApp(HqErrorState(title: 'Could not load', retryLabel: 'Retry', onRetry: () {}), theme: themed(badge: true)));
      expect(tester.widget<Icon>(find.byIcon(Icons.error_outline)).color, HqTokens.light.warn);
      expect(badges(tester), hasLength(1));
    });

    testWidgets('reduced motion: badge without the entrance animation', (tester) async {
      await tester.pumpWidget(hqTestApp(
        Builder(
          builder: (context) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: const HqEmptyState(title: 'Empty'),
          ),
        ),
        theme: themed(badge: true),
      ));
      expect(badges(tester), hasLength(1));
      expect(find.byType(TweenAnimationBuilder<double>), findsNothing);
    });

    test('copyWith and lerp carry the flag', () {
      const on = HqBrandStyle(stateBadge: true);
      expect(const HqBrandStyle().copyWith(stateBadge: true).stateBadge, isTrue);
      expect(on.copyWith().stateBadge, isTrue);
      expect(const HqBrandStyle().lerp(on, 0.7).stateBadge, isTrue);
      expect(const HqBrandStyle().lerp(on, 0.3).stateBadge, isFalse);
    });
  });

  group('HqOtpInput.onResendAsync', () {
    Future<void> pumpOtp(WidgetTester tester, Future<bool> Function() resend) => tester.pumpWidget(hqTestApp(HqOtpInput(
          onCompleted: (_) {},
          autofocus: false,
          resendSeconds: 2,
          resendLabel: 'Resend code',
          resendCountdown: (s) => 'Resend in ${s}s',
          onResendAsync: resend,
        )));

    testWidgets('a failed resend keeps "resend" on offer; a successful one restarts the countdown', (tester) async {
      var ok = false;
      var calls = 0;
      await pumpOtp(tester, () async {
        calls++;
        return ok;
      });
      expect(find.text('Resend in 2s'), findsOneWidget);
      await tester.pump(const Duration(seconds: 2));
      await tester.tap(find.text('Resend code'));
      await tester.pump();
      expect(calls, 1);
      expect(find.text('Resend code'), findsOneWidget, reason: 'failed: no new countdown');

      ok = true;
      await tester.tap(find.text('Resend code'));
      await tester.pump();
      expect(calls, 2);
      expect(find.text('Resend in 2s'), findsOneWidget);
      await tester.pump(const Duration(seconds: 2));
    });

    testWidgets('a throwing resend counts as failed and never escapes', (tester) async {
      await pumpOtp(tester, () async => throw StateError('offline'));
      await tester.pump(const Duration(seconds: 2));
      await tester.tap(find.text('Resend code'));
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(find.text('Resend code'), findsOneWidget);
    });
  });

  group('HqToastService.showOn', () {
    testWidgets('shows a toast on a given messenger, replacing the visible one', (tester) async {
      await tester.pumpWidget(hqTestApp(const SizedBox()));
      final messenger = tester.state<ScaffoldMessengerState>(find.byType(ScaffoldMessenger));
      HqToastService.showOn(messenger, HqToastType.success, 'First', const Duration(seconds: 3));
      await tester.pump();
      HqToastService.showOn(messenger, HqToastType.error, 'Second', const Duration(seconds: 3),
          action: HqToastAction(label: 'Retry', onPressed: () {}));
      await tester.pumpAndSettle();
      expect(find.text('First'), findsNothing);
      expect(find.text('Second'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
      expect(find.byIcon(Icons.error_outline), findsOneWidget);
    });

    testWidgets('does nothing without a messenger', (tester) async {
      expect(HqToastService.showOn(null, HqToastType.info, 'Nowhere', const Duration(seconds: 3)), isNull);
    });
  });

  group('HqDialog.confirm tone', () {
    Future<void> open(WidgetTester tester, {HqDialogTone? tone, bool destructive = false}) async {
      await tester.pumpWidget(hqTestApp(Builder(
        builder: (context) => HqButton(
          label: 'Open',
          onPressed: () => HqDialog.confirm(
            context,
            title: 'Leave?',
            confirmLabel: 'Leave',
            cancelLabel: 'Stay',
            icon: Icons.warning_amber_rounded,
            destructive: destructive,
            tone: tone,
          ),
        ),
      )));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
    }

    HqButton confirmButton(WidgetTester tester) => tester.widget<HqButton>(find.widgetWithText(HqButton, 'Leave'));
    Color badgeIconColor(WidgetTester tester) => tester.widget<Icon>(find.byIcon(Icons.warning_amber_rounded)).color!;

    testWidgets('warning paints the badge amber and keeps a primary confirm button', (tester) async {
      await open(tester, tone: HqDialogTone.warning);
      expect(badgeIconColor(tester), HqTokens.light.toneWarning.foreground);
      expect(confirmButton(tester).color, HqButtonColor.primary);
    });

    testWidgets('destructive without a tone is unchanged (danger)', (tester) async {
      await open(tester, destructive: true);
      expect(badgeIconColor(tester), HqTokens.light.warn);
      expect(confirmButton(tester).color, HqButtonColor.warn);
    });

    testWidgets('confirmIcon is on the confirm button and barrierDismissible false keeps it open', (tester) async {
      bool? answer;
      await tester.pumpWidget(hqTestApp(Builder(
        builder: (context) => HqButton(
          label: 'Open',
          onPressed: () async => answer = await HqDialog.confirm(
            context,
            title: 'Call for help?',
            confirmLabel: 'Call',
            cancelLabel: 'Not now',
            icon: Icons.sos,
            confirmIcon: Icons.phone,
            barrierDismissible: false,
          ),
        ),
      )));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(tester.widget<HqButton>(find.widgetWithText(HqButton, 'Call')).icon, Icons.phone);
      await tester.tapAt(const Offset(4, 4));
      await tester.pumpAndSettle();
      expect(find.text('Call for help?'), findsOneWidget);
      await tester.tap(find.text('Not now'));
      await tester.pumpAndSettle();
      expect(answer, isFalse);
    });

    testWidgets('alert with an icon: featured success badge, one button, barrier can be locked', (tester) async {
      await tester.pumpWidget(hqTestApp(Builder(
        builder: (context) => HqButton(
          label: 'Open',
          onPressed: () => HqDialog.alert(
            context,
            title: 'Money added',
            okLabel: 'Done',
            icon: Icons.check_rounded,
            tone: HqDialogTone.success,
            barrierDismissible: false,
          ),
        ),
      )));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(tester.widget<Icon>(find.byIcon(Icons.check_rounded)).color, HqTokens.light.success);
      expect(find.byType(HqButton), findsNWidgets(2), reason: 'the opener and the single Done button');
      await tester.tapAt(const Offset(4, 4));
      await tester.pumpAndSettle();
      expect(find.text('Money added'), findsOneWidget);
      await tester.tap(find.text('Done'));
      await tester.pumpAndSettle();
      expect(find.text('Money added'), findsNothing);
    });

    testWidgets('a tone overrides destructive', (tester) async {
      await open(tester, destructive: true, tone: HqDialogTone.primary);
      expect(badgeIconColor(tester), HqTokens.light.primary);
      expect(confirmButton(tester).color, HqButtonColor.primary);
    });
  });
}
