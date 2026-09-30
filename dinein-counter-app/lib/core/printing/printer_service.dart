import 'dart:io';

import 'package:equatable/equatable.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'bill.dart';
import 'escpos.dart';

/// How bytes reach a printer. Network (TCP 9100) is built in; a POS terminal's built-in printer or a
/// Bluetooth printer is another implementation of this interface.
abstract class PrinterTransport {
  Future<void> send(List<int> bytes);
}

class NetworkPrinterTransport implements PrinterTransport {
  NetworkPrinterTransport({required this.host, this.port = 9100});

  final String host;
  final int port;

  @override
  Future<void> send(List<int> bytes) async {
    final socket = await Socket.connect(host, port, timeout: const Duration(seconds: 4));
    try {
      socket.add(bytes);
      await socket.flush();
    } finally {
      await socket.close();
      socket.destroy();
    }
  }
}

class PrinterSettings extends Equatable {
  const PrinterSettings({this.host = '', this.port = 9100, this.columns = 48});

  final String host;
  final int port;

  /// 32 for 58 mm paper, 48 for 80 mm (the usual counter printer).
  final int columns;

  bool get configured => host.trim().isNotEmpty;

  @override
  List<Object?> get props => [host, port, columns];
}

final printerSettingsProvider =
    StateNotifierProvider<PrinterSettingsNotifier, PrinterSettings>((ref) => PrinterSettingsNotifier());

class PrinterSettingsNotifier extends StateNotifier<PrinterSettings> {
  PrinterSettingsNotifier() : super(const PrinterSettings()) {
    _load();
  }

  static const _hostKey = 'counter.printer.host';
  static const _portKey = 'counter.printer.port';
  static const _columnsKey = 'counter.printer.columns';

  Future<void> _load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      state = PrinterSettings(
        host: prefs.getString(_hostKey) ?? '',
        port: prefs.getInt(_portKey) ?? 9100,
        columns: prefs.getInt(_columnsKey) ?? 48,
      );
    } catch (_) {
      // Keep the defaults: no printer.
    }
  }

  Future<void> save(PrinterSettings settings) async {
    state = settings;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_hostKey, settings.host.trim());
      await prefs.setInt(_portKey, settings.port);
      await prefs.setInt(_columnsKey, settings.columns);
    } catch (_) {
      // The in-memory value still applies until the app restarts.
    }
  }
}

/// Overridable so tests and vendor-SDK builds can supply their own transport.
final printerTransportProvider = Provider<PrinterTransport?>((ref) {
  final settings = ref.watch(printerSettingsProvider);
  if (!settings.configured) return null;
  return NetworkPrinterTransport(host: settings.host.trim(), port: settings.port);
});

enum PrintOutcome { printed, notConfigured, failed }

/// The last bill printed, so it can be reprinted.
final lastBillProvider = StateProvider<BillData?>((ref) => null);

final printCoordinatorProvider = Provider<PrintCoordinator>((ref) => PrintCoordinator(ref));

/// Printing never throws and never blocks a sale: a jammed printer must not stop the queue.
class PrintCoordinator {
  PrintCoordinator(this._ref);

  final Ref _ref;

  Future<PrintOutcome> printBill(BillData bill) async {
    _ref.read(lastBillProvider.notifier).state = bill;
    return _send(buildBillSlip(bill, columns: _ref.read(printerSettingsProvider).columns));
  }

  Future<PrintOutcome> reprintLast() async {
    final last = _ref.read(lastBillProvider);
    if (last == null) return PrintOutcome.failed;
    return _send(buildBillSlip(last, columns: _ref.read(printerSettingsProvider).columns));
  }

  Future<PrintOutcome> printTest() async {
    return _send(buildTestSlip(columns: _ref.read(printerSettingsProvider).columns, now: DateTime.now()));
  }

  Future<PrintOutcome> _send(List<int> bytes) async {
    final transport = _ref.read(printerTransportProvider);
    if (transport == null) return PrintOutcome.notConfigured;
    try {
      await transport.send(bytes);
      return PrintOutcome.printed;
    } catch (_) {
      return PrintOutcome.failed;
    }
  }
}
