import { Component, Input } from '@angular/core';

export type HqStatTone = 'blue' | 'purple' | 'orange' | 'green';

export interface HqStatCardItem {
  /** Already-translated label, e.g. "Total". */
  label: string;
  value: number | string;
  /** Bootstrap Icons class, e.g. "bi-people-fill". */
  icon: string;
  tone: HqStatTone;
}

/** Row of headline number cards shown above a list. */
@Component({
  selector: 'hq-stat-cards',
  templateUrl: './hq-stat-cards.html',
  styleUrl: './hq-stat-cards.scss',
  standalone: false,
})
export class HqStatCards {
  @Input() stats: HqStatCardItem[] = [];
  /**
   * The cards share the row evenly (and wrap onto more rows on narrow screens) instead of keeping a fixed width with empty space
   * after them; for a row that should line up with the full width of the page. Off by default.
   */
  @Input() fill = false;
  /** No space below the row, and the cards take the full height of their slot: for a row placed beside another card. Off by default. */
  @Input() flush = false;
}
