import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_button.dart';
import '../ui/hq_form_field.dart';
import '../ui/hq_sheet.dart';
import 'hq_file_picker.dart';
import 'hq_file_uploader.dart';
import 'hq_image_processor.dart';
import 'hq_picked_file.dart';

/// Every text of [HqFileUpload] (the web `HqUploadTexts`), passed in by the app.
@immutable
class HqFileUploadTexts {
  const HqFileUploadTexts({
    required this.choose,
    required this.sheetTitle,
    required this.gallery,
    required this.camera,
    required this.files,
    required this.retry,
    required this.remove,
    required this.preview,
    required this.uploading,
    required this.uploaded,
    required this.close,
    required this.invalidType,
    required this.tooSmall,
    required this.tooLarge,
    required this.tooMany,
    required this.alreadyAttached,
    required this.uploadFailed,
    required this.permissionDeniedPhotos,
    required this.permissionDeniedCamera,
    required this.openSettings,
    required this.limits,
    required this.pickFailed,
  });

  /// English texts for tests and the example app; real apps pass translated ones.
  factory HqFileUploadTexts.english() => HqFileUploadTexts(
        choose: 'Choose file',
        sheetTitle: 'Add a file',
        gallery: 'Gallery',
        camera: 'Camera',
        files: 'Files',
        retry: 'Retry',
        remove: 'Remove',
        preview: 'Preview',
        uploading: 'Uploading',
        uploaded: 'Uploaded',
        close: 'Close',
        invalidType: (name, hints) => '$name: type not allowed ($hints)',
        tooSmall: (name) => '$name: file is empty or too small',
        tooLarge: (name, max) => '$name: larger than $max',
        tooMany: (max) => 'At most $max files',
        alreadyAttached: (name) => '$name: already added',
        uploadFailed: (name) => '$name: upload failed',
        permissionDeniedPhotos: 'Allow access to photos to continue',
        permissionDeniedCamera: 'Allow access to the camera to continue',
        openSettings: 'Open settings',
        limits: (hints, max) => '$hints, up to $max each',
        pickFailed: 'Could not open the camera or files. Try again.',
      );

  /// The picker could not open (no camera, another picker already open, a plugin failure).
  final String pickFailed;

  final String choose;
  final String sheetTitle;
  final String gallery;
  final String camera;
  final String files;
  final String retry;
  final String remove;
  final String preview;
  final String uploading;
  final String uploaded;
  final String close;
  final String Function(String name, String hints) invalidType;
  final String Function(String name) tooSmall;
  final String Function(String name, String max) tooLarge;
  final String Function(int max) tooMany;
  final String Function(String name) alreadyAttached;
  final String Function(String name) uploadFailed;
  final String permissionDeniedPhotos;
  final String permissionDeniedCamera;
  final String openSettings;

  /// The hint line: allowed types and the size limit.
  final String Function(String hints, String max) limits;
}

/// Formats a byte count for the size messages (`2.0 MB`). Units are universal symbols, not translated.
String hqFormatBytes(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
}

enum _ItemStatus { uploading, done, failed }

class _UploadItem {
  _UploadItem(this.file);

  HqPickedFile file;
  _ItemStatus status = _ItemStatus.uploading;
  double progress = 0;
  String? documentId;
  Uint8List? thumbnail;
}

/// File upload control for documents and photos (the counterpart of `hq-doc-upload` / `hq-multi-doc-upload`).
///
/// The choice of source follows [accept] (MIME types such as `image/*` / `application/pdf`, or extensions such as `.pdf`):
/// - only images: the gallery (a Gallery / Camera sheet when [allowCamera] is true);
/// - only documents: the system file manager, filtered to those types;
/// - a mix: a sheet with Gallery, Camera and Files.
///
/// Each file is checked for type, minimum size and [maxBytes] before upload, then sent through the injected [uploader];
/// the control shows progress, retry, remove and a preview. [onChanged] receives the DMS document ids of the files that
/// uploaded successfully, in order. Images are downscaled and stripped of EXIF location when [imageOptions] is set.
class HqFileUpload extends StatefulWidget {
  const HqFileUpload({
    super.key,
    required this.uploader,
    required this.picker,
    required this.texts,
    required this.onChanged,
    this.accept = const <String>[],
    this.typeHints = '',
    this.maxBytes = 0,
    this.minBytes = hqDmsMinFileBytes,
    this.maxFiles = 1,
    this.allowCamera = false,
    this.imageOptions,
    this.imageProcessor = const HqImagePackageProcessor(),
    this.label = '',
    this.required = false,
    this.enabled = true,
    this.error,
    this.onRejected,
  });

