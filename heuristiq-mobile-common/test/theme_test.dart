import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

void main() {
  test('light and dark themes carry their tokens', () {
    expect(HqTheme.light().extension<HqTokens>()!.primary, const Color(0xFF0B6E4F));
    expect(HqTheme.dark().extension<HqTokens>()!.primary, const Color(0xFF0F7A58));
    expect(HqTheme.dark().brightness, Brightness.dark);
  });

  test('primary override changes primary and link only', () {
    final tokens = HqTheme.light(primary: const Color(0xFF0369A1)).extension<HqTokens>()!;
    expect(tokens.primary, const Color(0xFF0369A1));
    expect(tokens.link, const Color(0xFF0369A1));
    expect(tokens.warn, HqTokens.light.warn);
  });

  test('every tone resolves to its colours', () {
    for (final tone in HqStatusTone.values) {
      expect(HqTokens.light.tone(tone).foreground, isNotNull);
    }
    expect(HqTokens.light.tone(HqStatusTone.danger).background, const Color(0xFFFEE2E2));
  });

  test('font scale factors match the web', () {
    expect(HqFontScale.small.factor, 0.875);
    expect(HqFontScale.xlarge.factor, 1.25);
  });

  testWidgets('context.hq returns the tokens in scope', (tester) async {
    late HqTokens seen;
    await tester.pumpWidget(MaterialApp(
      theme: HqTheme.dark(),
      home: Builder(builder: (context) {
        seen = context.hq;
        return const SizedBox();
      }),
    ));
    expect(seen.ink, HqTokens.dark.ink);
  });
}
