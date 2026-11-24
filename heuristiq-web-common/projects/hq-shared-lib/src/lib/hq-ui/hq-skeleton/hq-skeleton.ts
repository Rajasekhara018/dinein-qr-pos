import { Component, Input, inject, numberAttribute } from '@angular/core';
import { HQ_UI_STATE_DEFAULT_TEXTS, HQ_UI_STATE_TEXTS, HqUiStateTexts } from '../hq-view-states/hq-ui-state.texts';
import {
  HQ_SKELETON_DEFAULT_COLS, HQ_SKELETON_DEFAULT_LINES, HQ_SKELETON_DEFAULT_ROWS, HQ_SKELETON_DEFAULT_VARIANT,
  HQ_SKELETON_MAX_COUNT, HqSkeletonVariant,
} from './hq-skeleton.constants';

/** 0..max list for @for loops; negative or invalid counts give an empty list. */
function range(count: number): number[] {
  const n = Number.isFinite(count) ? Math.max(0, Math.min(HQ_SKELETON_MAX_COUNT, Math.floor(count))) : 0;
  return Array.from({ length: n }, (_, i) => i);
}

/**
 * Shimmering placeholder shown while content loads: lines of text, a block, a circle (avatar), a table or a card.
 * The drawing is hidden from screen readers; they hear the loading text instead (HQ_UI_STATE_TEXTS.loading()).
 *
 *   <hq-skeleton variant="table" [rows]="6" [cols]="5"></hq-skeleton>
 *   <hq-skeleton [lines]="2" width="60%"></hq-skeleton>
 */
@Component({
  selector: 'hq-skeleton',
  standalone: false,
  host: { role: 'status', 'aria-live': 'polite', '[attr.aria-busy]': 'true' },
  template: `
    <span class="hq-skeleton__sr">{{ label || texts.loading() }}</span>
    <div class="hq-skeleton" [ngClass]="'hq-skeleton--' + variant" aria-hidden="true"
         [style.inline-size]="width || null" [style.block-size]="variant === 'rect' || variant === 'circle' ? (height || null) : null">
      @switch (variant) {
        @case ('text') {
          @for (i of lineList; track i) {
            <span class="hq-skeleton__bar" [style.block-size]="height || null"></span>
          }
        }
        @case ('table') {
          <div class="hq-skeleton__table" [style.--_cols]="colCount">
            @for (c of colList; track c) { <span class="hq-skeleton__bar hq-skeleton__bar--head"></span> }
            @for (r of rowList; track r) {
              @for (c of colList; track c) { <span class="hq-skeleton__bar"></span> }
            }
          </div>
        }
        @case ('card') {
          <div class="hq-skeleton__card">
            <span class="hq-skeleton__bar hq-skeleton__bar--title"></span>
            @for (i of lineList; track i) { <span class="hq-skeleton__bar"></span> }
          </div>
        }
        @default {
          <span class="hq-skeleton__block"></span>
        }
      }
    </div>
  `,
  styleUrl: './hq-skeleton.scss',
})
export class HqSkeleton {
  readonly texts: HqUiStateTexts = inject(HQ_UI_STATE_TEXTS, { optional: true }) ?? HQ_UI_STATE_DEFAULT_TEXTS;

  @Input() variant: HqSkeletonVariant = HQ_SKELETON_DEFAULT_VARIANT;
  /** Text lines (`text`, `card`). */
  @Input({ transform: numberAttribute }) lines = HQ_SKELETON_DEFAULT_LINES;
  /** Body rows (`table`). */
  @Input({ transform: numberAttribute }) rows = HQ_SKELETON_DEFAULT_ROWS;
  /** Columns (`table`). */
  @Input({ transform: numberAttribute }) cols = HQ_SKELETON_DEFAULT_COLS;
  /** CSS width of the whole placeholder, e.g. '60%' or '12rem'; empty = full width (circle: 2.5rem). */
  @Input() width = '';
  /** CSS height: of each line for `text`, of the block for `rect` / `circle`. */
  @Input() height = '';
  /** Text for screen readers; empty uses HQ_UI_STATE_TEXTS.loading(). */
  @Input() label = '';

  get lineList(): number[] { return range(this.lines); }
  get rowList(): number[] { return range(this.rows); }
  get colList(): number[] { return range(this.cols); }
  get colCount(): number { return Math.max(1, this.colList.length); }
}
