import 'receipt.dart';

/// Builds ESC/POS bytes for a thermal receipt printer. Kept free of any I/O so it can be unit tested.
///
/// Text is limited to printable ASCII: thermal printers ship with different code pages, so
/// anything else (including the rupee sign) is replaced rather than risking garbled output.
class EscPosBuilder {
  EscPosBuilder({required this.columns});

  /// Characters per line at normal size: 32 for 58 mm paper, 48 for 80 mm.
  final int columns;

  final List<int> _bytes = [];

  List<int> get bytes => List.unmodifiable(_bytes);

  static const _esc = 0x1B;
  static const _gs = 0x1D;

  EscPosBuilder init() {
    _bytes.addAll([_esc, 0x40]);
    return this;
  }

  EscPosBuilder align(int mode) {
    // 0 left, 1 centre, 2 right
    _bytes.addAll([_esc, 0x61, mode]);
    return this;
  }

  EscPosBuilder bold(bool on) {
    _bytes.addAll([_esc, 0x45, on ? 1 : 0]);
    return this;
  }

  /// [width] and [height] are multipliers from 1 to 8.
  EscPosBuilder size({int width = 1, int height = 1}) {
    final w = (width.clamp(1, 8) - 1) << 4;
    final h = height.clamp(1, 8) - 1;
    _bytes.addAll([_gs, 0x21, w | h]);
    return this;
  }

  EscPosBuilder text(String value) {
    _bytes.addAll(sanitize(value).codeUnits);
    return this;
  }

  EscPosBuilder line([String value = '']) {
    text(value);
    _bytes.add(0x0A);
    return this;
  }

  EscPosBuilder divider() => line('-' * columns);

  /// Left text and right text on one line, the right one flush to the edge.
  EscPosBuilder columnsLine(String left, String right) {
    final r = sanitize(right);
    final room = columns - r.length - 1;
    final wrapped = wrap(left, room < 4 ? columns : room);
    for (var i = 0; i < wrapped.length; i++) {
      final isLast = i == wrapped.length - 1;
      if (isLast && room >= 4) {
        line(wrapped[i].padRight(columns - r.length) + r);
      } else {
        line(wrapped[i]);
        if (isLast) line(r.padLeft(columns));
      }
    }
    return this;
  }

  EscPosBuilder feed(int lines) {
    _bytes.addAll([_esc, 0x64, lines]);
    return this;
  }

  EscPosBuilder cut() {
    // Feed to the cutter, then partial cut.
    _bytes.addAll([_gs, 0x56, 0x42, 0x00]);
    return this;
  }

  static String sanitize(String value) {
    final buffer = StringBuffer();
    for (final rune in value.replaceAll('₹', 'Rs.').runes) {
      buffer.write(rune >= 0x20 && rune <= 0x7E ? String.fromCharCode(rune) : '?');
    }
    return buffer.toString();
  }

  /// Word-wraps to [width], hard-splitting a single word that is longer than a line.
  static List<String> wrap(String value, int width) {
    final clean = sanitize(value).trim();
    if (clean.isEmpty) return [''];
    final lines = <String>[];
    var current = '';
    for (var word in clean.split(RegExp(r'\s+'))) {
      while (word.length > width) {
        if (current.isNotEmpty) {
          lines.add(current);
          current = '';
        }
        lines.add(word.substring(0, width));
        word = word.substring(width);
      }
      if (current.isEmpty) {
        current = word;
      } else if (current.length + 1 + word.length <= width) {
        current = '$current $word';
      } else {
        lines.add(current);
        current = word;
      }
    }
    if (current.isNotEmpty) lines.add(current);
    return lines;
  }
}

String _rupees(double value) => 'Rs.${value.toStringAsFixed(2)}';

String _two(int n) => n.toString().padLeft(2, '0');

String _stamp(DateTime t) =>
    '${_two(t.day)}/${_two(t.month)}/${t.year} ${_two(t.hour)}:${_two(t.minute)}';

/// The customer's token slip.
List<int> buildTokenSlip(ReceiptData data, {required int columns}) {
  final b = EscPosBuilder(columns: columns)..init();

  b.align(1).bold(true).size(width: 1, height: 2);
  for (final l in EscPosBuilder.wrap(data.restaurantName, columns)) {
    b.line(l);
  }
  b.size().bold(false).line('YOUR TOKEN');
  b.size(width: 4, height: 4).bold(true).line('${data.tokenNumber}');
  b.size().bold(false).line(data.orderTypeLabel);
  b.align(0).divider();

  for (final item in data.lines) {
    for (final l in EscPosBuilder.wrap('${item.quantity} x ${item.name}', columns)) {
      b.line(l);
    }
    for (final detail in item.details) {
      for (final l in EscPosBuilder.wrap(detail, columns - 4)) {
        b.line('    $l');
      }
    }
  }

  b.divider();
  b.bold(true).columnsLine('TOTAL', _rupees(data.total)).bold(false);
  b.line();
  b.align(1);
  b.bold(true).line(data.paymentNote).bold(false);
  b.line(_stamp(data.printedAt));
  b.line(data.footer);
  b.feed(4).cut();
  return b.bytes;
}

/// A short slip to confirm the printer is wired up and the paper width is right.
List<int> buildTestSlip({required int columns, required DateTime now}) {
  final b = EscPosBuilder(columns: columns)..init();
  b.align(1).bold(true).line('PRINTER TEST').bold(false);
  b.align(0).divider();
  b.line('This line should be exactly');
  b.line('as wide as the divider above.');
  b.divider();
  b.line('1234567890' * (columns ~/ 10) + '1234567890'.substring(0, columns % 10));
  b.align(1).line(_stamp(now));
  b.feed(4).cut();
  return b.bytes;
}
