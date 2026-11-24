import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../auth/hq_sms_code_reader.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_button.dart';

/// Shared engine of [HqOtpInput] and [HqPinInput]: one hidden text field (so paste, one-time-code autofill and the
/// keyboard all work) drawn as [length] boxes.
class _CodeBoxes extends StatefulWidget {
  const _CodeBoxes({
    super.key,
    required this.length,
    required this.onChanged,
    required this.onCompleted,
    required this.obscure,
    required this.enabled,
    required this.error,
    required this.autofocus,
    required this.semanticsLabel,
    required this.autofillHint,
  });

  final int length;
  final ValueChanged<String> onChanged;
  final ValueChanged<String>? onCompleted;
  final bool obscure;
  final bool enabled;
  final bool error;
  final bool autofocus;
  final String semanticsLabel;
  final String? autofillHint;

  @override
  State<_CodeBoxes> createState() => _CodeBoxesState();
}

class _CodeBoxesState extends State<_CodeBoxes> {
  final TextEditingController _controller = TextEditingController();
  final FocusNode _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    _controller.addListener(() => setState(() {}));
    _focus.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _changed(String text) {
    widget.onChanged(text);
    if (text.length == widget.length) widget.onCompleted?.call(text);
  }

  /// Clears the entered code (after a wrong code).
  void clear() => _controller.clear();

