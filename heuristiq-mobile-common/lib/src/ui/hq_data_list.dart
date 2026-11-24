import 'package:flutter/material.dart';

import '../core/hq_format.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_button.dart';
import 'hq_field.dart';

/// Sort direction of a column (the web `HqSortDirection`).
enum HqSortDirection { asc, desc }

/// The column a list is sorted by (the web `HqCommonTableSort`).
@immutable
class HqSort {
  const HqSort(this.key, this.direction);

  final String key;
  final HqSortDirection direction;

  @override
  bool operator ==(Object other) => other is HqSort && other.key == key && other.direction == direction;

  @override
  int get hashCode => Object.hash(key, direction);
}

/// One column of a [HqDataList] (the web `HqCommonTableColumn`). On a phone the first column is the row's title and
/// the rest are shown as label / value lines.
class HqColumn<T> {
  const HqColumn({
    required this.key,
    required this.label,
    required this.valueOf,
    this.sortable = false,
    this.cellBuilder,
  });

  /// Stable identifier, also the key reported in [HqSort].
  final String key;

  /// Already-translated heading.
  final String label;

  /// The cell text; sorting and the search filter use it.
  final String Function(T row) valueOf;

  /// Header tap sorts by the cell text; also reported through `onSortChange` for server-side sorting.
  final bool sortable;

  /// Markup for the cell (a badge, two lines) instead of text.
  final Widget Function(BuildContext context, T row)? cellBuilder;
}

/// Every text of [HqDataList] and [HqListFooter], passed in by the app (no translation inside the package).
@immutable
class HqDataListTexts {
  const HqDataListTexts({
    required this.empty,
    required this.error,
    required this.retry,
    required this.searchHint,
    required this.sortBy,
    required this.previous,
    required this.next,
    required this.rowsPerPage,
    required this.range,
    required this.rangeNoTotal,
    this.loading = '',
  });

  /// English texts for tests and the example app; real apps pass translated ones.
  factory HqDataListTexts.english() => HqDataListTexts(
        empty: 'Nothing to show',
        error: 'Could not load the list',
        retry: 'Retry',
        searchHint: 'Search',
        sortBy: 'Sort by',
        previous: 'Previous page',
        next: 'Next page',
        rowsPerPage: 'Rows per page',
        range: (from, to, total) => '$from-$to of $total',
        rangeNoTotal: (from, to) => '$from-$to',
      );

  final String empty;
  final String error;
  final String retry;
  final String searchHint;
  final String sortBy;
  final String previous;
  final String next;
  final String rowsPerPage;

  /// Accessibility label of the loading indicator.
  final String loading;

  /// "1-20 of 134".
  final String Function(int from, int to, int total) range;

  /// "1-20" when the total is unknown.
  final String Function(int from, int to) rangeNoTotal;
}

/// Previous / next, range and page-size controls under a list (the counterpart of `hq-table-footer`).
///
/// [page] is zero-based, as the services' `page` parameter is.
class HqListFooter extends StatelessWidget {
  const HqListFooter({
    super.key,
    required this.page,
    required this.size,
    required this.itemCount,
    required this.texts,
    this.hasMore = false,
    this.totalItems,
    this.loading = false,
    this.sizeOptions,
    this.onPageChange,
    this.onSizeChange,
  });

  final int page;
  final int size;

  /// Rows on the current page.
  final int itemCount;
  final HqDataListTexts texts;

