import 'package:flutter/material.dart';

import '../display/hq_basics.dart';
import '../privilege/hq_privilege_access.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_button.dart';

/// One destination of the bottom bar or side rail.
@immutable
class HqNavItem {
  const HqNavItem({required this.id, required this.label, required this.icon, this.selectedIcon, this.privileges = const <String>[], this.badgeCount, this.badgeLabel});

  final String id;

  /// Already-translated label.
  final String label;
  final IconData icon;
  final IconData? selectedIcon;

  /// Privilege codes (from `privilege.json`); the item shows when the user holds at least one. Empty means everyone. A UI
  /// hint: the gateway still checks every call.
  final List<String> privileges;
  final int? badgeCount;

  /// Screen-reader text for the badge ("3 new").
  final String? badgeLabel;
}

/// The items the user may see: those without a privilege requirement, or with one the user holds.
List<HqNavItem> hqVisibleNavItems(List<HqNavItem> items, HqPrivilegeAccess? access) =>
    [for (final item in items) if (access == null || item.privileges.isEmpty || access.can(item.privileges)) item];

/// A bottom navigation bar driven by privileges: items the user may not use are left out (the counterpart of the web menu
/// built from `permissionGuard` keys). With fewer than two visible items no bar is drawn (Material needs two).
class HqBottomNav extends StatelessWidget {
  const HqBottomNav({super.key, required this.items, required this.currentId, required this.onSelected});

  final List<HqNavItem> items;
  final String currentId;
  final ValueChanged<String> onSelected;

  @override
  Widget build(BuildContext context) {
    final visible = hqVisibleNavItems(items, HqPrivilegeScope.maybeOf(context));
    if (visible.length < 2) return const SizedBox.shrink();
    final tokens = context.hq;
    final selected = visible.indexWhere((i) => i.id == currentId);
    return NavigationBar(
      selectedIndex: selected < 0 ? 0 : selected,
      backgroundColor: tokens.surface,
      indicatorColor: tokens.primarySoft,
      onDestinationSelected: (i) => onSelected(visible[i].id),
      destinations: [
        for (final item in visible)
          NavigationDestination(
            icon: _icon(item, item.icon),
            selectedIcon: _icon(item, item.selectedIcon ?? item.icon),
            label: item.label,
          ),
      ],
    );
  }
}

Widget _icon(HqNavItem item, IconData icon) => item.badgeCount == null ? Icon(icon) : HqBadge(count: item.badgeCount, semanticsLabel: item.badgeLabel, child: Icon(icon));

/// The signed-in frame of the app: a body with a bottom bar on phones and a side rail from 600 px, both driven by privileges.
class HqAppShell extends StatelessWidget {
  const HqAppShell({super.key, required this.items, required this.currentId, required this.onSelected, required this.body, this.appBar, this.floatingActionButton, this.wideWidth = 600});

  final List<HqNavItem> items;
  final String currentId;
  final ValueChanged<String> onSelected;
  final Widget body;
  final PreferredSizeWidget? appBar;
  final Widget? floatingActionButton;

  /// Width from which the rail replaces the bottom bar.
  final double wideWidth;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final wide = MediaQuery.sizeOf(context).width >= wideWidth;
    if (!wide) {
      return Scaffold(appBar: appBar, body: body, floatingActionButton: floatingActionButton, bottomNavigationBar: HqBottomNav(items: items, currentId: currentId, onSelected: onSelected));
    }
    final visible = hqVisibleNavItems(items, HqPrivilegeScope.maybeOf(context));
    final selected = visible.indexWhere((i) => i.id == currentId);
    return Scaffold(
      appBar: appBar,
      floatingActionButton: floatingActionButton,
      body: Row(
        children: [
          if (visible.length >= 2)
            NavigationRail(
              selectedIndex: selected < 0 ? 0 : selected,
              labelType: NavigationRailLabelType.all,
              backgroundColor: tokens.surface,
              indicatorColor: tokens.primarySoft,
              onDestinationSelected: (i) => onSelected(visible[i].id),
              destinations: [for (final item in visible) NavigationRailDestination(icon: _icon(item, item.icon), selectedIcon: _icon(item, item.selectedIcon ?? item.icon), label: Text(item.label))],
            ),
          Expanded(child: body),
        ],
      ),
    );
  }
}

