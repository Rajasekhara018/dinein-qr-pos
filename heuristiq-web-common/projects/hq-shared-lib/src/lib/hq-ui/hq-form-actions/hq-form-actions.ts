import { Component, Input, booleanAttribute } from '@angular/core';
import { HQ_FORM_ACTIONS_DEFAULT_ALIGN, HqFormActionsAlign } from './hq-form-actions.constants';

/**
 * The button row at the bottom of a form (Save / Cancel ...). Buttons wrap on narrow screens and, below 576px with
 * `align` end or between, take the full width. `sticky` keeps the row at the bottom of the scrolling panel.
 *
 *   <hq-form-actions>
 *     <button hqButton="secondary" type="button" (click)="cancel()">Cancel</button>
 *     <button hqButton type="submit" [loading]="saving">Save</button>
 *   </hq-form-actions>
 */
@Component({
  selector: 'hq-form-actions',
  standalone: false,
  host: {
    class: 'hq-form-actions',
    '[class.hq-form-actions--end]': "align === 'end'",
    '[class.hq-form-actions--start]': "align === 'start'",
    '[class.hq-form-actions--between]': "align === 'between'",
    '[class.hq-form-actions--center]': "align === 'center'",
    '[class.hq-form-actions--sticky]': 'sticky',
  },
  template: '<ng-content></ng-content>',
  styleUrl: './hq-form-actions.scss',
})
export class HqFormActions {
  @Input() align: HqFormActionsAlign = HQ_FORM_ACTIONS_DEFAULT_ALIGN;
  /** Stays at the bottom of the scroll container, on the panel background. */
  @Input({ transform: booleanAttribute }) sticky = false;
}
