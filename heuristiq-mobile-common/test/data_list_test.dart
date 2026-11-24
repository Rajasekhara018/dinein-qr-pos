import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

class _Row {
  const _Row(this.name, this.units);

  final String name;
  final int units;
}

final _columns = <HqColumn<_Row>>[
  HqColumn(key: 'name', label: 'Property', valueOf: (r) => r.name, sortable: true),
  HqColumn(key: 'units', label: 'Units', valueOf: (r) => '${r.units}', sortable: true),
];

List<_Row> _rows(int count) => [for (var i = 0; i < count; i++) _Row('Prop ${i.toString().padLeft(2, '0')}', i * 10)];

Widget _list({
  List<_Row>? items,
  bool isLoading = false,
  bool hasError = false,
  bool serverSide = false,
  int pageSize = 5,
  int page = 0,
  int? total,
  ValueChanged<int>? onPage,
  ValueChanged<HqSort?>? onSort,
  VoidCallback? onRetry,
  ValueChanged<_Row>? onTap,
  bool searchable = true,
}) {
  return SizedBox(
    height: 700,
    child: HqDataList<_Row>(
      items: items ?? _rows(12),
      columns: _columns,
      texts: HqDataListTexts.english(),
      isLoading: isLoading,
      hasError: hasError,
      serverSide: serverSide,
      pageSize: pageSize,
      page: page,
      totalItems: total,
      onPageChange: onPage,
      onSortChange: onSort,
      onRetry: onRetry,
      onRowTap: onTap,
      searchable: searchable,
      sizeOptions: const [5, 10],
    ),
  );
}

void main() {
  test('hqCompareCells compares numbers numerically and text case-insensitively', () {
    expect(hqCompareCells('9', '10'), lessThan(0));
    expect(hqCompareCells('1,000', '999'), greaterThan(0));
    expect(hqCompareCells('abc', 'ABD'), lessThan(0));
  });

  testWidgets('client side paging shows one page and moves on', (tester) async {
    await tester.pumpWidget(hqTestApp(_list()));
    expect(find.text('Prop 00'), findsOneWidget);
    expect(find.text('Prop 05'), findsNothing);
    expect(find.text('1-5 of 12'), findsOneWidget);
    await tester.tap(find.byTooltip('Next page'));
    await tester.pump();
    expect(find.text('Prop 05'), findsOneWidget);
    expect(find.text('6-10 of 12'), findsOneWidget);
  });

  testWidgets('search filters rows and resets to the first page', (tester) async {
    await tester.pumpWidget(hqTestApp(_list()));
    await tester.tap(find.byTooltip('Next page'));
    await tester.pump();
    await tester.enterText(find.byType(TextField), 'Prop 03');
    await tester.pump();
    // Once in the search box and once in the single remaining row.
    expect(find.text('Prop 03'), findsNWidgets(2));
    expect(find.text('Prop 05'), findsNothing);
    expect(find.text('1-1 of 1'), findsOneWidget);
  });

  testWidgets('sort cycles ascending, descending, none and reports each', (tester) async {
    final seen = <HqSort?>[];
    await tester.pumpWidget(hqTestApp(_list(onSort: seen.add)));
    Future<void> choose() async {
      await tester.tap(find.byTooltip('Sort by'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Units').last);
      await tester.pumpAndSettle();
    }

    await choose();
    await choose();
    expect(seen.last, const HqSort('units', HqSortDirection.desc));
    expect(find.text('Prop 11'), findsOneWidget);
    await choose();
    expect(seen.last, isNull);
    expect(find.text('Prop 00'), findsOneWidget);
  });

  testWidgets('server side mode reports page changes and does not slice the items', (tester) async {
    final pages = <int>[];
    await tester.pumpWidget(hqTestApp(_list(items: _rows(5), serverSide: true, page: 2, total: 40, onPage: pages.add)));
    expect(find.text('11-15 of 40'), findsOneWidget);
    await tester.tap(find.byTooltip('Previous page'));
    await tester.tap(find.byTooltip('Next page'));
    expect(pages, [1, 3]);
  });

  testWidgets('first page cannot go back and last page cannot go forward', (tester) async {
    await tester.pumpWidget(hqTestApp(_list(items: _rows(2), pageSize: 5)));
    expect(tester.widget<IconButton>(find.widgetWithIcon(IconButton, Icons.chevron_left)).onPressed, isNull);
    expect(tester.widget<IconButton>(find.widgetWithIcon(IconButton, Icons.chevron_right)).onPressed, isNull);
  });

  testWidgets('empty, loading and error states', (tester) async {
    await tester.pumpWidget(hqTestApp(_list(items: const [])));
    expect(find.text('Nothing to show'), findsOneWidget);
    await tester.pumpWidget(hqTestApp(_list(items: const [], isLoading: true)));
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    var retries = 0;
    await tester.pumpWidget(hqTestApp(_list(hasError: true, onRetry: () => retries++)));
    expect(find.text('Could not load the list'), findsOneWidget);
    await tester.tap(find.text('Retry'));
    expect(retries, 1);
  });

  testWidgets('row tap and custom cells', (tester) async {
    _Row? tapped;
    await tester.pumpWidget(hqTestApp(SizedBox(
      height: 600,
      child: HqDataList<_Row>(
        items: _rows(2),
        texts: HqDataListTexts.english(),
        onRowTap: (r) => tapped = r,
        columns: [
          HqColumn(key: 'name', label: 'Property', valueOf: (r) => r.name),
          HqColumn(key: 'units', label: 'Units', valueOf: (r) => '${r.units}', cellBuilder: (c, r) => HqStatusBadge(label: 'U${r.units}')),
        ],
      ),
    )));
    expect(find.text('U10'), findsOneWidget);
    await tester.tap(find.text('Prop 01'));
    expect(tapped?.name, 'Prop 01');
  });

  testWidgets('page size menu changes the page size', (tester) async {
    await tester.pumpWidget(hqTestApp(_list()));
    await tester.tap(find.byTooltip('Rows per page'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('10').last);
    await tester.pumpAndSettle();
    expect(find.text('1-10 of 12'), findsOneWidget);
  });
}
