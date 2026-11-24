import 'package:flutter/material.dart';

import '../core/hq_format.dart';
import '../theme/hq_mobile_finish.dart';
import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_form_field.dart';

/// A date picker field: shows the chosen date and opens the system calendar (the counterpart of `hq-date-time-input` in date
/// mode). Dates are plain dates (time zone free); [min] and [max] bound the choice. [format] defaults to `yyyy-MM-dd`: pass a
/// locale-aware formatter from the app.
class HqDateField extends StatelessWidget {
  const HqDateField({
    super.key,
    required this.value,
    required this.onChanged,
    this.label = '',
    this.required = false,
    this.placeholder = '',
    this.hint,
    this.error,
    this.min,
    this.max,
    this.format,
    this.clearable = true,
    this.clearLabel = '',
    this.enabled = true,
    this.helpText,
  });

  final DateTime? value;
  final ValueChanged<DateTime?> onChanged;
  final String label;
  final bool required;
  final String placeholder;
  final String? hint;
  final String? error;
  final DateTime? min;
  final DateTime? max;
  final String Function(DateTime)? format;
  final bool clearable;
  final String clearLabel;
  final bool enabled;

  /// Heading of the calendar dialog.
  final String? helpText;

  Future<void> _open(BuildContext context) async {
    final now = DateTime.now();
    final first = min ?? DateTime(now.year - 100);
    final last = max ?? DateTime(now.year + 50);
    var initial = value ?? now;
    if (initial.isBefore(first)) initial = first;
    if (initial.isAfter(last)) initial = last;
    final picked = await showDatePicker(context: context, initialDate: initial, firstDate: first, lastDate: last, helpText: helpText);
    if (picked != null) onChanged(DateTime(picked.year, picked.month, picked.day));
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final shown = value == null ? '' : (format ?? hqFormatDate)(value!);
    final hasError = error != null && error!.isNotEmpty;
    return HqFormField(
      label: label,
      required: required,
      hint: hint,
      error: error,
      child: Semantics(
        button: true,
        enabled: enabled,
        label: label.isEmpty ? shown : '$label: ${shown.isEmpty ? placeholder : shown}',
        excludeSemantics: true,
        onTap: enabled ? () => _open(context) : null,
        // The mobile finish: the same filled box as a text field, with press feedback.
        child: HqPressable(
          enabled: enabled,
          child: InkWell(
          key: const ValueKey<String>('hq-date-field'),
          borderRadius: BorderRadius.circular(HqMobile.controlRadius(context)),
          onTap: enabled ? () => _open(context) : null,
          child: Container(
            constraints: const BoxConstraints(minHeight: HqMobile.fieldHeight),
            padding: const EdgeInsets.symmetric(horizontal: HqSpacing.lg),
            decoration: HqMobile.fieldBox(context, error: hasError),
            child: Row(
              children: [
                Icon(Icons.calendar_month_rounded, size: 20, color: tokens.primary),
                const SizedBox(width: HqSpacing.sm),
                Expanded(child: Text(shown.isEmpty ? placeholder : shown, style: text.bodyLarge?.copyWith(color: shown.isEmpty ? tokens.muted : tokens.ink))),
                if (clearable && value != null && enabled)
                  InkResponse(onTap: () => onChanged(null), child: Semantics(button: true, label: clearLabel, child: Icon(Icons.close, size: 18, color: tokens.muted))),
              ],
            ),
          ),
        ),
        ),
      ),
    );
  }
}

/// A chosen period.
@immutable
class HqDateRange {
  const HqDateRange(this.start, this.end);

  final DateTime start;
  final DateTime end;

  @override
  bool operator ==(Object other) => other is HqDateRange && other.start == start && other.end == end;

  @override
  int get hashCode => Object.hash(start, end);
}

/// A quick choice of a [HqDateRangePicker], such as "Last 30 days" ([days] counts back from today inclusive).
@immutable
class HqDateRangePreset {
  const HqDateRangePreset({required this.id, required this.label, required this.days});

  final String id;
  final String label;
  final int days;
}

/// Preset chips plus a custom range from the system calendar (the counterpart of `hq-date-range`). The presets are texts
/// the app translates, so there are none by default.
class HqDateRangePicker extends StatelessWidget {
  const HqDateRangePicker({
    super.key,
    required this.value,
    required this.onChanged,
    this.presets = const <HqDateRangePreset>[],
    this.customLabel = '',
    this.allowCustom = true,
    this.min,
    this.max,
    this.format,
    this.today,
  });

  final HqDateRange? value;
  final ValueChanged<HqDateRange> onChanged;
  final List<HqDateRangePreset> presets;

  /// Text of the "Custom" chip.
  final String customLabel;
  final bool allowCustom;
  final DateTime? min;
  final DateTime? max;
  final String Function(DateTime)? format;

  /// "Today" for the presets (tests pass a fixed day).
  final DateTime? today;

  /// The range a preset stands for: [days] ending [today].
  static HqDateRange rangeOf(HqDateRangePreset preset, DateTime today) {
    final end = DateTime(today.year, today.month, today.day);
    return HqDateRange(end.subtract(Duration(days: preset.days - 1)), end);
  }

  bool _isPreset(HqDateRangePreset p, DateTime day) => value != null && value == rangeOf(p, day);

  Future<void> _custom(BuildContext context) async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: min ?? DateTime(now.year - 100),
      lastDate: max ?? DateTime(now.year + 50),
      initialDateRange: value == null ? null : DateTimeRange(start: value!.start, end: value!.end),
    );
    if (picked != null) onChanged(HqDateRange(DateTime(picked.start.year, picked.start.month, picked.start.day), DateTime(picked.end.year, picked.end.month, picked.end.day)));
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final day = today ?? DateTime.now();
    final matched = presets.any((p) => _isPreset(p, day));
    final fmt = format ?? hqFormatDate;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: HqSpacing.sm,
          runSpacing: HqSpacing.sm,
          children: [
            for (final preset in presets) _chip(context, preset.label, _isPreset(preset, day), () => onChanged(rangeOf(preset, day)), 'hq-range-${preset.id}'),
            if (allowCustom) _chip(context, customLabel, value != null && !matched, () => _custom(context), 'hq-range-custom'),
          ],
        ),
        if (value != null)
          Padding(
            padding: const EdgeInsets.only(top: HqSpacing.sm),
            child: Directionality(textDirection: TextDirection.ltr, child: Text('${fmt(value!.start)} - ${fmt(value!.end)}', style: Theme.of(context).textTheme.bodySmall?.copyWith(color: tokens.muted))),
          ),
      ],
    );
  }

  Widget _chip(BuildContext context, String label, bool selected, VoidCallback onTap, String key) {
    final tokens = context.hq;
    return Semantics(
      button: true,
      selected: selected,
      label: label,
      excludeSemantics: true,
      onTap: onTap,
      child: Material(
        color: selected ? tokens.primarySoft : Colors.transparent,
        shape: StadiumBorder(side: BorderSide(color: selected ? tokens.primary : tokens.fieldBorder)),
        child: InkWell(
          key: ValueKey<String>(key),
          customBorder: const StadiumBorder(),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: HqSpacing.md, vertical: HqSpacing.sm),
            child: Text(label, style: Theme.of(context).textTheme.labelLarge?.copyWith(color: selected ? tokens.link : tokens.ink)),
          ),
        ),
      ),
    );
  }
}
