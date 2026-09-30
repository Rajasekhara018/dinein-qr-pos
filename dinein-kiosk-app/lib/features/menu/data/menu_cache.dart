import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

class CachedMenu {
  const CachedMenu({required this.json, this.etag});

  final Map<String, dynamic> json;
  final String? etag;
}

final menuCacheProvider = Provider<MenuCache>((ref) => MenuCache());

/// The last menu the server sent, so a kiosk that loses Wi-Fi can still show
/// something and a restart does not need the network to reach the menu.
/// Storage problems are swallowed: the cache is a convenience, never a requirement.
class MenuCache {
  static const _jsonKey = 'kiosk.menu.json';
  static const _etagKey = 'kiosk.menu.etag';

  Future<CachedMenu?> read() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_jsonKey);
      if (raw == null) return null;
      return CachedMenu(
        json: Map<String, dynamic>.from(jsonDecode(raw) as Map),
        etag: prefs.getString(_etagKey),
      );
    } catch (_) {
      return null;
    }
  }

  Future<void> write(Map<String, dynamic> json, String? etag) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_jsonKey, jsonEncode(json));
      if (etag != null) {
        await prefs.setString(_etagKey, etag);
      } else {
        await prefs.remove(_etagKey);
      }
    } catch (_) {
      // Ignore: the next successful fetch tries again.
    }
  }
}