  /// More pages follow (used when [totalItems] is unknown).
  final bool hasMore;
  final int? totalItems;
  final bool loading;
  final List<int>? sizeOptions;
  final ValueChanged<int>? onPageChange;
  final ValueChanged<int>? onSizeChange;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final from = itemCount == 0 ? 0 : page * size + 1;
    final to = page * size + itemCount;
    final total = totalItems;
    final label = total == null ? texts.rangeNoTotal(from, to) : texts.range(from, to, total);
    final canNext = !loading && (total == null ? hasMore : to < total);
    final canPrevious = !loading && page > 0;
    final options = sizeOptions;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: HqSpacing.sm),
      child: Row(
        children: [
          if (options != null && options.length > 1 && onSizeChange != null)
            PopupMenuButton<int>(
              tooltip: texts.rowsPerPage,
              initialValue: size,
              onSelected: onSizeChange,
              itemBuilder: (_) => [for (final option in options) PopupMenuItem<int>(value: option, child: Text('$option'))],
              child: Padding(
                padding: const EdgeInsets.all(HqSpacing.sm),
                child: Row(mainAxisSize: MainAxisSize.min, children: [Text('$size'), Icon(Icons.arrow_drop_down, color: tokens.muted)]),
              ),
            ),
          Expanded(
            child: Text(label, textAlign: TextAlign.center, style: text.bodySmall?.copyWith(color: tokens.muted)),
          ),
          IconButton(
            tooltip: texts.previous,
            icon: const Icon(Icons.chevron_left),
            onPressed: canPrevious && onPageChange != null ? () => onPageChange!(page - 1) : null,
          ),
          IconButton(
            tooltip: texts.next,
            icon: const Icon(Icons.chevron_right),
            onPressed: canNext && onPageChange != null ? () => onPageChange!(page + 1) : null,
          ),
        ],
      ),
    );
  }
}

/// Compares two cell texts: numerically when both read as numbers (grouping commas ignored), otherwise case-insensitively.
int hqCompareCells(String a, String b) {
  final na = num.tryParse(a.replaceAll(',', '').trim());
  final nb = num.tryParse(b.replaceAll(',', '').trim());
  if (na != null && nb != null) return na.compareTo(nb);
  return a.toLowerCase().compareTo(b.toLowerCase());
}

/// A paginated list of rows with search and sort: the phone counterpart of `hq-common-table` + `hq-table-footer`.
///
/// Two modes:
/// - **Client side** (default): pass every row in [items]; the list searches, sorts and pages them itself.
/// - **Server side** (`serverSide: true`): pass only the current page in [items] with [page], [pageSize] and
///   [totalItems] (or [hasMore]); the list reports [onPageChange], [onPageSizeChange], [onSortChange] and
///   [onSearchChanged] and the screen reloads.
///
/// It fills the height it is given, so place it in a bounded parent (a `Scaffold` body or an `Expanded`).
class HqDataList<T> extends StatefulWidget {
  const HqDataList({
    super.key,
    required this.items,
    required this.columns,
    required this.texts,
    this.isLoading = false,
    this.hasError = false,
    this.onRetry,
    this.onRefresh,
    this.onRowTap,
    this.rowActions,
    this.itemBuilder,
    this.searchable = false,
    this.serverSide = false,
    this.page = 0,
    this.pageSize = 20,
    this.totalItems,
    this.hasMore = false,
    this.onPageChange,
    this.onPageSizeChange,
    this.sizeOptions = const <int>[10, 20, 50],
    this.sort,
    this.onSortChange,
    this.onSearchChanged,
    this.header,
  });

  final List<T> items;
  final List<HqColumn<T>> columns;
  final HqDataListTexts texts;
  final bool isLoading;
  final bool hasError;
  final VoidCallback? onRetry;

  /// Pull-to-refresh handler.
  final Future<void> Function()? onRefresh;
  final ValueChanged<T>? onRowTap;

  /// Buttons under a row (use `HqButton`s wrapped in `HqHasPrivilege`).
  final Widget Function(BuildContext context, T row)? rowActions;

  /// Replaces the default row card.
  final Widget Function(BuildContext context, T row)? itemBuilder;
  final bool searchable;
  final bool serverSide;
  final int page;
  final int pageSize;
  final int? totalItems;
  final bool hasMore;
  final ValueChanged<int>? onPageChange;
  final ValueChanged<int>? onPageSizeChange;
  final List<int> sizeOptions;

  /// Server-side mode: the current sort. Client-side mode: the starting sort.
  final HqSort? sort;
  final ValueChanged<HqSort?>? onSortChange;
  final ValueChanged<String>? onSearchChanged;

