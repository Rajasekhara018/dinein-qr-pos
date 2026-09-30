import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../../features/menu/data/menu_repository.dart';
import '../../features/pairing/providers/device_provider.dart';
import '../../features/upsell/data/upsell_repository.dart';
import '../config/app_config.dart';

/// Keeps the menu fresh: the backend broadcasts on /topic/menu whenever prices,
/// availability or items change, and the kiosk refetches (cheap, thanks to the ETag).
///
/// The topic is public and carries no data, so the kiosk connects without credentials.
/// If the socket drops it retries on its own; meanwhile the menu is refreshed at the
/// start of every customer session, so a missed push only delays an update.
final menuLiveUpdatesProvider = Provider<void>((ref) {
  final paired = ref.watch(deviceProvider.select((d) => d.paired));
  if (AppConfig.demoMode || !paired) return;

  late final StompClient client;
  client = StompClient(
    config: StompConfig(
      url: AppConfig.wsUrl,
      reconnectDelay: const Duration(seconds: 5),
      onConnect: (_) {
        client.subscribe(
          destination: '/topic/menu',
          callback: (_) {
            ref.invalidate(menuProvider);
            ref.invalidate(upsellsProvider);
          },
        );
      },
      onWebSocketError: (_) {},
      onStompError: (_) {},
    ),
  );
  client.activate();
  ref.onDispose(client.deactivate);
});
