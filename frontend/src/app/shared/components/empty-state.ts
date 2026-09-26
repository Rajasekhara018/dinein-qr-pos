import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Friendly empty/info state. Project an icon with `[emptyIcon]` and actions as default content:
 * `<app-empty-state title="Your cart is empty"><a appButton routerLink="/menu">Browse menu</a></app-empty-state>`
 */
@Component({
  selector: 'app-empty-state',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-col items-center px-4 py-10 text-center sm:py-16' },
  templateUrl: './empty-state.html',
})
export class EmptyState {
  readonly title = input.required<string>();
  readonly message = input('');
}
