import 'package:flutter/material.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_country_data.dart';
import 'hq_field.dart';
import 'hq_flag.dart';

/// Width from which the country picker opens as a centred dialog instead of a bottom sheet.
const double hqCountryPickerWideWidth = 600;

/// A searchable country list: a bottom sheet on phones, a dialog on wide screens.
///
/// Search by English or localized name, ISO code or calling code (`ind`, `IN`, `91`, `+91`). With an empty search the
/// [preferred] countries are pinned on top. Pressing enter in the search box picks the first result. Texts come from the
/// caller; the country names come from `countryNames` (iso to localized name) and default to English.
abstract final class HqCountryPicker {
  /// Opens the picker; completes with the chosen ISO code, or null when dismissed.
  static Future<String?> show(
    BuildContext context, {
    required List<HqPhoneCountry> countries,
    String? selectedIso,
    List<String> preferred = const <String>['IN'],
    Map<String, String>? countryNames,
    String title = '',
    String searchHint = '',
    String noResultsText = '',
    String closeLabel = '',
    HqFlagStyle flagStyle = HqFlagStyle.auto,
  }) {
    Widget content(BuildContext c) => HqCountryList(
          countries: countries,
          selectedIso: selectedIso,
          preferred: preferred,
          countryNames: countryNames,
          title: title,
          searchHint: searchHint,
          noResultsText: noResultsText,
          closeLabel: closeLabel,
          flagStyle: flagStyle,
          onSelected: (iso) => Navigator.of(c).pop(iso),
        );
    if (MediaQuery.sizeOf(context).width >= hqCountryPickerWideWidth) {
      return showDialog<String>(
        context: context,
        builder: (dialogContext) => Dialog(
          backgroundColor: dialogContext.hq.surface,
          child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 440, maxHeight: 560), child: content(dialogContext)),
        ),
      );
    }
    return showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(sheetContext).bottom),
        child: SizedBox(height: MediaQuery.sizeOf(sheetContext).height * 0.85, child: content(sheetContext)),
      ),
    );
  }
}

/// The body of the country picker: a search box over the list. Use it directly to embed the list in another surface.
class HqCountryList extends StatefulWidget {
  const HqCountryList({
    super.key,
    required this.countries,
    required this.onSelected,
    this.selectedIso,
    this.preferred = const <String>['IN'],
    this.countryNames,
    this.title = '',
    this.searchHint = '',
    this.noResultsText = '',
    this.closeLabel = '',
    this.flagStyle = HqFlagStyle.auto,
  });

  final List<HqPhoneCountry> countries;
  final ValueChanged<String> onSelected;
  final String? selectedIso;
  final List<String> preferred;
  final Map<String, String>? countryNames;
  final String title;
  final String searchHint;
  final String noResultsText;
  final String closeLabel;
  final HqFlagStyle flagStyle;

  @override
  State<HqCountryList> createState() => _HqCountryListState();
}

class _HqCountryListState extends State<HqCountryList> {
  String _query = '';

  String _nameOf(HqPhoneCountry c) => widget.countryNames?[c.iso] ?? c.name;

  List<HqPhoneCountry> get _results {
    if (_query.trim().isEmpty) return HqCountryData.withPreferredFirst(widget.countries, widget.preferred);
    return HqCountryData.search(widget.countries, _query, names: widget.countryNames);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final results = _results;
    final pinned = _query.trim().isEmpty ? widget.preferred.map((i) => i.toUpperCase()).toSet() : const <String>{};
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.title.isNotEmpty || widget.closeLabel.isNotEmpty)
          Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(HqSpacing.lg, HqSpacing.sm, HqSpacing.sm, 0),
            child: Row(
              children: [
                Expanded(child: Semantics(header: true, child: Text(widget.title, style: text.titleMedium?.copyWith(fontWeight: FontWeight.w700)))),
                if (widget.closeLabel.isNotEmpty) IconButton(tooltip: widget.closeLabel, icon: const Icon(Icons.close), onPressed: () => Navigator.of(context).maybePop()),
              ],
            ),
          ),
        Padding(
          padding: const EdgeInsets.all(HqSpacing.lg),
          child: HqField(
            showLabel: false,
            autofocus: true,
            icon: Icons.search,
            placeholder: widget.searchHint,
            textInputAction: TextInputAction.search,
            onChanged: (q) => setState(() => _query = q),
            onSubmitted: (_) {
              if (results.isNotEmpty) widget.onSelected(results.first.iso);
            },
          ),
        ),
        Expanded(
          child: results.isEmpty
              ? Center(child: Padding(padding: const EdgeInsets.all(HqSpacing.xl), child: Text(widget.noResultsText, style: text.bodyMedium?.copyWith(color: tokens.muted), textAlign: TextAlign.center)))
              : ListView.builder(
                  itemCount: results.length,
                  itemBuilder: (context, index) {
                    final c = results[index];
                    final selected = c.iso == widget.selectedIso;
                    final lastPinned = pinned.contains(c.iso) && (index + 1 >= results.length || !pinned.contains(results[index + 1].iso));
                    return Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Semantics(
                          button: true,
                          selected: selected,
                          label: '${_nameOf(c)} ${c.dialDisplay}',
                          excludeSemantics: true,
                          child: ListTile(
                            key: ValueKey<String>('hq-country-${c.iso}'),
                            selected: selected,
                            selectedColor: tokens.primary,
                            leading: HqFlag(iso: c.iso, style: widget.flagStyle),
                            title: Text(_nameOf(c)),
                            // Calling codes read left to right even in right-to-left languages.
                            trailing: Directionality(textDirection: TextDirection.ltr, child: Text(c.dialDisplay, style: text.bodyMedium?.copyWith(color: tokens.muted))),
                            onTap: () => widget.onSelected(c.iso),
                          ),
                        ),
                        if (lastPinned) const Divider(),
                      ],
                    );
                  },
                ),
        ),
      ],
    );
  }
}
