import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

/// 1x1 transparent PNG, enough for Image.memory to decode.
final Uint8List _png = base64Decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==');

HqPickedFile _file(String name, {int size = 1000, String? mime, Uint8List? bytes}) =>
    HqPickedFile(name: name, size: size, mimeType: mime, bytes: bytes ?? Uint8List(size));

class FakePicker implements HqFilePicker {
  FakePicker(this.result);

  List<HqPickedFile> result;
  bool deny = false;
  bool unavailable = false;
  final List<HqPickSource> sources = [];
  final List<bool> multiples = [];
  final List<int?> limits = [];
  var settingsOpened = 0;

  @override
  Future<List<HqPickedFile>> pick(HqPickSource source, {required bool multiple, required List<String> accept, int? limit}) async {
    sources.add(source);
    multiples.add(multiple);
    limits.add(limit);
    if (deny) throw HqPermissionDeniedException(source);
    if (unavailable) throw HqPickerUnavailableException(source, 'no_available_camera');
    return result;
  }

  @override
  Future<bool> openSettings() async {
    settingsOpened++;
    return true;
  }
}

class FakeUploader implements HqFileUploader {
  final List<String> uploaded = [];
  final List<String> removed = [];
  int failuresLeft = 0;
  Completer<void>? gate;
  var _next = 0;

  @override
  Future<String> upload(HqPickedFile file, {required void Function(double progress) onProgress}) async {
    onProgress(0.5);
    await gate?.future;
    if (failuresLeft > 0) {
      failuresLeft--;
      throw const HqApiException(statusCode: 500, message: 'boom');
    }
    uploaded.add(file.name);
    onProgress(1);
    return 'doc-${_next++}';
  }

  @override
  Future<void> remove(String documentId) async => removed.add(documentId);
}

class FakeProcessor implements HqImageProcessor {
  final List<HqImageOptions> calls = [];

  @override
  Future<HqPickedFile> process(HqPickedFile file, HqImageOptions options) async {
    calls.add(options);
    return HqPickedFile(name: 'processed.jpg', size: 500, bytes: _png, mimeType: 'image/jpeg');
  }
}

