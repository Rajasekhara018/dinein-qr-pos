import 'package:flutter/widgets.dart';

import 'hq_privilege_access.dart';

/// Route guard (the counterpart of the Angular `permissionGuard` with `data: { permission: '<key>' }`).
///
/// The [mapping] (screen permission key to the privilege codes that unlock it) belongs to the app, like
/// `HQ_PERMISSION_PRIVILEGES` does on the web. A screen opens when the user holds ANY listed code; when privileges are not
/// loaded or the key has no mapping the screen opens and the gateway stays the real gate.
abstract final class HqPermissionGuard {
  /// For a `go_router` `redirect:`: returns null to let the route open, or [redirectTo] to send the user away.
  ///
  /// ```dart
  /// GoRoute(path: '/portfolio', redirect: (c, s) => HqPermissionGuard.redirect(access, 'view_invest_portfolio', mapping, '/home'))
  /// ```
  static String? redirect(HqPrivilegeAccess access, String permission, Map<String, List<String>> mapping, String redirectTo) {
    return allows(access, permission, mapping) ? null : redirectTo;
  }

  /// True when the screen with [permission] may open.
  static bool allows(HqPrivilegeAccess access, String permission, Map<String, List<String>> mapping) =>
      access.canOpen(permission, mapping) ?? true;
}

/// Widget form of the guard for routes not managed by `go_router`: shows [child] or [deniedBuilder].
class HqPermissionGate extends StatelessWidget {
  const HqPermissionGate({
    super.key,
    required this.permission,
    required this.mapping,
    required this.child,
    required this.deniedBuilder,
  });

  final String permission;
  final Map<String, List<String>> mapping;
  final Widget child;
  final WidgetBuilder deniedBuilder;

  @override
  Widget build(BuildContext context) {
    final access = HqPrivilegeScope.maybeOf(context);
    final allowed = access == null || HqPermissionGuard.allows(access, permission, mapping);
    return allowed ? child : deniedBuilder(context);
  }
}
