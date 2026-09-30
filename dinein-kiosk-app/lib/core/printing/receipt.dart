import 'package:equatable/equatable.dart';

class ReceiptLine extends Equatable {
  const ReceiptLine({
    required this.name,
    required this.quantity,
    this.details = const [],
  });

  final String name;
  final int quantity;

  /// Sub-lines: chosen size, extras ("+ Extra cheese") and the customer's note.
  final List<String> details;

  @override
  List<Object?> get props => [name, quantity, details];
}

/// Everything printed on the customer's token slip. Built from the server's answer
/// (token, total) plus what the customer ordered, so the slip matches what they will pay.
class ReceiptData extends Equatable {
  const ReceiptData({
    required this.restaurantName,
    required this.tokenNumber,
    required this.orderTypeLabel,
    required this.lines,
    required this.total,
    required this.printedAt,
    this.paymentNote = 'Please pay at the counter',
    this.footer = 'Thank you!',
  });

  final String restaurantName;
  final int tokenNumber;
  final String orderTypeLabel;
  final List<ReceiptLine> lines;
  final double total;
  final DateTime printedAt;
  final String paymentNote;
  final String footer;

  @override
  List<Object?> get props => [
        restaurantName,
        tokenNumber,
        orderTypeLabel,
        lines,
        total,
        printedAt,
        paymentNote,
        footer,
      ];
}
