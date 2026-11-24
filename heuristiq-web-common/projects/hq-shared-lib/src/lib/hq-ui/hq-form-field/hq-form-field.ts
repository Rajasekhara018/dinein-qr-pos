import { Component, Input, booleanAttribute } from '@angular/core';
import { HQ_FORM_FIELD_ID_PREFIX } from './hq-form-field.constants';

let nextFormFieldId = 0;

/**
 * Gives any projected control (hq-searchable-select, a checkbox or radio group, hq-file-drop, a custom picker) the
 * same label, hint and error look as hq-field. Plain text, number and date inputs use hq-field's own
 * `label` / `hint` / `error` instead.
 *
 *   <hq-form-field [label]="'driver.city' | translate" for="city" required [error]="cityError">
 *     <hq-searchable-select inputId="city" formControlName="city" [allowOther]="false">...</hq-searchable-select>
 *   </hq-form-field>
 *
 * `for` links the label to the inner control's id. `error` replaces the hint while it is set and is announced
 * (role="alert"). `inline` puts the label beside the control on wide screens.
 */
@Component({
  selector: 'hq-form-field',
  standalone: false,
  host: {
    class: 'hq-form-field',
    '[class.hq-form-field--inline]': 'inline',
    '[class.has-error]': '!!error',
  },
  template: `
    @if (label) {
      <label class="hq-ff__label" [attr.for]="forId || null">{{ label }}@if (required) {<em class="hq-ff__req" aria-hidden="true">*</em>}</label>
    }
    <div class="hq-ff__control">
      <ng-content></ng-content>
      @if (error) {
        <small class="hq-ff__error" role="alert" [id]="messageId">{{ error }}</small>
      } @else if (hint) {
        <small class="hq-ff__hint" [id]="messageId">{{ hint }}</small>
      }
    </div>
  `,
  styleUrl: './hq-form-field.scss',
})
export class HqFormField {
  /** Already-translated label. */
  @Input() label = '';
  /** Already-translated help line under the control. */
  @Input() hint = '';
  /** Already-translated error; shown instead of the hint while set. */
  @Input() error: string | null | undefined = null;
  /** Shows the required asterisk (the validation itself stays on the control). */
  @Input({ transform: booleanAttribute }) required = false;
  /** Id of the inner control, so clicking the label focuses it. */
  @Input('for') forId = '';
  /** Label beside the control (wide screens) instead of above it. */
  @Input({ transform: booleanAttribute }) inline = false;

  /** Id of the hint / error line; pass it as the inner control's aria-describedby when it supports one. */
  readonly messageId = `${HQ_FORM_FIELD_ID_PREFIX}-${nextFormFieldId++}-msg`;
}
