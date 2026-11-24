import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme/hq_theme.dart';
import '../theme/hq_tokens.dart';
import '../ui/hq_status_badge.dart';

/// One row of a [HqKeyValueList].
@immutable
class HqKeyValueItem {
  const HqKeyValueItem({required this.label, this.value = '', this.valueWidget, this.copyable = false});

  final String label;

  /// Already formatted text. Shown as "-" when empty and no [valueWidget] is given.
  final String value;

  /// Replaces [value] (a badge, a masked value).
  final Widget? valueWidget;

  /// Adds a copy button.
  final bool copyable;
}

/// Label and value rows for details screens such as property and holding details (the counterpart of `hq-key-value-list`).
class HqKeyValueList extends StatelessWidget {
  const HqKeyValueList({super.key, required this.items, this.dense = false, this.labelFraction = 0.4, this.copyLabel = '', this.copiedMessage, this.emptyValue = '-'});

  final List<HqKeyValueItem> items;
  final bool dense;

  /// Share of the width given to the labels (0.2 to 0.6).
  final double labelFraction;

  /// Tooltip of the copy button.
  final String copyLabel;

  /// Called after copying so the app can show a toast.
  final VoidCallback? copiedMessage;
  final String emptyValue;

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final fraction = labelFraction.clamp(0.2, 0.6);
    final gap = dense ? HqSpacing.xs : HqSpacing.sm;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final item in items)
          Padding(
            padding: EdgeInsets.symmetric(vertical: gap),
            child: Semantics(
              container: true,
              label: '${item.label}: ${item.value.isEmpty ? emptyValue : item.value}',
              excludeSemantics: item.valueWidget == null,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(flex: (fraction * 100).round(), child: Text(item.label, style: text.bodyMedium?.copyWith(color: tokens.muted))),
                  const SizedBox(width: HqSpacing.md),
                  Expanded(
                    flex: ((1 - fraction) * 100).round(),
                    child: Row(
                      children: [
                        Expanded(child: item.valueWidget ?? Text(item.value.isEmpty ? emptyValue : item.value, style: text.bodyMedium?.copyWith(fontWeight: FontWeight.w600))),
                        if (item.copyable && item.value.isNotEmpty)
                          IconButton(
                            tooltip: copyLabel,
                            visualDensity: VisualDensity.compact,
                            icon: const Icon(Icons.copy, size: 18),
                            onPressed: () async {
                              await Clipboard.setData(ClipboardData(text: item.value));
                              copiedMessage?.call();
                            },
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

/// One event of a [HqTimeline].
@immutable
class HqTimelineEntry {
  const HqTimelineEntry({required this.id, required this.title, this.when = '', this.description = '', this.tone = HqStatusTone.neutral, this.icon});

  final String id;
  final String title;

  /// Already formatted date or time.
  final String when;
  final String description;
  final HqStatusTone tone;
  final IconData? icon;
}

/// A vertical history of events, newest first or oldest first as given (subscription and KYC status history; the counterpart
/// of `hq-timeline`). The tone colour always comes with an icon and the title text.
class HqTimeline extends StatelessWidget {
  const HqTimeline({super.key, required this.entries, this.emptyText = '', this.highlightFirst = true});

  final List<HqTimelineEntry> entries;
  final String emptyText;

  /// Draws the first entry (the current state) larger.
  final bool highlightFirst;

  static IconData _defaultIcon(HqStatusTone tone) => switch (tone) {
        HqStatusTone.success => Icons.check_circle,
        HqStatusTone.warning => Icons.error,
        HqStatusTone.danger => Icons.cancel,
        HqStatusTone.info => Icons.info,
        HqStatusTone.neutral => Icons.circle,
      };

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    if (entries.isEmpty) return Text(emptyText, style: text.bodyMedium?.copyWith(color: tokens.muted));
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < entries.length; i++)
          Semantics(
            container: true,
            label: '${entries[i].title}${entries[i].when.isEmpty ? '' : ', ${entries[i].when}'}${entries[i].description.isEmpty ? '' : '. ${entries[i].description}'}',
            excludeSemantics: true,
            child: IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(
                    width: 28,
                    child: Column(
                      children: [
                        Icon(entries[i].icon ?? _defaultIcon(entries[i].tone), size: highlightFirst && i == 0 ? 22 : 16, color: tokens.tone(entries[i].tone).foreground),
                        if (i < entries.length - 1) Expanded(child: Container(width: 2, color: tokens.fieldBorder)),
                      ],
                    ),
                  ),
                  const SizedBox(width: HqSpacing.md),
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.only(bottom: HqSpacing.lg),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(entries[i].title, style: text.titleSmall?.copyWith(fontWeight: highlightFirst && i == 0 ? FontWeight.w700 : FontWeight.w600)),
                          if (entries[i].when.isNotEmpty) Text(entries[i].when, style: text.bodySmall?.copyWith(color: tokens.muted)),
                          if (entries[i].description.isNotEmpty) Padding(padding: const EdgeInsets.only(top: HqSpacing.xs), child: Text(entries[i].description, style: text.bodyMedium)),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

/// Review state of a document.
enum HqDocumentStatus { uploaded, verified, rejected, pending, expired }

/// A document row: icon or thumbnail, name, detail line, a status badge and preview / remove actions (an uploaded DMS
/// document in the KYC list). Every text is passed in.
class HqDocumentTile extends StatelessWidget {
  const HqDocumentTile({
    super.key,
    required this.name,
    required this.statusLabel,
    this.status = HqDocumentStatus.uploaded,
    this.detail = '',
    this.thumbnail,
    this.isPdf = false,
    this.onPreview,
    this.previewLabel = '',
    this.onRemove,
    this.removeLabel = '',
  });

  final String name;
  final String detail;
  final HqDocumentStatus status;

  /// Translated status text ("Verified").
  final String statusLabel;
  final ImageProvider<Object>? thumbnail;
  final bool isPdf;
  final VoidCallback? onPreview;
  final String previewLabel;
  final VoidCallback? onRemove;
  final String removeLabel;

  HqStatusTone get _tone => switch (status) {
        HqDocumentStatus.verified => HqStatusTone.success,
        HqDocumentStatus.rejected => HqStatusTone.danger,
        HqDocumentStatus.expired => HqStatusTone.warning,
        HqDocumentStatus.pending => HqStatusTone.info,
        HqDocumentStatus.uploaded => HqStatusTone.neutral,
      };

  IconData get _icon => switch (status) {
        HqDocumentStatus.verified => Icons.verified,
        HqDocumentStatus.rejected => Icons.cancel,
        HqDocumentStatus.expired => Icons.schedule,
        HqDocumentStatus.pending => Icons.hourglass_empty,
        HqDocumentStatus.uploaded => Icons.upload_file,
      };

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    final Widget leading = thumbnail != null
        ? ClipRRect(borderRadius: BorderRadius.circular(HqRadius.sm), child: Image(image: thumbnail!, width: 48, height: 48, fit: BoxFit.cover))
        : Icon(isPdf ? Icons.picture_as_pdf_outlined : Icons.description_outlined, size: 40, color: tokens.muted);
    return Semantics(
      container: true,
      label: '$name, $statusLabel${detail.isEmpty ? '' : ', $detail'}',
      child: Container(
        padding: const EdgeInsets.all(HqSpacing.md),
        decoration: BoxDecoration(color: tokens.surface, border: Border.all(color: tokens.fieldBorder), borderRadius: BorderRadius.circular(HqRadius.control)),
        child: Row(
          children: [
            InkWell(onTap: onPreview, child: ExcludeSemantics(child: leading)),
            const SizedBox(width: HqSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(name, maxLines: 1, overflow: TextOverflow.ellipsis, style: text.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                  if (detail.isNotEmpty) Text(detail, style: text.bodySmall?.copyWith(color: tokens.muted)),
                  const SizedBox(height: HqSpacing.xs),
                  HqStatusBadge(label: statusLabel, tone: _tone, icon: _icon, size: HqStatusBadgeSize.sm),
                ],
              ),
            ),
            if (onPreview != null) IconButton(tooltip: previewLabel, icon: const Icon(Icons.visibility_outlined), onPressed: onPreview),
            if (onRemove != null) IconButton(tooltip: removeLabel, icon: const Icon(Icons.delete_outline), onPressed: onRemove),
          ],
        ),
      ),
    );
  }
}

/// One section of a [HqAccordion].
@immutable
class HqAccordionItem {
  const HqAccordionItem({required this.id, required this.title, required this.child, this.subtitle = '', this.leading});

  final String id;
  final String title;
  final String subtitle;
  final Widget child;
  final Widget? leading;
}

/// Sections that open and close (property documents, FAQs). With [multiple] several can be open; otherwise opening one closes
/// the others. [initiallyOpen] lists the ids open at the start.
class HqAccordion extends StatefulWidget {
  const HqAccordion({super.key, required this.items, this.multiple = false, this.initiallyOpen = const <String>{}, this.onChanged});

  final List<HqAccordionItem> items;
  final bool multiple;
  final Set<String> initiallyOpen;
  final ValueChanged<Set<String>>? onChanged;

  @override
  State<HqAccordion> createState() => _HqAccordionState();
}

class _HqAccordionState extends State<HqAccordion> {
  late Set<String> _open = {...widget.initiallyOpen};

  void _toggle(String id) {
    setState(() {
      if (_open.contains(id)) {
        _open = {..._open}..remove(id);
      } else {
        _open = widget.multiple ? {..._open, id} : {id};
      }
    });
    widget.onChanged?.call(_open);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = context.hq;
    final text = Theme.of(context).textTheme;
    return DecoratedBox(
      decoration: BoxDecoration(color: tokens.surface, border: Border.all(color: tokens.fieldBorder), borderRadius: BorderRadius.circular(HqRadius.panel)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(HqRadius.panel),
        child: Column(
          children: [
            for (var i = 0; i < widget.items.length; i++) ...[
              if (i > 0) Divider(height: 1, color: tokens.fieldBorder),
              Semantics(
                button: true,
                expanded: _open.contains(widget.items[i].id),
                label: widget.items[i].title,
                excludeSemantics: true,
                onTap: () => _toggle(widget.items[i].id),
                child: InkWell(
                  key: ValueKey<String>('hq-accordion-${widget.items[i].id}'),
                  onTap: () => _toggle(widget.items[i].id),
                  child: Padding(
                    padding: const EdgeInsets.all(HqSpacing.lg),
                    child: Row(
                      children: [
                        if (widget.items[i].leading != null) ...[widget.items[i].leading!, const SizedBox(width: HqSpacing.md)],
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(widget.items[i].title, style: text.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                              if (widget.items[i].subtitle.isNotEmpty) Text(widget.items[i].subtitle, style: text.bodySmall?.copyWith(color: tokens.muted)),
                            ],
                          ),
                        ),
                        Icon(_open.contains(widget.items[i].id) ? Icons.expand_less : Icons.expand_more, color: tokens.muted),
                      ],
                    ),
                  ),
                ),
              ),
              AnimatedSize(
                duration: const Duration(milliseconds: 180),
                alignment: Alignment.topCenter,
                child: _open.contains(widget.items[i].id)
                    ? Padding(padding: const EdgeInsets.fromLTRB(HqSpacing.lg, 0, HqSpacing.lg, HqSpacing.lg), child: Align(alignment: AlignmentDirectional.centerStart, child: widget.items[i].child))
                    : const SizedBox(width: double.infinity),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
