import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/branding/branding.dart';
import 'core/realtime/menu_live_updates.dart';
import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';
import 'core/update/update_required.dart';
import 'core/widgets/idle_guard.dart';
import 'features/session/session_provider.dart';
import 'features/staff/presentation/staff_access.dart';
import 'l10n/app_localizations.dart';

Future<void> main() async {
  // An unhandled error must never leave a customer facing a frozen or blank screen. Flutter keeps
  // rendering after one, and Android relaunches the app if it dies, because it is the device's home app.
  runZonedGuarded(() async {
    WidgetsFlutterBinding.ensureInitialized();
    FlutterError.onError = (details) {
      FlutterError.presentError(details);
      debugPrint('Unhandled Flutter error: ${details.exceptionAsString()}');
    };
    // Full screen with no system bars, so customers cannot leave the app.
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    runApp(const ProviderScope(child: KioskApp()));
  }, (error, stack) => debugPrint('Unhandled error: $error\n$stack'));
}

class KioskApp extends ConsumerWidget {
  const KioskApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final branding = ref.watch(brandingProvider).valueOrNull ?? Branding.defaults;
    final largeText = ref.watch(accessibilityModeProvider);
    final updateRequired = ref.watch(updateRequiredProvider);
    ref.watch(menuLiveUpdatesProvider);

    return MaterialApp.router(
      title: 'Self-Order Kiosk',
      debugShowCheckedModeBanner: false,
      theme: buildKioskTheme(branding, highContrast: largeText),
      locale: ref.watch(localeProvider),
      supportedLocales: supportedLanguages,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      routerConfig: ref.watch(appRouterProvider),
      builder: (context, child) {
        final page = updateRequired
            ? const UpdateRequiredScreen()
            : IdleGuard(child: child ?? const SizedBox.shrink());
        return StaffAccessLayer(
          child: MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: TextScaler.linear(
                largeText ? KioskSizes.largeTextScale : KioskSizes.baseTextScale,
              ),
            ),
            child: page,
          ),
        );
      },
    );
  }
}
