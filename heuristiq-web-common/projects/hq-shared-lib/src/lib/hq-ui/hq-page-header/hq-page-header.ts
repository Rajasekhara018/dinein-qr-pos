import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { HQ_UI_STATE_DEFAULT_TEXTS, HQ_UI_STATE_TEXTS, HqUiStateTexts, hqUiText } from '../hq-view-states/hq-ui-state.texts';
import { HqBreadcrumb } from './hq-page-header.constants';

/**
 * Title block shown at the top of a screen: optional breadcrumb trail, kicker, title, subtitle, a "last updated" /
 * "loading" status line and an actions slot (`<div actions>`). The library has no translation
 * dependency, so the status texts are passed in already translated.
 *
 *   <hq-page-header [title]="t" [breadcrumbs]="[{ label: 'Settings', link: '/settings' }, { label: t }]">
 */
@Component({
  selector: 'hq-page-header',
  templateUrl: './hq-page-header.html',
  styleUrl: './hq-page-header.scss',
  standalone: false,
})
export class HqPageHeader {
  private readonly texts: HqUiStateTexts = inject(HQ_UI_STATE_TEXTS, { optional: true }) ?? HQ_UI_STATE_DEFAULT_TEXTS;

  /** Trail shown above the title; the last item is the current page (aria-current). Empty/null shows none. */
  @Input() breadcrumbs: readonly HqBreadcrumb[] | null = null;
  /** Accessible name of the breadcrumb nav; empty uses HQ_UI_STATE_TEXTS.breadcrumbs(). */
  @Input() breadcrumbsLabel = '';
  @Input() kicker = '';
  @Input() title = '';
  @Input() subtitle = '';
  /** When set (and not loading) the status line shows `lastUpdatedLabel` with this time. */
  @Input() lastUpdated: Date | null = null;
  /** Already-translated text for the last-updated line, e.g. "Updated 10:42 AM". */
  @Input() lastUpdatedLabel = '';
  @Input() loading = false;
  /** Already-translated text shown while `loading`. */
  @Input() loadingLabel = '';
  /** Shows a back arrow left of the title; the parent decides where it goes via (backClick). */
  @Input() showBack = false;
  @Input() backLabel = '';
  @Output() backClick = new EventEmitter<void>();

  get crumbsLabel(): string {
    return this.breadcrumbsLabel || hqUiText(this.texts, 'breadcrumbs');
  }
}
