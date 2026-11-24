import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../theme/hq_mobile_finish.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_field.dart';

/// Switch sizes (the web `compact` / `regular`).
enum HqSwitchSize { compact, regular }

/// An on/off switch with a label (the counterpart of `hq-slide-toggle`). [loading] shows a spinner and blocks changes (a
/// setting being saved). The label is part of the tap target.
class HqSwitch extends StatelessWidget {
  const HqSwitch({super.key, required this.value, required this.onChanged, this.label = '', this.description = '', this.loading = false, this.enabled = true, this.labelBefore = false, this.size = HqSwitchSize.regular});

  final bool value;
  final ValueChanged<bool>? onChanged;
  final String label;
  final String description;
  final bool loading;
  final bool enabled;

  /// Label before the switch instead of after it.
  final bool labelBefore;
  final HqSwitchSize size;

  bool get _active => enabled && !loading && onChanged != null;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final control = loading
        ? const SizedBox(width: 24, height: 24, child: Padding(padding: EdgeInsets.all(2), child: CircularProgressIndicator(strokeWidth: 2)))
        : Transform.scale(
            scale: size == HqSwitchSize.compact ? 0.8 : 1,
            // The mobile finish: a native-feeling switch (iOS style on Apple platforms) with a white thumb on a soft
            // track, no outline, and a light haptic when flipped.
            child: Switch.adaptive(
              value: value,
              onChanged: _active ? (next) { HapticFeedback.selectionClick(); onChanged!(next); } : null,
              activeThumbColor: Colors.white,
              activeTrackColor: tokens.primary,
              inactiveTrackColor: HqMobile.trackFill(context),
              inactiveThumbColor: Colors.white,
              trackOutlineColor: const WidgetStatePropertyAll<Color>(Colors.transparent),
            ),
          );
    final labels = Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (label.isNotEmpty) Text(label, style: text.bodyLarge?.copyWith(fontWeight: FontWeight.w500, color: enabled ? tokens.ink : tokens.muted)),
          if (description.isNotEmpty) Text(description, style: text.bodySmall?.copyWith(color: tokens.muted)),
        ],
      ),
    );
    return Semantics(
      container: true,
      toggled: value,
      enabled: _active,
      label: label,
      excludeSemantics: true,
      onTap: _active ? () => onChanged!(!value) : null,
      child: InkWell(
        borderRadius: BorderRadius.circular(HqRadius.sm),
        onTap: _active ? () => onChanged!(!value) : null,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: HqSizes.minTapTarget),
          child: Row(children: labelBefore ? [labels, const SizedBox(width: HqSpacing.md), control] : [control, const SizedBox(width: HqSpacing.md), labels]),
        ),
      ),
    );
  }
}

/// A multi-line text field (a [HqField] with `multiline`): grows with the text up to [maxRows], optional counter.
class HqTextArea extends StatelessWidget {
  const HqTextArea({super.key, this.controller, this.value, this.onChanged, this.label = '', this.required = false, this.placeholder, this.hint, this.error, this.maxLength, this.showCounter = true, this.minRows = 3, this.maxRows = 8, this.enabled = true});

  final TextEditingController? controller;
  final String? value;
  final ValueChanged<String>? onChanged;
  final String label;
  final bool required;
  final String? placeholder;
  final String? hint;
  final String? error;
  final int? maxLength;
  final bool showCounter;
  final int minRows;
  final int maxRows;
  final bool enabled;

  @override
  Widget build(BuildContext context) => HqField(
        controller: controller,
        value: value,
        onChanged: onChanged,
        label: label,
        required: required,
        placeholder: placeholder,
        hint: hint,
        error: error,
        maxLength: maxLength,
        showCounter: showCounter && maxLength != null,
        rows: minRows,
        maxRows: maxRows,
        enabled: enabled,
        type: HqFieldType.multiline,
      );
}

/// A search box with a clear button and an optional debounce (the list search of a screen). [onSearch] fires after the user
/// pauses for [debounce] (zero fires on every keystroke) and on the keyboard's search key.
class HqSearchBar extends StatefulWidget {
  const HqSearchBar({super.key, required this.onSearch, this.placeholder = '', this.clearLabel = '', this.initialValue = '', this.debounce = const Duration(milliseconds: 350), this.autofocus = false});

  final ValueChanged<String> onSearch;
  final String placeholder;
  final String clearLabel;
  final String initialValue;
  final Duration debounce;
  final bool autofocus;

  @override
  State<HqSearchBar> createState() => _HqSearchBarState();
}

class _HqSearchBarState extends State<HqSearchBar> {
  late final TextEditingController _controller = TextEditingController(text: widget.initialValue);
  int _ticket = 0;

  @override
  void dispose() {
    _ticket++;
    _controller.dispose();
    super.dispose();
  }

  void _schedule(String text) {
    setState(() {});
    if (widget.debounce == Duration.zero) {
      widget.onSearch(text);
      return;
    }
    final ticket = ++_ticket;
    Future<void>.delayed(widget.debounce, () {
      if (mounted && ticket == _ticket) widget.onSearch(text);
    });
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    return HqField(
      controller: _controller,
      showLabel: false,
      autofocus: widget.autofocus,
      icon: Icons.search,
      placeholder: widget.placeholder,
      textInputAction: TextInputAction.search,
      suffix: _controller.text.isEmpty
          ? null
          : InkResponse(
              onTap: () {
                _controller.clear();
                _ticket++;
                setState(() {});
                widget.onSearch('');
              },
              child: Semantics(button: true, label: widget.clearLabel, child: Icon(Icons.close, size: 18, color: tokens.muted)),
            ),
      onChanged: _schedule,
      onSubmitted: (t) {
        _ticket++;
        widget.onSearch(t);
      },
    );
  }
}

/// A read-only value with a copy button (a referral code, a UPI id, an escrow account reference). [onCopied] lets the app
/// show a toast. Do not use it for secrets.
class HqCopyField extends StatelessWidget {
  const HqCopyField({super.key, required this.value, this.label = '', this.copyLabel = '', this.onCopied});

  final String value;
  final String label;
  final String copyLabel;
  final VoidCallback? onCopied;

  @override
  Widget build(BuildContext context) {
    return HqField(
      value: value,
      label: label,
      readOnly: true,
      suffix: InkResponse(
        onTap: () async {
          await Clipboard.setData(ClipboardData(text: value));
          onCopied?.call();
        },
        child: Semantics(button: true, label: copyLabel, child: Icon(Icons.copy, size: 20, color: context.hq.muted)),
      ),
    );
  }
}

/// A QR code for [data] (a payment link, a referral). It is drawn black on white whatever the theme, because scanners need
/// the contrast. [semanticsLabel] describes it for screen readers; never put a secret in it.
class HqQrCode extends StatelessWidget {
  const HqQrCode({super.key, required this.data, this.size = 180, this.semanticsLabel = ''});

  final String data;
  final double size;
  final String semanticsLabel;

  @override
  Widget build(BuildContext context) => Semantics(
        image: true,
        label: semanticsLabel,
        child: ExcludeSemantics(
          child: Container(
            padding: const EdgeInsets.all(HqSpacing.sm),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(HqRadius.sm)),
            child: QrImageView(data: data, size: size, backgroundColor: Colors.white, errorCorrectionLevel: QrErrorCorrectLevel.M),
          ),
        ),
      );
}