  final HqFileUploader uploader;
  final HqFilePicker picker;
  final HqFileUploadTexts texts;

  /// Document ids of the successfully uploaded files, whenever they change.
  final ValueChanged<List<String>> onChanged;

  /// MIME types or extensions; empty accepts any file.
  final List<String> accept;

  /// Shown in the "wrong type" and limits messages, e.g. `PDF, JPEG, PNG`.
  final String typeHints;

  /// Largest file in bytes; 0 means no limit.
  final int maxBytes;

  /// Smallest file in bytes (DMS refuses anything under 50).
  final int minBytes;

  /// More than one allows multi-select in the gallery and file manager.
  final int maxFiles;

  /// Offer the camera as a source when only images are accepted (always offered for mixed types).
  final bool allowCamera;

  /// Downscale and re-encode images (removing EXIF); null uploads images as picked.
  final HqImageOptions? imageOptions;
  final HqImageProcessor imageProcessor;
  final String label;
  final bool required;
  final bool enabled;
  final String? error;

  /// Called with each validation message (also shown under the control).
  final ValueChanged<String>? onRejected;

  @override
  State<HqFileUpload> createState() => _HqFileUploadState();
}

class _HqFileUploadState extends State<HqFileUpload> {
  final List<_UploadItem> _items = [];
  final List<String> _problems = [];
  String? _denied;
  bool _picking = false;

  bool get _multiple => widget.maxFiles > 1;
  bool get _full => _items.length >= widget.maxFiles;

  List<String> get _documentIds => [
        for (final item in _items)
          if (item.status == _ItemStatus.done && item.documentId != null) item.documentId!,
      ];

  void _notify() => widget.onChanged(_documentIds);

  Future<void> _choose() async {
    if (_picking || _full || !widget.enabled) return;
    final accept = widget.accept;
    final images = hqAcceptAllowsImages(accept);
    final documents = hqAcceptAllowsDocuments(accept);
    HqPickSource? source;
    if (hqAcceptIsImagesOnly(accept)) {
      source = widget.allowCamera ? await _askSource(const [HqPickSource.gallery, HqPickSource.camera]) : HqPickSource.gallery;
    } else if (!images) {
      source = HqPickSource.files;
    } else if (documents) {
      source = await _askSource(const [HqPickSource.gallery, HqPickSource.camera, HqPickSource.files]);
    }
    if (source != null) await _pick(source);
  }

