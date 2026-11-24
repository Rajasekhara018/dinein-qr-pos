import 'package:flutter/material.dart';
import 'package:hq_flutter_common/hq_flutter_common.dart';

// The example hardcodes English because it is not a product screen; real apps pass texts from their ARB files.

void main() => runApp(const ExampleApp());

class _Holding {
  const _Holding(this.name, this.units, this.value, this.status);

  final String name;
  final int units;
  final String value;
  final String status;
}

const _holdings = <_Holding>[
  _Holding('Lakeview Towers', 120, '1234567.50', 'ACTIVE'),
  _Holding('Harbor Mall', 40, '98000', 'ACTIVE'),
  _Holding('Green Park Offices', 15, '45000.25', 'PENDING'),
];

class ExampleApp extends StatefulWidget {
  const ExampleApp({super.key});

  @override
  State<ExampleApp> createState() => _ExampleAppState();
}

class _ExampleAppState extends State<ExampleApp> {
  final _toast = HqToastService();
  final _access = HqPrivilegeAccess();
  bool _dark = false;

  @override
  Widget build(BuildContext context) {
    return HqConfigScope(
      config: const HqConfig(gatewayUrl: 'https://example.invalid/api'),
      child: HqPrivilegeScope(
        access: _access,
        child: MaterialApp(
          title: 'hq_flutter_common',
          theme: HqTheme.light(),
          darkTheme: HqTheme.dark(),
          themeMode: _dark ? ThemeMode.dark : ThemeMode.light,
          scaffoldMessengerKey: _toast.messengerKey,
          home: Gallery(toast: _toast, onToggleTheme: () => setState(() => _dark = !_dark)),
        ),
      ),
    );
  }
}

class _FakePicker implements HqFilePicker {
  @override
  Future<List<HqPickedFile>> pick(HqPickSource source, {required bool multiple, required List<String> accept, int? limit}) async => const [];

  @override
  Future<bool> openSettings() async => false;
}

class _FakeUploader implements HqFileUploader {
  @override
  Future<String> upload(HqPickedFile file, {required void Function(double progress) onProgress}) async => 'demo-id';

  @override
  Future<void> remove(String documentId) async {}
}

class Gallery extends StatefulWidget {
  const Gallery({super.key, required this.toast, required this.onToggleTheme});

  final HqToastService toast;
  final VoidCallback onToggleTheme;

  @override
  State<Gallery> createState() => _GalleryState();
}

