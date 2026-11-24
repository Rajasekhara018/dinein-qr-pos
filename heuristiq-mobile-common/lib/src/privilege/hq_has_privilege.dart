import 'package:flutter/widgets.dart';

import 'hq_privilege_access.dart';

/// Shows [child] only when the user holds one of the privilege [codes] (the counterpart of `*hqHasPrivilege`).
///
/// ```dart
/// HqHasPrivilege(codes: ['INVEST_SUBSCRIPTION_CREATE'], child: HqButton(label: texts.invest, onPressed: ...))
/// ```
/// Use the exact names from `privilege.json`. Without a [HqPrivilegeScope] above, the child is shown.
class HqHasPrivilege extends StatelessWidget {
  const HqHasPrivilege({super.key, required this.codes, required this.child, this.fallback = const SizedBox.shrink()});

  /// One code or several; the user needs at least one.
  final List<String> codes;
  final Widget child;

  /// Shown instead of [child] when access is missing.
  final Widget fallback;

  @override
  Widget build(BuildContext context) {
    final access = HqPrivilegeScope.maybeOf(context);
    return access == null || access.can(codes) ? child : fallback;
  }
}
