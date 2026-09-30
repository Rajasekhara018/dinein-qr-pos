import 'package:equatable/equatable.dart';

enum FoodType { veg, nonVeg, egg, other }

double _num(Object? v) => (v as num?)?.toDouble() ?? 0;

FoodType _foodType(Object? v) {
  switch ((v as String?)?.toUpperCase()) {
    case 'VEG':
      return FoodType.veg;
    case 'NON_VEG':
    case 'NONVEG':
      return FoodType.nonVeg;
    case 'EGG':
      return FoodType.egg;
    default:
      return FoodType.other;
  }
}

class MenuVariant extends Equatable {
  const MenuVariant({
    required this.id,
    required this.name,
    required this.price,
    required this.isDefault,
  });

  final int id;
  final String name;
  final double price;
  final bool isDefault;

  factory MenuVariant.fromJson(Map<String, dynamic> json) => MenuVariant(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String? ?? '',
        price: _num(json['price']),
        isDefault: json['isDefault'] as bool? ?? false,
      );

  @override
  List<Object?> get props => [id, name, price, isDefault];
}

class MenuAddon extends Equatable {
  const MenuAddon({required this.id, required this.name, required this.price});

  final int id;
  final String name;
  final double price;

  factory MenuAddon.fromJson(Map<String, dynamic> json) => MenuAddon(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String? ?? '',
        price: _num(json['price']),
      );

  @override
  List<Object?> get props => [id, name, price];
}

class MenuItem extends Equatable {
  const MenuItem({
    required this.id,
    required this.name,
    required this.description,
    required this.foodType,
    required this.displayPrice,
    required this.gstPercent,
    required this.available,
    required this.variants,
    required this.addons,
    this.imageUrl,
  });

  final int id;
  final String name;
  final String description;
  final FoodType foodType;
  final double displayPrice;
  final double gstPercent;
  final bool available;
  final String? imageUrl;
  final List<MenuVariant> variants;
  final List<MenuAddon> addons;

  bool get isCustomisable => variants.isNotEmpty || addons.isNotEmpty;

  MenuVariant? get defaultVariant {
    if (variants.isEmpty) return null;
    return variants.firstWhere((v) => v.isDefault, orElse: () => variants.first);
  }

  factory MenuItem.fromJson(Map<String, dynamic> json) => MenuItem(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String? ?? '',
        description: json['description'] as String? ?? '',
        foodType: _foodType(json['foodType']),
        displayPrice: _num(json['displayPrice']),
        gstPercent: _num(json['gstPercent']),
        available: json['available'] as bool? ?? true,
        imageUrl: json['imageUrl'] as String?,
        variants: [
          for (final v in json['variants'] as List? ?? const [])
            MenuVariant.fromJson(Map<String, dynamic>.from(v as Map)),
        ],
        addons: [
          for (final a in json['addons'] as List? ?? const [])
            MenuAddon.fromJson(Map<String, dynamic>.from(a as Map)),
        ],
      );

  @override
  List<Object?> get props => [id, name, displayPrice, available, variants, addons];
}

class MenuCategory extends Equatable {
  const MenuCategory({
    required this.id,
    required this.name,
    required this.items,
  });

  final int id;
  final String name;
  final List<MenuItem> items;

  factory MenuCategory.fromJson(Map<String, dynamic> json) => MenuCategory(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String? ?? '',
        items: [
          for (final i in json['items'] as List? ?? const [])
            MenuItem.fromJson(Map<String, dynamic>.from(i as Map)),
        ],
      );

  @override
  List<Object?> get props => [id, name, items];
}

class MenuData extends Equatable {
  const MenuData({
    required this.version,
    required this.pricesIncludeGst,
    required this.categories,
    this.stale = false,
  });

  final String version;
  final bool pricesIncludeGst;
  final List<MenuCategory> categories;

  /// True when this is the last saved menu because the server could not be reached.
  final bool stale;

  MenuData asStale() => MenuData(
        version: version,
        pricesIncludeGst: pricesIncludeGst,
        categories: categories,
        stale: true,
      );

  /// The category an item belongs to, or null when the item is not on the menu.
  int? categoryIdOf(int itemId) {
    for (final c in categories) {
      if (c.items.any((i) => i.id == itemId)) return c.id;
    }
    return null;
  }

  MenuItem? itemById(int itemId) {
    for (final c in categories) {
      for (final i in c.items) {
        if (i.id == itemId) return i;
      }
    }
    return null;
  }

  factory MenuData.fromJson(Map<String, dynamic> json) => MenuData(
        version: json['version'] as String? ?? '',
        pricesIncludeGst: json['pricesIncludeGst'] as bool? ?? true,
        categories: [
          for (final c in json['categories'] as List? ?? const [])
            MenuCategory.fromJson(Map<String, dynamic>.from(c as Map)),
        ].where((c) => c.items.isNotEmpty).toList(),
      );

  @override
  List<Object?> get props => [version, pricesIncludeGst, categories, stale];
}
