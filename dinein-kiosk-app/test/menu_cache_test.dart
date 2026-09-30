import 'dart:convert';
import 'dart:typed_data';

import 'package:dinein_kiosk/features/menu/data/menu_cache.dart';
import 'package:dinein_kiosk/features/menu/data/menu_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeAdapter implements HttpClientAdapter {
  _FakeAdapter(this.handler);

  final Future<ResponseBody> Function(RequestOptions options) handler;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream,
      Future<void>? cancelFuture) {
    requests.add(options);
    return handler(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody menuResponse(Map<String, dynamic> json, {String etag = '"v1"'}) =>
    ResponseBody.fromString(jsonEncode(json), 200, headers: {
      Headers.contentTypeHeader: ['application/json'],
      'etag': [etag],
    });

ApiMenuRepository repoWith(HttpClientAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = adapter;
  return ApiMenuRepository(dio, MenuCache());
}

DioException offline(RequestOptions o) =>
    DioException(requestOptions: o, type: DioExceptionType.connectionError);

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('a fetched menu is saved and later sent back as If-None-Match', () async {
    final adapter = _FakeAdapter((o) async {
      if (o.headers['If-None-Match'] == '"v1"') {
        return ResponseBody.fromString('', 304);
      }
      return menuResponse(demoMenuJson);
    });
    final repo = repoWith(adapter);

    final first = await repo.fetchMenu();
    expect(first.categories, isNotEmpty);
    expect(first.stale, isFalse);

    final second = await repo.fetchMenu();
    expect(adapter.requests.last.headers['If-None-Match'], '"v1"');
    expect(second.categories.length, first.categories.length);
    expect(second.stale, isFalse);
  });

  test('when the server cannot be reached the saved menu is shown, marked stale', () async {
    var online = true;
    final adapter = _FakeAdapter((o) async {
      if (online) return menuResponse(demoMenuJson);
      throw offline(o);
    });
    final repo = repoWith(adapter);
    await repo.fetchMenu();

    online = false;
    final menu = await repo.fetchMenu();
    expect(menu.stale, isTrue);
    expect(menu.itemById(101)?.name, 'Classic Veg Burger');
  });

  test('with nothing saved and no server, the customer gets a clear error', () async {
    final repo = repoWith(_FakeAdapter((o) async => throw offline(o)));
    expect(
      repo.fetchMenu(),
      throwsA(predicate((e) => '$e'.contains("Can't reach the server"))),
    );
  });

  test('a real server error is not hidden behind the saved menu', () async {
    var fail = false;
    final adapter = _FakeAdapter((o) async {
      if (!fail) return menuResponse(demoMenuJson);
      return ResponseBody.fromString(jsonEncode({'message': 'Boom'}), 500, headers: {
        Headers.contentTypeHeader: ['application/json'],
      });
    });
    final repo = repoWith(adapter);
    await repo.fetchMenu();

    fail = true;
    // validateStatus only allows 200/304, so a 500 surfaces as a DioException with the backend's message.
    expect(repo.fetchMenu(), throwsA(predicate((e) => '$e'.contains('Boom'))));
  });
}
