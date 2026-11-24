import { Component, Input, booleanAttribute, inject } from '@angular/core';
import { HqNotificationService } from '../hq-notification.service';
import { HQ_UI_STATE_DEFAULT_TEXTS, HQ_UI_STATE_TEXTS, HqUiStateTexts, hqUiText } from '../hq-view-states/hq-ui-state.texts';
import { HQ_KEY_VALUE_DEFAULT_LABEL_WIDTH, HqKeyValueItem } from './hq-key-value-list.constants';
import { hqCopyText } from './hq-key-value-list.helpers';

/**
 * Label/value rows for drawers and detail panes (the shared replacement for the apps' `app-key-value-list`):
 * `<hq-key-value-list [items]="rows"></hq-key-value-list>`. A row with `tone` shows its value as a status badge, with
 * `mono` in a monospace font, with `copyable` a copy button (a toast says whether it worked). Words come from
 * HQ_UI_STATE_TEXTS (none, copy, copied, copyFailed, translate for `labelKey`).
 */
@Component({
  selector: 'hq-key-value-list',
  standalone: false,
  template: `
    <dl class="kv" [class.kv--dense]="dense" [style.--_label-w]="labelWidth">
      @for (item of items; track $index) {
        <div class="kv__row">
          <dt class="kv__label">{{ labelOf(item) }}</dt>
          <dd class="kv__value" [class.kv__value--mono]="item.mono">
            @if (hasValue(item)) {
              @if (item.tone) {
                <hq-status-badge [tone]="item.tone" [label]="'' + item.value"></hq-status-badge>
              } @else {
                <span class="kv__text">{{ item.value }}</span>
              }
              @if (item.copyable) {
                <button type="button" class="kv__copy" (click)="copy(item)" [attr.aria-label]="copyLabel" [title]="copyLabel">
                  <i class="bi bi-clipboard" aria-hidden="true"></i>
                </button>
              }
            } @else {
              <span class="kv__none">{{ noneText }}</span>
            }
          </dd>
        </div>
      }
    </dl>
  `,
  styleUrl: './hq-key-value-list.scss',
})
export class HqKeyValueList {
  private readonly texts: HqUiStateTexts = inject(HQ_UI_STATE_TEXTS, { optional: true }) ?? HQ_UI_STATE_DEFAULT_TEXTS;
  private readonly notifier = inject(HqNotificationService);

  @Input() items: readonly HqKeyValueItem[] = [];
  /** Tighter rows (less vertical padding). */
  @Input({ transform: booleanAttribute }) dense = false;
  /** CSS grid track of the label column; default `minmax(7rem, 38%)`. */
  @Input() labelWidth = HQ_KEY_VALUE_DEFAULT_LABEL_WIDTH;

  get noneText(): string {
    return hqUiText(this.texts, 'none');
  }

  get copyLabel(): string {
    return hqUiText(this.texts, 'copy');
  }

  labelOf(item: HqKeyValueItem): string {
    if (item.label) return item.label;
    if (!item.labelKey) return '';
    return (this.texts.translate ?? HQ_UI_STATE_DEFAULT_TEXTS.translate!)(item.labelKey);
  }

  hasValue(item: HqKeyValueItem): boolean {
    return item.value !== null && item.value !== undefined && String(item.value).trim() !== '';
  }

  async copy(item: HqKeyValueItem): Promise<void> {
    if (!this.hasValue(item)) return;
    const label = this.labelOf(item);
    const ok = await hqCopyText(String(item.value));
    if (ok) this.notifier.success((this.texts.copied ?? HQ_UI_STATE_DEFAULT_TEXTS.copied!)(label));
    else this.notifier.error((this.texts.copyFailed ?? HQ_UI_STATE_DEFAULT_TEXTS.copyFailed!)(label));
  }
}
