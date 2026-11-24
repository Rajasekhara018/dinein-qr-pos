import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/app_config.dart';
import '../../../core/lockdown/kiosk_lockdown.dart';
import '../../../core/printing/printer_service.dart';
import '../../../core/router/app_router.dart';
import '../../pairing/providers/device_provider.dart';
import '../../session/session_provider.dart';
import '../data/staff_repository.dart';

/// An invisible hot corner: hold the top-left of any screen to open the staff PIN prompt.
/// Customers never see it, so there is nothing for them to tap by accident.
class StaffAccessLayer extends ConsumerWidget {
  const StaffAccessLayer({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final paired = ref.watch(deviceProvider.select((d) => d.paired));
    return Stack(
      children: [
        child,
        if (paired)
          Positioned(
            top: 0,
            left: 0,
            width: 96,
            height: 96,
            child: GestureDetector(
              behavior: HitTestBehavior.translucent,
              onLongPress: () => _openUnlock(ref),
            ),
          ),
      ],
    );
  }

  Future<void> _openUnlock(WidgetRef ref) async {
    final context = rootNavigatorKey.currentContext;
    if (context == null || ref.read(staffMenuOpenProvider)) return;
    ref.read(staffMenuOpenProvider.notifier).state = true;
    try {
      final identity = await showDialog<StaffIdentity>(
        context: context,
        barrierDismissible: false,
        builder: (_) => const _UnlockDialog(),
      );
      if (identity != null && context.mounted) {
        await showDialog<void>(
          context: context,
          barrierDismissible: false,
          builder: (_) => _ServiceMenu(identity: identity),
        );
      }
    } finally {
      ref.read(staffMenuOpenProvider.notifier).state = false;
    }
  }
}

class _UnlockDialog extends ConsumerStatefulWidget {
  const _UnlockDialog();

  @override
  ConsumerState<_UnlockDialog> createState() => _UnlockDialogState();
}

class _UnlockDialogState extends ConsumerState<_UnlockDialog> {
  final _username = TextEditingController();
  final _pin = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _username.dispose();
    _pin.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy || _username.text.trim().isEmpty || _pin.text.length < 4) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final identity = await ref
          .read(staffRepositoryProvider)
          .unlock(_username.text, _pin.text);
      if (mounted) Navigator.of(context).pop(identity);
    } catch (e) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = '$e'.replaceFirst('Exception: ', '');
          _pin.clear();
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Staff access'),
      content: SizedBox(
        width: 360,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _username,
              autofocus: true,
              enabled: !_busy,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(labelText: 'Username'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _pin,
              enabled: !_busy,
              obscureText: true,
              keyboardType: TextInputType.number,
              inputFormatters: [
                FilteringTextInputFormatter.digitsOnly,
                LengthLimitingTextInputFormatter(8),
              ],
              decoration: InputDecoration(
                labelText: 'PIN',
                errorText: _error,
                helperText: AppConfig.demoMode ? 'Demo mode: PIN $demoStaffPin' : null,
              ),
              onSubmitted: (_) => _submit(),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _busy ? null : () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: _busy ? null : _submit,
          child: _busy
              ? const SizedBox(
                  width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Unlock'),
        ),
      ],
    );
  }
}

class _ServiceMenu extends ConsumerStatefulWidget {
  const _ServiceMenu({required this.identity});

  final StaffIdentity identity;

  @override
  ConsumerState<_ServiceMenu> createState() => _ServiceMenuState();
}

class _ServiceMenuState extends ConsumerState<_ServiceMenu> {
  String? _status;

  void _say(String message) => setState(() => _status = message);

  String _describe(PrintOutcome outcome) => switch (outcome) {
        PrintOutcome.printed => 'Sent to the printer.',
        PrintOutcome.notConfigured => 'No printer is set up. Open Printer settings first.',
        PrintOutcome.failed => 'Could not reach the printer. Check power, paper and the network address.',
      };

  Future<void> _reprint() async {
    if (ref.read(lastReceiptProvider) == null) {
      _say('There is no receipt to reprint yet.');
      return;
    }
    _say(_describe(await ref.read(printCoordinatorProvider).reprintLast()));
  }

