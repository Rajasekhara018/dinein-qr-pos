import 'package:flutter/foundation.dart';
import 'package:image/image.dart' as img;

import 'hq_picked_file.dart';

/// How large images are prepared before upload.
@immutable
class HqImageOptions {
  const HqImageOptions({this.maxDimension = 2048, this.quality = 85});

  /// Longest side in pixels; larger images are scaled down (never up).
  final int maxDimension;

  /// JPEG quality, 1 to 100.
  final int quality;
}

/// Prepares an image for upload: applies the EXIF orientation, scales it down and re-encodes it, which drops all metadata
/// (GPS location, device, time).
abstract interface class HqImageProcessor {
  /// Returns the processed copy of [file], or [file] itself when it cannot be decoded (the type check still applies).
  Future<HqPickedFile> process(HqPickedFile file, HqImageOptions options);
}

/// Default processor using the pure-Dart `image` package, run off the UI isolate.
///
/// Re-encoding to JPEG writes a fresh file with no EXIF block, so location is stripped. HEIC cannot be decoded by the
/// `image` package; HEIC files come back unchanged (iOS `image_picker` converts them to JPEG when it is given a size or
/// quality, so prefer picking through the gallery).
class HqImagePackageProcessor implements HqImageProcessor {
  const HqImagePackageProcessor();

  @override
  Future<HqPickedFile> process(HqPickedFile file, HqImageOptions options) async {
    final source = await file.readBytes();
    final encoded = await compute(_reencode, _Job(source, options.maxDimension, options.quality));
    if (encoded == null) return file;
    final base = file.name.contains('.') ? file.name.substring(0, file.name.lastIndexOf('.')) : file.name;
    return HqPickedFile(name: '$base.jpg', size: encoded.length, bytes: encoded, mimeType: 'image/jpeg');
  }
}

class _Job {
  const _Job(this.bytes, this.maxDimension, this.quality);

  final Uint8List bytes;
  final int maxDimension;
  final int quality;
}

Uint8List? _reencode(_Job job) {
  final decoded = img.decodeImage(job.bytes);
  if (decoded == null) return null;
  var image = img.bakeOrientation(decoded);
  final longest = image.width > image.height ? image.width : image.height;
  if (longest > job.maxDimension) {
    image = image.width >= image.height
        ? img.copyResize(image, width: job.maxDimension)
        : img.copyResize(image, height: job.maxDimension);
  }
  return Uint8List.fromList(img.encodeJpg(image, quality: job.quality));
}
