import 'package:flutter/widgets.dart';

import '../core/hq_api_client.dart';

/// Path (below the gateway base) of UAM's "my privileges" call, the same one the web app uses:
/// `GET {gateway}/v1/uam/identity/me/privileges`.
const String hqMyPrivilegesPath = '/v1/uam/identity/me/privileges';

/// Answers "may the signed-in user perform this action?" (the counterpart of `HqPrivilegeAccessService`).
///
/// A UI hint only: the gateway checks every API call. When no privileges are loaded yet (an older session, or UAM could not be
/// reached) the answer is "yes", so a screen behaves as it would without privileges and the gateway stays the real gate.
/// Listen to it (it is a [ChangeNotifier]) to rebuild when privileges change.
class HqPrivilegeAccess extends ChangeNotifier {
  Set<String>? _granted;

  /// The loaded permission codes, or null when none are loaded.
  Set<String>? get granted => _granted == null ? null : Set.unmodifiable(_granted!);

  /// Replaces the loaded privileges with [codes].
  void setPermissions(Iterable<String> codes) {
    _granted = Set<String>.of(codes);
    notifyListeners();
  }

  /// Forgets the privileges (sign-out).
  void clear() {
    _granted = null;
    notifyListeners();
  }

  /// Reads the `responseObject` of UAM's my-privileges answer (`{roles, permissions, privilegeIds}`).
  void loadFromResponse(Object? responseObject) {
    final permissions = responseObject is Map<String, dynamic> ? responseObject['permissions'] : null;
    if (permissions is List) {
      setPermissions(permissions.whereType<String>());
    } else {
      clear();
    }
  }

  /// Loads the privileges of the signed-in user from UAM through [client] (call after login and on token refresh).
  /// A failed call leaves the privileges unloaded (everything shown, the gateway decides) and rethrows nothing.
  Future<bool> refresh(HqApiClient client) async {
    try {
      loadFromResponse(await client.get(hqMyPrivilegesPath));
      return _granted != null;
    } on HqApiException {
      return false;
    }
  }

  /// True when the user holds at least one of [codes] (a code, or several; null or empty means "no requirement").
  bool can(Object? codes) {
    final wanted = switch (codes) {
      null => const <String>[],
      String code => <String>[code],
      Iterable<String> list => list.toList(),
      _ => throw ArgumentError('codes must be a String or an Iterable<String>'),
    };
    if (wanted.isEmpty) return true;
    final granted = _granted;
    return granted == null || wanted.any(granted.contains);
  }

  /// Whether the loaded privileges open the screen with [permission]: true or false when they decide it, null when they
  /// cannot (nothing loaded, or the key has no mapping) so the caller can fall back.
  bool? canOpen(String permission, Map<String, List<String>> mapping) {
    final required = mapping[permission];
    final granted = required == null ? null : _granted;
    if (required == null || granted == null) return null;
    return required.any(granted.contains);
  }
}

/// Makes a [HqPrivilegeAccess] available to the widgets below it and rebuilds dependents when it changes.
class HqPrivilegeScope extends InheritedNotifier<HqPrivilegeAccess> {
  const HqPrivilegeScope({super.key, required HqPrivilegeAccess access, required super.child}) : super(notifier: access);

  /// The access in scope; throws when the app did not place a [HqPrivilegeScope] above.
  static HqPrivilegeAccess of(BuildContext context) {
    final access = maybeOf(context);
    assert(access != null, 'No HqPrivilegeScope above this widget');
    return access!;
  }

  static HqPrivilegeAccess? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<HqPrivilegeScope>()?.notifier;
}
