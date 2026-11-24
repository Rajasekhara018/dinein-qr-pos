import 'dart:io';

import 'package:equatable/equatable.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'escpos.dart';
import 'receipt.dart';

/// How bytes reach a printer. Network (TCP 9100) is built in; a vendor SDK for a
/// kiosk's built-in printer is another implementation of this interface.
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
  const PrinterSettings({this.host = '', this.port = 9100, this.columns = 32});

  final String host;
  final int port;

  /// 32 for 58 mm paper, 48 for 80 mm.
  final int columns;

  bool get configured => host.trim().isNotEmpty;

  PrinterSettings copyWith({String? host, int? port, int? columns}) => PrinterSettings(
        host: host ?? this.host,
        port: port ?? this.port,
        columns: columns ?? this.columns,
      );

  @override
  List<Object?> get props => [host, port, columns];
}

final printerSettingsProvider =
    StateNotifierProvider<PrinterSettingsNotifier, PrinterSettings>((ref) {
  return PrinterSettingsNotifier();
});

class PrinterSettingsNotifier extends StateNotifier<PrinterSettings> {
  PrinterSettingsNotifier() : super(const PrinterSettings()) {
    _load();
  }

  static const _hostKey = 'kiosk.printer.host';
  static const _portKey = 'kiosk.printer.port';
  static const _columnsKey = 'kiosk.printer.columns';

  Future<void> _load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      state = PrinterSettings(
        host: prefs.getString(_hostKey) ?? '',
        port: prefs.getInt(_portKey) ?? 9100,
        columns: prefs.getInt(_columnsKey) ?? 32,
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

/// The last slip that was ordered, kept in memory so staff can reprint it.
final lastReceiptProvider = StateProvider<ReceiptData?>((ref) => null);

/// Set when the slip for the order just placed could not be printed, so the
/// confirmation screen can tell the customer to remember their token.
final receiptPrintFailedProvider = StateProvider<bool>((ref) => false);

final printCoordinatorProvider = Provider<PrintCoordinator>((ref) => PrintCoordinator(ref));

/// Printing never throws and never blocks an order: a jammed printer must not stop sales.
class PrintCoordinator {
  PrintCoordinator(this._ref);

  final Ref _ref;

  Future<PrintOutcome> printTokenSlip(ReceiptData data) async {
    _ref.read(lastReceiptProvider.notifier).state = data;
    final columns = _ref.read(printerSettingsProvider).columns;
    return _send(buildTokenSlip(data, columns: columns));
  }

  Future<PrintOutcome> reprintLast() async {
    final last = _ref.read(lastReceiptProvider);
    if (last == null) return PrintOutcome.failed;
    final columns = _ref.read(printerSettingsProvider).columns;
    return _send(buildTokenSlip(last, columns: columns));
  }

  Future<PrintOutcome> printTest() async {
    final columns = _ref.read(printerSettingsProvider).columns;
    return _send(buildTestSlip(columns: columns, now: DateTime.now()));
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