  Future<void> _testPrint() async =>
      _say(_describe(await ref.read(printCoordinatorProvider).printTest()));

  Future<void> _backToWelcome() async {
    ref.read(kioskSessionProvider).reset();
    ref.read(appRouterProvider).go('/attract');
    Navigator.of(context).pop();
  }

  Future<void> _exitKioskMode() async {
    final ok = await _confirm(
      'Exit kiosk mode?',
      'The kiosk lock is lifted so you can leave the app. It locks again next time the app opens.',
      'Exit kiosk mode',
    );
    if (ok != true) return;
    await ref.read(kioskLockdownProvider).exit();
    _say('Kiosk lock lifted. Use the system navigation to leave the app.');
  }

  Future<void> _unpair() async {
    final ok = await _confirm(
      'Unpair this kiosk?',
      'The kiosk will forget its restaurant and need a new pairing code from Admin.',
      'Unpair',
    );
    if (ok != true) return;
    await ref.read(deviceProvider.notifier).unpair();
    if (mounted) Navigator.of(context).pop();
  }

  Future<bool?> _confirm(String title, String body, String action) {
    return showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(body),
        actions: [
          TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.of(context).pop(true), child: Text(action)),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    Widget action(IconData icon, String label, VoidCallback onTap) => ListTile(
          leading: Icon(icon),
          title: Text(label),
          onTap: onTap,
          minVerticalPadding: 12,
        );

    return AlertDialog(
      title: Text('Service menu  •  ${widget.identity.displayName}'),
      content: SizedBox(
        width: 420,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            action(Icons.home_rounded, 'Back to welcome screen', _backToWelcome),
            action(Icons.receipt_long_rounded, 'Reprint last receipt', _reprint),
            action(Icons.print_rounded, 'Print test slip', _testPrint),
            action(Icons.settings_ethernet_rounded, 'Printer settings', () async {
              await showDialog<void>(
                  context: context, builder: (_) => const _PrinterSettingsDialog());
            }),
            action(Icons.lock_open_rounded, 'Exit kiosk mode', _exitKioskMode),
            action(Icons.link_off_rounded, 'Unpair this kiosk', _unpair),
            if (_status != null) ...[
              const Divider(),
              Text(_status!, style: Theme.of(context).textTheme.bodyMedium),
            ],
          ],
        ),
      ),
      actions: [
        FilledButton(
            onPressed: () => Navigator.of(context).pop(), child: const Text('Close')),
      ],
    );
  }
}

class _PrinterSettingsDialog extends ConsumerStatefulWidget {
  const _PrinterSettingsDialog();

  @override
  ConsumerState<_PrinterSettingsDialog> createState() => _PrinterSettingsDialogState();
}

class _PrinterSettingsDialogState extends ConsumerState<_PrinterSettingsDialog> {
  late final TextEditingController _host;
  late final TextEditingController _port;
  late int _columns;

  @override
  void initState() {
    super.initState();
    final current = ref.read(printerSettingsProvider);
    _host = TextEditingController(text: current.host);
    _port = TextEditingController(text: '${current.port}');
    _columns = current.columns;
  }

  @override
  void dispose() {
    _host.dispose();
    _port.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    await ref.read(printerSettingsProvider.notifier).save(
          PrinterSettings(
            host: _host.text.trim(),
            port: int.tryParse(_port.text) ?? 9100,
            columns: _columns,
          ),
        );
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Printer settings'),
      content: SizedBox(
        width: 380,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _host,
              autofocus: true,
              keyboardType: TextInputType.url,
              decoration: const InputDecoration(
                labelText: 'Printer address',
                helperText: 'e.g. 192.168.1.50. Leave empty for no printer.',
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _port,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(labelText: 'Port (usually 9100)'),
            ),
            const SizedBox(height: 16),
            SegmentedButton<int>(
              segments: const [
                ButtonSegment(value: 32, label: Text('58 mm')),
                ButtonSegment(value: 48, label: Text('80 mm')),
              ],
              selected: {_columns},
              onSelectionChanged: (s) => setState(() => _columns = s.first),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
        FilledButton(onPressed: _save, child: const Text('Save')),
      ],
    );
  }
}
