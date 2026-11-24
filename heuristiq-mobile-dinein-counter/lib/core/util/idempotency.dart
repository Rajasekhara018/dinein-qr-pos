import 'dart:math';

/// One key per attempt at taking a payment. A retry after a timeout reuses it, so the backend returns the
/// original order instead of creating a second one.
String newIdempotencyKey() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  return bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
}
