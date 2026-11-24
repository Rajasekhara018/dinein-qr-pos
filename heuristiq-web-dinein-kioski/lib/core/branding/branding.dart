import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../features/pairing/providers/device_provider.dart';
import '../config/app_config.dart';
import '../network/dio_client.dart';

enum PaymentMode { payAtCounter, upi }

/// Restaurant-controlled look and copy. Fetched from the backend so owners can
/// change it without an app update; [Branding.defaults] is the offline fallback.
class Branding extends Equatable {
  const Branding({
    required this.restaurantName,
    required this.primary,
    required this.secondary,
    required this.headline,
    required this.subtext,
    required this.startButtonLabel,
    required this.idleTimeoutSeconds,
    required this.paymentModes,
    this.logoUrl,
    this.backgroundUrl,
  });

  final String restaurantName;
  final Color primary;
  final Color secondary;
  final String headline;
  final String subtext;
  final String startButtonLabel;
  final int idleTimeoutSeconds;
  final List<PaymentMode> paymentModes;
  final String? logoUrl;
  final String? backgroundUrl;

  static const defaults = Branding(
    restaurantName: 'Welcome',
    primary: Color(0xFFD9480F),
    secondary: Color(0xFF212529),
    headline: 'Hungry? Order here',
    subtext: 'Fresh, hot and made just for you',
    startButtonLabel: 'Touch to order',
    idleTimeoutSeconds: 60,
    paymentModes: [PaymentMode.payAtCounter],
  );

  factory Branding.fromJson(Map<String, dynamic> json) {
    final d = defaults;
    return Branding(
      restaurantName: json['restaurantName'] as String? ?? d.restaurantName,
      primary: _color(json['primaryColor'], d.primary),
      secondary: _color(json['secondaryColor'], d.secondary),
      headline: json['headline'] as String? ?? d.headline,
      subtext: json['subtext'] as String? ?? d.subtext,
      startButtonLabel:
          json['startButtonLabel'] as String? ?? d.startButtonLabel,
      idleTimeoutSeconds:
          (json['idleTimeoutSeconds'] as num?)?.toInt() ?? d.idleTimeoutSeconds,
      paymentModes: _modes(json['paymentModes']) ?? d.paymentModes,
      logoUrl: json['logoUrl'] as String?,
      backgroundUrl: json['backgroundUrl'] as String?,
    );
  }

  static Color _color(Object? hex, Color fallback) {
    if (hex is! String) return fallback;
    final cleaned = hex.replaceFirst('#', '');
    final value = int.tryParse(cleaned.length == 6 ? 'FF$cleaned' : cleaned,
        radix: 16);
    return value == null ? fallback : Color(value);
  }

  static List<PaymentMode>? _modes(Object? raw) {
    if (raw is! List) return null;
    final modes = <PaymentMode>[];
    for (final name in raw) {
      switch (name) {
        case 'PAY_AT_COUNTER':
          modes.add(PaymentMode.payAtCounter);
        case 'UPI':
          modes.add(PaymentMode.upi);
      }
    }
    return modes.isEmpty ? null : modes;
  }

  @override
  List<Object?> get props => [
        restaurantName,
        primary,
        secondary,
        headline,
        subtext,
        startButtonLabel,
        idleTimeoutSeconds,
        paymentModes,
        logoUrl,
        backgroundUrl,
      ];
}

const _brandingCacheKey = 'kiosk.branding.json';

/// Fetches this restaurant's branding once the kiosk is paired (and again if it is re-paired), remembers the last
/// good copy, and falls back to it, then to the built-in defaults, so the welcome page always renders.
final brandingProvider = FutureProvider<Branding>((ref) async {
  if (AppConfig.demoMode) return Branding.defaults;
  final paired = ref.watch(deviceProvider.select((d) => d.paired));
  if (!paired) return Branding.defaults;

  final dio = ref.watch(dioProvider);
  try {
    final response = await dio.get('/api/v1/kiosk/branding');
    final json = Map<String, dynamic>.from(response.data as Map);
    await _saveBranding(json);
    return Branding.fromJson(json);
  } on DioException {
    return await _loadBranding() ?? Branding.defaults;
  }
});

Future<void> _saveBranding(Map<String, dynamic> json) async {
  try {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_brandingCacheKey, jsonEncode(json));
  } catch (_) {
    // The cache is a convenience only.
  }
}

Future<Branding?> _loadBranding() async {
  try {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_brandingCacheKey);
    if (raw == null) return null;
    return Branding.fromJson(Map<String, dynamic>.from(jsonDecode(raw) as Map));
  } catch (_) {
    return null;
  }
}
