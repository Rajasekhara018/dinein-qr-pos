import { Component, HostBinding, Input, OnChanges } from '@angular/core';

import { AdminFormGuide } from '../admin-form.types';

/**
 * The help beside (or above) a form: what the screen is for, what each setting means and a worked example, all translation keys.
 * Beside a form it stays in view while the form scrolls (`sticky`); above a step it can be folded away (`collapsible`), and that
 * choice is kept in this browser under `storageKey`. A foldable guide starts folded and opens where the person opened it before.
 */
@Component({
  selector: 'app-form-guide',
  standalone: false,
  templateUrl: './form-guide.html',
  styleUrl: './form-guide.scss',
})
export class FormGuide implements OnChanges {
  @Input({ required: true }) guide!: AdminFormGuide;
  /** Beside a form: stays in view on a wide screen while the form scrolls. */
  @HostBinding('class.is-sticky') @Input() sticky = false;
  /** Above a step: a header that folds the guide away. */
  @Input() collapsible = false;
  /** Where the folded choice is kept (per browser); empty keeps it only while the screen is open. */
  @Input() storageKey = '';

  folded = false;
  /** Unique per instance, so several guides on one page have their own heading ids. */
  readonly titleId = `form-guide-${nextId++}`;

  ngOnChanges(): void {
    this.folded = this.collapsible && readFolded(this.storageKey);
  }

  toggle(): void {
    this.folded = !this.folded;
    if (!this.storageKey) return;
    try {
      localStorage.setItem(this.storageKey, String(this.folded));
    } catch {
      // Storage blocked (private window): the choice lasts until the screen is left.
    }
  }
}

let nextId = 0;

/**
 * A foldable guide starts folded, like every "how this screen works" panel, so a step opens on its form; it shows open only
 * where the person opened it before (kept as "false" under the key).
 */
function readFolded(key: string): boolean {
  if (!key) return true;
  try {
    return localStorage.getItem(key) !== 'false';
  } catch {
    return true;
  }
}
