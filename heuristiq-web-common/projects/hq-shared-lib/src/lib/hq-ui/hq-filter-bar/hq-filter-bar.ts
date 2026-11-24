import { ChangeDetectorRef, Component, Directive, Input, OnDestroy, OnInit, booleanAttribute, inject } from '@angular/core';
import { HQ_UI_STATE_DEFAULT_TEXTS, HQ_UI_STATE_TEXTS, HqUiStateTexts, hqUiText } from '../hq-view-states/hq-ui-state.texts';
import { HQ_FILTER_BAR_COLLAPSE_QUERY } from './hq-filter-bar.constants';

let nextFilterBarId = 0;

/** Marks the Filter / Clear buttons of a {@link HqFilterBar} (shown at the end of the row). */
@Directive({ selector: '[hqFilterActions]', standalone: false, host: { class: 'hq-filter-actions' } })
export class HqFilterActions {}

/**
 * The row of filters above a list (the shared replacement for `.list-filters` / `.wk-filter-row`): the projected
 * controls wrap onto as many lines as they need, the element marked `hqFilterActions` (Filter / Clear) sits at the end.
 * With `collapsible` the controls fold behind a "Filters" button on phones (HQ_UI_STATE_TEXTS.filters()).
 *
 *   <hq-filter-bar collapsible>
 *     <hq-field type="search" [placeholder]="..." [(ngModel)]="q"></hq-field>
 *     <hq-searchable-select [(ngModel)]="status">...</hq-searchable-select>
 *     <div hqFilterActions>
 *       <button hqButton="accent" (click)="apply()">Filter</button>
 *       <button hqButton="secondary" (click)="clear()">Clear</button>
 *     </div>
 *   </hq-filter-bar>
 */
@Component({
  selector: 'hq-filter-bar',
  standalone: false,
  host: { '[class.hq-filter-bar--dense]': 'dense' },
  template: `
    @if (collapsible && narrow) {
      <button type="button" class="hq-filter-bar__toggle" [attr.aria-expanded]="expanded" [attr.aria-controls]="panelId"
              (click)="expanded = !expanded">
        <i class="bi bi-funnel" aria-hidden="true"></i>
        <span>{{ toggleLabel }}</span>
        <i class="bi hq-filter-bar__chevron" [class.bi-chevron-down]="!expanded" [class.bi-chevron-up]="expanded" aria-hidden="true"></i>
      </button>
    }
    <div class="hq-filter-bar" [id]="panelId" role="group" [attr.aria-label]="ariaLabel || null"
         [hidden]="collapsible && narrow && !expanded">
      <div class="hq-filter-bar__controls"><ng-content></ng-content></div>
      <ng-content select="[hqFilterActions]"></ng-content>
    </div>
  `,
  styleUrl: './hq-filter-bar.scss',
})
export class HqFilterBar implements OnInit, OnDestroy {
  private readonly texts: HqUiStateTexts = inject(HQ_UI_STATE_TEXTS, { optional: true }) ?? HQ_UI_STATE_DEFAULT_TEXTS;
  private readonly cdr = inject(ChangeDetectorRef);

  /** On phones, fold the controls behind a "Filters" toggle. */
  @Input({ transform: booleanAttribute }) collapsible = false;
  /** Smaller gaps and no bottom margin (e.g. inside a card header). */
  @Input({ transform: booleanAttribute }) dense = false;
  /** Label of the phone toggle; empty uses HQ_UI_STATE_TEXTS.filters(). */
  @Input() toggleText = '';
  /** Accessible name of the filter group. */
  @Input() ariaLabel = '';
  /** Whether the folded controls are shown on phones (starts folded). */
  @Input({ transform: booleanAttribute }) expanded = false;

  readonly panelId = `hq-filter-bar-${nextFilterBarId++}`;
  narrow = false;
  private media: MediaQueryList | null = null;
  // Zoneless apps (Rider_IMSWeb) need the explicit markForCheck.
  private readonly onMedia = (event: MediaQueryListEvent): void => {
    this.narrow = event.matches;
    this.cdr.markForCheck();
  };

  get toggleLabel(): string {
    return this.toggleText || hqUiText(this.texts, 'filters');
  }

  ngOnInit(): void {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    this.media = window.matchMedia(HQ_FILTER_BAR_COLLAPSE_QUERY);
    this.narrow = this.media.matches;
    this.media.addEventListener?.('change', this.onMedia);
  }

  ngOnDestroy(): void {
    this.media?.removeEventListener?.('change', this.onMedia);
  }
}
