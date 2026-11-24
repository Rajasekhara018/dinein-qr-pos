import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/app_config.dart';
import '../../../core/network/dio_client.dart';
import '../domain/upsell_models.dart';

abstract class UpsellRepository {
  Future<List<UpsellRule>> fetchRules();
}

final upsellRepositoryProvider = Provider<UpsellRepository>((ref) {
  if (AppConfig.demoMode) return DemoUpsellRepository();
  return ApiUpsellRepository(ref.watch(dioProvider));
});

/// Upsell is a nudge, never a requirement: if the rules cannot be loaded the
/// customer simply sees no prompts and ordering carries on.
final upsellsProvider = FutureProvider<List<UpsellRule>>((ref) async {
  try {
    return await ref.watch(upsellRepositoryProvider).fetchRules();
  } catch (_) {
    return const [];
  }
});

class ApiUpsellRepository implements UpsellRepository {
  ApiUpsellRepository(this._dio);

  final Dio _dio;

  @override
  Future<List<UpsellRule>> fetchRules() async {
    final response = await _dio.get('/api/v1/kiosk/upsells');
    return [
      for (final r in response.data as List)
        UpsellRule.fromJson(Map<String, dynamic>.from(r as Map)),
    ];
  }
}

class DemoUpsellRepository implements UpsellRepository {
  @override
  Future<List<UpsellRule>> fetchRules() async => const [
        // Any burger -> a drink.
        UpsellRule(
          id: 1,
          triggerCategoryId: 1,
          suggestedItemId: 301,
          placement: UpsellPlacement.itemAdded,
          message: 'Add a drink?',
        ),
        // Any order -> a dessert at checkout.
        UpsellRule(
          id: 2,
          suggestedItemId: 401,
          placement: UpsellPlacement.checkout,
          message: 'Something sweet to finish?',
        ),
      ];
}
