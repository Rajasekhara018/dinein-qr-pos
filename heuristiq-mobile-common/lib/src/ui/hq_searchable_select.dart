import 'package:flutter/material.dart';

import '../core/hq_format.dart';
import '../theme/hq_mobile_finish.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import 'hq_button.dart';
import 'hq_checkbox.dart';
import 'hq_field.dart';
import 'hq_form_field.dart';

/// One choice of a [HqSearchableSelect] (the web `HqOption`).
@immutable
class HqOption<T> {
  const HqOption({required this.value, required this.label, this.subtitle, this.disabled = false});

  final T value;

  /// Already-translated (or API-provided) text; also what the search matches.
  final String label;
  final String? subtitle;
  final bool disabled;
}

/// The options whose label or subtitle contains [query] (case-insensitive); all of them for a blank query.
List<HqOption<T>> hqFilterOptions<T>(List<HqOption<T>> options, String query) {
  final needle = query.trim();
  if (needle.isEmpty) return options;
  return [
    for (final option in options)
      if (hqContainsIgnoreCase(option.label, needle) || (option.subtitle != null && hqContainsIgnoreCase(option.subtitle!, needle))) option,
  ];
}

/// A select that opens a searchable bottom sheet (the counterpart of `hq-searchable-select`).
///
/// Single choice: [value] and [onChanged]. Several choices: `multiple: true`, [values] and [onValuesChanged]. While
/// [loading] the field shows a spinner; when [loadFailed] tapping it calls [onRetry].
class HqSearchableSelect<T> extends StatelessWidget {
  const HqSearchableSelect({
    super.key,
    required this.options,
    this.value,
    this.values,
    this.multiple = false,
    this.onChanged,
    this.onValuesChanged,
    this.label = '',
    this.showLabel = true,
    this.required = false,
    this.placeholder = '',
    this.searchHint = '',
    this.emptyText = '',
    this.doneLabel = '',
    this.clearLabel,
    this.sheetTitle,
    this.loading = false,
    this.loadFailed = false,
    this.onRetry,
    this.loadFailedText = '',
    this.hint,
    this.error,
    this.enabled = true,
  });

  final List<HqOption<T>> options;
  final T? value;
  final List<T>? values;
  final bool multiple;
  final ValueChanged<T?>? onChanged;
  final ValueChanged<List<T>>? onValuesChanged;
  final String label;
  final bool showLabel;
  final bool required;
  final String placeholder;
  final String searchHint;
  final String emptyText;

  /// Label of the confirm button of a multiple select.
  final String doneLabel;

  /// When set, a first row with this text clears a single select.
  final String? clearLabel;
  final String? sheetTitle;
  final bool loading;
  final bool loadFailed;
  final VoidCallback? onRetry;
  final String loadFailedText;
  final String? hint;
  final String? error;
  final bool enabled;

  String _display() {
    if (multiple) {
      final chosen = values ?? <T>[];
      return [for (final o in options) if (chosen.contains(o.value)) o.label].join(', ');
    }
    for (final option in options) {
      if (option.value == value) return option.label;
    }
    return '';
  }

  Future<void> _open(BuildContext context) async {
    if (loadFailed) {
      onRetry?.call();
      return;
    }
    final result = await showModalBottomSheet<_SelectResult<T>>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      backgroundColor: context.hq.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(HqMobile.sheetRadius))),
      builder: (_) => _HqSelectSheet<T>(
        options: options,
        selected: multiple ? (values ?? <T>[]) : (value == null ? <T>[] : <T>[value as T]),
        multiple: multiple,
        title: sheetTitle ?? label,
        searchHint: searchHint,
        emptyText: emptyText,
        doneLabel: doneLabel,
        clearLabel: clearLabel,
      ),
    );
    if (result == null) return;
    if (multiple) {
      onValuesChanged?.call(result.values);
    } else {
      onChanged?.call(result.cleared || result.values.isEmpty ? null : result.values.first);
    }
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final display = _display();
    final hasError = error != null && error!.isNotEmpty;
    final active = enabled && !loading;
    final shown = loadFailed ? loadFailedText : (display.isEmpty ? placeholder : display);
    final Widget trailing = loading
        ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
        : Icon(loadFailed ? Icons.refresh : Icons.keyboard_arrow_down_rounded, color: tokens.muted);
    final box = Semantics(
      button: true,
      enabled: active,
      label: label.isEmpty ? shown : '$label: $shown',
      excludeSemantics: true,
      onTap: active ? () => _open(context) : null,
      // The mobile finish: the same filled box as a text field, with press feedback.
      child: HqPressable(
        enabled: active,
        child: InkWell(
        borderRadius: BorderRadius.circular(HqMobile.controlRadius(context)),
        onTap: active ? () => _open(context) : null,
        child: Container(
          constraints: const BoxConstraints(minHeight: HqMobile.fieldHeight),
          padding: const EdgeInsets.symmetric(horizontal: HqSpacing.lg, vertical: HqSpacing.sm),
          decoration: HqMobile.fieldBox(context, error: hasError || loadFailed),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  shown,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: text.bodyLarge?.copyWith(color: display.isEmpty || loadFailed ? tokens.muted : tokens.ink),
                ),
              ),
              trailing,
            ],
          ),
        ),
      ),
      ),
    );
    if (!showLabel || label.isEmpty) return box;
    return HqFormField(label: label, required: required, hint: hint, error: error, child: box);
  }
}

