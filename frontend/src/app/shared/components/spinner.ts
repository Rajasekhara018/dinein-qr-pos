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
  template: `
    <svg class="animate-spin motion-reduce:animate-none" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-opacity="0.25" stroke-width="3" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
    </svg>
  `,
})
export class Spinner {
  readonly size = input(20);
  readonly label = input('');
}
