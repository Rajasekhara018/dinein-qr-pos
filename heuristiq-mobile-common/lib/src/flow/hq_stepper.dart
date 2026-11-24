import 'dart:async';

import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_button.dart';

/// State of a step in a [HqStepper].
enum HqStepState { upcoming, current, completed, error, disabled }

/// One step: its title and optional subtitle, icon and content (the web `HqStepRailStep`, plus the state model of a
/// Material stepper).
@immutable
class HqStep {
  const HqStep({required this.id, required this.title, this.subtitle = '', this.icon, this.optional = false, this.state, this.content});

  final String id;
  final String title;
  final String subtitle;
  final IconData? icon;

  /// An optional step (the caller passes its "Optional" text in [subtitle]).
  final bool optional;

  /// Forces a state (error, disabled); null lets the stepper derive it from the current index.
  final HqStepState? state;

  /// Shown under the step in the vertical layout while it is current.
  final Widget? content;
}

/// Words the stepper needs for screen readers (`step 2 of 4, completed`), passed in by the app.
@immutable
class HqStepperTexts {
  const HqStepperTexts({required this.stepOf, required this.completed, required this.current, required this.error, required this.disabled, required this.upcoming});

  /// "Step 2 of 4".
  final String Function(int number, int total) stepOf;
  final String completed;
  final String current;
  final String error;
  final String disabled;
  final String upcoming;

  /// English texts for tests and the example app; real apps pass translated ones.
  factory HqStepperTexts.english() => HqStepperTexts(stepOf: (n, t) => 'Step $n of $t', completed: 'completed', current: 'current', error: 'has an error', disabled: 'unavailable', upcoming: 'not started');

  String of(HqStepState state) => switch (state) {
        HqStepState.completed => completed,
        HqStepState.current => current,
        HqStepState.error => error,
        HqStepState.disabled => disabled,
        HqStepState.upcoming => upcoming,
      };
}

/// A step indicator for multi-step flows such as KYC and subscription (the counterpart of `hq-step-rail`): horizontal
/// across the top, or vertical with the current step's content open. States are completed (check), current, error and
/// disabled; none relies on colour alone (icon and text for screen readers). A tap on a completed or current step calls
/// [onStepTapped]; upcoming steps are not reachable by tapping.
class HqStepper extends StatelessWidget {
  const HqStepper({super.key, required this.steps, required this.currentIndex, required this.texts, this.onStepTapped, this.axis = Axis.horizontal});

  final List<HqStep> steps;
  final int currentIndex;
  final HqStepperTexts texts;
  final ValueChanged<int>? onStepTapped;
  final Axis axis;

  /// The state of step [index]: an explicit [HqStep.state], else completed before the current step, current, upcoming after.
  HqStepState stateOf(int index) {
    final forced = steps[index].state;
    if (forced != null) return forced;
    if (index < currentIndex) return HqStepState.completed;
    if (index == currentIndex) return HqStepState.current;
    return HqStepState.upcoming;
  }

  bool _tappable(int index) {
    final s = stateOf(index);
    return onStepTapped != null && (s == HqStepState.completed || s == HqStepState.current || s == HqStepState.error);
  }

  @override
  Widget build(BuildContext context) => axis == Axis.horizontal ? _horizontal(context) : _vertical(context);

  Widget _marker(BuildContext context, int index) {
    final tokens = context.hq;
    final state = stateOf(index);
    final (Color bg, Color fg, Widget inner) = switch (state) {
      HqStepState.completed => (tokens.success, tokens.onSuccess, Icon(Icons.check, size: 16, color: tokens.onSuccess)),
      HqStepState.error => (tokens.warn, tokens.onWarn, Icon(Icons.priority_high, size: 16, color: tokens.onWarn)),
      HqStepState.current => (tokens.primary, tokens.onPrimary, steps[index].icon != null ? Icon(steps[index].icon, size: 16, color: tokens.onPrimary) : Text('${index + 1}', style: TextStyle(color: tokens.onPrimary, fontWeight: FontWeight.w700, fontSize: 13))),
      HqStepState.disabled => (tokens.fieldBorder, tokens.muted, Icon(Icons.lock_outline, size: 14, color: tokens.muted)),
      HqStepState.upcoming => (Colors.transparent, tokens.muted, steps[index].icon != null ? Icon(steps[index].icon, size: 16, color: tokens.muted) : Text('${index + 1}', style: TextStyle(color: tokens.muted, fontWeight: FontWeight.w600, fontSize: 13))),
    };
    return Container(
      width: 28,
      height: 28,
      alignment: Alignment.center,
      decoration: BoxDecoration(color: bg, shape: BoxShape.circle, border: state == HqStepState.upcoming ? Border.all(color: fg.withValues(alpha: 0.6)) : null),
      child: inner,
    );
  }

