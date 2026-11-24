import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:permission_handler/permission_handler.dart';

import 'hq_picked_file.dart';

/// Where a file comes from.
enum HqPickSource { gallery, camera, files }

/// Why choosing a file failed. The picker throws only these, never a plugin's `PlatformException`, so callers handle
/// two cases and show their own translated text.
sealed class HqPickerException implements Exception {
  const HqPickerException(this.source);

  final HqPickSource source;
}

/// Photos or camera access was refused by the user (or the system): offer "open settings".
class HqPermissionDeniedException extends HqPickerException {
  const HqPermissionDeniedException(super.source);

  @override
  String toString() => 'HqPermissionDeniedException($source)';
}

/// The picker could not run: no camera on the device, another picker already open, or a plugin failure. [code] is the
/// plugin's error code, for logs only (never shown to the user).
class HqPickerUnavailableException extends HqPickerException {
  const HqPickerUnavailableException(super.source, this.code);

  final String code;

  @override
  String toString() => 'HqPickerUnavailableException($source, $code)';
}

/// Chooses files on the device. The widget talks to this interface, so tests use a fake and apps may swap the plugin.
abstract interface class HqFilePicker {
  /// Returns the chosen files; an empty list when the user cancels. Throws [HqPermissionDeniedException] on a refusal
  /// and [HqPickerUnavailableException] when the picker cannot run.
  Future<List<HqPickedFile>> pick(
    HqPickSource source, {
    required bool multiple,
    required List<String> accept,
    int? limit,
  });

  /// Opens the system settings page of the app so the user can grant access. Returns false when it could not be opened.
  Future<bool> openSettings();
}

/// The real picker: `image_picker` for gallery and camera, `file_picker` for the system file manager.
///
/// - Gallery uses the Android 13+ system photo picker and iOS PHPicker, which need no storage/photo permission, so iOS
///   "limited access" and Android partial access work without extra handling.
/// - The camera needs the camera permission; a refusal becomes a [HqPermissionDeniedException].
class HqDeviceFilePicker implements HqFilePicker {
  HqDeviceFilePicker({ImagePicker? imagePicker}) : _images = imagePicker ?? ImagePicker();

  final ImagePicker _images;

  static const Set<String> _deniedCodes = <String>{'photo_access_denied', 'camera_access_denied', 'permission_denied'};

  @override
  Future<List<HqPickedFile>> pick(
    HqPickSource source, {
    required bool multiple,
    required List<String> accept,
    int? limit,
  }) async {
    try {
      switch (source) {
        case HqPickSource.gallery:
          if (multiple) {
            final shots = await _images.pickMultiImage(limit: (limit != null && limit >= 2) ? limit : null);
            return [for (final shot in shots) await _fromXFile(shot)];
          }
          final shot = await _images.pickImage(source: ImageSource.gallery);
          return shot == null ? const [] : [await _fromXFile(shot)];
        case HqPickSource.camera:
          final shot = await _images.pickImage(source: ImageSource.camera);
          return shot == null ? const [] : [await _fromXFile(shot)];
        case HqPickSource.files:
          final extensions = hqAcceptExtensions(accept);
          // file_picker 12: pickFiles answers several files (empty when cancelled), pickFile one (null when cancelled).
          final type = extensions.isEmpty ? FileType.any : FileType.custom;
          final allowed = extensions.isEmpty ? null : extensions;
          final List<PlatformFile> files;
          if (multiple) {
            files = await FilePicker.pickFiles(type: type, allowedExtensions: allowed);
          } else {
            final one = await FilePicker.pickFile(type: type, allowedExtensions: allowed);
            files = one == null ? const [] : [one];
          }
          return [for (final f in files) await _fromPlatformFile(f)];
      }
    } on PlatformException catch (e) {
      if (_deniedCodes.contains(e.code)) throw HqPermissionDeniedException(source);
      throw HqPickerUnavailableException(source, e.code);
    }
  }

  /// A picked file on the device keeps its path; one without (web) is read into memory.
  Future<HqPickedFile> _fromPlatformFile(PlatformFile file) async {
    final path = file.path;
    return HqPickedFile(
      name: file.name,
      size: await file.length(),
      path: path,
      bytes: path == null ? await file.readAsBytes() : null,
    );
  }

  Future<HqPickedFile> _fromXFile(XFile file) async =>
      HqPickedFile(name: file.name, size: await file.length(), path: file.path, mimeType: file.mimeType);

  @override
  Future<bool> openSettings() => openAppSettings();
}
