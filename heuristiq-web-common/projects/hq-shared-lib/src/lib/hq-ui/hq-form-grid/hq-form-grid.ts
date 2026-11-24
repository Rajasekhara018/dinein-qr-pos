import { Component, Input } from '@angular/core';
import {
  HQ_FORM_GRID_DEFAULT_COLS, HQ_FORM_GRID_DEFAULT_GAP, HqFormGridCols, HqFormGridGap, hqFormGridCols,
} from './hq-form-grid.constants';

/**
 * The shared form layout: the projected fields flow into `cols` equal columns. Below 992px a 3 or 4 column grid drops
 * to 2 columns; below 576px every grid is a single column. A child spans more columns with the global helper classes
 * `hq-span-2`, `hq-span-3` or `hq-span-full` (from hq-shared-lib/theme/styles.scss); spans shrink with the grid.
 *
 *   <hq-form-grid cols="3">
 *     <hq-field label="Name" formControlName="name"></hq-field>
 *     <hq-field class="hq-span-2" label="Address" formControlName="address"></hq-field>
 *     <hq-field class="hq-span-full" type="textarea" label="Notes" formControlName="notes"></hq-field>
 *   </hq-form-grid>
 */
@Component({
  selector: 'hq-form-grid',
  standalone: false,
  host: {
    class: 'hq-form-grid',
    '[class.hq-form-grid--cols-1]': 'cols === 1',
    '[class.hq-form-grid--cols-2]': 'cols === 2',
    '[class.hq-form-grid--cols-3]': 'cols === 3',
    '[class.hq-form-grid--cols-4]': 'cols === 4',
    '[class.hq-form-grid--gap-sm]': "gap === 'sm'",
    '[class.hq-form-grid--gap-md]': "gap === 'md'",
    '[class.hq-form-grid--gap-lg]': "gap === 'lg'",
  },
  template: '<ng-content></ng-content>',
  styleUrl: './hq-form-grid.scss',
})
export class HqFormGrid {
  /** Columns on wide screens: 1–4, as a number (`[cols]="3"`) or an attribute (`cols="3"`). */
  @Input({ transform: hqFormGridCols }) cols: HqFormGridCols = HQ_FORM_GRID_DEFAULT_COLS;
  @Input() gap: HqFormGridGap = HQ_FORM_GRID_DEFAULT_GAP;
}