class _SelectResult<T> {
  const _SelectResult(this.values, {this.cleared = false});

  final List<T> values;
  final bool cleared;
}

class _HqSelectSheet<T> extends StatefulWidget {
  const _HqSelectSheet({
    required this.options,
    required this.selected,
    required this.multiple,
    required this.title,
    required this.searchHint,
    required this.emptyText,
    required this.doneLabel,
    required this.clearLabel,
  });

  final List<HqOption<T>> options;
  final List<T> selected;
  final bool multiple;
  final String title;
  final String searchHint;
  final String emptyText;
  final String doneLabel;
  final String? clearLabel;

  @override
  State<_HqSelectSheet<T>> createState() => _HqSelectSheetState<T>();
}

class _HqSelectSheetState<T> extends State<_HqSelectSheet<T>> {
  String _query = '';
  late final List<T> _selected = List<T>.of(widget.selected);

  void _toggle(T value) {
    setState(() {
      if (_selected.contains(value)) {
        _selected.remove(value);
      } else {
        _selected.add(value);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final visible = hqFilterOptions(widget.options, _query);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (widget.title.isNotEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: HqSpacing.lg),
                child: Align(alignment: AlignmentDirectional.centerStart, child: Text(widget.title, style: text.titleLarge?.copyWith(fontWeight: FontWeight.w700, color: tokens.ink))),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(HqSpacing.lg, HqSpacing.md, HqSpacing.lg, HqSpacing.sm),
              child: HqField(
                showLabel: false,
                placeholder: widget.searchHint,
                icon: Icons.search,
                onChanged: (q) => setState(() => _query = q),
              ),
            ),
            Flexible(
              child: visible.isEmpty
                  ? Padding(
                      padding: const EdgeInsets.all(HqSpacing.xl),
                      child: Text(widget.emptyText, style: text.bodyMedium?.copyWith(color: tokens.muted)),
                    )
                  : ListView(
                      shrinkWrap: true,
                      padding: EdgeInsets.only(bottom: widget.multiple ? 0 : MediaQuery.paddingOf(context).bottom + HqSpacing.md),
                      children: [
                        if (!widget.multiple && widget.clearLabel != null)
                          ListTile(
                            title: Text(widget.clearLabel!, style: TextStyle(color: tokens.muted)),
                            onTap: () => Navigator.of(context).pop(_SelectResult<T>(<T>[], cleared: true)),
                          ),
                        for (final option in visible) _row(option, tokens),
                      ],
                    ),
            ),
            if (widget.multiple)
              Padding(
                padding: EdgeInsets.fromLTRB(HqSpacing.lg, HqSpacing.md, HqSpacing.lg, HqSpacing.lg + MediaQuery.paddingOf(context).bottom),
                child: HqButton(
                  label: widget.doneLabel.isEmpty ? null : widget.doneLabel,
                  icon: widget.doneLabel.isEmpty ? Icons.check : null,
                  block: true,
                  onPressed: () => Navigator.of(context).pop(_SelectResult<T>(List<T>.of(_selected))),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _row(HqOption<T> option, HqTokens tokens) {
    final selected = _selected.contains(option.value);
    if (widget.multiple) {
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: HqSpacing.md),
        child: HqCheckbox(
          checked: selected,
          label: option.label,
          enabled: !option.disabled,
          onChanged: (_) => _toggle(option.value),
        ),
      );
    }
    // A 52 px row; the chosen one is tinted with a check mark.
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: HqSpacing.sm, vertical: 2),
      child: ListTile(
        enabled: !option.disabled,
        selected: selected,
        minTileHeight: HqMobile.fieldHeight,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(HqMobile.controlRadius(context))),
        selectedColor: tokens.primary,
        selectedTileColor: tokens.primary.withValues(alpha: 0.08),
        title: Text(option.label, style: TextStyle(fontWeight: selected ? FontWeight.w700 : FontWeight.w500)),
        subtitle: option.subtitle == null ? null : Text(option.subtitle!),
        trailing: selected ? Icon(Icons.check_circle_rounded, color: tokens.primary) : null,
        onTap: () => Navigator.of(context).pop(_SelectResult<T>([option.value])),
      ),
    );
  }
}
