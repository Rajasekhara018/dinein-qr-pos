import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  DOCUMENT,
  effect,
  inject,
} from '@angular/core';
import { KitchenPrefs } from '../data/kitchen-prefs';

/**
 * Kitchen root: applies the kitchen theme (dark by default) to its host AND to <html> (so the body background,
 * scrollbars and root-level toasts match), and restores the page theme when leaving the kitchen.
 */
@Component({
  selector: 'app-kitchen-shell',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'block min-h-dvh bg-bg text-ink',
    '[class.dark]': 'prefs.theme() === "dark"',
    '[attr.data-theme]': 'prefs.theme()',
  },
  templateUrl: './kitchen-shell.html',
})
export class KitchenShell {
  protected readonly prefs = inject(KitchenPrefs);

  constructor() {
    const root = inject(DOCUMENT).documentElement;
    const hadDark = root.classList.contains('dark');
    effect(() => root.classList.toggle('dark', this.prefs.theme() === 'dark'));
    inject(DestroyRef).onDestroy(() => root.classList.toggle('dark', hadDark));
  }
}
