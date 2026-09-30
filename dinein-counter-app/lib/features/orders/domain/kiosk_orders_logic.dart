import 'order_models.dart';

/// Orders whose token number starts with what the cashier typed. "1" shows 1, 10-19, 100-199..., so the
/// list narrows as they key in a customer's token and a full token leaves exactly that order.
List<KioskOrder> filterByToken(List<KioskOrder> orders, String query) {
  final digits = query.replaceAll(RegExp(r'\D'), '');
  if (digits.isEmpty) return orders;
  return orders.where((o) => '${o.displayToken}'.startsWith(digits)).toList();
}

/// "just now", "5 min", "1 h 10 min": how long a customer has been waiting to pay.
String waitingFor(DateTime placedAt, DateTime now) {
  final minutes = now.difference(placedAt).inMinutes;
  if (minutes < 1) return 'just now';
  if (minutes < 60) return '$minutes min';
  final hours = minutes ~/ 60;
  final rest = minutes % 60;
  return rest == 0 ? '$hours h' : '$hours h $rest min';
}