class _GalleryState extends State<Gallery> {
  String _tab = 'list';
  bool _agree = false;
  String _kind = 'individual';
  int? _city;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('hq_flutter_common'), actions: [
        IconButton(icon: const Icon(Icons.brightness_6), onPressed: widget.onToggleTheme),
      ]),
      body: Padding(
        padding: const EdgeInsets.all(HqSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const HqPageHeader(title: 'Components', subtitle: 'Every widget of the package', kicker: 'Example'),
            HqTabs<String>(
              value: _tab,
              onChanged: (v) => setState(() => _tab = v),
              tabs: const [HqTab(value: 'list', label: 'List'), HqTab(value: 'form', label: 'Form'), HqTab(value: 'misc', label: 'Misc')],
            ),
            const SizedBox(height: HqSpacing.lg),
            Expanded(child: switch (_tab) { 'list' => _list(), 'form' => _form(), _ => _misc(context) }),
          ],
        ),
      ),
    );
  }

  Widget _list() {
    return HqDataList<_Holding>(
      items: _holdings,
      searchable: true,
      texts: HqDataListTexts.english(),
      header: const HqStatCards(fill: true, stats: [
        HqStatCardItem(label: 'Holdings', value: 3, icon: Icons.apartment),
        HqStatCardItem(label: 'Units', value: 175, icon: Icons.pie_chart, tone: HqStatTone.green),
      ]),
      columns: [
        HqColumn(key: 'name', label: 'Property', valueOf: (h) => h.name, sortable: true),
        HqColumn(key: 'units', label: 'Units', valueOf: (h) => '${h.units}', sortable: true),
        HqColumn(key: 'value', label: 'Value', valueOf: (h) => hqFormatMoney(h.value)),
        HqColumn(
          key: 'status',
          label: 'Status',
          valueOf: (h) => h.status,
          cellBuilder: (context, h) => HqStatusBadge(label: h.status, tone: h.status == 'ACTIVE' ? HqStatusTone.success : HqStatusTone.warning),
        ),
      ],
    );
  }

  Widget _form() {
    return ListView(children: [
      HqFormGrid(cols: 2, children: [
        const HqField(label: 'Full name', required: true, placeholder: 'As on PAN'),
        HqAmountInput(label: 'Amount', hint: 'Minimum 5,000', min: 5000, minError: 'Below the minimum'),
        const HqPhoneNumberInput(label: 'Mobile', invalidError: 'Not a valid number'),
        HqSearchableSelect<int>(
          label: 'City',
          value: _city,
          placeholder: 'Choose',
          searchHint: 'Search',
          emptyText: 'No match',
          options: const [HqOption(value: 1, label: 'Mumbai'), HqOption(value: 2, label: 'Chennai')],
          onChanged: (v) => setState(() => _city = v),
        ),
      ]),
      const SizedBox(height: HqSpacing.lg),
      HqRadioGroup<String>(
        label: 'Investor type',
        value: _kind,
        options: const [HqRadioOption(value: 'individual', label: 'Individual'), HqRadioOption(value: 'company', label: 'Company')],
        onChanged: (v) => setState(() => _kind = v),
      ),
      HqCheckbox(checked: _agree, label: 'I accept the terms', onChanged: (v) => setState(() => _agree = v)),
      const SizedBox(height: HqSpacing.lg),
      HqFileUpload(
        picker: _FakePicker(),
        uploader: _FakeUploader(),
        texts: HqFileUploadTexts.english(),
        accept: const ['image/*', 'application/pdf'],
        typeHints: 'PDF, JPEG, PNG',
        label: 'KYC document',
        onChanged: (_) {},
      ),
      HqFormActions(children: [
        HqButton(label: 'Cancel', appearance: HqButtonAppearance.text, onPressed: () {}),
        HqButton(label: 'Save', onPressed: () => widget.toast.success('Saved')),
      ]),
    ]);
  }

  Widget _misc(BuildContext context) {
    return ListView(children: [
      Wrap(spacing: HqSpacing.sm, runSpacing: HqSpacing.sm, children: [
        for (final color in HqButtonColor.values) HqButton(label: color.name, color: color, onPressed: () {}),
        HqButton(label: 'Loading', loading: true, onPressed: () {}),
      ]),
      const SizedBox(height: HqSpacing.lg),
      Wrap(spacing: HqSpacing.sm, children: [
        for (final tone in HqStatusTone.values) HqStatusBadge(label: tone.name, tone: tone),
      ]),
      const SizedBox(height: HqSpacing.lg),
      HqFilterBar(collapsible: true, toggleText: 'Filters', children: const [Text('Status: all')]),
      Wrap(spacing: HqSpacing.sm, children: [
        HqButton(label: 'Toast', appearance: HqButtonAppearance.outline, onPressed: () => widget.toast.info('Hello')),
        HqButton(
          label: 'Confirm',
          appearance: HqButtonAppearance.outline,
          onPressed: () => HqDialog.confirm(context, title: 'Delete?', confirmLabel: 'Delete', cancelLabel: 'Cancel', destructive: true),
        ),
        HqButton(
          label: 'Drawer',
          appearance: HqButtonAppearance.outline,
          onPressed: () => HqDrawer.show<void>(context, title: 'Details', closeLabel: 'Close', builder: (_) => const Text('Drawer content')),
        ),
        HqButton(
          label: 'Modal',
          appearance: HqButtonAppearance.outline,
          onPressed: () => HqModal.show<void>(context, title: 'Modal', closeLabel: 'Close', builder: (_) => const Text('Modal content')),
        ),
      ]),
    ]);
  }
}
