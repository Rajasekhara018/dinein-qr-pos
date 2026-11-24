class AppConfig {
  AppConfig._();

  /// Sent as X-App-Id. Matches the Android applicationId.
  static const String appId = 'com.heuristq.dinein_counter';

  /// Override per build: --dart-define=COUNTER_API_BASE=https://uat.example.com
  static const String apiBase = String.fromEnvironment(
    'COUNTER_API_BASE',
    defaultValue: 'http://10.0.2.2:8080',
  );
}
