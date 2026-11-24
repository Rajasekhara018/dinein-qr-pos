import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/** Columns of a dashboard on a wide screen. */
export type HqDashboardGridCols = 1 | 2 | 3 | 4;

function toCols(value: unknown): HqDashboardGridCols {
  const n = Number(value);
  return n === 1 || n === 2 || n === 3 || n === 4 ? n : 2;
}

/**
 * The layout of a dashboard's cards: a grid of 1–4 columns that drops to 2 below 992px and 1 below 576px. A card spans more
 * columns with the global classes .hq-span-2 / .hq-span-3 / .hq-span-full. The filter row (one hq-filter-bar with the
 * hq-date-range) sits above the grid, never inside a card.
 */
@Component({
  selector: 'hq-dashboard-grid',
  standalone: true,
  host: {
    class: 'hq-dashboard-grid',
    '[class.hq-dashboard-grid--cols-1]': 'cols === 1',
    '[class.hq-dashboard-grid--cols-2]': 'cols === 2',
    '[class.hq-dashboard-grid--cols-3]': 'cols === 3',
    '[class.hq-dashboard-grid--cols-4]': 'cols === 4',
    '[class.hq-dashboard-grid--gap-sm]': "gap === 'sm'",
    '[class.hq-dashboard-grid--gap-md]': "gap === 'md'",
    '[class.hq-dashboard-grid--gap-lg]': "gap === 'lg'",
  },
  template: '<ng-content></ng-content>',
  styles: [`
    :host { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: stretch; min-width: 0; gap: 1rem; }
    :host(.hq-dashboard-grid--cols-1) { grid-template-columns: minmax(0, 1fr); }
    :host(.hq-dashboard-grid--cols-3) { grid-template-columns: repeat(3, minmax(0, 1fr)); }
    :host(.hq-dashboard-grid--cols-4) { grid-template-columns: repeat(4, minmax(0, 1fr)); }
    :host(.hq-dashboard-grid--gap-sm) { gap: .75rem; }
    :host(.hq-dashboard-grid--gap-lg) { gap: 1.5rem; }
    @media (max-width: 991.98px) {
      :host(.hq-dashboard-grid--cols-3), :host(.hq-dashboard-grid--cols-4) { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }
    @media (max-width: 575.98px) {
      :host { grid-template-columns: minmax(0, 1fr) !important; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HqDashboardGrid {
  @Input({ transform: toCols }) cols: HqDashboardGridCols = 2;
  @Input() gap: 'sm' | 'md' | 'lg' = 'md';
}
