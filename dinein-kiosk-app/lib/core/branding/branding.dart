import 'package:dio/dio.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

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

final brandingProvider = FutureProvider<Branding>((ref) async {
  if (AppConfig.demoMode) return Branding.defaults;
  try {
    final response = await ref.watch(dioProvider).get('/api/v1/kiosk/branding');
    return Branding.fromJson(Map<String, dynamic>.from(response.data as Map));
  } on DioException {
    return Branding.defaults;
  }
});
