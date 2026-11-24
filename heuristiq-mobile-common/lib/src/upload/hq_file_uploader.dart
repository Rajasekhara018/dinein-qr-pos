import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;

import '../core/hq_api_client.dart';
import 'hq_picked_file.dart';

/// Sends a picked file somewhere and returns the id the store gave it. The widget knows nothing about DMS, URLs or tokens.
abstract interface class HqFileUploader {
  /// Uploads [file]; reports progress from 0 to 1 through [onProgress]; completes with the stored document id.
  /// Throws on failure (the widget shows a retry).
  Future<String> upload(HqPickedFile file, {required void Function(double progress) onProgress});

  /// Deletes a stored document (called when the user removes an uploaded file).
  Future<void> remove(String documentId);
}

/// Uploads to Rider_DMS through the gateway, the same call as the web `HqDocumentService.upload`:
/// `POST {gateway}/v1/dms/documents` multipart `{file, category, ownerId, checksum, documentType}`; the response envelope's
/// `responseObject.id` is the document id. DMS takes one file per call and checks the SHA-256 `checksum` we send.
///
/// The `http` package cannot report upload progress, so progress jumps from 0 to 1 when the request ends; apps that need a
/// moving bar can supply another [HqFileUploader].
class HqDmsUploader implements HqFileUploader {
  HqDmsUploader({
    required String gatewayUrl,
    required this.tokenProvider,
    required this.category,
    required this.ownerId,
    this.documentType,
    http.Client? httpClient,
    this.timeout = const Duration(minutes: 2),
  })  : _base = '${gatewayUrl.replaceAll(RegExp(r'/+$'), '')}/v1/dms/documents',
        _http = httpClient ?? http.Client();

  final String _base;
  final HqTokenProvider tokenProvider;

  /// DMS category the document is filed under (for example `KYC`).
  final String category;

  /// Owner of the document. For KYC this is the signed-in investor; the service re-checks it against the token.
  final String ownerId;

  /// Organization document type code; DMS then enforces that type's formats, size, scan and retention.
  final String? documentType;
  final Duration timeout;
  final http.Client _http;

  @override
  Future<String> upload(HqPickedFile file, {required void Function(double progress) onProgress}) async {
    onProgress(0);
    final bytes = await file.readBytes();
    final request = http.MultipartRequest('POST', Uri.parse(_base))
      ..fields['category'] = category
      ..fields['ownerId'] = ownerId
      ..fields['checksum'] = sha256.convert(bytes).toString()
      ..files.add(http.MultipartFile.fromBytes('file', bytes, filename: file.name));
    if (documentType != null) request.fields['documentType'] = documentType!;
    await _authorize(request.headers);
    final response = await http.Response.fromStream(await _http.send(request).timeout(timeout));
    final envelope = _decode(response);
    if (response.statusCode < 200 || response.statusCode >= 300 || envelope?['success'] == false) {
      throw HqApiException(
        statusCode: response.statusCode,
        message: (envelope?['message'] as String?) ?? response.reasonPhrase ?? '',
        errorCode: envelope?['errorCode'] as String?,
      );
    }
    final payload = envelope?['responseObject'];
    final id = payload is Map<String, dynamic> ? payload['id'] : null;
    if (id == null) throw HqApiException(statusCode: response.statusCode, message: 'DMS answered without a document id');
    onProgress(1);
    return id.toString();
  }

  @override
  Future<void> remove(String documentId) async {
    final request = http.Request('DELETE', Uri.parse('$_base/metadata/$documentId'));
    await _authorize(request.headers);
    final response = await http.Response.fromStream(await _http.send(request).timeout(timeout));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HqApiException(statusCode: response.statusCode, message: response.reasonPhrase ?? '');
    }
  }

  Future<void> _authorize(Map<String, String> headers) async {
    final token = await tokenProvider();
    if (token != null && token.isNotEmpty) headers['Authorization'] = 'Bearer $token';
  }

  Map<String, dynamic>? _decode(http.Response response) {
    if (response.bodyBytes.isEmpty) return null;
    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      return decoded is Map<String, dynamic> ? decoded : null;
    } on FormatException {
      return null;
    }
  }
}
