import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Inline spinner (inherits `currentColor`). Decorative unless `label` is set. */
@Component({
  selector: 'app-spinner',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'inline-flex',
    '[attr.role]': 'label() ? "status" : null',
    '[attr.aria-label]': 'label() || null',
    '[attr.aria-hidden]': 'label() ? null : "true"',
  },
  templateUrl: './spinner.html',
})
export class Spinner {
  readonly size = input(20);
  readonly label = input('');
}