  Widget _semantic(int index, Widget child) {
    final state = stateOf(index);
    return Semantics(
      container: true,
      button: _tappable(index),
      selected: state == HqStepState.current,
      enabled: state != HqStepState.disabled,
      label: '${texts.stepOf(index + 1, steps.length)}: ${steps[index].title}, ${texts.of(state)}',
      excludeSemantics: true,
      onTap: _tappable(index) ? () => onStepTapped!(index) : null,
      child: child,
    );
  }

  Widget _horizontal(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < steps.length; i++) ...[
            _semantic(
              i,
              InkWell(
                key: ValueKey<String>('hq-step-${steps[i].id}'),
                borderRadius: BorderRadius.circular(HqRadius.sm),
                onTap: _tappable(i) ? () => onStepTapped!(i) : null,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minWidth: 72, maxWidth: 120),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: HqSpacing.xs, vertical: HqSpacing.xs),
                    child: Column(
                      children: [
                        _marker(context, i),
                        const SizedBox(height: HqSpacing.xs),
                        Text(steps[i].title, textAlign: TextAlign.center, maxLines: 2, overflow: TextOverflow.ellipsis, style: text.labelMedium?.copyWith(fontWeight: stateOf(i) == HqStepState.current ? FontWeight.w700 : FontWeight.w500, color: stateOf(i) == HqStepState.error ? tokens.warn : (stateOf(i) == HqStepState.disabled ? tokens.muted : tokens.ink))),
                        if (steps[i].subtitle.isNotEmpty) Text(steps[i].subtitle, textAlign: TextAlign.center, maxLines: 1, overflow: TextOverflow.ellipsis, style: text.labelSmall?.copyWith(color: tokens.muted)),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            if (i < steps.length - 1) Padding(padding: const EdgeInsets.only(top: 14), child: Container(width: 24, height: 2, color: i < currentIndex ? tokens.success : tokens.fieldBorder)),
          ],
        ],
      ),
    );
  }

  Widget _vertical(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < steps.length; i++)
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SizedBox(
                  width: 28,
                  child: Column(children: [_marker(context, i), if (i < steps.length - 1) Expanded(child: Container(width: 2, color: i < currentIndex ? tokens.success : tokens.fieldBorder))]),
                ),
                const SizedBox(width: HqSpacing.md),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: HqSpacing.lg),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _semantic(
                          i,
                          InkWell(
                            key: ValueKey<String>('hq-step-${steps[i].id}'),
                            onTap: _tappable(i) ? () => onStepTapped!(i) : null,
                            child: ConstrainedBox(
                              constraints: const BoxConstraints(minHeight: 28),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(steps[i].title, style: text.titleSmall?.copyWith(fontWeight: stateOf(i) == HqStepState.current ? FontWeight.w700 : FontWeight.w500, color: stateOf(i) == HqStepState.error ? tokens.warn : null)),
                                  if (steps[i].subtitle.isNotEmpty) Text(steps[i].subtitle, style: text.bodySmall?.copyWith(color: tokens.muted)),
                                ],
                              ),
                            ),
                          ),
                        ),
                        if (stateOf(i) == HqStepState.current && steps[i].content != null) Padding(padding: const EdgeInsets.only(top: HqSpacing.md), child: steps[i].content),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

/// A [HqStep] with the screen shown for it and an optional check run before moving on.
@immutable
class HqWizardStep {
  const HqWizardStep({required this.step, required this.builder, this.validate});

  final HqStep step;
  final WidgetBuilder builder;

  /// Returns an error message to stay on the step (it is shown in the step and the step turns red), or null to continue.
  final FutureOr<String?> Function()? validate;
}

/// Texts of the [HqWizard] buttons.
@immutable
class HqWizardTexts {
  const HqWizardTexts({required this.back, required this.next, required this.submit, this.saveDraft = ''});

  final String back;
  final String next;
  final String submit;

  /// Label of the draft button; the button is hidden when empty.
  final String saveDraft;
}

