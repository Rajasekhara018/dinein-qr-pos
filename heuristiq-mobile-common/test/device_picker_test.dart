
import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:image/image.dart' as img;
import 'package:image_picker/image_picker.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

class _FakeImagePicker extends ImagePicker {
  _FakeImagePicker({this.single, this.multi = const [], this.error});

  final XFile? single;
  final List<XFile> multi;
  final PlatformException? error;
  final List<String> calls = [];

  @override
  Future<XFile?> pickImage({required ImageSource source, double? maxWidth, double? maxHeight, int? imageQuality, CameraDevice preferredCameraDevice = CameraDevice.rear, bool requestFullMetadata = true}) async {
    calls.add('image:${source.name}');
    if (error != null) throw error!;
    return single;
  }

  @override
  Future<List<XFile>> pickMultiImage({double? maxWidth, double? maxHeight, int? imageQuality, int? limit, bool requestFullMetadata = true}) async {
    calls.add('multi:$limit');
    return multi;
  }
}

/// A picked file held in memory (web) or on the device (a path), as file_picker 12 answers it.
final class _FakePlatformFile extends PlatformFile {
  _FakePlatformFile(this.name, {this.filePath, List<int>? content}) : _bytes = Uint8List.fromList(content ?? List<int>.filled(900, 1));

  @override
  final String name;
  final String? filePath;
  final Uint8List _bytes;

  @override
  Uri get uri => filePath == null ? Uri.parse('blob:$name') : Uri.file(filePath!);
  @override
  String? get path => filePath;
  @override
  XFile get xFile => XFile.fromData(_bytes, name: name);
  @override
  int? lengthSync() => _bytes.length;
  @override
  Future<int> length() async => _bytes.length;
  @override
  Future<Uint8List> readAsBytes() async => _bytes;
  @override
  Stream<Uint8List> readAsByteStream() => Stream.value(_bytes);
}

/// The file_picker platform, answering fixed files and remembering how it was asked.
class _FakeFilePickerPlatform extends FilePickerPlatform with MockPlatformInterfaceMixin {
  _FakeFilePickerPlatform(this.files);

  final List<PlatformFile> files;
  FileType? type;
  List<String>? extensions;
  String? call;

  @override
  Future<List<PlatformFile>> pickFiles({
    String? dialogTitle,
    String? initialDirectory,
    FileType type = FileType.any,
    List<String>? allowedExtensions,
    Function(FilePickerStatus)? onFileLoading,
    int compressionQuality = 0,
    AndroidOptions androidOptions = const AndroidOptions(),
    DarwinOptions darwinOptions = const DarwinOptions(),
    WindowsOptions windowsOptions = const WindowsOptions(),
    LinuxOptions linuxOptions = const LinuxOptions(),
    WebOptions webOptions = const WebOptions(),
  }) async {
    call = 'pickFiles';
    this.type = type;
    extensions = allowedExtensions;
    return files;
  }

  @override
  Future<PlatformFile?> pickFile({
    String? dialogTitle,
    String? initialDirectory,
    FileType type = FileType.any,
    List<String>? allowedExtensions,
    Function(FilePickerStatus)? onFileLoading,
    int compressionQuality = 0,
    AndroidOptions androidOptions = const AndroidOptions(),
    DarwinOptions darwinOptions = const DarwinOptions(),
    WindowsOptions windowsOptions = const WindowsOptions(),
    LinuxOptions linuxOptions = const LinuxOptions(),
    WebOptions webOptions = const WebOptions(),
  }) async {
    call = 'pickFile';
    this.type = type;
    extensions = allowedExtensions;
    return files.isEmpty ? null : files.first;
  }
}