  /// Shown above the toolbar and scrolls with nothing (e.g. a `HqStatCards`).
  final Widget? header;

  @override
  State<HqDataList<T>> createState() => _HqDataListState<T>();
}

class _HqDataListState<T> extends State<HqDataList<T>> {
  late int _page = widget.page;
  late int _size = widget.pageSize;
  late HqSort? _sort = widget.sort;
  String _query = '';

  @override
  void didUpdateWidget(HqDataList<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.serverSide) {
      _page = widget.page;
      _size = widget.pageSize;
      _sort = widget.sort;
    }
  }

  List<T> _filteredSorted() {
    var rows = List<T>.of(widget.items);
    final needle = _query.trim();
    if (needle.isNotEmpty) {
      rows = [
        for (final row in rows)
          if (widget.columns.any((c) => hqContainsIgnoreCase(c.valueOf(row), needle))) row,
      ];
    }
    final sort = _sort;
    if (sort != null) {
      final column = widget.columns.where((c) => c.key == sort.key).firstOrNull;
      if (column != null) {
        rows.sort((a, b) {
          final result = hqCompareCells(column.valueOf(a), column.valueOf(b));
          return sort.direction == HqSortDirection.asc ? result : -result;
        });
      }
    }
    return rows;
  }

  void _changeSort(String key) {
    final current = _sort;
    final HqSort? next;
    if (current == null || current.key != key) {
      next = HqSort(key, HqSortDirection.asc);
    } else if (current.direction == HqSortDirection.asc) {
      next = HqSort(key, HqSortDirection.desc);
    } else {
      next = null;
    }
    setState(() {
      _sort = next;
      if (!widget.serverSide) _page = 0;
    });
    widget.onSortChange?.call(next);
  }

  void _changeQuery(String query) {
    setState(() {
      _query = query;
      if (!widget.serverSide) _page = 0;
    });
    widget.onSearchChanged?.call(query);
  }

  void _changePage(int page) {
    if (widget.serverSide) {
      widget.onPageChange?.call(page);
    } else {
      setState(() => _page = page);
      widget.onPageChange?.call(page);
    }
  }

  void _changeSize(int size) {
    if (widget.serverSide) {
      widget.onPageSizeChange?.call(size);
    } else {
      setState(() {
        _size = size;
        _page = 0;
      });
      widget.onPageSizeChange?.call(size);
    }
  }

  @override
  Widget build(BuildContext context) {
    final server = widget.serverSide;
    final all = server ? widget.items : _filteredSorted();
    // Rows may shrink under the list (a refresh, a search): fall back to the first page instead of showing nothing.
    final page = server || _page * _size < all.length ? _page : 0;
    final shown = server ? all : all.skip(page * _size).take(_size).toList();
    final total = server ? widget.totalItems : all.length;
    final hasMore = server ? widget.hasMore : (page + 1) * _size < all.length;
    final sortable = widget.columns.where((c) => c.sortable).toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.header != null) widget.header!,
        if (widget.searchable || sortable.isNotEmpty) _toolbar(context, sortable),
        Expanded(child: _body(context, shown)),
        if (!widget.hasError && (shown.isNotEmpty || page > 0))
          HqListFooter(
            page: page,
            size: _size,
            itemCount: shown.length,
            hasMore: hasMore,
            totalItems: total,
            loading: widget.isLoading,
            sizeOptions: widget.sizeOptions,
            texts: widget.texts,
            onPageChange: _changePage,
            onSizeChange: _changeSize,
          ),
      ],
    );
  }

  Widget _toolbar(BuildContext context, List<HqColumn<T>> sortable) {
    final tokens = context.hq;
    return Padding(
      padding: const EdgeInsets.only(bottom: HqSpacing.md),
      child: Row(
        children: [
          if (widget.searchable)
            Expanded(
              child: HqField(showLabel: false, placeholder: widget.texts.searchHint, icon: Icons.search, onChanged: _changeQuery),
            )
          else
            const Spacer(),
          if (sortable.isNotEmpty)
            PopupMenuButton<String>(
              tooltip: widget.texts.sortBy,
              icon: Icon(Icons.sort, color: _sort == null ? tokens.muted : tokens.primary),
              onSelected: _changeSort,
              itemBuilder: (_) => [
                for (final column in sortable)
                  PopupMenuItem<String>(
                    value: column.key,
                    child: Row(
                      children: [
                        Expanded(child: Text(column.label)),
                        if (_sort?.key == column.key)
                          Icon(_sort!.direction == HqSortDirection.asc ? Icons.arrow_upward : Icons.arrow_downward, size: 16),
                      ],
                    ),
                  ),
              ],
            ),
        ],
      ),
    );
  }

  Widget _body(BuildContext context, List<T> rows) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    if (widget.hasError) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline, color: tokens.warn, size: 40),
            const SizedBox(height: HqSpacing.sm),
            Text(widget.texts.error, textAlign: TextAlign.center, style: text.bodyMedium),
            if (widget.onRetry != null) ...[
              const SizedBox(height: HqSpacing.md),
              HqButton(label: widget.texts.retry, appearance: HqButtonAppearance.outline, onPressed: widget.onRetry),
            ],
          ],
        ),
      );
    }
    if (widget.isLoading && rows.isEmpty) {
      return Center(child: CircularProgressIndicator(semanticsLabel: widget.texts.loading.isEmpty ? null : widget.texts.loading));
    }
    if (rows.isEmpty) {
      return Center(child: Text(widget.texts.empty, textAlign: TextAlign.center, style: text.bodyMedium?.copyWith(color: tokens.muted)));
    }
    final list = ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(),
      itemCount: rows.length,
      separatorBuilder: (_, __) => const SizedBox(height: HqSpacing.md),
      itemBuilder: (context, index) {
        final row = rows[index];
        return widget.itemBuilder != null ? widget.itemBuilder!(context, row) : _RowCard<T>(row: row, list: widget);
      },
    );
    final body = widget.onRefresh == null ? list : RefreshIndicator(onRefresh: widget.onRefresh!, child: list);
    if (!widget.isLoading) return body;
    return Column(
      children: [
        LinearProgressIndicator(color: tokens.primary, semanticsLabel: widget.texts.loading.isEmpty ? null : widget.texts.loading),
        Expanded(child: body),
      ],
    );
  }
}

