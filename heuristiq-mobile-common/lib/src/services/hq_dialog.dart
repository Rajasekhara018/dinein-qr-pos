import 'package:flutter/material.dart';

import '../theme/hq_brand_style.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_button.dart';
import '../ui/hq_field.dart';

/// Colour of a confirm dialog: [primary] (the default), [warning] (a caution: amber icon badge, primary confirm button) or
/// [danger] (red icon badge and confirm button, the same as `destructive: true`); [success] (green badge, for alerts).
enum HqDialogTone { primary, warning, danger, success }

/// Confirm, alert and prompt dialogs (the counterpart of `HqAppDialogService`). Every text is passed in.
abstract final class HqDialog {
  /// Asks a yes/no question; completes with true only when the user confirms. [destructive] paints the confirm button red.
  /// With an [icon] the dialog is the featured layout: the icon in a soft badge, a centred title and message, and the two
  /// actions side by side at full width. A [tone] overrides [destructive] when given (see [HqDialogTone]).
  /// [confirmIcon] puts an icon on the confirm button; with [barrierDismissible] false only the two buttons close it
  /// (for a decision that must be answered, e.g. an SOS call).
  static Future<bool> confirm(
    BuildContext context, {
    required String title,
    required String confirmLabel,
    required String cancelLabel,
    String? message,
    bool destructive = false,
    IconData? icon,
    HqDialogTone? tone,
    IconData? confirmIcon,
    bool barrierDismissible = true,
  }) async {
    final effective = tone ?? (destructive ? HqDialogTone.danger : HqDialogTone.primary);
    if (icon != null) {
      final featured = await showDialog<bool>(
        context: context,
        barrierDismissible: barrierDismissible,
        builder: (dialogContext) => _FeaturedConfirm(
          icon: icon,
          title: title,
          message: message,
          confirmLabel: confirmLabel,
          cancelLabel: cancelLabel,
          tone: effective,
          confirmIcon: confirmIcon,
        ),
      );
      return featured == true;
    }
    final result = await showDialog<bool>(
      context: context,
      barrierDismissible: barrierDismissible,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: dialogContext.hq.surface,
        title: Text(title),
        content: message == null ? null : Text(message),
        actions: [
          HqButton(label: cancelLabel, appearance: HqButtonAppearance.text, onPressed: () => Navigator.of(dialogContext).pop(false)),
          HqButton(
            label: confirmLabel,
            color: effective == HqDialogTone.danger ? HqButtonColor.warn : HqButtonColor.primary,
            icon: confirmIcon,
            onPressed: () => Navigator.of(dialogContext).pop(true),
          ),
        ],
      ),
    );
    return result == true;
  }

  /// Shows a message with one button. With an [icon] it is the featured layout (icon badge in the [tone]'s colour,
  /// centred text, one full-width button), e.g. a success after a payment; [barrierDismissible] false makes the button
  /// the only way to close it.
  static Future<void> alert(
    BuildContext context, {
    required String title,
    required String okLabel,
    String? message,
    IconData? icon,
    HqDialogTone tone = HqDialogTone.primary,
    bool barrierDismissible = true,
  }) {
    if (icon != null) {
      return showDialog<void>(
        context: context,
        barrierDismissible: barrierDismissible,
        builder: (_) => _FeaturedConfirm(icon: icon, title: title, message: message, confirmLabel: okLabel, tone: tone),
      );
    }
    return showDialog<void>(
      context: context,
      barrierDismissible: barrierDismissible,
      builder: (dialogContext) => AlertDialog(
        backgroundColor: dialogContext.hq.surface,
        title: Text(title),
        content: message == null ? null : Text(message),
        actions: [HqButton(label: okLabel, onPressed: () => Navigator.of(dialogContext).pop())],
      ),
    );
  }

  /// Asks for one text value; completes with the text, or null when cancelled. With [required] an empty value shows
  /// [requiredMessage] and the dialog stays open. [type] [HqFieldType.password] hides the text (with the show / hide
  /// toggle) and returns it untrimmed.
  static Future<String?> prompt(
    BuildContext context, {
    required String title,
    required String confirmLabel,
    required String cancelLabel,
    String? message,
    String label = '',
    String initialValue = '',
    bool required = false,
    String? requiredMessage,
    int? maxLength,
    HqFieldType type = HqFieldType.text,
  }) {
    return showDialog<String>(
      context: context,
      builder: (_) => _PromptDialog(
        title: title,
        message: message,
        label: label,
        initialValue: initialValue,
        confirmLabel: confirmLabel,
        cancelLabel: cancelLabel,
        required: required,
        requiredMessage: requiredMessage,
        maxLength: maxLength,
        type: type,
      ),
    );
  }
}

class _PromptDialog extends StatefulWidget {
  const _PromptDialog({
    required this.title,
    required this.message,
    required this.label,
    required this.initialValue,
    required this.confirmLabel,
    required this.cancelLabel,
    required this.required,
    required this.requiredMessage,
    required this.maxLength,
    this.type = HqFieldType.text,
  });

  final String title;
  final String? message;
  final String label;
  final String initialValue;
  final String confirmLabel;
  final String cancelLabel;
  final bool required;
  final String? requiredMessage;
  final int? maxLength;
  final HqFieldType type;

  @override
  State<_PromptDialog> createState() => _PromptDialogState();
}