  /// Puts a code read from an SMS into the boxes as if typed (digits only, cut to [length]).
  void fill(String code) {
    final digits = code.replaceAll(RegExp(r'\D'), '');
    final value = digits.length > widget.length ? digits.substring(0, widget.length) : digits;
    _controller.value = TextEditingValue(text: value, selection: TextSelection.collapsed(offset: value.length));
    _changed(value);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final code = _controller.text;
    return Semantics(
      textField: true,
      label: widget.semanticsLabel,
      obscured: widget.obscure,
      child: Stack(
        children: [
          // The real input: invisible, covers the boxes so a tap focuses it.
          Positioned.fill(
            child: Opacity(
              opacity: 0,
              child: TextField(
                controller: _controller,
                focusNode: _focus,
                autofocus: widget.autofocus,
                enabled: widget.enabled,
                keyboardType: TextInputType.number,
                autofillHints: widget.autofillHint == null ? null : [widget.autofillHint!],
                enableInteractiveSelection: false,
                showCursor: false,
                maxLength: widget.length,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                obscureText: widget.obscure,
                decoration: const InputDecoration(counterText: '', border: InputBorder.none),
                onChanged: _changed,
              ),
            ),
          ),
          IgnorePointer(
            child: ExcludeSemantics(
              child: Directionality(
                textDirection: TextDirection.ltr,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    for (var i = 0; i < widget.length; i++)
                      Container(
                        key: ValueKey<String>('hq-code-box-$i'),
                        width: 44,
                        height: 52,
                        margin: const EdgeInsets.symmetric(horizontal: 4),
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          color: tokens.surface,
                          borderRadius: BorderRadius.circular(HqRadius.control),
                          border: Border.all(color: widget.error ? tokens.warn : (_focus.hasFocus && i == code.length.clamp(0, widget.length - 1) ? tokens.primary : tokens.fieldBorder), width: _focus.hasFocus && i == code.length.clamp(0, widget.length - 1) ? 2 : 1),
                        ),
                        child: Text(i < code.length ? (widget.obscure ? '•' : code[i]) : '', style: text.headlineSmall?.copyWith(fontWeight: FontWeight.w700)),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// A one-time-password entry: [length] digit boxes with paste and SMS autofill, an error line and a resend countdown
/// (the OTP is hashed server side; this widget only collects it and never stores it).
///
/// [onCompleted] fires when all digits are in. After a wrong code change [clearToken] to empty the boxes.
///
/// Auto-read: on iOS the keyboard offers the code from Messages by itself (the field sets `AutofillHints.oneTimeCode`).
/// On Android pass [smsReader] (`HqAndroidSmsCodeReader`): it starts listening when the field appears, again after each
/// resend, and stops when the field goes away. A code that arrives fills the boxes and fires [onCompleted]; the user can
/// always type instead.
class HqOtpInput extends StatefulWidget {
  const HqOtpInput({
    super.key,
    required this.onCompleted,
    this.length = 6,
    this.onChanged,
    this.error,
    this.enabled = true,
    this.autofocus = true,
    this.semanticsLabel = '',
    this.resendSeconds = 30,
    this.onResend,
    this.onResendAsync,
    this.resendLabel = '',
    this.resendCountdown,
    this.clearToken = 0,
    this.smsReader,
  });

  final int length;
  final ValueChanged<String> onCompleted;

  /// Reads the code from the incoming SMS (Android). Null turns auto-read off.
  final HqSmsCodeReader? smsReader;
  final ValueChanged<String>? onChanged;

  /// Message under the boxes (already translated), which also turns them red.
  final String? error;
  final bool enabled;
  final bool autofocus;
  final String semanticsLabel;

  /// Seconds before "resend" is offered; zero hides the resend row.
  final int resendSeconds;
  final VoidCallback? onResend;

  /// Like [onResend], for a resend that can fail: the button shows a spinner while it runs and the countdown restarts
  /// only when it completes with true (a failed resend leaves "resend" on offer). Used instead of [onResend] when given.
  final Future<bool> Function()? onResendAsync;

  /// Text of the resend button, e.g. "Resend code".
  final String resendLabel;

  /// Text while counting down, e.g. `(s) => 'Resend in ${s}s'`.
  final String Function(int secondsLeft)? resendCountdown;

  /// Change this number to clear the entered digits.
  final int clearToken;

  @override
  State<HqOtpInput> createState() => _HqOtpInputState();
}

class _HqOtpInputState extends State<HqOtpInput> {
  final GlobalKey<_CodeBoxesState> _boxes = GlobalKey<_CodeBoxesState>();
  Timer? _timer;
  late int _left = widget.resendSeconds;
  bool _resending = false;

  int _readTicket = 0;

  Future<void> _resend() async {
    final asyncResend = widget.onResendAsync;
    if (asyncResend == null) {
      widget.onResend!();
      setState(_startTimer);
      unawaited(_restartSmsRead());
      return;
    }
    if (_resending) return;
    setState(() => _resending = true);
    bool sent;
    try {
      sent = await asyncResend();
    } on Object {
      sent = false;
    }
    if (!mounted) return;
    setState(() {
      _resending = false;
      if (sent) _startTimer();
    });
    if (sent) unawaited(_restartSmsRead());
  }

  @override
  void initState() {
    super.initState();
    _startTimer();
    _listenForSms();
  }

  /// Starts one SMS read; a result from an older read (before a resend, or after dispose) is ignored.
  Future<void> _listenForSms() async {
    final reader = widget.smsReader;
    if (reader == null || !widget.enabled) return;
    final ticket = ++_readTicket;
    final code = await reader.read(length: widget.length);
    if (!mounted || ticket != _readTicket || code == null) return;
    _boxes.currentState?.fill(code);
  }

  Future<void> _restartSmsRead() async {
    final reader = widget.smsReader;
    if (reader == null) return;
    _readTicket++;
    await reader.cancel();
    if (mounted) await _listenForSms();
  }

  void _startTimer() {
    _timer?.cancel();
    _left = widget.resendSeconds;
    if (_left <= 0) return;
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (!mounted) return;
      setState(() => _left -= 1);
      if (_left <= 0) t.cancel();
    });
  }

  @override
  void didUpdateWidget(HqOtpInput oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.clearToken != widget.clearToken) _boxes.currentState?.clear();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _readTicket++;
    widget.smsReader?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final hasError = widget.error != null && widget.error!.isNotEmpty;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _CodeBoxes(
          key: _boxes,
          length: widget.length,
          onChanged: widget.onChanged ?? (_) {},
          onCompleted: widget.onCompleted,
          obscure: false,
          enabled: widget.enabled,
          error: hasError,
          autofocus: widget.autofocus,
          semanticsLabel: widget.semanticsLabel,
          autofillHint: AutofillHints.oneTimeCode,
        ),
        if (hasError) Padding(padding: const EdgeInsets.only(top: HqSpacing.sm), child: Semantics(liveRegion: true, child: Text(widget.error!, style: text.bodySmall?.copyWith(color: tokens.warn)))),
        if (widget.resendSeconds > 0 && (widget.onResend != null || widget.onResendAsync != null))
          Padding(
            padding: const EdgeInsets.only(top: HqSpacing.md),
            child: _left > 0
                ? Text(widget.resendCountdown?.call(_left) ?? '$_left', style: text.bodySmall?.copyWith(color: tokens.muted))
                : HqButton(
                    label: widget.resendLabel,
                    appearance: HqButtonAppearance.text,
                    size: HqButtonSize.sm,
                    loading: _resending,
                    onPressed: _resend,
                  ),
          ),
      ],
    );
  }
}

/// A PIN entry: [length] boxes that show dots, for an app PIN or a transaction PIN. Same engine as [HqOtpInput].
class HqPinInput extends StatelessWidget {
  const HqPinInput({super.key, required this.onCompleted, this.length = 4, this.onChanged, this.error, this.enabled = true, this.autofocus = true, this.semanticsLabel = ''});

  final int length;
  final ValueChanged<String> onCompleted;
  final ValueChanged<String>? onChanged;
  final String? error;
  final bool enabled;
  final bool autofocus;
  final String semanticsLabel;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final hasError = error != null && error!.isNotEmpty;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _CodeBoxes(length: length, onChanged: onChanged ?? (_) {}, onCompleted: onCompleted, obscure: true, enabled: enabled, error: hasError, autofocus: autofocus, semanticsLabel: semanticsLabel, autofillHint: null),
        if (hasError) Padding(padding: const EdgeInsets.only(top: HqSpacing.sm), child: Semantics(liveRegion: true, child: Text(error!, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: tokens.warn)))),
      ],
    );
  }
}
