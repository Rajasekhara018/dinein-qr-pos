import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/app_config.dart';
import '../../../core/network/dio_client.dart';
import '../domain/menu_models.dart';
import 'menu_cache.dart';

abstract class MenuRepository {
  Future<MenuData> fetchMenu();
}

final menuRepositoryProvider = Provider<MenuRepository>((ref) {
  if (AppConfig.demoMode) return DemoMenuRepository();
  return ApiMenuRepository(ref.watch(dioProvider), ref.watch(menuCacheProvider));
});

final menuProvider = FutureProvider<MenuData>((ref) {
  return ref.watch(menuRepositoryProvider).fetchMenu();
});

class ApiMenuRepository implements MenuRepository {
  ApiMenuRepository(this._dio, this._cache);

  final Dio _dio;
  final MenuCache _cache;

  /// Revalidates with the saved ETag, so an unchanged menu costs one tiny
  /// request. If the server cannot be reached, the saved menu is shown (marked
  /// stale) instead of an error page.
  @override
  Future<MenuData> fetchMenu() async {
    final cached = await _cache.read();
    try {
      final response = await _dio.get(
        '/api/v1/kiosk/menu',
        options: Options(
          headers: {if (cached?.etag != null) 'If-None-Match': cached!.etag},
          validateStatus: (s) => s == 200 || s == 304,
        ),
      );
      if (response.statusCode == 304 && cached != null) {
        return MenuData.fromJson(cached.json);
      }
      final json = Map<String, dynamic>.from(response.data as Map);
      await _cache.write(json, response.headers.value('etag'));
      return MenuData.fromJson(json);
    } on DioException catch (e) {
      if (cached != null && isConnectivityError(e)) {
        return MenuData.fromJson(cached.json).asStale();
      }
      throwApiError(e, fallback: 'Could not load the menu');
    }
  }
}

class DemoMenuRepository implements MenuRepository {
  @override
  Future<MenuData> fetchMenu() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
    return MenuData.fromJson(demoMenuJson);
  }
}

const demoMenuJson = <String, dynamic>{
  'version': 'demo',
  'pricesIncludeGst': true,
  'categories': [
    {
      'id': 1,
      'name': 'Burgers',
      'items': [
        {
          'id': 101,
          'name': 'Classic Veg Burger',
          'description': 'Crispy patty, lettuce, tomato and house sauce',
          'foodType': 'VEG',
          'displayPrice': 129,
          'gstPercent': 5,
          'available': true,
          'variants': [],
          'addons': [
            {'id': 1, 'name': 'Extra cheese', 'price': 20},
            {'id': 2, 'name': 'Peri peri dip', 'price': 15},
          ],
        },
        {
          'id': 102,
          'name': 'Chicken Zinger',
          'description': 'Spicy fried chicken with jalapeno mayo',
          'foodType': 'NON_VEG',
          'displayPrice': 179,
          'gstPercent': 5,
          'available': true,
          'variants': [],
          'addons': [
            {'id': 1, 'name': 'Extra cheese', 'price': 20},
          ],
        },
        {
          'id': 103,
          'name': 'Paneer Tikka Burger',
          'description': 'Tandoori paneer with mint chutney',
          'foodType': 'VEG',
          'displayPrice': 149,
          'gstPercent': 5,
          'available': false,
          'variants': [],
          'addons': [],
        },
      ],
    },
    {
      'id': 2,
      'name': 'Sides',
      'items': [
        {
          'id': 201,
          'name': 'Loaded Fries',
          'description': 'Fries with cheese sauce and jalapenos',
          'foodType': 'VEG',
          'displayPrice': 99,
          'gstPercent': 5,
          'available': true,
          'variants': [
            {'id': 1, 'name': 'Regular', 'price': 99, 'isDefault': true},
            {'id': 2, 'name': 'Large', 'price': 139, 'isDefault': false},
          ],
          'addons': [],
        },
        {
          'id': 202,
          'name': 'Chicken Nuggets',
          'description': 'Six crispy nuggets with a dip',
          'foodType': 'NON_VEG',
          'displayPrice': 119,
          'gstPercent': 5,
          'available': true,
          'variants': [],
          'addons': [],
        },
      ],
    },
    {
      'id': 3,
      'name': 'Drinks',
      'items': [
        {
          'id': 301,
          'name': 'Cold Coffee',
          'description': 'Chilled and creamy',
          'foodType': 'VEG',
          'displayPrice': 89,
          'gstPercent': 5,
          'available': true,
          'variants': [
            {'id': 1, 'name': 'Regular', 'price': 89, 'isDefault': true},
            {'id': 2, 'name': 'Large', 'price': 119, 'isDefault': false},
          ],
          'addons': [],
        },
        {
          'id': 302,
          'name': 'Masala Lemonade',
          'description': 'Fresh lemon with a spicy twist',
          'foodType': 'VEG',
          'displayPrice': 59,
          'gstPercent': 5,
          'available': true,
          'variants': [],
          'addons': [],
        },
      ],
    },
    {
      'id': 4,
      'name': 'Desserts',
      'items': [
        {
          'id': 401,
          'name': 'Choco Lava Cake',
          'description': 'Warm cake with a molten centre',
          'foodType': 'EGG',
          'displayPrice': 79,
          'gstPercent': 5,
          'available': true,
          'variants': [],
          'addons': [],
        },
      ],
    },
  ],
};