class _PromptDialogState extends State<_PromptDialog> {
  late final TextEditingController _controller = TextEditingController(text: widget.initialValue);
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final value = widget.type == HqFieldType.password ? _controller.text : _controller.text.trim();
    if (widget.required && value.isEmpty) {
      setState(() => _error = widget.requiredMessage ?? '');
      return;
    }
    Navigator.of(context).pop(value);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: context.hq.surface,
      title: Text(widget.title),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (widget.message != null) Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(widget.message!)),
          HqField(
            controller: _controller,
            label: widget.label,
            required: widget.required,
            error: _error,
            maxLength: widget.maxLength,
            type: widget.type,
            autofocus: true,
            onChanged: (_) {
              if (_error != null) setState(() => _error = null);
            },
            onSubmitted: (_) => _submit(),
          ),
        ],
      ),
      actions: [
        HqButton(label: widget.cancelLabel, appearance: HqButtonAppearance.text, onPressed: () => Navigator.of(context).pop()),
        HqButton(label: widget.confirmLabel, onPressed: _submit),
      ],
    );
  }
}

/// The featured confirm dialog: an icon badge, centred text and full-width actions, in the brand dialog radius.
class _FeaturedConfirm extends StatelessWidget {
  const _FeaturedConfirm({
    required this.icon,
    required this.title,
    required this.message,
    required this.confirmLabel,
    this.cancelLabel,
    required this.tone,
    this.confirmIcon,
  });

  final IconData icon;
  final String title;
  final String? message;
  final String confirmLabel;

  /// Null shows only the confirm button (an alert).
  final String? cancelLabel;
  final HqDialogTone tone;
  final IconData? confirmIcon;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final accent = switch (tone) {
      HqDialogTone.danger => tokens.warn,
      HqDialogTone.warning => tokens.toneWarning.foreground,
      HqDialogTone.success => tokens.success,
      HqDialogTone.primary => tokens.primary,
    };
    final text = Theme.of(context).textTheme;
    final body = message;
    final cancel = cancelLabel;
    return Dialog(
      backgroundColor: tokens.surface,
      insetPadding: const EdgeInsets.symmetric(horizontal: HqSpacing.xxl),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(context.hqBrand.dialogRadius ?? HqRadius.popup)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(HqSpacing.xl, HqSpacing.xxl, HqSpacing.xl, HqSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ExcludeSemantics(
              child: Container(
                width: 72,
                height: 72,
                decoration: BoxDecoration(color: accent.withValues(alpha: 0.12), shape: BoxShape.circle),
                child: Icon(icon, color: accent, size: 34),
              ),
            ),
            const SizedBox(height: HqSpacing.lg),
            Text(title, textAlign: TextAlign.center, style: text.titleLarge?.copyWith(color: tokens.ink)),
            if (body != null) ...[
              const SizedBox(height: HqSpacing.sm),
              Text(body, textAlign: TextAlign.center, style: text.bodyMedium?.copyWith(color: tokens.muted, height: 1.45)),
            ],
            const SizedBox(height: HqSpacing.xl),
            LayoutBuilder(builder: (context, constraints) {
              final confirmButton = HqButton(
                label: confirmLabel,
                color: tone == HqDialogTone.danger ? HqButtonColor.warn : HqButtonColor.primary,
                icon: confirmIcon,
                size: HqButtonSize.lg,
                block: true,
                onPressed: () => Navigator.of(context).pop(true),
              );
              final cancelLabel = cancel;
              if (cancelLabel == null) return confirmButton;
              final cancelButton = HqButton(
                label: cancelLabel,
                appearance: HqButtonAppearance.outline,
                color: HqButtonColor.secondary,
                size: HqButtonSize.lg,
                block: true,
                onPressed: () => Navigator.of(context).pop(false),
              );
              // Side by side only when both labels fit their half without being cut ("Delete Fo…"); a long label (or a
              // longer translation, or large text) stacks them full width, the main action first.
              final half = (constraints.maxWidth - HqSpacing.md) / 2;
              final fits = _labelFits(context, confirmLabel, half, hasIcon: confirmIcon != null) && _labelFits(context, cancelLabel, half);
              if (fits) {
                return Row(children: [
                  Expanded(child: cancelButton),
                  const SizedBox(width: HqSpacing.md),
                  Expanded(child: confirmButton),
                ]);
              }
              return Column(mainAxisSize: MainAxisSize.min, children: [
                confirmButton,
                const SizedBox(height: HqSpacing.sm),
                cancelButton,
              ]);
            }),
          ],
        ),
      ),
    );
  }

  /// Whether [label] fits on one line in a large button [width] wide (its 24 px side padding, plus an 18 px icon and gap
  /// when [hasIcon]), measured with the button's own text style and the user's text scale.
  static bool _labelFits(BuildContext context, String label, double width, {bool hasIcon = false}) {
    final style = Theme.of(context).textTheme.labelLarge?.copyWith(fontSize: 16, fontWeight: FontWeight.w600, letterSpacing: 0.2) ??
        const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, letterSpacing: 0.2);
    final painter = TextPainter(
      text: TextSpan(text: label, style: style),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final available = width - 2 * 24 - (hasIcon ? 18 + HqSpacing.sm : 0);
    final fits = painter.width <= available;
    painter.dispose();
    return fits;
  }
}
