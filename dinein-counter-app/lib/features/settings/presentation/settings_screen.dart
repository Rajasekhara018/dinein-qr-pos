import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_provider.dart';
import '../../../core/printing/printer_service.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  late final TextEditingController _host;
  late final TextEditingController _port;
  late int _columns;
  String? _status;

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
    await ref.read(printerSettingsProvider.notifier).save(PrinterSettings(
          host: _host.text.trim(),
          port: int.tryParse(_port.text) ?? 9100,
          columns: _columns,
        ));
    setState(() => _status = 'Printer settings saved.');
  }

  String _describe(PrintOutcome outcome) => switch (outcome) {
        PrintOutcome.printed => 'Sent to the printer.',
        PrintOutcome.notConfigured => 'No printer is set up. Enter its address and save first.',
        PrintOutcome.failed => 'Could not reach the printer. Check power, paper and the network address.',
      };

  Future<void> _testPrint() async {
    await _save();
    final outcome = await ref.read(printCoordinatorProvider).printTest();
    if (mounted) setState(() => _status = _describe(outcome));
  }

  Future<void> _reprint() async {
    if (ref.read(lastBillProvider) == null) {
      setState(() => _status = 'There is no bill to reprint yet.');
      return;
    }
    final outcome = await ref.read(printCoordinatorProvider).reprintLast();
    if (mounted) setState(() => _status = _describe(outcome));
  }

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(sessionProvider).user;
    final text = Theme.of(context).textTheme;
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Text('Signed in', style: text.titleLarge),
        const SizedBox(height: 4),
        Text('${user?.displayName ?? ''}  •  ${user?.role ?? ''}'),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton.icon(
            onPressed: () => ref.read(sessionProvider.notifier).signOut(),
            icon: const Icon(Icons.logout_rounded),
            label: const Text('Sign out'),
          ),
        ),
        const Divider(height: 40),
        Text('Bill printer', style: text.titleLarge),
        const SizedBox(height: 12),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 460),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TextField(
                controller: _host,
                keyboardType: TextInputType.url,
                decoration: const InputDecoration(
                  labelText: 'Printer address',
                  helperText: 'e.g. 192.168.1.50. Leave empty for no printer.',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _port,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                decoration: const InputDecoration(
                    labelText: 'Port (usually 9100)', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              SegmentedButton<int>(
                segments: const [
                  ButtonSegment(value: 32, label: Text('58 mm')),
                  ButtonSegment(value: 48, label: Text('80 mm')),
                ],
                selected: {_columns},
                onSelectionChanged: (s) => setState(() => _columns = s.first),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  FilledButton(onPressed: _save, child: const Text('Save')),
                  OutlinedButton(onPressed: _testPrint, child: const Text('Print test slip')),
                  OutlinedButton(onPressed: _reprint, child: const Text('Reprint last bill')),
                ],
              ),
              if (_status != null) ...[
                const SizedBox(height: 12),
                Text(_status!, style: text.bodyLarge),
              ],
            ],
          ),
        ),
      ],
    );
  }
}
