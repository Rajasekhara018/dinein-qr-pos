import 'dart:io';
import 'dart:typed_data';

/// Smallest file DMS accepts (`HQ_DMS_MIN_FILE_BYTES`); anything smaller is almost certainly empty or broken.
const int hqDmsMinFileBytes = 50;

/// A file chosen by the user, before and after processing. Holds a [path] on the device and/or in-memory [bytes].
class HqPickedFile {
  const HqPickedFile({required this.name, required this.size, this.path, this.bytes, this.mimeType});

  final String name;
  final int size;
  final String? path;
  final Uint8List? bytes;

  /// MIME type when the picker knew it; otherwise it is inferred from the extension.
  final String? mimeType;

  /// The MIME type, from [mimeType] or the file extension (null when unknown).
  String? get effectiveMimeType => mimeType ?? hqMimeFromName(name);

  bool get isImage => (effectiveMimeType ?? '').startsWith('image/');

  /// The content: [bytes] when held in memory, otherwise read from [path].
  Future<Uint8List> readBytes() async {
    final held = bytes;
    if (held != null) return held;
    final location = path;
    if (location == null) throw StateError('HqPickedFile has neither bytes nor a path');
    return File(location).readAsBytes();
  }

  HqPickedFile copyWith({String? name, int? size, String? path, Uint8List? bytes, String? mimeType}) => HqPickedFile(
        name: name ?? this.name,
        size: size ?? this.size,
        path: path ?? this.path,
        bytes: bytes ?? this.bytes,
        mimeType: mimeType ?? this.mimeType,
      );
}

const Map<String, String> _mimeByExtension = <String, String>{
  'jpg': 'image/jpeg',
  'jpeg': 'image/jpeg',
  'png': 'image/png',
  'webp': 'image/webp',
  'heic': 'image/heic',
  'heif': 'image/heif',
  'gif': 'image/gif',
  'pdf': 'application/pdf',
  'doc': 'application/msword',
  'docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'xls': 'application/vnd.ms-excel',
  'xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'csv': 'text/csv',
  'txt': 'text/plain',
};

/// MIME type for a file name's extension, or null.
String? hqMimeFromName(String name) {
  final dot = name.lastIndexOf('.');
  if (dot < 0 || dot == name.length - 1) return null;
  return _mimeByExtension[name.substring(dot + 1).toLowerCase()];
}

/// Extension (no dot) for a MIME type, or null.
String? hqExtensionForMime(String mime) {
  final lower = mime.toLowerCase();
  for (final entry in _mimeByExtension.entries) {
    if (entry.value == lower) return entry.key;
  }
  return null;
}

bool _isImageEntry(String entry) {
  final e = entry.trim().toLowerCase();
  if (e.startsWith('image/')) return true;
  if (e.startsWith('.')) return (_mimeByExtension[e.substring(1)] ?? '').startsWith('image/');
  return false;
}

/// True when [accept] is not empty and every entry is an image type or image extension.
bool hqAcceptIsImagesOnly(List<String> accept) => accept.isNotEmpty && accept.every(_isImageEntry);

/// True when [accept] allows images (empty accept allows everything).
bool hqAcceptAllowsImages(List<String> accept) => accept.isEmpty || accept.any(_isImageEntry);

/// True when [accept] allows something other than images (documents).
bool hqAcceptAllowsDocuments(List<String> accept) => accept.isEmpty || accept.any((e) => !_isImageEntry(e));

/// The file extensions (no dot) behind [accept], for a system file manager filter. `image/*` expands to the common image
/// extensions; unknown MIME types are dropped.
List<String> hqAcceptExtensions(List<String> accept) {
  final result = <String>{};
  for (final raw in accept) {
    final entry = raw.trim().toLowerCase();
    if (entry.startsWith('.')) {
      result.add(entry.substring(1));
    } else if (entry == 'image/*') {
      result.addAll(const ['jpg', 'jpeg', 'png', 'webp', 'heic']);
    } else {
      final ext = hqExtensionForMime(entry);
      if (ext != null) result.add(ext);
    }
  }
  return result.toList();
}

/// Mirrors the web `isAcceptedFile`: an empty [accept] accepts any file; an entry is an extension (`.pdf`), an exact MIME
/// type (`application/pdf`) or a wildcard (`image/*`). The MIME type is inferred from the extension when the picker gave none.
bool hqIsAcceptedFile(HqPickedFile file, List<String> accept) {
  if (accept.isEmpty) return true;
  final name = file.name.toLowerCase();
  final mime = (file.effectiveMimeType ?? '').toLowerCase();
  for (final raw in accept) {
    final entry = raw.trim().toLowerCase();
    if (entry.isEmpty) continue;
    if (entry.startsWith('.')) {
      if (name.endsWith(entry)) return true;
    } else if (entry.endsWith('/*')) {
      if (mime.startsWith(entry.substring(0, entry.length - 1))) return true;
    } else if (mime == entry) {
      return true;
    }
  }
  return false;
}
