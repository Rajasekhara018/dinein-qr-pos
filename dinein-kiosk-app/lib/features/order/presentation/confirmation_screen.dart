import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/printing/printer_service.dart';
import '../../../core/widgets/kiosk_widgets.dart';
import '../../../l10n/app_localizations.dart';
import '../data/order_repository.dart';

class ConfirmationScreen extends ConsumerStatefulWidget {
  const ConfirmationScreen({super.key, required this.result});

  final OrderResult result;

  @override
  ConsumerState<ConfirmationScreen> createState() => _ConfirmationScreenState();
}

class _ConfirmationScreenState extends ConsumerState<ConfirmationScreen> {
  static const _seconds = 15;
  int _left = _seconds;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (_left <= 1) {
        t.cancel();
        if (mounted) context.go('/attract');
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
    final l10n = AppLocalizations.of(context);
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final printFailed = ref.watch(receiptPrintFailedProvider);
    return Scaffold(
      backgroundColor: scheme.primary,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.check_circle_rounded, size: 96, color: Colors.white),
                const SizedBox(height: 16),
                Text(l10n.orderPlaced,
                    textAlign: TextAlign.center,
                    style: text.displaySmall
                        ?.copyWith(color: Colors.white, fontWeight: FontWeight.w800)),
                const SizedBox(height: 32),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 64, vertical: 32),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(32),
                  ),
                  child: Column(
                    children: [
                      Text(l10n.yourToken, style: text.titleLarge),
                      Text(
                        '${widget.result.tokenNumber}',
                        style: text.displayLarge?.copyWith(
                            fontSize: 120,
                            fontWeight: FontWeight.w900,
                            color: scheme.primary),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                Text(
                  l10n.payAtCounterAmount(formatPriceExact(widget.result.total)),
                  textAlign: TextAlign.center,
                  style: text.headlineSmall?.copyWith(color: Colors.white),
                ),
                const SizedBox(height: 8),
                Text(l10n.weWillCall,
                    textAlign: TextAlign.center,
                    style: text.titleMedium?.copyWith(color: Colors.white70)),
                if (printFailed) ...[
                  const SizedBox(height: 12),
                  Text(l10n.receiptNotPrinted,
                      textAlign: TextAlign.center,
                      style: text.titleMedium?.copyWith(
                          color: Colors.white, fontWeight: FontWeight.w700)),
                ],
                const SizedBox(height: 32),
                OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.white,
                    side: const BorderSide(color: Colors.white, width: 2),
                  ),
                  onPressed: () => context.go('/attract'),
                  child: Text(l10n.doneWithSeconds(_left)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
