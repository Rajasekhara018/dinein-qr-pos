import { Directive, inject } from '@angular/core';
import { NgControl } from '@angular/forms';

const BASE =
  'block w-full min-w-0 min-h-touch rounded-control border border-line-strong bg-surface px-3 py-2 text-base ' +
  'text-ink placeholder:text-ink-subtle focus-visible:outline-3 focus-visible:outline-offset-1 ' +
  'focus-visible:outline-focus disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-ink-subtle ' +
  'aria-invalid:border-danger aria-invalid:ring-1 aria-invalid:ring-danger';

/**
 * Consistent admin form control styling (≥ 44px tall, 16px text so iOS does not zoom) and `aria-invalid` once an
 * invalid control was touched. `<input appInput formControlName="name" />`
 */
@Directive({
  selector: 'input[appInput], select[appInput], textarea[appInput]',
  standalone: false,
  host: {
    class: BASE,
    '[attr.aria-invalid]': 'invalid() ? "true" : null',
  },
})
export class InputDirective {
  private readonly ngControl = inject(NgControl, { optional: true, self: true });

  protected invalid(): boolean {
    const control = this.ngControl?.control;
    return !!control && control.invalid && (control.touched || control.dirty) && control.enabled;
  }
}
