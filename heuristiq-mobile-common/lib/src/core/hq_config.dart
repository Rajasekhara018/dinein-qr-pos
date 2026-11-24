import 'package:flutter/widgets.dart';

/// What the consuming app supplies once, the counterpart of `HqSharedLibConfig` / `HQ_AMOUNT_DEFAULT_CURRENCY` /
/// `HQ_PHONE_DEFAULT_COUNTRY` of hq-shared-lib.
@immutable
class HqConfig {
  const HqConfig({
    required this.gatewayUrl,
    this.defaultCurrency = 'INR',
    this.defaultPhoneCountry = 'IN',
    this.appChannel = 'CUSTOMER_APP',
  });

  /// Rider_ApiGateway base including the `/api` segment, e.g. `https://host/api`.
  final String gatewayUrl;

  /// ISO 4217 code an amount field starts on when it names none.
  final String defaultCurrency;

  /// ISO 3166 alpha-2 code a phone field starts on.
  final String defaultPhoneCountry;

  /// Value of `appChannel` on screen-view audit events (Rider_AuditService `AppChannel`).
  final String appChannel;
}

/// Makes a [HqConfig] available to the widgets below it.
class HqConfigScope extends InheritedWidget {
  const HqConfigScope({super.key, required this.config, required super.child});

  final HqConfig config;

  /// The config in scope, or null when the app did not provide one.
  static HqConfig? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<HqConfigScope>()?.config;

  @override
  bool updateShouldNotify(HqConfigScope oldWidget) => config != oldWidget.config;
}