void main() {
  group('accept helpers', () {
    test('classifies accept lists', () {
      expect(hqAcceptIsImagesOnly(['image/*']), isTrue);
      expect(hqAcceptIsImagesOnly(['image/jpeg', '.png']), isTrue);
      expect(hqAcceptIsImagesOnly(['image/jpeg', 'application/pdf']), isFalse);
      expect(hqAcceptIsImagesOnly(const []), isFalse);
      expect(hqAcceptAllowsImages(['application/pdf']), isFalse);
      expect(hqAcceptAllowsDocuments(['image/*']), isFalse);
      expect(hqAcceptAllowsDocuments(['image/*', '.pdf']), isTrue);
    });

    test('matches by extension, exact MIME and wildcard, inferring MIME from the name', () {
      expect(hqIsAcceptedFile(_file('a.PDF'), ['.pdf']), isTrue);
      expect(hqIsAcceptedFile(_file('a.pdf'), ['application/pdf']), isTrue);
      expect(hqIsAcceptedFile(_file('a.png'), ['image/*']), isTrue);
      expect(hqIsAcceptedFile(_file('a.exe'), ['image/*', '.pdf']), isFalse);
      expect(hqIsAcceptedFile(_file('a.exe'), const []), isTrue);
      expect(hqIsAcceptedFile(_file('noext', mime: 'image/png'), ['image/png']), isTrue);
    });

    test('expands accept entries to file manager extensions', () {
      expect(hqAcceptExtensions(['application/pdf', '.docx']), ['pdf', 'docx']);
      expect(hqAcceptExtensions(['image/*']), containsAll(['jpg', 'png']));
    });
  });

  Widget upload({
    required FakePicker picker,
    required FakeUploader uploader,
    required List<String> accept,
    required ValueChanged<List<String>> onChanged,
    int maxFiles = 1,
    int maxBytes = 0,
    bool allowCamera = false,
    HqImageOptions? imageOptions,
    HqImageProcessor? processor,
    ValueChanged<String>? onRejected,
  }) {
    return SingleChildScrollView(
      child: HqFileUpload(
        picker: picker,
        uploader: uploader,
        texts: HqFileUploadTexts.english(),
        accept: accept,
        typeHints: 'PDF, JPEG',
        maxBytes: maxBytes,
        maxFiles: maxFiles,
        allowCamera: allowCamera,
        imageOptions: imageOptions,
        imageProcessor: processor ?? const HqImagePackageProcessor(),
        onChanged: onChanged,
        onRejected: onRejected,
        label: 'Document',
      ),
    );
  }

  testWidgets('images only: opens the gallery directly, multi-select when maxFiles > 1', (tester) async {
    final picker = FakePicker([_file('a.jpg', bytes: _png, size: 100)]);
    final uploader = FakeUploader();
    List<String> ids = [];
    await tester.pumpWidget(hqTestApp(upload(picker: picker, uploader: uploader, accept: ['image/*'], maxFiles: 3, onChanged: (v) => ids = v)));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(picker.sources, [HqPickSource.gallery]);
    expect(picker.multiples, [true]);
    expect(picker.limits, [3]);
    expect(ids, ['doc-0']);
    expect(find.text('a.jpg'), findsOneWidget);
  });

  testWidgets('images only with the camera flag asks Gallery or Camera', (tester) async {
    final picker = FakePicker([_file('shot.jpg', bytes: _png, size: 100)]);
    await tester.pumpWidget(hqTestApp(upload(picker: picker, uploader: FakeUploader(), accept: ['image/*'], allowCamera: true, onChanged: (_) {})));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(find.text('Gallery'), findsOneWidget);
    expect(find.text('Camera'), findsOneWidget);
    expect(find.text('Files'), findsNothing);
    await tester.tap(find.text('Camera'));
    await tester.pumpAndSettle();
    expect(picker.sources, [HqPickSource.camera]);
    expect(picker.multiples, [false]);
  });

  testWidgets('documents only: opens the file manager directly', (tester) async {
    final picker = FakePicker([_file('kyc.pdf', size: 2000)]);
    List<String> ids = [];
    await tester.pumpWidget(hqTestApp(upload(picker: picker, uploader: FakeUploader(), accept: ['application/pdf', '.docx'], onChanged: (v) => ids = v)));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(picker.sources, [HqPickSource.files]);
    expect(ids, ['doc-0']);
  });

  testWidgets('mixed types show Gallery, Camera and Files', (tester) async {
    final picker = FakePicker([_file('kyc.pdf', size: 2000)]);
    await tester.pumpWidget(hqTestApp(upload(picker: picker, uploader: FakeUploader(), accept: ['image/*', 'application/pdf'], onChanged: (_) {})));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(find.text('Gallery'), findsOneWidget);
    expect(find.text('Camera'), findsOneWidget);
    expect(find.text('Files'), findsOneWidget);
    await tester.tap(find.text('Files'));
    await tester.pumpAndSettle();
    expect(picker.sources, [HqPickSource.files]);
  });

  testWidgets('rejects a wrong type, a too large file and a too small file before upload', (tester) async {
    final picker = FakePicker([
      _file('virus.exe', size: 1000),
      _file('huge.pdf', size: 5000),
      _file('empty.pdf', size: 10),
    ]);
    final uploader = FakeUploader();
    final rejected = <String>[];
    await tester.pumpWidget(hqTestApp(upload(picker: picker, uploader: uploader, accept: ['.pdf'], maxBytes: 2000, maxFiles: 5, onChanged: (_) {}, onRejected: rejected.add)));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(uploader.uploaded, isEmpty);
    expect(rejected, ['virus.exe: type not allowed (PDF, JPEG)', 'huge.pdf: larger than 2.0 KB', 'empty.pdf: file is empty or too small']);
    expect(find.text('virus.exe: type not allowed (PDF, JPEG)'), findsOneWidget);
  });

  testWidgets('more files than maxFiles are refused and duplicates are not added twice', (tester) async {
    final picker = FakePicker([_file('a.pdf'), _file('a.pdf'), _file('b.pdf'), _file('c.pdf')]);
    final uploader = FakeUploader();
    final rejected = <String>[];
    await tester.pumpWidget(hqTestApp(upload(picker: picker, uploader: uploader, accept: ['.pdf'], maxFiles: 2, onChanged: (_) {}, onRejected: rejected.add)));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(uploader.uploaded, ['a.pdf', 'b.pdf']);
    expect(rejected, containsAll(['a.pdf: already added', 'At most 2 files']));
    expect(find.text('Choose file'), findsNothing);
  });

  testWidgets('shows progress while uploading', (tester) async {
    final uploader = FakeUploader()..gate = Completer<void>();
    await tester.pumpWidget(hqTestApp(upload(picker: FakePicker([_file('a.pdf')]), uploader: uploader, accept: ['.pdf'], onChanged: (_) {})));
    await tester.tap(find.text('Choose file'));
    await tester.pump();
    await tester.pump();
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    expect(find.textContaining('Uploading'), findsWidgets);
    uploader.gate!.complete();
    await tester.pumpAndSettle();
    expect(find.byType(LinearProgressIndicator), findsNothing);
    expect(find.textContaining('Uploaded'), findsOneWidget);
  });

  testWidgets('a failed upload offers retry and succeeds the second time', (tester) async {
    final uploader = FakeUploader()..failuresLeft = 1;
    List<String> ids = [];
    await tester.pumpWidget(hqTestApp(upload(picker: FakePicker([_file('a.pdf')]), uploader: uploader, accept: ['.pdf'], onChanged: (v) => ids = v)));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(ids, isEmpty);
    expect(find.textContaining('upload failed'), findsWidgets);
    await tester.tap(find.byTooltip('Retry'));
    await tester.pumpAndSettle();
    expect(ids, ['doc-0']);
    expect(find.byTooltip('Retry'), findsNothing);
  });

  testWidgets('remove deletes the stored document, updates the ids and brings the picker back', (tester) async {
    final uploader = FakeUploader();
    List<String> ids = [];
    await tester.pumpWidget(hqTestApp(upload(picker: FakePicker([_file('a.pdf')]), uploader: uploader, accept: ['.pdf'], onChanged: (v) => ids = v)));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(find.text('Choose file'), findsNothing);
    await tester.tap(find.byTooltip('Remove'));
    await tester.pumpAndSettle();
    expect(uploader.removed, ['doc-0']);
    expect(ids, isEmpty);
    expect(find.text('Choose file'), findsOneWidget);
  });

  testWidgets('image preview opens a larger view; documents show a file icon', (tester) async {
    final picker = FakePicker([_file('photo.png', bytes: _png, size: 100), _file('kyc.pdf', size: 2000)]);
    await tester.pumpWidget(hqTestApp(upload(picker: picker, uploader: FakeUploader(), accept: ['image/*', '.pdf'], maxFiles: 2, onChanged: (_) {})));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Files'));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.picture_as_pdf_outlined), findsOneWidget);
    expect(find.byType(Image), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('hq-upload-preview-photo.png')));
    await tester.pumpAndSettle();
    expect(find.byType(InteractiveViewer), findsOneWidget);
  });

  testWidgets('permission denied shows the passed-in message and an open-settings action', (tester) async {
    final picker = FakePicker(const [])..deny = true;
    await tester.pumpWidget(hqTestApp(upload(picker: picker, uploader: FakeUploader(), accept: ['image/*'], onChanged: (_) {})));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(find.text('Allow access to photos to continue'), findsOneWidget);
    await tester.tap(find.text('Open settings'));
    expect(picker.settingsOpened, 1);
  });

  testWidgets('a picker that cannot open shows the passed-in message and stops the spinner', (tester) async {
    final rejected = <String>[];
    final picker = FakePicker(const [])..unavailable = true;
    await tester.pumpWidget(hqTestApp(upload(picker: picker, uploader: FakeUploader(), accept: ['application/pdf'], onChanged: (_) {}, onRejected: rejected.add)));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(rejected, ['Could not open the camera or files. Try again.']);
    expect(find.text('Could not open the camera or files. Try again.'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
  });

  testWidgets('camera refusal uses the camera message', (tester) async {
    final picker = FakePicker(const [])..deny = true;
    await tester.pumpWidget(hqTestApp(upload(picker: picker, uploader: FakeUploader(), accept: ['image/*'], allowCamera: true, onChanged: (_) {})));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Camera'));
    await tester.pumpAndSettle();
    expect(find.text('Allow access to the camera to continue'), findsOneWidget);
  });

  testWidgets('images are processed (downscale, EXIF strip) before the size check and upload', (tester) async {
    final processor = FakeProcessor();
    final uploader = FakeUploader();
    // 9000 bytes picked, 500 after processing: the limit applies to the processed file.
    final picker = FakePicker([_file('big.jpg', bytes: _png, size: 9000)]);
    await tester.pumpWidget(hqTestApp(upload(
      picker: picker,
      uploader: uploader,
      accept: ['image/*'],
      maxBytes: 1000,
      imageOptions: const HqImageOptions(maxDimension: 1600, quality: 70),
      processor: processor,
      onChanged: (_) {},
    )));
    await tester.tap(find.text('Choose file'));
    await tester.pumpAndSettle();
    expect(processor.calls.single.maxDimension, 1600);
    expect(uploader.uploaded, ['processed.jpg']);
  });

  testWidgets('the picker button is disabled when the control is', (tester) async {
    await tester.pumpWidget(hqTestApp(HqFileUpload(
      picker: FakePicker(const []),
      uploader: FakeUploader(),
      texts: HqFileUploadTexts.english(),
      onChanged: (_) {},
      enabled: false,
    )));
    expect(tester.widget<OutlinedButton>(find.byType(OutlinedButton)).onPressed, isNull);
  });

  group('HqDmsUploader', () {
    test('posts multipart with category, owner, checksum and returns the document id', () async {
      late http.Request seen;
      late String body;
      final uploader = HqDmsUploader(
        gatewayUrl: 'https://gw/api/',
        tokenProvider: () => 'tok',
        category: 'KYC',
        ownerId: 'user-1',
        documentType: 'PAN_CARD',
        httpClient: MockClient((request) async {
          seen = request;
          body = request.body;
          return http.Response(jsonEncode({'success': true, 'responseObject': {'id': 'dms-42'}}), 200);
        }),
      );
      final progress = <double>[];
      final id = await uploader.upload(_file('pan.pdf', size: 100), onProgress: progress.add);
      expect(id, 'dms-42');
      expect(progress, [0, 1]);
      expect(seen.url.toString(), 'https://gw/api/v1/dms/documents');
      expect(seen.headers['Authorization'], 'Bearer tok');
      expect(body, contains('name="category"'));
      expect(body, contains('KYC'));
      expect(body, contains('user-1'));
      expect(body, contains('PAN_CARD'));
      expect(body, contains('name="checksum"'));
      expect(body, contains('filename="pan.pdf"'));
    });

    test('a failure or a missing id throws HqApiException; remove calls DELETE', () async {
      final methods = <String>[];
      final failing = HqDmsUploader(
        gatewayUrl: 'https://gw/api',
        tokenProvider: () => 'tok',
        category: 'KYC',
        ownerId: 'u',
        httpClient: MockClient((request) async {
          methods.add('${request.method} ${request.url.path}');
          if (request.method == 'DELETE') return http.Response('', 204);
          return http.Response(jsonEncode({'success': false, 'message': 'virus found', 'errorCode': 'DMS_SCAN'}), 422);
        }),
      );
      await expectLater(failing.upload(_file('a.pdf', size: 100), onProgress: (_) {}), throwsA(isA<HqApiException>().having((e) => e.errorCode, 'errorCode', 'DMS_SCAN')));
      await failing.remove('dms-1');
      expect(methods.last, 'DELETE /api/v1/dms/documents/metadata/dms-1');

      final noId = HqDmsUploader(
        gatewayUrl: 'https://gw/api',
        tokenProvider: () => null,
        category: 'KYC',
        ownerId: 'u',
        httpClient: MockClient((_) async => http.Response(jsonEncode({'success': true, 'responseObject': {}}), 200)),
      );
      await expectLater(noId.upload(_file('a.pdf', size: 100), onProgress: (_) {}), throwsA(isA<HqApiException>()));
    });
  });

  test('hqFormatBytes', () {
    expect(hqFormatBytes(512), '512 B');
    expect(hqFormatBytes(2048), '2.0 KB');
    expect(hqFormatBytes(5 * 1024 * 1024), '5.0 MB');
  });
}
