import 'package:flutter/material.dart';

import '../services/hq_dialog.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_button.dart';
import '../ui/hq_field.dart';

/// Look of a [HqInlineConfirm] (the web `HqInlineConfirmVariant`).
enum HqInlineConfirmVariant { warning, danger, info }

/// A confirmation shown in place of a button row (no dialog): title, message, an optional reason field, Confirm and Cancel
/// (the counterpart of `hq-inline-confirm`). [onConfirmed] receives the reason (empty without [withReason]).
class HqInlineConfirm extends StatefulWidget {
  const HqInlineConfirm({
    super.key,
    required this.title,
    required this.confirmLabel,
    required this.cancelLabel,
    required this.onConfirmed,
    required this.onCancelled,
    this.message = '',
    this.variant = HqInlineConfirmVariant.warning,
    this.busy = false,
    this.withReason = false,
    this.reasonRequired = true,
    this.reasonLabel = '',
    this.reasonRequiredMessage = '',
    this.maxLength = 500,
    this.confirmDisabled = false,
  });

  final String title;
  final String message;
  final String confirmLabel;
  final String cancelLabel;
  final ValueChanged<String> onConfirmed;
  final VoidCallback onCancelled;
  final HqInlineConfirmVariant variant;
  final bool busy;
  final bool withReason;
  final bool reasonRequired;
  final String reasonLabel;
  final String reasonRequiredMessage;
  final int maxLength;
  final bool confirmDisabled;

  @override
  State<HqInlineConfirm> createState() => _HqInlineConfirmState();
}

class _HqInlineConfirmState extends State<HqInlineConfirm> {
  final TextEditingController _reason = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  void _confirm() {
    final reason = _reason.text.trim();
    if (widget.withReason && widget.reasonRequired && reason.isEmpty) {
      setState(() => _error = widget.reasonRequiredMessage);
      return;
    }
    widget.onConfirmed(reason);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final colors = switch (widget.variant) {
      HqInlineConfirmVariant.warning => tokens.toneWarning,
      HqInlineConfirmVariant.danger => tokens.toneDanger,
      HqInlineConfirmVariant.info => tokens.toneInfo,
    };
    return Semantics(
      container: true,
      liveRegion: true,
      child: DecoratedBox(
        decoration: BoxDecoration(color: colors.background, border: Border.all(color: colors.border), borderRadius: BorderRadius.circular(HqRadius.control)),
        child: Padding(
          padding: const EdgeInsets.all(HqSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(widget.title, style: text.titleSmall?.copyWith(color: colors.foreground, fontWeight: FontWeight.w700)),
              if (widget.message.isNotEmpty) Text(widget.message, style: text.bodyMedium?.copyWith(color: colors.foreground)),
              if (widget.withReason)
                Padding(
                  padding: const EdgeInsets.only(top: HqSpacing.sm),
                  child: HqField(
                    controller: _reason,
                    label: widget.reasonLabel,
                    required: widget.reasonRequired,
                    type: HqFieldType.multiline,
                    rows: 2,
                    maxLength: widget.maxLength,
                    error: _error,
                    onChanged: (_) {
                      if (_error != null) setState(() => _error = null);
                    },
                  ),
                ),
              const SizedBox(height: HqSpacing.md),
              Wrap(
                alignment: WrapAlignment.end,
                spacing: HqSpacing.sm,
                children: [
                  HqButton(label: widget.cancelLabel, appearance: HqButtonAppearance.text, size: HqButtonSize.sm, onPressed: widget.busy ? null : widget.onCancelled),
                  HqButton(
                    label: widget.confirmLabel,
                    size: HqButtonSize.sm,
                    color: widget.variant == HqInlineConfirmVariant.danger ? HqButtonColor.warn : HqButtonColor.primary,
                    loading: widget.busy,
                    onPressed: widget.confirmDisabled ? null : _confirm,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// The confirm dialog as a widget-style entry point: `HqConfirmDialog.show(...)`. Same as [HqDialog.confirm].
abstract final class HqConfirmDialog {
  static Future<bool> show(
    BuildContext context, {
    required String title,
    required String confirmLabel,
    required String cancelLabel,
    String? message,
    bool destructive = false,
  }) =>
      HqDialog.confirm(context, title: title, confirmLabel: confirmLabel, cancelLabel: cancelLabel, message: message, destructive: destructive);
}
