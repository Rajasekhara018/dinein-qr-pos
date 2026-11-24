import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

import 'helpers.dart';

HqChartData _line({int count = 6, int series = 1}) => HqChartData(
      series: [
        for (var s = 0; s < series; s++)
          HqChartSeries(id: 's$s', label: 'Series $s', points: [for (var i = 0; i < count; i++) HqChartPoint(x: i.toDouble(), y: (i + s * 3) * 1000.0)]),
      ],
      xLabels: const ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
      valueFormatter: hqAmountFormatter(compact: true),
      semanticsLabel: 'Value trend',
    );

Widget _sized(Widget child) => SizedBox(width: 400, height: 700, child: SingleChildScrollView(child: child));

void main() {
  group('HqChartData', () {
    test('fromValues builds one series with index x', () {
      final data = HqChartData.fromValues(id: 'a', label: 'A', values: [1, 2, 3], xLabels: ['x', 'y', 'z']);
      expect(data.series.single.points.map((p) => p.x), [0, 1, 2]);
      expect(data.formatX(1), 'y');
      expect(data.formatX(7), '7');
      expect(data.categoryCount, 3);
      expect(data.minY, 1);
      expect(data.maxY, 3);
      expect(data.isEmpty, isFalse);
      expect(const HqChartData(series: []).isEmpty, isTrue);
    });

    test('downsampling keeps the first and last point and the peak', () {
      final points = [for (var i = 0; i < 1000; i++) HqChartPoint(x: i.toDouble(), y: i == 500 ? 9999 : (i % 7).toDouble())];
      final sampled = hqDownsample(points, 100);
      expect(sampled.length, 100);
      expect(sampled.first.x, 0);
      expect(sampled.last.x, 999);
      expect(sampled.any((p) => p.y == 9999), isTrue);
      expect(hqDownsample(points.take(50).toList(), 100).length, 50);
      final data = HqChartData(series: [HqChartSeries(id: 'a', label: 'A', points: points)]).downsampled(120);
      expect(data.series.single.points.length, 120);
    });

    test('describe reads the label then the series values', () {
      final text = _line(count: 3).describe();
      expect(text, startsWith('Value trend. Series 0: Jan'));
      expect(text, contains('Mar'));
    });
  });

  group('compact formatting', () {
    test('Indian style uses lakh and crore', () {
      expect(hqFormatCompact(12500000), '1.3 Cr');
      expect(hqFormatCompact(1250000), '12.5 L');
      expect(hqFormatCompact(25000), '25K');
      expect(hqFormatCompact(900), '900');
      expect(hqFormatCompact(-150000), '-1.5 L');
      expect(hqFormatCompact(100000), '1 L');
    });

    test('international style', () {
      expect(hqFormatCompact(2500000, indian: false), '2.5M');
      expect(hqFormatCompact(3200000000, indian: false), '3.2B');
    });

    test('units can be localized', () {
      expect(hqFormatCompact(12500000, units: const HqCompactUnits(crore: 'करोड़')), '1.3 करोड़');
    });

    test('money compact and the amount formatter', () {
      expect(hqFormatMoneyCompact(25000000), '₹2.5 Cr');
      expect(hqFormatMoneyCompact(-150000), '-₹1.5 L');
      expect(hqFormatMoneyCompact(2500000, currency: 'USD'), r'$2.5M');
      expect(hqAmountFormatter()(1234567.5), '₹12,34,567.50');
      expect(hqAmountFormatter(compact: true)(1234567.5), '₹12.3 L');
    });
  });

  group('palette', () {
    test('eight categorical slots in both themes, different steps for dark', () {
      expect(HqTokens.light.chartCategorical.length, 8);
      expect(HqTokens.dark.chartCategorical.length, 8);
      expect(HqTokens.light.chartCategorical.first, isNot(HqTokens.dark.chartCategorical.first));
      expect(HqTokens.dark.chartSequential.first, HqTokens.light.chartSequential.last);
    });

    testWidgets('slot lookup never cycles: the ninth is the neutral Other colour', (tester) async {
      late BuildContext captured;
      await tester.pumpWidget(MaterialApp(theme: HqTheme.light(), home: Builder(builder: (c) {
        captured = c;
        return const SizedBox();
      })));
      expect(captured.chartColorAt(0), HqTokens.light.chartCategorical[0]);
      expect(captured.chartColorAt(7), HqTokens.light.chartCategorical[7]);
      expect(captured.chartColorAt(8), HqTokens.light.chartOther);
      expect(captured.chartColorAt(2, override: Colors.black), Colors.black);
    });
  });

  group('line, area, sparkline', () {
    testWidgets('line chart renders, shows a legend for several series and describes itself', (tester) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(hqTestApp(_sized(HqLineChart(data: _line(series: 2)))));
      expect(find.byType(LineChart), findsOneWidget);
      expect(find.text('Series 0'), findsOneWidget);
      expect(find.text('Series 1'), findsOneWidget);
      expect(find.bySemanticsLabel(RegExp('Value trend')), findsOneWidget);
      semantics.dispose();
    });

    testWidgets('a single series has no legend', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqLineChart(data: _line()))));
      expect(find.text('Series 0'), findsNothing);
    });

    testWidgets('area chart fills below the line', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqAreaChart(data: _line()))));
      final chart = tester.widget<LineChart>(find.byType(LineChart));
      expect(chart.data.lineBarsData.single.belowBarData.show, isTrue);
    });

    testWidgets('long series are downsampled before drawing', (tester) async {
      final big = HqChartData(series: [HqChartSeries(id: 'a', label: 'A', points: [for (var i = 0; i < 5000; i++) HqChartPoint(x: i.toDouble(), y: (i % 50).toDouble())])]);
      await tester.pumpWidget(hqTestApp(_sized(HqLineChart(data: big, maxPoints: 150))));
      expect(tester.widget<LineChart>(find.byType(LineChart)).data.lineBarsData.single.spots.length, 150);
    });

    testWidgets('empty data shows the passed-in text and no chart', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(const HqLineChart(data: HqChartData(series: []), emptyText: 'No history yet'))));
      expect(find.text('No history yet'), findsOneWidget);
      expect(find.byType(LineChart), findsNothing);
    });

    testWidgets('sparkline draws from two values and nothing from one', (tester) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(hqTestApp(const HqSparkline(values: [1, 3, 2, 5], semanticsLabel: 'Rising')));
      expect(find.byType(LineChart), findsOneWidget);
      expect(find.bySemanticsLabel('Rising'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(const HqSparkline(values: [1])));
      expect(find.byType(LineChart), findsNothing);
      semantics.dispose();
    });
  });

  group('HqNavChart', () {
    testWidgets('marks valuation dates and names them in the legend', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqNavChart(
        seriesLabel: 'NAV per unit',
        valuationLabel: 'Valuation',
        semanticsLabel: 'NAV trend',
        points: [
          HqNavPoint(at: DateTime.utc(2026, 1, 1), nav: 100, valuation: true),
          HqNavPoint(at: DateTime.utc(2026, 2, 1), nav: 102),
          HqNavPoint(at: DateTime.utc(2026, 3, 1), nav: 105, valuation: true),
        ],
      ))));
      expect(find.byType(LineChart), findsOneWidget);
      expect(find.text('Valuation'), findsOneWidget);
      expect(find.text('NAV per unit'), findsOneWidget);
      final bar = tester.widget<LineChart>(find.byType(LineChart)).data.lineBarsData.single;
      expect(bar.spots.length, 3);
      expect(bar.dotData.checkToShowDot(bar.spots[0], bar), isTrue);
      expect(bar.dotData.checkToShowDot(bar.spots[1], bar), isFalse);
    });

    testWidgets('unsorted points are sorted by date', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqNavChart(
        seriesLabel: 'NAV',
        valuationLabel: 'V',
        points: [HqNavPoint(at: DateTime.utc(2026, 3, 1), nav: 3), HqNavPoint(at: DateTime.utc(2026, 1, 1), nav: 1)],
      ))));
      final spots = tester.widget<LineChart>(find.byType(LineChart)).data.lineBarsData.single.spots;
      expect(spots.first.y, 1);
      expect(spots.last.y, 3);
    });
  });

  group('HqBarChart', () {
    testWidgets('grouped bars: one rod per series', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqBarChart(data: _line(series: 2)))));
      final groups = tester.widget<BarChart>(find.byType(BarChart)).data.barGroups;
      expect(groups.length, 6);
      expect(groups.first.barRods.length, 2);
    });

    testWidgets('stacked bars: one rod with stack items summing to the total', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqBarChart(data: _line(series: 2), stacked: true))));
      final rod = tester.widget<BarChart>(find.byType(BarChart)).data.barGroups[2].barRods.single;
      expect(rod.rodStackItems.length, 2);
      expect(rod.toY, rod.rodStackItems.last.toY);
      expect(rod.toY, 2000 + 5000);
    });

    testWidgets('horizontal bars list the categories with values', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqBarChart(data: _line(count: 3), horizontal: true))));
      expect(find.byType(BarChart), findsNothing);
      expect(find.text('Jan'), findsOneWidget);
      expect(find.text('₹2K'), findsOneWidget);
    });

    testWidgets('horizontal stacked shows totals', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqBarChart(data: _line(count: 2, series: 2), horizontal: true, stacked: true))));
      expect(find.text('₹3K'), findsOneWidget);
    });

    testWidgets('empty shows the text', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(const HqBarChart(data: HqChartData(series: []), emptyText: 'Nothing paid'))));
      expect(find.text('Nothing paid'), findsOneWidget);
    });
  });

  group('pie and donut', () {
    HqChartData allocation(int slices) => HqChartData(
          series: [HqChartSeries(id: 'a', label: 'Allocation', points: [for (var i = 0; i < slices; i++) HqChartPoint(x: i.toDouble(), y: 10)])],
          xLabels: [for (var i = 0; i < slices; i++) 'Property $i'],
          valueFormatter: hqAmountFormatter(compact: true),
        );

    testWidgets('one series with several points gives a slice per point with shares in the legend', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqPieChart(data: allocation(4)))));
      final sections = tester.widget<PieChart>(find.byType(PieChart)).data.sections;
      expect(sections.length, 4);
      expect(find.text('Property 0  25.0%'), findsOneWidget);
    });

    testWidgets('several series give a slice per series total', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqPieChart(data: _line(count: 2, series: 3)))));
      expect(tester.widget<PieChart>(find.byType(PieChart)).data.sections.length, 3);
    });

    testWidgets('more than eight slices fold into Other', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqPieChart(data: allocation(11), otherLabel: 'Other'))));
      final sections = tester.widget<PieChart>(find.byType(PieChart)).data.sections;
      expect(sections.length, 8);
      expect(sections.last.value, 40);
      expect(sections.last.color, HqTokens.light.chartOther);
      expect(find.textContaining('Other'), findsOneWidget);
    });

    testWidgets('donut has a hole and shows the centre text', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqDonutChart(data: allocation(3), centerLabel: 'Total', centerValue: '₹30'))));
      expect(tester.widget<PieChart>(find.byType(PieChart)).data.centerSpaceRadius, greaterThan(0));
      expect(find.text('Total'), findsOneWidget);
      expect(find.text('₹30'), findsOneWidget);
    });

    testWidgets('custom percent formatter and zero or negative values are ignored', (tester) async {
      final data = HqChartData(series: [
        const HqChartSeries(id: 'a', label: 'A', points: [HqChartPoint(x: 0, y: 30)]),
        const HqChartSeries(id: 'b', label: 'B', points: [HqChartPoint(x: 0, y: 0)]),
        const HqChartSeries(id: 'c', label: 'C', points: [HqChartPoint(x: 0, y: -5)]),
        const HqChartSeries(id: 'd', label: 'D', points: [HqChartPoint(x: 0, y: 10)]),
      ]);
      await tester.pumpWidget(hqTestApp(_sized(HqPieChart(data: data, percentFormatter: (p) => '${p.round()} pct'))));
      expect(tester.widget<PieChart>(find.byType(PieChart)).data.sections.length, 2);
      expect(find.text('A  75 pct'), findsOneWidget);
    });

    testWidgets('empty data shows the text', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(const HqPieChart(data: HqChartData(series: []), emptyText: 'No holdings'))));
      expect(find.text('No holdings'), findsOneWidget);
    });
  });

  group('gauge and progress', () {
    test('fraction clamps to 0..1', () {
      expect(const HqGaugeChart(value: 150, max: 100).fraction, 1);
      expect(const HqGaugeChart(value: -5).fraction, 0);
      expect(const HqGaugeChart(value: 5, max: 0).fraction, 0);
      expect(const HqGaugeChart(value: 25).fraction, 0.25);
    });

    testWidgets('gauge shows value text and label and is described to screen readers', (tester) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(hqTestApp(const HqGaugeChart(value: 72, valueText: '72%', label: 'Funded', semanticsLabel: 'Funded 72 percent')));
      expect(find.text('72%'), findsOneWidget);
      expect(find.text('Funded'), findsOneWidget);
      expect(find.bySemanticsLabel('Funded 72 percent'), findsOneWidget);
      semantics.dispose();
    });

    testWidgets('progress bar fills in proportion and uses the success colour when full', (tester) async {
      await tester.pumpWidget(hqTestApp(const Column(children: [
        HqProgressBar(key: Key('half'), fraction: 0.5, label: 'Raised', valueText: '50%'),
        HqProgressBar(key: Key('full'), fraction: 1.4, label: 'Done', valueText: '100%'),
      ])));
      expect(find.text('Raised'), findsOneWidget);
      final fills = tester.widgetList<Container>(find.descendant(of: find.byKey(const Key('full')), matching: find.byType(Container))).toList();
      expect(fills.any((c) => c.color == HqTokens.light.success), isTrue);
    });

    testWidgets('funding progress shows raised, target and footnote', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqFundingProgress(
        fraction: 0.4,
        title: 'Round 1',
        raisedText: '₹4 Cr raised',
        targetText: '₹10 Cr target',
        percentText: '40%',
        footnote: '120 investors',
      )));
      expect(find.text('Round 1'), findsOneWidget);
      expect(find.text('₹4 Cr raised'), findsOneWidget);
      expect(find.text('₹10 Cr target'), findsOneWidget);
      expect(find.text('120 investors'), findsOneWidget);
      expect(find.text('40%'), findsOneWidget);
    });
  });

  group('HqHeatmap', () {
    test('bucket maps values onto the ramp', () {
      expect(HqHeatmap.bucket(0, 100, 7), 0);
      expect(HqHeatmap.bucket(100, 100, 7), 6);
      expect(HqHeatmap.bucket(1, 100, 7), 1);
      expect(HqHeatmap.bucket(50, 0, 7), 0);
    });

    Widget heatmap({void Function(DateTime, double)? onTap}) => HqHeatmap(
          months: [DateTime.utc(2026, 11, 1)],
          monthTitle: (m) => 'M${m.month}',
          weekdayLabels: const ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'],
          lessLabel: 'Less',
          moreLabel: 'More',
          valueFormatter: hqAmountFormatter(),
          onDayTap: onTap,
          values: {DateTime.utc(2026, 11, 5): 1250.5, DateTime.utc(2026, 11, 20): 500},
        );

    testWidgets('draws every day of the month, the headings and the scale captions', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(heatmap())));
      expect(find.text('M11'), findsOneWidget);
      expect(find.text('Mo'), findsOneWidget);
      expect(find.text('30'), findsOneWidget);
      expect(find.text('31'), findsNothing);
      expect(find.text('Less'), findsOneWidget);
      expect(find.text('More'), findsOneWidget);
    });

    testWidgets('a day with a value reports it when tapped and has a tooltip', (tester) async {
      DateTime? day;
      double? value;
      await tester.pumpWidget(hqTestApp(_sized(heatmap(onTap: (d, v) {
        day = d;
        value = v;
      }))));
      await tester.tap(find.text('5'));
      expect(day, DateTime.utc(2026, 11, 5));
      expect(value, 1250.5);
      expect(find.byTooltip('2026-11-05: ₹1,250.50'), findsOneWidget);
    });

    testWidgets('a day without a value is not tappable', (tester) async {
      var taps = 0;
      await tester.pumpWidget(hqTestApp(_sized(heatmap(onTap: (_, __) => taps++))));
      await tester.tap(find.text('6'));
      expect(taps, 0);
    });
  });

  group('scatter and radar', () {
    testWidgets('scatter draws a spot per point with a legend', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqScatter(data: _line(series: 2)))));
      expect(tester.widget<ScatterChart>(find.byType(ScatterChart)).data.scatterSpots.length, 12);
      expect(find.text('Series 1'), findsOneWidget);
    });

    testWidgets('scatter empty text', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(const HqScatter(data: HqChartData(series: []), emptyText: 'No points'))));
      expect(find.text('No points'), findsOneWidget);
    });

    testWidgets('radar uses the labels as axes and pins the scale', (tester) async {
      final data = HqChartData(
        series: [HqChartSeries(id: 'risk', label: 'Risk profile', points: [for (final v in [3.0, 4.0, 2.0, 5.0]) HqChartPoint(x: 0, y: v)])],
        xLabels: const ['Market', 'Credit', 'Liquidity', 'Legal'],
      );
      await tester.pumpWidget(hqTestApp(_sized(HqRadar(data: data, maxValue: 5))));
      final radar = tester.widget<RadarChart>(find.byType(RadarChart)).data;
      expect(radar.dataSets.length, 2);
      expect(radar.dataSets.first.dataEntries.first.value, 5);
      expect(radar.getTitle!(1, 0).text, 'Credit');
    });

    testWidgets('radar with fewer than three axes shows the empty text', (tester) async {
      await tester.pumpWidget(hqTestApp(_sized(HqRadar(data: _line(count: 2), emptyText: 'Not enough data'))));
      expect(find.text('Not enough data'), findsOneWidget);
    });
  });

  group('dashboard', () {
    testWidgets('KPI tile shows value, delta and the right tone for a good and a bad move', (tester) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(hqTestApp(const Column(children: [
        HqKpiTile(label: 'Value', value: '₹1.2 Cr', deltaText: '+4.2%', trend: HqTrend.up, sparkline: [1, 2, 3]),
        HqKpiTile(label: 'Fees', value: '₹50K', deltaText: '+10%', trend: HqTrend.up, positiveIsGood: false),
      ])));
      expect(find.text('₹1.2 Cr'), findsOneWidget);
      expect(find.byIcon(Icons.arrow_upward), findsNWidgets(2));
      expect(find.byType(LineChart), findsOneWidget);
      final decorations = tester.widgetList<DecoratedBox>(find.byType(DecoratedBox)).map((d) => d.decoration).whereType<BoxDecoration>().map((d) => d.color).toList();
      expect(decorations, contains(HqTokens.light.toneSuccess.background));
      expect(decorations, contains(HqTokens.light.toneDanger.background));
      expect(find.bySemanticsLabel('Value: ₹1.2 Cr, +4.2%'), findsOneWidget);
      semantics.dispose();
    });

    testWidgets('flat trend uses a neutral arrow and a missing delta shows none', (tester) async {
      await tester.pumpWidget(hqTestApp(const HqKpiTile(label: 'Units', value: '120', trend: HqTrend.flat, deltaText: '0%')));
      expect(find.byIcon(Icons.arrow_forward), findsOneWidget);
      await tester.pumpWidget(hqTestApp(const HqKpiTile(label: 'Units', value: '120')));
      expect(find.byIcon(Icons.arrow_forward), findsNothing);
    });

    testWidgets('card states: ready, loading, empty, error with retry', (tester) async {
      var retries = 0;
      Widget card(HqCardState state) => HqDashboardCard(
            title: 'Trend',
            subtitle: 'Last 6 months',
            state: state,
            emptyText: 'Nothing yet',
            errorText: 'Could not load',
            retryLabel: 'Retry',
            onRetry: () => retries++,
            child: const Text('content'),
          );
      await tester.pumpWidget(hqTestApp(card(HqCardState.ready)));
      expect(find.text('content'), findsOneWidget);
      expect(find.text('Last 6 months'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(card(HqCardState.loading)));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('content'), findsNothing);
      await tester.pumpWidget(hqTestApp(card(HqCardState.empty)));
      expect(find.text('Nothing yet'), findsOneWidget);
      await tester.pumpWidget(hqTestApp(card(HqCardState.error)));
      expect(find.text('Could not load'), findsOneWidget);
      await tester.tap(find.text('Retry'));
      expect(retries, 1);
    });

    testWidgets('card period selector reports the choice', (tester) async {
      String? picked;
      await tester.pumpWidget(hqTestApp(HqDashboardCard(
        title: 'Trend',
        period: '6M',
        periods: const [HqTab(value: '1M', label: '1M'), HqTab(value: '6M', label: '6M')],
        onPeriodChanged: (v) => picked = v,
        child: const SizedBox(),
      )));
      await tester.tap(find.text('1M'));
      expect(picked, '1M');
    });

    test('grid columns follow the width', () {
      expect(HqDashboardGrid.columnsFor(390), 1);
      expect(HqDashboardGrid.columnsFor(700), 2);
      expect(HqDashboardGrid.columnsFor(1200), 3);
    });

    testWidgets('grid puts two cards side by side on a tablet and a spanning item across both', (tester) async {
      tester.view.physicalSize = const Size(800, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(hqTestApp(const HqDashboardGrid(children: [
        SizedBox(key: Key('a'), height: 50),
        SizedBox(key: Key('b'), height: 50),
        HqDashboardItem(child: SizedBox(key: Key('c'), height: 50)),
      ])));
      expect(tester.getTopLeft(find.byKey(const Key('a'))).dy, tester.getTopLeft(find.byKey(const Key('b'))).dy);
      expect(tester.getSize(find.byKey(const Key('c'))).width, greaterThan(tester.getSize(find.byKey(const Key('a'))).width));
    });

    testWidgets('grid is one column on a phone and pull to refresh calls onRefresh', (tester) async {
      tester.view.physicalSize = const Size(400, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      var refreshed = 0;
      await tester.pumpWidget(hqTestApp(HqDashboardGrid(
        onRefresh: () async => refreshed++,
        children: const [SizedBox(key: Key('a'), height: 50), SizedBox(key: Key('b'), height: 50)],
      )));
      expect(tester.getTopLeft(find.byKey(const Key('b'))).dy, greaterThan(tester.getTopLeft(find.byKey(const Key('a'))).dy));
      await tester.fling(find.byType(SingleChildScrollView), const Offset(0, 300), 1000);
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();
      expect(refreshed, 1);
    });
  });

  testWidgets('charts draw in the dark theme too', (tester) async {
    await tester.pumpWidget(hqTestApp(_sized(Column(children: [HqLineChart(data: _line()), HqDonutChart(data: _line(count: 2, series: 3))])), theme: HqTheme.dark()));
    expect(find.byType(LineChart), findsOneWidget);
    expect(find.byType(PieChart), findsOneWidget);
  });
}
