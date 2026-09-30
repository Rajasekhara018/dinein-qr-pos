import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/branding/branding.dart';
import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';
import 'core/widgets/idle_guard.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Full screen with no system bars, so customers cannot leave the app.
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
  runApp(const ProviderScope(child: KioskApp()));
}

class KioskApp extends ConsumerWidget {
  const KioskApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final branding = ref.watch(brandingProvider).valueOrNull ?? Branding.defaults;
    return MaterialApp.router(
      title: 'Self-Order Kiosk',
      debugShowCheckedModeBanner: false,
      theme: buildKioskTheme(branding),
      routerConfig: ref.watch(appRouterProvider),
      builder: (context, child) => IdleGuard(child: child ?? const SizedBox.shrink()),
    );
  }
}
