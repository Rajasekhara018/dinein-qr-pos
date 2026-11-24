import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

void main() {
  late List<http.Request> requests;
  late int status;
  var now = DateTime.utc(2026, 11, 22, 10);
  var seq = 0;

  HqScreenAudit make({String? token = 'tok'}) => HqScreenAudit(
        gatewayUrl: 'https://gw/api/',
        tokenProvider: () => token,
        appVersion: '1.0.0',
        autoFlush: false,
        clock: () => now,
        idGenerator: () => 'event-${seq++}',
        httpClient: MockClient((request) async {
          requests.add(request);
          return http.Response('{}', status);
        }),
      );

  setUp(() {
    requests = [];
    status = 200;
    now = DateTime.utc(2026, 11, 22, 10);
    seq = 0;
  });

  test('posts the web payload shape to the gateway audit path with the bearer token', () async {
    final audit = make();
    audit.report('invest.portfolio');
    audit.report('invest.holding_detail');
    await audit.flush();
    expect(requests, hasLength(1));
    expect(requests.single.url.toString(), 'https://gw/api/v1/audit/screen-views');
    expect(requests.single.headers['Authorization'], 'Bearer tok');
    final events = (jsonDecode(requests.single.body) as Map<String, dynamic>)['events'] as List<dynamic>;
    final first = events.first as Map<String, dynamic>;
    expect(first['screenKey'], 'invest.portfolio');
    expect(first['feature'], 'invest');
    expect(first['appChannel'], 'CUSTOMER_APP');
    expect(first['occurredAt'], '2026-11-22T10:00:00.000Z');
    expect(first['eventId'], 'event-0');
    expect((first['details'] as Map<String, dynamic>)['platform'], 'mobile');
    expect((first['details'] as Map<String, dynamic>)['appVersion'], '1.0.0');
    expect((events.last as Map<String, dynamic>)['details']['previousScreenKey'], 'invest.portfolio');
    expect(audit.pendingCount, 0);
  });

  test('ignores invalid keys and a repeated key', () {
    final audit = make();
    audit.report('Invalid Key');
    audit.report('9bad');
    audit.report('invest.portfolio');
    audit.report('invest.portfolio');
    expect(audit.pendingCount, 1);
  });

  test('a signed-out flush drops the buffer and sends nothing', () async {
    final audit = make(token: null);
    audit.report('auth.login');
    await audit.flush();
    expect(requests, isEmpty);
    expect(audit.pendingCount, 0);
  });

  test('sends in batches of 20', () async {
    final audit = make();
    for (var i = 0; i < 45; i++) {
      audit.report('invest.screen_$i');
    }
    // The 20th report starts a flush on its own; give it time to drain, then flush what is left.
    await Future<void>.delayed(const Duration(milliseconds: 50));
    await audit.flush();
    expect(requests, isNotEmpty);
    for (final r in requests) {
      expect(((jsonDecode(r.body) as Map<String, dynamic>)['events'] as List<dynamic>).length, lessThanOrEqualTo(20));
    }
    expect(audit.pendingCount, 0);
  });

  test('after HTTP 429 reporting pauses for a minute', () async {
    final audit = make();
    status = 429;
    audit.report('invest.a');
    await audit.flush();
    final sent = requests.length;
    audit.report('invest.b');
    await audit.flush();
    expect(requests.length, sent);
    now = now.add(const Duration(seconds: 61));
    status = 200;
    await audit.flush();
    expect(requests.length, sent + 1);
  });

  test('a network error drops the batch without throwing', () async {
    final audit = HqScreenAudit(
      gatewayUrl: 'https://gw/api',
      tokenProvider: () => 'tok',
      autoFlush: false,
      httpClient: MockClient((_) async => throw http.ClientException('offline')),
    );
    audit.report('invest.a');
    await audit.flush();
    expect(audit.pendingCount, 0);
  });

  test('reset clears the buffer and the previous key', () {
    final audit = make();
    audit.report('invest.a');
    audit.reset();
    expect(audit.pendingCount, 0);
    audit.report('invest.a');
    expect(audit.pendingCount, 1);
  });
}