/// Pull-to-refresh around a scrollable [child] (the child must scroll, even when short: use
/// `AlwaysScrollableScrollPhysics`).
class HqPullToRefresh extends StatelessWidget {
  const HqPullToRefresh({super.key, required this.onRefresh, required this.child});

  final Future<void> Function() onRefresh;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    return RefreshIndicator(color: tokens.primary, backgroundColor: tokens.surface, onRefresh: onRefresh, child: child);
  }
}

/// A list that loads the next page as the user nears the end. Shows a spinner row while loading more and a retry row when the
/// last load failed (never an empty list that hides a failure). [onLoadMore] is called once per need, not repeatedly while
/// [isLoadingMore] is true.
class HqInfiniteList<T> extends StatefulWidget {
  const HqInfiniteList({
    super.key,
    required this.items,
    required this.itemBuilder,
    required this.hasMore,
    required this.onLoadMore,
    this.isLoadingMore = false,
    this.loadFailed = false,
    this.retryLabel = '',
    this.loadingLabel = '',
    this.onRefresh,
    this.separatorHeight = HqSpacing.md,
    this.threshold = 240,
    this.padding = EdgeInsets.zero,
  });

  final List<T> items;
  final Widget Function(BuildContext context, T item, int index) itemBuilder;
  final bool hasMore;
  final VoidCallback onLoadMore;
  final bool isLoadingMore;

  /// The last load failed: show the retry row instead of loading again.
  final bool loadFailed;
  final String retryLabel;
  final String loadingLabel;
  final Future<void> Function()? onRefresh;
  final double separatorHeight;

  /// Distance from the end (in pixels) at which the next page is requested.
  final double threshold;
  final EdgeInsets padding;

  @override
  State<HqInfiniteList<T>> createState() => _HqInfiniteListState<T>();
}

class _HqInfiniteListState<T> extends State<HqInfiniteList<T>> {
  final ScrollController _controller = ScrollController();

  @override
  void initState() {
    super.initState();
    _controller.addListener(_check);
    WidgetsBinding.instance.addPostFrameCallback((_) => _check());
  }

  @override
  void didUpdateWidget(HqInfiniteList<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    // A short first page may not fill the screen: keep asking until it scrolls or there is no more.
    WidgetsBinding.instance.addPostFrameCallback((_) => _check());
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _check() {
    if (!mounted || !widget.hasMore || widget.isLoadingMore || widget.loadFailed || !_controller.hasClients) return;
    final position = _controller.position;
    if (position.maxScrollExtent - position.pixels <= widget.threshold) widget.onLoadMore();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final footer = widget.isLoadingMore || widget.loadFailed;
    final list = ListView.separated(
      controller: _controller,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: widget.padding,
      itemCount: widget.items.length + (footer ? 1 : 0),
      separatorBuilder: (_, __) => SizedBox(height: widget.separatorHeight),
      itemBuilder: (context, index) {
        if (index < widget.items.length) return widget.itemBuilder(context, widget.items[index], index);
        if (widget.loadFailed) {
          return Center(child: HqButton(label: widget.retryLabel, appearance: HqButtonAppearance.outline, icon: Icons.refresh, onPressed: widget.onLoadMore));
        }
        return Padding(
          padding: const EdgeInsets.all(HqSpacing.lg),
          child: Center(child: CircularProgressIndicator(color: tokens.primary, semanticsLabel: widget.loadingLabel.isEmpty ? null : widget.loadingLabel)),
        );
      },
    );
    return widget.onRefresh == null ? list : HqPullToRefresh(onRefresh: widget.onRefresh!, child: list);
  }
}
