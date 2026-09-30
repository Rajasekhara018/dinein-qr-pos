import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/session/session_provider.dart';
import '../branding/branding.dart';
import '../router/app_router.dart';

/// Returns the kiosk to the welcome page when nobody has touched it for the
/// configured time, after a short "Still there?" countdown.
class IdleGuard extends ConsumerStatefulWidget {
  const IdleGuard({super.key, required this.child});

  final Widget child;

  @override
  ConsumerState<IdleGuard> createState() => _IdleGuardState();
}

class _IdleGuardState extends ConsumerState<IdleGuard> {
  static const _countdownSeconds = 10;
  Timer? _timer;
  bool _dialogOpen = false;

  Duration get _timeout {
    final branding = ref.read(brandingProvider).valueOrNull ?? Branding.defaults;
    return Duration(seconds: branding.idleTimeoutSeconds);
  }

  @override
  void initState() {
    super.initState();
    _restart();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _restart() {
    if (_dialogOpen) return;
    _timer?.cancel();
    _timer = Timer(_timeout, _onIdle);
  }

  Future<void> _onIdle() async {
    final router = ref.read(appRouterProvider);
    final location = router.routerDelegate.currentConfiguration.uri.path;
    if (idleExemptRoutes.contains(location)) {
      _restart();
      return;
    }
    final context = rootNavigatorKey.currentContext;
    if (context == null) return;

    _dialogOpen = true;
    final stillHere = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const _StillThereDialog(seconds: _countdownSeconds),
    );
    _dialogOpen = false;

    if (stillHere != true) {
      ref.read(kioskSessionProvider).reset();
      router.go('/attract');
    }
    _restart();
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (_) => _restart(),
      child: widget.child,
    );
  }
}

class _StillThereDialog extends StatefulWidget {
  const _StillThereDialog({required this.seconds});

  final int seconds;

  @override
  State<_StillThereDialog> createState() => _StillThereDialogState();
}

class _StillThereDialogState extends State<_StillThereDialog> {
  late int _left = widget.seconds;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (_left <= 1) {
        t.cancel();
        if (mounted) Navigator.of(context).pop(false);
      } else {
        setState(() => _left--);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Still there?'),
      content: Text('Your order will be cleared in $_left seconds.'),
      actions: [
        FilledButton(
          onPressed: () => Navigator.of(context).pop(true),
          child: const Text('Yes, keep ordering'),
        ),
      ],
    );
  }
}