class _RowCard<T> extends StatelessWidget {
  const _RowCard({required this.row, required this.list});

  final T row;
  final HqDataList<T> list;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final columns = list.columns;
    if (columns.isEmpty) return const SizedBox.shrink();
    final title = columns.first;
    final details = columns.skip(1).toList();
    final actions = list.rowActions?.call(context, row);
    final content = Padding(
      padding: const EdgeInsets.all(HqSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          title.cellBuilder != null
              ? title.cellBuilder!(context, row)
              : Text(title.valueOf(row), style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          for (final column in details)
            Padding(
              padding: const EdgeInsets.only(top: HqSpacing.sm),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(flex: 4, child: Text(column.label, style: text.bodySmall?.copyWith(color: tokens.muted))),
                  Expanded(
                    flex: 5,
                    child: Align(
                      alignment: AlignmentDirectional.centerEnd,
                      child: column.cellBuilder != null
                          ? column.cellBuilder!(context, row)
                          : Text(column.valueOf(row), textAlign: TextAlign.end, style: text.bodyMedium),
                    ),
                  ),
                ],
              ),
            ),
          if (actions != null) Padding(padding: const EdgeInsets.only(top: HqSpacing.md), child: actions),
        ],
      ),
    );
    return Material(
      color: tokens.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(HqRadius.panel),
        side: BorderSide(color: tokens.fieldBorder),
      ),
      clipBehavior: Clip.antiAlias,
      child: list.onRowTap == null ? content : InkWell(onTap: () => list.onRowTap!(row), child: content),
    );
  }
}
