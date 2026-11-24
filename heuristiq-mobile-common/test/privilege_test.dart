import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

const _mapping = <String, List<String>>{
  'view_invest_portfolio': ['INVEST_PORTFOLIO_LIST'],
};

void main() {
  group('HqPrivilegeAccess', () {
    test('everything is allowed until privileges are loaded', () {
      final access = HqPrivilegeAccess();
      expect(access.can('ANYTHING'), isTrue);
      expect(access.canOpen('view_invest_portfolio', _mapping), isNull);
    });

    test('can() needs any one of the codes; empty means no requirement', () {
      final access = HqPrivilegeAccess()..setPermissions(['A', 'B']);
      expect(access.can('A'), isTrue);
      expect(access.can(['X', 'B']), isTrue);
      expect(access.can('X'), isFalse);
      expect(access.can(null), isTrue);
      expect(access.can(<String>[]), isTrue);
      expect(() => access.can(5), throwsArgumentError);
    });

    test('canOpen decides only mapped keys', () {
      final access = HqPrivilegeAccess()..setPermissions(['INVEST_PORTFOLIO_LIST']);
      expect(access.canOpen('view_invest_portfolio', _mapping), isTrue);
      expect(access.canOpen('unknown', _mapping), isNull);
      access.setPermissions(['OTHER']);
      expect(access.canOpen('view_invest_portfolio', _mapping), isFalse);
    });

    test('loadFromResponse reads permissions and clears on junk; notifies', () {
      final access = HqPrivilegeAccess();
      var notified = 0;
      access.addListener(() => notified++);
      access.loadFromResponse({'permissions': ['P1', 'P2'], 'roles': ['R']});
      expect(access.granted, {'P1', 'P2'});
      access.loadFromResponse('nonsense');
      expect(access.granted, isNull);
      expect(notified, 2);
    });

    test('refresh loads from UAM and survives a failure', () async {
      final calls = <Uri>[];
      final ok = HqApiClient(
        baseUrl: 'https://gw/api',
        httpClient: MockClient((request) async {
          calls.add(request.url);
          return http.Response(jsonEncode({'success': true, 'responseObject': {'permissions': ['INVEST_PORTFOLIO_LIST']}}), 200);
        }),
      );
      final access = HqPrivilegeAccess();
      expect(await access.refresh(ok), isTrue);
      expect(calls.single.path, '/api$hqMyPrivilegesPath');
      expect(access.can('INVEST_PORTFOLIO_LIST'), isTrue);
      expect(access.can('OTHER'), isFalse);

      final failing = HqApiClient(baseUrl: 'https://gw/api', httpClient: MockClient((_) async => http.Response('{}', 503)));
      final fresh = HqPrivilegeAccess();
      expect(await fresh.refresh(failing), isFalse);
      expect(fresh.can('OTHER'), isTrue);
    });
  });

  group('HqHasPrivilege', () {
    testWidgets('shows the child only with the privilege and reacts to changes', (tester) async {
      final access = HqPrivilegeAccess()..setPermissions(['OTHER']);
      await tester.pumpWidget(hqTestApp(const HqHasPrivilege(codes: ['INVEST_SUBSCRIPTION_CREATE'], fallback: Text('No access'), child: Text('Invest')), access: access));
      expect(find.text('Invest'), findsNothing);
      expect(find.text('No access'), findsOneWidget);
      access.setPermissions(['INVEST_SUBSCRIPTION_CREATE']);
      await tester.pump();
      expect(find.text('Invest'), findsOneWidget);
    });

    testWidgets('without a scope the child is shown', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqHasPrivilege(codes: ['X'], child: Text('Shown'))));
      expect(find.text('Shown'), findsOneWidget);
    });
  });

  group('HqPermissionGuard', () {
    test('redirects only when the privileges deny the screen', () {
      final access = HqPrivilegeAccess();
      expect(HqPermissionGuard.redirect(access, 'view_invest_portfolio', _mapping, '/home'), isNull);
      access.setPermissions(['OTHER']);
      expect(HqPermissionGuard.redirect(access, 'view_invest_portfolio', _mapping, '/home'), '/home');
      access.setPermissions(['INVEST_PORTFOLIO_LIST']);
      expect(HqPermissionGuard.redirect(access, 'view_invest_portfolio', _mapping, '/home'), isNull);
      expect(HqPermissionGuard.redirect(access, 'unmapped', _mapping, '/home'), isNull);
    });

    testWidgets('gate shows the denied view', (tester) async {
      final access = HqPrivilegeAccess()..setPermissions(['OTHER']);
      await tester.pumpWidget(hqTestApp(
        HqPermissionGate(permission: 'view_invest_portfolio', mapping: _mapping, child: const Text('Screen'), deniedBuilder: (_) => const Text('Denied')),
        access: access,
      ));
      expect(find.text('Denied'), findsOneWidget);
      expect(find.text('Screen'), findsNothing);
    });
  });

  group('HqApiClient', () {
    test('unwraps the envelope, sends the token and correlation id', () async {
      late http.BaseRequest seen;
      final client = HqApiClient(
        baseUrl: 'https://gw/api/',
        tokenProvider: () => 'tok',
        correlationId: () => 'corr-1',
        httpClient: MockClient((request) async {
          seen = request;
          return http.Response(jsonEncode({'success': true, 'responseObject': [1, 2]}), 200);
        }),
      );
      expect(await client.get('/v1/investment/portfolio', query: {'a': 'b'}), [1, 2]);
      expect(seen.url.toString(), 'https://gw/api/v1/investment/portfolio?a=b');
      expect(seen.headers['Authorization'], 'Bearer tok');
      expect(seen.headers['X-Correlation-ID'], 'corr-1');
    });

    test('a failed envelope or status becomes HqApiException', () async {
      final client = HqApiClient(
        baseUrl: 'https://gw/api',
        httpClient: MockClient((_) async => http.Response(jsonEncode({'success': false, 'message': 'nope', 'errorCode': 'E1'}), 400)),
      );
      await expectLater(
        client.post('/x', body: {'a': 1}),
        throwsA(isA<HqApiException>().having((e) => e.errorCode, 'errorCode', 'E1').having((e) => e.statusCode, 'status', 400)),
      );
      final unauthorized = HqApiClient(baseUrl: 'https://gw/api', httpClient: MockClient((_) async => http.Response('', 401)));
      await expectLater(unauthorized.get('/x'), throwsA(isA<HqApiException>().having((e) => e.isUnauthorized, 'isUnauthorized', isTrue)));
    });

    test('a network failure has status 0', () async {
      final client = HqApiClient(baseUrl: 'https://gw/api', httpClient: MockClient((_) async => throw http.ClientException('offline')));
      await expectLater(client.get('/x'), throwsA(isA<HqApiException>().having((e) => e.isNetworkError, 'isNetworkError', isTrue)));
    });
  });
}
