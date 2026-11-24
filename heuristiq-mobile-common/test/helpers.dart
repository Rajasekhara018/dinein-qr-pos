import 'package:flutter/material.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

/// Pumps-ready app shell with the HqTheme and a privilege scope.
Widget hqTestApp(Widget child, {ThemeData? theme, HqPrivilegeAccess? access, GlobalKey<ScaffoldMessengerState>? messengerKey}) {
  final app = MaterialApp(
    theme: theme ?? HqTheme.light(),
    scaffoldMessengerKey: messengerKey,
    home: Scaffold(body: Padding(padding: const EdgeInsets.all(16), child: child)),
  );
  return access == null ? app : HqPrivilegeScope(access: access, child: app);
}
