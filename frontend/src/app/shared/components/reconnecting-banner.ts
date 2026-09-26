import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RealtimeService } from '../../core/realtime/realtime.service';

/** "Reconnecting…" banner bound to `RealtimeService.connectionState` (visible only while a lost connection retries). */
@Component({
  selector: 'app-reconnecting-banner',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block print:hidden' },
  template: `
    <div aria-live="polite">
      @if (visible()) {
        <div
          class="flex items-center justify-center gap-2 bg-amber-100 px-4 py-2 text-center text-sm font-medium text-amber-950 dark:bg-amber-900 dark:text-amber-50"
          role="status"
        >
          <app-spinner [size]="16" />
          <span>Reconnecting… live updates will resume shortly.</span>
        </div>
      }
    </div>
  `,
})
export class ReconnectingBanner {
  private readonly realtime = inject(RealtimeService);
  protected readonly visible = computed(() => this.realtime.connectionState() === 'reconnecting');
}
