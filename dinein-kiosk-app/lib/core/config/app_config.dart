class AppConfig {
  AppConfig._();

  static const String appId = 'com.heuristq.dinein.kiosk';

  /// Override per build: --dart-define=KIOSK_API_BASE=https://uat.example.com
  static const String apiBase = String.fromEnvironment(
    'KIOSK_API_BASE',
    defaultValue: 'http://10.0.2.2:8080',
  );

  /// Demo mode serves an in-app menu and accepts any pairing code, so the app
  /// runs before the /api/v1/kiosk endpoints exist.
  /// Switch off with --dart-define=KIOSK_DEMO=false
  static const bool demoMode = bool.fromEnvironment(
    'KIOSK_DEMO',
    defaultValue: true,
  );

  static String? resolveUrl(String? url) {
    if (url == null || url.isEmpty) return null;
    if (url.startsWith('http')) return url;
    return '$apiBase$url';
  }
}