/// A stepper with the current step's screen and Back, Next / Submit and optional Save draft buttons (the KYC and subscription
/// flows). Next runs the step's `validate`; a message keeps the user on the step with the error shown, success marks the step
/// completed and moves on. Going back is always allowed; jumping forward only to steps already completed. The last step's
/// button is Submit and calls [onSubmit] (its returned error, if any, is shown).
class HqWizard extends StatefulWidget {
  const HqWizard({super.key, required this.steps, required this.texts, required this.stepperTexts, required this.onSubmit, this.onSaveDraft, this.onStepChanged, this.initialIndex = 0, this.axis = Axis.horizontal});

  final List<HqWizardStep> steps;
  final HqWizardTexts texts;
  final HqStepperTexts stepperTexts;

  /// Called on the last step's Submit; return an error message to stay, null when it worked.
  final FutureOr<String?> Function() onSubmit;

  /// Called by "Save draft" with the current step index; the app persists the draft (never PAN, Aadhaar or OTP locally).
  final FutureOr<void> Function(int stepIndex)? onSaveDraft;
  final ValueChanged<int>? onStepChanged;
  final int initialIndex;
  final Axis axis;

  @override
  State<HqWizard> createState() => _HqWizardState();
}

class _HqWizardState extends State<HqWizard> {
  late int _index = widget.initialIndex.clamp(0, widget.steps.length - 1);
  late final List<HqStepState?> _forced = List<HqStepState?>.filled(widget.steps.length, null);
  final Set<int> _done = <int>{};
  String? _error;
  bool _busy = false;

  bool get _isLast => _index == widget.steps.length - 1;

  void _go(int index) {
    setState(() {
      _index = index;
      _error = null;
    });
    widget.onStepChanged?.call(index);
  }

  Future<void> _next() async {
    if (_busy) return;
    setState(() => _busy = true);
    final step = widget.steps[_index];
    String? problem;
    try {
      problem = await step.validate?.call();
      if (problem == null && _isLast) problem = await widget.onSubmit();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    if (!mounted) return;
    if (problem != null) {
      setState(() {
        _forced[_index] = HqStepState.error;
        _error = problem;
      });
      return;
    }
    _forced[_index] = null;
    _done.add(_index);
    if (_isLast) {
      setState(() => _error = null);
    } else {
      _go(_index + 1);
    }
  }

  Future<void> _draft() async {
    if (widget.onSaveDraft == null || _busy) return;
    setState(() => _busy = true);
    try {
      await widget.onSaveDraft!(_index);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final stepper = HqStepper(
      axis: widget.axis,
      currentIndex: _index,
      texts: widget.stepperTexts,
      steps: [
        for (var i = 0; i < widget.steps.length; i++)
          HqStep(
            id: widget.steps[i].step.id,
            title: widget.steps[i].step.title,
            subtitle: widget.steps[i].step.subtitle,
            icon: widget.steps[i].step.icon,
            optional: widget.steps[i].step.optional,
            state: _forced[i] ?? (_done.contains(i) && i != _index ? HqStepState.completed : widget.steps[i].step.state),
          ),
      ],
      onStepTapped: (i) {
        if (i <= _index || _done.contains(i - 1)) _go(i);
      },
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        stepper,
        const SizedBox(height: HqSpacing.lg),
        Expanded(child: SingleChildScrollView(child: widget.steps[_index].builder(context))),
        if (_error != null && _error!.isNotEmpty)
          Padding(padding: const EdgeInsets.only(top: HqSpacing.sm), child: Semantics(liveRegion: true, child: Text(_error!, style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: tokens.warn)))),
        Padding(
          padding: const EdgeInsets.only(top: HqSpacing.md),
          child: Wrap(
            alignment: WrapAlignment.spaceBetween,
            runSpacing: HqSpacing.sm,
            spacing: HqSpacing.sm,
            children: [
              if (_index > 0) HqButton(label: widget.texts.back, appearance: HqButtonAppearance.outline, onPressed: _busy ? null : () => _go(_index - 1)),
              if (widget.onSaveDraft != null && widget.texts.saveDraft.isNotEmpty) HqButton(label: widget.texts.saveDraft, appearance: HqButtonAppearance.text, onPressed: _busy ? null : _draft),
              HqButton(label: _isLast ? widget.texts.submit : widget.texts.next, loading: _busy, onPressed: _next),
            ],
          ),
        ),
      ],
    );
  }
}