  Future<HqPickSource?> _askSource(List<HqPickSource> sources) {
    final texts = widget.texts;
    String label(HqPickSource s) => switch (s) {
          HqPickSource.gallery => texts.gallery,
          HqPickSource.camera => texts.camera,
          HqPickSource.files => texts.files,
        };
    IconData icon(HqPickSource s) => switch (s) {
          HqPickSource.gallery => Icons.photo_library_outlined,
          HqPickSource.camera => Icons.photo_camera_outlined,
          HqPickSource.files => Icons.folder_open_outlined,
        };
    return showModalBottomSheet<HqPickSource>(
      context: context,
      showDragHandle: true,
      useSafeArea: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(HqSpacing.lg),
              child: Align(
                alignment: AlignmentDirectional.centerStart,
                child: Text(texts.sheetTitle, style: Theme.of(sheetContext).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
              ),
            ),
            for (final s in sources)
              ListTile(
                key: ValueKey<String>('hq-upload-source-${s.name}'),
                leading: Icon(icon(s)),
                title: Text(label(s)),
                onTap: () => Navigator.of(sheetContext).pop(s),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _pick(HqPickSource source) async {
    setState(() {
      _picking = true;
      _problems.clear();
      _denied = null;
    });
    List<HqPickedFile> picked = const [];
    try {
      picked = await widget.picker.pick(
        source,
        multiple: _multiple && source != HqPickSource.camera,
        accept: widget.accept,
        limit: widget.maxFiles - _items.length,
      );
    } on HqPermissionDeniedException catch (e) {
      if (mounted) {
        setState(() => _denied = e.source == HqPickSource.camera ? widget.texts.permissionDeniedCamera : widget.texts.permissionDeniedPhotos);
      }
    } on HqPickerException {
      _problems.add(widget.texts.pickFailed);
    }
    if (!mounted) return;
    setState(() => _picking = false);
    for (final file in picked) {
      await _accept(file);
      if (!mounted) return;
    }
    if (_problems.isNotEmpty) {
      setState(() {});
      for (final message in _problems) {
        widget.onRejected?.call(message);
      }
    }
  }

  Future<void> _accept(HqPickedFile original) async {
    final texts = widget.texts;
    if (_items.length >= widget.maxFiles) {
      _problems.add(texts.tooMany(widget.maxFiles));
      return;
    }
    if (!hqIsAcceptedFile(original, widget.accept)) {
      _problems.add(texts.invalidType(original.name, widget.typeHints));
      return;
    }
    if (_items.any((i) => i.file.name == original.name && i.file.size == original.size)) {
      _problems.add(texts.alreadyAttached(original.name));
      return;
    }
    var file = original;
    final options = widget.imageOptions;
    if (options != null && file.isImage) {
      try {
        file = await widget.imageProcessor.process(file, options);
      } on Object {
        // An image that cannot be processed is checked and uploaded as picked.
        file = original;
      }
    }
    if (widget.minBytes > 0 && file.size < widget.minBytes) {
      _problems.add(texts.tooSmall(original.name));
      return;
    }
    if (widget.maxBytes > 0 && file.size > widget.maxBytes) {
      _problems.add(texts.tooLarge(original.name, hqFormatBytes(widget.maxBytes)));
      return;
    }
    final item = _UploadItem(file);
    if (file.isImage) {
      try {
        item.thumbnail = await file.readBytes();
      } on Object {
        item.thumbnail = null;
      }
    }
    if (!mounted) return;
    setState(() => _items.add(item));
    await _upload(item);
  }

  Future<void> _upload(_UploadItem item) async {
    setState(() {
      item.status = _ItemStatus.uploading;
      item.progress = 0;
    });
    try {
      final id = await widget.uploader.upload(
        item.file,
        onProgress: (p) {
          if (mounted) setState(() => item.progress = p.clamp(0, 1).toDouble());
        },
      );
      if (!mounted) return;
      setState(() {
        item.documentId = id;
        item.status = _ItemStatus.done;
        item.progress = 1;
      });
      _notify();
    } on Object {
      if (!mounted) return;
      setState(() => item.status = _ItemStatus.failed);
    }
  }

  Future<void> _remove(_UploadItem item) async {
    final id = item.documentId;
    setState(() => _items.remove(item));
    _notify();
    if (id == null) return;
    try {
      await widget.uploader.remove(id);
    } on Object {
      if (mounted) setState(() => _problems.add(widget.texts.uploadFailed(item.file.name)));
    }
  }

  void _preview(_UploadItem item) {
    final bytes = item.thumbnail;
    if (bytes == null) return;
    HqModal.show<void>(
      context,
      title: item.file.name,
      closeLabel: widget.texts.close,
      builder: (_) => InteractiveViewer(child: Image.memory(bytes, fit: BoxFit.contain)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final texts = widget.texts;
    final limitText = widget.typeHints.isEmpty && widget.maxBytes <= 0
        ? null
        : widget.maxBytes > 0
            ? texts.limits(widget.typeHints, hqFormatBytes(widget.maxBytes))
            : widget.typeHints;
    return HqFormField(
      label: widget.label,
      required: widget.required,
      hint: limitText,
      error: widget.error,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final item in _items) _ItemTile(item: item, texts: texts, onRetry: () => _upload(item), onRemove: () => _remove(item), onPreview: () => _preview(item)),
          if (!_full)
            HqButton(
              label: texts.choose,
              icon: Icons.upload_file,
              appearance: HqButtonAppearance.outline,
              loading: _picking,
              onPressed: widget.enabled ? _choose : null,
            ),
          if (_denied != null)
            Padding(
              padding: const EdgeInsets.only(top: HqSpacing.sm),
              child: Row(
                children: [
                  Expanded(child: Text(_denied!, style: text.bodySmall?.copyWith(color: tokens.warn))),
                  HqButton(
                    label: texts.openSettings,
                    size: HqButtonSize.sm,
                    appearance: HqButtonAppearance.text,
                    onPressed: () => widget.picker.openSettings(),
                  ),
                ],
              ),
            ),
          for (final problem in _problems)
            Padding(
              padding: const EdgeInsets.only(top: HqSpacing.xs),
              child: Text(problem, style: text.bodySmall?.copyWith(color: tokens.warn)),
            ),
        ],
      ),
    );
  }
}

class _ItemTile extends StatelessWidget {
  const _ItemTile({required this.item, required this.texts, required this.onRetry, required this.onRemove, required this.onPreview});

  final _UploadItem item;
  final HqFileUploadTexts texts;
  final VoidCallback onRetry;
  final VoidCallback onRemove;
  final VoidCallback onPreview;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final isPdf = item.file.effectiveMimeType == 'application/pdf';
    final Widget leading = item.thumbnail != null
        ? ClipRRect(
            borderRadius: BorderRadius.circular(HqRadius.sm),
            child: Image.memory(item.thumbnail!, width: 48, height: 48, fit: BoxFit.cover, cacheWidth: 144, gaplessPlayback: true),
          )
        : Icon(isPdf ? Icons.picture_as_pdf_outlined : Icons.insert_drive_file_outlined, size: 40, color: tokens.muted);
    final String status = switch (item.status) {
      _ItemStatus.uploading => texts.uploading,
      _ItemStatus.done => texts.uploaded,
      _ItemStatus.failed => texts.uploadFailed(item.file.name),
    };
    return Container(
      margin: const EdgeInsets.only(bottom: HqSpacing.sm),
      padding: const EdgeInsets.all(HqSpacing.sm),
      decoration: BoxDecoration(
        color: tokens.surface,
        borderRadius: BorderRadius.circular(HqRadius.control),
        border: Border.all(color: item.status == _ItemStatus.failed ? tokens.warn : tokens.fieldBorder),
      ),
      child: Column(
        children: [
          Row(
            children: [
              InkWell(key: ValueKey<String>('hq-upload-preview-${item.file.name}'), onTap: item.thumbnail == null ? null : onPreview, child: leading),
              const SizedBox(width: HqSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(item.file.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: text.bodyMedium),
                    Text(
                      '${hqFormatBytes(item.file.size)}  -  $status',
                      style: text.bodySmall?.copyWith(color: item.status == _ItemStatus.failed ? tokens.warn : tokens.muted),
                    ),
                  ],
                ),
              ),
              if (item.status == _ItemStatus.failed)
                IconButton(tooltip: texts.retry, icon: const Icon(Icons.refresh), onPressed: onRetry),
              IconButton(
                tooltip: texts.remove,
                icon: const Icon(Icons.close),
                onPressed: item.status == _ItemStatus.uploading ? null : onRemove,
              ),
            ],
          ),
          if (item.status == _ItemStatus.uploading)
            Padding(
              padding: const EdgeInsets.only(top: HqSpacing.sm),
              child: LinearProgressIndicator(value: item.progress > 0 ? item.progress : null, color: tokens.primary, semanticsLabel: texts.uploading),
            ),
        ],
      ),
    );
  }
}
