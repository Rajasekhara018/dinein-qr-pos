import 'package:flutter/material.dart';

import '../theme/hq_brand_style.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_button.dart';

/// The icon of an empty or error state: plain and muted, or (with [HqBrandStyle.stateBadge]) inside a soft tinted badge.
class _StateIcon extends StatelessWidget {
  const _StateIcon({required this.icon, required this.color, required this.plainColor});

  final IconData icon;
  final Color color;
  final Color plainColor;

  @override
  Widget build(BuildContext context) {
    if (!context.hqBrand.stateBadge) return ExcludeSemantics(child: Icon(icon, size: 48, color: plainColor));
    return ExcludeSemantics(
      child: Container(
        width: 76,
        height: 76,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [color.withValues(alpha: 0.14), color.withValues(alpha: 0.05)],
          ),
        ),
        child: Icon(icon, size: 34, color: color),
      ),
    );
  }
}

/// With [HqBrandStyle.stateBadge], fades and lifts [child] in once; otherwise (or with reduced motion) shows it as is.
class _StateEntrance extends StatelessWidget {
  const _StateEntrance({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (!context.hqBrand.stateBadge || (MediaQuery.maybeDisableAnimationsOf(context) ?? false)) return child;
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: const Duration(milliseconds: 420),
      curve: Curves.easeOutCubic,
      builder: (context, value, child) => Opacity(
        opacity: value,
        child: Transform.translate(offset: Offset(0, (1 - value) * 14), child: child),
      ),
      child: child,
    );
  }
}

/// Nothing to show: an icon, a title, a message and an optional action (the counterpart of `hq-empty-state`).
class HqEmptyState extends StatelessWidget {
  const HqEmptyState({super.key, required this.title, this.message = '', this.icon = Icons.inbox_outlined, this.action});

  final String title;
  final String message;
  final IconData icon;

  /// A button, e.g. "Browse properties".
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(HqSpacing.xl),
        child: Semantics(
          container: true,
          child: _StateEntrance(child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _StateIcon(icon: icon, color: tokens.primary, plainColor: tokens.muted),
              const SizedBox(height: HqSpacing.md),
              Text(title, textAlign: TextAlign.center, style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
              if (message.isNotEmpty) Padding(padding: const EdgeInsets.only(top: HqSpacing.xs), child: Text(message, textAlign: TextAlign.center, style: text.bodyMedium?.copyWith(color: tokens.muted))),
              if (action != null) Padding(padding: const EdgeInsets.only(top: HqSpacing.lg), child: action),
            ],
          )),
        ),
      ),
    );
  }
}

/// Something failed: a message and a retry button (the counterpart of `hq-error-state`).
class HqErrorState extends StatelessWidget {
  const HqErrorState({super.key, required this.title, this.message = '', this.retryLabel = '', this.onRetry});

  final String title;
  final String message;
  final String retryLabel;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(HqSpacing.xl),
        child: Semantics(
          container: true,
          liveRegion: true,
          child: _StateEntrance(child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _StateIcon(icon: Icons.error_outline, color: tokens.warn, plainColor: tokens.warn),
              const SizedBox(height: HqSpacing.md),
              Text(title, textAlign: TextAlign.center, style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
              if (message.isNotEmpty) Padding(padding: const EdgeInsets.only(top: HqSpacing.xs), child: Text(message, textAlign: TextAlign.center, style: text.bodyMedium?.copyWith(color: tokens.muted))),
              if (onRetry != null)
                Padding(
                  padding: const EdgeInsets.only(top: HqSpacing.lg),
                  child: HqButton(label: retryLabel.isEmpty ? null : retryLabel, icon: retryLabel.isEmpty ? Icons.refresh : null, appearance: HqButtonAppearance.outline, onPressed: onRetry),
                ),
            ],
          )),
        ),
      ),
    );
  }
}

/// Which of the four views a [HqViewState] shows.
enum HqViewStatus { loading, empty, error, content }

/// Switches between loading, empty, error and content so every screen handles all four (the counterpart of `hq-view-states`).
class HqViewState extends StatelessWidget {
  const HqViewState({
    super.key,
    required this.status,
    required this.content,
    this.loading,
    this.empty,
    this.error,
  });

  final HqViewStatus status;
  final Widget content;

  /// Defaults to a centred spinner.
  final Widget? loading;

  /// Defaults to nothing: pass a [HqEmptyState].
  final Widget? empty;

  /// Defaults to nothing: pass a [HqErrorState].
  final Widget? error;

  @override
  Widget build(BuildContext context) => switch (status) {
        HqViewStatus.loading => loading ?? const Center(child: CircularProgressIndicator()),
        HqViewStatus.empty => empty ?? const SizedBox.shrink(),
        HqViewStatus.error => error ?? const SizedBox.shrink(),
        HqViewStatus.content => content,
      };
}