XFile _shot(String name) => XFile.fromData(Uint8List.fromList(List<int>.filled(120, 7)), name: name, path: name, mimeType: 'image/jpeg');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('HqDeviceFilePicker', () {
    test('gallery single, gallery multiple with a limit, camera, and cancel', () async {
      final images = _FakeImagePicker(single: _shot('a.jpg'), multi: [_shot('b.jpg'), _shot('c.jpg')]);
      final picker = HqDeviceFilePicker(imagePicker: images);
      final one = await picker.pick(HqPickSource.gallery, multiple: false, accept: const ['image/*']);
      expect(one.single.name, 'a.jpg');
      expect(one.single.size, 120);
      expect(one.single.mimeType, 'image/jpeg');
      expect((await picker.pick(HqPickSource.gallery, multiple: true, accept: const [], limit: 3)).length, 2);
      await picker.pick(HqPickSource.gallery, multiple: true, accept: const [], limit: 1);
      expect((await picker.pick(HqPickSource.camera, multiple: false, accept: const [])).single.name, 'a.jpg');
      expect(images.calls, ['image:gallery', 'multi:3', 'multi:null', 'image:camera']);

      final none = HqDeviceFilePicker(imagePicker: _FakeImagePicker());
      expect(await none.pick(HqPickSource.gallery, multiple: false, accept: const []), isEmpty);
      expect(await none.pick(HqPickSource.camera, multiple: false, accept: const []), isEmpty);
    });

    test('a refused permission becomes HqPermissionDeniedException; other errors pass through', () async {
      final denied = HqDeviceFilePicker(imagePicker: _FakeImagePicker(error: PlatformException(code: 'camera_access_denied')));
      await expectLater(
        denied.pick(HqPickSource.camera, multiple: false, accept: const []),
        throwsA(isA<HqPermissionDeniedException>().having((e) => e.source, 'source', HqPickSource.camera).having((e) => e.toString(), 'text', contains('camera'))),
      );
      final broken = HqDeviceFilePicker(imagePicker: _FakeImagePicker(error: PlatformException(code: 'no_available_camera')));
      await expectLater(broken.pick(HqPickSource.camera, multiple: false, accept: const []), throwsA(isA<HqPickerUnavailableException>().having((e) => e.code, 'code', 'no_available_camera').having((e) => e.toString(), 'text', contains('no_available_camera'))));
    });

    test('files go through the system picker filtered by extension', () async {
      final original = FilePickerPlatform.instance;
      addTearDown(() => FilePickerPlatform.instance = original);
      final fake = _FakeFilePickerPlatform([_FakePlatformFile('deed.pdf', filePath: '/tmp/deed.pdf')]);
      FilePickerPlatform.instance = fake;
      final picker = HqDeviceFilePicker(imagePicker: _FakeImagePicker());
      final files = await picker.pick(HqPickSource.files, multiple: true, accept: const ['application/pdf']);
      expect(files.single.name, 'deed.pdf');
      expect(files.single.path, '/tmp/deed.pdf');
      expect(files.single.size, 900);
      expect(files.single.bytes, isNull, reason: 'a file on the device is read from its path when uploaded');
      expect(fake.call, 'pickFiles');
      expect(fake.type, FileType.custom);
      expect(fake.extensions, ['pdf']);

      final none = _FakeFilePickerPlatform(const []);
      FilePickerPlatform.instance = none;
      expect(await picker.pick(HqPickSource.files, multiple: false, accept: const []), isEmpty);
      expect(none.call, 'pickFile');
      expect(none.type, FileType.any);
      expect(none.extensions, isNull);
    });

    test('a single file without a path (web) is read into memory', () async {
      final original = FilePickerPlatform.instance;
      addTearDown(() => FilePickerPlatform.instance = original);
      FilePickerPlatform.instance = _FakeFilePickerPlatform([_FakePlatformFile('id.png', content: [1, 2, 3])]);
      final files = await HqDeviceFilePicker(imagePicker: _FakeImagePicker()).pick(HqPickSource.files, multiple: false, accept: const ['image/png']);
      expect(files.single.path, isNull);
      expect(files.single.bytes, [1, 2, 3]);
      expect(files.single.size, 3);
    });

    test('openSettings asks the permission plugin', () async {
      const channel = MethodChannel('flutter.baseflow.com/permissions/methods');
      final calls = <String>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call.method);
        return true;
      });
      addTearDown(() => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null));
      expect(await HqDeviceFilePicker(imagePicker: _FakeImagePicker()).openSettings(), isTrue);
      expect(calls, ['openAppSettings']);
    });
  });

  group('HqImagePackageProcessor', () {
    test('scales the longest side down and re-encodes to JPEG', () async {
      final png = Uint8List.fromList(img.encodePng(img.Image(width: 400, height: 200)));
      final out = await const HqImagePackageProcessor().process(HqPickedFile(name: 'pan.card.png', size: png.length, bytes: png), const HqImageOptions(maxDimension: 100, quality: 70));
      expect(out.name, 'pan.card.jpg');
      expect(out.mimeType, 'image/jpeg');
      expect(out.size, out.bytes!.length);
      final decoded = img.decodeJpg(out.bytes!)!;
      expect([decoded.width, decoded.height], [100, 50]);
    });

    test('portrait images scale by height; small ones are not enlarged; a name without extension', () async {
      final tall = Uint8List.fromList(img.encodePng(img.Image(width: 50, height: 300)));
      final out = await const HqImagePackageProcessor().process(HqPickedFile(name: 'photo', size: tall.length, bytes: tall), const HqImageOptions(maxDimension: 150));
      expect(out.name, 'photo.jpg');
      final d = img.decodeJpg(out.bytes!)!;
      expect(d.height, 150);
      final small = Uint8List.fromList(img.encodePng(img.Image(width: 20, height: 10)));
      final kept = await const HqImagePackageProcessor().process(HqPickedFile(name: 's.png', size: small.length, bytes: small), const HqImageOptions());
      expect(img.decodeJpg(kept.bytes!)!.width, 20);
    });

    test('a file that is not an image comes back unchanged', () async {
      final file = HqPickedFile(name: 'x.heic', size: 4, bytes: Uint8List.fromList([1, 2, 3, 4]));
      expect(identical(await const HqImagePackageProcessor().process(file, const HqImageOptions()), file), isTrue);
    });
  });
}
