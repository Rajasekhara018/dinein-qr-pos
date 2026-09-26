import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RealtimeService } from '../../core/realtime/realtime.service';

/** "Reconnecting…" banner bound to `RealtimeService.connectionState` (visible only while a lost connection retries). */
@Component({
  selector: 'app-reconnecting-banner',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block print:hidden' },
  templateUrl: './reconnecting-banner.html',
})
export class ReconnectingBanner {
  private readonly realtime = inject(RealtimeService);
  protected readonly visible = computed(() => this.realtime.connectionState() === 'reconnecting');
}
