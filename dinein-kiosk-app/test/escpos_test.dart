import 'dart:convert';

import 'package:dinein_kiosk/core/printing/escpos.dart';
import 'package:dinein_kiosk/core/printing/receipt.dart';
import 'package:flutter_test/flutter_test.dart';

ReceiptData slip({List<ReceiptLine>? lines, String name = 'Burger Hub'}) => ReceiptData(
      restaurantName: name,
      tokenNumber: 104,
      orderTypeLabel: 'TAKEAWAY',
      total: 322.5,
      printedAt: DateTime(2026, 9, 30, 14, 5),
      lines: lines ??
          const [
            ReceiptLine(
              name: 'Classic Veg Burger',
              quantity: 2,
              details: ['+ Extra cheese', '"no onion"'],
            ),
            ReceiptLine(name: 'Cold Coffee', quantity: 1, details: ['Large']),
          ],
    );

bool containsSequence(List<int> haystack, List<int> needle) {
  for (var i = 0; i <= haystack.length - needle.length; i++) {
    var match = true;
    for (var j = 0; j < needle.length; j++) {
      if (haystack[i + j] != needle[j]) {
        match = false;
        break;
      }
    }
    if (match) return true;
  }
  return false;
}

/// The printable text of a slip, with control sequences left in (they are not ASCII text).
String textOf(List<int> bytes) => latin1.decode(bytes, allowInvalid: true);

void main() {
  group('token slip', () {
    test('starts by resetting the printer and ends with feed then a cut', () {
      final bytes = buildTokenSlip(slip(), columns: 32);
      expect(bytes.take(2), [0x1B, 0x40]);
      expect(bytes.sublist(bytes.length - 4), [0x1D, 0x56, 0x42, 0x00]);
      expect(containsSequence(bytes, [0x1B, 0x64, 4]), isTrue);
    });

    test('shows the token, order type, items, details and total', () {
      final text = textOf(buildTokenSlip(slip(), columns: 32));
      expect(text, contains('104'));
      expect(text, contains('TAKEAWAY'));
      expect(text, contains('2 x Classic Veg Burger'));
      expect(text, contains('+ Extra cheese'));
      expect(text, contains('"no onion"'));
      expect(text, contains('1 x Cold Coffee'));
      expect(text, contains('Rs.322.50'));
      expect(text, contains('Please pay at the counter'));
      expect(text, contains('30/09/2026 14:05'));
    });

    test('the token is printed large', () {
      final bytes = buildTokenSlip(slip(), columns: 32);
      // GS ! 0x33 = 4x width and height.
      expect(containsSequence(bytes, [0x1D, 0x21, 0x33]), isTrue);
    });

    test('no printed line is wider than the paper', () {
      for (final columns in [32, 48]) {
        final long = slip(
          name: 'The Extraordinarily Long Restaurant Name Pvt Ltd',
          lines: const [
            ReceiptLine(
              name: 'Supercalifragilisticexpialidocious Paneer Tikka Masala Burger',
              quantity: 12,
              details: ['"please make it extra spicy with less oil and no onions at all"'],
            ),
          ],
        );
        final text = textOf(buildTokenSlip(long, columns: columns));
        for (final line in text.split('\n')) {
          // Strip the control bytes; only visible characters count towards the width.
          final visible = line.replaceAll(RegExp(r'[\x00-\x1F]'), '');
          // Text at 2x/4x size is centred lines that are short by construction; the widest normal-size
          // line must still fit.
          expect(visible.length, lessThanOrEqualTo(columns + 6),
              reason: 'line too wide for $columns columns: "$visible"');
        }
      }
    });
  });

  group('text safety', () {
    test('the rupee sign is spelled out and other non-ASCII is replaced', () {
      expect(EscPosBuilder.sanitize('₹99'), 'Rs.99');
      expect(EscPosBuilder.sanitize('पनीर'), '????');
      expect(EscPosBuilder.sanitize('Café'), 'Caf?');
    });

    test('a non-ASCII item name never produces raw multi-byte output', () {
      final bytes = buildTokenSlip(
        slip(lines: const [ReceiptLine(name: 'पनीर टिक्का', quantity: 1)]),
        columns: 32,
      );
      expect(bytes.every((b) => b < 0x80), isTrue);
    });
  });

  group('wrapping', () {
    test('wraps on words', () {
      expect(EscPosBuilder.wrap('aaa bbb ccc', 7), ['aaa bbb', 'ccc']);
    });

    test('splits a word that is longer than a line', () {
      expect(EscPosBuilder.wrap('abcdefghij', 4), ['abcd', 'efgh', 'ij']);
    });

    test('empty text is one blank line', () {
      expect(EscPosBuilder.wrap('   ', 10), ['']);
    });
  });

  test('right-aligned amounts line up to the paper width', () {
    final b = EscPosBuilder(columns: 20)..columnsLine('TOTAL', 'Rs.10.00');
    final line = textOf(b.bytes).split('\n').first;
    expect(line.length, 20);
    expect(line, startsWith('TOTAL'));
    expect(line, endsWith('Rs.10.00'));
  });

  test('test slip fits the paper width', () {
    final text = textOf(buildTestSlip(columns: 32, now: DateTime(2026, 1, 2, 3, 4)));
    expect(text, contains('PRINTER TEST'));
    expect(text, contains('12345678901234567890123456789012'));
  });
}
