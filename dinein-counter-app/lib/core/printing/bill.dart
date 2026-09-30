import 'package:equatable/equatable.dart';

class BillLine extends Equatable {
  const BillLine({required this.name, required this.quantity, this.details = const []});

  final String name;
  final int quantity;

  /// Chosen size, extras ("+ Extra cheese") and the note.
  final List<String> details;

  @override
  List<Object?> get props => [name, quantity, details];
}

/// What is printed for a customer once they have paid at the counter.
class BillData extends Equatable {
  const BillData({
    required this.restaurantName,
    required this.orderNumber,
    required this.tokenNumber,
    required this.orderTypeLabel,
    required this.lines,
    required this.total,
    required this.paymentLabel,
    required this.printedAt,
    this.cashier = '',
    this.footer = 'Thank you!',
  });

  final String restaurantName;
  final String orderNumber;
  final int tokenNumber;
  final String orderTypeLabel;
  final List<BillLine> lines;
  final double total;
  final String paymentLabel;
  final DateTime printedAt;
  final String cashier;
  final String footer;

  @override
  List<Object?> get props => [
        restaurantName,
        orderNumber,
        tokenNumber,
        orderTypeLabel,
        lines,
        total,
        paymentLabel,
        printedAt,
        cashier,
        footer,
      ];
}
